// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import kotlin.math.abs
import kotlin.math.min

/**
 * Stage 100 "Gezeichnete Symbole ohne Wartezeit": is a drawing finished, or may a further stroke still come? Until
 * stage 99 every drawing waited [InkPace.pauseMillis] (600–1500 ms) after its last stroke, because any stroke could be
 * the first of a symbol with more strokes. Now a drawing is written at once when it is [finished]: the recognizer
 * accepts it as a complete symbol, that symbol cannot go on ([extendable]: "≤", "∞", "÷", "#" cannot become anything
 * else; "–" can become "=", "→", "÷", "→" can become "↔", "%" can become "‰"), and the drawing itself is no part of a
 * template with more strokes ([continues], a safety net for a sloppy drawing). Everything else waits as before, with
 * the pause following the user's own gaps ([InkPace]). Nothing is written speculatively.
 *
 * A part is any proper subset of a template's strokes (stroke order and direction never matter, so the first strokes
 * drawn can be any of its strokes): "–" is a part of "=", "÷", "→" and "+", the slash of "%" and "×". Parts are matched
 * like whole templates ([InkRecognizer.greedyCloudMatch] with the direction term and the structure term, upright and
 * turned by the recognizer's rotations; lines and dots counted apart). A drawing that waits once too often costs the
 * pause it always cost; one written too early splits a symbol in two, so both bounds are generous. Built-in templates
 * have at most five strokes; a template with many strokes (an own symbol may have twelve) only offers the parts in the
 * order it was drawn ([MAX_SUBSETS]). Built-in, own and personal templates all count.
 *
 * Pure and thread-safe once built; the parts are built lazily on first use (the keyboard asks on its
 * worker thread). Nothing is stored.
 */
class InkCompletion(templates: List<InkTemplate>, private val tuning: InkRecognizer.Tuning = InkRecognizer.Tuning.DEFAULT) {
    private val templates = templates.toList()

    /** A proper part of a template: [strokes] of [of] strokes of the template of [text]. */
    class Part(val text: String, val cloud: InkCloud, val strokes: Int, val of: Int)

    /** The parts by their number of strokes (lines and dots). */
    private val parts: Map<Int, List<Part>> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { build() }

    /** How many parts there are in all (tests, diagnostics). */
    val partCount: Int get() = parts.values.sumOf { it.size }

    /** True when the drawing [strokes] may still be the first strokes of a symbol with more strokes. */
    fun continues(strokes: List<InkStroke>): Boolean = InkCloud.of(strokes)?.let { continues(it) } ?: true

    /** True when [cloud] is close to a part of a template with more strokes ([PART_DISTANCE]). */
    fun continues(cloud: InkCloud): Boolean = nearestPart(cloud, PART_DISTANCE, firstWithin = true) != null

