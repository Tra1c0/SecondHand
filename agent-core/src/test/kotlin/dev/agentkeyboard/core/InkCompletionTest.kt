// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stage 100: when is a drawn symbol finished (written at once) and when may a further stroke still come (it waits)?
 * The finger model ([InkFingerModel]: every symbol in the ways a hand draws it, strokes in random order and direction,
 * sizes from 1.2 to 4 key widths, shortened, bent, hooked, jittered, turned) is the benchmark: no true beginning of a
 * drawing may ever be taken as finished, and the symbols that cannot go on should mostly be written at once.
 */
class InkCompletionTest {
    private val recognizer = InkRecognizer(InkSymbols.templates)
    private val completion = InkCompletion(InkSymbols.templates)

    private fun finished(strokes: List<InkStroke>, rule: InkCompletion = completion, matcher: InkRecognizer = recognizer): Boolean {
        val cloud = InkCloud.of(strokes) ?: return false
        return rule.finished(cloud, matcher.recognize(cloud))
    }

    private fun best(strokes: List<InkStroke>) = recognizer.recognize(strokes).best?.text

    /** Screen pixels from the 0..100 box, 3 px a unit, as a finger on a phone. */
    private fun s(vararg xy: Number) = InkStroke(FloatArray(xy.size) { xy[it].toFloat() * 3 + 200 })

    private val dash = s(10, 50, 90, 50)
    private val dashBack = s(90, 50, 10, 50)
    private val head = s(62, 22, 92, 50, 62, 78)
    private val headBack = s(62, 78, 92, 50, 62, 22)
    private val slash = s(82, 6, 18, 94)

    @Test fun noTrueBeginningOfAnyFingerDrawingIsTakenAsFinished() {
        val r = Random(100)
        var beginnings = 0
        val early = ArrayList<String>()
        for (text in InkFingerModel.symbols) repeat(150) {
            val drawing = InkFingerModel.draw(text, r)
            for (k in 1 until drawing.size) {
                beginnings++
                if (finished(drawing.subList(0, k))) early += "$text: first $k of ${drawing.size} as ${best(drawing.subList(0, k))}"
            }
        }
        for (text in InkFingerModel.dottedSymbols) repeat(150) {
            val drawing = InkFingerModel.drawDotted(text, r)
            for (k in 1 until drawing.size) {
                beginnings++
                if (finished(drawing.subList(0, k))) early += "$text (dots): first $k of ${drawing.size} as ${best(drawing.subList(0, k))}"
            }
        }
        println("stage 100: $beginnings true beginnings, ${early.size} taken as finished")
        assertTrue(beginnings > 8000)
        assertEquals(emptyList(), early.take(20))
    }

    @Test fun noTrueBeginningIsTakenAsFinishedWithPersonalTemplatesOfAShakyHand() {
        // concept review, finding 2: calibrated templates (two finger drawings of every symbol, as "Kalibrieren" keeps
        // them) change what is accepted and which parts there are; the margin to PART_DISTANCE is printed
        val r = Random(41)
        var personal = emptyList<OwnInkSymbols.Symbol>()
        for (text in InkFingerModel.symbols) repeat(2) {
            personal = PersonalInkSymbols.add(personal, text, InkCloud.of(InkFingerModel.draw(text, r))!!)
        }
        val all = PersonalInkSymbols.templates(personal) + InkSymbols.templates
        val rule = InkCompletion(all)
        val matcher = InkRecognizer(all)
        var beginnings = 0
        var farthest = 0f
        val early = ArrayList<String>()
        for (text in InkFingerModel.symbols) repeat(60) {
            val drawing = InkFingerModel.draw(text, r)
            for (k in 1 until drawing.size) {
                beginnings++
                val cloud = InkCloud.of(drawing.subList(0, k))!!
                val result = matcher.recognize(cloud)
                val best = result.best
                // the beginnings that only the part check holds back: how close did they come to a part?
                if (best != null && best.complete && best.distance <= InkRecognizer.ACCEPT_DISTANCE && !rule.extendable(best.text))
                    farthest = maxOf(farthest, rule.nearestPart(cloud)?.second ?: InkCompletion.CALIBRATION_BOUND)
                if (rule.finished(cloud, result)) early += "$text: first $k of ${drawing.size} as ${best?.text}"
            }
        }
        println("stage 100, ${personal.size} personal symbols: $beginnings beginnings, ${early.size} taken as finished; " +
            "the farthest beginning held back only by the part check was $farthest from a part (bound ${InkCompletion.PART_DISTANCE})")
        assertEquals(emptyList(), early.take(20))
    }

