// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.view.inputmethod.InputMethodSubtype
import dev.agentkeyboard.core.AliasKind
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
 *
 * Stage 52: `@lang` is an area of the unified command system ([CommandSystem]): `@lang whatsapp
 * german`, `@lang apps`, `@lang whatsapp del`, chains; also as `@sprache`. The old forms (`@lang`,
 * `@lang spanisch`, `@deutsch`) keep these chips; Enter runs a language word with exactly one chip.
 */
object LanguageCommand : KeyboardCommands.Command {
    override val name = "lang"
    override val aliases: List<String> get() = CommandSystem.ALIASES[name].orEmpty()
    /** Stage 52: app and language words are not corrected. */
    override val protectsArgument = true
    /** Review of stage 52: only words that read as a command (`@lang xyz` is text and corrected as usual). */
    override fun protects(context: Context, call: CommandCall) = CommandSystem.protects(context, call)

    /** The real source of [subtypes]; tests put it back with this. */
    internal val defaultSubtypes: () -> List<InputMethodSubtype> = {
        runCatching { SubtypeSettings.getEnabledSubtypes(true) }.getOrDefault(emptyList())
    }

    /** The enabled keyboard subtypes, in the order of the language settings; replaced in tests. */
    @JvmStatic var subtypes: () -> List<InputMethodSubtype> = defaultSubtypes

    /** Built from the enabled languages when the keyboard reads its settings; read by the suggestion thread too. */
    @Volatile private var matcher: LanguageMatcher? = null
    /** What [matcher] was built from: the enabled language tags and the command names. */
    @Volatile private var matcherKey: Pair<List<String>, List<String>>? = null

    /** The matcher in use, for tests. */
    internal val currentMatcher: LanguageMatcher? get() = matcher

    override fun description(context: Context): String {
        val enabled = subtypes().map { it.locale().language }.filter { it != SubtypeLocaleUtils.NO_LANGUAGE }.distinct()
            .joinToString(", ") { language ->
                val short = listOf(language) + LanguageNames.SHORT_FORMS[language].orEmpty().take(1)
                "${nativeName(language)} (${short.joinToString(", ")})"
            }.ifEmpty { context.getString(R.string.agent_command_lang_none) }
        return context.getString(R.string.agent_command_lang, KeyboardCommands.prefix(context).toString(), enabled)
    }

    override fun load(context: Context) {
        InstalledApps.refresh(context) // stage 52: app names for "@lang whatsapp german"
        val locales = subtypes().map { it.locale() }.filter { it.language != SubtypeLocaleUtils.NO_LANGUAGE }
        // stage 52: the second names of the commands (`@sprache`, `@gruppe`) are reserved as well
        val names = KeyboardCommands.registry.map { it.name } + KeyboardCommands.registry.flatMap { it.aliases }
        // ICU names and the closeness of every alias to every command name cost time on the main
        // thread: build again only when the enabled languages or the commands changed
        val key = locales.map { it.toLanguageTag() } to names
        if (key == matcherKey && matcher != null) return
        val languages = locales.groupBy { it.language }.map { (language, sameLanguage) ->
            // a subtype's own country (en-GB, es-MX) adds its names
            val kinds = mutableMapOf<String, AliasKind>()
            for (locale in sameLanguage) for ((alias, kind) in LanguageNames.classifiedAliases(locale.toLanguageTag()))
                kinds[alias] = maxOf(kind, kinds[alias] ?: kind)
            LanguageMatcher.Language(language, kinds)
        }
        matcher = LanguageMatcher(languages, names)
        matcherKey = key
    }

    override fun claims(word: String): Boolean = matcher?.match(word)?.isNotEmpty() == true

    /** Stage 52: the enabled languages (ids like "es") [word] names, best first. */
    internal fun matchLanguages(word: String, exactOnly: Boolean): List<String> = matcher?.match(word, exactOnly).orEmpty()

    /** Stage 52: the enabled subtype for [language]: [current] when it is one of it, else the first in the settings. */
    internal fun subtypeFor(language: String, current: InputMethodSubtype?): InputMethodSubtype? {
        val enabled = subtypes().filter { it.locale().language == language }
        return enabled.firstOrNull { it == current } ?: enabled.firstOrNull()
    }

