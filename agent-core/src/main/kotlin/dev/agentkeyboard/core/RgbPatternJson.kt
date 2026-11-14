// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import java.util.Locale
import kotlin.math.roundToInt

/** Why a pattern file was not taken (stage 34). */
enum class RgbPatternError {
    /** More than [RgbPatternJson.MAX_FILE_BYTES]. */
    TOO_LARGE,
    /** Not strict JSON (comments, trailing commas, duplicate keys, too deep or too long). */
    NOT_JSON,
    /** JSON, but not a light pattern of this app. */
    WRONG_FORMAT,
    /** Made by a newer app version. */
    NEWER_VERSION,
    /** A field this version does not know: refused rather than silently dropped. */
    UNKNOWN_FIELD,
    /** A value of the wrong type, an unknown shape or blend, a broken colour or curve. */
    BAD_VALUE,
    NO_LAYERS,
    TOO_MANY_LAYERS,
    /** Empty, too long, or with control characters, markup or an address in it. */
    BAD_NAME,
}

/** Result of reading a pattern file: the pattern or the reason. */
sealed interface RgbPatternRead {
    data class Ok(val pattern: RgbOwnPattern) : RgbPatternRead
    data class Failed(val error: RgbPatternError) : RgbPatternRead
}

/**
 * Own light patterns as small JSON (stage 34): one pattern as an export file, the whole library as
 * the stored setting. The schema is strict and versioned ([VERSION]): only the fields below, every
 * shape, blend, reaction and palette mode from a fixed list, colours as `#RRGGBB`, the curve in the
 * text form of [RgbCurve]; numbers are clamped and rounded ([RgbPatternLayer.normalized]). A file is
 * only data: nothing in it can name code, an address, a tool, a provider or a permission.
 */
object RgbPatternJson {
    const val VERSION = 1
    const val FORMAT = "agent-keyboard-rgb-pattern"
    const val LIBRARY_FORMAT = "agent-keyboard-rgb-patterns"
    /** One exported pattern: four layers with eight stops each take about 3 KiB. */
    const val MAX_FILE_BYTES = 16 * 1024
    /** The stored library ([RgbPatternLibrary.MAX_PATTERNS] patterns). */
    const val MAX_LIBRARY_BYTES = 128 * 1024

    private val PATTERN_FIELDS = setOf("format", "version", "name", "layers")
    private val LIBRARY_FIELDS = setOf("format", "version", "patterns")
    private val ENTRY_FIELDS = setOf("id", "name", "layers")
    private val LAYER_FIELDS = setOf("generator", "blend", "speed", "angle", "scale", "offsetX", "offsetY", "opacity", "curve",
        "reaction", "palette", "stops")
    private val STOP_FIELDS = setOf("at", "color")

    // --- writing ---

    /** One pattern as an export file (no id: an import always gets a new one). */
    fun encode(pattern: RgbOwnPattern): String {
        val p = pattern.normalized()
        return "{\"format\":\"$FORMAT\",\"version\":$VERSION,\"name\":${quote(p.name)},\"layers\":${layers(p.layers)}}"
    }

    fun encodeLibrary(library: RgbPatternLibrary): String =
        "{\"format\":\"$LIBRARY_FORMAT\",\"version\":$VERSION,\"patterns\":[" + library.patterns.joinToString(",") { pattern ->
            val p = pattern.normalized()
            "{\"id\":${quote(p.id)},\"name\":${quote(p.name)},\"layers\":${layers(p.layers)}}"
        } + "]}"

    /**
     * The content check of [layers] for the settings text (`own=<id>.<check>`, stage 34): 64-bit
     * FNV-1a over the layers as stored, the low [RgbOwnPattern.CHECK_LENGTH] hex digits. The name
     * is not part of it, so a renamed pattern keeps its references.
     */
    fun fingerprint(layers: List<RgbPatternLayer>): String {
        var h = -0x340d631b7bdddcdbL // FNV offset basis 0xcbf29ce484222325
        for (c in layers(layers.map { it.normalized() })) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return java.lang.Long.toHexString(h).padStart(16, '0').takeLast(RgbOwnPattern.CHECK_LENGTH)
    }

