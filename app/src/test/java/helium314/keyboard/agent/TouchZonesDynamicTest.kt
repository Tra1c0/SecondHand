// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.KeyguardManager
import android.content.Context
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyDetector
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.UniqueKeysCache
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Stage 50: the dynamic hit zones in the keyboard (hook, confirmation of words, restricted fields, promotion). */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class TouchZonesDynamicTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var now = time(1, 10)

    private fun time(day: Int, hour: Int) = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, day, hour, 0) }.timeInMillis

    @Before fun reset() {
        latinIME.prefs().edit().clear().commit()
        TouchModel.resetForTest()
        TouchModel.clear(latinIME)
        TouchModel.clock = { now }
        latinIME.prefs().edit().putBoolean(AgentPreferences.ADAPT_TOUCH, true).putBoolean(AgentPreferences.ADAPT_TOUCH_DYNAMIC, true).commit()
        TouchModel.enabled = true
    }

    @After fun cleanUp() {
        TouchModel.clear(latinIME)
        TouchModel.resetForTest()
        shadowOf(latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(false)
    }

    private fun build(element: KeyboardElement = KeyboardElement.ALPHABET, width: Int = 1080): Keyboard {
        val layoutParams = KeyboardLayoutSet.Params()
        layoutParams.editorInfo = EditorInfo()
        layoutParams.subtype = RichInputMethodSubtype.get(SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.GERMANY, "qwertz", true))
        layoutParams.keyboardWidth = width
        layoutParams.keyboardHeight = 700
        layoutParams.settingsValues = Settings.getValues()
        val builder = KeyboardBuilder(ContextThemeWrapper(latinIME, R.style.KeyboardTheme_LXX_Base_Border), KeyboardParams(UniqueKeysCache.NO_CACHE))
        builder.load(KeyboardId(element, layoutParams))
        return builder.build()
    }
    private val keyboard: Keyboard by lazy { build() }
    private fun key(c: Char) = keyboard.sortedKeys.single { it.code == c.code }
    private val detector by lazy { KeyDetector().also { it.setKeyboard(this.keyboard, 0f, 0f) } }

    private fun field(app: String = "org.chat", type: Int = InputType.TYPE_CLASS_TEXT, flags: Int = 0, id: Int = 1) =
        EditorInfo().apply { packageName = app; inputType = type; imeOptions = flags; fieldId = id }

    /** Opens [editorInfo] as LatinIME does: our hook first (with the old settings), then the settings of the field. */
    private fun open(editorInfo: EditorInfo, restarting: Boolean = false) {
        TouchModel.load(latinIME, Settings.getValues() ?: loadSettings(editorInfo), editorInfo, restarting)
        loadSettings(editorInfo)
    }
    private fun loadSettings(editorInfo: EditorInfo): helium314.keyboard.latin.settings.SettingsValues {
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editorInfo, false, latinIME.packageName))
        return Settings.getValues()
    }

    private val words = listOf("wert", "haus", "nebel", "kino", "sofa", "pilz", "juwel", "fragen", "mixer", "gold", "tasche", "bild", "quer", "vogel")

    /** Types [word] key by key and commits it with space, [dy] key heights too low; its touches wait for the next key. */
    private fun type(word: String, dy: Float) {
        val boxes = word.map { key(it).hitBox }
        word.forEach { TouchModel.onKeyInput(it.code); now += 250 }
        TouchModel.onKeyInput(' '.code); now += 250
        val jitter = { i: Int -> ((i * 7) % 5 - 2) * 0.02f }
        TouchModel.learnFromWord(latinIME, word, word, boxes.mapIndexed { i, b -> (b.exactCenterX() + jitter(i) * b.width()).toInt() }.toIntArray(),
            boxes.mapIndexed { i, b -> (b.exactCenterY() + (dy + jitter(i + 1)) * b.height()).toInt() }.toIntArray(),
            word.length, keyboard, 0, 0, Settings.getValues())
    }

    private fun typeWords(count: Int, dy: Float) { repeat(count) { type(words[it % words.size], dy) } }

    @Test fun confirmedWordsMoveTheZonesWithinAFewWordsAndLeaveTheBase() {
        open(field())
        assertTrue(TouchModel.dynamic)
        typeWords(8, 0.3f)
        TouchModel.onKeyInput('a'.code) // confirms the last word
        assertTrue(TouchModel.engine.sessionTaps >= 30, "${TouchModel.engine.sessionTaps}")
        // the base stays as calibrated (nothing): only the short-term layer moved
        assertEquals(0, TouchModel.samples('e'.code))
        assertEquals(0f, TouchModel.baseOffsetY('e'.code))
        assertTrue(TouchModel.offsetY('e'.code) > 0.15f, "${TouchModel.offsetY('e'.code)}")
        assertTrue(TouchModel.offsetY('e'.code) <= TouchModel.MAX_SHIFT)
        // just below e, in the next row: now e
        val e = key('e').hitBox
        val below = e.bottom + e.height() / 8
        TouchModel.dynamic = false
        val plain = detector.detectHitKey(e.centerX(), below)
        assertNotSame(key('e'), plain)
        TouchModel.dynamic = true
        assertSame(key('e'), detector.detectHitKey(e.centerX(), below))
        // the middle of the other key stays that key
        assertSame(plain, detector.detectHitKey(plain.hitBox.centerX(), plain.hitBox.centerY()))
        // another app: the layer is gone
        now += 1000
        open(field(app = "org.mail"))
        assertEquals(0f, TouchModel.offsetY('e'.code))
    }

    @Test fun aWordDeletedRightAfterwardsTeachesNothing() {
        open(field())
        type("wert", 0.3f)
        TouchModel.onKeyInput(KeyCode.DELETE) // the user corrects the word: its touches are dropped
        assertEquals(0, TouchModel.engine.sessionTaps)
        type("haus", 0.3f)
        TouchModel.onKeyInput('x'.code) // kept
        assertEquals(4, TouchModel.engine.sessionTaps)
        // a field change before the next key drops the word too
        type("kino", 0.3f)
        open(field(id = 2))
        TouchModel.onKeyInput('x'.code)
        assertEquals(4, TouchModel.engine.sessionTaps)
    }

    @Test fun restrictedFieldsAndStatesTeachAndMoveNothing() {
        fun nothingIn(editorInfo: EditorInfo) {
            TouchModel.engine.clear()
            open(editorInfo)
            typeWords(8, 0.3f)
            TouchModel.onKeyInput('a'.code)
            assertEquals(0, TouchModel.engine.sessionTaps)
            assertEquals(0f, TouchModel.offsetY('e'.code))
            // the layer itself did not move (offsetY alone reads only the base in such a field) ...
            for (r in -1 until dev.agentkeyboard.core.TouchDynamics.REGIONS) {
                assertEquals(0f, TouchModel.engine.shortX(r), "region $r")
                assertEquals(0f, TouchModel.engine.shortY(r), "region $r")
            }
            // ... the base learned nothing either (stage 22b would have counted every touch) ...
            assertEquals(0, TouchModel.samples('e'.code))
            assertEquals(0f, TouchModel.baseOffsetY('e'.code))
            // ... and nothing waits for the next normal field: there the zones start unmoved
            now += 1000
            open(field(app = "org.other"))
            assertEquals(0f, TouchModel.offsetY('e'.code))
            TouchModel.onKeyInput('a'.code)
            assertEquals(0, TouchModel.engine.sessionTaps)
            assertTrue(TouchModel.engine.scenes.isEmpty() && TouchModel.engine.observations.isEmpty())
        }
        nothingIn(field(type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        nothingIn(field(type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        nothingIn(field(flags = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING))
        for (flag in listOf(Settings.PREF_ALWAYS_INCOGNITO_MODE, AgentPreferences.LEARNING_PAUSED)) {
            latinIME.prefs().edit().putBoolean(flag, true).commit()
            nothingIn(field())
            latinIME.prefs().edit().remove(flag).commit()
        }
        shadowOf(latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(true)
        nothingIn(field())
        shadowOf(latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(false)
        // switched off: nothing either, and the base learns as in stage 22b
        latinIME.prefs().edit().putBoolean(AgentPreferences.ADAPT_TOUCH_DYNAMIC, false).commit()
        open(field())
        assertFalse(TouchModel.dynamic)
        type("wert", 0.3f)
        TouchModel.onKeyInput('a'.code)
        assertEquals(0, TouchModel.engine.sessionTaps)
        assertEquals(1, TouchModel.samples('w'.code))
        // and back on in a normal field: it works
        latinIME.prefs().edit().putBoolean(AgentPreferences.ADAPT_TOUCH_DYNAMIC, true).commit()
        open(field())
        type("wert", 0.3f)
        TouchModel.onKeyInput('a'.code)
        assertEquals(4, TouchModel.engine.sessionTaps)
    }

    @Test fun aRegularDeviationMovesTheBaseOnceItCanBeUndoneAndScenesSurviveARestart() {
        val sessions = listOf(1 to 8, 1 to 20, 2 to 9, 3 to 8, 3 to 19)
        sessions.forEachIndexed { i, (day, hour) ->
            now = time(day, hour)
            open(field()) // more than five minutes after the last session: that one ends here
            assertEquals(0, TouchModel.promotions(), "before session $i")
            assertEquals(0f, TouchModel.baseOffsetY('e'.code))
            typeWords(10, 0.25f)
            TouchModel.onKeyInput('a'.code)
        }
        now += 10 * 60_000L
        open(field())
        assertEquals(1, TouchModel.promotions())
        val promoted = TouchModel.baseOffsetY('e'.code)
        // only typed letters move; a letter never typed keeps no base and no count
        assertEquals(3 * TouchModel.MIN_SAMPLES, TouchModel.samples('e'.code))
        for (untyped in listOf('y')) { // the only letter of the qwertz layout missing in the words
            assertTrue(untyped.code in keyboard.sortedKeys.map { it.code }, "$untyped on the keyboard")
            assertEquals(0, TouchModel.samples(untyped.code), "$untyped")
            assertEquals(0f, TouchModel.baseOffsetY(untyped.code), "$untyped")
        }
        assertTrue(promoted in 0.05f..0.07f, "base after the promotion: $promoted") // half of 0.25, at most 0.06 per step
        assertTrue(abs(TouchModel.baseOffsetX('e'.code)) < 0.03f)
        assertTrue(TouchModel.scenes().isNotEmpty())
        assertTrue(TouchModel.hasPrevious(latinIME))
        // a restart keeps base and scenes
        val scenes = TouchModel.scenes().map { it.id }
        TouchModel.resetForTest()
        TouchModel.loadStored(latinIME)
        assertEquals(promoted, TouchModel.baseOffsetY('e'.code))
        assertEquals(scenes, TouchModel.scenes().map { it.id })
        assertEquals(1, TouchModel.promotions())
        // back to the previous calibration (here: none)
        assertTrue(TouchModel.restorePrevious(latinIME))
        assertEquals(0f, TouchModel.baseOffsetY('e'.code))
        assertFalse(TouchModel.hasPrevious(latinIME))
        assertEquals(scenes, TouchModel.scenes().map { it.id }) // scenes stay
    }

    @Test fun aCalibrationKeepsThePreviousBase() {
        TouchModel.loadStored(latinIME)
        assertFalse(TouchModel.hasPrevious(latinIME))
        TouchModel.replace(latinIME, mapOf('e'.code to Triple(0.2f, 0f, 30)))
        TouchModel.replace(latinIME, mapOf('e'.code to Triple(-0.2f, 0f, 30)))
        assertTrue(TouchModel.offsetX('e'.code) < 0f)
        assertTrue(TouchModel.restorePrevious(latinIME))
        assertTrue(TouchModel.offsetX('e'.code) > 0.1f) // the first calibration again
        TouchModel.clear(latinIME)
        assertFalse(TouchModel.hasPrevious(latinIME))
    }

    /** Four shifted sessions and a fifth one that is still running: its end would promote. */
    private fun almostPromoted() {
        listOf(1 to 8, 1 to 20, 2 to 9, 3 to 8, 3 to 19).forEach { (day, hour) ->
            now = time(day, hour)
            open(field())
            typeWords(10, 0.25f)
            TouchModel.onKeyInput('a'.code)
        }
        assertEquals(0, TouchModel.promotions())
        assertTrue(TouchModel.engine.sessionTaps >= dev.agentkeyboard.core.TouchDynamics.MIN_SESSION)
    }

    @Test fun switchedOffTheNextFieldLooksAtNothing() {
        for (switch in listOf(AgentPreferences.ADAPT_TOUCH_DYNAMIC, AgentPreferences.ADAPT_TOUCH)) {
            TouchModel.clear(latinIME)
            latinIME.prefs().edit().putBoolean(AgentPreferences.ADAPT_TOUCH, true).putBoolean(AgentPreferences.ADAPT_TOUCH_DYNAMIC, true).commit()
            almostPromoted()
            val scenes = TouchModel.engine.scenes.size
            val observations = TouchModel.engine.observations.size
            val spreadRef = TouchModel.engine.spreadRef
            latinIME.prefs().edit().putBoolean(switch, false).commit()
            now += 10 * 60_000L
            open(field())
            assertFalse(TouchModel.dynamic, switch)
            assertEquals(0, TouchModel.promotions(), switch)
            assertEquals(0, TouchModel.samples('e'.code), switch)
            assertEquals(0f, TouchModel.baseOffsetY('e'.code), switch)
            assertFalse(TouchModel.hasPrevious(latinIME), switch)
            assertEquals(scenes, TouchModel.engine.scenes.size, switch)
            assertEquals(observations, TouchModel.engine.observations.size, switch)
            assertEquals(spreadRef, TouchModel.engine.spreadRef, switch)
            assertEquals(0, TouchModel.engine.sessionTaps, switch) // dropped, not ended
            // switched on again: the dropped session never comes back
            latinIME.prefs().edit().putBoolean(switch, true).commit()
            now += 10 * 60_000L
            open(field())
            assertEquals(0, TouchModel.promotions(), switch)
            assertEquals(observations, TouchModel.engine.observations.size, switch)
        }
    }

    @Test fun aHeldDeleteCountsOnceAndUndoOrCutDropTheWord() {
        open(field())
        type("wert", 0.3f)
        TouchModel.onKeyInput(KeyCode.DELETE)
        repeat(10) { TouchModel.onKeyInput(KeyCode.DELETE, true); now += 50 }
        assertEquals(1, TouchModel.engine.sessionDeletes)
        assertEquals(0, TouchModel.engine.sessionTaps)
        for (code in listOf(KeyCode.UNDO, KeyCode.CLIPBOARD_CUT)) {
            type("haus", 0.3f)
            TouchModel.onKeyInput(code)
            assertEquals(0, TouchModel.engine.sessionTaps, "$code")
        }
        assertEquals(1, TouchModel.engine.sessionDeletes) // undo and cut drop the word but are no deletes
        type("haus", 0.3f)
        TouchModel.onKeyInput('x'.code)
        assertEquals(4, TouchModel.engine.sessionTaps)
    }

    @Test fun anotherKeyboardGeometryEndsTheSession() {
        open(field())
        typeWords(8, 0.3f)
        TouchModel.onKeyInput('a'.code, false, keyboard)
        val taps = TouchModel.engine.sessionTaps
        assertTrue(taps >= 30 && TouchModel.offsetY('e'.code) > 0.15f)
        // the shifted keyboard has the same geometry: nothing changes
        val shifted = build(KeyboardElement.ALPHABET_MANUAL_SHIFTED)
        assertNotSame(keyboard, shifted)
        TouchModel.onKeyInput('a'.code, false, shifted)
        assertEquals(taps, TouchModel.engine.sessionTaps)
        // symbols have no letters: the regions stay
        TouchModel.onKeyInput('1'.code, false, build(KeyboardElement.SYMBOLS))
        assertEquals(taps, TouchModel.engine.sessionTaps)
        val region = TouchModel.region('p'.code)
        // narrower (one-handed, another orientation): the session ends, the layer is gone, regions are new
        val narrow = build(width = 700)
        TouchModel.onKeyInput('a'.code, false, narrow)
        assertEquals(0, TouchModel.engine.sessionTaps)
        assertEquals(0f, TouchModel.offsetY('e'.code))
        assertEquals(region, TouchModel.region('p'.code)) // same layout: same region, made from the new keyboard
        // a word typed on the wide keyboard waits; the next key comes from the narrow one: the word is dropped
        type("wert", 0.3f)
        TouchModel.onKeyInput('a'.code, false, narrow)
        assertEquals(0, TouchModel.engine.sessionTaps)
    }

    @Test fun aCalibrationAndTheStepBackMoveTheScenesWithTheBase() {
        open(field())
        TouchModel.onKeyInput('a'.code, false, keyboard) // regions of this keyboard
        val engine = TouchModel.engine
        engine.scenes += dev.agentkeyboard.core.TouchDynamics.Scene(3, dev.agentkeyboard.core.TouchDynamics.Bucket.EVENING, 2, 1, 2,
            FloatArray(9) { 0.2f }, FloatArray(9) { 0.2f }, IntArray(9) { 10 }, 0.2f, 0.2f, 0.1f, Float.NaN, Float.NaN)
        engine.observations += dev.agentkeyboard.core.TouchDynamics.Observation(1, dev.agentkeyboard.core.TouchDynamics.Bucket.EVENING, 3,
            0.2f, 0.2f, FloatArray(9) { 0.2f }, FloatArray(9) { 0.2f }, IntArray(9) { 10 })
        // every letter calibrated 0.2 lower: applied 0.2 * 45 / 60 = 0.15
        val letters = keyboard.sortedKeys.map { it.code }.filter { Character.isLetter(it) }.distinct()
        TouchModel.replace(latinIME, letters.associateWith { Triple(0f, 0.2f, 45) })
        assertEquals(0.15f, TouchModel.baseOffsetY('e'.code), 1e-5f)
        val scene = engine.scenes.single()
        assertEquals(0.05f, scene.dy, 1e-4f) // the same touches, now relative to the new base
        assertEquals(0.2f, scene.dx, 1e-4f)
        scene.regionDy.forEach { assertEquals(0.05f, it, 1e-4f) }
        scene.regionDx.forEach { assertEquals(0.2f, it, 1e-4f) }
        assertTrue(engine.observations.isEmpty()) // evidence against the old base
        // stored like that
        TouchModel.resetForTest(); TouchModel.loadStored(latinIME)
        assertEquals(0.05f, TouchModel.engine.scenes.single().dy, 1e-3f)
        // back to the previous calibration (none): the scenes move back as well
        open(field())
        TouchModel.onKeyInput('a'.code, false, keyboard)
        assertTrue(TouchModel.restorePrevious(latinIME))
        assertEquals(0f, TouchModel.baseOffsetY('e'.code))
        val back = TouchModel.engine.scenes.single()
        assertEquals(0.2f, back.dy, 1e-3f)
        back.regionDy.forEach { assertEquals(0.2f, it, 1e-3f) }
    }

    @Test fun storedSceneNumbersAreRounded() {
        TouchModel.loadStored(latinIME)
        TouchModel.engine.scenes += dev.agentkeyboard.core.TouchDynamics.Scene(4, dev.agentkeyboard.core.TouchDynamics.Bucket.DAY, 1, 1, 1,
            FloatArray(9) { 0.123456f }, FloatArray(9) { -0.0004f }, IntArray(9) { 1 }, 0.123456f, 0f, 0.1f, 123.4567f, Float.NaN)
        TouchModel.deleteScene(latinIME, 99) // marks the state as changed and saves it
        val text = latinIME.getSharedPreferences(TouchModel.FILE, Context.MODE_PRIVATE).getString("dynamics", null)!!
        assertTrue("0.123," in text || "0.123]" in text, text)
        assertFalse("0.123456" in text, text)
        TouchModel.resetForTest(); TouchModel.loadStored(latinIME)
        val scene = TouchModel.engine.scenes.single()
        assertEquals(0.123f, scene.dx, 1e-6f)
        assertEquals(123.457f, scene.speed, 1e-3f)
        assertTrue(scene.corrections.isNaN())
    }

    private fun abs(v: Float) = kotlin.math.abs(v)
}
