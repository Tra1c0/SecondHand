// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.charart

import org.junit.Test
import kotlin.test.*

/** Golden outputs of the character art engine (stage 53). */
class CharArtTest {
    private val blank = TextColumns.BRAILLE_BLANK

    /** The test drawing: a circle with a line through the middle. */
    private fun circle(): SketchRaster = Sketch().apply {
        add(SketchOp.Ellipse(SketchPoint(0.1f, 0.1f), SketchPoint(0.9f, 0.9f), 0.06f))
        add(SketchOp.Line(SketchPoint(0.3f, 0.5f), SketchPoint(0.7f, 0.5f), 0.06f))
    }.rasterize()

    private fun plain(width: Int, app: TargetApp = TargetApp.GENERIC) = OutputOptions(app = app, width = width, codeBlock = false)

    @Test fun brailleCircleGolden() {
        val result = CharArt.render(PictureElement(circle()), null, output = plain(16))
        assertEquals(listOf(
            "⠀⠀⢀⣠⣶⣾⠿⠿⠿⠿⣷⣶⣄⡀",
            "⠀⣰⣿⠟⠉⠀⠀⠀⠀⠀⠀⠉⠻⣿⣆",
            "⣸⣿⠃⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠘⣿⣇",
            "⣿⡇⠀⢀⣤⣤⣤⣤⣤⣤⣤⣤⡀⠀⢸⣿",
            "⣿⡇⠀⠈⠛⠛⠛⠛⠛⠛⠛⠛⠁⠀⢸⣿",
            "⢹⣿⡄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⢠⣿⡏",
            "⠀⠹⣿⣦⣀⠀⠀⠀⠀⠀⠀⣀⣴⣿⠏",
            "⠀⠀⠈⠙⠿⢿⣶⣶⣶⣶⡿⠿⠋⠁",
        ), result.lines)
        assertEquals(16, result.columns)
        assertEquals(result.lines.joinToString("\n"), result.text) // no code block for "other apps"
        assertTrue(result.warnings.isEmpty())
        // the same input always gives the same text
        assertEquals(result.text, CharArt.render(PictureElement(circle()), null, output = plain(16)).text)
    }

    @Test fun everyStyleGolden() {
        fun art(style: ArtStyle, look: Look = Look(style = style)) =
            CharArt.render(PictureElement(circle(), look = look), null, output = plain(16)).lines
        assertEquals(listOf(
            "⠀⠀▗▄████████▄▖", "⠀▟█▛▀⠀⠀⠀⠀⠀⠀▀▜█▙", "▟█▘⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀▝█▙", "█▌⠀▗▄▄▄▄▄▄▄▄▖⠀▐█",
            "█▌⠀▝▀▀▀▀▀▀▀▀▘⠀▐█", "▜█▖⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀▗█▛", "⠀▜█▙▄⠀⠀⠀⠀⠀⠀▄▟█▛", "⠀⠀▝▀████████▀▘",
        ), art(ArtStyle.BLOCKS))
        assertEquals(listOf(
            "⠀⠀▄▄████████▄▄", "⠀███▀⠀⠀⠀⠀⠀⠀▀███", "██▀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀▀██", "██⠀▄▄▄▄▄▄▄▄▄▄⠀██",
            "██⠀▀▀▀▀▀▀▀▀▀▀⠀██", "██▄⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀▄██", "⠀███▄⠀⠀⠀⠀⠀⠀▄███", "⠀⠀▀▀████████▀▀",
        ), art(ArtStyle.HALF_BLOCKS))
        assertEquals(listOf(
            "⠀⠀++@@@@@@@@++", "⠀@@@+⠀⠀⠀⠀⠀⠀+@@@", "@@+⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀+@@", "@@⠀++++++++++⠀@@",
            "@@⠀++++++++++⠀@@", "@@+⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀+@@", "⠀@@@+⠀⠀⠀⠀⠀⠀+@@@", "⠀⠀++@@@@@@@@++",
        ), art(ArtStyle.ASCII))
        // one emoji per pixel, two columns each: 16 columns are 8 emojis
        assertEquals(listOf(
            "⬜⬛⬛⬛⬛⬛⬛⬜", "⬛⬛⬛⬜⬜⬛⬛⬛", "⬛⬛⬜⬜⬜⬜⬛⬛", "⬛⬛⬛⬛⬛⬛⬛⬛",
            "⬛⬛⬛⬛⬛⬛⬛⬛", "⬛⬛⬜⬜⬜⬜⬛⬛", "⬛⬛⬛⬜⬜⬛⬛⬛", "⬜⬛⬛⬛⬛⬛⬛⬜",
        ), art(ArtStyle.EMOJI))
        assertEquals("🤍❤️", art(ArtStyle.EMOJI, Look(style = ArtStyle.EMOJI, emoji = EmojiPixels.HEARTS))[0].take(4))
        // the user's own characters, from paper to ink; a ramp of five by default
        assertEquals("⠀⠀▒▒████████▒▒", art(ArtStyle.CUSTOM)[0])
        assertEquals("⠀⠀############", art(ArtStyle.CUSTOM, Look(style = ArtStyle.CUSTOM, customChars = " #"))[0])
        // "." is the user's paper, not a blank: the line keeps its full width
        assertEquals("..oo@@@@@@@@oo..", art(ArtStyle.CUSTOM, Look(style = ArtStyle.CUSTOM, customChars = ".o@"))[0])
    }

