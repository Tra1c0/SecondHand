// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

import org.junit.Test
import kotlin.test.*

/** Stage 34: own light patterns as strict, versioned, size-capped JSON. */
class RgbPatternJsonTest {
    private val full = RgbOwnPattern("p4", "Polarlicht \"Nord\"", listOf(
        RgbPatternLayer(RgbGenerator.NOISE, listOf(RgbStop(0f, 0xFF00FF80.toInt()), RgbStop(0.4f, 0xFF0080FF.toInt()), RgbStop(1f, 0xFF8000FF.toInt())),
            speed = 0.35f, angle = 30, scale = 1.4f, offsetX = 0.1f, offsetY = -0.2f, opacity = 90, curve = RgbCurve.preset(RgbWaveform.SINE)),
        RgbPatternLayer(RgbGenerator.SPARKLE, listOf(RgbStop(0f, 0xFFFFFFFF.toInt()), RgbStop(1f, 0xFFFFFF00.toInt())), blend = RgbBlend.SCREEN,
            speed = -1.25f, opacity = 60, reaction = RgbLayerReaction.PULSE, palette = RgbPaletteMode.HUE_CYCLE),
        RgbPatternLayer(RgbGenerator.SPOTLIGHT, blend = RgbBlend.ADD, reaction = RgbLayerReaction.KEY_GLOW),
        RgbPatternLayer(RgbGenerator.CONTOUR, blend = RgbBlend.MULTIPLY, curve = RgbCurve.preset(RgbWaveform.STEPS, 5)),
    )).normalized()

    private fun ok(text: String) = (RgbPatternJson.decode(text) as? RgbPatternRead.Ok)?.pattern
        ?: fail("refused: ${RgbPatternJson.decode(text)}\n$text")
    private fun refused(text: String) = (RgbPatternJson.decode(text) as? RgbPatternRead.Failed)?.error
        ?: fail("taken: $text")
    private val valid = """{"format":"agent-keyboard-rgb-pattern","version":1,"name":"Welle","layers":[{"generator":"LINEAR","stops":[{"at":0,"color":"#FF0050"},{"at":1,"color":"#00C8FF"}]}]}"""

    @Test fun aPatternSurvivesTheRoundTrip() {
        val text = RgbPatternJson.encode(full)
        val back = ok(text)
        assertEquals(full.copy(id = ""), back, "everything but the id (an import gets a new one)")
        assertFalse("\"id\"" in text)
        assertTrue(text.length < 2500, "small: ${text.length} characters")
        assertEquals(text, RgbPatternJson.encode(back), "stable")
        // the largest possible pattern still fits the file limit
        val largest = RgbOwnPattern("p1", "x".repeat(32), List(4) { RgbPatternLayer(stops = List(8) { RgbStop(it / 7f, 0xFF123456.toInt()) },
            curve = RgbCurve.preset(RgbWaveform.STEPS, 12)) }).normalized()
        assertTrue(RgbPatternJson.encode(largest).length < RgbPatternJson.MAX_FILE_BYTES)
        assertEquals(largest.layers, ok(RgbPatternJson.encode(largest)).layers)
    }

