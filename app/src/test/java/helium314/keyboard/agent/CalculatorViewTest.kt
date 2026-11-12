// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class CalculatorViewTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private lateinit var view: CalculatorView
    private val inserted = mutableListOf<String>()

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        view = CalculatorView(latinIME)
        view.onInsert = { inserted += it }
    }

    private fun buttons(group: View = view): List<Button> = when (group) {
        is Button -> listOf(group)
        is ViewGroup -> (0 until group.childCount).flatMap { buttons(group.getChildAt(it)) }
        else -> emptyList()
    }
    private fun press(label: String) = buttons().first { it.text.toString() == label }.performClick()
    private val preview get() = view.findViewById<TextView>(R.id.agent_calculator_preview).text.toString()
    private val insert get() = view.findViewById<Button>(R.id.agent_calculator_insert)

    @Test fun startsWithTheTypedCalculationAndInsertsOnlyOnTap() {
        view.show("12*3,5", "de")
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals("12×3,5", view.typed)
        assertEquals("12 × 3,5 = 42", preview)
        assertTrue(inserted.isEmpty())
        insert.performClick()
        assertEquals(listOf("12 × 3,5 = 42"), inserted)
    }

    @Test fun keysEditTheCalculation() {
        view.show("", "de")
        assertFalse(insert.isEnabled)
        listOf("1", "+", "2", "×", "3").forEach(::press)
        assertEquals("1 + 2 × 3 = 7", preview)
        press("⌫")
        assertEquals("1+2×", view.typed)
        assertFalse(insert.isEnabled) // incomplete
        assertEquals("", preview)
        press("C")
        assertEquals("", view.typed)
        listOf("1", "÷", "0").forEach(::press)
        assertEquals(latinIME.getString(R.string.agent_calculator_division_by_zero), preview)
        assertFalse(insert.isEnabled)
    }

    @Test fun outputFormatIsChosenAndRemembered() {
        view.show("2^10", "en")
        press(latinIME.getString(R.string.agent_calculator_style_unicode))
        assertEquals("2¹⁰ = 1024", preview)
        press(latinIME.getString(R.string.agent_calculator_style_result))
        assertEquals("1024", preview)
        insert.performClick()
        assertEquals(listOf("1024"), inserted)
        assertEquals("RESULT", latinIME.prefs().getString(AgentPreferences.CALCULATOR_STYLE, null))
        view.hide()
        view.show("1,5+1", "en")
        assertEquals("2.5", preview) // English decimal point, remembered format
        assertEquals(View.VISIBLE, view.visibility)
        view.hide()
        assertEquals(View.GONE, view.visibility)
        assertEquals("", view.typed)
    }

    @Test fun functionKeys() {
        view.show("", "de")
        press("fx")
        listOf("sin", "3", "0", ")").forEach(::press)
        assertEquals("sin(30)", view.typed)
        assertEquals("sin(30°) = 0,5", preview)
        press("⌫"); press("⌫"); press("⌫")
        assertEquals("sin(", view.typed)
        press("⌫") // the function name goes as a whole
        assertEquals("", view.typed)
        listOf("ln", "0", ")").forEach(::press)
        assertEquals(latinIME.getString(R.string.agent_calculator_undefined), preview)
        press("123")
        assertEquals("fx", view.findViewById<Button>(R.id.agent_calculator_functions).text.toString())
    }
}
