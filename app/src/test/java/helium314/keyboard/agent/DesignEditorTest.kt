// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.AgentDesignScreen
import helium314.keyboard.settings.screens.DesignPreview
import helium314.keyboard.settings.screens.previewHeight
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Design page and editor (stage 23f2): "My designs" shows the "+" tile and the saved designs, a tap
 * selects, a long press or the pencil opens the editor, the editor saves and deletes, and the live
 * preview follows every change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h1000dp")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()

    @Before @After fun clear() {
        prefs.edit().clear().commit()
        MyDesigns.imageDir(app).deleteRecursively()
        Settings.getCustomBackgroundFile(app, false, false).delete()
    }

    private fun design(id: String, name: String, background: Int) = MyDesigns.Design(
        id = id, name = name, style = KeyboardTheme.STYLE_ROUNDED,
        colors = mapOf(
            KeyboardTheme.COLOR_BACKGROUND to background, KeyboardTheme.COLOR_KEYS to Color.rgb(60, 60, 60),
            KeyboardTheme.COLOR_FUNCTIONAL_KEYS to Color.rgb(30, 30, 30), KeyboardTheme.COLOR_TEXT to Color.WHITE,
            KeyboardTheme.COLOR_HINT_TEXT to Color.LTGRAY, KeyboardTheme.COLOR_ACCENT to Color.rgb(0, 150, 136),
        ),
    )

    private fun tile(id: String) = compose.onNodeWithTag("agent_design_tile_$id")

    private fun showPage() {
        compose.setContent { Theme(true) { AgentDesignScreen {} } }
        compose.waitForIdle()
    }

    /** Draws the window like the screen shows it (captureToImage times out in Robolectric, P46). */
    private fun screenshot(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String) {
        val file = File("build/reports/design-editor/$name").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun myDesignsShowsThePlusTileAndTheSavedDesigns() {
        MyDesigns.save(app, design("d1", "Nacht", Color.rgb(20, 20, 40)))
        MyDesigns.save(app, design("d2", "Wüste", Color.rgb(230, 220, 200)))
        showPage()
        compose.onNodeWithTag("agent_design_add").assertExists()
        compose.onNodeWithText("Meine Designs").assertExists()
        tile("agent_design_d1").assertExists().assertIsNotSelected()
        tile("agent_design_d2").assertExists()
        compose.onNodeWithText("Nacht").assertExists()
        compose.onNodeWithText("Wüste").assertExists()

        tile("agent_design_d2").performClick()
        compose.waitForIdle()
        assertEquals("d2", MyDesigns.selectedId(prefs))
        tile("agent_design_d2").assertIsSelected()
        tile("agent_design_d1").assertIsNotSelected()
        // the built-in tiles are no longer marked
        tile(KeyboardTheme.THEME_LIGHT).assertIsNotSelected()
    }

    @Test fun longPressAndThePencilOpenTheEditor() {
        MyDesigns.save(app, design("d1", "Nacht", Color.rgb(20, 20, 40)))
        showPage()
        tile("agent_design_d1").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("agent_design_editor").assertExists()
        compose.onNodeWithTag("agent_design_name").assert(hasText("Nacht"))
        // back without changes returns to the tiles
        compose.onNodeWithText("Abbrechen").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_design_edit_d1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_design_editor").assertExists()
        // nothing was written by opening and closing
        assertEquals(null, MyDesigns.selectedId(prefs))
    }

    @Test fun theEditorCreatesADesignAndTheLivePreviewFollowsEveryChange() {
        prefs.edit().putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_DARK).commit()
        showPage()
        compose.onNodeWithTag("agent_design_add").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_design_preview").assertExists()
        compose.onNodeWithTag("agent_design_name").performTextReplacement("Mein Look")
        compose.onNodeWithTag("agent_design_style_${KeyboardTheme.STYLE_FLAT}").performScrollTo().performClick()
        compose.onNodeWithTag("agent_design_corner_own").performScrollTo().performClick()
        compose.onNodeWithTag("agent_design_font_${DesignLook.FONT_BOLD}").performScrollTo().performClick()
        compose.waitForIdle()
        save(screenshot(), "editor.png")
        assertEquals(null, prefs.getString(MyDesigns.PREF_DESIGNS, null), "nothing is stored before saving")

        compose.onNodeWithTag("agent_design_save").performScrollTo().performClick()
        compose.waitForIdle()
        val saved = MyDesigns.list(prefs).single()
        assertEquals("Mein Look", saved.name)
        assertEquals(KeyboardTheme.STYLE_FLAT, saved.style)
        assertEquals(6f, saved.cornerRadius)
        assertEquals(DesignLook.FONT_BOLD, saved.font)
        assertEquals(saved.id, MyDesigns.selectedId(prefs))
        assertEquals(6f, prefs.getFloat(DesignLook.PREF_CORNER_RADIUS, -1f))
        assertEquals(KeyboardTheme.STYLE_FLAT, prefs.getString(Settings.PREF_THEME_STYLE, null))
        // back on the page, the new tile is there and marked
        tile(saved.themeName).assertIsSelected()
        save(screenshot(), "my-designs.png")
    }

    @Test fun deletingInTheEditorAsksFirst() {
        val saved = MyDesigns.save(app, design("d1", "Nacht", Color.rgb(20, 20, 40)))
        MyDesigns.select(app, saved)
        showPage()
        compose.onNodeWithTag("agent_design_edit_d1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("agent_design_delete").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Das Design „Nacht“ löschen?")).assertExists()
        compose.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(1, MyDesigns.list(prefs).size)

        compose.onNodeWithTag("agent_design_delete").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), MyDesigns.list(prefs))
        assertEquals(null, prefs.getString(Settings.PREF_THEME_COLORS, null))
        tile("agent_design_d1").assertDoesNotExist()
        compose.onNodeWithTag("agent_design_add").assertExists()
    }

    @Test fun thePreviewShowsColoursHeightAndAccentOfTheDesign() {
        var shown by mutableStateOf(design("d1", "Nacht", Color.rgb(200, 30, 30)))
        compose.setContent { Theme(true) { DesignPreview(shown, null) } }
        compose.waitForIdle()

        fun pixelAt(xFraction: Float, yFromBottomPx: Int): Int {
            val bounds = compose.onNodeWithTag("agent_design_preview").fetchSemanticsNode().boundsInRoot
            val bitmap = screenshot()
            val x = (bounds.left + bounds.width * xFraction).roundToInt()
            return bitmap.getPixel(x, bounds.bottom.roundToInt() - yFromBottomPx)
        }
        // bottom padding of the preview shows the keyboard background
        assertEquals(Color.rgb(200, 30, 30), pixelAt(0.5f, 3))
        shown = shown.copy(colors = shown.colors + (KeyboardTheme.COLOR_BACKGROUND to Color.rgb(10, 120, 200)))
        compose.waitForIdle()
        assertEquals(Color.rgb(10, 120, 200), pixelAt(0.5f, 3))

        // Enter sits at the right end of the bottom row in the accent colour
        val density = app.resources.displayMetrics.density
        val enterY = (6 * density + 50 * density / 2).roundToInt() // padding plus half a row from the bottom
        assertEquals(Color.rgb(0, 150, 136), pixelAt(0.92f, enterY))
        shown = shown.copy(colors = shown.colors + (KeyboardTheme.COLOR_ACCENT to Color.rgb(250, 200, 0)))
        compose.waitForIdle()
        assertEquals(Color.rgb(250, 200, 0), pixelAt(0.92f, enterY))

        // the key height setting changes the height of the preview
        compose.onNodeWithTag("agent_design_preview").assertHeightIsEqualTo(previewHeight(1f))
        shown = shown.copy(keyHeight = 1.3f)
        compose.waitForIdle()
        compose.onNodeWithTag("agent_design_preview").assertHeightIsEqualTo(previewHeight(1.3f))
        assertNotNull(screenshot())
        assertTrue(previewHeight(1.3f) > previewHeight(1f))
    }
}