    private fun layers(list: List<RgbPatternLayer>) = list.joinToString(",", "[", "]") { l ->
        "{\"generator\":\"${l.generator}\",\"blend\":\"${l.blend}\",\"speed\":${num(l.speed)},\"angle\":${l.angle}," +
            "\"scale\":${num(l.scale)},\"offsetX\":${num(l.offsetX)},\"offsetY\":${num(l.offsetY)},\"opacity\":${l.opacity}," +
            "\"curve\":\"${l.curve.encode()}\",\"reaction\":\"${l.reaction}\",\"palette\":\"${l.palette}\",\"stops\":" +
            l.stops.joinToString(",", "[", "]") { s -> "{\"at\":${num(s.position)},\"color\":\"#${RgbSettings.hex(s.color)}\"}" } + "}"
    }

    private fun num(v: Float): String {
        val i = (v * 1000f).roundToInt()
        if (i % 1000 == 0) return (i / 1000).toString()
        return String.format(Locale.ROOT, "%.3f", i / 1000f).trimEnd('0')
    }

    private fun quote(text: String): String {
        val b = StringBuilder("\"")
        for (c in text) when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c < ' ' -> b.append(String.format(Locale.ROOT, "\\u%04x", c.code))
            else -> b.append(c)
        }
        return b.append('"').toString()
    }

    // --- reading ---

    /** Reads an export file strictly; any doubt refuses the whole file with a reason. */
    fun decode(text: String): RgbPatternRead {
        if (text.length > MAX_FILE_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) return RgbPatternRead.Failed(RgbPatternError.TOO_LARGE)
        val root = try { StrictJson.parse(text) } catch (_: StrictJson.Invalid) { return RgbPatternRead.Failed(RgbPatternError.NOT_JSON) }
        if (root !is Map<*, *> || root["format"] != FORMAT) return RgbPatternRead.Failed(RgbPatternError.WRONG_FORMAT)
        return try {
            version(root["version"])
            fields(root, PATTERN_FIELDS)
            val name = RgbOwnPattern.cleanName(root["name"] as? String ?: throw Refused(RgbPatternError.BAD_NAME))
                ?: throw Refused(RgbPatternError.BAD_NAME)
            RgbPatternRead.Ok(RgbOwnPattern("", name, readLayers(root["layers"])).normalized())
        } catch (e: Refused) {
            RgbPatternRead.Failed(e.error)
        }
    }

    /** Reads the stored library as [readLibrary] does; a text that cannot be read as a whole gives an empty library. */
    fun decodeLibrary(text: String?): RgbPatternLibrary = readLibrary(text) ?: RgbPatternLibrary.EMPTY

    /**
     * Reads the stored library; a broken entry is skipped (the others stay). Null (not empty) for
     * a text that cannot be read as a whole: too long, not strict JSON, foreign, from a newer
     * version. The app then keeps that text and refuses to write over it (N6 of the review).
     * Ids must be unique and valid, at most [RgbPatternLibrary.MAX_PATTERNS]. No text: empty.
     */
    fun readLibrary(text: String?): RgbPatternLibrary? {
        if (text.isNullOrBlank()) return RgbPatternLibrary.EMPTY
        if (text.length > MAX_LIBRARY_BYTES) return null
        val root = try { StrictJson.parse(text) } catch (_: StrictJson.Invalid) { return null }
        if (root !is Map<*, *> || root["format"] != LIBRARY_FORMAT) return null
        try { version(root["version"]); fields(root, LIBRARY_FIELDS) } catch (_: Refused) { return null }
        val entries = root["patterns"] as? List<*> ?: return null
        val out = ArrayList<RgbOwnPattern>()
        for (entry in entries) {
            if (out.size >= RgbPatternLibrary.MAX_PATTERNS) break
            try {
                if (entry !is Map<*, *>) continue
                fields(entry, ENTRY_FIELDS)
                val id = entry["id"] as? String
                if (!RgbOwnPattern.validId(id) || out.any { it.id == id }) continue
                val name = RgbOwnPattern.cleanName(entry["name"] as? String) ?: continue
                out += RgbOwnPattern(id!!, name, readLayers(entry["layers"])).normalized()
            } catch (_: Refused) {
                continue
            }
        }
        return RgbPatternLibrary(out)
    }

    private class Refused(val error: RgbPatternError) : Exception(null, null, false, false)

    private fun version(value: Any?) {
        val v = value as? Double ?: throw Refused(RgbPatternError.WRONG_FORMAT)
        if (v != Math.floor(v) || v < 1) throw Refused(RgbPatternError.WRONG_FORMAT)
        if (v > VERSION) throw Refused(RgbPatternError.NEWER_VERSION)
    }

    private fun fields(map: Map<*, *>, allowed: Set<String>) {
        if (map.keys.any { it !in allowed }) throw Refused(RgbPatternError.UNKNOWN_FIELD)
    }

    private fun readLayers(value: Any?): List<RgbPatternLayer> {
        val list = value as? List<*> ?: throw Refused(RgbPatternError.BAD_VALUE)
        if (list.isEmpty()) throw Refused(RgbPatternError.NO_LAYERS)
        if (list.size > RgbOwnPattern.MAX_LAYERS) throw Refused(RgbPatternError.TOO_MANY_LAYERS)
        return list.map { readLayer(it) }
    }

    private fun readLayer(value: Any?): RgbPatternLayer {
        val m = value as? Map<*, *> ?: throw Refused(RgbPatternError.BAD_VALUE)
        fields(m, LAYER_FIELDS)
        val d = RgbPatternLayer()
        return RgbPatternLayer(
            generator = enum(m["generator"], d.generator),
            stops = readStops(m["stops"]),
            blend = enum(m["blend"], d.blend),
            speed = float(m["speed"], d.speed),
            angle = number(m["angle"], d.angle.toDouble()).let { Math.floorMod(it.roundToLong(), 360L).toInt() },
            scale = float(m["scale"], d.scale),
            offsetX = float(m["offsetX"], 0f),
            offsetY = float(m["offsetY"], 0f),
            opacity = number(m["opacity"], 100.0).coerceIn(0.0, 100.0).roundToInt(),
            curve = m["curve"]?.let { RgbCurve.parse(it as? String ?: throw Refused(RgbPatternError.BAD_VALUE)) ?: throw Refused(RgbPatternError.BAD_VALUE) }
                ?: d.curve,
            reaction = enum(m["reaction"], d.reaction),
            palette = enum(m["palette"], d.palette),
        ).normalized()
    }

    private fun Double.roundToLong(): Long = Math.round(this.coerceIn(-1e9, 1e9))

    /** A number clamped while still a double: 1e300 would be infinite as a float and fall back to the default instead of the limit. */
    private fun float(value: Any?, default: Float): Float = number(value, default.toDouble()).coerceIn(-FLOAT_LIMIT, FLOAT_LIMIT).toFloat()
    private const val FLOAT_LIMIT = 1e6

    private fun readStops(value: Any?): List<RgbStop> {
        val list = value as? List<*> ?: throw Refused(RgbPatternError.BAD_VALUE)
        if (list.size < RgbPatternLayer.MIN_STOPS || list.size > RgbPatternLayer.MAX_STOPS) throw Refused(RgbPatternError.BAD_VALUE)
        return list.map { s ->
            val m = s as? Map<*, *> ?: throw Refused(RgbPatternError.BAD_VALUE)
            fields(m, STOP_FIELDS)
            val at = m["at"] as? Double ?: throw Refused(RgbPatternError.BAD_VALUE)
            val color = RgbSettings.parseHex(m["color"] as? String ?: throw Refused(RgbPatternError.BAD_VALUE))
                ?: throw Refused(RgbPatternError.BAD_VALUE)
            RgbStop(at.coerceIn(0.0, 1.0).toFloat(), color)
        }
    }

    /** Missing: [default]; a number: clamped later; anything else refuses the file. */
    private fun number(value: Any?, default: Double): Double = when (value) {
        null -> default
        is Double -> value
        else -> throw Refused(RgbPatternError.BAD_VALUE)
    }

    private inline fun <reified E : Enum<E>> enum(value: Any?, default: E): E {
        if (value == null) return default
        val name = value as? String ?: throw Refused(RgbPatternError.BAD_VALUE)
        return enumValues<E>().firstOrNull { it.name == name } ?: throw Refused(RgbPatternError.BAD_VALUE)
    }
}

