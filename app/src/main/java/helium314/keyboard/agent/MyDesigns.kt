// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.core.content.edit
import helium314.keyboard.keyboard.ColorSetting
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.prefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * "My designs" (stage 23f2): named designs the owner builds in the design editor. A design is
 * nothing but values for existing preferences: a HeliBoard user colour set
 * (`user_colors_agent_design_<id>`), key style and borders, key gap and height for portrait, and
 * the two [DesignLook] preferences. Selecting a design writes exactly those (see [selectionValues]);
 * the list of designs is a small JSON array in [PREF_DESIGNS]. An own background image is copied
 * into `agent_designs/` next to HeliBoard's background image and installed as that image on
 * selection. Nothing is downloaded.
 */
object MyDesigns {
    const val PREF_DESIGNS = "agent_designs"
    /** Id of the design whose image is currently installed as the keyboard background. */
    const val PREF_BACKGROUND_FROM = "agent_design_background"
    const val THEME_PREFIX = "agent_design_"
    const val IMAGE_DIR = "agent_designs"
    private const val BACKGROUND_BEFORE = "background_before"
    const val MAX_DESIGNS = 30

    /** Styles the editor offers; Holo needs its own colour logic and is left out. */
    val STYLES = listOf(KeyboardTheme.STYLE_MATERIAL, KeyboardTheme.STYLE_ROUNDED, KeyboardTheme.STYLE_FLAT)
    val CORNER_RANGE = 0f..24f
    val GAP_RANGE = 0.5f..2.5f
    val HEIGHT_RANGE = 0.5f..1.5f

    /** The colours the editor offers, in the order shown; names are those of HeliBoard's colour sets. */
    val COLOR_NAMES = listOf(
        KeyboardTheme.COLOR_BACKGROUND, KeyboardTheme.COLOR_KEYS, KeyboardTheme.COLOR_FUNCTIONAL_KEYS,
        KeyboardTheme.COLOR_TEXT, KeyboardTheme.COLOR_HINT_TEXT, KeyboardTheme.COLOR_ACCENT,
    )

