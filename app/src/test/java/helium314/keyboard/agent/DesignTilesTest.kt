// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.screens.AgentDesignScreen
import helium314.keyboard.settings.screens.createAppearanceSettings
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
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Design page (stage 23f1): a tile writes exactly what the existing appearance settings write,
 * the flat tile applies the Flat preset with its backup, and only the selected tile is marked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
class DesignTilesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()

    @Before @After fun clear() {
        prefs.edit().clear().commit()
        app.getSharedPreferences(FlatPreset.BACKUP_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun start(values: Map<String, Any>) {
        val edit = prefs.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                else -> error("unsupported")
            }
        }
        edit.commit()
    }

    private fun setting(key: String): Setting = createAppearanceSettings(app).first { it.key == key }

    private fun tile(id: String) = compose.onNodeWithTag("agent_design_tile_$id")

    /** Picks [theme] in the existing colour picker dialog of [key]. */
    private fun pickInExistingDialog(key: String, label: String) {
        compose.onNodeWithText(setting(key).title).performClick()
        compose.waitForIdle()
        compose.onNode(hasText(label) and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
    }

    /**
     * Runs [existing] on the existing appearance settings and [tileId] on the design page, both from
     * [before], and checks that the preferences end up the same.
     */
    private fun assertSameAsExisting(before: Map<String, Any>, tileId: String, existing: () -> Unit): Map<String, *> {
        start(before)
        var phase by mutableIntStateOf(0)
        compose.setContent {
            MaterialTheme {
                if (phase == 0) Column {
                    setting(Settings.PREF_THEME_DAY_NIGHT).Preference()
                    setting(Settings.PREF_THEME_COLORS).Preference()
                    setting(Settings.PREF_THEME_COLORS_NIGHT).Preference()
                } else AgentDesignScreen {}
            }
        }
        compose.waitForIdle()
        existing()
        val viaExisting = prefs.all.toMap()
        assertNotEquals(before, viaExisting, "the existing settings changed nothing")

        start(before)
        phase = 1
        compose.waitForIdle()
        tile(tileId).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(viaExisting, prefs.all.toMap())
        tile(tileId).assertIsSelected()
        return viaExisting
    }

    @Test fun aColourTileWritesWhatTheDayNightSwitchAndTheColourPickerWrite() {
        val before = mapOf<String, Any>(Settings.PREF_THEME_DAY_NIGHT to true,
            Settings.PREF_THEME_COLORS to KeyboardTheme.THEME_LIGHT, Settings.PREF_THEME_COLORS_NIGHT to KeyboardTheme.THEME_BLACK)
        val after = assertSameAsExisting(before, KeyboardTheme.THEME_DARK) {
            compose.onNodeWithText(setting(Settings.PREF_THEME_DAY_NIGHT).title).performClick()
            compose.waitForIdle()
            pickInExistingDialog(Settings.PREF_THEME_COLORS, "Dark")
        }
        assertEquals(false, after[Settings.PREF_THEME_DAY_NIGHT])
        assertEquals(KeyboardTheme.THEME_DARK, after[Settings.PREF_THEME_COLORS])
        // the night colours and the key style stay as they were
        assertEquals(KeyboardTheme.THEME_BLACK, after[Settings.PREF_THEME_COLORS_NIGHT])
        assertEquals(null, after[Settings.PREF_THEME_STYLE])
    }

    @Test fun theSystemTileWritesWhatTheSwitchAndBothColourPickersWrite() {
        val before = mapOf<String, Any>(Settings.PREF_THEME_DAY_NIGHT to false,
            Settings.PREF_THEME_COLORS to KeyboardTheme.THEME_BLACK, Settings.PREF_THEME_COLORS_NIGHT to KeyboardTheme.THEME_BLACK)
        val after = assertSameAsExisting(before, DesignTiles.SYSTEM) {
            compose.onNodeWithText(setting(Settings.PREF_THEME_DAY_NIGHT).title).performClick()
            compose.waitForIdle()
            pickInExistingDialog(Settings.PREF_THEME_COLORS, "Light")
            pickInExistingDialog(Settings.PREF_THEME_COLORS_NIGHT, "Dark")
        }
        assertEquals(true, after[Settings.PREF_THEME_DAY_NIGHT])
    }

    @Test fun theFlatTileAppliesThePresetWhichCanBeRestored() {
        start(mapOf(Settings.PREF_THEME_STYLE to KeyboardTheme.STYLE_ROUNDED, Settings.PREF_THEME_COLORS to KeyboardTheme.THEME_SAND))
        val before = prefs.all.toMap()
        val preset = FlatPreset(app)
        compose.setContent { MaterialTheme { AgentDesignScreen {} } }
        compose.waitForIdle()
        tile(KeyboardTheme.THEME_SAND).assertIsSelected()
        tile(DesignTiles.FLAT).assertIsNotSelected()

        tile(DesignTiles.FLAT).performScrollTo().performClick()
        compose.waitForIdle()
        preset.values().forEach { (key, value) -> assertEquals(value, prefs.all[key], key) }
        assertTrue(preset.canRestore)
        tile(DesignTiles.FLAT).assertIsSelected()
        tile(KeyboardTheme.THEME_SAND).assertIsNotSelected()
        // the colour tile of the same colours is not marked a second time
        tile(KeyboardTheme.THEME_FLAT_BLACK).assertIsNotSelected()

        preset.restore()
        assertEquals(before, prefs.all.toMap())
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "en-w411dp-h1000dp")
    fun exactlyOneTileIsMarkedAndAPreviewIsRendered() {
        start(mapOf(Settings.PREF_THEME_DAY_NIGHT to false, Settings.PREF_THEME_COLORS to KeyboardTheme.THEME_DARK))
        compose.setContent { Theme(true) { AgentDesignScreen {} } }
        compose.waitForIdle()
        val tiles = DesignTiles.sections(prefs).flatMap { it.second }
        // Android 9: no dynamic colours, but the system tile
        assertTrue(tiles.none { it.id == KeyboardTheme.THEME_DYNAMIC })
        assertTrue(tiles.any { it.id == DesignTiles.SYSTEM })
        assertEquals(1, compose.onAllNodes(hasTestTagPrefix() and isSelected()).fetchSemanticsNodes().size)
        tile(KeyboardTheme.THEME_DARK).assertIsSelected()
        tile(DesignTiles.SYSTEM).assertIsNotSelected()

        tile(DesignTiles.SYSTEM).performClick()
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodes(hasTestTagPrefix() and isSelected()).fetchSemanticsNodes().size)
        tile(DesignTiles.SYSTEM).assertIsSelected()
        tile(KeyboardTheme.THEME_DARK).assertIsNotSelected()

        val preview = File("build/reports/design-tiles/design.png").apply { parentFile!!.mkdirs() }
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun hasTestTagPrefix() = SemanticsMatcher("tile") {
        it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("agent_design_tile_") == true
    }
}
