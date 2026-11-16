// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.agentkeyboard.core.ForcedCorrection
import dev.agentkeyboard.core.SnippetApps
import helium314.keyboard.latin.InputAttributes

/**
 * Stage 61 "Korrektur in Apps erzwingen": the apps the owner listed and the decision for one field.
 * The list is a string set of package names in the keyboard's normal preferences (the ones
 * `SettingsValues` reads), by default [ForcedCorrection.DEFAULT_PACKAGES]; an empty set the owner saved
 * stays empty. Read at every field start (`SettingsValues`), so a change counts from the next field
 * without a restart. Nothing here is logged; the rule itself is [ForcedCorrection].
 *
 * Called from `SettingsValues`, so nothing in here may throw.
 */
object ForcedApps {
    /** More than the owner will ever list; keeps the preference small. */
    const val MAX_APPS = 50

    /** The listed packages (valid names only); the default until the owner saved a list. */
    @JvmStatic fun read(prefs: SharedPreferences): Set<String> = runCatching {
        prefs.getStringSet(AgentPreferences.FORCED_CORRECTION_APPS, null)
            ?.filter { SnippetApps.isValidPackage(it) }?.toSet() ?: ForcedCorrection.DEFAULT_PACKAGES
    }.getOrDefault(ForcedCorrection.DEFAULT_PACKAGES)

    @JvmStatic fun isListed(prefs: SharedPreferences, packageName: String?): Boolean =
        packageName != null && packageName in read(prefs)

    /** Lists [packageName] (a valid package name, at most [MAX_APPS] entries); false when it was not added. */
    fun add(prefs: SharedPreferences, packageName: String): Boolean {
        if (!SnippetApps.isValidPackage(packageName)) return false
        val current = read(prefs)
        if (packageName in current) return true
        if (current.size >= MAX_APPS) return false
        prefs.edit { putStringSet(AgentPreferences.FORCED_CORRECTION_APPS, current + packageName) }
        return true
    }

    fun remove(prefs: SharedPreferences, packageName: String) {
        val current = read(prefs)
        if (packageName !in current) return
        // an empty set is stored as such: the owner removed the default on purpose
        prefs.edit { putStringSet(AgentPreferences.FORCED_CORRECTION_APPS, current - packageName) }
    }

    /**
     * Whether the field described by [attributes] is forced: its app is listed and the field qualifies
     * ([ForcedCorrection.eligible], on `InputAttributes.mInputType` as the engine uses it, so a Firefox
     * workaround counts like the engine does). Never a password field of the engine's own definition.
     */
    @JvmStatic fun forces(prefs: SharedPreferences, attributes: InputAttributes): Boolean = runCatching {
        !attributes.mIsPasswordField && isListed(prefs, attributes.mTargetApplicationPackageName)
            && ForcedCorrection.eligible(attributes.mInputType, attributes.imeOptions)
    }.getOrDefault(false)
}
