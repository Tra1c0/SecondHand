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
        tap('x'); TouchCalibration.onKey(KeyCode.DELETE, 0, 0, 0, 0, keyboard) // typed and deleted
        TouchCalibration.onKey('ä'.code, 5, 5, 0, 0, keyboard) // from a popup: not on a letter key, ignored
        "welt".forEach { tap(it, 0f, 0.2f) }
        assertEquals(9, TouchCalibration.touchCount())
        val result = TouchCalibration.result("Hallo Welt")
        // l occurs three times in the target and was hit three times; s was meant as a
        assertTrue(result.isEmpty()) // fewer than MIN_TOUCHES per key
        repeat(4) { "hsllo welt".filter { it != ' ' }.forEach { c -> tap(c, 0f, 0.2f) } }
        val more = TouchCalibration.result("Hallo Welt Hallo Welt Hallo Welt Hallo Welt Hallo Welt")
        val l = more.getValue('l'.code)
        assertTrue(abs(l.second - 0.2f) < 0.03f && abs(l.first) < 0.03f)
        assertEquals(15, l.third)
        val a = more.getValue('a'.code) // the s touches count for a, measured from the a key
        assertTrue(a.first > 0.5f, "s is right of a: ${a.first}")
        assertFalse('s'.code in more)
        TouchCalibration.start()
        assertEquals(0, TouchCalibration.touchCount())
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