    @Serializable
    data class Design(
        val id: String,
        val name: String,
        val style: String = KeyboardTheme.STYLE_ROUNDED,
        val keyBorders: Boolean = true,
        /** Colour per name in [COLOR_NAMES]; the others (space bar, gesture, ...) follow automatically. */
        val colors: Map<String, Int> = emptyMap(),
        /** dp, null = radius of the style. */
        val cornerRadius: Float? = null,
        val keyGap: Float = 1f,
        val keyHeight: Float = 1f,
        /** One of [DesignLook.FONTS], null = font of the style. */
        val font: String? = null,
        val hasImage: Boolean = false,
    ) {
        val themeName get() = THEME_PREFIX + id
        fun color(name: String): Int? = colors[name]
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun list(prefs: SharedPreferences): List<Design> =
        runCatching { json.decodeFromString<List<Design>>(prefs.getString(PREF_DESIGNS, null) ?: "[]") }
            .getOrDefault(emptyList()).filter { valid(it) }

    fun get(prefs: SharedPreferences, id: String) = list(prefs).firstOrNull { it.id == id }

    private fun valid(design: Design) = ID.matches(design.id) && design.style in STYLES &&
        design.colors.keys.all { it in COLOR_NAMES } && (design.font == null || design.font in DesignLook.FONTS) &&
        design.keyGap.isFinite() && design.keyHeight.isFinite() && design.cornerRadius?.isFinite() != false

    private val ID = Regex("[a-z0-9]{1,16}")

    /** A new, unsaved design that starts from the look the keyboard has now. */
    fun newDesign(context: Context, name: String): Design {
        val prefs = context.prefs()
        val style = prefs.getString(Settings.PREF_THEME_STYLE, Defaults.PREF_THEME_STYLE)!!.takeIf { it in STYLES }
            ?: KeyboardTheme.STYLE_ROUNDED
        val borders = style == KeyboardTheme.STYLE_FLAT || prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS)
        val theme = prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS)!!
        val current = KeyboardTheme.getPreviewColors(context, theme, style, withBackgroundImage = false)
        val existing = list(prefs).map { it.id }.toSet()
        var n = list(prefs).size + 1
        while ("d$n" in existing) n++
        return Design(
            id = "d$n",
            name = name,
            style = style,
            keyBorders = borders,
            colors = mapOf(
                KeyboardTheme.COLOR_BACKGROUND to current.get(ColorType.MAIN_BACKGROUND),
                KeyboardTheme.COLOR_KEYS to current.get(ColorType.KEY_BACKGROUND),
                KeyboardTheme.COLOR_FUNCTIONAL_KEYS to current.get(ColorType.FUNCTIONAL_KEY_BACKGROUND),
                KeyboardTheme.COLOR_TEXT to current.get(ColorType.KEY_TEXT),
                KeyboardTheme.COLOR_HINT_TEXT to current.get(ColorType.KEY_HINT_TEXT),
                KeyboardTheme.COLOR_ACCENT to current.get(ColorType.ACTION_KEY_BACKGROUND),
            ),
            cornerRadius = null,
            // 1f is the portrait default of both scales; reading Defaults.PREF_KEY_GAP_SCALE[0] here made
            // the compiler emit an invalid Defaults.<clinit> (VerifyError, see ARCHITECTURE.md P50)
            keyGap = prefs.getFloat(GAP_KEY, 1f),
            keyHeight = prefs.getFloat(HEIGHT_KEY, 1f),
            font = null,
        )
    }

    val GAP_KEY: String = createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2)
    val HEIGHT_KEY: String = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2)

    /** HeliBoard's colour set of [design]: every offered colour fixed, the rest automatic. */
    fun colorSettings(design: Design): List<ColorSetting> =
        COLOR_NAMES.mapNotNull { name -> design.color(name)?.let { ColorSetting(name, false, it) } }

    /** The colours the keyboard will use, computed by the same code as for the keyboard itself. */
    fun colors(context: Context, design: Design): Colors {
        val borders = design.style == KeyboardTheme.STYLE_FLAT || design.keyBorders
        return KeyboardTheme.readUserColorTheme(design.style, borders, colorSettings(design), context, false, null)
    }

    /**
     * Saves [design] (new or changed). [image] replaces the stored image: a file to take over, or
     * null to keep what [Design.hasImage] says. If the design is selected, it is applied again.
     */
    fun save(context: Context, design: Design, image: File? = null): Design {
        val prefs = context.prefs()
        require(valid(design))
        val others = list(prefs).filterNot { it.id == design.id }
        require(others.size < MAX_DESIGNS)
        val target = imageFile(context, design.id)
        val saved = when {
            image != null -> {
                target.parentFile?.mkdirs()
                image.copyTo(target, overwrite = true)
                design.copy(hasImage = true)
            }
            !design.hasImage -> design.also { target.delete() }
            else -> design.copy(hasImage = target.isFile)
        }
        prefs.edit {
            putString(PREF_DESIGNS, json.encodeToString(others + saved))
            putString(Settings.PREF_USER_COLORS_PREFIX + saved.themeName, Json.encodeToString(colorSettings(saved)))
        }
        if (selectedId(prefs) == saved.id) select(context, saved) else reload()
        return saved
    }

    /** The values [select] writes; null means the preference is removed. */
    fun selectionValues(design: Design): Map<String, Any?> = linkedMapOf(
        Settings.PREF_THEME_STYLE to design.style,
        Settings.PREF_ICON_STYLE to design.style,
        Settings.PREF_THEME_KEY_BORDERS to (design.style == KeyboardTheme.STYLE_FLAT || design.keyBorders),
        Settings.PREF_THEME_DAY_NIGHT to false,
        Settings.PREF_THEME_COLORS to design.themeName,
        Settings.PREF_USER_COLORS_PREFIX + design.themeName to Json.encodeToString(colorSettings(design)),
        GAP_KEY to design.keyGap,
        HEIGHT_KEY to design.keyHeight,
        DesignLook.PREF_CORNER_RADIUS to design.cornerRadius,
        DesignLook.PREF_FONT to design.font,
    )

    fun select(context: Context, design: Design) {
        val prefs = context.prefs()
        prefs.edit {
            selectionValues(design).forEach { (key, value) ->
                when (value) {
                    null -> remove(key)
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    else -> error("unsupported preference type")
                }
            }
        }
        if (design.hasImage && imageFile(context, design.id).isFile) installBackground(context, design.id)
        else releaseBackground(context)
        reload()
    }

    /** The design whose colour set the keyboard shows by day (day/night off), or null. */
    fun selectedId(prefs: SharedPreferences): String? {
        if (prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT)) return null
        val theme = prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS) ?: return null
        if (!theme.startsWith(THEME_PREFIX)) return null
        return theme.removePrefix(THEME_PREFIX).takeIf { id -> list(prefs).any { it.id == id } }
    }

    /**
     * Removes [id] with its colour set and image. If it is selected, the keyboard goes back to the
     * default colours, the style's radius and font, and the background image from before.
     */
    fun delete(context: Context, id: String) {
        val prefs = context.prefs()
        val selected = selectedId(prefs) == id
        val design = get(prefs, id) ?: return
        prefs.edit {
            putString(PREF_DESIGNS, json.encodeToString(list(prefs).filterNot { it.id == id }))
            remove(Settings.PREF_USER_COLORS_PREFIX + design.themeName)
            remove(Settings.PREF_USER_MORE_COLORS_PREFIX + design.themeName)
            remove(Settings.PREF_USER_ALL_COLORS_PREFIX + design.themeName)
            if (selected) {
                remove(Settings.PREF_THEME_COLORS)
                remove(DesignLook.PREF_CORNER_RADIUS)
                remove(DesignLook.PREF_FONT)
            }
        }
        if (selected || prefs.getString(PREF_BACKGROUND_FROM, null) == id) releaseBackground(context)
        imageFile(context, id).delete()
        reload()
    }

    /** Called when a built-in tile is selected: the radius, font and image of a design no longer apply. */
    fun leaveDesign(context: Context) {
        context.prefs().edit {
            remove(DesignLook.PREF_CORNER_RADIUS)
            remove(DesignLook.PREF_FONT)
        }
        releaseBackground(context)
    }

    fun imageDir(context: Context) = File(DeviceProtectedUtils.getFilesDir(context), IMAGE_DIR)
    fun imageFile(context: Context, id: String) = File(imageDir(context), "$id.img")
    /** Where the editor keeps a picked image until the design is saved. */
    fun pendingImageFile(context: Context) = File(imageDir(context), "pending.img")

    /** Whether [file] can be decoded as an image (only the bounds are read). */
    fun isImage(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }
        return options.outWidth > 0 && options.outHeight > 0
    }

    /**
     * Copies the design image over HeliBoard's portrait day background. An image the owner had set
     * before (not from a design) is kept once in `background_before` and comes back on [releaseBackground].
     */
    private fun installBackground(context: Context, id: String) {
        val prefs = context.prefs()
        val background = Settings.getCustomBackgroundFile(context, false, false)
        val before = File(imageDir(context), BACKGROUND_BEFORE)
        if (prefs.getString(PREF_BACKGROUND_FROM, null) == null && background.isFile) {
            before.parentFile?.mkdirs()
            background.copyTo(before, overwrite = true)
        }
        imageFile(context, id).copyTo(background, overwrite = true)
        prefs.edit { putString(PREF_BACKGROUND_FROM, id) }
        Settings.clearCachedBackgroundImages()
    }

    /** Takes a design image off the background and puts back the owner's own image, if there was one. */
    fun releaseBackground(context: Context) {
        val prefs = context.prefs()
        if (prefs.getString(PREF_BACKGROUND_FROM, null) == null) return
        val background = Settings.getCustomBackgroundFile(context, false, false)
        val before = File(imageDir(context), BACKGROUND_BEFORE)
        if (before.isFile) {
            before.copyTo(background, overwrite = true)
            before.delete()
        } else background.delete()
        prefs.edit { remove(PREF_BACKGROUND_FROM) }
        Settings.clearCachedBackgroundImages()
    }

    private fun reload() {
        KeyboardIconsSet.needsReload = true
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
}
