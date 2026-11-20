// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.view.MotionEvent
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.*
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
import org.robolectric.util.ReflectionHelpers
import kotlin.test.*

/**
 * Stage 87: the real PointerTracker with a mock listener that records what the tracker tells it.
 * The legacy fixture (first half) was recorded from the code before stage 87 and must keep passing
 * with "Wie bisher" (agent_cursor_trackpad off) and with a listener that does not know the trackpad.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ShadowProximityInfo::class])
@LooperMode(LooperMode.Mode.PAUSED)
class CursorGestureTrackerTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var tracker: PointerTracker
    private lateinit var detector: KeyDetector
    private lateinit var listener: KeyboardActionListener
    private lateinit var drawing: DrawingProxy
    private lateinit var timer: TimerHandler
    private lateinit var key: Key
    private var now = 10_000L
    private val log = mutableListOf<String>()

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
        `when`(listener.onSpaceSwipe(any(SpaceGesture::class.java), anyInt())).thenAnswer { log += "swipe ${it.getArgument<SpaceGesture>(0)} ${it.getArgument<Int>(1)}"; true }
        `when`(listener.onMoveDeletePointer(anyInt())).thenAnswer { log += "delete ${it.getArgument<Int>(0)}"; null }
        `when`(listener.onUpWithDeletePointerActive()).thenAnswer { log += "delete-up"; null }
        `when`(listener.onEndSpaceSwipe()).thenAnswer { log += "end"; null }
        `when`(listener.onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer { log += "code ${it.getArgument<Int>(0)}"; null }
        PointerTracker.setKeyboardActionListener(listener)
        tracker = PointerTracker.getPointerTracker(0)
    }

    @After fun cleanup() { touch(MotionEvent.ACTION_CANCEL, 100f, 100f); timer.cancelAllMessages() }

    private fun touch(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(now, now + 5, action, x, y, 0)
        try { tracker.processMotionEvent(event, detector) } finally { event.recycle() }
        now += 10
    }

    private fun move(x: Float, y: Float) = touch(MotionEvent.ACTION_MOVE, x, y)
    private fun down(x: Float = 100f, y: Float = 100f) = touch(MotionEvent.ACTION_DOWN, x, y)
    private fun up(x: Float, y: Float) = touch(MotionEvent.ACTION_UP, x, y)

    private fun legacy() { app.prefs().edit().putBoolean(AgentPreferences.CURSOR_TRACKPAD, false).commit(); Settings.getInstance().loadSettings(app) }

    // ---- the legacy fixture: what the tracker reported before stage 87, 10 dp per step, per move event

    @Test fun legacySpaceSwipeSidewaysReportsTenDpSteps() {
        legacy()
        down(); move(104f, 100f); move(113f, 100f); move(131f, 102f); move(129f, 100f); move(160f, 100f); move(150f, 100f); up(150f, 100f)
        assertEquals(listOf("swipe SWIPE_RIGHT 1", "swipe SWIPE_RIGHT 2", "swipe SWIPE_RIGHT 3", "swipe SWIPE_RIGHT -1", "end"), log)
    }

    @Test fun legacySpaceSwipeVerticalWithoutActionReportsOnlyTheFirstStep() {
        legacy()
        down(); move(100f, 85f); move(100f, 62f); up(100f, 62f)
        // a vertical swipe without an action is a one-shot swipe: it reports once (and still is no tap)
        assertEquals(listOf("swipe SWIPE_UP -1", "end"), log)
    }

    @Test fun legacySpaceSwipeVerticalWithCursorMovementReportsTenDpSteps() {
        legacy()
        app.prefs().edit().putString(SpaceGesture.SWIPE_UP.key, "MOVE_CURSOR").commit(); Settings.getInstance().loadSettings(app)
        down(); move(100f, 85f); move(100f, 62f); up(100f, 62f)
        assertEquals(listOf("swipe SWIPE_UP -1", "swipe SWIPE_UP -2", "end"), log)
    }

    @Test fun legacyDeleteSwipeReportsTenDpStepsAndDeletesOnRelease() {
        legacy()
        `when`(key.code).thenReturn(KeyCode.DELETE)
        down(); move(88f, 100f); move(70f, 100f); move(85f, 100f); up(85f, 100f)
        assertEquals(listOf("delete -1", "delete -2", "delete 1", "delete-up", "end"), log)
    }

    @Test fun legacyATapOnSpaceStillTypesASpace() {
        legacy()
        down(); move(104f, 102f); up(104f, 102f)
        assertEquals(listOf("code 32"), log)
    }
}