    @Test fun missingLayerFieldsGetTheirDefaultsAndNumbersAreClamped() {
        val p = ok(valid)
        assertEquals("Welle", p.name)
        val layer = p.layers.single()
        assertEquals(RgbPatternLayer(stops = layer.stops), layer, "defaults for everything not written")
        val wild = ok("""{"format":"agent-keyboard-rgb-pattern","version":1,"name":"Wild","layers":[{"generator":"RADIAL","speed":-80,"angle":725.4,
            |"scale":1e9,"offsetX":3,"offsetY":-3,"opacity":-5,"stops":[{"at":7,"color":"ff0000"},{"at":-1,"color":"#0000FF"}]}]}""".trimMargin()).layers.single()
        assertEquals(-RgbPatternLayer.MAX_SPEED, wild.speed)
        assertEquals(5, wild.angle)
        assertEquals(RgbPatternLayer.MAX_SCALE, wild.scale)
        assertEquals(1f, wild.offsetX); assertEquals(-1f, wild.offsetY)
        assertEquals(0, wild.opacity)
        assertEquals(listOf(RgbStop(0f, 0xFF0000FF.toInt()), RgbStop(1f, 0xFFFF0000.toInt())), wild.stops, "clamped and sorted")
        // review N4: a huge number is clamped while still a double (as a float it would be infinite and fall back to the default)
        val huge = ok(valid.replace("\"generator\":\"LINEAR\"", "\"generator\":\"LINEAR\",\"speed\":1e300,\"scale\":1e300,\"offsetX\":-1e300,\"offsetY\":1e300"))
            .layers.single()
        assertEquals(RgbPatternLayer.MAX_SPEED, huge.speed)
        assertEquals(RgbPatternLayer.MAX_SCALE, huge.scale)
        assertEquals(-1f, huge.offsetX); assertEquals(1f, huge.offsetY)
        val tiny = ok(valid.replace("\"generator\":\"LINEAR\"", "\"generator\":\"LINEAR\",\"speed\":-1e300,\"scale\":-1e300")).layers.single()
        assertEquals(-RgbPatternLayer.MAX_SPEED, tiny.speed)
        assertEquals(RgbPatternLayer.MIN_SCALE, tiny.scale)
    }

    /** Review N4: escapes take exactly four hex digits; names refuse separators and lone surrogates also when escaped. */
    @Test fun escapesAndNamesAreExact() {
        for (bad in listOf("\"\\u+04a\"", "\"\\u-001\"", "\"\\u12\"", "\"\\u 123\"", "\"\\u12g4\"", "\"\\u0x12\""))
            assertFailsWith<StrictJson.Invalid>(bad) { StrictJson.parse(bad) }
        assertEquals("\u00e4\u00C4", StrictJson.parse("\"\\u00e4\\u00C4\""))
        for (name in listOf("a\\u2028b", "a\\u2029b", "a\\ud800b", "\\udc00", "a\\u0085"))
            assertEquals(RgbPatternError.BAD_NAME, refused(valid.replace("\"Welle\"", "\"$name\"")), name)
        assertEquals("Welle \uD83C\uDF08", ok(valid.replace("\"Welle\"", "\"Welle \\ud83c\\udf08\"")).name, "an escaped pair is one character")
    }

    /** Review M1: the content check of the settings text depends on the layers only, and is stable. */
    @Test fun theFingerprintFollowsTheLayersOnly() {
        val check = RgbPatternJson.fingerprint(full.layers)
        assertEquals(RgbOwnPattern.CHECK_LENGTH, check.length)
        assertEquals(check, full.fingerprint())
        assertEquals(check, full.copy(id = "x", name = "Anders").fingerprint(), "id and name are not part of it")
        assertEquals(check, ok(RgbPatternJson.encode(full)).fingerprint(), "the same after export and import")
        assertNotEquals(check, full.copy(layers = full.layers.dropLast(1)).fingerprint())
        assertNotEquals(check, full.copy(layers = full.layers.map { it.copy(opacity = 89) }).fingerprint())
    }

