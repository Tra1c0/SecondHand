// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

    // open: the draw-three-times dialog is not tested; in Robolectric compose never got idle after
    // "Weiter" (cause not found, see docs/VALIDATION.md, stage 27). The store is tested in InputLogicTest.
}
