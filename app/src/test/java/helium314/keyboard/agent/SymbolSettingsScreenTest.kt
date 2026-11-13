// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import dev.agentkeyboard.core.InkRecognizer
import dev.agentkeyboard.core.InkStroke
import dev.agentkeyboard.core.InkSymbols
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentSymbolsScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The page "Symbole malen" (stage 27): switch, built-in list and own symbols drawn three times. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SymbolSettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()

    @Before @After fun clear() {
        app.prefs().edit().clear().commit()
        SymbolDrawing.saveOwnSymbols(app, emptyList())
        SymbolDrawing.savePersonalSymbols(app, emptyList())
    }

    private fun page() {
        compose.setContent { Theme(true) { AgentSymbolsScreen {} } }
        compose.waitForIdle()
    }

    @Test fun switchIsOffByDefaultAndSaved() {
        page()
        assertFalse(app.prefs().getBoolean(AgentPreferences.SYMBOL_DRAWING, false))
        compose.onNode(hasText("Symbole malen (statt Gestenschreiben)")).performClick()
        compose.waitForIdle()
        assertTrue(app.prefs().getBoolean(AgentPreferences.SYMBOL_DRAWING, false))
        // every built-in symbol is listed
        InkSymbols.all.forEach { compose.onAllNodes(hasText(it.text, substring = false)).onFirst().assertExists() }
    }

    @Test fun ownSymbolIsDrawnThreeTimesSavedAndDeleted() {
        page()
        compose.onNodeWithTag("agent_symbol_add").performClick()
        compose.waitForIdle()
        repeat(3) { round ->
            compose.onNode(hasText("Zeichnung ${round + 1} von 3")).assertExists()
            compose.onNodeWithText("Weiter").assertIsNotEnabled() // nothing drawn yet
            compose.onNodeWithTag("agent_symbol_pad").performScrollTo().performTouchInput {
                // a "W" in one stroke
                down(Offset(width * 0.1f, height * 0.1f))
                moveTo(Offset(width * 0.3f, height * 0.9f))
                moveTo(Offset(width * 0.5f, height * 0.4f))
                moveTo(Offset(width * 0.7f, height * 0.9f))
                moveTo(Offset(width * 0.9f, height * 0.1f))
                up()
            }
            compose.waitForIdle()
            compose.onNodeWithText("Weiter").performScrollTo().assertIsEnabled().performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Speichern").assertIsNotEnabled() // no text yet
        compose.onNodeWithTag("agent_symbol_text").performTextInput("123456789")
        compose.waitForIdle()
        compose.onNodeWithText("Speichern").assertIsNotEnabled() // more than 8 characters
        compose.onNodeWithTag("agent_symbol_text").performTextClearance()
        compose.onNodeWithTag("agent_symbol_text").performTextInput("👋")
        compose.waitForIdle()
        compose.onNodeWithText("Speichern").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()

        val saved = SymbolDrawing.ownSymbols(app)
        assertEquals(1, saved.size)
        assertEquals("👋", saved[0].text)
        assertEquals(3, saved[0].samples.size)
        // the saved drawing is recognized as the own symbol
        val w = InkStroke.of(0f, 0f, 75f, 300f, 150f, 120f, 225f, 300f, 300f, 0f)
        val recognizer = InkRecognizer(saved.flatMap { it.templates() } + InkSymbols.templates)
        assertEquals("👋", recognizer.recognize(listOf(w)).best?.text)
        compose.onNodeWithTag("agent_symbol_own_0").assertExists()

        compose.onNode(hasContentDescription("👋 löschen")).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(SymbolDrawing.ownSymbols(app).isEmpty())
        compose.onNodeWithTag("agent_symbol_own_0").assertDoesNotExist()
    }

    // stage 31

    @Test fun gestureTypingIsGreyedOutWithAHintWhileSymbolsAreDrawn() {
        helium314.keyboard.latin.utils.JniUtils.sHaveGestureLib = true
        try {
            val title = app.getString(helium314.keyboard.latin.R.string.gesture_input)
            app.prefs().edit().putBoolean(helium314.keyboard.latin.settings.Settings.PREF_GESTURE_INPUT, true)
                .putBoolean(AgentPreferences.SYMBOL_DRAWING, true).commit()
            helium314.keyboard.settings.SettingsActivity.settingsContainer = helium314.keyboard.settings.SettingsContainer(app)
            compose.setContent { Theme(true) { helium314.keyboard.settings.screens.GestureTypingScreen {} } }
            compose.waitForIdle()
            compose.onNode(hasText(title) and hasClickAction()).assertIsNotEnabled()
            compose.onNode(hasText("Aus, solange „Symbole malen“ an ist")).assertExists()
            compose.onNode(hasText(title) and hasClickAction()).performClick() // does nothing
            compose.waitForIdle()
            assertTrue(app.prefs().getBoolean(helium314.keyboard.latin.settings.Settings.PREF_GESTURE_INPUT, false)) // the own value stays
            // the gesture typing options are gone, the trail fade-out stays (the drawing's trail uses it)
            compose.onNode(hasText(app.getString(helium314.keyboard.latin.R.string.gesture_preview_trail))).assertDoesNotExist()
            compose.onNode(hasText(app.getString(helium314.keyboard.latin.R.string.gesture_trail_fadeout_duration))).assertExists()
        } finally {
            helium314.keyboard.latin.utils.JniUtils.sHaveGestureLib = false
        }
    }

    @Test fun gestureTypingSwitchIsNormalWithoutSymbolDrawing() {
        helium314.keyboard.latin.utils.JniUtils.sHaveGestureLib = true
        try {
            val title = app.getString(helium314.keyboard.latin.R.string.gesture_input)
            helium314.keyboard.settings.SettingsActivity.settingsContainer = helium314.keyboard.settings.SettingsContainer(app)
            compose.setContent { Theme(true) { helium314.keyboard.settings.screens.GestureTypingScreen {} } }
            compose.waitForIdle()
            compose.onNode(hasText(title) and hasClickAction()).assertIsEnabled()
            compose.onNode(hasText("Aus, solange „Symbole malen“ an ist")).assertDoesNotExist()
        } finally {
            helium314.keyboard.latin.utils.JniUtils.sHaveGestureLib = false
        }
    }

    @Test fun drawingPadAsksToRedrawAfterTwelveStrokes() {
        page()
        compose.onNodeWithTag("agent_symbol_add").performClick()
        compose.waitForIdle()
        fun dash(i: Int) = compose.onNodeWithTag("agent_symbol_pad").performScrollTo().performTouchInput {
            down(Offset(width * 0.1f, height * (0.05f + 0.07f * i))); moveBy(Offset(width * 0.5f, 0f)); up()
        }
        repeat(12) { dash(it) }
        compose.waitForIdle()
        compose.onNodeWithTag("agent_symbol_pad_limit").assertDoesNotExist()
        compose.onNodeWithText("Weiter").assertIsEnabled()
        dash(12)
        compose.waitForIdle()
        compose.onNode(hasText("Höchstens 12 Striche. Bitte neu malen.")).assertExists()
        compose.onNodeWithText("Weiter").assertIsNotEnabled()
        compose.onNodeWithText("Neu malen").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_symbol_pad_limit").assertDoesNotExist()
    }

    @Test fun drawingPadKeepsAtMost4096Points() {
        page()
        compose.onNodeWithTag("agent_symbol_add").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_symbol_pad").performScrollTo().performTouchInput {
            down(Offset(width * 0.1f, height * 0.1f))
            // 4200 moves back and forth, each to a new point
            for (i in 1..4200) moveTo(Offset(width * (0.1f + 0.8f * ((i % 200) / 200f)), height * (0.1f + 0.8f * (i / 4200f))))
            up()
        }
        compose.waitForIdle()
        compose.onNode(hasText("Zu lange gemalt. Bitte neu malen.")).assertExists()
        compose.onNodeWithText("Weiter").assertIsNotEnabled()
    }

    @Test fun drawingPadTakesExactlyAsManyPointsAsTheKeyboard() {
        // review N5: 4096 points are a drawing (InkGroup.MAX_POINTS), 4097 are too long
        page()
        compose.onNodeWithTag("agent_symbol_add").performClick()
        compose.waitForIdle()
        fun draw(points: Int) = compose.onNodeWithTag("agent_symbol_pad").performScrollTo().performTouchInput {
            down(Offset(width * 0.1f, height * 0.1f))
            // each move to a new point
            for (i in 1 until points) moveTo(Offset(width * (0.1f + 0.8f * ((i % 200) / 200f)), height * (0.1f + 0.8f * (i / 4200f))))
            up()
        }
        draw(dev.agentkeyboard.core.InkGroup.MAX_POINTS)
        compose.waitForIdle()
        compose.onNodeWithTag("agent_symbol_pad_limit").assertDoesNotExist()
        compose.onNodeWithText("Weiter").assertIsEnabled()
        compose.onNodeWithText("Neu malen").performScrollTo().performClick()
        compose.waitForIdle()
        draw(dev.agentkeyboard.core.InkGroup.MAX_POINTS + 1)
        compose.waitForIdle()
        compose.onNode(hasText("Zu lange gemalt. Bitte neu malen.")).assertExists()
        compose.onNodeWithText("Weiter").assertIsNotEnabled()
    }

    @Test fun cancelledAddingSavesNothing() {
        page()
        compose.onNodeWithTag("agent_symbol_add").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_symbol_pad").performScrollTo().performTouchInput { down(center); moveBy(Offset(50f, 0f)); up() }
        compose.waitForIdle()
        compose.onNode(hasText("Abbrechen") or hasText("Cancel")).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(SymbolDrawing.ownSymbols(app).isEmpty())
        compose.onNodeWithTag("agent_symbol_card").assertDoesNotExist()
        compose.onNodeWithTag("agent_symbol_add").assertExists()
    }

    // stage 36: personal templates (learning switch, list, calibration)

    private fun cloud(vararg xy: Float) = dev.agentkeyboard.core.InkCloud.of(listOf(InkStroke(xy)))!!

    @Test fun learningIsOffByDefaultAndSaved() {
        page()
        assertFalse(app.prefs().getBoolean(AgentPreferences.SYMBOL_LEARNING, false))
        compose.onNode(hasText("Aus meinen Korrekturen lernen")).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(app.prefs().getBoolean(AgentPreferences.SYMBOL_LEARNING, false))
    }

    @Test fun personalTemplatesAreListedAndDeleted() {
        var personal = dev.agentkeyboard.core.PersonalInkSymbols.add(emptyList(), "→", cloud(0f, 0f, 100f, 0f))
        personal = dev.agentkeyboard.core.PersonalInkSymbols.add(personal, "→", cloud(0f, 0f, 100f, 3f))
        personal = dev.agentkeyboard.core.PersonalInkSymbols.add(personal, "≠", cloud(0f, 0f, 50f, 100f))
        SymbolDrawing.savePersonalSymbols(app, personal)
        page()
        compose.onNodeWithTag("agent_symbol_personal_→").performScrollTo().assertExists()
        compose.onNode(hasText("2 Zeichnungen")).assertExists()
        compose.onNode(hasText("1 Zeichnungen")).assertExists()
        compose.onNode(hasContentDescription("→ löschen")).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("≠"), SymbolDrawing.personalSymbols(app).map { it.text })
        compose.onNodeWithTag("agent_symbol_personal_→").assertDoesNotExist()
        compose.onNodeWithTag("agent_symbol_personal_clear").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(SymbolDrawing.personalSymbols(app).isEmpty())
        compose.onNode(hasText("Noch keine persönlichen Vorlagen.")).assertExists()
    }

    private fun drawOnPad() {
        compose.onNodeWithTag("agent_symbol_pad").performScrollTo().performTouchInput {
            down(Offset(width * 0.1f, height * 0.5f)); moveTo(Offset(width * 0.5f, height * 0.5f)); moveTo(Offset(width * 0.9f, height * 0.5f)); up()
        }
        compose.waitForIdle()
    }

    private fun next() {
        compose.onNodeWithText("Weiter").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
    }

    @Test fun calibrationKeepsEveryDrawingAndCanSkipAndStop() {
        page()
        val total = InkSymbols.all.size
        compose.onNodeWithTag("agent_symbol_calibrate").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Male → (1 von 2), Symbol 1 von $total")).assertExists()
        compose.onNodeWithText("Weiter").assertIsNotEnabled() // nothing drawn yet
        drawOnPad(); next()
        compose.onNode(hasText("Male → (2 von 2), Symbol 1 von $total")).assertExists()
        assertEquals(1, SymbolDrawing.personalSymbols(app).single().samples.size) // kept at once
        drawOnPad(); next()
        compose.onNode(hasText("Male ← (1 von 2), Symbol 2 von $total")).assertExists()
        compose.onNodeWithText("Überspringen").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Male ${InkSymbols.all[2].text} (1 von 2), Symbol 3 von $total")).assertExists()
        drawOnPad(); next()
        compose.onNodeWithText("Fertig").performScrollTo().performClick() // stop half way: what was drawn stays
        compose.waitForIdle()
        compose.onNodeWithTag("agent_symbol_calibration").assertDoesNotExist()
        val personal = SymbolDrawing.personalSymbols(app)
        assertEquals(listOf("→" to 2, InkSymbols.all[2].text to 1), personal.map { it.text to it.samples.size })
        compose.onNodeWithTag("agent_symbol_personal_→").performScrollTo().assertExists()
    }
}
