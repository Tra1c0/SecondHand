// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import dev.agentkeyboard.core.InkRecognizer
import dev.agentkeyboard.core.InkStroke
import dev.agentkeyboard.core.InkSymbols
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.*
import helium314.keyboard.keyboard.internal.*
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.InputPointers
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration
import java.util.Locale
import kotlin.test.*

/**
 * Stage 27 with the real PointerTracker and TimerHandler (like AgentSpaceGestureTest): synthetic
 * touch packets, a mocked listener that routes the batch callbacks like KeyboardActionListenerImpl
 * (InputLogicTest.symbolDraw* covers the real routing), and a sink that records the drawings.
 * Letters fill the keyboard above y = 150; below it space (x < 200) and delete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [ShadowProximityInfo::class])
@LooperMode(LooperMode.Mode.PAUSED)
class SymbolInkTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var detector: KeyDetector
    private lateinit var listener: KeyboardActionListener
    private lateinit var drawing: DrawingProxy
    private lateinit var timer: TimerHandler
    private lateinit var letter: Key
    private lateinit var space: Key
    private lateinit var delete: Key
    private var now = 10_000L
    private val log = mutableListOf<String>()
    private val drawings = mutableListOf<List<InkStroke>>()
    private val waiting = mutableListOf<Int>()
    private val sink = object : SymbolInk.Sink {
        override fun onDrawing(strokes: List<InkStroke>) { drawings += strokes; log += "symbol" }
        override fun onWaiting(strokes: List<InkStroke>, possibleDots: List<Int>) { waiting += strokes.size }
    }
    private val recognizer = InkRecognizer(InkSymbols.templates)

    private fun key(code: Int) = mock(Key::class.java).also {
        `when`(it.code).thenReturn(code)
        `when`(it.isEnabled).thenReturn(true)
        `when`(it.isLongPressEnabled).thenReturn(true)
    }

    private fun setDrawing(on: Boolean) {
        app.prefs().edit().putBoolean(AgentPreferences.SYMBOL_DRAWING, on).commit()
        Settings.getInstance().loadSettings(app)
    }

    @Before fun setup() {
        app.prefs().edit().clear().commit()
        setDrawing(true)
        helium314.keyboard.accessibility.AccessibilityUtils.init(app) // gesture detection asks for touch exploration
        PointerTracker.setGestureHandlingEnabledByUser(true)
        PointerTracker.setMainDictionaryAvailability(true)
        drawing = mock(DrawingProxy::class.java)
        timer = TimerHandler(drawing, 0, 100)
        val attrs = app.obtainStyledAttributes(R.styleable.MainKeyboardView)
        PointerTracker.init(attrs, timer, drawing)
        attrs.recycle()
        letter = key('a'.code)
        space = key(32)
        delete = key(KeyCode.DELETE)
        `when`(delete.isRepeatable).thenReturn(true)
        val keyboard = Keyboard(KeyboardParams().apply {
            GRID_WIDTH = 1; GRID_HEIGHT = 1; mMostCommonKeyWidth = 50; mMostCommonKeyHeight = 50
            mOccupiedHeight = 200; mOccupiedWidth = 360
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.ALPHABET)
        })
        detector = mock(KeyDetector::class.java)
        `when`(detector.keyboard).thenReturn(keyboard)
        `when`(detector.detectHitKey(anyInt(), anyInt())).thenAnswer {
            val x = it.getArgument<Int>(0); val y = it.getArgument<Int>(1)
            if (y < 150) letter else if (x < 200) space else delete
        }
        PointerTracker.setKeyDetector(detector) // not a password field: gesture detection allowed
        listener = mock(KeyboardActionListener::class.java)
        // like KeyboardActionListenerImpl: other keys first finish a waiting symbol; batches are ink
        `when`(listener.onPressKey(anyInt(), anyInt(), anyInt(), any())).thenAnswer { SymbolDrawing.beforeOtherInput() }
        `when`(listener.onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())).thenAnswer { log += "code ${it.getArgument<Int>(0)}" }
        `when`(listener.onStartBatchInput()).thenAnswer { if (SymbolInk.isEnabled()) SymbolInk.onBatchStart() else log += "word start" }
        `when`(listener.onEndBatchInput(any())).thenAnswer {
            if (SymbolInk.isEnabled()) SymbolInk.onBatchEnd(it.getArgument<InputPointers>(0)) else log += "word end"
        }
        `when`(listener.onCancelBatchInput()).thenAnswer { if (SymbolInk.isEnabled()) SymbolInk.onBatchCancel() }
        `when`(listener.onSpaceSwipe(any(SpaceGesture::class.java), anyInt())).thenReturn(true)
        PointerTracker.setKeyboardActionListener(listener)
        SymbolInk.cancel()
        SymbolInk.resetPace()
        SymbolInk.sink = sink
    }

    @After fun cleanup() {
        if (::detector.isInitialized) touch(MotionEvent.ACTION_CANCEL)
        timer.cancelAllMessages()
        SymbolInk.cancel()
        SymbolInk.sink = null
        JniUtils.sHaveGestureLib = false
        app.prefs().edit().clear().commit()
        Settings.getInstance().loadSettings(app)
    }

    private fun touch(action: Int, x: Float = 100f, y: Float = 100f, pointer: Int = 0) {
        val event = MotionEvent.obtain(now, now + 5, action, x, y, 0)
        try { PointerTracker.getPointerTracker(pointer).processMotionEvent(event, detector) } finally { event.recycle() }
        now += 10
    }

    /** A stroke along [points] (x, y pairs), one move every 10 ms. */
    private fun stroke(vararg points: Float) {
        touch(MotionEvent.ACTION_DOWN, points[0], points[1])
        for (i in 2 until points.size step 2) touch(MotionEvent.ACTION_MOVE, points[i], points[i + 1])
        touch(MotionEvent.ACTION_UP, points[points.size - 2], points[points.size - 1])
    }

    /** A line with a move every 20 px, so the gesture detection sees a real swipe. */
    private fun line(x1: Float, y1: Float, x2: Float, y2: Float): FloatArray {
        val steps = 12
        return FloatArray(2 * (steps + 1)) { i -> val t = (i / 2).toFloat() / steps; if (i % 2 == 0) x1 + t * (x2 - x1) else y1 + t * (y2 - y1) }
    }

    // stage 36: the pause is 0.6–1.5 s now (was 0.5 s); waiting the longest one ends any drawing
    private fun pause() = Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SymbolInk.MAX_PAUSE_MILLIS + 100))
    private fun idle(millis: Long) = Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    private fun hold() = Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis((Settings.getValues().mKeyLongpressTimeout * 2).toLong()))
    private fun noLetterTyped() = verify(listener, never()).onCodeInput(eq('a'.code), anyInt(), anyInt(), anyBoolean())

    @Test fun swipeAcrossLettersIsInkAndTwoStrokesMakeAnArrow() {
        stroke(*line(20f, 80f, 300f, 80f))
        verify(listener).onStartBatchInput()
        verify(listener).onEndBatchInput(any())
        assertTrue(SymbolInk.isWaiting)
        assertTrue(drawings.isEmpty()) // waits for a further stroke
        // the head: a short stroke right away is ink without any threshold
        stroke(260f, 40f, 280f, 60f, 300f, 80f, 280f, 100f, 260f, 120f)
        verify(listener, times(1)).onStartBatchInput() // no second gesture
        pause()
        assertEquals(1, drawings.size)
        assertEquals(2, drawings[0].size)
        assertEquals("→", recognizer.recognize(drawings[0]).best?.text)
        noLetterTyped()
        assertFalse(SymbolInk.isWaiting)
        // afterwards a tap types its letter again
        touch(MotionEvent.ACTION_DOWN, 50f, 50f); touch(MotionEvent.ACTION_UP, 50f, 50f)
        verify(listener).onCodeInput(eq('a'.code), anyInt(), anyInt(), eq(false))
    }

    @Test fun tapsTypeLettersAndStartNoInk() {
        touch(MotionEvent.ACTION_DOWN, 50f, 50f); touch(MotionEvent.ACTION_UP, 50f, 50f)
        touch(MotionEvent.ACTION_DOWN, 120f, 60f); touch(MotionEvent.ACTION_UP, 120f, 60f)
        verify(listener, times(2)).onCodeInput(eq('a'.code), anyInt(), anyInt(), eq(false))
        verify(listener, never()).onStartBatchInput()
        assertFalse(SymbolInk.isWaiting)
        pause()
        assertTrue(drawings.isEmpty())
    }

    @Test fun dotsWhileWaitingBelongToTheSymbol() {
        stroke(*line(20f, 80f, 300f, 80f))
        touch(MotionEvent.ACTION_DOWN, 160f, 30f); touch(MotionEvent.ACTION_UP, 160f, 30f)
        touch(MotionEvent.ACTION_DOWN, 161f, 130f); touch(MotionEvent.ACTION_UP, 161f, 130f)
        noLetterTyped()
        pause()
        assertEquals(3, drawings.single().size)
        assertEquals("÷", recognizer.recognize(drawings.single()).best?.text)
    }

    @Test fun anotherKeyFirstFinishesTheWaitingSymbol() {
        stroke(*line(20f, 80f, 300f, 80f))
        touch(MotionEvent.ACTION_DOWN, 100f, 180f) // space: not ink, the symbol comes first
        assertEquals(listOf("symbol"), log)
        touch(MotionEvent.ACTION_UP, 100f, 180f)
        assertEquals(listOf("symbol", "code 32"), log)
        assertEquals("–", recognizer.recognize(drawings.single()).best?.text)
    }

    @Test fun longPressPopupStaysAsItIs() {
        `when`(letter.popupKeys).thenReturn(arrayOf(PopupKeySpec("á", false, Locale.ROOT)))
        touch(MotionEvent.ACTION_DOWN, 50f, 50f)
        hold()
        verify(drawing).showPopupKeysKeyboard(eq(letter), any())
        verify(listener, never()).onStartBatchInput()
        assertFalse(SymbolInk.isWaiting)
    }

    @Test fun spaceAndDeleteSwipesStayAsTheyAre() {
        touch(MotionEvent.ACTION_DOWN, 60f, 180f); touch(MotionEvent.ACTION_MOVE, 160f, 180f); touch(MotionEvent.ACTION_UP, 160f, 180f)
        verify(listener, atLeastOnce()).onSpaceSwipe(eq(SpaceGesture.SWIPE_RIGHT), anyInt())
        touch(MotionEvent.ACTION_DOWN, 340f, 180f); touch(MotionEvent.ACTION_MOVE, 240f, 180f); touch(MotionEvent.ACTION_UP, 240f, 180f)
        verify(listener, atLeastOnce()).onMoveDeletePointer(intThat { it < 0 })
        verify(listener, never()).onStartBatchInput()
        assertFalse(SymbolInk.isWaiting)
        pause()
        assertTrue(drawings.isEmpty())
    }

    @Test fun secondFingerDuringAStrokeWritesTheDrawingAndIsHandledAsUsual() {
        stroke(*line(20f, 80f, 300f, 80f))
        touch(MotionEvent.ACTION_DOWN, 100f, 40f) // continuation stroke of pointer 0
        // stage 31 fix: a real stroke (farther than the tap slop); a mere tap would be a key (two thumbs)
        touch(MotionEvent.ACTION_MOVE, 115f, 55f)
        assertTrue(SymbolInk.isDrawing())
        val properties = Array(2) { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(2) { MotionEvent.PointerCoords().apply { x = 100f + 50 * it; y = 40f; pressure = 1f; size = 1f } }
        fun second(action: Int) {
            val packet = MotionEvent.obtain(now, now + 5, action or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { PointerTracker.getPointerTracker(1).processMotionEvent(packet, detector) } finally { packet.recycle() }
            now += 10
        }
        second(MotionEvent.ACTION_POINTER_DOWN)
        assertFalse(SymbolInk.isWaiting)
        // review M1: the drawing with the stroke so far is written (before: dropped, nothing came out)
        assertEquals(listOf("symbol"), log)
        assertEquals(2, drawings.single().size)
        second(MotionEvent.ACTION_POINTER_UP)
        verify(listener, times(1)).onCodeInput(eq('a'.code), anyInt(), anyInt(), anyBoolean()) // the second finger typed
        touch(MotionEvent.ACTION_MOVE, 120f, 60f); touch(MotionEvent.ACTION_UP, 120f, 60f) // the first finger ends quietly
        pause()
        assertEquals(1, drawings.size)
        assertEquals(listOf("symbol", "code ${'a'.code}"), log)
        verify(listener, times(1)).onCodeInput(anyInt(), anyInt(), anyInt(), anyBoolean())
    }

    @Test fun cancelledTouchDropsTheDrawing() {
        stroke(*line(20f, 80f, 300f, 80f))
        touch(MotionEvent.ACTION_DOWN, 100f, 40f)
        touch(MotionEvent.ACTION_CANCEL, 100f, 40f)
        pause()
        assertTrue(drawings.isEmpty())
        assertFalse(SymbolInk.isWaiting)
        assertFalse(SymbolInk.isDrawing())
    }

    @Test fun settingOffKeepsGestureTyping() {
        setDrawing(false)
        stroke(*line(20f, 80f, 300f, 80f))
        assertEquals(listOf("word start", "word end"), log)
        // no waiting symbol: the next tap is a letter
        touch(MotionEvent.ACTION_DOWN, 50f, 50f); touch(MotionEvent.ACTION_UP, 50f, 50f)
        verify(listener).onCodeInput(eq('a'.code), anyInt(), anyInt(), eq(false))
        pause()
        assertTrue(drawings.isEmpty())
    }

    @Test fun settingTurnsGestureTypingOff() {
        JniUtils.sHaveGestureLib = true
        app.prefs().edit().putBoolean(Settings.PREF_GESTURE_INPUT, true).commit()
        setDrawing(false)
        assertTrue(Settings.getValues().mGestureInputEnabled)
        assertFalse(Settings.getValues().mSymbolDrawing)
        setDrawing(true)
        assertFalse(Settings.getValues().mGestureInputEnabled)
        assertTrue(Settings.getValues().mSymbolDrawing)
    }

    @Test fun passwordFieldGetsNoInk() {
        val password = Keyboard(KeyboardParams().apply {
            GRID_WIDTH = 1; GRID_HEIGHT = 1; mMostCommonKeyWidth = 50; mMostCommonKeyHeight = 50
            mOccupiedHeight = 200; mOccupiedWidth = 360
            mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardElement.ALPHABET).copy(
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        })
        val normal = detector.keyboard
        `when`(detector.keyboard).thenReturn(password)
        PointerTracker.setKeyDetector(detector)
        try {
            stroke(*line(20f, 80f, 300f, 80f))
            verify(listener, never()).onStartBatchInput()
            assertFalse(SymbolInk.isWaiting)
            pause()
            assertTrue(drawings.isEmpty())
        } finally { // PointerTracker keeps the password mode statically
            `when`(detector.keyboard).thenReturn(normal)
            PointerTracker.setKeyDetector(detector)
        }
    }

    @Test fun tooManyStrokesAreNoSymbol() {
        stroke(*line(20f, 80f, 300f, 80f))
        // short strokes, not taps: since stage 31 a tap that is no dot types its letter
        repeat(12) { touch(MotionEvent.ACTION_DOWN, 30f + 20 * it, 40f); touch(MotionEvent.ACTION_MOVE, 30f + 20 * it, 70f); touch(MotionEvent.ACTION_UP, 30f + 20 * it, 70f) }
        pause()
        assertTrue(drawings.isEmpty())
        noLetterTyped()
    }

    // stage 31: a letter tapped in the pause is no longer lost

    private fun tap(x: Float, y: Float) { touch(MotionEvent.ACTION_DOWN, x, y); touch(MotionEvent.ACTION_UP, x, y) }

    @Test fun aTapInThePauseWritesTheSymbolFirstAndThenTypesItsLetter() {
        stroke(*line(20f, 80f, 300f, 80f))
        assertTrue(SymbolInk.isWaiting)
        clearInvocations(listener) // the stroke's own key press
        // far from where ÷ has its dots (stage 43: was 40, 30, above the left end, which may now wait as a
        // possible dot: aTapNearADotOfASymbolWaitsAndIsTypedAfterIt)
        tap(330f, 140f)
        assertEquals(listOf("symbol", "code ${'a'.code}"), log)
        verify(listener, times(1)).onPressKey(eq('a'.code), anyInt(), anyInt(), any()) // pressed like a tap (feedback)
        verify(listener, times(1)).onReleaseKey(eq('a'.code), eq(false))
        assertEquals("–", recognizer.recognize(drawings.single()).best?.text) // the tap is not part of it
        assertFalse(SymbolInk.isWaiting)
        // the next tap is an ordinary tap, and nothing else comes after the pause
        tap(60f, 30f)
        verify(listener, times(2)).onCodeInput(eq('a'.code), anyInt(), anyInt(), eq(false))
        pause()
        assertEquals(1, drawings.size)
    }

    @Test fun aTapAfterAFinishedArrowTypesItsLetter() {
        stroke(*line(20f, 80f, 300f, 80f))
        stroke(260f, 40f, 280f, 60f, 300f, 80f, 280f, 100f, 260f, 120f)
        tap(160f, 130f)
        assertEquals(listOf("symbol", "code ${'a'.code}"), log)
        assertEquals("→", recognizer.recognize(drawings.single()).best?.text)
    }

    @Test fun dotsCountOnlyForSymbolsWithDots() {
        // the same tap as the first dot of ÷, but without any symbol that has dots: a letter
        SymbolInk.dotCheck = SymbolInk.DotCheck { strokes, x, y ->
            // stage 43: more built-in symbols have dots; before only ÷ was left out
            dev.agentkeyboard.core.InkDots(InkSymbols.templates.filter { it.cloud.dotCount == 0 }).isDot(strokes, x, y) }
        try {
            stroke(*line(20f, 80f, 300f, 80f))
            tap(160f, 30f)
            assertEquals(listOf("symbol", "code ${'a'.code}"), log)
        } finally {
            SymbolInk.dotCheck = SymbolInk.builtInDotCheck
        }
    }

    @Test fun aDroppedStrokeDrawsNoMoreTrail() {
        stroke(*line(20f, 80f, 300f, 80f))
        touch(MotionEvent.ACTION_DOWN, 100f, 40f) // continuation stroke of pointer 0
        touch(MotionEvent.ACTION_MOVE, 110f, 50f)
        val first = PointerTracker.getPointerTracker(0)
        verify(drawing, atLeastOnce()).showGestureTrail(eq(first), anyBoolean())
        val properties = Array(2) { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(2) { MotionEvent.PointerCoords().apply { x = 110f + 50 * it; y = 50f; pressure = 1f; size = 1f } }
        val packet = MotionEvent.obtain(now, now + 5, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { PointerTracker.getPointerTracker(1).processMotionEvent(packet, detector) } finally { packet.recycle() }
        now += 10
        assertFalse(SymbolInk.isWaiting)
        clearInvocations(drawing)
        touch(MotionEvent.ACTION_MOVE, 140f, 70f); touch(MotionEvent.ACTION_MOVE, 170f, 90f) // the first finger goes on
        verify(drawing, never()).showGestureTrail(eq(first), anyBoolean())
    }

    // stage 31 fix (review M1, N1): two thumbs in the pause, and a tap that slides a little

    /** One packet with all [fingers] (pointer id, x, y) down; [action] concerns the finger at [index]. */
    private fun fingers(action: Int, index: Int, vararg fingers: Triple<Int, Float, Float>) {
        val properties = Array(fingers.size) { i -> MotionEvent.PointerProperties().apply { id = fingers[i].first; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(fingers.size) { i -> MotionEvent.PointerCoords().apply { x = fingers[i].second; y = fingers[i].third; pressure = 1f; size = 1f } }
        val packet = MotionEvent.obtain(now, now + 5, action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            fingers.size, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { PointerTracker.getPointerTracker(fingers[index].first).processMotionEvent(packet, detector) } finally { packet.recycle() }
        now += 10
    }

    /** Letters left of x = 200 are "a", right of it "b" (only in these tests). */
    private fun twoLetters() {
        val b = key('b'.code)
        `when`(detector.detectHitKey(anyInt(), anyInt())).thenAnswer {
            val x = it.getArgument<Int>(0); val y = it.getArgument<Int>(1)
            if (y < 150) (if (x < 200) letter else b) else if (x < 200) space else delete
        }
    }

    @Test fun twoThumbsInThePauseTypeBothLettersAfterTheSymbol() {
        twoLetters()
        stroke(*line(20f, 80f, 300f, 80f))
        assertTrue(SymbolInk.isWaiting)
        clearInvocations(listener) // the stroke's own key press
        val a = Triple(0, 30f, 130f); val b = Triple(1, 330f, 30f) // both far from where ÷ has its dots
        touch(MotionEvent.ACTION_DOWN, a.second, a.third) // thumb A: a tap so far, held as ink
        assertTrue(SymbolInk.isDrawing())
        fingers(MotionEvent.ACTION_POINTER_DOWN, 1, a, b) // thumb B before A lifts
        assertEquals(listOf("symbol"), log) // the symbol is written at once
        assertFalse(SymbolInk.isWaiting)
        fingers(MotionEvent.ACTION_POINTER_UP, 0, a, b) // A lifts first
        assertEquals(listOf("symbol", "code ${'a'.code}"), log)
        fingers(MotionEvent.ACTION_UP, 0, b) // then B
        assertEquals(listOf("symbol", "code ${'a'.code}", "code ${'b'.code}"), log)
        verify(listener, times(1)).onPressKey(eq('a'.code), anyInt(), anyInt(), any()) // A pressed once, like a tap
        assertEquals("–", recognizer.recognize(drawings.single()).best?.text) // A is not part of it
        pause()
        assertEquals(1, drawings.size)
    }

    @Test fun twoThumbsInThePauseKeepTheirOrderWhenTheSecondLiftsFirst() {
        twoLetters()
        stroke(*line(20f, 80f, 300f, 80f))
        val a = Triple(0, 30f, 130f); val b = Triple(1, 330f, 30f)
        touch(MotionEvent.ACTION_DOWN, a.second, a.third)
        fingers(MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
        fingers(MotionEvent.ACTION_POINTER_UP, 1, a, b) // B lifts first: A, the older key, is typed before it
        assertEquals(listOf("symbol", "code ${'a'.code}", "code ${'b'.code}"), log)
        fingers(MotionEvent.ACTION_UP, 0, a) // A ends quietly, it was already typed
        assertEquals(listOf("symbol", "code ${'a'.code}", "code ${'b'.code}"), log)
    }

    @Test fun aTapThatSlidesALittleTypesTheKeyWhereItTouchedDown() {
        stroke(*line(20f, 80f, 300f, 80f))
        clearInvocations(listener)
        // down on a letter just above delete, up 3 px lower on delete: still a tap (stage 36: below 4 dp;
        // before, 8 px within the 10 dp slop)
        touch(MotionEvent.ACTION_DOWN, 330f, 148f); touch(MotionEvent.ACTION_MOVE, 330f, 150f); touch(MotionEvent.ACTION_UP, 330f, 151f)
        assertEquals(listOf("symbol", "code ${'a'.code}"), log)
        verify(listener, never()).onPressKey(eq(KeyCode.DELETE), anyInt(), anyInt(), any())
        verify(listener, never()).onCodeInput(eq(KeyCode.DELETE), anyInt(), anyInt(), anyBoolean())
    }

    // stage 36: grouping of strokes (owner feedback on build 14) and the state machine

    @Test fun aShortStrokeInThePauseIsAStrokeNotATap() {
        stroke(*line(20f, 80f, 300f, 80f))
        clearInvocations(listener)
        stroke(40f, 30f, 45f, 30f, 49f, 30f) // 9 px, far from the dots of ÷: a tap with the old 10 dp, a stroke now (8 dp)
        assertTrue(SymbolInk.isWaiting)
        assertTrue(log.isEmpty())
        pause()
        assertEquals(2, drawings.single().size)
        noLetterTyped()
    }

    @Test fun aVeryFastStrokeWithoutMovesIsAStroke() {
        stroke(*line(20f, 80f, 300f, 80f))
        touch(MotionEvent.ACTION_DOWN, 100f, 40f); touch(MotionEvent.ACTION_UP, 130f, 40f) // down and up only
        assertTrue(SymbolInk.isWaiting)
        pause()
        assertEquals(2, drawings.single().size)
        noLetterTyped()
    }

    @Test fun aStrokeAfterMoreThanHalfASecondStillBelongsToTheSymbol() {
        stroke(*line(20f, 80f, 300f, 80f))
        idle(800) // the old pause was 500 ms: the head started a new symbol
        assertTrue(SymbolInk.isWaiting)
        stroke(260f, 40f, 280f, 60f, 300f, 80f, 280f, 100f, 260f, 120f)
        pause()
        assertEquals("→", recognizer.recognize(drawings.single()).best?.text)
        verify(listener, times(1)).onStartBatchInput()
    }

    @Test fun thePauseFollowsThePaceOfTheStrokes() {
        assertEquals(900, SymbolInk.pauseMillis)
        stroke(*line(20f, 80f, 300f, 80f))
        repeat(3) { i ->
            idle(100) // quick hands: 100 ms between two strokes
            stroke(40f + 60 * i, 20f, 70f + 60 * i, 40f)
        }
        assertEquals(600, SymbolInk.pauseMillis) // 2.5 × 100 ms, but never below 600 ms
        idle(650)
        assertEquals(1, drawings.size) // the drawing ended after the shorter pause
        assertEquals(4, drawings.single().size)
    }

    @Test fun lettersTypedInThePauseDoNotSetThePace() {
        repeat(3) {
            now += 1_000 // no fast typing before the stroke (HeliBoard would then ask for a longer swipe)
            stroke(*line(20f, 80f, 300f, 80f))
            idle(100)
            tap(330f, 140f) // a letter right after the symbol: no gap between two strokes (stage 43: far from the dots of ÷)
        }
        assertEquals(3, drawings.size)
        assertEquals(900, SymbolInk.pauseMillis)
    }

    @Test fun aTapHeldStillIsALetterNotADot() {
        stroke(*line(20f, 80f, 300f, 80f))
        touch(MotionEvent.ACTION_DOWN, 40f, 30f)
        hold() // no long-press popup in the pause, and no stray dot in the drawing
        touch(MotionEvent.ACTION_UP, 40f, 30f)
        pause() // stage 43: near where ÷ could have a dot it waits as a possible dot, then it is its letter
        assertEquals(listOf("symbol", "code ${'a'.code}"), log)
        assertEquals(1, drawings.single().size)
    }

    /** "." for x >= 300 above y = 150 (only in these tests). */
    private fun periodKey(): Key {
        val period = key('.'.code)
        `when`(detector.detectHitKey(anyInt(), anyInt())).thenAnswer {
            val x = it.getArgument<Int>(0); val y = it.getArgument<Int>(1)
            if (y < 150) (if (x >= 300) period else letter) else if (x < 200) space else delete
        }
        return period
    }

    @Test fun aStrokeMayStartOnThePeriodKeyAndATapThereTypesIt() {
        periodKey()
        stroke(*line(20f, 80f, 280f, 80f))
        clearInvocations(listener)
        stroke(*line(320f, 40f, 200f, 120f)) // starts on "." (before: wrote "–" and began a new symbol)
        assertTrue(SymbolInk.isWaiting)
        verify(listener, never()).onCodeInput(eq('.'.code), anyInt(), anyInt(), anyBoolean())
        verify(listener, never()).onStartBatchInput()
        tap(330f, 30f) // a tap on "." in the pause: the symbol, then the period
        assertEquals(listOf("symbol", "code ${'.'.code}"), log)
        assertEquals(2, drawings.single().size)
    }

    @Test fun theWaitingDrawingIsShownAfterEveryStrokeAndDroppedWhenCancelled() {
        stroke(*line(20f, 80f, 300f, 80f))
        assertTrue(waiting.isEmpty()) // review M2: only after a short quiet time
        idle(SymbolInk.PREVIEW_DELAY_MILLIS + 10)
        stroke(260f, 40f, 280f, 60f, 300f, 80f, 280f, 100f, 260f, 120f)
        idle(SymbolInk.PREVIEW_DELAY_MILLIS + 10)
        assertEquals(listOf(1, 2), waiting)
        touch(MotionEvent.ACTION_DOWN, 100f, 40f)
        touch(MotionEvent.ACTION_CANCEL, 100f, 40f)
        assertEquals(listOf(1, 2, 0), waiting) // the preview goes with the drawing
        pause()
        assertTrue(drawings.isEmpty())
    }

    @Test fun aCancelThatReachesAnotherTrackerEndsTheInkStroke() {
        // review of 057a6d5 (N-a): ACTION_CANCEL reaches only one tracker; an ink stroke of another
        // pointer stayed "drawing", and the next touch made it a key that never got its up (a ghost letter)
        stroke(*line(20f, 80f, 300f, 80f))
        val ink = Triple(1, 40f, 30f)
        fingers(MotionEvent.ACTION_DOWN, 0, ink) // a continuation stroke of pointer 1, a tap so far
        assertEquals(1, SymbolInk.drawingPointer)
        fingers(MotionEvent.ACTION_CANCEL, 0, Triple(0, 60f, 60f), ink) // the cancel goes to pointer 0's tracker
        assertFalse(SymbolInk.isDrawing())
        assertFalse(SymbolInk.isWaiting)
        clearInvocations(listener)
        tap(60f, 30f)
        pause()
        tap(80f, 30f)
        verify(listener, times(2)).onPressKey(eq('a'.code), anyInt(), anyInt(), any())
        verify(listener, times(2)).onCodeInput(eq('a'.code), anyInt(), anyInt(), anyBoolean())
        assertTrue(drawings.isEmpty())
    }

    @Test fun strokesInQuickSuccessionGetOnePreview() {
        // review M2: no matching on the main thread for every stroke while the hand is still drawing
        stroke(*line(20f, 80f, 300f, 80f))
        idle(60) // the main thread is free between two strokes, as on a phone
        stroke(260f, 40f, 280f, 60f, 300f, 80f, 280f, 100f, 260f, 120f)
        idle(60)
        stroke(40f, 30f, 60f, 30f)
        assertTrue(waiting.isEmpty())
        idle(SymbolInk.PREVIEW_DELAY_MILLIS + 10)
        assertEquals(listOf(3), waiting)
    }

    // review H1: typing right after a symbol, away from the drawing, is typed

    /** Above y = 150: "3" for 250 <= x < 300, "." from x = 300, else the letter (only in these tests). */
    private fun digitAndPeriod() {
        val three = key('3'.code); val period = key('.'.code)
        `when`(detector.detectHitKey(anyInt(), anyInt())).thenAnswer {
            val x = it.getArgument<Int>(0); val y = it.getArgument<Int>(1)
            if (y < 150) (if (x >= 300) period else if (x >= 250) three else letter) else if (x < 200) space else delete
        }
    }

    @Test fun digitsAndPunctuationTypedAwayFromTheDrawingAreTyped() {
        digitAndPeriod()
        stroke(*line(20f, 40f, 120f, 40f)) // a small dash: its area reaches 3 key widths (150 px) around it
        clearInvocations(listener)
        // a rolling finger (7 px) on "3" far from the dash (the area: 3 keys = 150 px around it): no stroke
        // of the drawing, the symbol and then 3
        touch(MotionEvent.ACTION_DOWN, 285f, 120f); touch(MotionEvent.ACTION_MOVE, 290f, 125f); touch(MotionEvent.ACTION_UP, 290f, 125f)
        assertEquals(listOf("symbol", "code ${'3'.code}"), log)
        assertEquals(1, drawings.single().size)
        now += 1_000 // no fast typing before the next stroke (HeliBoard would then ask for a longer swipe)
        stroke(*line(20f, 40f, 120f, 40f))
        touch(MotionEvent.ACTION_DOWN, 330f, 40f); touch(MotionEvent.ACTION_MOVE, 336f, 44f); touch(MotionEvent.ACTION_UP, 336f, 44f)
        assertEquals(listOf("symbol", "code ${'3'.code}", "symbol", "code ${'.'.code}"), log)
        pause()
        assertEquals(2, drawings.size)
    }

    @Test fun aShortStrokeNearTheDrawingStillJoinsAndARollingTapThereIsALetter() {
        digitAndPeriod()
        stroke(*line(20f, 40f, 120f, 40f))
        stroke(60f, 75f, 65f, 75f, 70f, 75f) // 10 px inside the area: a further stroke
        assertTrue(SymbolInk.isWaiting)
        assertTrue(log.isEmpty())
        // a tap rolling 7 px inside the area: below 8 dp, a tap (a letter, no dot of a dash there;
        // follow-up review F3, with 6 dp it was a stroke)
        touch(MotionEvent.ACTION_DOWN, 150f, 90f); touch(MotionEvent.ACTION_MOVE, 155f, 95f); touch(MotionEvent.ACTION_UP, 155f, 95f)
        assertEquals(listOf("symbol", "code ${'a'.code}"), log)
        assertEquals(2, drawings.single().size)
    }

    @Test fun aSecondThumbAfterASlightlyMovedTapStillTypesBoth() {
        // review M1: the first thumb moved 7 px (more than the 6 dp tap rule) before the second came
        twoLetters()
        stroke(*line(20f, 80f, 300f, 80f))
        clearInvocations(listener)
        touch(MotionEvent.ACTION_DOWN, 30f, 130f)
        touch(MotionEvent.ACTION_MOVE, 36f, 134f)
        val a = Triple(0, 36f, 134f); val b = Triple(1, 330f, 30f)
        fingers(MotionEvent.ACTION_POINTER_DOWN, 1, a, b)
        assertEquals(listOf("symbol"), log)
        fingers(MotionEvent.ACTION_POINTER_UP, 0, a, b)
        fingers(MotionEvent.ACTION_UP, 0, b)
        assertEquals(listOf("symbol", "code ${'a'.code}", "code ${'b'.code}"), log)
        assertEquals(1, drawings.single().size) // the tap is not part of it
    }

    // stage 43 (owner feedback on build 19): dots of ÷, %, ! and ?, and drawing right after typing

    @Test fun aTapNearADotOfASymbolWaitsAndIsTypedAfterIt() {
        stroke(*line(20f, 80f, 300f, 80f))
        clearInvocations(listener)
        tap(40f, 30f) // above the left end: a possible dot of ÷, nothing is typed yet
        assertTrue(log.isEmpty())
        assertTrue(SymbolInk.isWaiting)
        pause()
        // one dot is no ÷: the dash, then the letter where it touched down
        assertEquals(listOf("symbol", "code ${'a'.code}"), log)
        assertEquals(1, drawings.single().size)
        assertEquals("–", recognizer.recognize(drawings.single()).best?.text)
        verify(listener, times(1)).onPressKey(eq('a'.code), anyInt(), anyInt(), any())
        verify(listener, times(1)).onReleaseKey(eq('a'.code), eq(false))
    }

    @Test fun aSingleTapAboveTheMiddleOfADashIsItsLetterAfterIt() {
        stroke(*line(20f, 80f, 300f, 80f))
        tap(160f, 30f) // where ÷ has a dot, but no second one comes
        pause()
        assertEquals(listOf("symbol", "code ${'a'.code}"), log)
        assertEquals("–", recognizer.recognize(drawings.single()).best?.text)
    }

    @Test fun possibleDotsAndALaterTapKeepTheirOrder() {
        twoLetters()
        stroke(*line(20f, 80f, 300f, 80f))
        tap(40f, 30f) // "a", a possible dot
        tap(330f, 140f) // "b", far from any dot: the drawing is written, then a, then b
        assertEquals(listOf("symbol", "code ${'a'.code}", "code ${'b'.code}"), log)
        assertEquals("–", recognizer.recognize(drawings.single()).best?.text)
    }

    @Test fun divisionSignWithTheLowerDotFirst() {
        stroke(*line(20f, 80f, 300f, 80f))
        tap(158f, 132f)
        tap(162f, 28f)
        noLetterTyped()
        pause()
        assertEquals("÷", recognizer.recognize(drawings.single()).best?.text)
        assertTrue(log == listOf("symbol"))
    }

    @Test fun aDivisionSignWithDotsBesideTheMiddleIsStillOne() {
        // stage 31 asked for 0.12 of the size to the side: these dots (0.14 and 0.16) were letters
        stroke(*line(20f, 80f, 300f, 80f))
        tap(200f, 25f)
        tap(115f, 135f)
        pause()
        assertEquals(listOf("symbol"), log)
        assertEquals("÷", recognizer.recognize(drawings.single()).best?.text)
    }

    /** % 140 px tall: the slash, then its circles at the top left and the bottom right. */
    private val slash = line(219f, 5f, 121f, 145f)

    @Test fun percentWithTappedDots() {
        stroke(*slash)
        tap(125f, 30f)
        tap(215f, 120f)
        noLetterTyped()
        pause()
        assertEquals(listOf("symbol"), log)
        assertEquals("%", recognizer.recognize(drawings.single()).best?.text)
    }

    @Test fun percentWithATinyCircleAndADot() {
        stroke(*slash)
        // a circle of 5 px around (125, 30): more than a tap, a dot of the drawing by its size
        stroke(130f, 30f, 128f, 34f, 125f, 35f, 121f, 34f, 120f, 30f, 121f, 26f, 125f, 25f, 129f, 26f, 130f, 30f)
        tap(215f, 120f)
        noLetterTyped()
        pause()
        assertEquals("%", recognizer.recognize(drawings.single()).best?.text)
    }

    @Test fun exclamationAndQuestionMarks() {
        stroke(*line(150f, 5f, 150f, 105f))
        tap(151f, 140f)
        pause()
        assertEquals("!", recognizer.recognize(drawings.last()).best?.text)
        now += 1_000
        // the hook of ? (a hand-drawn arc over the top, then down), then its dot
        stroke(110f, 30f, 115f, 15f, 130f, 6f, 150f, 5f, 170f, 10f, 182f, 25f, 178f, 45f, 162f, 60f, 151f, 72f, 150f, 88f, 150f, 100f)
        tap(150f, 140f)
        pause()
        assertEquals("?", recognizer.recognize(drawings.last()).best?.text)
        noLetterTyped()
        assertEquals(listOf("symbol", "symbol"), log)
    }

    /** Types a word on "a" (three taps 60 ms apart), as fast typing does before a drawing. */
    private fun typeAWord() {
        repeat(3) { tap(60f + 30 * it, 40f); now += 40 }
    }

    @Test fun drawingRightAfterTypingAWordStartsInk() {
        for (delay in listOf(100L, 200L, 400L)) {
            now += 2_000
            typeAWord()
            clearInvocations(listener)
            now += delay - 40
            stroke(*line(60f, 80f, 200f, 80f)) // the line of ÷, 140 px = 2.8 keys in 120 ms
            verify(listener, times(1)).onStartBatchInput()
            noLetterTyped()
            assertTrue(SymbolInk.isWaiting, "after $delay ms")
            pause()
        }
        assertEquals(3, drawings.size)
    }

    @Test fun aSlidingTapWhileTypingFastStaysTyping() {
        typeAWord()
        clearInvocations(listener)
        now += 20
        // 0.7 keys (35 px) in 30 ms: HeliBoard's usual swipe start (0.35 keys from where it got fast) takes
        // it, so right after a letter drawn symbols ask for 0.75 keys from the down point
        touch(MotionEvent.ACTION_DOWN, 100f, 60f); touch(MotionEvent.ACTION_MOVE, 112f, 60f); touch(MotionEvent.ACTION_MOVE, 125f, 60f)
        touch(MotionEvent.ACTION_MOVE, 135f, 60f); touch(MotionEvent.ACTION_UP, 135f, 60f)
        verify(listener, never()).onStartBatchInput()
        verify(listener, times(1)).onCodeInput(eq('a'.code), anyInt(), anyInt(), anyBoolean())
        assertFalse(SymbolInk.isWaiting)
        // the same slide well after typing is a stroke, as before stage 43
        now += 2_000
        touch(MotionEvent.ACTION_DOWN, 100f, 60f); touch(MotionEvent.ACTION_MOVE, 112f, 60f); touch(MotionEvent.ACTION_MOVE, 125f, 60f)
        touch(MotionEvent.ACTION_MOVE, 135f, 60f); touch(MotionEvent.ACTION_UP, 135f, 60f)
        verify(listener, times(1)).onStartBatchInput()
    }

    @Test fun withoutDrawnSymbolsTheFastTypingCooldownStays() {
        setDrawing(false)
        typeAWord()
        clearInvocations(listener)
        now += 100
        stroke(*line(60f, 80f, 200f, 80f)) // too short for HeliBoard right after typing: a tap, no gesture
        verify(listener, never()).onStartBatchInput()
    }
}
