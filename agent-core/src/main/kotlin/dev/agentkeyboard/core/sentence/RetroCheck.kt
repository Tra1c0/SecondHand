// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

/**
 * Stages 104/105: one check of the text before the cursor after a word ended. Stage 104 ([SentenceRepair]) runs first,
 * stage 105 ([AutoPunctuation]) on the text as 104 left it; both together become ONE edit of the end of the text, so that one
 * Backspace takes everything back. Pure: the text is read, never kept.
 */
object RetroCheck {
    /**
     * The end of the checked text from [start] on: today [old], afterwards [replacement]; both end at the cursor. [pairs] are
     * the changed words (old -> new) for the rejection memory, [rules] the rules that fired (names only, for counting).
     */
    class Edit(val start: Int, val old: String, val replacement: String, val pairs: List<Pair<String, String>>, val rules: List<String>,
               /** Where each changed word starts in the checked text (stage 105 marks: in the text as stage 104 left it). */
               val pairStarts: List<Int> = emptyList())

    class Options(val repair: Boolean, val punctuate: Boolean, val german: Boolean, val lineBreak: Boolean)

    @JvmStatic
    fun check(text: String, lexicon: SentenceLexicon, context: SentenceContext, options: Options): Edit? {
        val pairs = ArrayList<Pair<String, String>>()
        val rules = ArrayList<String>()
        val starts = ArrayList<Int>()
        var current = text
        if (options.repair && options.german) {
            val changes = SentenceRepair(lexicon, context).repair(current)
            current = applied(current, changes)
            changes.forEach { pairs.add(it.old to it.replacement); rules.add(it.rule); starts.add(it.start) }
        }
        if (options.punctuate) {
            val changes = AutoPunctuation(lexicon, context).punctuate(current, options.lineBreak, options.german)
            current = applied(current, changes)
            changes.forEach { pairs.add(it.old to it.replacement); rules.add(it.rule); starts.add(it.start) }
        }
        if (current == text) return null
        var start = 0
        val limit = minOf(text.length, current.length)
        while (start < limit && text[start] == current[start]) start++
        // whole words: the edit starts at the beginning of the first changed word
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '\'')) start--
        return Edit(start, text.substring(start), current.substring(start), pairs, rules, starts)
    }

    private fun applied(text: String, changes: List<Change>): String {
        if (changes.isEmpty()) return text
        val sb = StringBuilder(text)
        for (c in changes.sortedByDescending { it.start }) sb.replace(c.start, c.end, c.replacement)
        return sb.toString()
    }
}