    /**
     * Stage 52: a language word (`@deutsch`, `@lang spanisch`) with exactly one chip switches like its
     * tap. Review: only an exact name of the language (`@Fran`, `@Dan` may be names and are sent as
     * usual); when it is in use already, or has several layouts, Enter is taken and says so.
     */
    override fun onEnter(host: KeyboardCommands.Host, call: CommandCall): Boolean {
        CommandSystem.onEnter(host, call)?.let { return it }
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) return false
        if (call.claimed == null && call.argument.isBlank()) return false
        // words after "@lang" that read as no command are text
        if (!CommandSystem.isLegacy(host, call)) return false
        val languages = matchLanguages(call.claimed ?: call.argument.trim(), exactOnly = true)
        if (languages.isEmpty()) return false
        val chips = chips(host, call)
        if (chips.isEmpty()) return false
        val current = host.currentSubtype
        val inUse = current != null && current.locale().language in languages
        val chip = chips.singleOrNull()?.takeIf { it.kind == KeyboardCommands.Chip.Kind.ACTION }
        if (chip != null && !inUse) {
            if (host.canReplaceNow) chip.onTap()
            return true
        }
        // already in use (another layout of it is no switch on Enter), the only language, or several layouts: the text stays
        val hint = when {
            inUse -> host.context.getString(R.string.agent_command_lang_active, label(host.context, current!!, subtypes()).removePrefix("🌐 "))
            chips.size > 1 -> host.context.getString(R.string.agent_unified_lang_choose)
            else -> chips.first().description.toString()
        }
        android.widget.Toast.makeText(host.context, hint, android.widget.Toast.LENGTH_SHORT).show()
        return true
    }

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) return emptyList()
        // stage 52: apps, groups, actions and chains belong to the unified command system
        CommandSystem.chips(host, call)?.let { return it }
        // the language words take no argument: "@sp hola" is text again (the parser no longer offers it)
        if (call.claimed != null && call.argument.isNotBlank()) return emptyList()
        val enabled = subtypes()
        val word = call.claimed ?: call.argument.trim()
        // after the space a word is finished: only an exact name keeps its chip ("@sp ", not "@spnaish " or "@ale ")
        val finished = call.claimed != null && call.text.endsWith(' ')
        val languages = if (word.isEmpty()) null else matcher?.match(word, exactOnly = finished).orEmpty()
        val chosen = enabled.filter { languages == null || it.locale().language in languages }
        if (chosen.isEmpty()) return emptyList()
        val context = host.context
        if (enabled.size <= 1) return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_lang_single),
            context.getString(R.string.agent_command_lang_single_description), KeyboardCommands.Chip.Kind.MUTED) {
            SnipCommand.openSettings(context, SettingsDestination.Languages)
        })
        // the language in use gets no chip: its tap would only delete the text
        val current = host.currentSubtype
        val others = chosen.filter { it != current }
        if (others.isEmpty()) {
            val label = label(context, chosen.first(), enabled).removePrefix("🌐 ")
            val text = context.getString(R.string.agent_command_lang_active, label)
            // a tap keeps the text and changes nothing
            return listOf(KeyboardCommands.Chip(text, text, KeyboardCommands.Chip.Kind.MUTED) {})
        }
        // the order of the matched languages, then of the settings
        val ordered = if (languages == null) others else others.sortedBy { languages.indexOf(it.locale().language) }
        return ordered.map { subtype ->
            val label = label(context, subtype, enabled)
            KeyboardCommands.Chip(label, context.getString(R.string.agent_command_lang_switch, label.removePrefix("🌐 "))) {
                // the language may have been switched off since the chip was built: then nothing happens
                if (subtype !in subtypes()) {
                    android.widget.Toast.makeText(context, R.string.agent_command_lang_gone, android.widget.Toast.LENGTH_SHORT).show()
                    return@Chip
                }
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

    internal fun nativeName(language: String): String {
        val locale = java.util.Locale.forLanguageTag(language)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
