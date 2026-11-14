// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.agentkeyboard.core.CommandGrammar
import dev.agentkeyboard.core.SnippetApps
import helium314.keyboard.agent.AgentStorageContexts
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.prefs

/**
 * Stage 52: what the unified commands set per app, in the keyboard's device-protected storage
 * (file [FILE], not the keyboard settings, so a write does not reload the keyboard; read at every
 * field start, also before the first unlock). Only the user's own commands write here, each after a tap.
 *
 * - Language per app (`@lang whatsapp german`): package name (or [ALL]) to the subtype as HeliBoard
 *   stores it. It applies at the start of a field of that app even with "Sprache pro App" off
 *   ([subtypeForApp]); with the switch on, HeliBoard's own record of the last language used in the
 *   app comes first, and a command removes that record for its app, so the latest choice wins.
 * - Format guard per app (`@guard banking off`): package name to on/off; an app without an entry
 *   follows the switch on the settings page ([guardAllows]).
 * - Own groups (`@group messenger whatsapp signal`): name to package names.
 *
 * The memory's own list of excluded apps (stage 46, [helium314.keyboard.agent.Memory]) stays where it is.
 */
object CommandAppSettings {
    const val FILE = "agent_command_settings"
    /** The entry for every app without an own one (`@lang all german`). */
    const val ALL = "*"
    private const val LANGUAGE = "language:"
    private const val GUARD = "guard:"
    private const val GROUP = "group:"
    const val MAX_APPS = 100
    const val MAX_GROUPS = 20
    const val MAX_MEMBERS = 30

    private fun store(context: Context): SharedPreferences =
        AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun isAppKey(packageName: String) = packageName == ALL || SnippetApps.isValidPackage(packageName)

    private fun entries(context: Context, prefix: String): Map<String, Any?> = runCatching {
        store(context).all.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
    }.getOrDefault(emptyMap())

    // ---- language per app ----

    /** Package name (or [ALL]) to the stored subtype. */
    fun languages(context: Context): Map<String, String> =
        entries(context, LANGUAGE).mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    fun language(context: Context, packageName: String): String? =
        runCatching { store(context).getString(LANGUAGE + packageName, null) }.getOrNull()

    /** Stores [subtype] (HeliBoard's form) for [packageName], or removes the entry; false when full or invalid. */
    fun setLanguage(context: Context, packageName: String, subtype: String?): Boolean {
        if (!isAppKey(packageName)) return false
        val current = languages(context)
        if (subtype != null && packageName !in current && current.size >= MAX_APPS) return false
        return runCatching { store(context).edit(commit = true) {
            if (subtype == null) remove(LANGUAGE + packageName) else putString(LANGUAGE + packageName, subtype)
        } }.isSuccess
    }

    /** HeliBoard's own record of the language last used in [packageName] ("Sprache pro App"), or null. */
    fun savedSubtype(context: Context, packageName: String): String? =
        context.prefs().getString(Settings.PREF_SAVED_APP_SUBTYPE_PREFIX + packageName, null)

    fun setSavedSubtype(context: Context, packageName: String, subtype: String?) {
        if (packageName == ALL) return
        context.prefs().edit {
            if (subtype == null) remove(Settings.PREF_SAVED_APP_SUBTYPE_PREFIX + packageName)
            else putString(Settings.PREF_SAVED_APP_SUBTYPE_PREFIX + packageName, subtype)
        }
    }

    /** Package names with a record of HeliBoard's own per-app language. */
    fun savedSubtypeApps(context: Context): List<String> =
        context.prefs().all.keys.filter { it.startsWith(Settings.PREF_SAVED_APP_SUBTYPE_PREFIX) }
            .map { it.removePrefix(Settings.PREF_SAVED_APP_SUBTYPE_PREFIX) }

    /**
     * The subtype for a field of [packageName] when it starts (`LatinIME.onStartInputInternal`): HeliBoard's
     * own per-app language when that switch is on, else the language a command set for the app, else
     * the one set for all apps. Null when none applies or the language is no longer enabled.
     */
    @JvmStatic fun subtypeForApp(context: Context, settings: Settings, packageName: String?): RichInputMethodSubtype? {
        settings.getSubtypeForApp(packageName)?.let { return it }
        if (packageName.isNullOrEmpty()) return null
        val stored = language(context, packageName) ?: language(context, ALL) ?: return null
        return resolve(stored)
    }