    @Test fun theSymbolsThatCannotGoOnAreMostlyWrittenAtOnceTheOthersNever() {
        val r = Random(7)
        val share = LinkedHashMap<String, Int>()
        var total = 0; var atOnce = 0
        for (text in InkFingerModel.symbols) {
            var n = 0
            repeat(150) { if (finished(InkFingerModel.draw(text, r))) n++ }
            share[text] = n * 100 / 150
            total += 150; atOnce += n
        }
        println("stage 100: written at once (%): " + share.entries.joinToString { "${it.key} ${it.value}" } + "; all ${atOnce * 100 / total} %")
        // cannot go on: at once in most drawings (the rest were not accepted as complete, or came near a part)
        for (text in listOf("≤", "≥", "±", "÷", "∞", "#", "?", "¿")) assertTrue(share.getValue(text) >= 80, "$text: ${share[text]} %")
        // can go on: never at once ("–" of "=", "→" of "↔", "/" of "%", "°" of "%", "~" of "≈", ")" of the head of "⇒")
        for (text in listOf("–", "|", "→", "←", "↑", "↓", "=", "≠", "~", "°", "+", "(", ")", "€", "%", "!", "¡"))
            assertEquals(0, share.getValue(text), text)
        assertTrue(atOnce * 100 / total >= 25, "${atOnce * 100 / total} %")
    }

    @Test fun anArrowFromDashAndHeadInEveryOrderAndDirectionIsOneArrowThatWaitsForAPossibleSecondHead() {
        for (parts in listOf(listOf(dash, head), listOf(head, dash), listOf(dashBack, head), listOf(headBack, dashBack))) {
            assertEquals("→", best(parts))
            assertFalse(finished(parts), "↔ can still follow")
            assertFalse(finished(parts.subList(0, 1)), "the first stroke alone waits")
        }
        assertFalse(finished(listOf(dash, head, s(38, 22, 8, 50, 38, 78)))) // ↔ itself: its 5-stroke drawing has it as a part
        assertEquals("↔", best(listOf(dash, head, s(38, 22, 8, 50, 38, 78))))
    }

    @Test fun aPercentSignFromASlashAndTwoDotsInEitherOrderOfTheDots() {
        val a = s(20, 20); val b = s(80, 80)
        assertEquals("%", best(listOf(slash, a, b)))
        assertEquals("%", best(listOf(slash, b, a)))
        assertFalse(finished(listOf(slash))) // "/" may get dots
        assertFalse(finished(listOf(slash, a))) // and a second one
        assertFalse(finished(listOf(slash, a, b))) // and "%" may still become "‰"
    }

    @Test fun aDivisionSignIsWrittenAtOnceAfterItsSecondDotNotAfterTheFirst() {
        val line = s(10, 50, 90, 50)
        assertFalse(finished(listOf(line)))
        assertFalse(finished(listOf(line, s(50, 16))))
        assertEquals("÷", best(listOf(line, s(50, 16), s(50, 84))))
        assertTrue(finished(listOf(line, s(50, 16), s(50, 84))))
    }

    @Test fun lessOrEqualAndInfinityAreWrittenAtOnceTheirBeginningsNot() {
        val less = s(85, 6, 15, 36, 85, 66)
        val bar = s(15, 92, 85, 92)
        assertEquals("≤", best(listOf(less, bar)))
        assertTrue(finished(listOf(less, bar)))
        assertTrue(finished(listOf(bar, less)))
        assertFalse(finished(listOf(less)))
        assertFalse(finished(listOf(bar)))
        val infinity = InkStroke(FloatArray(74) { k -> val t = 2 * Math.PI * (k / 2) / 36
            (if (k % 2 == 0) 350 + 138 * Math.sin(t) else 350 + 66 * Math.sin(2 * t)).toFloat() })
        assertEquals("∞", best(listOf(infinity)))
        assertTrue(finished(listOf(infinity)))
    }

