// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.utils.ToolbarKey.*

/**
 * Fork, stage 85: the model of the fixed bar ("Leiste wie Gboard"), without any view. The bar holds an ordered
 * list of tools (at most [MAX]); the all-tools grid offers every [ToolbarKey] in [offered]. Nothing here touches
 * the editor, the network or any text.
 *
 * Storage: the preference [AgentPreferences.BAR_TOOLS] in the format of the old toolbar preferences
 * (`NAME:true|NAME:false|...`, the enabled entries in bar order), so that the settings reorder dialog edits it
 * unchanged. It is written only when the user edits the bar. While it is missing, [read] derives the bar from
 * what the user had before (see [migrate]), without writing anything.
 */
object BarTools {
    /** More tools cannot be placed in the bar; they live in the grid. */
    const val MAX = 8
    /** Up to this many tools sit at full size; more use the compact row. */
    const val FULL_SIZE_MAX = 5
    /** How many of the old toolbar order go into the bar of a user who never edited it. */
    const val MIGRATED_FROM_ORDER = 5

    /** Tools the voice and dictation keys: the last one in the bar sits in the circle at the right end. */
    val MIC_KEYS = setOf(VOICE, DICTATE)

    /** Every tool the bar and the grid can hold: all keys except the internal "close history" key of the clipboard view. */
    val offered: List<ToolbarKey> = ToolbarKey.entries.filterNot { it == CLOSE_HISTORY }

    /** Tools whose own controllers refuse password fields, incognito and a locked device; the bar dims them there. */
    val PROTECTED = setOf(AGENT, SKILL_1, SKILL_2, SKILL_3, DICTATE, PROMPTS, CHAR_ART)

    /**
     * The order of the all-tools grid: the fork's own actions first (they are the ones used most), then the common
     * text tools, then the rest in the order of [ToolbarKey]. Contains every tool of [offered] exactly once.
     */
    val gridOrder: List<ToolbarKey> = run {
        val first = listOf(AGENT, DICTATE, SKILL_1, SKILL_2, SKILL_3, PROMPTS, CHAR_ART, CLIPBOARD, EMOJI, VOICE, UNDO, REDO,
            COPY, PASTE, CUT, SELECT_ALL, SELECT_WORD, SETTINGS, DESIGN, ONE_HANDED)
        first + offered.filterNot { it in first }
    }

    /** The store is not offered in number layouts, as the popup key of the comma key (`PromptsKey.shown`); the character art has no such rule. */
    val NOT_IN_NUMBER_LAYOUTS = setOf(PROMPTS)

    /** Tools that destroy something with one tap: the grid does not offer them unless the user had them in a list before. */
    val DESTRUCTIVE = setOf(CLEAR_CLIPBOARD)

    fun isCompact(count: Int) = count > FULL_SIZE_MAX

    /** The standard bar ("Zurücksetzen", stage 88): what [migrate] makes of the default old toolbar preferences. */
    val standard: List<ToolbarKey> by lazy { migrate(listOf(AGENT, SETTINGS, VOICE, CLIPBOARD, UNDO, REDO, SELECT_WORD, COPY, PASTE, LEFT, RIGHT), listOf(AGENT)) }

    /**
     * What the bar shows for a user who never edited it: the first [MIGRATED_FROM_ORDER] enabled keys of the old
     * toolbar order, then the pinned keys that are not among them (they were always visible, so they stay),
     * at most [MAX]. A microphone key moves to the end so that it sits in the circle at the right.
     */
    fun migrate(enabled: List<ToolbarKey>, pinned: List<ToolbarKey>): List<ToolbarKey> {
        val bar = enabled.filter { it in offered }.distinct().take(MIGRATED_FROM_ORDER).toMutableList()
        pinned.filter { it in offered }.distinct().forEach { if (it !in bar && bar.size < MAX) bar.add(it) }
        if (bar.isNotEmpty() && bar.last() !in MIC_KEYS) {
            val mic = bar.firstOrNull { it == VOICE } ?: bar.firstOrNull { it == DICTATE }
            if (mic != null) { bar.remove(mic); bar.add(mic) }
        }
        return bar
    }

    /**
     * The enabled tools of a stored value, in order, without duplicates and unknown names (a tool a later version dropped), at
     * most [MAX]. Null when there is no value or it is garbled (blank, or an entry that is not `NAME:true` or `NAME:false`): the
     * caller then derives the bar from the old preferences, and the value stays as it is until the user edits.
     */
    fun parse(stored: String?): List<ToolbarKey>? {
        if (stored.isNullOrBlank()) return null
        val entries = stored.split(Separators.ENTRY).map { it.split(Separators.KV) }
        if (entries.any { it.size != 2 || it[1] != "true" && it[1] != "false" }) return null
        return entries.mapNotNull { (name, state) ->
            if (state != "true") return@mapNotNull null
            ToolbarKey.entries.firstOrNull { it.name == name }
        }.filter { it in offered }.distinct().take(MAX)
    }

