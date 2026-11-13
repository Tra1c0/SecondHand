package dev.agentkeyboard.core

import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Emoji-Mix (stage 25a): two emojis combined into one sticker image of Google's Emoji Kitchen.
 * This file only knows which pairs exist and where their image lies; nothing here touches the
 * network. The pair table comes from `tools/emoji_mix_table.py` (format there, source and licence
 * status in docs/BUILD.md).
 */

/** One existing pair: [left] and [right] are code point strings as in the table (e.g. "2764-fe0f"). */
data class EmojiMix(val left: String, val right: String, val date: String) {
    /** The emoji characters, for chips. */
    val leftEmoji: String get() = EmojiClusters.fromCodepoints(left)
    val rightEmoji: String get() = EmojiClusters.fromCodepoints(right)
    /** Stable, file-name safe key (only 0-9, a-f, "-" and "_"). */
    val key: String get() = "${left}_$right"
    val url: String get() = EmojiMixUrls.url(date, left, right)
}

object EmojiMixUrls {
    const val HOST = "www.gstatic.com"
    const val PATH = "/android/keyboard/emojikitchen/"
    private const val BASE = "https://$HOST$PATH"
    private val CODEPOINTS = Regex("[0-9a-f]{1,6}(-[0-9a-f]{1,6})*")
    private val DATE = Regex("[0-9]{8}")
    private val ALLOWED = Regex("https://www\\.gstatic\\.com/android/keyboard/emojikitchen/[0-9]{8}/u[0-9a-f-u]+/u[0-9a-f-u]+_u[0-9a-f-u]+\\.png")

    private fun part(codepoints: String) = codepoints.split('-').joinToString("-") { "u$it" }

    /** `…/<date>/u<left>/u<left>_u<right>.png`, every code point with "u", joined by "-" (checked for every pair by the generator). */
    fun url(date: String, left: String, right: String): String {
        require(DATE.matches(date) && CODEPOINTS.matches(left) && CODEPOINTS.matches(right)) { "not a table entry" }
        return "$BASE$date/${part(left)}/${part(left)}_${part(right)}.png"
    }

    /** Only HTTPS on [HOST] below [PATH], in exactly the built form: nothing else is ever requested. */
    fun isAllowed(url: String): Boolean = ALLOWED.matches(url)
}

/** Splits text into emoji-like clusters and normalises them for the table. */
object EmojiClusters {
    private const val ZWJ = 0x200D
    private const val VS15 = 0xFE0E
    private const val VS16 = 0xFE0F
    private const val KEYCAP = 0x20E3
    private val SKIN_TONES = 0x1F3FB..0x1F3FF
    private val TAGS = 0xE0020..0xE007F
    private val REGIONAL = 0x1F1E6..0x1F1FF

    /**
     * Clusters of [text]: a code point plus variation selectors, skin tones, keycap, tags, ZWJ joins
     * and the second regional indicator of a flag. A leading lone low surrogate (cut-off text) is skipped.
     */
    fun split(text: CharSequence): List<String> {
        val clusters = mutableListOf<String>()
        val current = StringBuilder()
        var previous = -1
        var regionalCount = 0
        var i = if (text.isNotEmpty() && Character.isLowSurrogate(text[0])) 1 else 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            val attaches = current.isNotEmpty() && (cp == VS15 || cp == VS16 || cp == KEYCAP || cp == ZWJ ||
                cp in SKIN_TONES || cp in TAGS || previous == ZWJ || (cp in REGIONAL && regionalCount == 1))
            if (!attaches) {
                if (current.isNotEmpty()) clusters += current.toString()
                current.setLength(0)
                regionalCount = 0
            }
            current.appendCodePoint(cp)
            if (cp in REGIONAL) regionalCount++
            previous = cp
            i += Character.charCount(cp)
        }
        if (current.isNotEmpty()) clusters += current.toString()
        return clusters
    }

    /** "2764-fe0f" for "❤️". */
    fun codepoints(emoji: String): String = emoji.codePoints().toArray().joinToString("-") { Integer.toHexString(it) }

    fun fromCodepoints(codepoints: String): String = buildString {
        codepoints.split('-').forEach { appendCodePoint(it.toInt(16)) }
    }

    /** Lookup key: code points without variation selectors (so "❤" and "❤️" are the same). */
    fun normalize(codepoints: String): String =
        codepoints.split('-').filter { it != "fe0f" && it != "fe0e" }.joinToString("-")

    /** The same without skin tones, for tables that only know the base emoji. */
    fun withoutSkinTone(codepoints: String): String =
        codepoints.split('-').filter { it.toInt(16) !in SKIN_TONES }.joinToString("-")
}

