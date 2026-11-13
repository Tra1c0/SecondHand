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

    /** Typos allowed between words whose shorter one has [length] letters: none up to 2, one up to 5, two from 6. */
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

    /**
     * True if [typed] is [alias] with at most the typos [allowedEdits] grants the shorter of both.
     * Up to four letters only a swap of two neighbours counts (`mtah` → `math`, `gre` → `ger`):
     * one other letter in a short word mostly makes another word or name (`matt`, `lana`, `lange`).
     */
    fun isClose(typed: String, alias: String): Boolean {
        if (typed == alias) return true
        val shorter = minOf(typed.length, alias.length)
        val allowed = allowedEdits(shorter)
        if (allowed == 0 || kotlin.math.abs(typed.length - alias.length) > allowed) return false
        if (shorter <= 4) return isSwap(typed, alias)
        return distance(typed, alias) <= allowed
    }

    /**
     * The looser closeness of the first 24g build ([allowedEdits] of the longer word, any edit);
     * keeps aliases near a command name out of prefix and typo matching ([LanguageMatcher]).
     */
    fun isNear(a: String, b: String): Boolean {
        if (a == b) return true
        val allowed = allowedEdits(maxOf(a.length, b.length))
        return allowed > 0 && kotlin.math.abs(a.length - b.length) <= allowed && distance(a, b) <= allowed
    }

    private fun isSwap(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        val diff = a.indices.filter { a[it] != b[it] }
        return diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]]
    }

    /**
     * A typo the keyboard accepts (stage 24g after review): both words have at least four letters,
     * start with the same letter (real typos mostly keep it: `spnaish`, `deustch`, `englsih`) and
     * are [isClose]. The rule for language names, short forms and command names alike.
     */
    fun isTypo(typed: String, alias: String): Boolean =
        typed.length >= 4 && alias.length >= 4 && typed != alias && typed[0] == alias[0] && isClose(typed, alias)

    /** The aliases of [languageTag] without their kinds; see [classifiedAliases]. */
    fun aliases(languageTag: String, displayLanguages: Collection<String> = DISPLAY_LANGUAGES): Set<String> =
        classifiedAliases(languageTag, displayLanguages).keys

    /**
     * The aliases of the language [languageTag] (e.g. "es" or "en-GB"), normalized, with how freely
     * each matches ([AliasKind]): its ISO 639 codes, its name in [DISPLAY_LANGUAGES] and in itself,
     * [SHORT_FORMS], and the names (English, German, own language) of its [COUNTRIES] and of the
     * tag's own country. Country codes (`us`, `ca`, `ch`) are no aliases: they start too many
     * mentions. A language [Locale] does not know only has its code. [normalize] keeps only a–z,
     * so a name in another script (Русский) adds nothing: "in itself" only helps Latin scripts.
     */
    fun classifiedAliases(languageTag: String, displayLanguages: Collection<String> = DISPLAY_LANGUAGES): Map<String, AliasKind> {
        val locale = Locale.forLanguageTag(languageTag)
        val language = locale.language
        if (language.isEmpty()) return emptyMap()
        val out = linkedMapOf<String, AliasKind>()
        fun add(text: String, kind: AliasKind) {
            val alias = normalize(text).takeIf { it.length >= 2 } ?: return
            out[alias] = maxOf(kind, out[alias] ?: kind)
            // the German spelling without umlauts ("oesterreich") is no typo but a second form
            val lower = text.lowercase(Locale.ROOT)
            if (lower.any { it in "äöü" }) add(lower.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue"), kind)
        }
        add(language, AliasKind.CODE)
        val own = Locale.forLanguageTag(language)
        // a language Locale does not know is displayed as its code: nothing more to read
        if (normalize(own.getDisplayLanguage(Locale.ENGLISH)) == language) return out
        runCatching { own.isO3Language }.getOrNull()?.let { add(it, AliasKind.CODE) }
        for (display in displayLanguages + language) add(own.getDisplayLanguage(Locale.forLanguageTag(display)), AliasKind.NAME)
        SHORT_FORMS[language]?.forEach { add(it, AliasKind.SHORT) }
        val countries = COUNTRIES[language].orEmpty() + listOf(locale.country).filter { it.isNotEmpty() }
        for (country in countries.distinct()) {
            val region = Locale.Builder().setRegion(country).build()
            for (display in listOf("en", "de", language))
                add(region.getDisplayCountry(Locale.forLanguageTag(display)), AliasKind.COUNTRY)
        }
        return out
    }
}