    /** The bar of this user: the stored choice, or the migration of the old toolbar preferences. Never writes. */
    fun read(prefs: SharedPreferences): List<ToolbarKey> =
        parse(prefs.getString(AgentPreferences.BAR_TOOLS, null)) ?: migrated(prefs)

    fun isCustomised(prefs: SharedPreferences) = parse(prefs.getString(AgentPreferences.BAR_TOOLS, null)) != null

    private fun migrated(prefs: SharedPreferences) = migrate(getEnabledToolbarKeys(prefs), getPinnedToolbarKeys(prefs))

    /** The stored format of [bar]: the bar in order as `true`, then every other offered tool as `false`. */
    fun format(bar: List<ToolbarKey>): String {
        val inBar = bar.distinct()
        return (inBar.map { it.name + Separators.KV + true } + offered.filter { it !in inBar }.map { it.name + Separators.KV + false })
            .joinToString(Separators.ENTRY)
    }

    /** What the settings dialog shows as the current value: the stored choice or the migration, in the stored format. */
    fun currentFormatted(prefs: SharedPreferences) = format(read(prefs))

    /** Stores [bar] (limited to [MAX]); the only place that writes the preference. */
    fun write(prefs: SharedPreferences, bar: List<ToolbarKey>) {
        prefs.edit { putString(AgentPreferences.BAR_TOOLS, format(bar.filter { it in offered }.distinct().take(MAX))) }
    }

    // ---- edits of the list, as the edit mode and the settings use them (pure: the caller writes the result) ----

    /** [bar] with [key] at [index] (clamped); other tools move over. Unchanged when [key] is there already or the bar is full. */
    fun insert(bar: List<ToolbarKey>, key: ToolbarKey, index: Int): List<ToolbarKey> {
        if (key !in offered || key in bar || bar.size >= MAX) return bar
        return bar.toMutableList().apply { add(index.coerceIn(0, size), key) }
    }

    /** [bar] without [key]. */
    fun remove(bar: List<ToolbarKey>, key: ToolbarKey): List<ToolbarKey> = bar.filterNot { it == key }

    /** [bar] with [key] moved to [index] (position in the list after the move, clamped); unchanged when [key] is not in it. */
    fun move(bar: List<ToolbarKey>, key: ToolbarKey, index: Int): List<ToolbarKey> {
        if (key !in bar) return bar
        val rest = bar.filterNot { it == key }
        return rest.toMutableList().apply { add(index.coerceIn(0, size), key) }
    }

    /** [bar] with [key] at the first free place, i.e. at the end; unchanged when it is there or the bar is full. */
    fun add(bar: List<ToolbarKey>, key: ToolbarKey): List<ToolbarKey> = insert(bar, key, bar.size)

    /** [key] one place to the left or right ([delta] -1 or 1); unchanged at the ends. */
    fun shift(bar: List<ToolbarKey>, key: ToolbarKey, delta: Int): List<ToolbarKey> {
        val at = bar.indexOf(key)
        if (at < 0 || at + delta !in bar.indices) return bar
        return move(bar, key, at + delta)
    }

    // ---- availability ----

    /** Why a tool is shown in the "off" look (stage 88): the real reason of the check that blocks it. */
    enum class Reason { PRIVATE_FIELD, INCOGNITO_SWITCH, LOCKED_DEVICE, NUMBER_LAYOUT }

    /** The reason [key] is unavailable in the current field, or null when it is available. */
    fun unavailableReason(key: ToolbarKey, incognitoOrProtectedField: Boolean, locked: Boolean, numberLayout: Boolean = false, alwaysIncognito: Boolean = false): Reason? = when {
        key in PROTECTED && locked -> Reason.LOCKED_DEVICE
        // the user's own switch "Always incognito" makes every field incognito: say that, not "private field"
        key in PROTECTED && incognitoOrProtectedField -> if (alwaysIncognito) Reason.INCOGNITO_SWITCH else Reason.PRIVATE_FIELD
        key in NOT_IN_NUMBER_LAYOUTS && numberLayout -> Reason.NUMBER_LAYOUT
        else -> null
    }

    /** Whether [key] is dimmed in the current field: the tools that refuse password fields, incognito and a locked device. */
    fun isUnavailable(key: ToolbarKey, incognitoOrProtectedField: Boolean, locked: Boolean, numberLayout: Boolean = false) =
        unavailableReason(key, incognitoOrProtectedField, locked, numberLayout) != null
}
