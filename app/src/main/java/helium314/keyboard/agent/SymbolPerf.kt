// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

/**
 * Stage 100: how fast drawn symbols are written, for the dump line `symbolPerf:` (`dumpsys input_method`, read by the
 * device tests) and the card "Symbole" on the Diagnose page.
 *
 * Numbers only, never what was drawn or written: for every finished drawing the way it was written ([Way]) and the time
 * from lifting the finger off its last stroke to the end of writing it (`SystemClock.uptimeMillis`), how long the check
 * "can this drawing still go on?" took, and late strokes (a new drawing that starts near one just written after the
 * pause: the pause was probably too short). In memory only, gone with the process; [reset] clears it. Main thread only,
 * except [decided] (the worker), so every access is synchronized; the cost is a few array writes per drawing.
 */
object SymbolPerf {
    /** How a drawing was written. */
    enum class Way(val mark: Char) {
        /** Stage 100: right after its last stroke, because nothing can follow (InkCompletion). */
        INSTANT('i'),
        /** After the pause for a further stroke. */
        PAUSE('p'),
        /** Another key or a tap that is no dot came first (flush). */
        KEY('k'),
        /** The preview chip was tapped. */
        CHIP('c'),
    }

    /** Drawings kept; older ones fall out. */
    const val KEPT = 64

    private val ways = arrayOfNulls<Way>(KEPT)
    private val millis = LongArray(KEPT)
    private var next = 0
    private var finished = 0
    private val counts = IntArray(Way.values().size)
    private val decideMillis = LongArray(KEPT)
    private var decisions = 0
    private var lateAfterPause = 0
    private var lateAfterInstant = 0

    /** A drawing was written [way], [sinceStrokeEndMillis] after its last stroke ended (negative: unknown). */
    @Synchronized fun written(way: Way, sinceStrokeEndMillis: Long) {
        ways[next] = way
        millis[next] = sinceStrokeEndMillis
        next = (next + 1) % KEPT
        finished++
        counts[way.ordinal]++
    }

    /** The check whether a drawing can go on took [millis] (stage 100). */
    @Synchronized fun decided(millis: Long) {
        decideMillis[decisions % KEPT] = millis
        decisions++
    }

    /** A new drawing started near one written [after] its way shortly before (stage 100). */
    @Synchronized fun late(after: Way) {
        if (after == Way.INSTANT) lateAfterInstant++ else lateAfterPause++
    }

    @Synchronized fun reset() {
        ways.fill(null); millis.fill(0); next = 0; finished = 0; counts.fill(0)
        decideMillis.fill(0); decisions = 0; lateAfterPause = 0; lateAfterInstant = 0
    }

    /** How many drawings were written [way] since the start (or [reset]). */
    @Synchronized fun count(way: Way): Int = counts[way.ordinal]

    /** The median time from the last stroke to the written symbol of the kept drawings written [way], or -1. */
    @Synchronized fun medianMillis(way: Way): Long {
        val values = (0 until KEPT).filter { ways[it] == way && millis[it] >= 0 }.map { millis[it] }.sorted()
        return if (values.isEmpty()) -1 else values[values.size / 2]
    }

    /** The median time of the check (stage 100), or -1. */
    @Synchronized fun medianDecideMillis(): Long {
        val values = decideMillis.copyOf(minOf(decisions, KEPT)).sorted()
        return if (values.isEmpty()) -1 else values[values.size / 2]
    }

    /** Late strokes after a drawing written after the pause and right away. */
    @Synchronized fun lateCounts(): Pair<Int, Int> = lateAfterPause to lateAfterInstant

    /**
     * One line for `LatinIME.dump` (numbers only): the counts by way, medians, late strokes, the pause now ([pauseMillis])
     * and the gaps it is based on, and the last drawings oldest first as way mark and milliseconds ("p612").
     */
    @Synchronized fun line(pauseMillis: Long, gaps: Int): String {
        val last = StringBuilder()
        val kept = minOf(finished, KEPT)
        for (k in 0 until kept) {
            val i = ((next - kept + k) % KEPT + KEPT) % KEPT
            if (last.isNotEmpty()) last.append(',')
            last.append(ways[i]?.mark ?: '?').append(millis[i])
        }
        return "symbolPerf: finished=$finished " + Way.values().joinToString(" ") { "${it.name.lowercase()}=${counts[it.ordinal]}" } +
            " instantMed=${medianMillis(Way.INSTANT)} pauseMed=${medianMillis(Way.PAUSE)} decideMed=${medianDecideMillis()}" +
            " late=$lateAfterPause/$lateAfterInstant pauseNow=$pauseMillis gaps=$gaps last=[$last]"
    }
}
