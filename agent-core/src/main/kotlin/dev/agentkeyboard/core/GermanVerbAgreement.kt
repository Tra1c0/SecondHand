// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Locale

/** Conservative agreement for competing regular verb forms with the same stem. */
object GermanVerbAgreement {
    private enum class Ending(val suffix: String) { FIRST("e"), SECOND("st"), THIRD("t"), INFINITIVE("en") }

    private val subjects = mapOf(
        "ich" to Ending.FIRST, "du" to Ending.SECOND, "er" to Ending.THIRD, "man" to Ending.THIRD,
    )
    private val modifiers = setOf(
        "auch", "bereits", "bitte", "dann", "das", "dich", "doch", "eben", "endlich", "es", "gern",
        "gerade", "gleich", "heute", "jetzt", "lieber", "morgen", "nachher", "nachträglich", "nicht",
        "noch", "nur", "oft", "schon", "selbst", "sofort", "später", "vielleicht", "wieder", "zuerst",
    )
    private val modals = mapOf(
        Ending.FIRST to setOf("darf", "kann", "mag", "möchte", "muss", "muß", "soll", "werde", "will"),
        Ending.SECOND to setOf("darfst", "kannst", "magst", "möchtest", "musst", "mußt", "sollst", "willst", "wirst"),
        Ending.THIRD to setOf("darf", "kann", "mag", "möchte", "muss", "muß", "soll", "will", "wird"),
    )

    /** Returns a form already present in the candidates, or null to leave their ranking alone. */
    fun prefer(
        typed: String,
        first: String,
        alternatives: Sequence<String>,
        previousWords: Sequence<String>,
        after: () -> CharSequence?,
    ): String? {
        val context = previousWords.map { it.lowercase(Locale.GERMAN) }.dropWhile { it in modifiers }.iterator()
        if (!context.hasNext()) return null
        val nearest = context.next()
        val subject = subjects[nearest]
        if (subject == null && !context.hasNext()) return null
        val finiteEnding = subject ?: subjects[context.next()] ?: return null
        val allowedModals = modals.getValue(finiteEnding)
        if (subject == null && nearest !in allowedModals) return null

        // A typed form of the same verb can be intentional, including when the user has not yet typed a modal.
        if (relatedForms(first, typed, finiteEnding)) return typed
        val sibling = alternatives.firstOrNull { relatedForms(first, it, finiteEnding) } ?: return null
        val expected = if (subject == null) Ending.INFINITIVE else {
            val followingText = after() ?: return null
            if (followingWord(followingText) in allowedModals) Ending.INFINITIVE else finiteEnding
        }
        return if (first.endsWith(expected.suffix)) null else sibling
    }

    private fun relatedForms(a: String, b: String, finite: Ending): Boolean {
        if (a == b || !a.all { it.isLowerCase() } || !b.all { it.isLowerCase() }) return false
        fun matches(verb: String, infinitive: String): Boolean =
            verb.endsWith(finite.suffix) && infinitive.endsWith(Ending.INFINITIVE.suffix) &&
                infinitive.length >= 5 && verb.dropLast(finite.suffix.length) == infinitive.dropLast(2)
        return matches(a, b) || matches(b, a)
    }

    private fun followingWord(after: CharSequence): String? {
        val start = after.indexOfFirst { !it.isWhitespace() }
        if (start <= 0 || (0 until start).any { after[it] == '\n' || after[it] == '\r' }) return null
        return after.subSequence(start, after.length).takeWhile { it.isLetter() }.toString().lowercase(Locale.GERMAN)
    }
}
