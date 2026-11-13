// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.text.Normalizer
import java.util.Locale

/**
 * Stage 24g: which keyboard language a typed word like `sp`, `deutsch`, `inglés` or `spnaish`
 * names. Pure matching: the aliases of a language, how a typed word is compared (case, accents,
 * typos) and which languages it names. Nothing here switches anything.
 */
object LanguageNames {
    /** Words that name the language command itself rather than one language: all languages. */
    val GENERIC: Set<String> = setOf("lang", "language", "languages", "sprache", "sprachen", "idioma", "idiomas")

    /** Short forms and names [Locale] does not know, by ISO 639-1 code; kept small on purpose. */
    val SHORT_FORMS: Map<String, List<String>> = mapOf(
        "de" to listOf("ger"),
        "en" to listOf("eng", "england", "uk", "usa", "america", "amerika", "britain", "britannien"),
        "es" to listOf("sp", "esp", "castellano"),
        "fr" to listOf("fre"),
        "nl" to listOf("dut", "holland"),
    )

    /**
     * Countries whose main language is the key (ISO 3166 alpha-2). A curated list instead of every
     * country that uses a language somewhere, so that country names stay unambiguous.
     */
    val COUNTRIES: Map<String, List<String>> = mapOf(
        "de" to listOf("DE", "AT", "CH", "LI"),
        "en" to listOf("GB", "US", "IE", "AU", "NZ", "CA"),
        "es" to listOf("ES", "MX", "AR", "CO", "CL", "PE", "VE", "CU", "UY", "EC", "BO", "PY", "GT", "CR", "PA", "DO"),
        "fr" to listOf("FR", "BE", "MC"),
        "it" to listOf("IT", "SM"),
        "pt" to listOf("PT", "BR", "AO", "MZ"),
        "nl" to listOf("NL"),
        "pl" to listOf("PL"), "cs" to listOf("CZ"), "sk" to listOf("SK"), "sl" to listOf("SI"),
        "hr" to listOf("HR"), "hu" to listOf("HU"), "ro" to listOf("RO"), "bg" to listOf("BG"),
        "sv" to listOf("SE"), "da" to listOf("DK"), "nb" to listOf("NO"), "fi" to listOf("FI"),
        "el" to listOf("GR"), "tr" to listOf("TR"), "ru" to listOf("RU"), "uk" to listOf("UA"),
    )

    /** Languages the names of every language are read in, besides the language itself. */
    val DISPLAY_LANGUAGES: List<String> = listOf("en", "de", "es", "fr", "it", "pt")

    /** Lower case without accents and anything but a–z: "Español" → "espanol", "Österreich" → "osterreich". */
    fun normalize(text: CharSequence): String {
        val lower = text.toString().lowercase(Locale.ROOT)
            .replace("ß", "ss").replace("æ", "ae").replace("œ", "oe").replace("ø", "o").replace("ł", "l").replace("đ", "d")
        return Normalizer.normalize(lower, Normalizer.Form.NFD).filter { it in 'a'..'z' }
    }

    /** Typos allowed for an alias of [length] letters: none up to 2, one up to 5, two from 6. */
    fun allowedEdits(length: Int): Int = when {
        length <= 2 -> 0
        length <= 5 -> 1
        else -> 2
    }

    /** Damerau-Levenshtein distance (optimal string alignment): insert, delete, replace, swap two neighbours. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            var best = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) best = minOf(best, d[i - 2][j - 2] + 1)
            d[i][j] = best
        }
        return d[a.length][b.length]
    }

    /** True if [typed] is [alias] with at most the typos [allowedEdits] grants the alias. */
    fun isClose(typed: String, alias: String): Boolean {
        val allowed = allowedEdits(alias.length)
        if (allowed == 0 || kotlin.math.abs(typed.length - alias.length) > allowed) return typed == alias
        return distance(typed, alias) <= allowed
    }

    /**
     * The aliases of the language [languageTag] (e.g. "es" or "en-GB"), normalized: its codes, its
     * name in [DISPLAY_LANGUAGES] and in itself, [SHORT_FORMS], and codes and names (English,
     * German, own language) of its [COUNTRIES] and of the tag's own country. A language [Locale]
     * does not know only has its code.
     */
    fun aliases(languageTag: String, displayLanguages: Collection<String> = DISPLAY_LANGUAGES): Set<String> {
        val locale = Locale.forLanguageTag(languageTag)
        val language = locale.language
        if (language.isEmpty()) return emptySet()
        val out = linkedSetOf<String>()
        fun add(text: String) { normalize(text).takeIf { it.length >= 2 }?.let(out::add) }
        add(language)
        val own = Locale.forLanguageTag(language)
        // a language Locale does not know is displayed as its code: nothing more to read
        if (normalize(own.getDisplayLanguage(Locale.ENGLISH)) == language) return out
        runCatching { own.isO3Language }.getOrNull()?.let(::add)
        for (display in displayLanguages + language) add(own.getDisplayLanguage(Locale.forLanguageTag(display)))
        SHORT_FORMS[language]?.forEach(::add)
        val countries = COUNTRIES[language].orEmpty() + listOf(locale.country).filter { it.isNotEmpty() }
        for (country in countries.distinct()) {
            add(country)
            val region = Locale.Builder().setRegion(country).build()
            for (display in listOf("en", "de", language)) add(region.getDisplayCountry(Locale.forLanguageTag(display)))
        }
        return out
    }
}

/**
 * Stage 24g: the languages a typed word names, best first. [languages] are the enabled keyboard
 * languages with their aliases; [reserved] are command names. An alias that equals or nearly
 * equals a reserved name is only matched exactly, so a typo of a command never turns into a language.
 */
class LanguageMatcher(languages: List<Language>, reserved: Collection<String> = emptyList()) {
    /** One keyboard language, e.g. id "es" with the aliases from [LanguageNames.aliases]. */
    class Language(val id: String, aliases: Collection<String>) {
        val aliases: Set<String> = aliases.map { LanguageNames.normalize(it) }.filter { it.length >= 2 }.toSet()
    }

    private class Entry(val id: String, val exact: Set<String>, val loose: Set<String>)

    private val entries: List<Entry> = run {
        val names = reserved.map { LanguageNames.normalize(it) }.filter { it.isNotEmpty() }
        languages.map { language ->
            Entry(language.id, language.aliases, language.aliases.filter { alias ->
                names.none { it == alias || LanguageNames.isClose(it, alias) || LanguageNames.isClose(alias, it) }
            }.toSet())
        }
    }

    val ids: List<String> get() = entries.map { it.id }

    /**
     * The ids of the languages [typed] names, best first, or empty. A generic word ("sprache")
     * names all; an exact alias wins over everything else; otherwise an alias that starts with at
     * least two typed letters or is within [LanguageNames.allowedEdits] typos (from three letters).
     */
    fun match(typed: String): List<String> {
        val word = LanguageNames.normalize(typed)
        if (word.length < 2) return emptyList()
        if (word in LanguageNames.GENERIC) return ids.distinct()
        val exact = entries.filter { word in it.exact }.map { it.id }.distinct()
        if (exact.isNotEmpty()) return exact
        val scored = entries.mapNotNull { entry ->
            val prefix = if (entry.loose.any { it.length > word.length && it.startsWith(word) }) 1 else null
            val typo = if (word.length < 3) null else entry.loose
                .filter { LanguageNames.isClose(word, it) }
                .minOfOrNull { 1 + LanguageNames.distance(word, it) }
            listOfNotNull(prefix, typo).minOrNull()?.let { entry.id to it }
        }
        return scored.sortedBy { it.second }.map { it.first }.distinct()
    }
}
