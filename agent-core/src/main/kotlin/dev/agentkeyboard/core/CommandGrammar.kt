// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/**
 * Stage 52: the unified command language, `@<area> [action] [words …]`. Every area declares its
 * slots (app, group, language, name, text, on/off); the words are given to the slots with the
 * tolerance of the language words ([NameMatch]: exact, started from three letters, a typo by
 * [LanguageNames.isTypo]), in any order. Steps are chained with `;`, `then`/`dann` or `→`; an area
 * holds until the next `@<area>`. A word that fits several slots gives several [Reading]s, best first.
 *
 * Pure: from the typed text to readings with a [Role] for every word (the colours of the preview).
 * Nothing here changes a setting; the keyboard turns a reading into its effects only on a tap.
 */
object CommandGrammar {
    /** The areas of the unified commands, by command name. */
    enum class Area(val id: String) { LANG("lang"), SNIP("snip"), CLIP("clip"), GUARD("guard"), MEM("mem"), GROUP("group") }

    /** An explicit action; none typed means "show" without a value and "set" with one. */
    enum class Action { SET, LIST, DELETE, ON, OFF, ADD }

    /** What a typed word is, for the colours of the preview. */
    enum class Role { AREA, ACTION, TARGET, VALUE, SEPARATOR, UNKNOWN }

    /** Action words, English first, then German; symbols as typed, words after [LanguageNames.normalize]. */
    val ACTION_WORDS: Map<String, Action> = linkedMapOf(
        "set" to Action.SET, "list" to Action.LIST, "?" to Action.LIST, "del" to Action.DELETE,
        "delete" to Action.DELETE, "remove" to Action.DELETE, "-" to Action.DELETE, "on" to Action.ON,
        "off" to Action.OFF, "add" to Action.ADD, "new" to Action.ADD, "+" to Action.ADD,
        "setze" to Action.SET, "setzen" to Action.SET, "zeig" to Action.LIST, "zeige" to Action.LIST,
        "liste" to Action.LIST, "weg" to Action.DELETE, "losch" to Action.DELETE, "losche" to Action.DELETE,
        "loschen" to Action.DELETE, "loesch" to Action.DELETE, "loesche" to Action.DELETE,
        "loeschen" to Action.DELETE, "entferne" to Action.DELETE, "entfernen" to Action.DELETE,
        "an" to Action.ON, "aus" to Action.OFF, "neu" to Action.ADD, "dazu" to Action.ADD,
    )

    /** The actions each area takes. */
    val ACTIONS: Map<Area, Set<Action>> = mapOf(
        Area.LANG to setOf(Action.SET, Action.LIST, Action.DELETE),
        Area.SNIP to setOf(Action.ADD, Action.DELETE, Action.LIST),
        Area.CLIP to setOf(Action.ON, Action.OFF, Action.LIST),
        Area.GUARD to setOf(Action.ON, Action.OFF, Action.LIST, Action.DELETE),
        Area.MEM to setOf(Action.ON, Action.OFF, Action.LIST, Action.DELETE),
        Area.GROUP to setOf(Action.SET, Action.LIST, Action.DELETE, Action.ADD),
    )

    /** Words for every app (the group "all"). */
    val ALL_WORDS: Set<String> = setOf("all", "apps", "alle", "allen", "uberall", "ueberall")

    /** Words between two steps; `;`, `→` and `->` also split a word they stick to. */
    val SEPARATORS: Set<String> = setOf(";", "→", "->", "then", "dann")

    /** Forces the word after it to be an app (`app:signal`). */
    const val APP_PREFIX = "app:"

    /** At most this many readings are offered. */
    const val MAX_READINGS = 3
    /** Words that fit no slot cost this much, so readings with fewer unknown words come first. */
    private const val UNKNOWN_COST = 3
    /** Combinations tried per step before the best are kept. */
    private const val MAX_COMBINATIONS = 64

    /** An installed app: [label] is what the user types. [toString] names nothing private. */
    class AppName(val packageName: String, val label: String) {
        /** Review of stage 52: [label] normalized once per app, not at every typed word. */
        val normalized: String by lazy { LanguageNames.normalize(label) }
        /** The words of [label] (two letters or more), normalized. */
        internal val parts: List<String> by lazy { NameMatch.parts(label) }
        override fun equals(other: Any?) = other is AppName && other.packageName == packageName
        override fun hashCode() = packageName.hashCode()
        override fun toString() = "AppName(${label.length} chars)"
    }

