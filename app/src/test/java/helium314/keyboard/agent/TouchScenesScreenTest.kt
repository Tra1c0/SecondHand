// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.agentkeyboard.core.TouchDynamics
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
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

/** Stage 50: the section "Trefferzonen" with the switch for dynamic zones, the scenes and the step back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
class TouchScenesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()

    @Before @After fun clear() {
        app.prefs().edit().clear().commit()
        TouchModel.resetForTest()
        TouchModel.clear(app)
    }

    private val nothingTyped = object : LearningSource {
        override fun languages() = emptyList<String>()
        override fun words(language: String) = emptyList<LearnedWord>()
        override fun deleteAll() {}
    }

    private fun page() {
        compose.setContent { Theme(true) { helium314.keyboard.settings.screens.AgentLearningScreen(onClickBack = {}, sourceFactory = { nothingTyped }) } }
        compose.waitForIdle()
    }

    private fun eveningScene() {
        TouchModel.loadStored(app) // as the page does; afterwards the scene below is not read over
        val engine = TouchModel.engine
        engine.spreadRef = 0.1f
        engine.scenes += TouchDynamics.Scene(7, TouchDynamics.Bucket.EVENING, 3, 1, 2, FloatArray(9) { 0.2f }, FloatArray(9) { 0.2f },
            IntArray(9) { 10 }, 0f, 0.2f, 0.2f, Float.NaN, Float.NaN)
    }

    @Test fun dynamicSwitchIsOffByDefaultAndNeedsTheMainSwitch() {
        page()
        // nothing calibrated yet: no step back
        compose.onNode(hasText("Auf vorherige Kalibrierung zurücksetzen") and hasClickAction()).assertDoesNotExist()
        compose.onNode(hasText("Dynamisch anpassen (Szenen)")).performScrollTo().assertExists()
        val dynamic = hasTestTag("agent_touch_dynamic")
        compose.onNode(dynamic).performScrollTo().assertIsOff().assertIsNotEnabled()
        compose.onNodeWithTag("agent_touch_adapt").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(dynamic).performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertTrue(app.prefs().getBoolean(AgentPreferences.ADAPT_TOUCH_DYNAMIC, false))
        assertTrue(TouchModel.dynamic)
        compose.onNode(hasText("Noch keine Szenen.")).performScrollTo().assertExists()
        // the main switch off: the dynamic zones are off too
        compose.onNodeWithTag("agent_touch_adapt").performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(TouchModel.dynamic)
    }

    @Test fun scenesAreShownInWordsAndCanBeDeleted() {
        eveningScene()
        page()
        compose.onNode(hasText("abends, ungenauer, trifft tiefer (in 3 Sitzungen gesehen)")).performScrollTo().assertExists()
        compose.onNode(hasText("Szene löschen") and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(TouchModel.scenes().isEmpty())
        compose.onAllNodes(hasText("abends", substring = true)).assertCountEquals(0)
    }

    @Test fun resetToThePreviousCalibration() {
        val restore = hasText("Auf vorherige Kalibrierung zurücksetzen") and hasClickAction()
        TouchModel.loadStored(app)
        TouchModel.replace(app, mapOf('e'.code to Triple(0.2f, 0f, 30)))
        TouchModel.replace(app, mapOf('e'.code to Triple(-0.2f, 0f, 30)))
        page()
        compose.onNode(restore).performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        compose.onAllNodes(restore).onLast().performClick() // the confirmation
        compose.waitForIdle()
        assertTrue(TouchModel.baseOffsetX('e'.code) > 0.1f)
        assertFalse(TouchModel.hasPrevious(app))
        assertEquals(1, TouchModel.all().size)
    }
}