/**
 * The pair table: [emojis] code point strings, a bitmap of existing (left, right) pairs and one
 * date per pair. Immutable after [read]; lookups are safe from any thread.
 */
class EmojiMixTable private constructor(
    private val emojis: List<String>,
    private val dates: List<String>,
    private val bitmap: ByteArray,
    private val pairDates: ByteArray,
) {
    private val stride = (emojis.size + 7) / 8
    private val byKey: Map<String, Int> = HashMap<String, Int>(emojis.size * 2).apply {
        emojis.forEachIndexed { i, code -> put(EmojiClusters.normalize(code), i) }
    }
    /** Pairs before row i: the date of a pair is at rowStart[i] + rank of j in row i. */
    private val rowStart = IntArray(emojis.size + 1).also { starts ->
        for (i in emojis.indices) {
            var count = 0
            for (b in 0 until stride) count += Integer.bitCount(bitmap[i * stride + b].toInt() and 0xFF)
            starts[i + 1] = starts[i] + count
        }
    }

    val emojiCount: Int get() = emojis.size
    val pairCount: Int get() = rowStart[emojis.size]

    /** Index of the emoji [text] (one cluster) in the table, or -1: without FE0F, then without skin tone. */
    fun indexOf(text: String): Int {
        if (text.isEmpty()) return -1
        val codes = EmojiClusters.normalize(EmojiClusters.codepoints(text))
        return byKey[codes] ?: byKey[EmojiClusters.withoutSkinTone(codes)] ?: -1
    }

    fun contains(text: String) = indexOf(text) >= 0

    /** The table's code points of [text], or null. */
    fun codepointsOf(text: String): String? = indexOf(text).takeIf { it >= 0 }?.let { emojis[it] }

    private fun has(i: Int, j: Int) = bitmap[i * stride + j / 8].toInt() and (1 shl (j % 8)) != 0

    private fun mixAt(i: Int, j: Int): EmojiMix {
        var rank = 0
        val row = i * stride
        for (b in 0 until j / 8) rank += Integer.bitCount(bitmap[row + b].toInt() and 0xFF)
        rank += Integer.bitCount(bitmap[row + j / 8].toInt() and ((1 shl (j % 8)) - 1))
        val date = dates[pairDates[rowStart[i] + rank].toInt() and 0xFF]
        return EmojiMix(emojis[i], emojis[j], date)
    }

    /** The mix of the emojis [a] and [b] in the table's order, also when typed the other way round; null if none. */
    fun find(a: String, b: String): EmojiMix? {
        val i = indexOf(a)
        val j = indexOf(b)
        if (i < 0 || j < 0) return null
        return when {
            has(i, j) -> mixAt(i, j)
            has(j, i) -> mixAt(j, i)
            else -> null
        }
    }

    /** Mixes of [emoji] with the [candidates] that exist (in their order), at most [limit]; no duplicates. */
    fun partners(emoji: String, candidates: List<String>, limit: Int): List<EmojiMix> {
        if (indexOf(emoji) < 0) return emptyList()
        val seen = HashSet<String>()
        val result = mutableListOf<EmojiMix>()
        for (candidate in candidates) {
            if (result.size >= limit) break
            val mix = find(emoji, candidate) ?: continue
            if (seen.add(mix.key)) result += mix
        }
        return result
    }

    companion object {
        private val MAGIC = byteArrayOf('E'.code.toByte(), 'M'.code.toByte(), 'X'.code.toByte(), '1'.code.toByte())
        private val CODEPOINTS = Regex("[0-9a-f]{1,6}(-[0-9a-f]{1,6})*")
        /** More than the generator could ever write: a broken or foreign file fails fast. */
        private const val MAX_EMOJIS = 4096

        /** Hex code points joined by "-", each a Unicode scalar value (at most 10FFFF, no surrogate). */
        internal fun validCodepoints(codes: String): Boolean = CODEPOINTS.matches(codes) &&
            codes.split('-').all { val cp = it.toInt(16); cp <= Character.MAX_CODE_POINT && cp !in 0xD800..0xDFFF }

        /** Reads the gzip'd table; throws [IOException] for anything that is not exactly the format. */
        fun read(input: InputStream): EmojiMixTable {
            val data = DataInputStream(GZIPInputStream(input.buffered()))
            try {
                val magic = ByteArray(4).also { data.readFully(it) }
                if (!magic.contentEquals(MAGIC)) throw IOException("not an Emoji-Mix table")
                val count = data.readUnsignedShort()
                if (count == 0 || count > MAX_EMOJIS) throw IOException("bad emoji count")
                val emojis = List(count) {
                    val raw = ByteArray(data.readUnsignedByte()).also { data.readFully(it) }
                    String(raw, Charsets.US_ASCII).also { if (!validCodepoints(it)) throw IOException("bad code points") }
                }
                val dates = List(data.readUnsignedByte()) {
                    String(ByteArray(8).also { data.readFully(it) }, Charsets.US_ASCII)
                        .also { d -> if (!d.all { it in '0'..'9' }) throw IOException("bad date") }
                }
                val bitmap = ByteArray(((count + 7) / 8) * count).also { data.readFully(it) }
                val pairs = data.readInt()
                val set = bitmap.sumOf { Integer.bitCount(it.toInt() and 0xFF) }
                if (pairs != set) throw IOException("pair count does not match")
                val pairDates = ByteArray(pairs).also { data.readFully(it) }
                if (pairDates.any { (it.toInt() and 0xFF) >= dates.size }) throw IOException("bad date index")
                if (data.read() != -1) throw IOException("trailing data")
                return EmojiMixTable(emojis, dates, bitmap, pairDates)
            } catch (e: EOFException) {
                throw IOException("table cut off", e)
            }
        }

        /**
         * Partners offered for `@mix 🐶` and in the palette when a pair has no mix, in this order;
         * only those that exist for the emoji are shown.
         */
        val POPULAR: List<String> = listOf(
            "❤️", "😂", "🥰", "😍", "🔥", "😭", "🥺", "👍", "😎", "🤔", "💀", "🙏", "🐶", "🐱", "🌮", "🍕",
            "🌈", "⭐", "😊", "🤣", "😘", "🥳", "😡", "👻", "🤡", "💜", "🌸", "🥑",
        )
    }
}

