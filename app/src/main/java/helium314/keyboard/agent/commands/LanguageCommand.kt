// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.view.inputmethod.InputMethodSubtype
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.LanguageMatcher
import dev.agentkeyboard.core.LanguageNames
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.mainLayoutNameOrQwerty
import helium314.keyboard.settings.SettingsDestination

/**
 * `@lang` and the language words (stage 24g): `@sp`, `@deutsch`, `@spnaish`, `@usa` … name one of
 * the enabled keyboard languages ([LanguageMatcher], aliases from [LanguageNames]). The command
 * claims such words as its own ([claims]); command names always win ([dev.agentkeyboard.core.CommandSyntax]).
 * A chip per enabled subtype of the named language; its tap removes the typed command in one commit
 * and only then switches the keyboard ([KeyboardCommands.Host.switchSubtype]), like the language key.
 */
object LanguageCommand : KeyboardCommands.Command {
    override val name = "lang"

    /** The enabled keyboard subtypes, in the order of the language settings; replaced in tests. */
    @JvmStatic var subtypes: () -> List<InputMethodSubtype> = {
        runCatching { SubtypeSettings.getEnabledSubtypes(true) }.getOrDefault(emptyList())
    }

    /** Built from the enabled languages when the keyboard reads its settings; read by the suggestion thread too. */
    @Volatile private var matcher: LanguageMatcher? = null

    override fun description(context: Context): String {
        val enabled = subtypes().map { it.locale().language }.filter { it != SubtypeLocaleUtils.NO_LANGUAGE }.distinct()
            .joinToString(", ") { language ->
                val short = listOf(language) + LanguageNames.SHORT_FORMS[language].orEmpty().take(1)
                "${nativeName(language)} (${short.joinToString(", ")})"
            }.ifEmpty { context.getString(R.string.agent_command_lang_none) }
        return context.getString(R.string.agent_command_lang, KeyboardCommands.prefix(context).toString(), enabled)
    }

    override fun load(context: Context) {
        val languages = subtypes().map { it.locale() }.filter { it.language != SubtypeLocaleUtils.NO_LANGUAGE }
            .groupBy { it.language }
            .map { (language, locales) ->
                // a subtype's own country (en-GB, es-MX) adds its names
                LanguageMatcher.Language(language, locales.flatMap { LanguageNames.aliases(it.toLanguageTag()) })
            }
        matcher = LanguageMatcher(languages, KeyboardCommands.registry.map { it.name })
    }

    override fun claims(word: String): Boolean = matcher?.match(word)?.isNotEmpty() == true

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) return emptyList()
        // the language words take no argument: "@sp hola" is text again
        if (call.claimed != null && call.argument.isNotBlank()) return emptyList()
        val enabled = subtypes()
        val word = call.claimed ?: call.argument.trim()
        val languages = if (word.isEmpty()) null else matcher?.match(word).orEmpty()
        val chosen = enabled.filter { languages == null || it.locale().language in languages }
        if (chosen.isEmpty()) return emptyList()
        val context = host.context
        if (enabled.size <= 1) return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_lang_single),
            context.getString(R.string.agent_command_lang_single_description), KeyboardCommands.Chip.Kind.MUTED) {
            SnipCommand.openSettings(context, SettingsDestination.Languages)
        })
        // the order of the matched languages, then of the settings
        val ordered = if (languages == null) chosen else chosen.sortedBy { languages.indexOf(it.locale().language) }
        return ordered.map { subtype ->
            val label = label(context, subtype, enabled)
            KeyboardCommands.Chip(label, context.getString(R.string.agent_command_lang_switch, label.removePrefix("🌐 "))) {
                // first the command goes, in the editor it was typed in; then the keyboard switches
                if (host.replace(call, "")) host.switchSubtype(subtype)
            }
        }
    }

    /** "🌐 Español (ES)"; with the layout when the same language and country is enabled more than once. */
    internal fun label(context: Context, subtype: InputMethodSubtype, enabled: List<InputMethodSubtype>): String {
        val locale = subtype.locale()
        if (locale.language == SubtypeLocaleUtils.NO_LANGUAGE) return "🌐 " + subtype.displayName()
        val code = locale.country.ifEmpty { locale.language }.uppercase(java.util.Locale.ROOT)
        val language = nativeName(locale.language)
        val twin = enabled.count { it.locale() == locale } > 1
        if (!twin) return context.getString(R.string.agent_command_lang_chip, language, code)
        val layout = subtype.mainLayoutNameOrQwerty().let { SubtypeLocaleUtils.getMainLayoutDisplayName(it) ?: subtype.displayName() }
        return context.getString(R.string.agent_command_lang_chip_layout, language, code, layout)
    }

    private fun nativeName(language: String): String {
        val locale = java.util.Locale.forLanguageTag(language)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