    @Test fun cellsMapPixelsExactly() {
        val look = Look()
        // braille dots 1 (top left), 5 (middle right), 3, 8: U+2800 + 0x01 + 0x10 + 0x04 + 0x80
        assertEquals(listOf("⢕"), CharArt.cells(Raster.parse(listOf("#.", ".#", "#.", ".#")), ArtStyle.BRAILLE, look))
        assertEquals(listOf("⠀"), CharArt.cells(Raster(2, 4), ArtStyle.BRAILLE, look)) // an empty cell is the braille blank
        assertEquals(listOf("▚▘"), CharArt.cells(Raster.parse(listOf("#.#.", ".#..")), ArtStyle.BLOCKS, look))
        assertEquals(listOf("▀▄█"), CharArt.cells(Raster.parse(listOf("#.#", ".##")), ArtStyle.HALF_BLOCKS, look))
        assertEquals(listOf("@+${CharArt.PAD}"), CharArt.cells(Raster.parse(listOf("##.", "#..")), ArtStyle.ASCII, look))
        assertEquals(listOf("⬛⬜"), CharArt.cells(Raster.parse(listOf("#.")), ArtStyle.EMOJI, look))
    }

    @Test fun widthFitting() {
        for (width in listOf(8, 12, 20, 26, 33, 60)) {
            val result = CharArt.render(PictureElement(circle()), null, output = plain(width))
            assertEquals(width, result.columns, "square picture fills $width columns")
            assertTrue(result.lines.all { TextColumns.of(it) <= width })
            // characters are about twice as tall as wide: half as many rows as columns, rounded
            assertEquals((width + 1) / 2, result.lines.size)
        }
        // out of range widths are kept to 8..60
        assertEquals(CharArt.MIN_WIDTH, CharArt.render(PictureElement(circle()), null, output = plain(2)).columns)
        assertEquals(CharArt.MAX_WIDTH, CharArt.render(PictureElement(circle()), null, output = plain(500)).columns)
        // half the width when the picture should be smaller
        assertEquals(10, CharArt.render(PictureElement(circle(), size = 0.5f), null, output = plain(20)).columns)
        // emojis are two columns: an odd width loses one column
        assertEquals(14, CharArt.render(PictureElement(circle(), look = Look(style = ArtStyle.EMOJI)), null, output = plain(15)).columns)
        // a tall picture stops at 40 rows and gets narrower instead
        val tall = Sketch().apply { add(SketchOp.Line(SketchPoint(0.5f, 0f), SketchPoint(0.5f, 1f), 0.4f)) }.rasterize()
        val result = CharArt.render(PictureElement(tall), null, output = plain(60))
        assertEquals(CharArt.MAX_PICTURE_ROWS, result.lines.size)
        assertTrue(result.columns < 60)
        // big letters wrap instead of getting wider than the line
        val big = CharArt.render(null, TextElement("Hallo", look = Look(style = ArtStyle.BLOCKS)), output = plain(26))
        assertTrue(big.columns <= 26)
        assertEquals(8, big.lines.size) // "HALL" and "O" below, each 4 rows
    }

