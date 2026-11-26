// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.sentence

/**
 * Stages 104/105: the case forms of German nouns with gender and number, from the bundled file `german_nouns.txt`
 * (German Wiktionary via german-nouns, CC BY-SA 4.0; built by `tools/build_german_lexicon.py`). One line per form:
 * form, readings mask in hex, main dictionary frequency. Kept as one sorted array (binary search, no map per form).
 *
 * A reading is gender (m, f, n, or plural) and case (nominative, genitive, dative, accusative); bit = 4 * gender + case.
 */
class GermanNouns(lines: Sequence<String>) {
    private val forms: Array<String>
    private val masks: ShortArray
    private val frequencies: ShortArray

    init {
        val f = ArrayList<String>(48_000)
        val m = ArrayList<Short>(48_000)
        val q = ArrayList<Short>(48_000)
        for (line in lines) {
            if (line.isEmpty() || line.startsWith("#")) continue
            val a = line.indexOf('\t')
            val b = line.indexOf('\t', a + 1)
            if (a <= 0 || b <= a) continue
            val mask = line.substring(a + 1, b).toIntOrNull(16) ?: continue
            f.add(line.substring(0, a))
            m.add(mask.toShort())
            q.add((line.substring(b + 1).trim().toIntOrNull() ?: -1).toShort())
        }
        // the file is sorted (UTF-16 order); sort only if it is not, so that the binary search is right
        val sorted = (1 until f.size).all { f[it - 1] < f[it] }
        val order = if (sorted) null else f.indices.sortedWith { x, y -> f[x].compareTo(f[y]) }
        forms = Array(f.size) { f[order?.get(it) ?: it] }
        masks = ShortArray(f.size) { m[order?.get(it) ?: it] }
        frequencies = ShortArray(f.size) { q[order?.get(it) ?: it] }
    }

    val size: Int get() = forms.size

    private fun index(form: String): Int = forms.binarySearch(form)

    /** The readings mask of [form] exactly as written (0: not a known noun form). */
    fun readings(form: String): Int = index(form).let { if (it >= 0) masks[it].toInt() and 0xFFFF else 0 }

    /** The main dictionary frequency stored with [form] (-1: unknown or not a noun form). */
    fun frequency(form: String): Int = index(form).let { if (it >= 0) frequencies[it].toInt() else -1 }

    /** Every noun form that starts with [prefix] (as written), in order. */
    fun withPrefix(prefix: String): Sequence<String> = sequence {
        var i = forms.binarySearch(prefix).let { if (it < 0) -it - 1 else it }
        while (i < forms.size && forms[i].startsWith(prefix)) yield(forms[i++])
    }

    companion object {
        const val MASC = 0
        const val FEM = 1
        const val NEUT = 2
        const val PLURAL = 3
        const val NOM = 0
        const val GEN = 1
        const val DAT = 2
        const val ACC = 3

        @JvmStatic fun bit(gender: Int, case: Int): Int = 1 shl (4 * gender + case)

        /** The readings of [mask] restricted to the cases in [cases] (a set of case numbers as bits 0..3). */
        @JvmStatic fun inCases(mask: Int, cases: Int): Int {
            var out = 0
            for (g in 0..3) for (c in 0..3) if (cases and (1 shl c) != 0) out = out or (mask and bit(g, c))
            return out
        }

        /** The case bits (0..3) that occur in [mask]. */
        @JvmStatic fun cases(mask: Int): Int {
            var out = 0
            for (g in 0..3) for (c in 0..3) if (mask and bit(g, c) != 0) out = out or (1 shl c)
            return out
        }
    }
}
