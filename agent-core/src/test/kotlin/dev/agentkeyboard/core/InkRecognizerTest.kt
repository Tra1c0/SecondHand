// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.*

class InkRecognizerTest {
    private val recognizer = InkRecognizer(InkSymbols.templates)

    /**
     * A drawing like a hand would make it from [strokes]: rotated up to ±[rotation]°, stretched per
     * axis, each stroke shifted a little against the others, sampled at a random density, with a
     * slow wobble of up to [wobble] and point jitter of up to [jitter] (in the 100 box), then moved
     * to keyboard-sized pixel coordinates.
     */
    private fun perturb(strokes: List<InkStroke>, random: Random, rotation: Double = 10.0, wobble: Double = 3.0,
            jitter: Double = 1.5, stretch: Double = 0.15): List<InkStroke> {
        val angle = Math.toRadians(random.nextDouble(-rotation, rotation))
        val sx = random.nextDouble(1 - stretch, 1 + stretch); val sy = random.nextDouble(1 - stretch, 1 + stretch)
        val size = random.nextDouble(150.0, 400.0) / 100
        val ox = random.nextDouble(0.0, 600.0); val oy = random.nextDouble(0.0, 300.0)
        return strokes.map { stroke ->
            val shiftX = random.nextDouble(-4.0, 4.0); val shiftY = random.nextDouble(-4.0, 4.0)
            // dense resampling like touch events, then jitter
            val dense = ArrayList<Float>()
            val pieces = if (stroke.size == 1) 0 else random.nextInt(3, 7)
            for (i in 0 until stroke.size) {
                if (i > 0) for (p in 1..pieces) {
                    val t = p.toFloat() / (pieces + 1)
                    dense += stroke.x(i - 1) + t * (stroke.x(i) - stroke.x(i - 1))
                    dense += stroke.y(i - 1) + t * (stroke.y(i) - stroke.y(i - 1))
                }
                dense += stroke.x(i); dense += stroke.y(i)
            }
            val out = FloatArray(dense.size)
            val count = dense.size / 2
            val ax = random.nextDouble(-wobble, wobble); val ay = random.nextDouble(-wobble, wobble)
            val cycles = random.nextDouble(0.5, 2.0); val phase = random.nextDouble(0.0, 2 * Math.PI)
            for (i in 0 until count) {
                val along = 2 * Math.PI * cycles * i / maxOf(1, count - 1) + phase
                val x = (dense[2 * i] - 50) * sx + shiftX + ax * sin(along) + random.nextDouble(-jitter, jitter)
                val y = (dense[2 * i + 1] - 50) * sy + shiftY + ay * cos(along) + random.nextDouble(-jitter, jitter)
                out[2 * i] = ((x * cos(angle) - y * sin(angle)) * size + ox).toFloat()
                out[2 * i + 1] = ((x * sin(angle) + y * cos(angle)) * size + oy).toFloat()
            }
            InkStroke(out)
        }.let { if (random.nextBoolean()) it.reversed() else it } // order does not matter
    }

    @Test fun everyBuiltInDrawingIsRecognizedAsItself() {
        for (symbol in InkSymbols.all) for (variant in symbol.variants) {
            val result = recognizer.recognize(variant)
            assertEquals(symbol.text, result.best?.text, "${symbol.text}: ${result.candidates.take(3)}")
        }
    }

    @Test fun perturbedDrawingsAreRecognizedInAtLeast95Percent() {
        val random = Random(27)
        val perVariant = 25
        var total = 0; var correct = 0; var rejected = 0
        val distances = ArrayList<Float>()
        val confusions = HashMap<Pair<String, String>, Int>()
        val perSymbol = LinkedHashMap<String, IntArray>()
        for (symbol in InkSymbols.all) for (variant in symbol.variants) repeat(perVariant) {
            val result = recognizer.recognize(perturb(variant, random))
            val best = result.best?.text
            result.candidates.firstOrNull { it.text == symbol.text }?.let { distances += it.distance }
            total++
            val counts = perSymbol.getOrPut(symbol.text) { IntArray(2) }
            counts[1]++
            when (best) {
                symbol.text -> { correct++; counts[0]++ }
                null -> { rejected++; confusions.merge(symbol.text to "(nicht erkannt)", 1, Int::plus) }
                else -> confusions.merge(symbol.text to best, 1, Int::plus)
            }
        }
        val rate = correct.toDouble() / total
        distances.sort()
        println("ink: distance of the right symbol, median ${distances[distances.size / 2]}, 95 % ${distances[distances.size * 95 / 100]}, 99 % ${distances[distances.size * 99 / 100]}, max ${distances.last()}")
        println("ink: $correct/$total correct (${"%.1f".format(rate * 100)} %), $rejected rejected")
        confusions.entries.sortedByDescending { it.value }.forEach { (pair, n) -> println("ink confusion: ${pair.first} -> ${pair.second}: $n") }
        perSymbol.forEach { (text, c) -> if (c[0] < c[1]) println("ink per symbol: $text ${c[0]}/${c[1]}") }
        assertTrue(rate >= 0.95, "only ${"%.1f".format(rate * 100)} % correct")
        // no symbol may be hopeless
        perSymbol.forEach { (text, c) -> assertTrue(c[0] >= c[1] * 0.8, "$text: ${c[0]}/${c[1]}") }
    }