    /** What the words of a command can name; read from the keyboard's own data. */
    interface Vocabulary {
        val apps: List<AppName>
        /** Own groups (stage 52 `@group`): name (lower case) to package names. */
        val groups: Map<String, List<String>> get() = emptyMap()
        /** The user's snippet names. */
        val snippets: List<String> get() = emptyList()
        /** The enabled languages [word] names, best first ([LanguageMatcher]); [exactOnly] for exact aliases. */
        fun languages(word: String, exactOnly: Boolean): List<String> = emptyList()
    }

    /** A target of a step: every app, an own group (resolved to its apps) or one app. */
    sealed interface Target {
        data object All : Target
        data class Group(val name: String, val packages: List<String>) : Target
        data class App(val app: AppName) : Target
    }

    /** A typed word (from [start] to [end] in the text) and its role. */
    data class Word(val start: Int, val end: Int, val role: Role)

    /**
     * One step of a reading. [action] is the typed action or null; [targets] empty means the field
     * typed in (this app). [legacy]: shaped like the area's old syntax (`@lang spanisch`, `@snip adr`,
     * `@clip Termin`), which the area's old chips handle when it is the only step.
     */
    class Step(
        val area: Area,
        val action: Action?,
        val targets: List<Target> = emptyList(),
        val language: String? = null,
        /** A group or snippet name; [nameExists] when it is one that exists. */
        val name: String? = null,
        val nameExists: Boolean = false,
        /** Apps of a group (stage 52 `@group name apps …`). */
        val members: List<AppName> = emptyList(),
        /** The text of a new snippet, or the search words of an old `@snip`/`@clip`. */
        val text: String? = null,
        /** Words that fit no slot, as typed. */
        val unknown: List<String> = emptyList(),
        val words: List<Word> = emptyList(),
        val legacy: Boolean = false,
        internal val score: Int = 0,
        /** Second review of stage 52: an action word or a language name typed exactly took its slot. */
        internal val exact: Boolean = false,
        /** Second review of stage 52: the step of a claimed language word (`@deutsch`), which is its value. */
        internal val claimed: Boolean = false,
    ) {
        /** True for a step that names every app. */
        val all: Boolean get() = targets.any { it == Target.All }

        /** Second review of stage 52: an area word with nothing typed after it (`…; @guard `); not complete yet. */
        var bare: Boolean = false
            internal set

        /** A group name that is no group yet, read as this step's target. */
        private val freshName: Boolean get() = area == Area.GROUP && name != null && !nameExists && words.any { it.role == Role.TARGET }

        /**
         * Review of stage 52: whether the words after the area word read as a command rather than as
         * text that happens to follow one ("Hallo @gruppe wer kommt heute?"): at least one word has a
         * known role (an action, a target or a value; a new group name alone is none), and the first
         * word is known or at most one word is unknown (`@lang foo german`: an app that is not
         * installed or not loaded yet). Second review: an action word or a language name typed
         * exactly is enough whatever else is unknown (`@guard google maps off` before the apps are
         * read), and a claimed language word (`@deutsch`) is a known first word like `@lang deutsch`.
         */
        val looksLikeCommand: Boolean get() {
            if (claimed && language != null) return true
            val known = words.count { it.role in KNOWN_ROLES } - (if (freshName) 1 else 0)
            if (known < 1) return false
            if (exact) return true
            val first = words.first()
            val firstKnown = first.role in KNOWN_ROLES && !(freshName && first.role == Role.TARGET)
            return firstKnown || unknown.size <= 1
        }
        override fun toString() = "Step($area, $action, ${targets.size} targets, language=$language, unknown=${unknown.size}, legacy=$legacy)"
    }

    /** One meaning of the whole typed chain; [words] colour every typed word, area words and separators included. */
    class Reading(val steps: List<Step>, val words: List<Word>, val score: Int) {
        val unknownCount: Int get() = steps.sumOf { it.unknown.size }
        /** A single step in the old syntax of its area. */
        val legacy: Boolean get() = steps.size == 1 && steps[0].legacy
        /** Review of stage 52: some step reads as a command ([Step.looksLikeCommand]). */
        val looksLikeCommand: Boolean get() = steps.any { it.looksLikeCommand }
        override fun toString() = "Reading($steps, score=$score)"
    }

