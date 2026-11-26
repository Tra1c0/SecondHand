// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

/**
 * Stages 104/105: the words of the last sentence before the cursor, with their places in the text. Pure; keeps nothing.
 */
class Tok(val text: String, val start: Int, val end: Int, val kind: Kind) {
    enum class Kind { WORD, NUMBER, PUNCT, OTHER }

    val isWord get() = kind == Kind.WORD
    val lower: String by lazy { text.lowercase() }
    val capitalized get() = isWord && text[0].isUpperCase()
    override fun toString() = text
}

/** The last sentence of [text]: its tokens, whether it ended with a punctuation mark or a line break, the text before it. */
class Sentence(val tokens: List<Tok>, val closed: Boolean, val before: String, val start: Int, val full: String) {
    val words get() = tokens.filter { it.isWord }
}

object SentenceText {
    /** Characters that make a sentence unsafe to judge (code, addresses, markup). */
    private const val CODE = "{}[]<>=;|\\/@#`_~^$%&*+"

    fun tokenize(text: String, from: Int = 0, to: Int = text.length): List<Tok> {
        val out = ArrayList<Tok>()
        var i = from
        while (i < to) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isLetter() -> {
                    var j = i + 1
                    while (j < to && (text[j].isLetter() || (text[j] == '\'' || text[j] == '’' || text[j] == '-')
                            && j + 1 < to && text[j + 1].isLetter())) j++
                    // a letter followed by a digit is no plain word ("mp3")
                    val kind = if (j < to && text[j].isDigit()) Tok.Kind.OTHER else Tok.Kind.WORD
                    while (j < to && kind == Tok.Kind.OTHER && text[j].isLetterOrDigit()) j++
                    out.add(Tok(text.substring(i, j), i, j, kind)); i = j
                }
                c.isDigit() -> {
                    var j = i + 1
                    while (j < to && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == ',' || text[j] == ':')
                            && !(text[j] == '.' && (j + 1 >= to || !text[j + 1].isDigit()))) j++
                    out.add(Tok(text.substring(i, j), i, j, Tok.Kind.NUMBER)); i = j
                }
                c in ".,!?;:…–—-()\"'„“”‚‘»«" -> { out.add(Tok(c.toString(), i, i + 1, Tok.Kind.PUNCT)); i++ }
                else -> {
                    val n = Character.charCount(text.codePointAt(i))
                    out.add(Tok(text.substring(i, i + n), i, i + n, Tok.Kind.OTHER)); i += n
                }
            }
        }
        return out
    }

    /** Whether the point at token [i] ends a sentence (not an abbreviation, an initial or a number). */
    fun endsSentence(tokens: List<Tok>, i: Int): Boolean {
        val t = tokens[i]
        if (t.kind != Tok.Kind.PUNCT || t.text !in ".!?") return false
        if (t.text != ".") return true
        val before = tokens.getOrNull(i - 1) ?: return true
        if (before.isWord && (before.text.length == 1 || before.lower in GermanWords.abbreviations)) return false
        return true
    }

    /**
     * The last sentence of the paragraph that ends at the end of [text] (a line break just typed ends the paragraph before it).
     * Null when the text holds code, a link or an address (nothing there is judged).
     */
    fun lastSentence(text: String): Sentence? {
        var end = text.length
        val brokeLine = end > 0 && text[end - 1] == '\n'
        if (brokeLine) end--
        while (end > 0 && text[end - 1] == '\r') end--
        val paragraphStart = text.lastIndexOf('\n', end - 1) + 1
        val tokens = tokenize(text, paragraphStart, end)
        var startIndex = 0
        var lastEnd = tokens.size
        // the sentence the text ends in: if it ends with its mark, that one, otherwise the open one after the last mark
        var closedByMark = false
        if (tokens.isNotEmpty() && endsSentence(tokens, tokens.size - 1)) {
            closedByMark = true
            var k = tokens.size - 1
            while (k > 0 && tokens[k - 1].kind == Tok.Kind.PUNCT && tokens[k - 1].text in ".!?") k--
            lastEnd = tokens.size
            for (i in k - 1 downTo 0) if (endsSentence(tokens, i)) { startIndex = i + 1; break }
        } else {
            for (i in tokens.indices.reversed()) if (endsSentence(tokens, i)) { startIndex = i + 1; break }
        }
        val sentence = tokens.subList(startIndex, lastEnd)
        if (sentence.isEmpty()) return null
        val from = sentence.first().start
        val raw = text.substring(from, sentence.last().end)
        if (raw.any { it in CODE } || raw.contains("://") || raw.contains("www.")) return null
        return Sentence(sentence, closedByMark || brokeLine, text.substring(0, from), from, text)
    }
}