    /**
     * Texts that can go on: some template of the text is within [EXTEND_DISTANCE] of a part of a template with more
     * strokes ("–" of "=", "→" of "↔", "%" of "‰", ")" of the head of "⇒"). Computed once per set of templates.
     */
    private val extendableTexts: Set<String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val out = HashSet<String>()
        for (template in templates) {
            if (template.text in out) continue
            if (nearestPart(template.cloud, EXTEND_DISTANCE, firstWithin = true) != null) out += template.text
        }
        out
    }

    /** True when a drawing recognized as [text] may still go on (stage 100: then it waits for a further stroke). */
    fun extendable(text: String): Boolean = text in extendableTexts

    /**
     * Stage 100: the drawing [cloud], recognized as [result], is finished and can be written at once: its best match is a
     * complete symbol within [InkRecognizer.ACCEPT_DISTANCE] (not only the fallback to an incomplete one), that symbol
     * cannot go on ([extendable]), and the drawing itself is no part of a template with more strokes ([continues]).
     * Anything else may still get a stroke and waits. Never true for a drawing nothing is accepted for.
     */
    fun finished(cloud: InkCloud, result: InkResult): Boolean {
        val best = result.best ?: return false
        if (!best.complete || best.distance > InkRecognizer.ACCEPT_DISTANCE) return false
        if (extendable(best.text)) return false
        return !continues(cloud)
    }

    /**
     * The closest part within [bound] (inclusive), or null when no template with more strokes than the drawing has a
     * part that close; with [firstWithin] the first one found within it (only whether there is one counts). Parts are
     * matched upright and turned by the recognizer's rotations.
     */
    fun nearestPart(cloud: InkCloud, bound: Float = CALIBRATION_BOUND, firstWithin: Boolean = false): Pair<Part, Float>? {
        val candidates = parts[cloud.strokeCount] ?: return null
        val turned = tuning.rotations.filter { it != 0f }.map { InkCloud.rotated(cloud, it) }
        var best: Part? = null
        var bestDistance = Float.MAX_VALUE
        // the bound itself counts: summing stops at the bound, so stop just above it
        val limit = bound + 1e-4f
        for (part in candidates) {
            val structure = tuning.lineWeight * abs(cloud.lineCount - part.cloud.lineCount) +
                tuning.dotWeight * abs(cloud.dotCount - part.cloud.dotCount)
            val room = min(limit, bestDistance) - structure
            if (room <= 0f) continue
            // upright first, summed up to turnNear times the room: turning gains far less (as in the recognizer), so a part
            // that is farther than that upright is not turned (stage 100, speed on a slow phone)
            var d = InkRecognizer.greedyCloudMatch(cloud, part.cloud, room * tuning.turnNear, tuning.angleWeight)
            if (d >= room * tuning.turnNear) continue
            for (candidate in turned) { if (d < room) break; d = min(d, InkRecognizer.greedyCloudMatch(candidate, part.cloud, room, tuning.angleWeight)) }
            // greedyCloudMatch answers its bound when nothing came closer: that is no match
            if (d >= room) continue
            bestDistance = d + structure
            best = part
            if (firstWithin) break
        }
        return best?.let { it to min(bestDistance, bound) }
    }

    private fun build(): Map<Int, List<Part>> {
        val out = HashMap<Int, ArrayList<Part>>()
        for (template in templates) {
            val strokes = strokesOf(template.cloud)
            val k = strokes.size
            if (k < 2) continue
            for (n in 1 until k) {
                for (subset in subsets(k, n)) {
                    val cloud = InkCloud.of(subset.map { strokes[it] }) ?: continue
                    out.getOrPut(cloud.strokeCount) { ArrayList() } += Part(template.text, cloud, n, k)
                }
            }
        }
        return out
    }

    companion object {
        /**
         * A drawing this close to a part of a template with more strokes waits. With [EXTEND_DISTANCE] calibrated with the
         * finger model (InkCompletionTest, docs/VALIDATION.md, stage 100): at 2.0/2.0 no true beginning of any drawing of
         * the model, in any stroke order, is taken as finished; at 1.5 and an accepted distance up to 2.3 a few were.
         */
        const val PART_DISTANCE = 2.0f

        /** A symbol with a template this close to a part of a template with more strokes can go on ([extendable]). */
        const val EXTEND_DISTANCE = 2.0f

        /** [nearestPart] without a bound reports distances up to this (calibration only). */
        const val CALIBRATION_BOUND = 6f

        /**
         * At most this many subsets per template and part size; beyond, only the strokes in the order the
         * template was drawn (an own symbol with many strokes, drawn three times in the user's own order).
         */
        const val MAX_SUBSETS = 35

        /** The strokes of a normalized cloud, in its order (a dot is one or a few equal points). */
        internal fun strokesOf(cloud: InkCloud): List<InkStroke> {
            val out = ArrayList<InkStroke>()
            var start = 0
            for (i in 1..InkCloud.SIZE) if (i == InkCloud.SIZE || cloud.strokes[i] != cloud.strokes[start]) {
                out += InkStroke(FloatArray(2 * (i - start)) { k -> if (k % 2 == 0) cloud.xs[start + k / 2] else cloud.ys[start + k / 2] })
                start = i
            }
            return out
        }

        /** The index sets of size [n] out of [k]; the first [n] only when there would be more than [MAX_SUBSETS]. */
        internal fun subsets(k: Int, n: Int): List<IntArray> {
            if (binomial(k, n) > MAX_SUBSETS) return listOf(IntArray(n) { it })
            val out = ArrayList<IntArray>()
            val current = IntArray(n)
            fun pick(from: Int, depth: Int) {
                if (depth == n) { out += current.copyOf(); return }
                for (i in from..k - (n - depth)) { current[depth] = i; pick(i + 1, depth + 1) }
            }
            pick(0, 0)
            return out
        }

        private fun binomial(k: Int, n: Int): Long {
            var r = 1L
            for (i in 1..n) r = r * (k - n + i) / i
            return r
        }
    }
}