    /** A typed word with its place in the text. */
    class Token(val text: String, val start: Int, val end: Int) {
        override fun toString() = "Token($start-$end)"
    }

    /** Words separated by white space; `;`, `→` and `->` are words of their own even without spaces. */
    fun tokenize(text: CharSequence): List<Token> {
        val out = ArrayList<Token>()
        var index = 0
        while (index < text.length) {
            if (text[index].isWhitespace()) { index++; continue }
            val start = index
            while (index < text.length && !text[index].isWhitespace()) {
                val split = splitLength(text, index)
                if (split > 0) {
                    if (index > start) break
                    index += split
                    break
                }
                index++
            }
            out += Token(text.substring(start, index), start, index)
        }
        return out
    }

    private fun splitLength(text: CharSequence, index: Int): Int = when {
        text[index] == ';' || text[index] == '→' -> 1
        text[index] == '-' && index + 1 < text.length && text[index + 1] == '>' -> 2
        else -> 0
    }

    private fun isSeparator(token: Token) = token.text.lowercase() in SEPARATORS

    /** The roles of words that name something; unknown words, areas and separators do not. */
    private val KNOWN_ROLES = setOf(Role.ACTION, Role.TARGET, Role.VALUE)

    /** Areas with free text: `;`, `then` and `dann` belong to it (the [Area.SNIP] text, the [Area.CLIP] search). */
    private fun freeText(area: Area) = area == Area.SNIP || area == Area.CLIP

    /** Review of stage 52: true when the last word of [text] is a separator (`… german;`, `… dann`), an explicit chain. */
    fun endsWithSeparator(text: CharSequence): Boolean = tokenize(text).lastOrNull()?.let { isSeparator(it) } == true

    /** [word] as an action word (exact; from four letters also a typo), or null. */
    fun action(word: String): Action? {
        val raw = word.lowercase()
        ACTION_WORDS[raw]?.let { return it }
        // a word with other characters ("off,") is no action word
        if (raw.isEmpty() || !raw.all { it.isLetter() }) return null
        val normalized = LanguageNames.normalize(raw)
        ACTION_WORDS[normalized]?.let { return it }
        return ACTION_WORDS.entries.firstOrNull { (key, _) -> key.length >= 4 && LanguageNames.isTypo(normalized, key) }?.value
    }

    private fun isExactAction(word: String): Boolean {
        val raw = word.lowercase()
        return raw in ACTION_WORDS || (raw.all { it.isLetter() } && LanguageNames.normalize(raw) in ACTION_WORDS)
    }

    /** An action of `@clip`, typed exactly (`on`, `aus`, `list`). */
    private fun isClipAction(token: Token): Boolean =
        isExactAction(token.text) && action(token.text)?.let { it in ACTIONS.getValue(Area.CLIP) } == true