/**
 * A small strict JSON reader (RFC 8259 without extensions): objects, arrays, strings, numbers,
 * true, false, null. No comments, no trailing commas, no duplicate keys, no leading zeros or NaN;
 * at most [MAX_DEPTH] levels, [MAX_STRING] characters per string and [MAX_ITEMS] entries per array
 * or object, so a hostile file cannot make it work long. Objects keep their order.
 */
object StrictJson {
    class Invalid(message: String) : Exception(message, null, false, false)

    const val MAX_DEPTH = 8
    const val MAX_STRING = 512
    const val MAX_ITEMS = 256

    fun parse(text: String): Any? {
        val r = Reader(text)
        r.space()
        val value = r.value(0)
        r.space()
        if (r.i != text.length) throw Invalid("trailing text")
        return value
    }

    private class Reader(val s: String) {
        var i = 0

        fun space() { while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++ }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw Invalid("too deep")
            if (i >= s.length) throw Invalid("end")
            return when (s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> number()
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) throw Invalid("word")
            i += w.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            i++
            val map = LinkedHashMap<String, Any?>()
            space()
            if (i < s.length && s[i] == '}') { i++; return map }
            while (true) {
                space()
                if (i >= s.length || s[i] != '"') throw Invalid("key")
                val key = str()
                space()
                if (i >= s.length || s[i] != ':') throw Invalid("colon")
                i++
                space()
                if (map.containsKey(key)) throw Invalid("duplicate")
                map[key] = value(depth + 1)
                if (map.size > MAX_ITEMS) throw Invalid("items")
                space()
                if (i >= s.length) throw Invalid("end")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return map }
                    else -> throw Invalid("object")
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            i++
            val list = ArrayList<Any?>()
            space()
            if (i < s.length && s[i] == ']') { i++; return list }
            while (true) {
                space()
                list.add(value(depth + 1))
                if (list.size > MAX_ITEMS) throw Invalid("items")
                space()
                if (i >= s.length) throw Invalid("end")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return list }
                    else -> throw Invalid("array")
                }
            }
        }

        private fun str(): String {
            i++
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) throw Invalid("string end")
                val c = s[i++]
                when {
                    c == '"' -> return b.toString()
                    c == '\\' -> {
                        if (i >= s.length) throw Invalid("escape")
                        when (val e = s[i++]) {
                            '"' -> b.append('"')
                            '\\' -> b.append('\\')
                            '/' -> b.append('/')
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000C')
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            'u' -> {
                                // exactly four hex digits (toIntOrNull would also take a sign: "\\u+04a")
                                if (i + 4 > s.length || (i until i + 4).any { s[it] !in '0'..'9' && s[it].lowercaseChar() !in 'a'..'f' })
                                    throw Invalid("escape")
                                val code = s.substring(i, i + 4).toInt(16)
                                b.append(code.toChar())
                                i += 4
                            }
                            else -> throw Invalid("escape $e")
                        }
                    }
                    c < ' ' -> throw Invalid("control")
                    else -> b.append(c)
                }
                if (b.length > MAX_STRING) throw Invalid("string")
            }
        }

        private fun number(): Double {
            val start = i
            if (i < s.length && s[i] == '-') i++
            if (i >= s.length) throw Invalid("number")
            if (s[i] == '0') i++
            else if (s[i] in '1'..'9') while (i < s.length && s[i].isAsciiDigit()) i++
            else throw Invalid("number")
            if (i < s.length && s[i] == '.') {
                i++
                if (i >= s.length || !s[i].isAsciiDigit()) throw Invalid("fraction")
                while (i < s.length && s[i].isAsciiDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i >= s.length || !s[i].isAsciiDigit()) throw Invalid("exponent")
                while (i < s.length && s[i].isAsciiDigit()) i++
            }
            if (i - start > 32) throw Invalid("number length")
            val v = s.substring(start, i).toDouble()
            if (!v.isFinite()) throw Invalid("number range")
            return v
        }

        private fun Char.isAsciiDigit() = this in '0'..'9'
    }
}
