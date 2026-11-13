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
    private val sink = SymbolInk.Sink { drawings += it; log += "symbol" }
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

    private fun pause() = Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
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

    @Test fun secondFingerDropsTheDrawingAndIsHandledAsUsual() {
        stroke(*line(20f, 80f, 300f, 80f))
        touch(MotionEvent.ACTION_DOWN, 100f, 40f) // continuation stroke of pointer 0
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
        second(MotionEvent.ACTION_POINTER_UP)
        verify(listener, times(1)).onCodeInput(eq('a'.code), anyInt(), anyInt(), anyBoolean()) // the second finger typed
        touch(MotionEvent.ACTION_MOVE, 120f, 60f); touch(MotionEvent.ACTION_UP, 120f, 60f) // the first finger ends quietly
        pause()
        assertTrue(drawings.isEmpty())
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

    @Test fun tooManyStrokesAreNoSymbol() {
        stroke(*line(20f, 80f, 300f, 80f))
        repeat(12) { touch(MotionEvent.ACTION_DOWN, 30f + 20 * it, 40f); touch(MotionEvent.ACTION_UP, 30f + 20 * it, 40f) }
        pause()
        assertTrue(drawings.isEmpty())
        noLetterTyped()
    }
}