    /**
     * The readings of [text], which starts with an area word (`@lang …`); best first, at most
     * [maxReadings], all with the fewest unknown words and the lowest cost. Empty when [text] does not
     * start with an area. [areas] maps normalized area names and second names to their area; [claim]
     * names the area a word like `@deutsch` stands for (then the word is its value).
     */
    fun parse(text: String, prefix: Char, areas: Map<String, Area>, vocabulary: Vocabulary,
              claim: (String) -> Area? = { null }, maxReadings: Int = MAX_READINGS): List<Reading> {
        val segments = segments(tokenize(text), prefix, areas, claim) ?: return emptyList()
        // each step: its alternatives, best first
        val stepOptions = ArrayList<List<Step>>()
        val fixedWords = ArrayList<Word>()
        for (segment in segments) {
            // a claimed word (`@deutsch`) is the language itself
            fixedWords += Word(segment.head.start, segment.head.end, if (segment.claimed) Role.VALUE else Role.AREA)
            val (steps, separators) = if (freeText(segment.area)) listOf(segment.words.toList()) to emptyList() else split(segment.words)
            separators.forEach { fixedWords += Word(it.start, it.end, Role.SEPARATOR) }
            segment.trailing.forEach { fixedWords += Word(it.start, it.end, Role.SEPARATOR) }
            for ((index, words) in steps.withIndex()) {
                // nothing between two separators, after the last one or before the next area is no step;
                // second review of stage 52: an area word with nothing after it is one (an empty step,
                // which the keyboard does not run), else "@lang whatsapp german; @guard " ran the first part
                // and the tap deleted "@guard " too
                val bareArea = index == 0 && (segment.claimed || segment.words.isEmpty())
                if (words.isEmpty() && !bareArea && (steps.size > 1 || segments.size > 1)) continue
                val end = words.lastOrNull()?.end ?: segment.head.end
                stepOptions += parseStep(segment.area, words, if (index == 0 && segment.claimed) segment.head else null,
                    text, vocabulary, end).onEach { it.bare = words.isEmpty() && !segment.claimed }
            }
        }
        if (stepOptions.isEmpty()) stepOptions += listOf(listOf(Step(segments.first().area, null, legacy = true)))
        var readings = listOf(emptyList<Step>() to 0)
        for (options in stepOptions) {
            readings = readings.flatMap { (steps, score) -> options.map { steps + it to score + it.score } }
                .sortedBy { it.second }.take(MAX_COMBINATIONS)
        }
        val built = readings.map { (steps, score) ->
            Reading(steps, (fixedWords + steps.flatMap { it.words }).sortedBy { it.start }, score)
        }.sortedWith(compareBy({ it.unknownCount }, { it.score }))
        val best = built.firstOrNull() ?: return emptyList()
        return built.filter { it.unknownCount == best.unknownCount && it.score == best.score }.take(maxReadings)
    }

    private class Segment(val area: Area, val head: Token, val claimed: Boolean) {
        val words = ArrayList<Token>()
        /** Separators right before the next area word: they end the step and start nothing. */
        val trailing = ArrayList<Token>()
    }

    private fun segments(tokens: List<Token>, prefix: Char, areas: Map<String, Area>, claim: (String) -> Area?): List<Segment>? {
        val out = ArrayList<Segment>()
        for (token in tokens) {
            val head = head(token, prefix, areas, claim)
            if (head == null) {
                (out.lastOrNull() ?: return null).words += token
                continue
            }
            out.lastOrNull()?.let { last ->
                // in a snippet's text or a clip search only `;`, `→` and `->` end it ("bis dann" stays text);
                // second review of stage 52: `@clip on dann @lang …` is a chain again, only one exact
                // action word before the separators ("@clip pizza dann pasta" stays a search)
                val clipSwitch = last.area == Area.CLIP && last.words.size >= 2 && isClipAction(last.words.first()) &&
                    last.words.drop(1).all { isSeparator(it) }
                while (last.words.isNotEmpty() && isSeparator(last.words.last()) &&
                    (!freeText(last.area) || clipSwitch || last.words.last().text.none { it.isLetter() }))
                    last.trailing.add(0, last.words.removeAt(last.words.lastIndex))
            }
            out += Segment(head.first, token, head.second)
        }
        return out.ifEmpty { null }
    }

