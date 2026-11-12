// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

/**
 * Model of the design page (stage 23f1): tiles for the built-in colour themes, for "follow the
 * system" and for the Flat look preset. Selecting a tile writes the same preferences that the
 * appearance screen writes (colour picker and day/night switch) or applies [FlatPreset]; nothing
 * else is stored and nothing is downloaded.
 */
object DesignTiles {
    const val FLAT = "agent_flat_preset"
    const val SYSTEM = "agent_follow_system"

    sealed class Tile(val id: String) {
        /** One built-in colour theme for day and night, e.g. [KeyboardTheme.THEME_DARK]. */
        class Colors(val theme: String) : Tile(theme)
        /** Light by day, dark by night: the day/night switch with the default colour pair. */
        data object System : Tile(SYSTEM)
        /** The Flat look preset with its backup. */
        data object Flat : Tile(FLAT)
    }

    enum class Section { STANDARD, TEMPLATES, COLORS }

    /** Colours of a tile: background, space bar and accent (Enter). */
    data class Swatch(val background: Int, val spaceBar: Int, val accent: Int)

    private val STANDARD = listOf(KeyboardTheme.THEME_DYNAMIC, KeyboardTheme.THEME_LIGHT, KeyboardTheme.THEME_DARK)

    /** System follows the day/night switch, which the appearance screen offers from Android 9 on. */
    private val hasDayNight get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun sections(prefs: SharedPreferences): List<Pair<Section, List<Tile>>> {
        // the same list the colour picker offers for the day colours
        val available = KeyboardTheme.getAvailableDefaultColors(prefs, false)
        val standard = buildList {
            if (KeyboardTheme.THEME_DYNAMIC in available) add(Tile.Colors(KeyboardTheme.THEME_DYNAMIC))
            if (hasDayNight) add(Tile.System)
            add(Tile.Colors(KeyboardTheme.THEME_LIGHT))
            add(Tile.Colors(KeyboardTheme.THEME_DARK))
        }
        val colors = available.filterNot { it in STANDARD }.map { Tile.Colors(it) }
        return listOf(Section.STANDARD to standard, Section.TEMPLATES to listOf(Tile.Flat), Section.COLORS to colors)
    }

    private fun dayNight(prefs: SharedPreferences) =
        prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT)

    private fun dayColors(prefs: SharedPreferences) =
        prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS)

    private fun nightColors(prefs: SharedPreferences) =
        prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT)

    /** Whether the keyboard shows [theme] by day and, if day/night is on, also by night. */
    private fun showsOnly(prefs: SharedPreferences, theme: String) =
        dayColors(prefs) == theme && (!dayNight(prefs) || nightColors(prefs) == theme)

    /** The one tile that matches the current preferences, or null (e.g. a user-defined theme). */
    fun selectedId(prefs: SharedPreferences): String? {
        val style = prefs.getString(Settings.PREF_THEME_STYLE, Defaults.PREF_THEME_STYLE)
        return when {
            style == KeyboardTheme.STYLE_FLAT && showsOnly(prefs, KeyboardTheme.THEME_FLAT_BLACK) -> FLAT
            dayNight(prefs) && dayColors(prefs) == Defaults.PREF_THEME_COLORS
                && nightColors(prefs) == Defaults.PREF_THEME_COLORS_NIGHT -> SYSTEM
            else -> dayColors(prefs)?.takeIf { showsOnly(prefs, it) }
        }
    }

    /**
     * Applies [tile]. A colour tile switches day/night off (otherwise the night colours would hide
     * the choice) and sets the day colours, exactly as the switch and the colour picker do; the
     * key style stays as it is. The system tile switches day/night on with the default pair.
     */
    fun select(context: Context, tile: Tile) {
        val prefs = context.prefs()
        when (tile) {
            is Tile.Flat -> {
                FlatPreset(context).apply()
                return
            }
            is Tile.System -> prefs.edit {
                putBoolean(Settings.PREF_THEME_DAY_NIGHT, true)
                putString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS)
                putString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT)
            }
            is Tile.Colors -> prefs.edit {
                if (dayNight(prefs)) putBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
                putString(Settings.PREF_THEME_COLORS, tile.theme)
            }
        }
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    /** Preview colours of a theme; the flat tile shows its own style, the others the current one. */
    fun swatch(context: Context, theme: String, style: String = currentStyle(context)): Swatch {
        // the swatch colours do not depend on the user's background image: do not decode it per tile
        val colors = KeyboardTheme.getPreviewColors(context, theme, style, withBackgroundImage = false)
        return Swatch(
            colors.get(ColorType.MAIN_BACKGROUND),
            colors.get(ColorType.SPACE_BAR_BACKGROUND),
            colors.get(ColorType.ACTION_KEY_BACKGROUND),
        )
    }

    /** One swatch per tile half: the system tile is half day colours, half night colours. */
    fun swatches(context: Context, tile: Tile): List<Swatch> = when (tile) {
        is Tile.Colors -> listOf(swatch(context, tile.theme))
        is Tile.System -> listOf(swatch(context, Defaults.PREF_THEME_COLORS), swatch(context, Defaults.PREF_THEME_COLORS_NIGHT))
        is Tile.Flat -> listOf(swatch(context, KeyboardTheme.THEME_FLAT_BLACK, KeyboardTheme.STYLE_FLAT))
    }

    /** What the tiles of the non-flat themes depend on besides the theme: style and key borders. */
    fun look(context: Context): String = currentStyle(context) + "/" +
        context.prefs().getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS)

    private fun currentStyle(context: Context) =
        context.prefs().getString(Settings.PREF_THEME_STYLE, Defaults.PREF_THEME_STYLE)!!
}