/**
 * The downloaded images on the device: one file per pair in [directory], least recently used
 * first out once all files together exceed [maxBytes]. Not thread-safe by itself; the app calls it
 * from one worker thread.
 */
class EmojiMixCache(val directory: File, private val maxBytes: Long = MAX_BYTES, private val clock: () -> Long = System::currentTimeMillis) {
    fun file(mix: EmojiMix) = File(directory, "${mix.key}.png")

    /** The cached image of [mix], marked as just used; null if not cached. */
    fun get(mix: EmojiMix): File? {
        val file = file(mix)
        if (!file.isFile) return null
        file.setLastModified(clock())
        return file
    }

    /** Stores [bytes] for [mix] (written to a temporary file first) and trims the cache; returns the file. */
    fun put(mix: EmojiMix, bytes: ByteArray): File {
        directory.mkdirs()
        val target = file(mix)
        val temporary = File(directory, "${mix.key}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("could not store")
        }
        target.setLastModified(clock())
        trim(keep = target)
        return target
    }

    /** Deletes the least recently used files until the rest fits into [maxBytes]; never [keep]. */
    fun trim(keep: File? = null) {
        val files = directory.listFiles()?.filter { it.isFile } ?: return
        // leftovers of an interrupted write
        files.filter { it.name.endsWith(".tmp") }.forEach { it.delete() }
        val images = files.filter { it.name.endsWith(".png") }.sortedBy { it.lastModified() }
        var total = images.sumOf { it.length() }
        for (file in images) {
            if (total <= maxBytes) break
            if (file == keep) continue
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    fun size(): Long = directory.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}

object PngCheck {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    fun isPng(bytes: ByteArray) = bytes.size > SIGNATURE.size && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }
}