    /** The area [token] starts, and whether it is a claimed word (`@deutsch`) rather than an area name. */
    private fun head(token: Token, prefix: Char, areas: Map<String, Area>, claim: (String) -> Area?): Pair<Area, Boolean>? {
        if (token.text.length < 2 || token.text[0] != prefix) return null
        val word = token.text.substring(1)
        if (!word.all { it.isLetter() }) return null
        val lower = word.lowercase()
        areas[lower]?.let { return it to false }
        areas[LanguageNames.normalize(lower)]?.let { return it to false }
        areas[LanguageNames.normalize(lower.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue"))]?.let { return it to false }
        return claim(lower)?.let { it to true }
    }

    /** The words of one area split into steps at the separators, and the separators (for the colours). */
    private fun split(words: List<Token>): Pair<List<List<Token>>, List<Token>> {
        val steps = ArrayList<List<Token>>()
        val separators = ArrayList<Token>()
        var current = ArrayList<Token>()
        for (word in words) {
            if (isSeparator(word)) { steps += current; separators += word; current = ArrayList(); continue }
            current += word
        }
        steps += current
        return steps to separators
    }

    // ---- one step ----

    /** One or more typed words read as one (an app called "Google Maps"). */
    private class Unit(val tokens: List<Token>, val forcedApp: Boolean) {
        val start get() = tokens.first().start
        val end get() = tokens.last().end
        val text: String get() = tokens.joinToString(" ") { it.text }.let { if (forcedApp) it.substring(APP_PREFIX.length) else it }
    }

    private sealed interface Meaning {
        val score: Int
        data class Act(val action: Action, override val score: Int) : Meaning
        data class To(val target: Target, override val score: Int) : Meaning
        data class Language(val id: String, override val score: Int) : Meaning
        data object Unknown : Meaning { override val score = UNKNOWN_COST }
    }

    private fun parseStep(area: Area, tokens: List<Token>, claimed: Token?, text: String, vocabulary: Vocabulary,
                          segmentEnd: Int): List<Step> = when (area) {
        Area.LANG, Area.GUARD, Area.MEM -> targetStep(area, tokens, claimed, vocabulary)
        Area.GROUP -> groupStep(tokens, vocabulary)
        Area.SNIP -> listOf(snipStep(tokens, text, vocabulary, segmentEnd))
        Area.CLIP -> listOf(clipStep(tokens))
    }

    /** Joins neighbouring words that together are the name of an app; `app:` forces an app. */
    private fun units(tokens: List<Token>, apps: List<AppName>): List<Unit> {
        val labels = apps.mapTo(HashSet()) { it.normalized }.apply { remove("") }
        val out = ArrayList<Unit>()
        var index = 0
        while (index < tokens.size) {
            val forced = tokens[index].text.lowercase().startsWith(APP_PREFIX) && tokens[index].text.length > APP_PREFIX.length
            var taken = 1
            for (length in 3 downTo 2) {
                if (index + length > tokens.size) continue
                val window = tokens.subList(index, index + length)
                // only words join: no action word, no symbol ("+ telegram" is an action and an app)
                if (window.any { isExactAction(it.text) || it.text.none { c -> c.isLetter() } }) continue
                val joined = LanguageNames.normalize(window.joinToString("") { it.text }.let { if (forced) it.substring(APP_PREFIX.length) else it })
                if (joined in labels) { taken = length; break }
            }
            out += Unit(tokens.subList(index, index + taken), forced)
            index += taken
        }
        return out
    }

    private fun appMeanings(word: String, apps: List<AppName>): List<Meaning> =
        LanguageNames.normalize(word).let { typed -> apps.mapNotNull { app -> NameMatch.score(typed, app)?.let { Meaning.To(Target.App(app), it) } } }

    private fun groupMeanings(word: String, groups: Map<String, List<String>>): List<Meaning> =
        groups.mapNotNull { (name, packages) -> NameMatch.score(word, name)?.let { Meaning.To(Target.Group(name, packages), it) } }

    /** The meanings of [unit] with the lowest cost (several on a tie), or [Meaning.Unknown]. */
    private fun meanings(area: Area, unit: Unit, vocabulary: Vocabulary, allowAll: Boolean = true,
                         languages: Boolean = area == Area.LANG): List<Meaning> {
        val word = unit.text
        if (unit.forcedApp) return best(appMeanings(word, vocabulary.apps))
        val options = ArrayList<Meaning>()
        if (unit.tokens.size == 1) {
            val action = action(word)
            if (action != null && action in ACTIONS.getValue(area)) {
                // an action word typed exactly is the action, whatever else it could be
                if (isExactAction(word)) return listOf(Meaning.Act(action, 0))
                options += Meaning.Act(action, 2)
            }
            if (allowAll && LanguageNames.normalize(word) in ALL_WORDS) return listOf(Meaning.To(Target.All, 0))
            if (languages) {
                val exact = vocabulary.languages(word, exactOnly = true)
                if (exact.isNotEmpty()) options += exact.map { Meaning.Language(it, 0) }
                else options += vocabulary.languages(word, exactOnly = false).take(2).map { Meaning.Language(it, 1) }
            }
            options += groupMeanings(word, vocabulary.groups)
        }
        options += appMeanings(word, vocabulary.apps)
        return best(options)
    }

    private fun best(options: List<Meaning>): List<Meaning> {
        val lowest = options.minOfOrNull { it.score } ?: return listOf(Meaning.Unknown)
        return options.filter { it.score == lowest }.distinct().take(MAX_READINGS)
    }

    private fun <T> combinations(choices: List<List<T>>): List<List<T>> {
        var out = listOf(emptyList<T>())
        for (choice in choices) {
            out = out.flatMap { prefix -> choice.map { prefix + it } }
            if (out.size > MAX_COMBINATIONS) out = out.take(MAX_COMBINATIONS)
        }
        return out
    }

    /** `@lang`, `@guard`, `@mem`: apps, groups or all, a language (`@lang` only) and an action, any order. */
    private fun targetStep(area: Area, tokens: List<Token>, claimed: Token?, vocabulary: Vocabulary): List<Step> {
        val units = units(tokens, vocabulary.apps)
        val choices = units.map { meanings(area, it, vocabulary) }
        // the claimed head (`@deutsch`) is a language and nothing else
        val claimedLanguage = claimed?.let { vocabulary.languages(it.text.substring(1), exactOnly = false).firstOrNull() }
        return combinations(choices).map { combination ->
            var action: Action? = null
            val targets = ArrayList<Target>()
            var language: String? = claimedLanguage
            val unknown = ArrayList<String>()
            val words = ArrayList<Word>()
            var score = 0
            var exactAction = false
            var exactLanguage = false
            for ((unit, meaning) in units.zip(combination)) {
                val role = when (meaning) {
                    is Meaning.Act -> if (action == null) { action = meaning.action; exactAction = meaning.score == 0; Role.ACTION } else null
                    is Meaning.To -> { targets += meaning.target; Role.TARGET }
                    is Meaning.Language -> if (language == null) { language = meaning.id; exactLanguage = meaning.score == 0; Role.VALUE } else null
                    Meaning.Unknown -> null
                }
                score += if (role == null) UNKNOWN_COST else meaning.score
                if (role == null) unknown += unit.text
                words += Word(unit.start, unit.end, role ?: Role.UNKNOWN)
            }
            // a language means nothing to delete or to list
            if (language != null && claimed == null && (action == Action.DELETE || action == Action.LIST)) {
                val index = words.indexOfFirst { it.role == Role.VALUE }
                if (index >= 0) {
                    val word = words[index]
                    words[index] = word.copy(role = Role.UNKNOWN)
                    unknown += units.first { it.start == word.start }.text
                    score += UNKNOWN_COST
                }
                language = null
                exactLanguage = false
            }
            // review of stage 52: "@lang foo german" (an app not known, or not loaded yet) is no old command
            val legacy = area == Area.LANG && action == null && targets.isEmpty() && (tokens.size <= 1 || unknown.isEmpty())
            Step(area, action, targets.distinct(), language, unknown = unknown, words = words, legacy = legacy, score = score,
                exact = exactAction || exactLanguage, claimed = claimed != null)
        }.distinctBy { listOf(it.action, it.targets, it.language, it.unknown) }
    }

    /**
     * `@group name [apps …]`: the first word that is no action is the group's name (an own group,
     * also with a typo, or a new name), the other words are its apps (own groups give their apps).
     */
    private fun groupStep(tokens: List<Token>, vocabulary: Vocabulary): List<Step> {
        val units = units(tokens, vocabulary.apps)
        fun isAction(unit: Unit) = unit.tokens.size == 1 && action(unit.text)?.let { it in ACTIONS.getValue(Area.GROUP) } == true
        val nameIndex = units.indexOfFirst { !isAction(it) }
        // the group's name: an own group (exact, started or with a typo) or a new name; a new name
        // costs as much as a typo, so a typo of an own group offers both
        val nameChoices: List<Pair<String, Boolean>?> = if (nameIndex < 0) listOf(null) else {
            val typed = units[nameIndex].text
            val existing = vocabulary.groups.keys.mapNotNull { name -> NameMatch.score(typed, name)?.let { name to it } }
            val lowest = existing.minOfOrNull { it.second }
            val choices = ArrayList<Pair<String, Boolean>?>()
            existing.filter { it.second == lowest }.forEach { choices += it.first to true }
            val fresh = LanguageNames.normalize(typed).takeIf { isValidGroupName(it) && it == typed.lowercase() }
            if (fresh != null && (lowest == null || lowest >= 2) && choices.none { it?.first == fresh }) choices += fresh to false
            choices.ifEmpty { listOf(null) }
        }
        val memberUnits = units.filterIndexed { index, _ -> index != nameIndex }
        val memberChoices = memberUnits.map { meanings(Area.GROUP, it, vocabulary, allowAll = false, languages = false) }
        val known = vocabulary.apps.associateBy { it.packageName }
        val steps = ArrayList<Step>()
        for (name in nameChoices) for (combination in combinations(memberChoices)) {
            var action: Action? = null
            val members = ArrayList<AppName>()
            val unknown = ArrayList<String>()
            val words = ArrayList<Word>()
            var score = 0
            var exactAction = false
            if (nameIndex >= 0) {
                val unit = units[nameIndex]
                if (name == null) { unknown += unit.text; score += UNKNOWN_COST }
                else score += if (name.second) (NameMatch.score(unit.text, name.first) ?: 0) else 2
                words += Word(unit.start, unit.end, if (name == null) Role.UNKNOWN else Role.TARGET)
            }
            for ((unit, meaning) in memberUnits.zip(combination)) {
                val role = when (meaning) {
                    is Meaning.Act -> if (action == null) { action = meaning.action; exactAction = meaning.score == 0; Role.ACTION } else null
                    is Meaning.To -> when (val target = meaning.target) {
                        is Target.App -> { members += target.app; Role.VALUE }
                        is Target.Group -> { members += target.packages.map { known[it] ?: AppName(it, it) }; Role.VALUE }
                        Target.All -> null
                    }
                    else -> null
                }
                score += if (role == null) UNKNOWN_COST else meaning.score
                if (role == null) unknown += unit.text
                words += Word(unit.start, unit.end, role ?: Role.UNKNOWN)
            }
            // a new name has nothing to show or to delete
            if (name != null && !name.second && (action == Action.DELETE || (action == null && members.isEmpty()))) {
                val index = words.indexOfFirst { it.role == Role.TARGET }
                if (index >= 0) words[index] = words[index].copy(role = Role.UNKNOWN)
                unknown += units[nameIndex].text
                score += UNKNOWN_COST
            }
            steps += Step(Area.GROUP, action, name = name?.first, nameExists = name?.second == true,
                members = members.distinct(), unknown = unknown, words = words.sortedBy { it.start }, score = score, exact = exactAction)
        }
        return steps.distinctBy { listOf(it.action, it.name, it.members, it.unknown) }
    }

    /** A group name: 2 to 16 letters a–z, no word of the grammar itself. */
    fun isValidGroupName(name: String): Boolean =
        name.length in 2..16 && name.all { it in 'a'..'z' } && name !in ALL_WORDS && name !in ACTION_WORDS &&
            name !in SEPARATORS && Area.entries.none { it.id == name }

    /**
     * `@snip add name text`, `@snip del name`, `@snip list`. Without an action word as the first
     * word, and when that word is the name of a snippet, the step is the old `@snip name` ([Step.legacy]).
     */
    private fun snipStep(tokens: List<Token>, text: String, vocabulary: Vocabulary, segmentEnd: Int): Step {
        val first = tokens.firstOrNull() ?: return Step(Area.SNIP, null, legacy = true)
        val action = action(first.text)?.takeIf { it in ACTIONS.getValue(Area.SNIP) }
        val exists = vocabulary.snippets.any { it.equals(first.text, ignoreCase = true) }
        if (action == null || exists) return Step(Area.SNIP, null, text = text.substring(first.start, segmentEnd), legacy = true)
        val cost = if (isExactAction(first.text)) 0 else 2
        val words = arrayListOf(Word(first.start, first.end, Role.ACTION))
        val nameToken = tokens.getOrNull(1)
        return when (action) {
            Action.LIST -> {
                tokens.drop(1).forEach { words += Word(it.start, it.end, Role.VALUE) }
                Step(Area.SNIP, action, text = nameToken?.let { text.substring(it.start, segmentEnd) }.orEmpty(), words = words, score = cost)
            }
            Action.DELETE -> {
                val match = nameToken?.let { typed ->
                    vocabulary.snippets.mapNotNull { name -> NameMatch.score(typed.text, name)?.let { name to it } }.minByOrNull { it.second }
                }
                val unknown = ArrayList<String>()
                if (nameToken != null) words += Word(nameToken.start, nameToken.end, if (match == null) Role.UNKNOWN else Role.TARGET)
                if (nameToken != null && match == null) unknown += nameToken.text
                tokens.drop(2).forEach { words += Word(it.start, it.end, Role.UNKNOWN); unknown += it.text }
                Step(Area.SNIP, action, name = match?.first, nameExists = match != null, unknown = unknown, words = words,
                    score = cost + (match?.second ?: 0) + UNKNOWN_COST * unknown.size)
            }
            else -> {
                val name = nameToken?.text
                val valid = name != null && name.length in 1..MAX_SNIPPET_NAME && name.all { it.isLetter() }
                val unknown = if (name != null && !valid) listOf(name) else emptyList()
                if (nameToken != null) words += Word(nameToken.start, nameToken.end, if (valid) Role.TARGET else Role.UNKNOWN)
                val body = tokens.getOrNull(2)?.let { text.substring(it.start, segmentEnd).trimEnd() }
                if (body != null) words += Word(tokens[2].start, segmentEnd, Role.VALUE)
                val existing = if (valid) vocabulary.snippets.firstOrNull { it.equals(name, ignoreCase = true) } else null
                Step(Area.SNIP, action, name = existing ?: name.takeIf { valid }, nameExists = existing != null, text = body,
                    unknown = unknown, words = words, score = cost + UNKNOWN_COST * unknown.size)
            }
        }
    }

    /** The longest snippet name ([dev.agentkeyboard.core] mirror of the snippet store's limit). */
    const val MAX_SNIPPET_NAME = 24

    /** `@clip on`, `@clip aus`, `@clip list`: one action word; anything else is the old search (separators included). */
    private fun clipStep(tokens: List<Token>): Step {
        val single = tokens.singleOrNull()
        val action = single?.let { action(it.text) }?.takeIf { it in ACTIONS.getValue(Area.CLIP) && isExactAction(single.text) }
        if (single == null || action == null)
            return Step(Area.CLIP, null, text = tokens.joinToString(" ") { it.text }, legacy = true)
        return Step(Area.CLIP, action, words = listOf(Word(single.start, single.end, Role.ACTION)))
    }
}

/**
 * Stage 52: how closely a typed word names something (an app, a group, a snippet), with the
 * tolerance of the language words ([LanguageNames]): 0 = the same, 1 = started from three letters
 * or a whole word of a longer name ("maps" for "Google Maps"), 2 = a typo ([LanguageNames.isTypo]).
 */
object NameMatch {
    private val WORD_BREAK = Regex("[^\\p{L}\\p{N}]+")

    fun score(typed: String, name: String): Int? = score(LanguageNames.normalize(typed), LanguageNames.normalize(name), parts(name))

    /** [score] for an app, with its name normalized once ([CommandGrammar.AppName.normalized]); [word] is normalized already. */
    internal fun score(word: String, app: CommandGrammar.AppName): Int? = score(word, app.normalized, app.parts)

    /** The words of [name] with two letters or more, normalized. */
    internal fun parts(name: String): List<String> = name.split(WORD_BREAK).map { LanguageNames.normalize(it) }.filter { it.length >= 2 }

    private fun score(word: String, full: String, parts: List<String>): Int? {
        if (word.isEmpty() || full.isEmpty()) return null
        if (word == full) return 0
        if (word.length >= 3 && full.startsWith(word)) return 1
        if (parts.size > 1 && parts.any { it == word || (word.length >= 3 && it.startsWith(word)) }) return 1
        // a typo only of the whole name: a typo of one word would match every app that shares it
        if (LanguageNames.isTypo(word, full)) return 2
        return null
    }

    /** Names that start with [typed] (from one letter), for the suggestions of a slot; best first. */
    fun started(typed: String, names: Collection<String>): List<String> {
        val word = LanguageNames.normalize(typed)
        if (word.isEmpty()) return emptyList()
        return names.filter { LanguageNames.normalize(it).let { full -> full.startsWith(word) && full != word } }
            .sortedBy { LanguageNames.normalize(it).length }
    }
}
