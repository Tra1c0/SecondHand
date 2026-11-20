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

    // ---- the trackpad protocol (stage 87): the tracker hands over raw px distances and only marks the swipe

    private val calls = mutableListOf<List<Number>>()
    private var answer = KeyboardActionListener.TRACK_OFF

    private fun script(vararg answers: Int) {
        val queue = ArrayDeque(answers.toList())
        `when`(listener.onCursorTrack(anyInt(), anyInt(), anyInt(), anyInt(), anyLong(), anyLong())).thenAnswer {
            calls += listOf(it.getArgument<Int>(0), it.getArgument<Int>(1), it.getArgument<Int>(2), it.getArgument<Int>(3), it.getArgument<Long>(4), it.getArgument<Long>(5))
            if (queue.size > 1) queue.removeFirst() else queue.first()
        }
    }

    @Test fun trackpadGetsRawDistancesFromTouchDownAndTheOldStepLogicStaysOut() {
        script(KeyboardActionListener.TRACK_PENDING, KeyboardActionListener.TRACK_ACTIVE)
        down(); move(104f, 103f); move(160f, 70f); move(150f, 90f); up(150f, 90f)
        // pointer 0, the key, the distance from the touch-down point in px
        assertEquals(listOf(listOf(0, 32, 4, 3), listOf(0, 32, 60, -30), listOf(0, 32, 50, -10)), calls.map { it.take(4).map { n -> n.toInt() } })
        assertTrue(calls.map { it[4].toLong() }.zipWithNext().all { (a, b) -> a < b }, "event times grow")
        assertEquals(1, calls.map { it[5] }.toSet().size, "one touch, one token")
        // no old step reports, no space typed; the end is reported once
        assertEquals(listOf("end"), log)
    }

    @Test fun trackpadPendingIsATapForTheTracker() {
        script(KeyboardActionListener.TRACK_PENDING)
        down(); move(104f, 102f); up(104f, 102f)
        assertEquals(listOf("code 32"), log)
    }

    @Test fun trackpadActiveMarksTheSwipeAndEndsItLikeASidewaysSwipe() {
        script(KeyboardActionListener.TRACK_ACTIVE)
        down(); move(120f, 100f)
        assertTrue(ReflectionHelpers.getField<Boolean>(tracker, "mInHorizontalSwipe"))
        assertFalse(ReflectionHelpers.getField<Boolean>(tracker, "mInVerticalSwipe"))
        up(120f, 100f)
        assertFalse(ReflectionHelpers.getField<Boolean>(tracker, "mInHorizontalSwipe"))
        assertFalse(ReflectionHelpers.getField<Boolean>(tracker, "mKeySwipeAllowed"))
        assertEquals(listOf("end"), log)
    }

    @Test fun trackpadVerticalDragIsTheSameSwipe() {
        script(KeyboardActionListener.TRACK_ACTIVE)
        down(); move(100f, 60f); move(100f, 20f); up(100f, 20f)
        assertEquals(listOf("end"), log) // no old vertical report, no space
    }

    @Test fun trackpadActiveCancelsTheSpaceHold() {
        script(KeyboardActionListener.TRACK_ACTIVE)
        down(); move(120f, 100f)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis((Settings.getValues().mKeyLongpressTimeout * 2).toLong()))
        verify(listener, never()).onCustomRequest(KeyboardActionListener.CustomAction.SPACE_HOLD)
        up(120f, 100f)
    }

    @Test fun trackpadDeleteSlideMarksTheSwipeAndTheLiftDeletes() {
        `when`(key.code).thenReturn(KeyCode.DELETE)
        script(KeyboardActionListener.TRACK_ACTIVE)
        down(); move(85f, 100f); move(60f, 101f); up(60f, 101f)
        assertEquals(listOf("delete-up", "end"), log) // no onMoveDeletePointer from the tracker any more
        assertEquals(listOf(KeyCode.DELETE, KeyCode.DELETE), calls.map { it[1].toInt() })
    }

    @Test fun trackpadOnALetterKeyIsNeverAsked() {
        `when`(key.code).thenReturn('a'.code)
        script(KeyboardActionListener.TRACK_ACTIVE)
        down(); move(140f, 100f); up(140f, 100f)
        assertTrue(calls.isEmpty())
    }

    @Test fun trackpadOffFromTheStartLeavesEverythingToTheOldLogic() {
        // a listener that says OFF (settings, selection, bound swipe) leaves the whole touch to the old logic (it decides once per touch)
        script(KeyboardActionListener.TRACK_OFF)
        down(); move(113f, 100f); move(131f, 100f); up(131f, 100f)
        assertEquals(listOf("swipe SWIPE_RIGHT 1", "swipe SWIPE_RIGHT 2", "end"), log)
    }

    @Test fun theEndOfAFingersTouchIsReportedWithItsPointerAtUpAndAtCancel() {
        script(KeyboardActionListener.TRACK_ACTIVE)
        val ends = mutableListOf<Int>()
        `when`(listener.onCursorTrackEnd(anyInt())).thenAnswer { ends += it.getArgument<Int>(0); null }
        down(); move(130f, 100f); up(130f, 100f)
        assertEquals(listOf(0), ends)
        down(); move(130f, 100f); touch(MotionEvent.ACTION_CANCEL, 130f, 100f)
        assertEquals(listOf(0, 0), ends)
    }

    @Test fun twoFingersEachGetTheirOwnPointerIdAndTokenAndTheirOwnEnd() {
        script(KeyboardActionListener.TRACK_PENDING)
        val ends = mutableListOf<Int>()
        `when`(listener.onCursorTrackEnd(anyInt())).thenAnswer { ends += it.getArgument<Int>(0); null }
        val second = PointerTracker.getPointerTracker(1)
        `when`(detector.detectHitKey(eq(300), anyInt())).thenReturn(key)
        down()
        val props = Array(2) { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = 100f; y = 100f; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = 300f; y = 100f; pressure = 1f; size = 1f })
        fun packet(action: Int) = MotionEvent.obtain(now, now, action, 2, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        val down2 = packet(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT))
        try { second.processMotionEvent(down2, detector) } finally { down2.recycle() }
        coords[0].x = 106f
        val moved = packet(MotionEvent.ACTION_MOVE)
        try { tracker.processMotionEvent(moved, detector) } finally { moved.recycle() }
        val pointers = calls.map { it[0].toInt() }.toSet()
        assertEquals(setOf(0, 1), pointers, "every finger is asked for itself")
        for (pointer in 0..1) assertEquals(1, calls.filter { it[0].toInt() == pointer }.map { it[5] }.toSet().size, "one token per touch of finger $pointer")
        val up2 = packet(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT))
        try { second.processMotionEvent(up2, detector) } finally { up2.recycle() }
        // upstream releases every older finger when a newer one lifts (PointerTrackerQueue.releaseAllPointersOlderThan): A's end first, then B's
        assertEquals(listOf(0, 1), ends, "each end names its own finger")
        up(106f, 100f)
    }

    @Test fun aRefusedDragCancelsTheTimersAndTheLiftTypesNothing() {
        script(KeyboardActionListener.TRACK_PENDING, KeyboardActionListener.TRACK_REFUSED_DRAG)
        down(); move(104f, 100f); move(130f, 100f)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis((Settings.getValues().mKeyLongpressTimeout * 2).toLong()))
        verify(listener, never()).onCustomRequest(KeyboardActionListener.CustomAction.SPACE_HOLD)
        verify(listener, never()).onCustomRequest(KeyboardActionListener.CustomAction.SHOW_INPUT_METHOD_PICKER)
        move(150f, 100f) // the dropped touch gets no further calls
        assertEquals(2, calls.size)
        up(150f, 100f)
        assertEquals(emptyList(), log, "no space, no end of a swipe")
    }

    @Test fun aRefusedDragOnDeleteTypesNoDeleteAtTheLift() {
        `when`(key.code).thenReturn(KeyCode.DELETE)
        script(KeyboardActionListener.TRACK_REFUSED_DRAG)
        down(); move(130f, 100f); up(130f, 100f)
        assertEquals(emptyList(), log, "neither a delete nor the old slide's delete-up")
    }

    @Test fun aRefusedFingerThatStaysPendingIsATapAsBefore() {
        script(KeyboardActionListener.TRACK_PENDING)
        down(); move(104f, 102f); up(104f, 102f)
        assertEquals(listOf("code 32"), log)
    }
}
