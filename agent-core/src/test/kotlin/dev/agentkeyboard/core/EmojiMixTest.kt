package dev.agentkeyboard.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.*
import org.junit.Test

class EmojiMixTest {
    /** The committed asset (tools/emoji_mix_table.py); the test runs in agent-core/. */
    private val asset = File("../app/src/main/assets/emoji_mix_pairs.bin")
    private val real by lazy { asset.inputStream().use { EmojiMixTable.read(it) } }

    /** Writes a table in the generator's format: [pairs] as (left, right, date). */
    private fun table(emojis: List<String>, pairs: List<Triple<String, String, String>>, mutate: (ByteArray) -> ByteArray = { it }): ByteArray {
        val dates = pairs.map { it.third }.distinct().sorted()
        val stride = (emojis.size + 7) / 8
        val bitmap = ByteArray(stride * emojis.size)
        val index = emojis.withIndex().associate { it.value to it.index }
        for ((l, r, _) in pairs) {
            val i = index.getValue(l); val j = index.getValue(r)
            bitmap[i * stride + j / 8] = (bitmap[i * stride + j / 8].toInt() or (1 shl (j % 8))).toByte()
        }
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).apply {
            write("EMX1".toByteArray())
            writeShort(emojis.size)
            emojis.forEach { writeByte(it.length); write(it.toByteArray()) }
            writeByte(dates.size)
            dates.forEach { write(it.toByteArray()) }
            write(bitmap)
            val ordered = pairs.sortedWith(compareBy({ index.getValue(it.first) }, { index.getValue(it.second) }))
            writeInt(ordered.size)
            ordered.forEach { writeByte(dates.indexOf(it.third)) }
        }
        val zipped = ByteArrayOutputStream()
        GZIPOutputStream(zipped).use { it.write(mutate(raw.toByteArray())) }
        return zipped.toByteArray()
    }

    private fun read(bytes: ByteArray) = EmojiMixTable.read(ByteArrayInputStream(bytes))

    @Test fun theCommittedTableHasAllPairs() {
        assertTrue(asset.isFile, "asset missing: ${asset.absolutePath}")
        assertEquals(619, real.emojiCount)
        assertEquals(147_000, real.pairCount)
        assertTrue(asset.length() < 100_000, "asset should stay small, is ${asset.length()}")
    }

    @Test fun lookupKeepsTheTablesOrderAndTriesTheReversedPair() {
        // stored as 🐶 (left) + 🐱 (right)
        val cat = real.find("🐱", "🐶")!!
        assertEquals("1f436", cat.left)
        assertEquals("1f431", cat.right)
        assertEquals(cat, real.find("🐶", "🐱"))
        assertEquals("https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f436/u1f436_u1f431.png", cat.url)
        assertEquals("🐶", cat.leftEmoji)
        assertEquals("🐱", cat.rightEmoji)
        assertEquals("https://www.gstatic.com/android/keyboard/emojikitchen/20211115/u1f436/u1f436_u1f525.png", real.find("🐶", "🔥")!!.url)
        assertEquals("https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u1f602/u1f602_u1f602.png", real.find("😂", "😂")!!.url)
        // no taco mix for the dog in the table
        assertNull(real.find("🐶", "🌮"))
        assertNull(real.find("🐶", "a"))
        assertNull(real.find("", "🐶"))
    }

    @Test fun variationSelectorsSkinTonesAndZwjSequences() {
        val heart = "https://www.gstatic.com/android/keyboard/emojikitchen/20250130/u2764-ufe0f/u2764-ufe0f_u1f602.png"
        assertEquals(heart, real.find("❤️", "😂")!!.url)
        // without FE0F, the table's code points (with FE0F) still build the URL
        assertEquals(heart, real.find("❤", "😂")!!.url)
        assertEquals(heart, real.find("😂", "❤").also { assertNotNull(it) }!!.url)
        // the table has no toned emojis: a toned thumbs up maps to the base
        assertEquals(real.indexOf("👍"), real.indexOf("👍🏽"))
        assertTrue(real.contains("👍🏽"))
        // ZWJ sequence: face exhaling
        assertEquals("https://www.gstatic.com/android/keyboard/emojikitchen/20210218/u1f62e-u200d-u1f4a8/u1f62e-u200d-u1f4a8_u1f602.png",
            real.find("😮‍💨", "😂")!!.url)
        assertEquals("1f62e-200d-1f4a8", real.codepointsOf("😮‍💨"))
    }

    @Test fun clustersSplitEmojiSequences() {
        assertEquals(listOf("a", "🐶", "🔥"), EmojiClusters.split("a🐶🔥"))
        assertEquals(listOf("❤️", "😂"), EmojiClusters.split("❤️😂"))
        assertEquals(listOf("😮‍💨", "👍🏽"), EmojiClusters.split("😮‍💨👍🏽"))
        assertEquals(listOf("🇩🇪", "🇪🇸"), EmojiClusters.split("🇩🇪🇪🇸"))
        assertEquals(listOf("1️⃣"), EmojiClusters.split("1️⃣"))
        // cut-off text: a lone low surrogate at the start is skipped
        assertEquals(listOf("🔥"), EmojiClusters.split("🐶".substring(1) + "🔥"))
        assertEquals("2764-fe0f", EmojiClusters.codepoints("❤️"))
        assertEquals("❤️", EmojiClusters.fromCodepoints("2764-fe0f"))
        assertEquals("2764", EmojiClusters.normalize("2764-fe0f"))
        assertEquals("1f44d", EmojiClusters.withoutSkinTone("1f44d-1f3fd"))
    }

    @Test fun partnersOnlyExistingOnesInOrder() {
        val partners = real.partners("🐶", EmojiMixTable.POPULAR, 6)
        assertEquals(6, partners.size)
        assertTrue(partners.all { real.find(it.leftEmoji, it.rightEmoji) == it })
        assertTrue(partners.none { it.key == "1f436_1f32e" || it.key == "1f32e_1f436" })
        assertEquals(partners.map { it.key }.distinct(), partners.map { it.key })
        assertTrue(real.partners("a", EmojiMixTable.POPULAR, 6).isEmpty())
        // every popular partner is in the table
        assertTrue(EmojiMixTable.POPULAR.all { real.contains(it) }, EmojiMixTable.POPULAR.filterNot { real.contains(it) }.toString())
    }

    @Test fun syntheticTableAndBrokenFiles() {
        val emojis = listOf("1f436", "1f525", "2764-fe0f")
        val pairs = listOf(Triple("1f525", "1f436", "20200101"), Triple("2764-fe0f", "2764-fe0f", "20210202"))
        val small = read(table(emojis, pairs))
        assertEquals(2, small.pairCount)
        assertEquals(EmojiMix("1f525", "1f436", "20200101"), small.find("🐶", "🔥"))
        assertEquals(EmojiMix("2764-fe0f", "2764-fe0f", "20210202"), small.find("❤", "❤️"))
        assertNull(small.find("🐶", "🐶"))
        assertFailsWith<IOException> { read(table(emojis, pairs) { it.copyOf(it.size - 1) }) }
        assertFailsWith<IOException> { read(table(emojis, pairs) { it + 0 }) }
        assertFailsWith<IOException> { read(table(emojis, pairs) { it.copyOf().also { b -> b[0] = 'X'.code.toByte() } }) }
        // the pair count must match the bitmap
        assertFailsWith<IOException> { read(table(emojis, pairs) { b -> b.copyOf().also { it[it.size - 3] = 9 } }) }
        assertFailsWith<IOException> { read("not gzip".toByteArray()) }
    }

    @Test fun urlsAreBuiltAndCheckedStrictly() {
        assertEquals("https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u263a-ufe0f/u263a-ufe0f_u2615.png",
            EmojiMixUrls.url("20201001", "263a-fe0f", "2615"))
        assertTrue(EmojiMixUrls.isAllowed(EmojiMixUrls.url("20201001", "263a-fe0f", "2615")))
        for (bad in listOf(
            "http://www.gstatic.com/android/keyboard/emojikitchen/20201001/u2615/u2615_u2615.png",
            "https://gstatic.com/android/keyboard/emojikitchen/20201001/u2615/u2615_u2615.png",
            "https://www.gstatic.com.evil.example/android/keyboard/emojikitchen/20201001/u2615/u2615_u2615.png",
            "https://www.gstatic.com@evil.example/android/keyboard/emojikitchen/20201001/u2615/u2615_u2615.png",
            "https://www.gstatic.com/android/keyboard/emojikitchen/20201001/u2615/u2615_u2615.png?x=1",
            "https://www.gstatic.com/other/20201001/u2615/u2615_u2615.png",
        )) assertFalse(EmojiMixUrls.isAllowed(bad), bad)
        assertFailsWith<IllegalArgumentException> { EmojiMixUrls.url("2020", "2615", "2615") }
        assertFailsWith<IllegalArgumentException> { EmojiMixUrls.url("20201001", "../x", "2615") }
    }

    @Test fun cacheDropsTheLeastRecentlyUsedFirst() {
        val dir = Files.createTempDirectory("emojimix").toFile()
        var now = 1_000_000L
        val cache = EmojiMixCache(dir, maxBytes = 250, clock = { now })
        val a = EmojiMix("1f436", "1f525", "20200101")
        val b = EmojiMix("1f436", "1f431", "20200101")
        val c = EmojiMix("1f602", "1f602", "20200101")
        cache.put(a, ByteArray(100)); now += 10_000
        cache.put(b, ByteArray(100)); now += 10_000
        // a is used again, so b is the oldest
        assertNotNull(cache.get(a)); now += 10_000
        cache.put(c, ByteArray(100))
        assertNotNull(cache.get(a))
        assertNull(cache.get(b))
        assertNotNull(cache.get(c))
        assertTrue(cache.size() <= 250)
        // a file larger than the whole cache stays (just written), the others go
        val d = EmojiMix("1f602", "1f525", "20200101")
        now += 10_000
        cache.put(d, ByteArray(300))
        assertNotNull(cache.get(d))
        assertNull(cache.get(a))
        File(dir, "x.tmp").writeBytes(ByteArray(1))
        cache.trim()
        assertFalse(File(dir, "x.tmp").exists())
        cache.clear()
        assertEquals(0, cache.size())
        dir.deleteRecursively()
    }

    @Test fun pngSignature() {
        assertTrue(PngCheck.isPng(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertFalse(PngCheck.isPng("<html>".toByteArray()))
        assertFalse(PngCheck.isPng(ByteArray(0)))
    }
}