    @Test fun confusableDrawingsWaitOrAreNotRecognized() {
        assertEquals("/", best(listOf(slash)))
        assertEquals("–", best(listOf(s(0, 100, 100, 100)))) // a low line ("_"): there is no "_", it is a dash and waits
        assertFalse(finished(listOf(s(0, 100, 100, 100))))
        val ring = InkStroke(FloatArray(74) { k -> val a = 2 * Math.PI * (k / 2) / 36; (if (k % 2 == 0) 350 + 100 * Math.cos(a) else 350 + 100 * Math.sin(a)).toFloat() })
        val tall = InkStroke(FloatArray(74) { k -> val a = 2 * Math.PI * (k / 2) / 36; (if (k % 2 == 0) 350 + 60 * Math.cos(a) else 350 + 110 * Math.sin(a)).toFloat() })
        assertEquals("°", best(listOf(ring))) // "o" and "0" are a degree sign, which may become "%": it waits
        assertEquals("°", best(listOf(tall)))
        assertFalse(finished(listOf(ring)))
        // ">" alone: there is no ">"; drawn like this it is taken for ")" (the head of "⇒" is a part of it), so it waits
        // for the dash of "→" and is written as ")" only after the pause, never "→" (docs/SYMBOLE-TEMPO-KONZEPT.md, 5)
        assertTrue(best(listOf(head)) != "→")
        assertFalse(finished(listOf(head)))
    }

    @Test fun anOwnSymbolMakesItsBeginningsWait() {
        val infinity = InkStroke(FloatArray(74) { k -> val t = 2 * Math.PI * (k / 2) / 36
            (if (k % 2 == 0) 350 + 138 * Math.sin(t) else 350 + 66 * Math.sin(2 * t)).toFloat() })
        val under = InkStroke(floatArrayOf(212f, 450f, 488f, 450f))
        val own = OwnInkSymbols.Symbol("∞̲", List(3) { InkCloud.of(listOf(infinity, under))!! }).templates()
        val all = own + InkSymbols.templates
        val withOwn = InkCompletion(all)
        val matcher = InkRecognizer(all)
        assertTrue(finished(listOf(infinity)))
        assertTrue(withOwn.extendable("∞"))
        assertFalse(finished(listOf(infinity), withOwn, matcher)) // the own symbol may follow
        assertEquals("∞̲", matcher.recognize(listOf(infinity, under)).best?.text)
        assertTrue(finished(listOf(infinity, under), withOwn, matcher))
    }

    @Test fun whichBuiltInSymbolsCanGoOn() {
        val cannot = InkSymbols.all.map { it.text }.filter { !completion.extendable(it) }
        assertEquals(listOf("≤", "≥", "±", "×", "÷", "∞", "√", "♥", "★", "&", "#", "‰", "?", "¿", "{", "}"), cannot)
    }

    @Test fun partsAreEveryProperSubsetOfAFewStrokesAndPrefixesOfMany() {
        assertEquals(10, InkCompletion.subsets(5, 2).size)
        assertEquals(listOf(listOf(0, 1), listOf(0, 2), listOf(1, 2)), InkCompletion.subsets(3, 2).map { it.toList() })
        assertEquals(listOf(listOf(0, 1, 2, 3, 4, 5)), InkCompletion.subsets(12, 6).map { it.toList() }) // 924 > MAX_SUBSETS
        val cloud = InkCloud.of(listOf(dash, head, s(50, 0)))!!
        assertEquals(listOf(true, true, false), InkCompletion.strokesOf(cloud).map { it.length > 0f })
        assertNotNull(completion.nearestPart(InkCloud.of(listOf(dash))!!, InkCompletion.PART_DISTANCE))
    }

    @Test fun theCheckIsFastEnoughForEveryStroke() {
        val r = Random(3)
        InkCompletion(InkSymbols.templates).let { fresh ->
            val started = System.nanoTime()
            InkSymbols.all.forEach { fresh.extendable(it.text) }
            val millis = (System.nanoTime() - started) / 1e6
            println("stage 100: parts and every symbol that can go on in $millis ms (JVM; each symbol is worked out when first asked)")
            assertTrue(millis < 5_000, "$millis ms")
        }
        InkSymbols.all.forEach { completion.extendable(it.text) }
        val times = ArrayList<Double>()
        for (text in InkFingerModel.symbols) repeat(10) {
            val cloud = InkCloud.of(InkFingerModel.draw(text, r))!!
            val result = recognizer.recognize(cloud)
            val started = System.nanoTime()
            completion.finished(cloud, result)
            times += (System.nanoTime() - started) / 1e6
        }
        times.sort()
        println("stage 100: finished() median ${times[times.size / 2]} ms, 90 % ${times[times.size * 9 / 10]} ms (JVM)")
        assertTrue(times[times.size / 2] < 20.0, "${times[times.size / 2]} ms")
    }
}