/**
 * How freely a typed word may match an alias (stage 24g after review), from strict to loose; an
 * alias of two kinds keeps the looser one. Every kind matches exactly.
 */
enum class AliasKind {
    /** ISO 639 code (`es`, `deu`): exact only. */
    CODE,
    /** Country name (`spain`, `osterreich`): exact or started from three letters, no typos. */
    COUNTRY,
    /** Short form from [LanguageNames.SHORT_FORMS] (`sp`, `ger`, `usa`): also typos ([LanguageNames.isTypo]). */
    SHORT,
    /** Language name (`spanisch`, `ingles`): exact, started from three letters, typos ([LanguageNames.isTypo]). */
    NAME,
}

/**
 * Stage 24g: the languages a typed word names, best first. [languages] are the enabled keyboard
 * languages with their aliases; [reserved] are command names. An alias that equals or nearly
 * equals a reserved name ([LanguageNames.isNear]) is only matched exactly, so a typo of a command
 * never turns into a language.
 */
class LanguageMatcher(languages: List<Language>, reserved: Collection<String> = emptyList()) {
    /** One keyboard language, e.g. id "es" with the aliases from [LanguageNames.classifiedAliases]. */
    class Language(val id: String, kinds: Map<String, AliasKind>) {
        /** Plain aliases count as language names ([AliasKind.NAME]). */
        constructor(id: String, aliases: Collection<String>) : this(id, aliases.associateWith { AliasKind.NAME })

        val kinds: Map<String, AliasKind> = buildMap {
            for ((alias, kind) in kinds) {
                val normalized = LanguageNames.normalize(alias).takeIf { it.length >= 2 } ?: continue
                put(normalized, maxOf(kind, get(normalized) ?: kind))
            }
        }
        val aliases: Set<String> get() = kinds.keys
    }

    private class Entry(val id: String, val exact: Set<String>, val loose: Map<String, AliasKind>)

    private val entries: List<Entry> = run {
        val names = reserved.map { LanguageNames.normalize(it) }.filter { it.isNotEmpty() }
        languages.map { language ->
            Entry(language.id, language.aliases, language.kinds.filter { (alias, kind) ->
                kind != AliasKind.CODE && names.none { LanguageNames.isNear(it, alias) }
            })
        }
    }

    val ids: List<String> get() = entries.map { it.id }

    /**
     * The ids of the languages [typed] names, best first, or empty. A generic word ("sprache")
     * names all; an exact alias wins over everything else. Two letters only match exactly (`es`,
     * `sp`, `uk`), so `@ca`, `@me` or `@al` at the start of a mention name nothing. From three
     * letters an alias that is no code may start with the word, from four letters a language name
     * or short form may differ by a typo ([LanguageNames.isTypo]). With [exactOnly] only generic
     * words and exact aliases count (a finished word, stage 24g after review).
     */
    fun match(typed: String, exactOnly: Boolean = false): List<String> {
        val word = LanguageNames.normalize(typed)
        if (word.length < 2) return emptyList()
        if (word in LanguageNames.GENERIC) return ids.distinct()
        val exact = entries.filter { word in it.exact }.map { it.id }.distinct()
        if (exact.isNotEmpty() || exactOnly || word.length < 3) return exact
        val scored = entries.mapNotNull { entry ->
            val prefix = if (entry.loose.keys.any { it.length > word.length && it.startsWith(word) }) 1 else null
            val typo = entry.loose.filter { (alias, kind) -> kind >= AliasKind.SHORT && LanguageNames.isTypo(word, alias) }
                .keys.minOfOrNull { 1 + LanguageNames.distance(word, it) }
            listOfNotNull(prefix, typo).minOrNull()?.let { entry.id to it }
        }
        return scored.sortedBy { it.second }.map { it.first }.distinct()
    }
}
