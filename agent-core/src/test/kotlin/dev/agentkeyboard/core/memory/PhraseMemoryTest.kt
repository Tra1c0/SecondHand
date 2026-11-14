// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.memory

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhraseMemoryTest {
    /** Feeds [sentence] word by word, as the commits of one sentence part arrive. */
    private fun PhraseBook.type(sentence: String, day: Long = 0) {
        val words = sentence.split(' ')
        for (i in words.indices) learn(words.subList(0, i + 1), day)
    }

    @Test fun tokensKeysAndSentenceParts() {
        assertEquals(listOf("Liebe", "Grüße,", "Max"), PhraseText.currentSentence("Danke. Liebe Grüße, Max"))
        assertEquals(emptyList(), PhraseText.currentSentence("Wirklich? "))
        assertEquals("grüße", PhraseText.key("Grüße,"))
        assertEquals("geht's", PhraseText.key("geht's"))
        assertNull(PhraseText.key("12"))
        assertNull(PhraseText.key("a@b"))
        assertNull(PhraseText.key("http://x"))
        // stops at the last non-word: nothing before a number is context
        assertEquals(listOf("uhr", "bin", "ich"), PhraseText.trailingWords(listOf("um", "8", "Uhr", "bin", "ich")))
    }

    @Test fun twoWordSequencesAlwaysLongerOnlyAfterRepetition() {
        val book = PhraseBook()
        book.type("ich melde mich sobald ich mehr weiß")
        assertEquals(1.0, book.weight("ich melde", 0))
        assertEquals(0.0, book.weight("ich melde mich", 0), "a 3-word sequence needs both parts twice")
        book.type("ich melde mich sobald ich mehr weiß")
        assertEquals(2.0, book.weight("melde mich", 0))
        assertEquals(1.0, book.weight("ich melde mich", 0))
        repeat(6) { book.type("ich melde mich sobald ich mehr weiß") }
        assertTrue(book.weight("ich melde mich sobald ich mehr weiß", 0) >= 1.0, "grown to the full 7 words")
        assertTrue(book.phrases(0).all { it.words <= PhraseBook.MAX_WORDS })
        // a run with a non-word learns nothing
        val other = PhraseBook()
        other.learn(listOf("um", "8"), 0)
        assertEquals(0, other.size)
    }

    @Test fun decayHalvesTheWeightAndReinforcementAddsOne() {
        val book = PhraseBook()
        book.learn(listOf("guten", "Morgen"), 0)
        book.learn(listOf("guten", "Morgen"), 0)
        assertEquals(2.0, book.weight("guten morgen", 0))
        assertEquals(1.0, book.weight("guten morgen", 30), 1e-9)
        assertEquals(0.5, book.weight("guten morgen", 60), 1e-9)
        book.learn(listOf("guten", "Morgen"), 30)
        assertEquals(2.0, book.weight("guten morgen", 30), 1e-9, "decayed 1 plus 1")
        // a pinned phrase keeps its weight
        assertTrue(book.pin("guten morgen", true, 30))
        assertEquals(2.0, book.weight("guten morgen", 400), 1e-9)
    }

    @Test fun evictsTheWeakestButNeverPinned() {
        val book = PhraseBook()
        repeat(5) { book.type("ich melde mich bald", 0) }
        book.pin("melde mich", true, 0)
        // thousands of different 2-word sequences seen once
        val random = Random(7)
        repeat(3000) { book.learn(listOf("w${'a' + random.nextInt(26)}x${it}", "y${it}z"), 1) }
        assertTrue(book.size <= PhraseBook.MAX_ENTRIES)
        assertTrue(book.weight("ich melde mich bald", 1) > 0, "the repeated phrase stays")
        assertTrue(book.weight("melde mich", 1) > 0, "pinned stays")
    }

    @Test fun deleteRemovesThePhraseAndEveryLongerOneWithIt() {
        val book = PhraseBook()
        repeat(6) { book.type("ich melde mich sobald ich mehr weiß") }
        val gone = book.delete("sobald ich")
        assertTrue(gone >= 3)
        assertEquals(0.0, book.weight("ich melde mich sobald ich mehr weiß", 0))
        assertTrue(book.weight("ich melde", 0) > 0, "shorter ones without it stay")
        assertNull(book.predict("Ich melde mich ", 0)?.rest)
    }

    @Test fun predictsNextWordAndTheSureRest() {
        val book = PhraseBook()
        repeat(5) { book.type("ich melde mich sobald ich mehr weiß") }
        assertNull(book.predict("Ich melde mich ", 0)?.rest, "five times: the long parts are still growing")
        repeat(5) { book.type("ich melde mich sobald ich mehr weiß") }
        assertEquals("sobald ich", book.predict("Ich melde mich ", 0)?.rest, "ten times: two words are sure")
        repeat(10) { book.type("ich melde mich sobald ich mehr weiß") }
        val p = assertNotNull(book.predict("Danke. Ich melde mich ", 0))
        assertEquals("sobald", p.next)
        assertEquals("sobald ich mehr weiß", p.rest)
        assertEquals(4, p.restWords)
        // not mid-word, not without a space
        assertNull(book.predict("Ich melde mich", 0))
        // a sentence end breaks the context
        assertNull(book.predict("ich melde. mich ", 0))
    }

    @Test fun anAmbiguousContinuationOffersNoRest() {
        val book = PhraseBook()
        repeat(5) { book.type("ich melde mich sobald ich mehr weiß") }
        repeat(5) { book.type("ich melde mich morgen wieder bei dir") }
        val p = assertNotNull(book.predict("ich melde mich ", 0))
        assertNull(p.rest, "50:50 is not sure")
        assertTrue(p.nextShare <= 0.5, "share ${p.nextShare}")
        // below the next-word share, the strip keeps its usual predictions
        if (p.nextShare < PhraseBook.NEXT_MIN_SHARE) assertNull(p.next) else assertTrue(p.next == "sobald" || p.next == "morgen")
    }

    @Test fun encodeParseRoundTripAndBrokenInput() {
        val book = PhraseBook()
        repeat(4) { book.type("Liebe Grüße, Max", 3) }
        book.pin("liebe grüße", true, 3)
        val again = PhraseBook.parse(book.encode(), 3)
        assertEquals(book.size, again.size)
        assertEquals(book.weight("liebe grüße max", 3), again.weight("liebe grüße max", 3), 0.001)
        assertTrue(again.phrases(3).first { it.key == "liebe grüße" }.pinned)
        assertEquals("Liebe Grüße, Max", again.phrases(3).first { it.key == "liebe grüße max" }.text)
        assertEquals(0, PhraseBook.parse("v2\n1;0;0;a b", 0).size)
        assertEquals(0, PhraseBook.parse("v1\nx;0;0;a b\n1;0;0;a\n1;0;0;a 1\n-1;0;0;a b", 0).size)
        assertEquals(0, PhraseBook.parse(null, 0).size)
        // a clock that went back keeps the entries
        assertEquals(book.size, PhraseBook.parse(book.encode(), 1).size)
    }

    // ---- synthetic corpus: thresholds and measured precision ----

    private class Measure { var offers = 0; var hits = 0; var chances = 0; var covered = 0
        val precision get() = if (offers == 0) 1.0 else hits.toDouble() / offers
        val coverage get() = if (chances == 0) 0.0 else covered.toDouble() / chances }

    /**
     * An owner with 12 habits (two of them with two endings) among random sentences. 1500 sentences
     * to learn from (over 60 days), 600 to measure on. At every word boundary after two words, a
     * next word counts as hit when it is the next word typed, a rest when it is exactly what follows.
     */
    private fun measure(seed: Int, restShare: Double = PhraseBook.REST_MIN_SHARE): Triple<Measure, Measure, Measure> {
        val random = Random(seed)
        val vocabulary = ("haus auto heute morgen abend gestern essen kino arbeit schule zeit lust geld spiel buch film " +
            "stadt markt wetter regen sonne urlaub zug bus termin kaffee freund freundin mama papa oma opa kind hund katze " +
            "garten küche zimmer büro chef team projekt plan idee frage antwort problem lösung woche monat jahr tag nacht " +
            "ich du er sie wir ihr bin bist ist sind war hatte habe hast hat gehe gehst geht komme kommst kommt mache machst " +
            "macht sehe siehst sieht will willst kann kannst muss musst noch schon auch nur sehr gern bald später jetzt dann " +
            "hier dort mit ohne für gegen über unter nach vor bei zu von aus in an auf und oder aber denn weil dass wenn ob").split(' ')
        val habits = listOf(
            listOf("ich melde mich sobald ich mehr weiß" to 7, "ich melde mich morgen wieder" to 3),
            listOf("kannst du mir bitte kurz helfen" to 1),
            listOf("bin gleich da" to 1),
            listOf("hab dich lieb" to 1),
            listOf("lass uns das morgen besprechen" to 1),
            listOf("ich bin in fünf minuten da" to 1),
            listOf("danke für deine schnelle antwort" to 1),
            listOf("wie war dein tag heute" to 1),
            listOf("sag mir einfach bescheid" to 6, "sag mir einfach wann" to 4),
            listOf("ich schaue es mir später an" to 1),
            listOf("passt mir gut" to 1),
            listOf("schönes wochenende euch allen" to 1),
        )
        fun zipf(): String { val r = random.nextDouble(); return vocabulary[(vocabulary.size * r * r * r).toInt().coerceAtMost(vocabulary.lastIndex)] }
        fun sentence(): Pair<List<String>, Int> {
            if (random.nextDouble() < 0.35) {
                val variants = habits[random.nextInt(habits.size)]
                val total = variants.sumOf { it.second }
                var pick = random.nextInt(total)
                val phrase = variants.first { pick -= it.second; pick < 0 }.first.split(' ')
                val before = List(if (random.nextDouble() < 0.3) 1 + random.nextInt(2) else 0) { zipf() }
                val after = List(if (random.nextDouble() < 0.3) 1 + random.nextInt(3) else 0) { zipf() }
                return (before + phrase + after) to phrase.size
            }
            return List(4 + random.nextInt(9)) { zipf() } to 0
        }
        val book = PhraseBook()
        repeat(1500) { i ->
            val (words, _) = sentence()
            for (k in words.indices) book.learn(words.subList(0, k + 1), i / 25L)
        }
        val today = 60L
        val next = Measure(); val rest = Measure(); val randomRest = Measure()
        repeat(600) {
            val (words, habitual) = sentence()
            for (k in 2 until words.size) {
                val p = book.predict(words.subList(0, k).joinToString(" ") + " ", today, restShare) ?: continue
                if (p.next != null) { next.offers++; if (p.next == words[k]) next.hits++ }
                val r = p.rest
                if (r != null) {
                    val target = if (habitual > 0) rest else randomRest
                    target.offers++
                    val actual = words.subList(k, minOf(words.size, k + p.restWords)).joinToString(" ")
                    if (actual == r) target.hits++
                }
            }
            if (habitual >= 4) {
                // a chance: a habit of at least four words, so two typed and two more to come
                rest.chances++
                val offered = (2 until words.size - 1).any { k ->
                    val p = book.predict(words.subList(0, k).joinToString(" ") + " ", today, restShare)
                    p?.rest != null && words.subList(k, minOf(words.size, k + p.restWords)).joinToString(" ") == p.rest
                }
                if (offered) rest.covered++
            }
        }
        return Triple(next, rest, randomRest)
    }

    @Test fun measuredPrecisionOnASyntheticOwner() {
        // three corpora; the numbers are in docs/VALIDATION.md (Etappe 46)
        for (seed in listOf(1, 2, 3)) {
            val (next, rest, randomRest) = measure(seed)
            println("seed $seed: next word ${next.hits}/${next.offers} = ${"%.3f".format(next.precision)}; " +
                "rest in habits ${rest.hits}/${rest.offers} = ${"%.3f".format(rest.precision)}, coverage ${rest.covered}/${rest.chances} = ${"%.3f".format(rest.coverage)}; " +
                "rest in random sentences ${randomRest.hits}/${randomRest.offers}")
            assertTrue(rest.precision >= 0.9, "rest precision ${rest.precision}")
            assertTrue(rest.coverage >= 0.5, "rest coverage ${rest.coverage}")
            assertTrue(next.precision >= 0.6, "next word precision ${next.precision}")
            val all = rest.offers + randomRest.offers
            val hits = rest.hits + randomRest.hits
            assertTrue(hits.toDouble() / all >= 0.85, "rest precision over all sentences $hits/$all")
        }
    }

    @Test fun lowerRestThresholdsWouldBeLessPrecise() {
        // the reason for the share threshold: lower ones are wrong more often (same corpora)
        val table = listOf(0.35, 0.5, 0.6, 0.7, 0.8).associateWith { share ->
            listOf(1, 2, 3).map { measure(it, restShare = share).second }.let { m ->
                val offers = m.sumOf { it.offers }; val hits = m.sumOf { it.hits }
                val chances = m.sumOf { it.chances }; val covered = m.sumOf { it.covered }
                hits.toDouble() / offers to covered.toDouble() / chances
            }
        }
        table.forEach { (share, pc) -> println("rest share >= $share: precision ${"%.3f".format(pc.first)}, coverage ${"%.3f".format(pc.second)}") }
        assertTrue(table.getValue(PhraseBook.REST_MIN_SHARE).first >= table.getValue(0.35).first)
        assertTrue(table.getValue(PhraseBook.REST_MIN_SHARE).first >= 0.95)
    }

    // ---- review N1: 2000 phrases ----

    /** A full book: 2000 distinct phrases of 2 to 8 words over a few shared starts. */
    private fun fullBook(): PhraseBook {
        val rnd = Random(46)
        val vocabulary = (0 until 300).map { "wort" + ('a' + it % 26) + ('a' + it / 26) }
        val starts = listOf("ich melde mich", "wir sehen uns", "kannst du mir", "liebe grüße an", "bis später und")
        val text = StringBuilder("v1")
        val seen = HashSet<String>()
        while (seen.size < PhraseBook.MAX_ENTRIES) {
            val start = starts[rnd.nextInt(starts.size)].split(' ').take(2 + rnd.nextInt(2))
            val phrase = (start + List(rnd.nextInt(5)) { vocabulary[rnd.nextInt(vocabulary.size)] }).take(PhraseBook.MAX_WORDS)
            if (seen.add(phrase.joinToString(" "))) text.append("\n").append(2 + rnd.nextInt(20)).append(";0;0;").append(phrase.joinToString(" "))
        }
        return PhraseBook.parse(text.toString(), 0).also { assertEquals(PhraseBook.MAX_ENTRIES, it.size) }
    }

    @Test fun predictionWithTwoThousandPhrasesIsFast() {
        val book = fullBook()
        val contexts = listOf("Danke. Ich melde mich ", "wir sehen uns ", "kannst du ", "liebe grüße an ", "nichts davon hier ")
        // the same answers as a scan over all entries would give: the index finds every continuation
        val prediction = assertNotNull(book.predict("Danke. Ich melde mich ", 0))
        assertEquals(3, prediction.contextWords)
        assertTrue(prediction.next == null || book.weight("ich melde mich " + prediction.next!!.lowercase(), 0) > 0)
        repeat(200) { contexts.forEach { book.predict(it, 0) } } // warm up
        val start = System.nanoTime()
        repeat(1000) { contexts.forEach { book.predict(it, 0) } }
        val perUpdateMs = (System.nanoTime() - start) / 1e6 / (1000 * contexts.size)
        println("predict with ${book.size} phrases: ${"%.4f".format(perUpdateMs)} ms per strip update")
        assertTrue(perUpdateMs < 2.0, "generous bound: $perUpdateMs ms")
        // writing them out stays well below a frame on a desktop JVM, and it runs off the main thread in the app
        repeat(5) { book.copy().encode() }
        val encodeStart = System.nanoTime()
        val encoded = book.copy().encode()
        val encodeMs = (System.nanoTime() - encodeStart) / 1e6
        println("copy and encode ${book.size} phrases: ${"%.2f".format(encodeMs)} ms, ${encoded.length} chars")
        assertTrue(encodeMs < 500, "generous bound: $encodeMs ms")
        assertEquals(book.size, PhraseBook.parse(encoded, 0).size)
    }

    @Test fun theIndexFollowsDeleteEvictionAndClear() {
        val book = PhraseBook()
        repeat(12) { book.type("ich melde mich sobald ich mehr weiß") }
        assertNotNull(book.predict("ich melde mich ", 0)?.rest)
        book.delete("melde mich sobald")
        val after = book.predict("ich melde mich ", 0)
        assertTrue(after == null || after.rest == null, "a deleted continuation is not predicted")
        val copy = book.copy()
        book.clear()
        assertNull(book.predict("ich melde ", 0))
        assertEquals(0, book.size)
        assertTrue(copy.size > 0, "a copy is independent")
        book.type("wir sehen uns morgen")
        book.type("wir sehen uns morgen")
        book.type("wir sehen uns morgen")
        assertEquals("uns", book.predict("wir sehen ", 0)?.next)
        // eviction keeps the index in step: every remaining entry is still found
        val full = fullBook()
        repeat(200) { full.learn(listOf("neue", "folge$it"), 1) }
        assertTrue(full.size <= PhraseBook.MAX_ENTRIES)
        repeat(6) { full.type("neue folge null weiter", 1) }
        assertEquals("weiter", full.predict("neue folge null ", 1)?.next)
    }
}
