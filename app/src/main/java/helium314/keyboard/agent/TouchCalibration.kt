// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.graphics.Rect
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.common.Constants

/**
 * Stage 22a: the calibration in the settings. While the calibration field has the focus, the
 * keyboard records each letter touch (raw position and the key it landed on) and the geometry of
 * the letter keys. The settings page then aligns the touched letters with the text the user was
 * asked to type and computes, for each intended key, where the touches landed. Everything stays
 * in memory and is dropped when the page is left.
 */
object TouchCalibration {
    /** Text to type: two pangrams and a plain sentence, so every letter of QWERTZ occurs. */
    const val TEXT = "Franz jagt im komplett verwahrlosten Taxi quer durch Bayern. " +
        "Victor jagt zwei Boxer quer durch den Sylter Deich. " +
        "Die Katze sitzt heute wieder auf dem warmen Fensterbrett und schaut hinaus."

    /** Cost of an extra or missing letter; a substitution costs 1 between neighbouring keys and 2 otherwise. */
    private const val GAP = 2

    private fun step(touched: Int, intended: Int, substitution: (Int, Int) -> Int) =
        if (touched == intended) 0 else substitution(touched, intended)

    /** Hitting a neighbouring key is a likely slip, a key further away is not. */
    private fun substitutionCost(a: Int, b: Int): Int {
        val first = geometry[a] ?: return 2
        val second = geometry[b] ?: return 2
        val dx = (first.exactCenterX() - second.exactCenterX()) / first.width()
        val dy = (first.exactCenterY() - second.exactCenterY()) / first.height()
        return if (dx * dx + dy * dy <= 1.6f * 1.6f) 1 else 3
    }

    /** Keys with fewer touches than this are left out of the result. */
    const val MIN_TOUCHES = 4

    private class Touch(val code: Int, val x: Int, val y: Int)

    @Volatile @JvmStatic var active = false
    private val touches = ArrayList<Touch>()
    /** Lower-case letter code to hit box, as last seen. */
    private val geometry = HashMap<Int, Rect>()

    @JvmStatic
    fun start() = synchronized(this) { touches.clear(); geometry.clear() }

    /** Called by the keyboard for each key press while [active]. */
    @JvmStatic
    fun onKey(code: Int, viewX: Int, viewY: Int, paddingLeft: Int, paddingTop: Int, keyboard: Keyboard?) {
        if (!active) return
        synchronized(this) {
            if (code == helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.DELETE) {
                if (touches.isNotEmpty()) touches.removeAt(touches.size - 1)
                return
            }
            val lower = Character.toLowerCase(code)
            if (!Character.isLetter(lower) || viewX == Constants.NOT_A_COORDINATE || viewY == Constants.NOT_A_COORDINATE) return
            val x = viewX - paddingLeft
            val y = viewY - paddingTop
            keyboard?.sortedKeys?.forEach { key ->
                val c = Character.toLowerCase(key.code)
                if (Character.isLetter(c)) geometry[c] = Rect(key.hitBox)
            }
            // a letter from a popup has coordinates of the popup: only touches on a letter key count
            val box = geometry[lower] ?: return
            if (!box.contains(x, y)) return
            touches.add(Touch(lower, x, y))
        }
    }

    @JvmStatic fun touchCount(): Int = synchronized(this) { touches.size }

    /**
     * Per intended letter: mean offset from its key centre in key sizes and number of touches.
     * Letters not on the keyboard (umlauts on long press, ß, punctuation) are skipped.
     */
    fun result(target: String = TEXT): Map<Int, Triple<Float, Float, Int>> = synchronized(this) {
        val wanted = target.lowercase().map { it.code }.filter { it in geometry }
        val pairs = align(touches.map { it.code }, wanted) { a, b -> substitutionCost(a, b) }
        val sums = HashMap<Int, FloatArray>()
        pairs.forEach { (touchIndex, code) ->
            val touch = touches[touchIndex]
            val box = geometry[code] ?: return@forEach
            val ox = (touch.x - box.exactCenterX()) / box.width()
            val oy = (touch.y - box.exactCenterY()) / box.height()
            if (kotlin.math.abs(ox) > TouchModel.OUTLIER || kotlin.math.abs(oy) > TouchModel.OUTLIER) return@forEach
            val s = sums.getOrPut(code) { FloatArray(3) }
            s[0] += ox; s[1] += oy; s[2] += 1f
        }
        sums.filterValues { it[2] >= MIN_TOUCHES }
            .mapValues { (_, s) -> Triple(s[0] / s[2], s[1] / s[2], s[2].toInt()) }
    }

    /**
     * Edit-distance alignment of the touched letters with the intended ones. Returns
     * (index of touch, intended letter) for matches and substitutions; extra or missing letters
     * are left out.
     */
    internal fun align(touched: List<Int>, intended: List<Int>, substitution: (Int, Int) -> Int = { _, _ -> 1 }): List<Pair<Int, Int>> {
        val n = touched.size; val m = intended.size
        val cost = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) cost[i][0] = i
        for (j in 0..m) cost[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            cost[i][j] = minOf(cost[i - 1][j - 1] + step(touched[i - 1], intended[j - 1], substitution),
                cost[i - 1][j] + GAP, cost[i][j - 1] + GAP)
        }
        val result = ArrayList<Pair<Int, Int>>()
        var i = n; var j = m
        while (i > 0 && j > 0) {
            when {
                cost[i][j] == cost[i - 1][j - 1] + step(touched[i - 1], intended[j - 1], substitution) -> {
                    result.add(i - 1 to intended[j - 1]); i--; j--
                }
                cost[i][j] == cost[i - 1][j] + GAP -> i--
                else -> j--
            }
        }
        return result.asReversed()
    }
}
