// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.SharedPreferences
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.prefs

/**
 * One-tap preset for the Flat look: style, colours, icons, rows and sizes for portrait. The values
 * it replaces are kept in a separate file so that the previous look can be restored once.
 * It only writes appearance and layout preferences; learning, history and credentials are untouched.
 */
class FlatPreset(private val context: Context) {
    private val keyboardPrefs: SharedPreferences = context.prefs()
    private val backup: SharedPreferences = context.getSharedPreferences(BACKUP_NAME, Context.MODE_PRIVATE)

    /** The preference values of the preset, in the order they are written. */
    fun values(): Map<String, Any> = linkedMapOf(
        Settings.PREF_THEME_STYLE to KeyboardTheme.STYLE_FLAT,
        Settings.PREF_ICON_STYLE to KeyboardTheme.STYLE_FLAT,
        Settings.PREF_THEME_COLORS to KeyboardTheme.THEME_FLAT_BLACK,
        Settings.PREF_THEME_COLORS_NIGHT to KeyboardTheme.THEME_FLAT_BLACK,
        Settings.PREF_THEME_KEY_BORDERS to true,
        Settings.PREF_NAVBAR_COLOR to true,
        Settings.PREF_SHOW_NUMBER_ROW to true,
        Settings.PREF_SHOW_EMOJI_KEY to true,
        Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY to false,
        Settings.PREF_SHOW_HINTS to true,
        // strip: tools while nothing is suggested, words while typing, dictation at the right end
        Settings.PREF_TOOLBAR_MODE to ToolbarMode.EXPANDABLE.name,
        Settings.PREF_AUTO_SHOW_TOOLBAR to true,
        Settings.PREF_AUTO_HIDE_TOOLBAR to true,
        Settings.PREF_TOOLBAR_KEYS to toolbarPref(TOOLBAR_KEYS),
        Settings.PREF_PINNED_TOOLBAR_KEYS to toolbarPref(PINNED_KEYS),
        Settings.PREF_LAYOUT_PREFIX + LayoutType.FUNCTIONAL.name to FUNCTIONAL_LAYOUT,
        Settings.PREF_FONT_SCALE to 1f,
        Settings.PREF_HINT_FONT_SCALE to 1f,
        portraitKey(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 2) to heightScale(),
        portraitKey(Settings.PREF_BOTTOM_ROW_SCALE_PREFIX, 2) to FlatStyle.BOTTOM_ROW_SCALE,
        portraitKey(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 2) to 1f,
        portraitKey(Settings.PREF_KEY_GAP_SCALE_PREFIX, 2) to 1f,
        portraitKey(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 3) to 1f,
    )

    val canRestore: Boolean get() = backup.getBoolean(BACKUP_PRESENT, false)

    fun apply() {
        val values = values()
        if (!canRestore) saveBackup(values.keys)
        val edit = keyboardPrefs.edit()
        values.forEach { (key, value) -> edit.put(key, value) }
        check(edit.commit())
        reload()
    }

    /** Puts back what [apply] replaced, including "was not set", and forgets the backup. */
    fun restore() {
        check(canRestore)
        val edit = keyboardPrefs.edit()
        values().keys.forEach { key ->
            val old = backup.all[VALUE_PREFIX + key]
            if (old == null) edit.remove(key) else edit.put(key, old)
        }
        check(edit.commit())
        check(backup.edit().clear().commit())
        reload()
    }

    private fun saveBackup(keys: Set<String>) {
        val current = keyboardPrefs.all
        val edit = backup.edit().clear().putBoolean(BACKUP_PRESENT, true)
        keys.forEach { key -> current[key]?.let { edit.put(VALUE_PREFIX + key, it) } }
        check(edit.commit())
    }

    /** Scale that makes the key area [FlatStyle.HEIGHT_TO_WIDTH] of the portrait width high. */
    private fun heightScale(): Float {
        val resources = context.resources
        val metrics = resources.displayMetrics
        val width = minOf(metrics.widthPixels, metrics.heightPixels)
        val height = maxOf(metrics.widthPixels, metrics.heightPixels)
        return heightScale(
            width, height,
            defaultHeight = resources.getDimension(R.dimen.config_default_keyboard_height),
            maxHeightFraction = resources.getFraction(R.fraction.config_max_keyboard_height, 1, 1),
            minHeightFraction = resources.getFraction(R.fraction.config_min_keyboard_height, 1, 1),
        )
    }

    private fun reload() {
        KeyboardIconsSet.needsReload = true
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    companion object {
        const val BACKUP_NAME = "agent_flat_preset_backup"
        const val FUNCTIONAL_LAYOUT = "functional_keys_flat"
        private const val BACKUP_PRESENT = "present"
        private const val VALUE_PREFIX = "value:"
        private const val NUMBER_ROW_HEIGHT_FACTOR = 1.33f // as in ResourceUtils.getDefaultKeyboardHeight
        private const val MIN_SCALE = 0.3f // range of the height setting
        private const val MAX_SCALE = 1.5f

        val TOOLBAR_KEYS = listOf(ToolbarKey.CLIPBOARD, ToolbarKey.EMOJI, ToolbarKey.AGENT)
        val PINNED_KEYS = listOf(ToolbarKey.DICTATE)

        /** Same format as the toolbar settings write: the enabled keys first and in order, then all others. */
        private fun toolbarPref(enabled: List<ToolbarKey>): String =
            (enabled.map { it to true } + ToolbarKey.entries.filterNot { it in enabled || it == ToolbarKey.CLOSE_HISTORY }.map { it to false })
                .joinToString(Separators.ENTRY) { (key, on) -> key.name + Separators.KV + on }

        private fun portraitKey(prefix: String, conditions: Int) = createPrefKeyForBooleanSettings(prefix, 0, conditions)

        private fun SharedPreferences.Editor.put(key: String, value: Any) {
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                else -> error("unsupported preference type")
            }
        }

        /**
         * Mirrors ResourceUtils.getDefaultKeyboardHeight with a number row for a portrait screen.
         * A negative [minHeightFraction] is relative to the width, as in the resource.
         */
        fun heightScale(width: Int, height: Int, defaultHeight: Float, maxHeightFraction: Float, minHeightFraction: Float): Float {
            val maxHeight = maxHeightFraction * height
            val minHeight = if (minHeightFraction < 0) -minHeightFraction * width else minHeightFraction * height
            val base = (defaultHeight * NUMBER_ROW_HEIGHT_FACTOR).coerceAtMost(maxHeight).coerceAtLeast(minHeight).toInt()
            if (base <= 0) return 1f
            return (FlatStyle.HEIGHT_TO_WIDTH * width / base).coerceIn(MIN_SCALE, MAX_SCALE)
        }
    }
}
