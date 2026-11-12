// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.LARGE_TITLE_BAR_TAG
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.screens.AgentMainSettings
import helium314.keyboard.settings.screens.MainSettingsScreen
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stage 23e: the modern main settings page in the flat style, the upstream page otherwise. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-w411dp-h891dp-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class MainSettingsLookTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private var clicked = ""

    private fun show(style: String) {
        app.prefs().edit().putString(Settings.PREF_THEME_STYLE, style).commit()
        initPreview(app)
        compose.setContent {
            Theme(dark = true) {
                MainSettingsScreen(
                    onClickAbout = {}, onClickTextCorrection = {}, onClickPreferences = {}, onClickToolbar = {},
                    onClickGestureTyping = {}, onClickDataGathering = {}, onClickAdvanced = {}, onClickAppearance = {},
                    onClickLanguage = { clicked = "language" }, onClickLayouts = {}, onClickDictionaries = {},
                    onClickBack = {}, onClickAgentAi = { clicked = "ai" },
                )
            }
        }
        compose.waitForIdle()
    }

    // Fallstrick P32: the settings singleton outlives the test, so reload the defaults
    @After fun resetStyle() {
        app.prefs().edit().remove(Settings.PREF_THEME_STYLE).commit()
        Settings.getInstance().loadSettings(app)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun flatStyleShowsLargeTitleTallRowsAndOnlyTheLanguageSubtitle() {
        show(KeyboardTheme.STYLE_FLAT)
        compose.onNodeWithTag(LARGE_TITLE_BAR_TAG).assertExists()
        assertTrue(compose.onAllNodesWithText(app.getString(R.string.ime_settings)).fetchSemanticsNodes().isNotEmpty())

        val rows = compose.onAllNodesWithTag(AgentMainSettings.ROW_TAG).fetchSemanticsNodes()
        assertTrue(rows.size >= 16, "rows: ${rows.size}")
        val minHeight = with(compose.density) { AgentMainSettings.ROW_MIN_HEIGHT.toPx() }
        rows.forEach { assertTrue(it.size.height >= minHeight - 1, "row height ${it.size.height} < $minHeight") }
        // every row: one title line, only the languages row has a second text
        val texts = rows.map { row -> row.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        val withSubtitle = texts.filter { it.size > 1 }
        assertEquals(1, withSubtitle.size, "rows with subtitle: $withSubtitle")
        assertEquals(app.getString(R.string.language_and_layouts_title), withSubtitle.single().first())
        assertTrue(texts.all { it.isNotEmpty() })

        // rows are clickable and keep their destination
        compose.onNodeWithText(app.getString(R.string.agent_ai_title)).performClick()
        assertEquals("ai", clicked)

        // dark warm page background below Android 12 (no dynamic color), bar in the same color
        val image = capture("main-settings")
        val background = AgentMainSettings.WARM_DARK.toArgb()
        assertEquals(background, image.getPixel(image.width - 4, image.height - 4))
        assertEquals(background, image.getPixel(image.width / 2, 4))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun largeTitleCollapsesWhenTheListScrolls() {
        show(KeyboardTheme.STYLE_FLAT)
        val before = compose.onNodeWithTag(LARGE_TITLE_BAR_TAG).fetchSemanticsNode().size.height
        compose.onNodeWithTag(AgentMainSettings.LIST_TAG).performTouchInput { swipeUp() }
        compose.waitForIdle()
        val after = compose.onNodeWithTag(LARGE_TITLE_BAR_TAG).fetchSemanticsNode().size.height
        assertTrue(after < before, "title bar $before -> $after")
        capture("main-settings-scrolled")
        // the languages entry still works after scrolling
        compose.onNodeWithText(app.getString(R.string.language_and_layouts_title)).performScrollTo().performClick()
        assertEquals("language", clicked)
    }

    @Test
    fun otherStylesKeepTheUpstreamPage() {
        show(KeyboardTheme.STYLE_ROUNDED)
        compose.onNodeWithTag(LARGE_TITLE_BAR_TAG).assertDoesNotExist()
        assertEquals(0, compose.onAllNodesWithTag(AgentMainSettings.ROW_TAG).fetchSemanticsNodes().size)
        compose.onNodeWithText(app.getString(R.string.agent_ai_title)).assertExists().performClick()
        assertEquals("ai", clicked)
    }

    // captureToImage waits for a redraw that never comes in Robolectric (Fallstrick P15)
    private fun capture(name: String): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        val file = File("build/reports/agent-settings/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }
}