    @Test fun scribblesAreMostlyRejected() {
        val random = Random(5)
        var accepted = 0
        val runs = 300
        val distances = ArrayList<Float>()
        repeat(runs) {
            // a random walk of 6 to 14 corners, like an aimless swipe
            val corners = random.nextInt(6, 15)
            val xy = FloatArray(2 * corners) { random.nextDouble(0.0, 100.0).toFloat() }
            val result = recognizer.recognize(listOf(InkStroke(xy)))
            distances += result.candidates.first().distance
            if (result.best != null) accepted++
        }
        distances.sort()
        println("ink: best distance of scribbles, 5 % ${distances[runs * 5 / 100]}, 10 % ${distances[runs / 10]}, median ${distances[runs / 2]}")
        println("ink: $accepted/$runs scribbles accepted")
        assertTrue(accepted <= runs * 0.10, "$accepted of $runs scribbles accepted")
    }

    @Test fun strokeOrderAndDirectionDoNotMatter() {
        val strokes = InkSymbols.all.first { it.text == "≠" }.variants.first()
        val reversed = strokes.reversed().map { s ->
            InkStroke(FloatArray(s.coordinates.size) { i -> val p = s.size - 1 - i / 2; if (i % 2 == 0) s.x(p) else s.y(p) })
        }
        assertEquals("≠", recognizer.recognize(reversed).best?.text)
    }

    @Test fun sizeAndPositionDoNotMatterButAspectDoes() {
        val horizontal = listOf(InkStroke.of(300f, 900f, 700f, 905f))
        val vertical = listOf(InkStroke.of(40f, 10f, 42f, 210f))
        assertEquals("–", recognizer.recognize(horizontal).best?.text)
        assertEquals("|", recognizer.recognize(vertical).best?.text)
    }

    @Test fun dotsCountForDivide() {
        val divide = listOf(InkStroke.of(0f, 50f, 100f, 50f), InkStroke.of(50f, 10f), InkStroke.of(51f, 90f))
        assertEquals("÷", recognizer.recognize(divide).best?.text)
        val minus = listOf(InkStroke.of(0f, 50f, 100f, 50f))
        assertEquals("–", recognizer.recognize(minus).best?.text)
    }

    @Test fun alternativesAreOtherTextsAndCloseEnough() {
        val result = recognizer.recognize(InkSymbols.all.first { it.text == "→" }.variants.first())
        val alternatives = result.alternatives
        assertTrue(alternatives.size <= InkRecognizer.ALTERNATIVES)
        assertTrue(alternatives.none { it.text == "→" })
        assertEquals(alternatives.map { it.text }.distinct(), alternatives.map { it.text })
        assertTrue(alternatives.all { it.distance <= InkRecognizer.ALTERNATIVE_DISTANCE })
    }

    @Test fun ownTemplatesAreMatchedTogetherWithBuiltIns() {
        // a "W", which no built-in symbol looks like
        val spiral = InkStroke.of(0f, 0f, 25f, 100f, 50f, 40f, 75f, 100f, 100f, 0f)
        assertNotEquals("🌀", recognizer.recognize(listOf(spiral)).best?.text)
        val own = InkTemplate("🌀", InkCloud.of(listOf(spiral))!!, own = true)
        val withOwn = InkRecognizer(InkSymbols.templates + own)
        val random = Random(9)
        repeat(10) {
            val r = withOwn.recognize(perturb(listOf(spiral), random))
            val match = r.best
            assertEquals("🌀", match?.text)
            assertTrue(match!!.own)
        }
        // the built-in ones still work
        assertEquals("→", withOwn.recognize(InkSymbols.all.first { it.text == "→" }.variants.first()).best?.text)
    }