    @Test fun anythingDoubtfulRefusesTheWholeFile() {
        assertEquals(RgbPatternError.TOO_LARGE, refused(valid.replace("\"Welle\"", "\"Welle\"" + " ".repeat(RgbPatternJson.MAX_FILE_BYTES))))
        assertEquals(RgbPatternError.TOO_LARGE, refused("[" + "\"ää\",".repeat(RgbPatternJson.MAX_FILE_BYTES / 6) + "1]"), "bytes, not characters")
        for (broken in listOf("", "{", "nope", "{\"a\":1,}", "{\"a\":1}//", "[1,2,]", "{'a':1}", "{\"a\":01}", "{\"a\":NaN}", "{\"a\":1,\"a\":2}",
            "{\"a\":\"\u0001\"}", "[[[[[[[[[[1]]]]]]]]]]", "{\"a\":\"\\x\"}", valid + "x"))
            assertEquals(RgbPatternError.NOT_JSON, refused(broken), broken)
        assertEquals(RgbPatternError.WRONG_FORMAT, refused("[]"))
        assertEquals(RgbPatternError.WRONG_FORMAT, refused(valid.replace("agent-keyboard-rgb-pattern", "agent-keyboard-profile")))
        assertEquals(RgbPatternError.WRONG_FORMAT, refused(valid.replace("\"version\":1", "\"version\":\"1\"")))
        assertEquals(RgbPatternError.WRONG_FORMAT, refused(valid.replace("\"version\":1", "\"version\":0.5")))
        assertEquals(RgbPatternError.NEWER_VERSION, refused(valid.replace("\"version\":1", "\"version\":2")))
        // unknown fields anywhere: no code, scripts, addresses, tools or providers can ride along
        assertEquals(RgbPatternError.UNKNOWN_FIELD, refused(valid.replace("\"name\"", "\"script\":\"alert(1)\",\"name\"")))
        assertEquals(RgbPatternError.UNKNOWN_FIELD, refused(valid.replace("\"generator\"", "\"url\":\"https://x.y\",\"generator\"")))
        assertEquals(RgbPatternError.UNKNOWN_FIELD, refused(valid.replace("{\"at\":0,", "{\"tool\":\"x\",\"at\":0,")))
        assertEquals(RgbPatternError.UNKNOWN_FIELD, refused(valid.replace("\"layers\"", "\"provider\":\"openai\",\"layers\"")))
        assertEquals(RgbPatternError.BAD_NAME, refused(valid.replace("\"Welle\"", "\"https://evil.example\"")))
        assertEquals(RgbPatternError.BAD_NAME, refused(valid.replace("\"Welle\"", "\"\"")))
        assertEquals(RgbPatternError.BAD_NAME, refused(valid.replace("\"Welle\"", "\"a\\nb\"")))
        assertEquals(RgbPatternError.BAD_NAME, refused(valid.replace("\"name\":\"Welle\",", "")))
        assertEquals(RgbPatternError.BAD_VALUE, refused(valid.replace("LINEAR", "SHADER")))
        assertEquals(RgbPatternError.BAD_VALUE, refused(valid.replace("LINEAR", "linear")), "names exactly")
        assertEquals(RgbPatternError.BAD_VALUE, refused(valid.replace("\"generator\":\"LINEAR\"", "\"generator\":\"LINEAR\",\"speed\":\"fast\"")))
        assertEquals(RgbPatternError.BAD_VALUE, refused(valid.replace("\"generator\":\"LINEAR\"", "\"generator\":\"LINEAR\",\"curve\":\"0:0,0.5\"")))
        assertEquals(RgbPatternError.BAD_VALUE, refused(valid.replace("#FF0050", "red")))
        assertEquals(RgbPatternError.BAD_VALUE, refused(valid.replace(",{\"at\":1,\"color\":\"#00C8FF\"}", "")), "one stop")
        val nine = (0 until 9).joinToString(",") { "{\"at\":0,\"color\":\"#000000\"}" }
        assertEquals(RgbPatternError.BAD_VALUE, refused(valid.replace(Regex("\"stops\":\\[.*?]"), "\"stops\":[$nine]")), "nine stops")
        assertEquals(RgbPatternError.NO_LAYERS, refused(valid.replace(Regex("\"layers\":\\[.*]}$"), "\"layers\":[]}")))
        val layer = valid.substringAfter("\"layers\":[").removeSuffix("]}")
        assertEquals(RgbPatternError.TOO_MANY_LAYERS, refused(valid.replace("[$layer]", "[" + List(5) { layer }.joinToString(",") + "]")))
        assertNotNull(ok(valid.replace("[$layer]", "[" + List(4) { layer }.joinToString(",") + "]")))
    }