    @Test fun bigLettersGolden() {
        val hi = CharArt.render(null, TextElement("Hi"), output = plain(26))
        assertEquals(listOf(
            "⣿⡇⠀⠀⠀⠀⣿⡇⠀⠿⢿⣿⠿⠇",
            "⣿⡇⠀⠀⠀⠀⣿⡇⠀⠀⢸⣿",
            "⣿⣷⣶⣶⣶⣶⣿⡇⠀⠀⢸⣿",
            "⣿⡇⠀⠀⠀⠀⣿⡇⠀⠀⢸⣿",
            "⣿⡇⠀⠀⠀⠀⣿⡇⠀⣤⣼⣿⣤⡄",
            "⠉⠁⠀⠀⠀⠀⠉⠁⠀⠉⠉⠉⠉⠁",
        ), hi.lines)
        val ok = CharArt.render(null, TextElement("OK", font = BigFont.MINI, size = 1, look = Look(style = ArtStyle.HALF_BLOCKS)), output = plain(26))
        assertEquals(listOf("▄▀▄⠀█⠀█", "█⠀█⠀█▀▄", "⠀▀⠀⠀▀⠀▀"), ok.lines)
        // classic FIGlet look: one character per pixel in ASCII
        val ascii = CharArt.render(null, TextElement("OK", font = BigFont.MINI, size = 1, look = Look(style = ArtStyle.CUSTOM, customChars = " #")),
            output = plain(26, TargetApp.GENERIC).copy(brailleBlanks = false))
        assertEquals(listOf(" #  # #", "# # # #", "# # ##", "# # # #", " #  # #"), ascii.lines)
        // automatic size stops at ten rows: tall ASCII letters stay at size 2
        val tall = CharArt.render(null, TextElement("OK", font = BigFont.MINI, look = Look(style = ArtStyle.ASCII)), output = plain(26))
        assertEquals(10, tall.lines.size)
        // hollow letters
        val hollow = CharArt.render(null, TextElement("I", size = 2, look = Look(style = ArtStyle.CUSTOM, customChars = " #", outline = true)),
            output = plain(26).copy(brailleBlanks = false))
        // the outline of the top bar is open where the stem starts
        assertEquals(listOf("######", "##  ##", "  ##"), hollow.lines.take(3))
    }

