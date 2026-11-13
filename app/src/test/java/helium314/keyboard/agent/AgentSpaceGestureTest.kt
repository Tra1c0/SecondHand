// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.view.MotionEvent
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.*
import helium314.keyboard.keyboard.KeyboardActionListener.CustomAction
import helium314.keyboard.keyboard.internal.*
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import java.util.Locale
import kotlin.test.*

/** Real PointerTracker and TimerHandler, synthetic touch packets and only drawing/listener mocks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ShadowProximityInfo::class])
@LooperMode(LooperMode.Mode.PAUSED)
class AgentSpaceGestureTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var tracker: PointerTracker
    private lateinit var detector: KeyDetector
    private lateinit var listener: KeyboardActionListener
    private lateinit var drawing: DrawingProxy
    private lateinit var timer: TimerHandler
    private lateinit var key: Key
    private var now = 10_000L

    @Before fun setup() {
        app.prefs().edit().clear().commit()
        Settings.getInstance().loadSettings(app)
        PointerTracker.setGestureHandlingEnabledByUser(false)
        drawing = mock(DrawingProxy::class.java)
        timer = TimerHandler(drawing, 0, 100)
        val attrs = app.obtainStyledAttributes(R.styleable.MainKeyboardView)
        PointerTracker.init(attrs, timer, drawing)
        attrs.recycle()
        key = mock(Key::class.java)
        `when`(key.code).thenReturn(32)
        `when`(key.isEnabled).thenReturn(true)
        `when`(key.isLongPressEnabled).thenReturn(true)
        val keyboard = Keyboard(KeyboardParams().apply {
            GRID_WIDTH = 1; GRID_HEIGHT = 1; mMostCommonKeyWidth = 50; mMostCommonKeyHeight = 50
            mOccupiedHeight = 200; mOccupiedWidth = 360
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.ALPHABET)
        })
        detector = mock(KeyDetector::class.java)
        `when`(detector.keyboard).thenReturn(keyboard)
        `when`(detector.detectHitKey(anyInt(), anyInt())).thenReturn(key)
        listener = mock(KeyboardActionListener::class.java)
        `when`(listener.onCustomRequest(CustomAction.SPACE_HOLD)).thenReturn(true)
        `when`(listener.onCustomRequest(CustomAction.SHOW_INPUT_METHOD_PICKER)).thenReturn(true)
        `when`(listener.onSpaceSwipe(any(SpaceGesture::class.java), anyInt())).thenReturn(true)
        PointerTracker.setKeyboardActionListener(listener)
        tracker = PointerTracker.getPointerTracker(0)
    }

    private fun touch(action: Int, x: Float = 100f, target: PointerTracker = tracker, y: Float = 100f) {
        val event = MotionEvent.obtain(now, now + 5, action, x, y, 0)
        try { target.processMotionEvent(event, detector) } finally { event.recycle() }
        now += 10
    }

    private fun hold() = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis((Settings.getValues().mKeyLongpressTimeout * 2).toLong()))
    private fun assertSwipeCleared() {
        assertFalse(ReflectionHelpers.getField<Boolean>(tracker, "mKeySwipeAllowed"))
        assertFalse(ReflectionHelpers.getStaticField<Boolean>(PointerTracker::class.java, "sInKeySwipe"))
    }

    @After fun cleanup() { touch(MotionEvent.ACTION_CANCEL); timer.cancelAllMessages() }

    @Test fun consumedHoldClearsSwipeStateWithoutSpaceAndNextTapWorks() {
        touch(MotionEvent.ACTION_DOWN)
        hold()
        verify(listener).onCustomRequest(CustomAction.SPACE_HOLD)
        verify(listener, never()).onCustomRequest(CustomAction.SHOW_INPUT_METHOD_PICKER)
        assertSwipeCleared()
        touch(MotionEvent.ACTION_UP)
        verify(listener, times(1)).onReleaseKey(32, false)
        verify(listener, never()).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
        verify(listener, times(1)).onCodeInput(eq(32), anyInt(), anyInt(), eq(false))
        assertSwipeCleared()
    }

    @Test fun consumedHoldThenCancelDoesNotLeaveSwipeFlags() {
        touch(MotionEvent.ACTION_DOWN); hold(); touch(MotionEvent.ACTION_CANCEL)
        assertSwipeCleared()
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
        verify(listener, times(1)).onCodeInput(eq(32), anyInt(), anyInt(), eq(false))
    }

    @Test fun movementCancelsTimerAndPreservesCursorSwipe() {
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_MOVE, 150f)
        hold()
        verify(listener, never()).onCustomRequest(CustomAction.SPACE_HOLD)
        verify(listener, atLeastOnce()).onSpaceSwipe(eq(SpaceGesture.SWIPE_RIGHT), anyInt())
        touch(MotionEvent.ACTION_UP)
        assertSwipeCleared()
    }

    @Test fun popupKeysAndGlobeKeepTheirExistingActions() {
        `when`(key.popupKeys).thenReturn(arrayOf(PopupKeySpec("x", false, Locale.ROOT)))
        touch(MotionEvent.ACTION_DOWN); hold()
        verify(listener, never()).onCustomRequest(CustomAction.SPACE_HOLD)
        verify(drawing).showPopupKeysKeyboard(key, tracker)
        touch(MotionEvent.ACTION_CANCEL)
        `when`(key.popupKeys).thenReturn(null)
        `when`(key.code).thenReturn(KeyCode.LANGUAGE_SWITCH)
        touch(MotionEvent.ACTION_DOWN); hold()
        verify(listener).onCustomRequest(CustomAction.SHOW_INPUT_METHOD_PICKER)
        verify(listener, never()).onCustomRequest(CustomAction.SPACE_HOLD)
    }

    @Test fun twoHeldPointersDoNotOpenWheel() {
        touch(MotionEvent.ACTION_DOWN)
        val properties = Array(2) { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(2) { MotionEvent.PointerCoords().apply { x = 100f; y = 100f; pressure = 1f; size = 1f } }
        val packet = MotionEvent.obtain(now, now + 5,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { PointerTracker.getPointerTracker(packet.getPointerId(packet.actionIndex)).processMotionEvent(packet, detector) }
        finally { packet.recycle() }
        hold()
        verify(listener, never()).onCustomRequest(CustomAction.SPACE_HOLD)
    }

    @Test fun unavailableWheelFallsBackToExistingPicker() {
        `when`(listener.onCustomRequest(CustomAction.SPACE_HOLD)).thenReturn(false)
        touch(MotionEvent.ACTION_DOWN); hold(); touch(MotionEvent.ACTION_UP)
        verify(listener).onCustomRequest(CustomAction.SHOW_INPUT_METHOD_PICKER)
        verify(listener, never()).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
        assertSwipeCleared()
    }

    private fun setGesture(gesture: SpaceGesture, action: SpaceAction) {
        SpaceGestures.write(app.prefs(), gesture, action)
        Settings.getInstance().loadSettings(app)
    }

    @Test fun eachSwipeDirectionReachesItsOwnGesture() {
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE, 40f); touch(MotionEvent.ACTION_UP, 40f)
        verify(listener, atLeastOnce()).onSpaceSwipe(eq(SpaceGesture.SWIPE_LEFT), intThat { it < 0 })
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE, 160f); touch(MotionEvent.ACTION_UP, 160f)
        verify(listener, atLeastOnce()).onSpaceSwipe(eq(SpaceGesture.SWIPE_RIGHT), intThat { it > 0 })
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE, y = 40f); touch(MotionEvent.ACTION_UP, y = 40f)
        verify(listener).onSpaceSwipe(eq(SpaceGesture.SWIPE_UP), intThat { it < 0 })
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE, y = 160f); touch(MotionEvent.ACTION_UP, y = 160f)
        verify(listener).onSpaceSwipe(eq(SpaceGesture.SWIPE_DOWN), intThat { it > 0 })
        // swipes never type a space, and every pointer ended cleanly
        verify(listener, never()).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
        verify(listener, times(4)).onEndSpaceSwipe()
        assertSwipeCleared()
    }

    @Test fun continuousSwipeKeepsItsStartDirectionWhenMovingBack() {
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_MOVE, 160f)
        touch(MotionEvent.ACTION_MOVE, 40f) // back past the start: still the right swipe moving the cursor back
        touch(MotionEvent.ACTION_UP, 40f)
        verify(listener, atLeastOnce()).onSpaceSwipe(eq(SpaceGesture.SWIPE_RIGHT), intThat { it < 0 })
        verify(listener, never()).onSpaceSwipe(eq(SpaceGesture.SWIPE_LEFT), anyInt())
        verify(listener, never()).onSpaceSwipe(eq(SpaceGesture.SWIPE_UP), anyInt())
    }

    @Test fun oneShotSwipeActionRunsOncePerSwipe() {
        setGesture(SpaceGesture.SWIPE_RIGHT, SpaceAction.ACTION_WHEEL)
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_MOVE, 130f); touch(MotionEvent.ACTION_MOVE, 170f); touch(MotionEvent.ACTION_MOVE, 60f)
        touch(MotionEvent.ACTION_UP, 60f)
        verify(listener, times(1)).onSpaceSwipe(eq(SpaceGesture.SWIPE_RIGHT), anyInt())
        verify(listener, times(1)).onSpaceSwipe(any(SpaceGesture::class.java), anyInt())
        verify(listener, never()).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
        assertSwipeCleared()
        // the next swipe runs it again, the next tap types a space
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE, 130f); touch(MotionEvent.ACTION_UP, 130f)
        verify(listener, times(2)).onSpaceSwipe(eq(SpaceGesture.SWIPE_RIGHT), anyInt())
        touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_UP)
        verify(listener, times(1)).onCodeInput(eq(32), anyInt(), anyInt(), eq(false))
    }

    @Test fun withoutSwipeActionsSpaceIsNoSwiper() {
        listOf(SpaceGesture.SWIPE_LEFT, SpaceGesture.SWIPE_RIGHT, SpaceGesture.SWIPE_UP, SpaceGesture.SWIPE_DOWN)
            .forEach { setGesture(it, SpaceAction.NONE) }
        touch(MotionEvent.ACTION_DOWN)
        assertSwipeCleared()
        touch(MotionEvent.ACTION_MOVE, 160f); touch(MotionEvent.ACTION_UP, 160f)
        verify(listener, never()).onSpaceSwipe(any(SpaceGesture::class.java), anyInt())
        // only the vertical axis set: the space bar swipes again
        setGesture(SpaceGesture.SWIPE_DOWN, SpaceAction.HIDE_KEYBOARD)
        touch(MotionEvent.ACTION_DOWN)
        assertTrue(ReflectionHelpers.getField<Boolean>(tracker, "mKeySwipeAllowed"))
        touch(MotionEvent.ACTION_MOVE, y = 160f); touch(MotionEvent.ACTION_UP, y = 160f)
        verify(listener).onSpaceSwipe(eq(SpaceGesture.SWIPE_DOWN), anyInt())
    }

    @Test fun noPanelAutoPopupKeepsPriority() {
        `when`(key.popupKeys).thenReturn(arrayOf(PopupKeySpec("x", false, Locale.ROOT)))
        `when`(key.hasNoPanelAutoPopupKey()).thenReturn(true)
        touch(MotionEvent.ACTION_DOWN); hold(); touch(MotionEvent.ACTION_UP)
        verify(listener, never()).onCustomRequest(CustomAction.SPACE_HOLD)
        verify(listener).onCodeInput(eq('x'.code), anyInt(), anyInt(), eq(false))
        assertSwipeCleared()
    }
}
