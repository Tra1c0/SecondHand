// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import dev.agentkeyboard.core.ConfigurationProfile
import dev.agentkeyboard.core.PreferenceValue
import dev.agentkeyboard.core.ProfileGroup
import dev.agentkeyboard.core.ProfileStore
import dev.agentkeyboard.core.RgbSettings
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardActionListener.SwipeAction
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlinx.serialization.json.*

/** Separate, deliberately small profile scopes. Never persists typing history or credentials. */
class KeyboardProfileStore(context: Context) : ProfileStore {
    private val keyboardPrefs = context.prefs()
    private val storage = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)

    /** [freeText]: any string up to that length is accepted (normalised elsewhere), [choices] are ignored. */
    private class Binding(val default: PreferenceValue, val choices: Set<String> = emptySet(), val freeText: Int = 0) {
        fun accepts(value: PreferenceValue) = when (default) {
            is PreferenceValue.Flag -> value is PreferenceValue.Flag
            is PreferenceValue.Choice -> value is PreferenceValue.Choice &&
                (if (freeText > 0) value.value.length <= freeText else value.value in choices)
            is PreferenceValue.Scale -> value is PreferenceValue.Scale && value.value.isFinite() && value.value in 0.1f..5f
        }
    }

    private val bindings = linkedMapOf(
        ProfileGroup.DESIGN to linkedMapOf(
            Settings.PREF_THEME_STYLE to Binding(PreferenceValue.Choice(Defaults.PREF_THEME_STYLE), KeyboardTheme.STYLES.toSet()),
            Settings.PREF_THEME_KEY_BORDERS to Binding(PreferenceValue.Flag(Defaults.PREF_THEME_KEY_BORDERS)),
            Settings.PREF_FONT_SCALE to Binding(PreferenceValue.Scale(Defaults.PREF_FONT_SCALE)),
            // stage 26: the whole RGB configuration as one text; "" (older profiles) = no own value
            RgbLighting.PREF to Binding(PreferenceValue.Choice(RgbSettings.DEFAULT.encode()), freeText = RgbSettings.MAX_ENCODED_LENGTH),
        ),
        ProfileGroup.LAYOUT to linkedMapOf(
            Settings.PREF_SHOW_NUMBER_ROW to Binding(PreferenceValue.Flag(Defaults.PREF_SHOW_NUMBER_ROW)),
            Settings.PREF_SHOW_EMOJI_KEY to Binding(PreferenceValue.Flag(Defaults.PREF_SHOW_EMOJI_KEY)),
            Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY to Binding(PreferenceValue.Flag(Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY)),
        ),
        ProfileGroup.GESTURES to linkedMapOf(
            AgentPreferences.SPACE_LONG_PRESS to Binding(PreferenceValue.Flag(false)),
            Settings.PREF_SPACE_HORIZONTAL_SWIPE to Binding(PreferenceValue.Choice(Defaults.PREF_SPACE_HORIZONTAL_SWIPE),
                setOf(SwipeAction.NONE, SwipeAction.MOVE_CURSOR, SwipeAction.SWITCH_LANGUAGE,
                    SwipeAction.TOGGLE_NUMPAD, SwipeAction.TOGGLE_DPAD).map { it.name }.toSet()),
            Settings.PREF_SPACE_VERTICAL_SWIPE to Binding(PreferenceValue.Choice(Defaults.PREF_SPACE_VERTICAL_SWIPE),
                SwipeAction.entries.map { it.name }.toSet()),
            Settings.PREF_DELETE_SWIPE to Binding(PreferenceValue.Flag(Defaults.PREF_DELETE_SWIPE)),
        ).apply {
            // INHERIT = no own value: the gesture follows the older settings above
            SpaceGesture.entries.forEach { gesture ->
                put(gesture.key, Binding(PreferenceValue.Choice(SpaceGestures.INHERIT),
                    SpaceGestures.choices(gesture).map { it.name }.toSet() + SpaceGestures.INHERIT))
            }
        },
    )

    val availableGroups: List<ProfileGroup> get() = bindings.keys.toList()
    fun hasSaved(group: ProfileGroup) = storage.contains(group.name)

    fun capture(group: ProfileGroup): ConfigurationProfile {
        val values = bindings.getValue(group).mapValues { (key, binding) ->
            when (val default = binding.default) {
                is PreferenceValue.Flag -> PreferenceValue.Flag(keyboardPrefs.getBoolean(key, default.value))
                is PreferenceValue.Choice ->
                    if (key == RgbLighting.PREF) PreferenceValue.Choice(RgbLighting.read(keyboardPrefs).encode())
                    else PreferenceValue.Choice(keyboardPrefs.getString(key, default.value) ?: default.value)
                is PreferenceValue.Scale -> PreferenceValue.Scale(keyboardPrefs.getFloat(key, default.value))
            }
        }
        return ConfigurationProfile(group = group, values = values).also(::validate)
    }

    override fun read(group: ProfileGroup): ConfigurationProfile? {
        val encoded = storage.getString(group.name, null) ?: return null
        return decode(encoded).also { require(it.group == group) }
    }

    override fun write(profile: ConfigurationProfile) {
        validate(profile)
        check(storage.edit().putString(profile.group.name, encode(profile)).commit())
    }

    fun saveCurrent(group: ProfileGroup) = write(capture(group))

    fun restoreSaved(group: ProfileGroup) {
        apply(requireNotNull(read(group)))
    }

    fun reset(group: ProfileGroup) {
        apply(ConfigurationProfile(group = group, values = bindings.getValue(group).mapValues { it.value.default }))
    }

    fun apply(profile: ConfigurationProfile) {
        // Complete validation precedes all mutations; a profile cannot toggle learning.
        validate(profile)
        val edit = keyboardPrefs.edit()
        profile.values.forEach { (key, value) ->
            when (value) {
                is PreferenceValue.Flag -> edit.putBoolean(key, value.value)
                is PreferenceValue.Choice ->
                    if (key in SpaceGestures.keys && value.value == SpaceGestures.INHERIT) edit.remove(key)
                    else if (key == RgbLighting.PREF) {
                        // invalid or out-of-range parts were clamped to their defaults when reading
                        if (value.value.isNotEmpty()) edit.putString(key, RgbSettings.parse(value.value).encode())
                    }
                    else edit.putString(key, value.value)
                is PreferenceValue.Scale -> edit.putFloat(key, value.value)
            }
        }
        check(edit.commit())
        KeyboardIconsSet.needsReload = true
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    fun exportSaved(group: ProfileGroup): String? = read(group)?.let(::encode)

    fun importSaved(encoded: String, expectedGroup: ProfileGroup? = null) {
        val profile = decode(encoded)
        require(expectedGroup == null || profile.group == expectedGroup)
        write(profile)
    }

    private fun validate(profile: ConfigurationProfile) {
        require(profile.formatVersion == 2)
        val allowed = requireNotNull(bindings[profile.group])
        require(profile.values.keys == allowed.keys)
        require(profile.values.all { (key, value) -> allowed.getValue(key).accepts(value) })
    }

    private fun encode(profile: ConfigurationProfile): String = buildJsonObject {
        put("formatVersion", profile.formatVersion)
        put("group", profile.group.name)
        put("values", buildJsonObject {
            profile.values.forEach { (key, value) ->
                put(key, when (value) {
                    is PreferenceValue.Flag -> JsonPrimitive(value.value)
                    is PreferenceValue.Choice -> JsonPrimitive(value.value)
                    is PreferenceValue.Scale -> JsonPrimitive(value.value)
                })
            }
        })
    }.toString()

    private fun decode(encoded: String): ConfigurationProfile {
        require(encoded.length <= 16_384)
        val root = Json.parseToJsonElement(encoded).jsonObject
        require(root.keys == setOf("formatVersion", "group", "values"))
        val version = root.getValue("formatVersion").jsonPrimitive
        require(!version.isString && version.intOrNull in setOf(1, 2))
        val groupValue = root.getValue("group").jsonPrimitive
        require(groupValue.isString)
        val group = ProfileGroup.valueOf(groupValue.content)
        var values = root.getValue("values").jsonObject.mapValues { (_, element) ->
            val primitive = element.jsonPrimitive
            when {
                primitive.isString -> PreferenceValue.Choice(primitive.content)
                primitive.booleanOrNull != null -> PreferenceValue.Flag(primitive.boolean)
                primitive.floatOrNull != null -> PreferenceValue.Scale(primitive.float)
                else -> error("Invalid profile value")
            }
        }
        if (version.int == 1 && group == ProfileGroup.GESTURES) {
            // Version 1 predates the opt-in wheel gesture. Never infer consent.
            require(values.keys == bindings.getValue(group).keys - AgentPreferences.SPACE_LONG_PRESS - SpaceGestures.keys)
            values = values + (AgentPreferences.SPACE_LONG_PRESS to PreferenceValue.Flag(false))
        }
        if (group == ProfileGroup.GESTURES && values.keys == bindings.getValue(group).keys - SpaceGestures.keys) {
            // Saved before the per-gesture space settings: every gesture keeps following the older settings.
            values = values + SpaceGestures.keys.associateWith { PreferenceValue.Choice(SpaceGestures.INHERIT) }
        }
        if (group == ProfileGroup.DESIGN && values.keys == bindings.getValue(group).keys - RgbLighting.PREF) {
            // saved before the RGB lighting: the profile leaves it as it is
            values = values + (RgbLighting.PREF to PreferenceValue.Choice(""))
        }
        values[RgbLighting.PREF]?.let { rgb ->
            // clamp an imported configuration field by field; a non-text value fails validation below
            if (rgb is PreferenceValue.Choice && rgb.value.isNotEmpty())
                values = values + (RgbLighting.PREF to PreferenceValue.Choice(RgbSettings.parse(rgb.value).encode()))
        }
        return ConfigurationProfile(group = group, values = values).also(::validate)
    }

    companion object {
        const val STORAGE_NAME = "agent_keyboard_profiles"
    }
}