    /**
     * Review of stage 52 (P219): the keyboard switched to [subtype] in the field [editorInfo] (language
     * key, space bar swipe, system picker). With "Sprache pro App" on HeliBoard records that for the app
     * itself. With the switch off, a language a command set for the app would win again when the app
     * restarts the same field (e.g. after sending in a messenger, `onStartInput` with restarting): the
     * switch is kept for this field like a language command's ([SubtypeChoice.remember]); a new field of
     * the app starts in the app's language again. Not when the switch went to that language itself.
     * Second review: not the automatic switch of a field start either (hint locales, the app's own
     * language; [helium314.keyboard.agent.SubtypeSwitchLog.FIELD_START]), only a choice of the user;
     * called from the engine, so nothing in here may throw.
     */
    @JvmStatic fun onSwitchedInField(context: Context, editorInfo: android.view.inputmethod.EditorInfo?,
                                     subtype: android.view.inputmethod.InputMethodSubtype) {
        runCatching {
            val packageName = editorInfo?.packageName?.takeIf { it.isNotEmpty() } ?: return
            if (helium314.keyboard.agent.SubtypeSwitchLog.lastReason() in helium314.keyboard.agent.SubtypeSwitchLog.FIELD_START) return
            if (context.prefs().getBoolean(Settings.PREF_SAVE_SUBTYPE_PER_APP, helium314.keyboard.latin.settings.Defaults.PREF_SAVE_SUBTYPE_PER_APP)) return
            val stored = language(context, packageName) ?: language(context, ALL) ?: return
            if (resolve(stored)?.rawSubtype == subtype) return
            SubtypeChoice.remember(editorInfo, subtype)
        }
    }

    /** The enabled subtype a stored one stands for: the same, else one of its language (as HeliBoard does). */
    fun resolve(stored: String): RichInputMethodSubtype? = runCatching {
        val subtype = stored.toSettingsSubtype()
        val enabled = subtype.toEnabledSubtype() ?: RichInputMethodManager.getInstance().findSubtypeForHintLocale(subtype.locale)
        enabled?.let { RichInputMethodSubtype.get(it) }
    }.getOrNull()

    // ---- format guard per app ----

    fun guards(context: Context): Map<String, Boolean> =
        entries(context, GUARD).mapNotNull { (key, value) -> (value as? Boolean)?.let { key to it } }.toMap()

    /** One app's entry, read as one key (review of stage 52: asked for every strip, no copy of the store). */
    fun guard(context: Context, packageName: String): Boolean? = runCatching {
        val store = store(context)
        if (store.contains(GUARD + packageName)) store.getBoolean(GUARD + packageName, true) else null
    }.getOrNull()

    fun setGuard(context: Context, packageName: String, on: Boolean?): Boolean {
        if (packageName == ALL || !isAppKey(packageName)) return false
        val current = guards(context)
        if (on != null && packageName !in current && current.size >= MAX_APPS) return false
        return runCatching { store(context).edit(commit = true) {
            if (on == null) remove(GUARD + packageName) else putBoolean(GUARD + packageName, on)
        } }.isSuccess
    }

    /** Whether the format guard may show its hints in [packageName]: its own entry, else the switch. */
    @JvmStatic fun guardAllows(context: Context, packageName: String?): Boolean =
        packageName?.let { guard(context, it) } ?: helium314.keyboard.agent.FormatGuardChips.isEnabled(context)

    // ---- own groups ----

    /** Group name to package names, by name. */
    fun groups(context: Context): Map<String, List<String>> =
        entries(context, GROUP).mapNotNull { (key, value) ->
            (value as? String)?.let { key to it.split(',').filter { pkg -> SnippetApps.isValidPackage(pkg) } }
        }.sortedBy { it.first }.toMap()

    /** Stores [packages] as the group [name], or deletes it with null; false when full or invalid. */
    fun setGroup(context: Context, name: String, packages: List<String>?): Boolean {
        if (!CommandGrammar.isValidGroupName(name)) return false
        if (packages != null && (packages.isEmpty() || packages.size > MAX_MEMBERS || packages.any { !SnippetApps.isValidPackage(it) })) return false
        val current = groups(context)
        if (packages != null && name !in current && current.size >= MAX_GROUPS) return false
        return runCatching { store(context).edit(commit = true) {
            if (packages == null) remove(GROUP + name) else putString(GROUP + name, packages.distinct().joinToString(","))
        } }.isSuccess.also { CommandSystem.dropReadings() } // a group name is a word of the commands
    }

    /** Tests only: everything gone. */
    internal fun clearForTest(context: Context) { store(context).edit(commit = true) { clear() }; CommandSystem.dropReadings() }
}
