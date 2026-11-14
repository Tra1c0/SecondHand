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

    private val keyboard: Keyboard by lazy {
        val layoutParams = KeyboardLayoutSet.Params()
        layoutParams.editorInfo = EditorInfo()
        layoutParams.subtype = RichInputMethodSubtype.get(SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.GERMANY, "qwertz", true))
        layoutParams.keyboardWidth = 1080
        layoutParams.keyboardHeight = 700
        layoutParams.settingsValues = Settings.getValues()
        val builder = KeyboardBuilder(ContextThemeWrapper(latinIME, R.style.KeyboardTheme_LXX_Base_Border), KeyboardParams(UniqueKeysCache.NO_CACHE))
        builder.load(KeyboardId(KeyboardElement.ALPHABET, layoutParams))
        builder.build()
    }
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

    private fun abs(v: Float) = kotlin.math.abs(v)
}
