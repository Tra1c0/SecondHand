// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.Key
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
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Stage 22a: personal hit boxes and the calibration. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class TouchModelTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)

    @Before fun reset() {
        latinIME.prefs().edit().clear().commit()
        TouchModel.resetForTest()
        TouchModel.clear(latinIME)
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
    }

    @After fun cleanUp() {
        TouchModel.clear(latinIME)
        TouchModel.resetForTest()
        TouchCalibration.active = false
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
    private fun learn(c: Char, dx: Float, dy: Float, times: Int = 60) = repeat(times) { TouchModel.record(c.code, dx, dy) }

    @Test fun offsetsNeedSamplesAreDampedAndBounded() {
        learn('e', 0.3f, 0f, TouchModel.MIN_SAMPLES - 1)
        assertEquals(0f, TouchModel.offsetX('e'.code))
        learn('e', 0.3f, 0f, 1)
        assertTrue(TouchModel.offsetX('e'.code) in 0.1f..0.2f) // damped while few: 15 / 30 of 0.3
        learn('e', 0.3f, 0f, 1000)
        assertTrue(abs(TouchModel.offsetX('e'.code) - 0.29f) < 0.02f)
        learn('w', 0.8f, -0.8f, 1000)
        assertEquals(TouchModel.MAX_SHIFT, TouchModel.offsetX('w'.code)) // bounded
        assertEquals(-TouchModel.MAX_SHIFT, TouchModel.offsetY('W'.code)) // case does not matter
        assertFalse(TouchModel.record('r'.code, 0.95f, 0f)) // slip to another key
        assertFalse(TouchModel.record(KeyCode.SHIFT, 0.1f, 0f)) // only letters
        assertEquals(0, TouchModel.samples('r'.code))
    }

    @Test fun withoutLearnedOffsetsDetectionIsExactlyTheSame() {
        TouchModel.enabled = false
        val plain = (0 until 1080 step 7).flatMap { x -> (0 until 700 step 7).map { y -> detector.detectHitKey(x, y) } }
        TouchModel.enabled = true
        val adjusted = (0 until 1080 step 7).flatMap { x -> (0 until 700 step 7).map { y -> detector.detectHitKey(x, y) } }
        assertEquals(plain, adjusted)
    }

    @Test fun aLearnedOffsetMovesOnlyTheEdgeBetweenKeys() {
        TouchModel.enabled = true
        learn('e', 0.3f, 0f, 2000) // the user hits e too far right
        val e: Rect = key('e').hitBox
        val r: Rect = key('r').hitBox
        // just right of the e/r edge: was r, is now e
        val nearEdge = r.left + r.width() / 8
        TouchModel.enabled = false
        assertSame(key('r'), detector.detectHitKey(nearEdge, r.centerY()))
        TouchModel.enabled = true
        assertSame(key('e'), detector.detectHitKey(nearEdge, r.centerY()))
        // the middle of r stays r, the middle of e stays e, other keys are untouched
        assertSame(key('r'), detector.detectHitKey(r.centerX(), r.centerY()))
        assertSame(key('e'), detector.detectHitKey(e.centerX(), e.centerY()))
        val q = key('q').hitBox
        assertSame(key('q'), detector.detectHitKey(q.centerX(), q.centerY()))
        // functional keys never change
        val shift = keyboard.sortedKeys.single { it.code == KeyCode.SHIFT }
        assertSame(shift, detector.detectHitKey(shift.hitBox.centerX(), shift.hitBox.centerY()))
        // switched off: back to normal
        TouchModel.enabled = false
        assertSame(key('r'), detector.detectHitKey(nearEdge, r.centerY()))
    }

    @Test fun learnedValuesSurviveARestartAndCanBeCleared() {
        learn('e', 0.2f, 0.1f, 40)
        TouchModel.persist(latinIME)
        val before = TouchModel.offsetX('e'.code)
        TouchModel.resetForTest()
        assertEquals(0f, TouchModel.offsetX('e'.code))
        TouchModel.load(latinIME, Settings.getValues())
        assertEquals(before, TouchModel.offsetX('e'.code))
        TouchModel.clear(latinIME)
        assertEquals(0, TouchModel.all().size)
    }

    @Test fun calibrationAlignsTypedWithIntendedLettersAndIgnoresPopupsAndDeletes() {
        TouchCalibration.active = true
        TouchCalibration.start()
        fun tap(c: Char, dx: Float = 0f, dy: Float = 0f) {
            val box = key(c).hitBox
            TouchCalibration.onKey(c.code, (box.exactCenterX() + dx * box.width()).toInt(), (box.exactCenterY() + dy * box.height()).toInt(), 0, 0, keyboard)
        }
        // target "hallo welt": the user hits a little too low, types "hsllo" (s for a), corrects one letter with delete
        "hsllo".forEach { tap(it, 0f, 0.2f) }
        tap('x'); TouchCalibration.onKey(KeyCode.DELETE, 0, 0, 0, 0, keyboard) // typed and deleted: left to the alignment
        TouchCalibration.onKey('ä'.code, 5, 5, 0, 0, keyboard) // no ä key on this layout: ignored
        "welt".forEach { tap(it, 0f, 0.2f) }
        assertEquals(10, TouchCalibration.touchCount())
        val result = TouchCalibration.result("Hallo Welt")
        // l occurs three times in the target and was hit three times; s was meant as a
        assertTrue(result.isEmpty()) // fewer than MIN_TOUCHES per key
        repeat(4) { "hsllo welt".filter { it != ' ' }.forEach { c -> tap(c, 0f, 0.2f) } }
        val more = TouchCalibration.result("Hallo Welt Hallo Welt Hallo Welt Hallo Welt Hallo Welt")
        val l = more.getValue('l'.code)
        assertTrue(abs(l.second - 0.2f) < 0.03f && abs(l.first) < 0.03f)
        assertEquals(15, l.third) // the deleted x was not counted for any letter
        val a = more.getValue('a'.code) // the s touches count for a, measured from the a key
        assertTrue(a.first > 0.5f, "s is right of a: ${a.first}")
        assertFalse('s'.code in more)
        TouchCalibration.start()
        assertEquals(0, TouchCalibration.touchCount())
    }

    @Test fun stoppingEarlyStillAlignsFromTheStart() {
        val target = TouchCalibration.TEXT.lowercase().filter { it.isLetter() }.map { it.code }
        val typed = target.take(20)
        val pairs = TouchCalibration.align(typed, target) { _, _ -> 3 }
        assertEquals(typed.indices.map { it to typed[it] }, pairs)
    }

    @Test fun aChangedKeyboardStartsTheCalibrationAgain() {
        TouchCalibration.active = true
        TouchCalibration.start()
        val box = key('e').hitBox
        repeat(3) { TouchCalibration.onKey('e'.code, box.centerX(), box.centerY(), 0, 0, keyboard) }
        assertEquals(3, TouchCalibration.touchCount())
        val other = KeyboardBuilder(ContextThemeWrapper(latinIME, R.style.KeyboardTheme_LXX_Base_Border), KeyboardParams(UniqueKeysCache.NO_CACHE)).run {
            val p = KeyboardLayoutSet.Params()
            p.editorInfo = EditorInfo()
            p.subtype = RichInputMethodSubtype.get(SubtypeUtilsAdditional.createEmojiCapableAdditionalSubtype(Locale.GERMANY, "qwertz", true))
            p.keyboardWidth = 1080; p.keyboardHeight = 500; p.settingsValues = Settings.getValues()
            load(KeyboardId(KeyboardElement.ALPHABET, p)); build()
        }
        val small = other.sortedKeys.single { it.code == 'e'.code }.hitBox
        TouchCalibration.onKey('e'.code, small.centerX(), small.centerY(), 0, 0, other)
        assertEquals(1, TouchCalibration.touchCount())
    }

    @Test fun typedWordsTeachTheZonesOnlyWhenAllowed() {
        fun type(word: String, committed: String, dx: Float, settings: helium314.keyboard.latin.settings.SettingsValues) {
            val boxes = word.map { key(it).hitBox }
            TouchModel.learnFromWord(latinIME, word, committed, boxes.map { (it.exactCenterX() + dx * it.width()).toInt() }.toIntArray(),
                boxes.map { it.centerY() }.toIntArray(), word.length, keyboard, 0, 0, settings)
        }
        type("wert", "wert", 0.25f, Settings.getValues()) // switched off
        assertEquals(0, TouchModel.samples('w'.code))
        latinIME.prefs().edit().putBoolean(AgentPreferences.ADAPT_TOUCH, true).commit()
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
        val on = Settings.getValues()
        type("wert", "wert", 0.25f, on)
        assertEquals(1, TouchModel.samples('w'.code))
        // a corrected slip: a touch on the left part of r counts for e, measured from the e key
        val e = key('e').hitBox; val r = key('r').hitBox; val w = key('w').hitBox; val t = key('t').hitBox
        TouchModel.learnFromWord(latinIME, "wrrt", "wert", intArrayOf(w.centerX(), r.left + r.width() / 5, r.centerX(), t.centerX()),
            IntArray(4) { e.centerY() }, 4, keyboard, 0, 0, on)
        assertEquals(2, TouchModel.samples('e'.code))
        val meanE = TouchModel.all().getValue('e'.code).first
        assertTrue(meanE > 0.4f && meanE < 0.6f, "mean $meanE") // (0.25 + about 0.7) / 2
        // different words or lengths teach nothing
        type("wert", "haus", 0f, on)
        type("wer", "wert", 0f, on)
        assertEquals(2, TouchModel.samples('w'.code))
        // incognito and paused learning teach nothing
        for (flag in listOf(Settings.PREF_ALWAYS_INCOGNITO_MODE, AgentPreferences.LEARNING_PAUSED)) {
            latinIME.prefs().edit().putBoolean(flag, true).commit()
            Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
            type("wert", "wert", 0f, Settings.getValues())
            latinIME.prefs().edit().remove(flag).commit()
        }
        assertEquals(2, TouchModel.samples('w'.code))
    }

    @Test fun alignmentKeepsMatchesAndSubstitutionsOnly() {
        val touched = "hslo".map { it.code } // a -> s, one l missing
        val intended = "hallo".map { it.code }
        // s and a are neighbours, s and l are not: the alignment takes the cheaper substitution
        val neighbours = setOf('s' to 'a', 'a' to 's')
        val pairs = TouchCalibration.align(touched, intended) { a, b -> if (a.toChar() to b.toChar() in neighbours) 1 else 2 }
            .map { (i, c) -> touched[i].toChar() to c.toChar() }
        assertEquals(listOf('h' to 'h', 's' to 'a', 'l' to 'l', 'o' to 'o'), pairs)
    }
}