    @Test fun figletFonts() {
        assertEquals(listOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"), BigFont.STANDARD.render("A").bits.rows())
        assertEquals(listOf(".#.", "#.#", "###", "#.#", "#.#"), BigFont.MINI.render("a").bits.rows()) // capitals only
        assertEquals(listOf("####", ".##.", ".##.", ".##.", ".##.", ".##.", "####"), BigFont.BOLD.render("I").bits.rows())
        // letters, one pixel apart; a space is three pixels in the standard font
        assertEquals(5 + 1 + 3 + 1 + 5, BigFont.STANDARD.width("A A"))
        assertEquals(BigFont.STANDARD.width("SS"), BigFont.STANDARD.width("ß")) // ß in capitals is SS
        // the capital ẞ of the window's keys too (review of stage 53, N13), and it is no unknown character
        assertEquals(BigFont.STANDARD.render("SS").bits.rows(), BigFont.STANDARD.render("ẞ").bits.rows())
        assertFalse(BigFont.STANDARD.render("ẞ").unsupported)
        assertEquals(BigFont.STANDARD.render("E").bits.rows(), BigFont.STANDARD.render("É").bits.rows()) // accents fall back to the letter
        assertEquals(listOf(".#.#.", ".....", ".###.", "#...#", "#####", "#...#", "#...#"), BigFont.STANDARD.render("ä").bits.rows())
        assertFalse(BigFont.STANDARD.render("Hallo, Welt!").unsupported)
        val unknown = BigFont.MINI.render("a@")
        assertTrue(unknown.unsupported)
        assertEquals(BigFont.MINI.render("a?").bits.rows(), unknown.bits.rows())
        val result = CharArt.render(null, TextElement("€"), output = plain(26))
        assertTrue(ArtWarning.UNSUPPORTED_CHARACTERS in result.warnings)
    }

    @Test fun unicodeStylesGolden() {
        fun styled(style: UnicodeStyle, text: String = "Grüße 42") = style.apply(text).text
        val f = UnicodeStyle.Family.entries.associateWith { UnicodeStyle(it) }
        assertEquals("Grüße 42", styled(f.getValue(UnicodeStyle.Family.SERIF)))
        assertEquals("𝐆𝐫𝐮̈ß𝐞 𝟒𝟐", styled(UnicodeStyle(bold = true)))
        assertEquals("𝖦𝗋𝗎̈ß𝖾 𝟦𝟤", styled(f.getValue(UnicodeStyle.Family.SANS)))
        assertEquals("𝗚𝗿𝘂̈ß𝗲 𝟰𝟮", styled(UnicodeStyle(UnicodeStyle.Family.SANS, bold = true)))
        assertEquals("𝒢𝓇𝓊̈ßℯ 42", styled(f.getValue(UnicodeStyle.Family.SCRIPT)))
        assertEquals("𝓖𝓻𝓾̈ß𝓮 42", styled(UnicodeStyle(UnicodeStyle.Family.SCRIPT, bold = true)))
        assertEquals("𝔊𝔯𝔲̈ß𝔢 42", styled(f.getValue(UnicodeStyle.Family.FRAKTUR)))
        assertEquals("𝕲𝖗𝖚̈ß𝖊 42", styled(UnicodeStyle(UnicodeStyle.Family.FRAKTUR, bold = true)))
        assertEquals("𝔾𝕣𝕦̈ß𝕖 𝟜𝟚", styled(f.getValue(UnicodeStyle.Family.DOUBLE_STRUCK)))
        assertEquals("𝙶𝚛𝚞̈ß𝚎 𝟺𝟸", styled(f.getValue(UnicodeStyle.Family.MONOSPACE)))
        assertEquals("Ⓖⓡⓤ̈ßⓔ ④②", styled(f.getValue(UnicodeStyle.Family.CIRCLED)))
        assertEquals("🅖🅡🅤̈ß🅔 42", styled(UnicodeStyle(UnicodeStyle.Family.CIRCLED, bold = true)))
        assertEquals("🄶🅁🅄̈ß🄴 42", styled(f.getValue(UnicodeStyle.Family.SQUARED)))
        assertEquals("🅶🆁🆄̈ß🅴 42", styled(UnicodeStyle(UnicodeStyle.Family.SQUARED, bold = true)))
        assertEquals("Gʀᴜ̈ßᴇ 42", styled(f.getValue(UnicodeStyle.Family.SMALL_CAPS)))
        assertEquals("ᄅㄣ ǝßnɹ⅁", styled(f.getValue(UnicodeStyle.Family.UPSIDE_DOWN)))
        // combinations: bold and italic, the italic h, the script and fraktur gaps of the alphabets
        assertEquals("𝑨𝒉", styled(UnicodeStyle(bold = true, italic = true), "Ah"))
        assertEquals("𝐴ℎ", styled(UnicodeStyle(italic = true), "Ah"))
        assertEquals("𝘼𝙝", styled(UnicodeStyle(UnicodeStyle.Family.SANS, bold = true, italic = true), "Ah"))
        assertEquals("ℬℯℊℴ", styled(f.getValue(UnicodeStyle.Family.SCRIPT), "Bego"))
        assertEquals("ℭℌℑℜℨ", styled(f.getValue(UnicodeStyle.Family.FRAKTUR), "CHIRZ"))
        assertEquals("ℂℍℕℙℚℝℤ", styled(f.getValue(UnicodeStyle.Family.DOUBLE_STRUCK), "CHNPQRZ"))
        // lines through and under every character, after its own accents, combinable with any family
        assertEquals("𝐚̶𝐛̶", styled(UnicodeStyle(bold = true, strike = true), "ab"))
        assertEquals("a̲ ̲b̲", styled(UnicodeStyle(underline = true), "a b"))
        assertEquals("𝒶̶̲", styled(UnicodeStyle(UnicodeStyle.Family.SCRIPT, strike = true, underline = true), "a"))
        assertEquals("ü̶", styled(UnicodeStyle(strike = true), "ü").let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD) })
        assertEquals("a\nb", styled(UnicodeStyle(), "a\nb"))
        // what Unicode lacks is said, the closest is used
        assertFalse(UnicodeStyle(bold = true, italic = true).apply("x").approximated)
        assertTrue(UnicodeStyle(UnicodeStyle.Family.DOUBLE_STRUCK, bold = true).apply("x").approximated)
        assertTrue(UnicodeStyle(UnicodeStyle.Family.CIRCLED, italic = true).apply("x").approximated)
        assertTrue(UnicodeStyle(UnicodeStyle.Family.SQUARED, bold = true).apply("Abc").emojiLetters)
        assertFalse(UnicodeStyle(UnicodeStyle.Family.SQUARED, bold = true).apply("Xyz").emojiLetters)
        assertTrue(UnicodeStyle().isPlain)
        val result = CharArt.render(null, TextElement("Hi", TextMode.UNICODE, UnicodeStyle(UnicodeStyle.Family.MONOSPACE, bold = true)), output = plain(26))
        assertEquals(listOf("𝙷𝚒"), result.lines)
        assertTrue(ArtWarning.STYLE_APPROXIMATED in result.warnings)
    }

    @Test fun textWrapsAndKeepsItsSpaces() {
        val result = CharArt.render(null, TextElement("eins zwei drei vier", TextMode.PLAIN), layout = LayoutOptions(center = false), output = plain(10))
        assertEquals(listOf("eins zwei", "drei vier"), result.lines)
        assertEquals(listOf("abcdefgh", "ij"), CharArt.wrap("abcdefghij", 8))
        // emojis count two columns
        assertEquals(listOf("😀😀😀", "😀"), CharArt.wrap("😀😀😀😀", 6))
    }

    @Test fun blanksAsBrailleBlank() {
        val text = TextElement("ab cd", TextMode.PLAIN)
        val picture = PictureElement(circle())
        val on = CharArt.render(picture, text, output = plain(16))
        // the centred text: the padding before it is U+2800, the user's own space stays a space
        assertEquals("$blank".repeat(5) + "ab cd", on.lines.last())
        assertFalse(on.lines.any { it.startsWith(' ') })
        val off = CharArt.render(PictureElement(circle(), look = Look(style = ArtStyle.BLOCKS)), text, output = plain(16).copy(brailleBlanks = false))
        assertEquals("     ab cd", off.lines.last())
        assertTrue(off.lines.first().startsWith("  ▗▄"))
        assertTrue(ArtWarning.SPACES_COLLAPSE in off.warnings)
        assertFalse(ArtWarning.SPACES_COLLAPSE in on.warnings)
        // braille art keeps its own blank cells even without the option: spaces would shift the dots
        val braille = CharArt.render(picture, null, output = plain(16).copy(brailleBlanks = false))
        assertTrue(braille.lines[0].startsWith("$blank$blank⢀"))
        // nothing trailing: apps cut it anyway
        assertTrue(on.lines.none { it.endsWith(blank) || it.endsWith(' ') })
    }

    @Test fun framesGolden() {
        val ok = TextElement("OK", TextMode.PLAIN)
        fun frame(style: FrameStyle, padding: Int = 0) =
            CharArt.render(null, ok, frame = FrameOptions(style, padding), output = plain(26)).lines
        assertEquals(listOf("┌──┐", "│OK│", "└──┘"), frame(FrameStyle.LINE))
        assertEquals(listOf("╔══╗", "║OK║", "╚══╝"), frame(FrameStyle.DOUBLE))
        assertEquals(listOf("╭────╮", "│$blank$blank$blank$blank│", "│${blank}OK$blank│", "│$blank$blank$blank$blank│", "╰────╯"),
            frame(FrameStyle.ROUND, padding = 1))
        // an emoji frame is a whole number of emojis wide
        assertEquals(listOf("✨✨✨", "✨OK✨", "✨✨✨"), frame(FrameStyle.EMOJI))
        assertEquals(listOf("❤️❤️❤️", "❤️OK❤️", "❤️❤️❤️"),
            CharArt.render(null, ok, frame = FrameOptions(FrameStyle.EMOJI, emoji = "❤️"), output = plain(26)).lines)
        val three = CharArt.render(null, TextElement("ABC", TextMode.PLAIN), frame = FrameOptions(FrameStyle.EMOJI), output = plain(26)).lines
        assertEquals(listOf("✨✨✨✨", "✨ABC$blank✨", "✨✨✨✨"), three)
        // the frame keeps the whole art inside the width
        val wide = CharArt.render(PictureElement(circle()), null, frame = FrameOptions(FrameStyle.LINE, 1), output = plain(20))
        assertEquals(20, wide.columns)
        assertTrue(wide.lines.all { TextColumns.of(it) == 20 })
    }

    @Test fun layoutCombinesElements() {
        val picture = PictureElement(circle())
        val text = TextElement("Hi", TextMode.PLAIN)
        val below = CharArt.render(picture, text, output = plain(8))
        assertEquals("${blank}${blank}${blank}Hi", below.lines.last())
        val above = CharArt.render(picture, text, layout = LayoutOptions(textFirst = true, spacing = 1), output = plain(8))
        assertEquals("${blank}${blank}${blank}Hi", above.lines.first())
        assertEquals("$blank", above.lines[1]) // the empty line between them
        val left = CharArt.render(picture, text, layout = LayoutOptions(center = false), output = plain(8))
        assertEquals("Hi", left.lines.last())
        // side by side: text centred next to the picture, the gap at least one column
        val side = CharArt.render(picture, text, layout = LayoutOptions(Arrangement.SIDE_BY_SIDE), output = plain(12))
        assertEquals(5, side.lines.size) // the picture gets 12 - 1 - 2 = 9 columns, so 5 rows
        assertTrue(side.lines[2].endsWith("${blank}Hi"))
        assertEquals(9, TextColumns.of(side.lines[0]))
        assertTrue(ArtWarning.ALIGNMENT in side.warnings)
        // mirrored: the picture flips, text is read backwards with brackets turned
        val arrow = Sketch().apply { add(SketchOp.Line(SketchPoint(0f, 0f), SketchPoint(0.5f, 0f), 0.05f)); add(SketchOp.Line(SketchPoint(0f, 1f), SketchPoint(0f, 1f), 0.05f)) }
        val normal = CharArt.render(PictureElement(arrow.rasterize(), crop = false), null, output = plain(8)).lines
        val mirror = CharArt.render(PictureElement(arrow.rasterize(), crop = false), null, layout = LayoutOptions(mirror = true), output = plain(8)).lines
        assertEquals(listOf("⠉⠉⠉⠉⠁", "⠀", "⠀", "⡀"), normal)
        // each cell's dots change sides too: ⠁ (left) becomes ⠈ (right), ⡀ becomes ⢀
        assertEquals(listOf("⠀⠀⠀⠈⠉⠉⠉⠉", "⠀", "⠀", "⠀⠀⠀⠀⠀⠀⠀⢀"), mirror)
        // reversed ")ba(" with each bracket turned: "(ba)"
        assertEquals(listOf("(ba)"), CharArt.render(null, TextElement("(ab)", TextMode.PLAIN), layout = LayoutOptions(mirror = true), output = plain(8)).lines)
        // nothing to draw: an empty result
        assertTrue(CharArt.render(PictureElement(Sketch().rasterize()), TextElement(" "), output = plain(8)).isEmpty)
    }

    @Test fun targetAppsAndWarnings() {
        val picture = PictureElement(circle())
        val whatsapp = CharArt.render(picture, null, output = OutputOptions(TargetApp.WHATSAPP))
        assertEquals(26, whatsapp.columns)
        // WhatsApp: no line breaks inside the backticks
        assertTrue(whatsapp.text.startsWith("```") && !whatsapp.text.startsWith("```\n"))
        assertTrue(whatsapp.text.endsWith("```") && !whatsapp.text.endsWith("\n```"))
        assertEquals("```" + whatsapp.lines.joinToString("\n") + "```", whatsapp.text)
        assertTrue(whatsapp.fitsPhone)
        val discord = CharArt.render(picture, null, output = OutputOptions(TargetApp.DISCORD))
        assertEquals(32, discord.columns)
        assertTrue(discord.text.startsWith("```\n") && discord.text.endsWith("\n```"))
        // Signal has no code block: the wish is ignored
        val signal = CharArt.render(picture, null, output = OutputOptions(TargetApp.SIGNAL, codeBlock = true))
        assertEquals(signal.lines.joinToString("\n"), signal.text)
        assertFalse(TargetApp.TELEGRAM.codeBlockDefault)
        assertTrue(CharArt.render(picture, null, output = OutputOptions(TargetApp.TELEGRAM, codeBlock = true)).text.startsWith("```\n"))
        // too wide for a phone, too long for the app
        val wide = CharArt.render(picture, null, output = OutputOptions(TargetApp.WHATSAPP, width = 40))
        assertFalse(wide.fitsPhone)
        assertTrue(ArtWarning.WIDER_THAN_PHONE in wide.warnings)
        val long = CharArt.render(null, TextElement("a".repeat(2_000), TextMode.UNICODE), output = OutputOptions(TargetApp.DISCORD, width = 60))
        assertTrue(long.text.length > 2_000)
        assertTrue(ArtWarning.TOO_LONG in long.warnings)
        // a line frame needs a code block; emojis and art in one line differ in width
        val framed = CharArt.render(picture, null, frame = FrameOptions(FrameStyle.LINE), output = OutputOptions(TargetApp.SIGNAL))
        assertTrue(ArtWarning.ALIGNMENT in framed.warnings)
        assertFalse(ArtWarning.ALIGNMENT in CharArt.render(picture, null, frame = FrameOptions(FrameStyle.LINE), output = OutputOptions(TargetApp.WHATSAPP)).warnings)
        val emojiFrame = CharArt.render(picture, null, frame = FrameOptions(FrameStyle.EMOJI), output = OutputOptions(TargetApp.SIGNAL))
        assertTrue(ArtWarning.MIXED_WIDTHS in emojiFrame.warnings)
        // styled letters next to art in a code block are not monospace either (review of stage 53, M1:
        // styled text alone gets no code block any more, so the warning needs art around it)
        val styledInCode = CharArt.render(picture, TextElement("Hi", TextMode.UNICODE), output = OutputOptions(TargetApp.WHATSAPP))
        assertTrue(styledInCode.codeBlock)
        assertTrue(ArtWarning.CODE_BLOCK_FONT in styledInCode.warnings)
        val boldSquares = CharArt.render(null, TextElement("ab", TextMode.UNICODE, UnicodeStyle(UnicodeStyle.Family.SQUARED, bold = true)),
            output = OutputOptions(TargetApp.SIGNAL))
        assertTrue(ArtWarning.EMOJI_LETTERS in boldSquares.warnings)
        // the app of the field picks the profile
        assertEquals(TargetApp.WHATSAPP, TargetApp.forPackage("com.whatsapp"))
        assertEquals(TargetApp.TELEGRAM, TargetApp.forPackage("org.telegram.messenger"))
        assertEquals(TargetApp.DISCORD, TargetApp.forPackage("com.discord"))
        assertEquals(TargetApp.SIGNAL, TargetApp.forPackage("org.thoughtcrime.securesms"))
        assertNull(TargetApp.forPackage("com.example"))
        assertNull(TargetApp.forPackage(null))
    }

    /** Review of stage 53, M1: a code block only around art, frames and side by side, never around text styles alone. */
    @Test fun codeBlockOnlyWhereWidthsMatter() {
        val whatsapp = OutputOptions(TargetApp.WHATSAPP)
        for (mode in listOf(TextMode.UNICODE, TextMode.PLAIN)) {
            val styled = CharArt.render(null, TextElement("Hallo", mode), output = whatsapp)
            assertFalse(styled.codeBlock, "$mode")
            assertEquals(styled.lines.joinToString("\n"), styled.text, "$mode: no backticks around text alone")
            assertFalse(ArtWarning.CODE_BLOCK_FONT in styled.warnings)
        }
        assertEquals("𝐇𝐚𝐥𝐥𝐨", CharArt.render(null, TextElement("Hallo", TextMode.UNICODE), output = whatsapp).text)
        // big letters, a picture, a frame or side by side are art: they keep the code block
        val big = CharArt.render(null, TextElement("Hi"), output = whatsapp)
        assertTrue(big.codeBlock)
        assertEquals("```" + big.lines.joinToString("\n") + "```", big.text)
        assertTrue(CharArt.render(PictureElement(circle()), null, output = whatsapp).codeBlock)
        assertTrue(CharArt.render(null, TextElement("Hi", TextMode.UNICODE), frame = FrameOptions(FrameStyle.LINE), output = whatsapp).codeBlock)
        assertTrue(CharArt.render(PictureElement(circle()), TextElement("Hi", TextMode.PLAIN), layout = LayoutOptions(Arrangement.SIDE_BY_SIDE),
            output = whatsapp).codeBlock)
        // switched off, or an app without code blocks: never
        assertFalse(CharArt.render(null, TextElement("Hi"), output = whatsapp.copy(codeBlock = false)).codeBlock)
        assertFalse(CharArt.render(null, TextElement("Hi"), output = OutputOptions(TargetApp.GENERIC, codeBlock = true)).codeBlock)
    }

    /** Review of stage 53, N4: the line through a space never starts the next line. */
    @Test fun wrappingKeepsNoMarkAtALineStart() {
        fun marked(text: String, mark: Char) = text.map { "$it$mark" }.joinToString("")
        val strike = '̶'
        val struck = UnicodeStyle(strike = true).apply("eins zwei drei").text
        assertEquals(marked("eins zwei drei", strike), struck) // the space is struck through as well
        val lines = CharArt.wrap(struck, 9)
        assertEquals(listOf(marked("eins zwei", strike), marked("drei", strike)), lines)
        for (line in lines) assertNotEquals(strike, line[0], "no mark at the start of \"$line\"")
        val underline = '̲'
        val underlined = CharArt.render(null, TextElement("abc def ghi", TextMode.UNICODE, UnicodeStyle(underline = true)),
            layout = LayoutOptions(center = false), output = plain(8))
        assertEquals(listOf(marked("abc def", underline), marked("ghi", underline)), underlined.lines)
        assertTrue(underlined.lines.all { Character.getType(it[0]) != Character.NON_SPACING_MARK.toInt() })
        // plain text wraps as before; spaces at a break vanish
        assertEquals(listOf("eins zwei", "drei"), CharArt.wrap("eins zwei drei", 9))
        assertEquals(listOf("a", "b"), CharArt.wrap("a   b", 1))
    }

    /** Review of stage 53, N5: upside down keeps emojis whole (VS16, skin tones, joined emojis, flags). */
    @Test fun upsideDownKeepsEmojisWhole() {
        fun cp(vararg codePoints: Int) = codePoints.joinToString("") { String(Character.toChars(it)) }
        val heart = cp(0x2764, 0xFE0F)
        val thumb = cp(0x1F44D, 0x1F3FD) // with a skin tone
        val coder = cp(0x1F469, 0x200D, 0x1F4BB) // woman, joiner, laptop
        val flag = cp(0x1F1E9, 0x1F1EA)
        val keycap = cp(0x31, 0xFE0F, 0x20E3)
        val upside = UnicodeStyle(UnicodeStyle.Family.UPSIDE_DOWN)
        assertEquals("$heart ᴉɥ", upside.apply("hi $heart").text)
        assertEquals("${thumb}o", upside.apply("o$thumb").text)
        assertEquals("$coder$flag", upside.apply("$flag$coder").text)
        assertEquals("ɐ$keycap", upside.apply("${keycap}ä").text)
        assertEquals(listOf("ɐ", keycap), TextColumns.graphemes(upside.apply("${keycap}ä").text))
        // a line through the letters leaves the emoji as it is
        assertEquals("a̶${thumb}b̶", UnicodeStyle(strike = true).apply("a${thumb}b").text)
        assertEquals("${cp(0x1D41A)}$coder", UnicodeStyle(bold = true).apply("a$coder").text)
    }

    /** Review of stage 53, N6: a frame never makes the art wider than the width, also at odd and small widths. */
    @Test fun framesStayInsideOddAndSmallWidths() {
        val picture = PictureElement(circle())
        val text = TextElement("Hallo Welt", TextMode.PLAIN)
        for (width in CharArt.MIN_WIDTH..33) for (style in FrameStyle.entries) for (padding in 0..2) for (emoji in listOf("✨", "❤️", "#")) {
            val frame = FrameOptions(style, padding, emoji)
            for ((name, result) in listOf(
                "picture" to CharArt.render(picture, null, frame = frame, output = plain(width)),
                "text" to CharArt.render(null, text, frame = frame, output = plain(width)),
                "both" to CharArt.render(picture, text, frame = frame, output = plain(width)),
                "side" to CharArt.render(picture, text, frame = frame, layout = LayoutOptions(Arrangement.SIDE_BY_SIDE), output = plain(width)),
            )) {
                assertFalse(result.isEmpty)
                assertTrue(result.lines.all { TextColumns.of(it) <= width }, "$name, $style, padding $padding, $emoji at $width: ${result.columns}")
                // every line of a frame is equally wide
                if (style != FrameStyle.NONE) assertEquals(1, result.lines.map(TextColumns::of).distinct().size, "$name, $style at $width")
            }
        }
        // the padding gives way first: a picture 4 columns wide in 8 keeps a padding of 1, not 2
        val small = CharArt.render(picture, null, frame = FrameOptions(FrameStyle.LINE, 2), output = plain(8))
        assertEquals(8, small.columns)
        assertEquals("┌──────┐", small.lines.first())
        assertEquals("│$blank$blank$blank$blank$blank$blank│", small.lines[1])
        // short text keeps the whole padding where it fits
        val ab = CharArt.render(null, TextElement("ab", TextMode.PLAIN), frame = FrameOptions(FrameStyle.LINE, 2), output = plain(8))
        assertEquals("│$blank${blank}ab$blank$blank│", ab.lines[3])
        // an emoji frame at an odd width: one column less instead of one too many
        val odd = CharArt.render(picture, null, frame = FrameOptions(FrameStyle.EMOJI), output = plain(27))
        assertEquals(26, odd.columns)
    }

    /** Review of stage 53, N7: big letters stop at 80 lines and say so. */
    @Test fun bigLettersStopAtEightyLines() {
        val long = "Das ist ein langer Text in grossen Buchstaben ".repeat(6).trim()
        val result = CharArt.render(null, TextElement(long, size = 3, look = Look(style = ArtStyle.BLOCKS)), output = plain(26))
        assertTrue(result.lines.size <= CharArt.MAX_BIG_LINES, "${result.lines.size} lines")
        assertTrue(result.lines.size > CharArt.MAX_BIG_LINES - 12) // whole big lines up to the limit
        assertTrue(ArtWarning.TOO_LARGE in result.warnings)
        assertTrue(result.lines.all { TextColumns.of(it) <= 26 })
        val short = CharArt.render(null, TextElement("Hallo", size = 3, look = Look(style = ArtStyle.BLOCKS)), output = plain(26))
        assertFalse(ArtWarning.TOO_LARGE in short.warnings)
    }

    @Test fun columnsEstimate() {
        assertEquals(2, TextColumns.of("ab"))
        assertEquals(1, TextColumns.of("ä")) // with a combining mark
        assertEquals(1, TextColumns.of("a̶"))
        assertEquals(2, TextColumns.of("😀"))
        assertEquals(2, TextColumns.of("❤️"))
        assertEquals(2, TextColumns.of("👍🏽"))
        assertEquals(2, TextColumns.of("⬛"))
        assertEquals(1, TextColumns.of("𝐀"))
        assertEquals(1, TextColumns.of("⣿"))
        assertEquals(2, TextColumns.of("漢"))
        assertEquals(listOf("ä", "😀", "b"), TextColumns.graphemes("ä😀b"))
    }
}
