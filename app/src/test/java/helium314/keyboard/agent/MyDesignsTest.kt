// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * "My designs" (stage 23f2): saving, selecting, editing and deleting a design write or remove
 * exactly the expected preferences, HeliBoard reads the stored colour set, and the design image
 * replaces the background image only while the design is in use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MyDesignsTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val prefs get() = app.prefs()

    @Before @After fun clear() {
        prefs.edit().clear().commit()
        MyDesigns.imageDir(app).deleteRecursively()
        Settings.getCustomBackgroundFile(app, false, false).delete()
        Settings.clearCachedBackgroundImages()
        DesignLook.load(app) // forget a font from an earlier test
    }

    private fun design(id: String = "d1", background: Int = Color.rgb(10, 20, 30)) = MyDesigns.Design(
        id = id,
        name = "Night $id",
        style = KeyboardTheme.STYLE_ROUNDED,
        keyBorders = true,
        colors = mapOf(
            KeyboardTheme.COLOR_BACKGROUND to background,
            KeyboardTheme.COLOR_KEYS to Color.rgb(40, 50, 60),
            KeyboardTheme.COLOR_FUNCTIONAL_KEYS to Color.rgb(70, 80, 90),
            KeyboardTheme.COLOR_TEXT to Color.WHITE,
            KeyboardTheme.COLOR_HINT_TEXT to Color.LTGRAY,
            KeyboardTheme.COLOR_ACCENT to Color.rgb(200, 100, 0),
        ),
        cornerRadius = 14f,
        keyGap = 1.5f,
        keyHeight = 1.2f,
        font = DesignLook.FONT_BOLD,
    )

    /** Keys whose value differs between [before] and [after] (added, changed or removed). */
    private fun changedKeys(before: Map<String, *>, after: Map<String, *>) =
        (before.keys + after.keys).filter { before[it] != after[it] }.toSet()

    private fun start() {
        prefs.edit().putString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
            .putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_DARK)
            .putBoolean(Settings.PREF_THEME_DAY_NIGHT, true)
            .putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true).commit()
    }

    @Test fun savingANewDesignWritesOnlyTheListAndItsColourSet() {
        start()
        val before = prefs.all.toMap()
        val saved = MyDesigns.save(app, design())
        assertEquals(setOf(MyDesigns.PREF_DESIGNS, Settings.PREF_USER_COLORS_PREFIX + "agent_design_d1"),
            changedKeys(before, prefs.all.toMap()))
        assertEquals(listOf(saved), MyDesigns.list(prefs))
        // HeliBoard's own reader finds every colour of the set
        val stored = KeyboardTheme.readUserColors(prefs, saved.themeName)
        assertEquals(MyDesigns.COLOR_NAMES.toSet(), stored.map { it.name }.toSet())
        assertTrue(stored.all { it.auto == false })
        assertNull(MyDesigns.selectedId(prefs))
    }

    @Test fun selectingWritesExactlyTheSelectionValuesAndTheKeyboardReadsThem() {
        start()
        val saved = MyDesigns.save(app, design())
        val before = prefs.all.toMap()
        MyDesigns.select(app, saved)
        val after = prefs.all.toMap()
        val expected = MyDesigns.selectionValues(saved)
        assertEquals(setOf(
            Settings.PREF_THEME_STYLE, Settings.PREF_ICON_STYLE, Settings.PREF_THEME_KEY_BORDERS, Settings.PREF_THEME_DAY_NIGHT,
            Settings.PREF_THEME_COLORS, MyDesigns.GAP_KEY, MyDesigns.HEIGHT_KEY, DesignLook.PREF_CORNER_RADIUS, DesignLook.PREF_FONT,
        ), changedKeys(before, after))
        expected.forEach { (key, value) -> assertEquals(value, after[key], key) }
        assertEquals(KeyboardTheme.STYLE_ROUNDED, after[Settings.PREF_THEME_STYLE])
        assertEquals(false, after[Settings.PREF_THEME_DAY_NIGHT])
        assertEquals("agent_design_d1", after[Settings.PREF_THEME_COLORS])
        assertEquals(1.5f, after["key_gap_scale_false_false"])
        assertEquals(1.2f, after["keyboard_height_scale_false_false"])
        assertEquals(14f, after[DesignLook.PREF_CORNER_RADIUS])
        assertEquals(DesignLook.FONT_BOLD, after[DesignLook.PREF_FONT])
        assertEquals("d1", MyDesigns.selectedId(prefs))
        // the keyboard's colour code resolves the stored set to the design colours
        val colors = KeyboardTheme.getPreviewColors(app, saved.themeName, KeyboardTheme.STYLE_ROUNDED)
        assertEquals(Color.rgb(10, 20, 30), colors.get(ColorType.MAIN_BACKGROUND))
        assertEquals(Color.rgb(40, 50, 60), colors.get(ColorType.KEY_BACKGROUND))
        assertEquals(Color.rgb(200, 100, 0), colors.get(ColorType.ACTION_KEY_BACKGROUND))
        // the design tiles mark nothing else
        assertEquals("agent_design_d1", DesignTiles.selectedId(prefs))
    }

    @Test fun savingTheSelectedDesignAppliesTheChangeAndRemovesWhatItNoLongerSets() {
        start()
        val saved = MyDesigns.save(app, design())
        MyDesigns.select(app, saved)
        val changed = saved.copy(cornerRadius = null, font = null, keyGap = 0.8f,
            colors = saved.colors + (KeyboardTheme.COLOR_BACKGROUND to Color.RED))
        val before = prefs.all.toMap()
        MyDesigns.save(app, changed)
        val after = prefs.all.toMap()
        assertEquals(setOf(MyDesigns.PREF_DESIGNS, Settings.PREF_USER_COLORS_PREFIX + "agent_design_d1", MyDesigns.GAP_KEY,
            DesignLook.PREF_CORNER_RADIUS, DesignLook.PREF_FONT), changedKeys(before, after))
        assertFalse(DesignLook.PREF_CORNER_RADIUS in after)
        assertFalse(DesignLook.PREF_FONT in after)
        assertEquals(Color.RED, KeyboardTheme.getPreviewColors(app, saved.themeName, KeyboardTheme.STYLE_ROUNDED).get(ColorType.MAIN_BACKGROUND))
    }

    @Test fun savingADesignThatIsNotSelectedLeavesTheKeyboardAlone() {
        start()
        val first = MyDesigns.save(app, design("d1"))
        MyDesigns.select(app, first)
        val before = prefs.all.toMap()
        MyDesigns.save(app, design("d2", Color.BLUE).copy(cornerRadius = 2f))
        assertEquals(setOf(MyDesigns.PREF_DESIGNS, Settings.PREF_USER_COLORS_PREFIX + "agent_design_d2"),
            changedKeys(before, prefs.all.toMap()))
        assertEquals(listOf("d1", "d2"), MyDesigns.list(prefs).map { it.id })
    }

    @Test fun deletingTheSelectedDesignRemovesExactlyItsPreferences() {
        start()
        val saved = MyDesigns.save(app, design())
        MyDesigns.save(app, design("d2"))
        MyDesigns.select(app, saved)
        val before = prefs.all.toMap()
        MyDesigns.delete(app, "d1")
        val after = prefs.all.toMap()
        assertEquals(setOf(MyDesigns.PREF_DESIGNS, Settings.PREF_USER_COLORS_PREFIX + "agent_design_d1", Settings.PREF_THEME_COLORS,
            DesignLook.PREF_CORNER_RADIUS, DesignLook.PREF_FONT), changedKeys(before, after))
        listOf(Settings.PREF_USER_COLORS_PREFIX + "agent_design_d1", Settings.PREF_THEME_COLORS,
            DesignLook.PREF_CORNER_RADIUS, DesignLook.PREF_FONT).forEach { assertFalse(it in after, it) }
        assertEquals(listOf("d2"), MyDesigns.list(prefs).map { it.id })
        assertNull(MyDesigns.selectedId(prefs))
    }

    @Test fun deletingAnotherDesignKeepsTheSelection() {
        start()
        val saved = MyDesigns.save(app, design())
        MyDesigns.save(app, design("d2"))
        MyDesigns.select(app, saved)
        val before = prefs.all.toMap()
        MyDesigns.delete(app, "d2")
        assertEquals(setOf(MyDesigns.PREF_DESIGNS, Settings.PREF_USER_COLORS_PREFIX + "agent_design_d2"),
            changedKeys(before, prefs.all.toMap()))
        assertEquals("d1", MyDesigns.selectedId(prefs))
    }

    @Test fun theDesignImageReplacesTheBackgroundOnlyWhileTheDesignIsUsed() {
        start()
        val background = Settings.getCustomBackgroundFile(app, false, false)
        background.parentFile!!.mkdirs()
        background.writeText("own image")
        val picked = File(app.cacheDir, "picked.img").apply { writeText("design image") }
        val withImage = MyDesigns.save(app, design("d1"), picked)
        assertTrue(withImage.hasImage)
        assertEquals("design image", MyDesigns.imageFile(app, "d1").readText())
        assertEquals("own image", background.readText(), "saving alone does not touch the background")
        val plain = MyDesigns.save(app, design("d2"))

        MyDesigns.select(app, withImage)
        assertEquals("design image", background.readText())
        assertEquals("d1", prefs.getString(MyDesigns.PREF_BACKGROUND_FROM, null))
        MyDesigns.select(app, plain)
        assertEquals("own image", background.readText())
        assertFalse(prefs.contains(MyDesigns.PREF_BACKGROUND_FROM))

        // without an own image before, the design image goes away again; deleting removes the copy
        background.delete()
        MyDesigns.select(app, withImage)
        assertTrue(background.isFile)
        MyDesigns.delete(app, "d1")
        assertFalse(background.exists())
        assertFalse(MyDesigns.imageFile(app, "d1").exists())
    }

    @Test fun anOwnImageSetWhileADesignIsUsedIsNeverOverwrittenOrDeleted() {
        start()
        val background = Settings.getCustomBackgroundFile(app, false, false)
        background.parentFile!!.mkdirs()
        background.writeText("old own image")
        val first = MyDesigns.save(app, design("d1"), File(app.cacheDir, "a.img").apply { writeText("image one") })
        val second = MyDesigns.save(app, design("d2"), File(app.cacheDir, "b.img").apply { writeText("image two") })
        MyDesigns.select(app, first)
        background.writeText("new own image") // set in HeliBoard's own settings meanwhile

        MyDesigns.select(app, second)
        assertEquals("image two", background.readText())
        MyDesigns.leaveDesign(app)
        assertEquals("new own image", background.readText(), "the newer own image is the one that comes back")

        // without any own image before, an image set meanwhile stays when the design is left
        background.delete()
        MyDesigns.select(app, first)
        background.writeText("set meanwhile")
        MyDesigns.leaveDesign(app)
        assertEquals("set meanwhile", background.readText())
        assertFalse(prefs.contains(MyDesigns.PREF_BACKGROUND_FROM))
    }

    @Test fun aBuiltInTileDropsRadiusFontAndImageOfADesign() {
        start()
        val picked = File(app.cacheDir, "picked.img").apply { writeText("design image") }
        MyDesigns.select(app, MyDesigns.save(app, design(), picked))
        DesignTiles.select(app, DesignTiles.Tile.Colors(KeyboardTheme.THEME_DARK))
        assertFalse(prefs.contains(DesignLook.PREF_CORNER_RADIUS))
        assertFalse(prefs.contains(DesignLook.PREF_FONT))
        assertFalse(Settings.getCustomBackgroundFile(app, false, false).exists())
        assertEquals(KeyboardTheme.THEME_DARK, prefs.getString(Settings.PREF_THEME_COLORS, null))
        // the design itself stays
        assertEquals(listOf("d1"), MyDesigns.list(prefs).map { it.id })
    }

    @Test fun aNewDesignStartsFromTheCurrentLook() {
        start()
        val new = MyDesigns.newDesign(app, "Mine")
        assertEquals(KeyboardTheme.STYLE_MATERIAL, new.style)
        val dark = KeyboardTheme.getPreviewColors(app, KeyboardTheme.THEME_DARK, KeyboardTheme.STYLE_MATERIAL, false)
        assertEquals(dark.get(ColorType.MAIN_BACKGROUND), new.color(KeyboardTheme.COLOR_BACKGROUND))
        assertEquals(dark.get(ColorType.ACTION_KEY_BACKGROUND), new.color(KeyboardTheme.COLOR_ACCENT))
        assertEquals("d1", new.id)
        MyDesigns.save(app, new)
        assertEquals("d2", MyDesigns.newDesign(app, "Mine").id)
    }

    @Test fun invalidStoredDesignsAreIgnored() {
        prefs.edit().putString(MyDesigns.PREF_DESIGNS, "not json").commit()
        assertEquals(emptyList(), MyDesigns.list(prefs))
        prefs.edit().putString(MyDesigns.PREF_DESIGNS,
            """[{"id":"d1","name":"a","style":"Holo"},{"id":"../x","name":"b"},{"id":"d3","name":"c","font":"comic"},{"id":"d4","name":"ok"}]""").commit()
        assertEquals(listOf("d4"), MyDesigns.list(prefs).map { it.id })
    }

    @Test fun theCornerRadiusChangesKeyShapesButNotPills() {
        val density = app.resources.displayMetrics.density
        val flat = ContextCompat.getDrawable(app, R.drawable.btn_keyboard_key_functional_flat)!!.mutate()
        DesignLook.setCornerRadius(flat, 12f * density, 100f * density)
        val shapes = (flat.constantState as DrawableContainer.DrawableContainerState).children.filterIsInstance<GradientDrawable>()
        assertTrue(shapes.any { it.cornerRadius == 12f * density }, "the rectangles take the new radius")
        assertTrue(shapes.any { it.cornerRadius == 500f * density }, "pills stay pills")
        // the shared resource is not changed
        val fresh = ContextCompat.getDrawable(app, R.drawable.btn_keyboard_key_flat_rect) as GradientDrawable
        assertEquals(6f * density, fresh.cornerRadius)

        // layer lists (Material and Rounded with borders) are reached too
        val rounded = ContextCompat.getDrawable(app, R.drawable.btn_keyboard_key_rounded_base_border)!!.mutate()
        prefs.edit().putFloat(DesignLook.PREF_CORNER_RADIUS, 3f).commit()
        DesignLook.load(app, rounded)
        val layers = (rounded.constantState as DrawableContainer.DrawableContainerState).children.filterNotNull()
            .filterIsInstance<android.graphics.drawable.LayerDrawable>()
        assertTrue(layers.isNotEmpty())
        val radii = layers.flatMap { layer ->
            (0 until layer.numberOfLayers).map { layer.getDrawable(it) }.filterIsInstance<GradientDrawable>().map { it.cornerRadius }
        }
        assertTrue(3f * density in radii)
        // the Rounded action key is a pill and stays one
        assertTrue(radii.all { it == 3f * density || it >= 100f * density }, radii.toString())
    }

    @Test fun theDesignFontAppliesToEveryStyle() {
        val colors = KeyboardTheme.getPreviewColors(app, KeyboardTheme.THEME_DARK, KeyboardTheme.STYLE_MATERIAL, false)
        DesignLook.load(app)
        assertSame(Typeface.DEFAULT, FlatStyle.typeface(colors, Typeface.DEFAULT))
        prefs.edit().putString(DesignLook.PREF_FONT, DesignLook.FONT_BOLD).commit()
        DesignLook.load(app)
        assertSame(Typeface.DEFAULT_BOLD, FlatStyle.typeface(colors, Typeface.DEFAULT))
        prefs.edit().putString(DesignLook.PREF_FONT, "unknown").commit()
        DesignLook.load(app)
        assertSame(Typeface.DEFAULT, FlatStyle.typeface(colors, Typeface.DEFAULT))
    }
}
