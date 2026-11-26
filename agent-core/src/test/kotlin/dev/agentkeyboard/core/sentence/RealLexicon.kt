// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

import java.io.File
import kotlin.test.fail

/**
 * Stages 104/105, tests only: the shipped lexicon files and the shipped German main dictionary, read from the app's assets,
 * so that the sentence check is tested with the data the keyboard has (not with a hand-made list).
 */
object RealLexicon {
    fun repoFile(path: String): File =
        listOf(File("../$path"), File(path)).firstOrNull { it.isFile } ?: fail("$path not found from ${File(".").absoluteFile}")

    val lemmas: Set<String> by lazy {
        repoFile("app/src/main/assets/agent/german_verb_lemmas.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }.toSet()
    }
    val lexicon: SentenceLexicon by lazy {
        SentenceLexicon(
            GermanVerbs({ it in lemmas }, repoFile("app/src/main/assets/agent/german_verb_forms.txt").readLines().asSequence()),
            GermanNouns(repoFile("app/src/main/assets/agent/german_nouns.txt").readLines().asSequence()),
        )
    }

    /** word as stored -> frequency 0..255 (not-a-word entries left out), like tools/build_informal_words.py reads it. */
    val german: Map<String, Int> by lazy { readDictionary(repoFile("app/src/main/assets/dicts/main_de.dict").readBytes()) }

    private fun readDictionary(b: ByteArray): Map<String, Int> {
        fun u(i: Int) = b[i].toInt() and 0xFF
        val header = (u(8) shl 24) or (u(9) shl 16) or (u(10) shl 8) or u(11)
        val words = HashMap<String, Int>(260_000)
        val stack = ArrayDeque<Pair<Int, String>>()
        stack.add(header to "")
        while (stack.isNotEmpty()) {
            var (pos, prefix) = stack.removeLast()
            var count = u(pos++)
            if (count and 0x80 != 0) count = ((count and 0x7F) shl 8) or u(pos++)
            repeat(count) {
                val flags = u(pos++)
                val sb = StringBuilder(prefix)
                fun codepoint(): Int? {
                    val c = u(pos)
                    if (c == 0x1F) { pos++; return null }
                    if (c < 0x20) { val v = (c shl 16) or (u(pos + 1) shl 8) or u(pos + 2); pos += 3; return v }
                    pos++; return c
                }
                if (flags and 0x20 != 0) {
                    while (true) { val cp = codepoint() ?: break; sb.appendCodePoint(cp) }
                } else sb.appendCodePoint(codepoint()!!)
                val word = sb.toString()
                var probability = -1
                if (flags and 0x10 != 0) probability = u(pos++)
                val children = flags and 0xC0
                var child = -1
                if (children != 0) {
                    val size = when (children) { 0x40 -> 1; 0x80 -> 2; else -> 3 }
                    var off = 0
                    repeat(size) { off = (off shl 8) or u(pos + it) }
                    child = pos + off
                    pos += size
                }
                if (flags and 0x08 != 0) pos += (u(pos) shl 8) or u(pos + 1)
                if (flags and 0x04 != 0) {
                    while (true) { val f = u(pos++); pos += (f shr 4) and 3; if (f and 0x80 == 0) break }
                }
                if (probability >= 0 && flags and 0x02 == 0) words[word] = maxOf(words[word] ?: 0, probability)
                if (child >= 0) stack.add(child to word)
            }
        }
        return words
    }

    /** A context with the real dictionary; [protected] and [rejected] as a test needs them. */
    class Context(private val protected: Set<String> = emptySet(), private val rejected: Set<Pair<String, String>> = emptySet()) : SentenceContext {
        override fun frequency(word: String): Int = german[word] ?: -1
        override fun isProtected(word: String): Boolean = word.lowercase() in protected
        override fun isRejected(old: String, new: String): Boolean = (old.lowercase() to new.lowercase()) in rejected
    }
}