    @Test fun thresholdRejectsFarDrawings() {
        fun result(vararg distances: Float) = InkResult(distances.mapIndexed { i, d -> InkMatch("s$i", d, false) })
        val accept = InkRecognizer.ACCEPT_DISTANCE
        assertEquals("s0", result(accept, accept + 0.01f).best?.text)
        // beyond the accept distance only with a clear lead
        assertNull(result(accept + 0.1f, accept + 0.2f).best)
        assertEquals("s0", result(accept + 0.1f, (accept + 0.1f) * InkRecognizer.CLEAR_MARGIN + 0.01f).best?.text)
        assertNull(result(InkRecognizer.CLEAR_DISTANCE + 0.01f, 99f).best)
        assertTrue(result(InkRecognizer.CLEAR_DISTANCE + 0.01f, 99f).alternatives.isEmpty())
        // alternatives only close enough
        val near = result(0.5f, 1.0f, InkRecognizer.ALTERNATIVE_DISTANCE + 0.5f)
        assertEquals(listOf("s1"), near.alternatives.map { it.text })
        assertEquals("s0", result(0.5f).best?.text) // a single candidate
    }

    @Test fun recognitionIsFastEnoughForTheMainThread() {
        val drawing = InkSymbols.all.first { it.text == "€" }.variants.first()
        repeat(5) { recognizer.recognize(drawing) } // warm up
        val start = System.nanoTime()
        repeat(20) { recognizer.recognize(drawing) }
        val millis = (System.nanoTime() - start) / 20 / 1_000_000.0
        println("ink: ${"%.1f".format(millis)} ms per drawing against ${InkSymbols.templates.size} templates")
        assertTrue(millis < 50, "$millis ms")
    }

    @Test fun emptyOrOverlongInputIsNoSymbol() {
        assertNull(InkCloud.of(emptyList()))
        assertNull(InkCloud.of(List(InkCloud.MAX_STROKES + 1) { InkStroke.of(it.toFloat(), 0f, it + 1f, 5f) }))
        assertTrue(recognizer.recognize(emptyList()).candidates.isEmpty())
        // a single tap is a cloud of one point and matches nothing well
        assertNull(recognizer.recognize(listOf(InkStroke.of(5f, 5f))).best)
    }

    @Test fun cloudsHaveFixedSizeAndAreCentred() {
        for (template in InkSymbols.templates) {
            val c = template.cloud
            assertEquals(InkCloud.SIZE, c.xs.size)
            assertEquals(0f, c.xs.average().toFloat(), 1e-3f)
            assertEquals(0f, c.ys.average().toFloat(), 1e-3f)
            assertTrue((c.xs.max() - c.xs.min()).coerceAtLeast(c.ys.max() - c.ys.min()) in 0.99f..1.01f)
        }
        assertTrue(InkSymbols.all.size in 30..50)
        assertEquals(InkSymbols.all.size, InkSymbols.all.map { it.text }.distinct().size)
    }

    // --- strokes of one symbol ---

    @Test fun strokesWithinThePauseFormOneSymbol() {
        val group = InkGroup(500)
        assertFalse(group.continuesAt(0))
        assertEquals(-1, group.dueAt)
        assertTrue(group.add(InkStroke.of(0f, 0f, 10f, 0f), 1_000))
        assertEquals(1_500, group.dueAt)
        assertTrue(group.continuesAt(1_499))
        assertFalse(group.continuesAt(1_500))
        assertTrue(group.add(InkStroke.of(0f, 5f, 10f, 5f), 1_300))
        assertEquals(1_800, group.dueAt)
        assertEquals(2, group.take().size)
        assertTrue(group.isEmpty)
        assertEquals(-1, group.dueAt)
    }

    @Test fun groupRefusesTooManyStrokesOrPoints() {
        val group = InkGroup()
        repeat(InkCloud.MAX_STROKES) { assertTrue(group.add(InkStroke.of(0f, 0f), it.toLong())) }
        assertFalse(group.add(InkStroke.of(0f, 0f), 99))
        group.clear()
        assertFalse(group.add(InkStroke(FloatArray(2 * (InkGroup.MAX_POINTS + 1))), 0))
        assertFalse(group.add(InkStroke(FloatArray(0)), 0))
        assertTrue(group.isEmpty)
    }
}
