package dev.agentkeyboard.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.test.*
import org.junit.Test

class EmojiKeywordsTest {
    /** The committed asset (tools/emoji_keywords.py); the test runs in agent-core/. */
    private val asset = File("../app/src/main/assets/emoji_keywords.bin")
    private val real by lazy { asset.inputStream().use { EmojiKeywords.read(it) } }
    private val de = listOf("de", "en")
    private val es = listOf("es", "de", "en")

    private fun first(word: String, langs: List<String> = de, isWord: Boolean = false) =
        real.suggest(word, langs, isWord = isWord).firstOrNull()

    private fun table(vararg lines: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(lines.joinToString("\n").toByteArray()) }
        return out.toByteArray()
    }

    @Test fun loadingTakesUnderThreeSecondsOnTheBuildMachine() {
        val start = System.nanoTime()
        val table = asset.inputStream().use { EmojiKeywords.read(it) }
        val millis = (System.nanoTime() - start) / 1_000_000
        println("EmojiKeywords: loaded ${table.emojis.size} emojis in $millis ms")
        assertTrue(millis < 3000, "$millis ms") // cold JVM; it runs once, off the typing thread
    }

    @Test fun theCommittedTableHasTheThreeLanguages() {
        assertEquals(setOf("de", "en", "es"), real.languageTags)
        assertTrue(real.emojis.size > 1800, "${real.emojis.size} emojis")
    }

    @Test fun exactKeywordsStillWork() {
        assertEquals("🐈", first("Katze"))
        assertEquals("❤️", first("Herz"))
        assertEquals("🐕", first("Hund"))
        assertEquals("🇩🇪", first("Deutschland"))
        assertEquals("☕", first("Kaffee"))
    }

    @Test fun wordFormsFindTheBaseWord() {
        assertEquals(listOf("🐈", "🐱"), real.suggest("Katzen", de, isWord = true))
        assertEquals("🐕", first("Hunde", isWord = true))
        assertEquals("🚗", first("Autos", isWord = true))
        assertContains(real.suggest("Biere", de, isWord = true), "🍺")
        assertEquals("🐁", first("Mäuse", isWord = true))
        assertEquals("🏘️", first("Häuser", isWord = true)) // CLDR has the plural itself: houses
        assertEquals(setOf("🤣", "😂"), real.suggest("lachte", de, isWord = true).toSet())
        assertEquals("🐈", first("cats", listOf("en", "de"), isWord = true))
        assertEquals("🐕", first("dogs", listOf("en", "de"), isWord = true))
        assertEquals("🐈", first("gatos", es, isWord = true))
    }

    @Test fun caseUmlautsAndAccentsDoNotMatter() {
        val cheese = real.suggest("Käse", de)
        assertEquals("🧀", cheese.first())
        for (spelling in listOf("käse", "KÄSE", "Kaese", "KAESE", "Kase")) assertEquals(cheese, real.suggest(spelling, de), spelling)
        assertEquals(listOf("🐈", "🐱"), real.suggest("KATZE", de))
        assertEquals(real.suggest("Straße", de), real.suggest("Strasse", de))
        assertTrue(real.suggest("Straße", de).isNotEmpty())
        assertEquals(real.suggest("corazón", es), real.suggest("corazon", es))
        assertTrue(real.suggest("corazon", es).isNotEmpty())
    }

    @Test fun wordStartsFromFourLetters() {
        assertEquals("🐈", first("Katz"))
        assertEquals("🍕", first("Pizz"))
        assertEquals("🦋", first("Schmetterl"))
        assertTrue(real.suggest("Kat", de).none { it == "🐈" }, "three letters are no word start")
    }

    @Test fun oneTypoFromFourLetters() {
        assertEquals("🐈", first("Kaze"))   // missing letter
        assertEquals("❤️", first("hetz"))   // wrong letter
        assertEquals("☕", first("Kafee"))  // missing letter, not "Käfer"
        assertEquals("🐕", first("Hnud"))   // swapped neighbours
        assertEquals("🍕", first("Pizzza")) // extra letter
        assertTrue(real.suggest("Kazzzze", de).isEmpty(), "two edits are too many")
    }

    @Test fun realWordsGetNoWordStartsOrTypos() {
        assertTrue(real.suggest("habe", de, isWord = true).isEmpty())   // not a typo of "Hase"
        assertTrue(real.suggest("Kante", de, isWord = true).none { it == "🫖" })
        assertEquals("🐈", first("Katzen", isWord = true))              // word forms still count
    }

    @Test fun germanAndEnglishMix() {
        assertEquals("🎉", first("party"))
        assertEquals("❤️", first("love"))
        assertEquals("🐈", first("cat"))
        assertEquals("🐈", first("Katze", listOf("en", "de")))
        assertTrue(real.suggest("gato", de).none { it == "🐈" }, "Spanish only when Spanish is a typing language")
        assertEquals("🐈", first("gato", es))
    }

    @Test fun synonyms() {
        assertEquals("🔥", first("geil", isWord = true))
        assertEquals("❤️", first("ilu"))
        assertEquals("🎉", first("Glückwünsche", isWord = true))
        assertEquals("🎮", first("zocken", isWord = true))
        assertEquals("🌱", first("Vegan", isWord = true))
        assertEquals("🤘", first("Metal", isWord = true))
        for (emoji in EmojiSynonyms.emojis) assertContains(real.emojis, emoji, "synonym emoji $emoji is in the palette")
    }

    @Test fun functionWordsAndVeryGeneralWordsGetNothing() {
        for (word in listOf("und", "die", "Die", "ich", "the", "el", "mit", "kann", "habe", "ist"))
            assertTrue(real.suggest(word, es, isWord = true).isEmpty(), word)
        for (word in listOf("Gesicht", "essen", "Tier", "Smiley"))
            assertTrue(real.suggest(word, de, isWord = true).isEmpty(), word)
        // "schön" folds to "schon" (a function word), but is checked unfolded
        assertTrue(real.suggest("schön", de, isWord = true).isNotEmpty())
    }

    @Test fun commonWordsAndWordsInsideLongerKeywordsGetNothing() {
        // review of stage 35: "zu viel gegessen", "sauber machen", "Säge" made these hit
        for (word in listOf("gute", "alles", "viel", "macht", "sagt", "kommt", "mehr", "sehr"))
            assertTrue(real.suggest(word, de, isWord = true).isEmpty(), word)
        assertEquals("🤗", first("Umarmungen", isWord = true)) // "-en" off, not "-ungen"
    }

    @Test fun aGermanWordIsNotReadAsEnglish() {
        // dictionary words ask only the main language: no price tag, picture frame, present or dinosaur
        val english = mapOf("Tag" to "🏷️", "Art" to "🖼️", "Gift" to "🎁", "Ding" to "🦕")
        for ((word, emoji) in english) assertTrue(emoji !in real.suggest(word, de, isWord = true), word)
        assertEquals("🏷️", first("Tag", listOf("en", "de"), isWord = true)) // the same word typed in English
        assertEquals("🔥", first("geil", listOf("en", "de"), isWord = true)) // synonyms in any case
        assertTrue(real.suggest("Katze", listOf("fr", "de", "en"), isWord = true).isEmpty(), "no table for the main language")
    }

    @Test fun theNamedEmojiOrAPopularNamesakeComesFirst() {
        assertEquals("❤️", first("Herz", isWord = true))   // "rotes Herz" before the card suit "Herz"
        assertEquals("🐈", first("Katze", isWord = true))  // named "Katze"
        assertEquals("☀️", first("Sonne", isWord = true))
        assertTrue(real.suggest("Hut", de, isWord = true).any { it in setOf("🤠", "🎩", "👒") })
    }

    @Test fun limitAndShortInput() {
        assertEquals(1, real.suggest("Katze", de, limit = 1).size)
        assertTrue(real.suggest("Katze", de, limit = 0).isEmpty())
        assertTrue(real.suggest("k", de).isEmpty())
        assertTrue(real.suggest("1234", de).isEmpty())
        assertTrue(real.suggest("", de).isEmpty())
        assertTrue(real.suggest("Katze", listOf("fr")).isEmpty())
    }

    @Test fun popularEmojisAreInThePalette() {
        for (emoji in EmojiKeywords.POPULAR) assertContains(real.emojis, emoji, "popular $emoji")
        assertEquals(EmojiKeywords.POPULAR.size, EmojiKeywords.POPULAR.toSet().size)
    }

    @Test fun readsTheFormatAndDropsUnsupportedEmojis() {
        val data = table("EKW1", "@de", "🐈\tKatze\tKatze|Tier", "🦖\tT-Rex\tDinosaurier|Katze", "@en", "🐈\tcat\tcat")
        val table = EmojiKeywords.read(ByteArrayInputStream(data)) { it != "🦖" }
        assertEquals(listOf("🐈"), table.suggest("Katze", listOf("de")))
        assertEquals(listOf("🐈"), table.suggest("cat", listOf("en")))
        assertFailsWith<IllegalArgumentException> { EmojiKeywords.read(ByteArrayInputStream(table("XXXX"))) }
        assertFailsWith<IllegalArgumentException> { EmojiKeywords.read(ByteArrayInputStream(table("EKW1", "🐈\tKatze\tKatze"))) }
    }

    @Test fun foldStemAndEditDistance() {
        assertEquals("kase", EmojiKeywords.fold("Käse!"))
        assertEquals("strasse", EmojiKeywords.fold("Straße"))
        assertEquals("corazon", EmojiKeywords.fold("Corazón"))
        assertEquals("katz", EmojiKeywords.stem("katzen"))
        assertEquals("katz", EmojiKeywords.stem("katze"))
        assertEquals("puppy", EmojiKeywords.stem("puppies"))
        assertEquals("bier", EmojiKeywords.stem("bier"))  // keeps three letters at least
        assertTrue(EmojiKeywords.withinOneEdit("kaze", "katze"))
        assertTrue(EmojiKeywords.withinOneEdit("hnud", "hund"))
        assertTrue(EmojiKeywords.withinOneEdit("hetz", "herz"))
        assertFalse(EmojiKeywords.withinOneEdit("hnd", "hund2"))
        assertFalse(EmojiKeywords.withinOneEdit("abcd", "badc"))
    }

    /**
     * Measures what typing pays per word (stage 35: typing must not get noticeably slower). One
     * lookup runs per suggestion update, i.e. per key press while composing. JVM numbers on the
     * build machine; the phone is slower, the budget leaves room for that.
     */
    @Test fun lookupIsFastEnoughForEveryKeyPress() {
        val corpus = ("Hallo wie geht es dir heute ich habe Katzen und Hunde gesehen das Wetter ist schön " +
            "Sonne Regen Kaffee Kuchen Geburtstag Glückwünsche morgen Arbeit Büro Termin vielleicht später " +
            "Kaze Kafee hetz Pizz Schmetterl Strasse Kaese party love cats dogs gatos corazon zocken geil " +
            "Bundesverfassungsgericht Donaudampfschifffahrt qwertz asdfgh xyzzy Entschuldigung").split(" ")
        val keyPresses = corpus.flatMap { word -> (1..word.length).map { word.substring(0, it) } }
        repeat(3) { keyPresses.forEach { real.suggest(it, es) } } // warm up
        val times = LongArray(keyPresses.size)
        for ((i, prefix) in keyPresses.withIndex()) {
            val start = System.nanoTime()
            real.suggest(prefix, es)
            times[i] = System.nanoTime() - start
        }
        times.sort()
        val averageMicros = times.average() / 1000
        val p99Micros = times[(times.size * 99) / 100] / 1000.0
        println("EmojiKeywords: ${keyPresses.size} key presses, average ${"%.1f".format(averageMicros)} µs, p99 ${"%.1f".format(p99Micros)} µs")
        assertTrue(averageMicros < 300, "average $averageMicros µs")
        assertTrue(p99Micros < 2000, "p99 $p99Micros µs")
    }
}
