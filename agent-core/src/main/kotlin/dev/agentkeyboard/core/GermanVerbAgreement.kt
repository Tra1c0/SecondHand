// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Locale

/** Conservative agreement for competing regular verb forms with the same stem. */
object GermanVerbAgreement {
    private enum class Form { FIRST, SECOND, THIRD, INFINITIVE }

    private enum class Subject(val form: Form, vararg val modals: String) {
        FIRST(Form.FIRST, "darf", "kann", "möchte", "muss", "muß", "soll", "werde", "will"),
        SECOND(Form.SECOND, "darfst", "kannst", "möchtest", "musst", "mußt", "sollst", "willst", "wirst"),
        THIRD(Form.THIRD, "darf", "kann", "möchte", "muss", "muß", "soll", "will", "wird"),
        SECOND_PLURAL(Form.THIRD, "dürft", "könnt", "möchtet", "müsst", "müßt", "sollt", "wollt", "werdet"),
        PLURAL(Form.INFINITIVE, "dürfen", "können", "möchten", "müssen", "sollen", "werden", "wollen"),
    }

    private fun subjects(word: String): List<Subject> = when (word) {
        "ich" -> listOf(Subject.FIRST)
        "du" -> listOf(Subject.SECOND)
        "er", "es", "man" -> listOf(Subject.THIRD)
        "wir" -> listOf(Subject.PLURAL)
        "ihr" -> listOf(Subject.SECOND_PLURAL)
        "sie" -> listOf(Subject.THIRD, Subject.PLURAL)
        else -> emptyList()
    }
    private val modifiers = setOf(
        "auch", "bereits", "bitte", "dann", "das", "dich", "doch", "eben", "endlich", "gern",
        "gerade", "gleich", "heute", "jetzt", "lieber", "morgen", "nachher", "nachträglich", "nicht",
        "noch", "nur", "oft", "schon", "selbst", "sofort", "später", "vielleicht", "wieder", "zuerst",
    )
    private val suffixes = listOf("est", "et", "st", "en", "e", "t", "n")
    private val possessiveStems = setOf("mein", "dein", "sein", "ihr", "unser", "eur")

    /** Returns a form already present in the candidates, or null to leave their ranking alone. */
    fun prefer(
        typed: String,
        first: String,
        alternatives: Sequence<String>,
        previousWords: Sequence<String>,
        isInfinitive: (String) -> Boolean,
        typedIsKnown: () -> Boolean = { false },
        after: () -> CharSequence?,
    ): String? {
        val possibleStems = stemsOf(first).filterNot { it in possessiveStems }
        if (possibleStems.isEmpty()) return null
        val stems by lazy {
            possibleStems.filter { stem ->
                isInfinitive(stem + "en") || (stem.endsWith("el") || stem.endsWith("er")) && isInfinitive(stem + "n")
            }
        }
        val words = previousWords.map { it.lowercase(Locale.GERMAN) }.dropWhile { it in modifiers }.toList()
        val beforeEs = if (words.firstOrNull() == "es") words.drop(1).dropWhile { it in modifiers } else emptyList()
        val context = if (beforeEs.any { subjects(it).isNotEmpty() }) beforeEs else words
        val nearest = context.firstOrNull().orEmpty()
        val direct = subjects(nearest)
        val withModal = if (direct.isEmpty() && context.size > 1) {
            subjects(context[1]).filter { nearest in it.modals }
        } else emptyList()
        // A complete typed variant can be intentional, including before the user has typed a modal.
        if (typed != first && possibleStems.any { forms(it, typed).isNotEmpty() } &&
            stems.any { forms(it, typed).isNotEmpty() } &&
            (direct.isNotEmpty() || withModal.isNotEmpty() || typedIsKnown())) return typed
        if (direct.isEmpty() && withModal.isEmpty()) return null

        val siblings = alternatives.filter { word -> word != first && possibleStems.any { forms(it, word).isNotEmpty() } }.toList()
        if (siblings.isEmpty()) return null
        if (stems.isEmpty()) return null
        val followingText = after() ?: return null
        val next = followingWord(followingText)
        val rightModal = direct.any { next in it.modals }
        // An existing word other than a matching modal can change the role of the word being corrected.
        if (next != null && !rightModal) return null
        val expected = if (withModal.isNotEmpty() || rightModal) setOf(Form.INFINITIVE)
            else direct.mapTo(hashSetOf()) { it.form }
        if (stems.any { stem -> forms(stem, first).any { it in expected } }) return null
        return siblings.firstOrNull { word -> stems.any { stem -> forms(stem, word).any { it in expected } } }
    }

    private fun stemsOf(word: String): List<String> {
        if (word.length < 4 || !word.all { it.isLowerCase() }) return emptyList()
        return suffixes.mapNotNull { suffix ->
            if (!word.endsWith(suffix) || word.length - suffix.length < 3) return@mapNotNull null
            word.dropLast(suffix.length).takeIf { forms(it, word).isNotEmpty() }
        }
    }

    private fun forms(stem: String, word: String): Set<Form> {
        if (!word.startsWith(stem)) return emptySet()
        val suffix = word.substring(stem.length)
        val epenthetic = stem.endsWith('d') || stem.endsWith('t')
        val sibilant = stem.last() in "sßxz"
        return when (suffix) {
            "e" -> setOf(Form.FIRST)
            "en" -> setOf(Form.INFINITIVE)
            "n" -> if (stem.endsWith("el") || stem.endsWith("er")) setOf(Form.INFINITIVE) else emptySet()
            "st" -> if (!epenthetic && !sibilant) setOf(Form.SECOND) else emptySet()
            "est" -> if (epenthetic) setOf(Form.SECOND) else emptySet()
            "t" -> when {
                epenthetic -> emptySet()
                sibilant -> setOf(Form.SECOND, Form.THIRD)
                else -> setOf(Form.THIRD)
            }
            "et" -> if (epenthetic) setOf(Form.THIRD) else emptySet()
            else -> emptySet()
        }
    }

    private fun followingWord(after: CharSequence): String? {
        val start = after.indexOfFirst { !it.isWhitespace() }
        if (start <= 0 || (0 until start).any { after[it] == '\n' || after[it] == '\r' }) return null
        return after.subSequence(start, after.length).takeWhile { it.isLetter() }.takeIf { it.isNotEmpty() }
            ?.toString()?.lowercase(Locale.GERMAN)
    }
}