    @Test fun theLibraryKeepsGoodEntriesAndDropsBadOnes() {
        val lib = RgbPatternLibrary(listOf(full, full.copy(id = "p7", name = "Zwei")))
        val text = RgbPatternJson.encodeLibrary(lib)
        assertEquals(lib, RgbPatternJson.decodeLibrary(text))
        assertEquals(RgbPatternLibrary.EMPTY, RgbPatternJson.decodeLibrary(null))
        assertEquals(RgbPatternLibrary.EMPTY, RgbPatternJson.decodeLibrary("kaputt"))
        assertEquals(RgbPatternLibrary.EMPTY, RgbPatternJson.decodeLibrary(text.replace("\"version\":1", "\"version\":9")))
        // a broken entry (bad id, duplicate id, unknown shape, bad name) is skipped, the rest stays
        val broken = text.replace("\"id\":\"p7\"", "\"id\":\"../7\"")
        assertEquals(listOf("p4"), RgbPatternJson.decodeLibrary(broken).patterns.map { it.id })
        val twice = text.replace("\"id\":\"p7\"", "\"id\":\"p4\"")
        assertEquals(1, RgbPatternJson.decodeLibrary(twice).patterns.size)
        val shape = text.replaceFirst("\"NOISE\"", "\"SHADER\"")
        assertEquals(listOf("p7"), RgbPatternJson.decodeLibrary(shape).patterns.map { it.id })
        assertTrue(RgbPatternJson.encodeLibrary(RgbPatternLibrary(List(RgbPatternLibrary.MAX_PATTERNS) { full.copy(id = "p$it") })).length <
            RgbPatternJson.MAX_LIBRARY_BYTES, "a full library fits")
        // review N6: a text that cannot be read as a whole is not "empty" (the app must not write over it)
        assertEquals(lib, RgbPatternJson.readLibrary(text))
        assertEquals(RgbPatternLibrary.EMPTY, RgbPatternJson.readLibrary(null))
        assertEquals(RgbPatternLibrary.EMPTY, RgbPatternJson.readLibrary(" "))
        for (unreadable in listOf(text.replace("\"version\":1", "\"version\":2"), "kaputt", "[]", text + " ".repeat(RgbPatternJson.MAX_LIBRARY_BYTES),
            text.replace("\"patterns\"", "\"more\":1,\"patterns\""), text.replace(RgbPatternJson.LIBRARY_FORMAT, "other")))
            assertNull(RgbPatternJson.readLibrary(unreadable), unreadable.take(80))
        assertEquals(listOf("p4"), RgbPatternJson.readLibrary(broken)!!.patterns.map { it.id }, "a broken entry alone is skipped")
    }

    @Test fun strictJsonReadsStandardJson() {
        val v = StrictJson.parse(""" { "a" : [1, -2.5e2, true, false, null, "\u00e4\n\"\\/"], "b": {} } """) as Map<*, *>
        assertEquals(listOf(1.0, -250.0, true, false, null, "ä\n\"\\/"), v["a"])
        assertEquals(emptyMap<String, Any?>(), v["b"])
        assertEquals(listOf("a", "b"), v.keys.toList(), "keeps the order")
        assertFailsWith<StrictJson.Invalid> { StrictJson.parse("\"" + "x".repeat(StrictJson.MAX_STRING + 1) + "\"") }
        assertFailsWith<StrictJson.Invalid> { StrictJson.parse("[" + List(StrictJson.MAX_ITEMS + 1) { "0" }.joinToString(",") + "]") }
        assertFailsWith<StrictJson.Invalid> { StrictJson.parse("1e999") }
        assertFailsWith<StrictJson.Invalid> { StrictJson.parse("-") }
        assertFailsWith<StrictJson.Invalid> { StrictJson.parse("1.") }
    }
}
