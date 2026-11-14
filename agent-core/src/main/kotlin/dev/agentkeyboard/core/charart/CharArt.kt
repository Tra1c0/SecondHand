// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.charart

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How pixels become characters. A character covers [cellWidth]×[cellHeight] pixels and takes
 * [columns] columns (an emoji is two letters wide). Braille: 2×4 dots; blocks: 2×2 quarters;
 * half blocks: upper and lower half; ASCII: a gray ramp; emoji: one emoji per pixel; custom: the
 * user's characters from light to dark.
 */
enum class ArtStyle(val cellWidth: Int, val cellHeight: Int, val columns: Int) {
    BRAILLE(2, 4, 1), BLOCKS(2, 2, 1), HALF_BLOCKS(1, 2, 1), ASCII(1, 2, 1), EMOJI(1, 1, 2), CUSTOM(1, 2, 1);

    /** Pixels per pixel of a big letter at size [size], so the letters keep their shape in this style. */
    internal fun letterScale(size: Int): Pair<Int, Int> = when (this) {
        BLOCKS -> 2 * size to size
        ASCII, CUSTOM -> size to 2 * size
        else -> size to size
    }
}

/** Ink and paper of the emoji style. */
enum class EmojiPixels(val ink: String, val paper: String) {
    SQUARES("⬛", "⬜"), RED("🟥", "⬜"), HEARTS("❤️", "🤍"), GREEN("🟩", "⬛");
}

/** How a picture or big letters are drawn: style, invert, outline only, the custom characters. */
data class Look(
    val style: ArtStyle = ArtStyle.BRAILLE,
    val invert: Boolean = false,
    val outline: Boolean = false,
    /** For [ArtStyle.CUSTOM]: from paper to full ink; two characters draw black and white. */
    val customChars: String = DEFAULT_CUSTOM,
    val emoji: EmojiPixels = EmojiPixels.SQUARES,
) {
    companion object { const val DEFAULT_CUSTOM = " ░▒▓█" }
}

/** How a photo template becomes black and white; without [use] it is only a template to draw over. */
data class PhotoOptions(
    val use: Boolean = true,
    val contrast: Float = 1f,
    val threshold: Float = 0.5f,
    val dither: Dither = Dither.FLOYD_STEINBERG,
    val invert: Boolean = false,
)

/** A photo's darkness (0 paper, 1 black) placed at [left]..[right], [top]..[bottom] of the square drawing area. */
class Photo(val raster: Raster, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        /** [raster] as large as it fits into the square area, centred. */
        fun fitted(raster: Raster): Photo {
            if (raster.width <= 0 || raster.height <= 0) return Photo(raster, 0f, 0f, 1f, 1f)
            val aspect = raster.height.toFloat() / raster.width
            return if (aspect <= 1f) Photo(raster, 0f, (1f - aspect) / 2f, 1f, (1f + aspect) / 2f)
            else Photo(raster, (1f - 1f / aspect) / 2f, 0f, (1f + 1f / aspect) / 2f, 1f)
        }
    }
}

/** The drawing area: what was drawn, an optional photo under it, and how both are drawn. */
class PictureElement(
    val sketch: SketchRaster?,
    val photo: Photo? = null,
    val photoOptions: PhotoOptions = PhotoOptions(),
    val look: Look = Look(),
    /** Share of the available width, 0.25–1. */
    val size: Float = 1f,
    /** Only the part with content instead of the whole square area. */
    val crop: Boolean = true,
)

enum class TextMode { PLAIN, UNICODE, BIG }

/** Text lines: as they are, in a Unicode style, or as big letters drawn in [look]. */
data class TextElement(
    val text: String,
    val mode: TextMode = TextMode.BIG,
    val unicode: UnicodeStyle = UnicodeStyle(bold = true),
    val font: BigFont = BigFont.STANDARD,
    /** Size of big letters, 1–3; 0 picks the largest that fits. */
    val size: Int = 0,
    val look: Look = Look(),
)

enum class FrameStyle { NONE, LINE, DOUBLE, ROUND, EMOJI }

data class FrameOptions(val style: FrameStyle = FrameStyle.NONE, val padding: Int = 0, val emoji: String = "✨")

enum class Arrangement { STACK, SIDE_BY_SIDE }

data class LayoutOptions(
    val arrangement: Arrangement = Arrangement.STACK,
    val textFirst: Boolean = false,
    val center: Boolean = true,
    val spacing: Int = 0,
    val mirror: Boolean = false,
)

/**
 * Where the art goes. [defaultWidth] and [phoneWidth] are columns of a chat bubble on a phone in
 * portrait (estimates); [codeBlock] wraps the art so the app shows it in a monospace font
 * ([codeBlockDefault] whether that is on at first); [maxLength] is the message limit (0 = none).
 */
enum class TargetApp(
    val defaultWidth: Int, val phoneWidth: Int, val codeBlock: Pair<String, String>?, val codeBlockDefault: Boolean,
    val maxLength: Int,
) {
    WHATSAPP(26, 28, "```" to "```", true, 65_536),
    TELEGRAM(28, 30, "```\n" to "\n```", false, 4_096),
    DISCORD(32, 36, "```\n" to "\n```", true, 2_000),
    SIGNAL(26, 28, null, false, 2_000),
    GENERIC(26, 28, null, false, 0);

    companion object {
        /** The profile of the app with [packageName], or null for any other app. */
        fun forPackage(packageName: String?): TargetApp? = when (packageName) {
            "com.whatsapp", "com.whatsapp.w4b" -> WHATSAPP
            "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus", "org.thunderdog.challegram" -> TELEGRAM
            "com.discord" -> DISCORD
            "org.thoughtcrime.securesms" -> SIGNAL
            else -> null
        }
    }
}

data class OutputOptions(
    val app: TargetApp = TargetApp.WHATSAPP,
    val width: Int = app.defaultWidth,
    val codeBlock: Boolean = app.codeBlockDefault,
    /** Blanks as U+2800 (braille blank), which apps neither trim nor merge, instead of spaces. */
    val brailleBlanks: Boolean = true,
)

/** What will probably go wrong with the art in the chosen app. */
enum class ArtWarning {
    /** Wider than a chat bubble on a phone: lines will wrap. */
    WIDER_THAN_PHONE,
    /** Longer than the app's message limit. */
    TOO_LONG,
    /** Frames and side by side need equal character widths, which only a code block gives. */
    ALIGNMENT,
    /** Art characters, letters and emojis in one line have different widths outside a code block. */
    MIXED_WIDTHS,
    /** Emojis and styled letters are not monospace in a code block either. */
    CODE_BLOCK_FONT,
    /** Spaces at the start of a line or several in a row are removed by the app. */
    SPACES_COLLAPSE,
    /** Unicode has no letters for part of the chosen text style. */
    STYLE_APPROXIMATED,
    /** Some styled letters show as colourful emojis (🅰 🅱 🅾 🅿). */
    EMOJI_LETTERS,
    /** The big font has no letter for some characters; they are drawn as "?". */
    UNSUPPORTED_CHARACTERS,
    /** The big letters would take more than [CharArt.MAX_BIG_LINES] lines: the text was cut there. */
    TOO_LARGE,
}

/**
 * The finished art: its [lines], the [text] to insert (with the code block, if [codeBlock]), widths
 * and warnings.
 */
class ArtResult(
    val lines: List<String>, val text: String, val columns: Int, val warnings: Set<ArtWarning>, val fitsPhone: Boolean,
    val codeBlock: Boolean = false,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * The character art engine of stage 53: turns the elements of the window (drawing with photo,
 * text lines, frame) into text, freely combined, at the width of the target app. Pure functions
 * on the device, no network; the same input always gives the same text.
 */
object CharArt {
    const val MIN_WIDTH = 8
    const val MAX_WIDTH = 60
    const val MAX_PICTURE_ROWS = 40
    const val MAX_TEXT_LINES = 12
    /** Big letters in automatic size grow only while they take at most this many rows. */
    const val MAX_BIG_ROWS = 10
    /** Big letters never take more lines than this; the rest of the text is cut ([ArtWarning.TOO_LARGE]). */
    const val MAX_BIG_LINES = 80
    /** The inside of a frame is at least this wide; the frame's padding shrinks first. */
    private const val MIN_INNER = 4
    /** Paper that becomes U+2800 or a space at the end; the user's own spaces stay spaces. */
    internal const val PAD = ''
    private const val ASCII_RAMP = " .:-=+*#%@"
    private const val QUADRANTS = " ▘▝▀▖▌▞▛▗▚▐▜▄▙▟█"
    private const val HALVES = " ▀▄█"

    private class Block(val lines: List<String>, val width: Int)

    fun render(
        picture: PictureElement?, text: TextElement?, frame: FrameOptions = FrameOptions(),
        layout: LayoutOptions = LayoutOptions(), output: OutputOptions = OutputOptions(),
    ): ArtResult {
        val warnings = mutableSetOf<ArtWarning>()
        val width = output.width.coerceIn(MIN_WIDTH, MAX_WIDTH)
        // the frame never makes the art wider than the width (review of stage 53, N6): an emoji frame
        // keeps its inside a whole number of emojis wide, a small width takes the padding first
        // (in [framed], once the content is known)
        val unit = frameUnit(frame)
        val padding = if (frame.style == FrameStyle.NONE) 0 else frame.padding.coerceIn(0, 2)
        val border = when (frame.style) {
            FrameStyle.NONE -> 0
            FrameStyle.EMOJI -> 2 * unit + 2 * padding
            else -> 2 + 2 * padding
        }
        var inner = max(MIN_INNER, width - border)
        if (frame.style == FrameStyle.EMOJI) inner -= (inner + 2 * padding) % unit
        val hasText = text != null && text.text.isNotBlank()
        val side = layout.arrangement == Arrangement.SIDE_BY_SIDE && hasText && picture != null
        val gap = if (side) max(1, layout.spacing) else layout.spacing

        var textBlock: Block? = null
        var pictureBlock: Block? = null
        if (side) {
            textBlock = renderText(text!!, (inner - gap) / 2, layout, warnings)
            pictureBlock = renderPicture(picture!!, inner - gap - (textBlock?.width ?: 0), layout.mirror)
        } else {
            if (hasText) textBlock = renderText(text!!, inner, layout, warnings)
            if (picture != null) pictureBlock = renderPicture(picture, inner, layout.mirror)
        }
        val blocks = (if (layout.textFirst) listOf(textBlock, pictureBlock) else listOf(pictureBlock, textBlock)).filterNotNull()
        if (blocks.isEmpty()) return ArtResult(emptyList(), "", 0, emptySet(), true)

        var lines = if (side && blocks.size == 2) sideBySide(blocks, gap, layout.center) else stacked(blocks, gap, layout.center)
        val framed = frame.style != FrameStyle.NONE
        if (framed) lines = framed(lines, frame, width)
        else lines = lines.map { it.trimEnd(PAD, ' ', TextColumns.BRAILLE_BLANK) }
        lines = lines.dropWhile(::isBlank).dropLastWhile(::isBlank).map { if (it.isEmpty()) PAD.toString() else it }
        val blank = if (output.brailleBlanks) TextColumns.BRAILLE_BLANK else ' '
        lines = lines.map { it.replace(PAD, blank) }

        // a code block only where equal widths matter (review of stage 53, M1): art, frames or two
        // blocks side by side; text in Unicode styles or as typed stays as it is, a code block
        // would only show it in a monospace font with the backticks around it
        val art = pictureBlock != null || textBlock != null && text?.mode == TextMode.BIG
        val needsCode = art || framed || side && blocks.size == 2
        val wrapper = output.app.codeBlock?.takeIf { output.codeBlock && needsCode }
        val code = wrapper != null
        val body = lines.joinToString("\n")
        val finished = if (wrapper != null) wrapper.first + body + wrapper.second else body
        val columns = lines.maxOfOrNull(TextColumns::of) ?: 0
        if (columns > output.app.phoneWidth) warnings += ArtWarning.WIDER_THAN_PHONE
        if (output.app.maxLength > 0 && finished.length > output.app.maxLength) warnings += ArtWarning.TOO_LONG
        if (!code && (frame.style in setOf(FrameStyle.LINE, FrameStyle.DOUBLE, FrameStyle.ROUND) || side && blocks.size == 2))
            warnings += ArtWarning.ALIGNMENT
        val kinds = lines.map(::kinds)
        if (!code && kinds.any { it.size > 1 }) warnings += ArtWarning.MIXED_WIDTHS
        if (code && kinds.any { Kind.EMOJI in it || Kind.STYLED in it }) warnings += ArtWarning.CODE_BLOCK_FONT
        if (!code && !output.brailleBlanks && lines.any { it.startsWith(' ') || it.contains("  ") }) warnings += ArtWarning.SPACES_COLLAPSE
        return ArtResult(lines, finished, columns, warnings, columns <= output.app.phoneWidth, code)
    }

    private fun frameEmoji(frame: FrameOptions) = TextColumns.graphemes(frame.emoji).firstOrNull() ?: "✨"

    /** Columns of one piece of the frame's edge: an emoji is two. */
    private fun frameUnit(frame: FrameOptions) = if (frame.style == FrameStyle.EMOJI) max(1, TextColumns.width(frameEmoji(frame))) else 1

    private fun isBlank(line: String) = line.all { it == PAD || it == ' ' || it == TextColumns.BRAILLE_BLANK }

    private enum class Kind { ART, EMOJI, LETTER, STYLED }

    private fun kinds(line: String): Set<Kind> = TextColumns.graphemes(line).mapNotNullTo(mutableSetOf()) { grapheme ->
        val first = grapheme.codePointAt(0)
        when {
            first == PAD.code || first == ' '.code || first == TextColumns.BRAILLE_BLANK.code -> null
            TextColumns.isEmoji(grapheme) -> Kind.EMOJI
            TextColumns.isArt(first) -> Kind.ART
            TextColumns.isStyledLetter(first) -> Kind.STYLED
            else -> Kind.LETTER
        }
    }.let { found -> if (Kind.STYLED in found && Kind.LETTER in found) found - Kind.STYLED else found }

    // ---- text ----

    private fun renderText(element: TextElement, available: Int, layout: LayoutOptions, warnings: MutableSet<ArtWarning>): Block? {
        if (available < 1) return null
        val source = element.text.replace("\r", "").split('\n').take(MAX_TEXT_LINES).joinToString("\n").trimEnd()
        if (source.isBlank()) return null
        return when (element.mode) {
            TextMode.BIG -> renderBig(element.copy(text = source), available, layout, warnings)
            TextMode.PLAIN, TextMode.UNICODE -> {
                val styled = if (element.mode == TextMode.UNICODE) element.unicode.apply(source).also {
                    if (it.approximated) warnings += ArtWarning.STYLE_APPROXIMATED
                    if (it.emojiLetters) warnings += ArtWarning.EMOJI_LETTERS
                }.text else source
                val lines = styled.split('\n').flatMap { line ->
                    wrap(if (layout.mirror) mirroredText(line) else line, available)
                }.take(MAX_TEXT_LINES * 2)
                Block(lines, lines.maxOfOrNull(TextColumns::of) ?: 0)
            }
        }
    }

    /**
     * Word wrap at [columns]; a word longer than a line is cut. A space keeps its marks (the line of
     * struck through or underlined text) between two words of a line; where the line breaks, the
     * space goes with its marks, so no mark is left at the start of the next line (review of
     * stage 53, N4).
     */
    internal fun wrap(line: String, columns: Int): List<String> {
        if (TextColumns.of(line) <= columns) return listOf(line)
        val words = mutableListOf<String>()
        val gaps = mutableListOf<String>()
        var part = StringBuilder()
        for (grapheme in TextColumns.graphemes(line)) {
            if (grapheme[0] == ' ') { words += part.toString(); gaps += grapheme; part = StringBuilder() } else part.append(grapheme)
        }
        words += part.toString()
        val out = mutableListOf<String>()
        var current = StringBuilder()
        var currentWidth = 0
        for ((index, word) in words.withIndex()) {
            val gap = if (index == 0) "" else gaps[index - 1]
            val wordWidth = TextColumns.of(word)
            val needed = if (current.isEmpty()) wordWidth else currentWidth + TextColumns.of(gap) + wordWidth
            if (needed <= columns) {
                if (current.isNotEmpty()) current.append(gap)
                current.append(word); currentWidth = needed
                continue
            }
            if (current.isNotEmpty()) { out += current.toString(); current = StringBuilder(); currentWidth = 0 }
            if (wordWidth <= columns) { current.append(word); currentWidth = wordWidth; continue }
            for (grapheme in TextColumns.graphemes(word)) {
                val w = TextColumns.width(grapheme)
                if (currentWidth + w > columns && current.isNotEmpty()) { out += current.toString(); current = StringBuilder(); currentWidth = 0 }
                current.append(grapheme); currentWidth += w
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private val MIRRORED = mapOf('(' to ')', ')' to '(', '[' to ']', ']' to '[', '{' to '}', '}' to '{', '<' to '>', '>' to '<',
        '/' to '\\', '\\' to '/', '«' to '»', '»' to '«', '‹' to '›', '›' to '‹')

    private fun mirroredText(line: String): String =
        TextColumns.graphemes(line).reversed().joinToString("") { g -> if (g.length == 1) (MIRRORED[g[0]] ?: g[0]).toString() else g }

    private fun renderBig(element: TextElement, available: Int, layout: LayoutOptions, warnings: MutableSet<ArtWarning>): Block? {
        val style = element.look.style
        val sizes = if (element.size in 1..3) listOf(element.size) else listOf(3, 2, 1)
        val lines = element.text.split('\n')
        fun columnsOf(pixels: Int, kx: Int) = ceil(pixels * kx.toDouble() / style.cellWidth).toInt() * style.columns
        var chosen: List<BigFont.Line>? = null
        var scale = sizes.last()
        val rendered = lines.map { element.font.render(it) }
        for (size in sizes) {
            val (kx, ky) = style.letterScale(size)
            // the largest size that fits the width, and in auto size also no more than MAX_BIG_ROWS rows
            val rows = ceil((rendered.sumOf { it.bits.height } + rendered.size - 1) * ky.toDouble() / style.cellHeight)
            if (element.size == 0 && size > 1 && rows > MAX_BIG_ROWS) continue
            if (rendered.all { columnsOf(it.bits.width, kx) <= available }) { chosen = rendered; scale = size; break }
        }
        if (chosen == null) {
            // nothing fits in one line even at the smallest size: wrap words, then letters
            val (kx, _) = style.letterScale(scale)
            chosen = lines.flatMap { line -> wrapBig(line, element.font) { columnsOf(it, kx) <= available } }
        }
        val (kx, ky) = style.letterScale(scale)
        // at most MAX_BIG_LINES lines (review of stage 53, N7): whole big lines, the rest is cut
        val all: List<BigFont.Line> = chosen
        var keep = 0
        var pixels = -1 // the gaps between big lines: one pixel row each
        while (keep < all.size && ceil((pixels + 1 + all[keep].bits.height) * ky.toDouble() / style.cellHeight) <= MAX_BIG_LINES) {
            pixels += 1 + all[keep].bits.height
            keep++
        }
        val kept = all.take(keep.coerceAtLeast(1))
        if (kept.size < all.size) warnings += ArtWarning.TOO_LARGE
        if (kept.any { it.unsupported }) warnings += ArtWarning.UNSUPPORTED_CHARACTERS
        var bits = Bits.stack(kept.map { it.bits }, gap = 1, center = layout.center).scaled(kx, ky)
        if (element.look.outline) bits = bits.outline()
        bits = padToCells(bits, style)
        if (element.look.invert) bits = bits.inverted()
        if (layout.mirror) bits = bits.mirrored()
        val rows = cells(gray(bits), style, element.look)
        return Block(rows, rows.maxOfOrNull(TextColumns::of) ?: 0)
    }

    private fun wrapBig(line: String, font: BigFont, fits: (Int) -> Boolean): List<BigFont.Line> {
        val out = mutableListOf<String>()
        var current = ""
        for (word in line.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (fits(font.width(candidate))) { current = candidate; continue }
            if (current.isNotEmpty()) out += current
            current = ""
            if (fits(font.width(word))) { current = word; continue }
            for (char in word) {
                val next = current + char
                if (current.isNotEmpty() && !fits(font.width(next))) { out += current; current = char.toString() } else current = next
            }
        }
        if (current.isNotEmpty()) out += current
        return out.map { font.render(it) }
    }

    // ---- picture ----

    private fun renderPicture(element: PictureElement, available: Int, mirror: Boolean): Block? {
        val look = element.look
        val style = look.style
        val photo = element.photo?.takeIf { element.photoOptions.use }
        val ink = element.sketch?.inkBounds()
        val bounds: FloatArray = if (!element.crop) floatArrayOf(0f, 0f, 1f, 1f) else {
            val parts = listOfNotNull(ink, photo?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) })
            if (parts.isEmpty()) return null
            floatArrayOf(parts.minOf { it[0] }, parts.minOf { it[1] }, parts.maxOf { it[2] }, parts.maxOf { it[3] })
        }
        if (element.crop && ink == null && photo == null) return null
        val boundsWidth = (bounds[2] - bounds[0]).coerceAtLeast(1e-3f)
        val boundsHeight = (bounds[3] - bounds[1]).coerceAtLeast(1e-3f)
        val aspect = boundsHeight / boundsWidth
        var columns = floor(available * element.size.coerceIn(0.25f, 1f).toDouble()).toInt()
        columns -= columns % style.columns
        if (columns < style.columns) return null
        var rowCount = max(1, (aspect * columns / 2.0).roundToInt())
        if (rowCount > MAX_PICTURE_ROWS) {
            rowCount = MAX_PICTURE_ROWS
            columns = max(style.columns, floor(rowCount * 2.0 / aspect).toInt().let { it - it % style.columns })
        }
        val cellsAcross = columns / style.columns
        val width = cellsAcross * style.cellWidth
        val height = rowCount * style.cellHeight

        // the photo: gray inside its place, paper around it
        var photoGray: Raster? = null
        var inside: Bits? = null
        if (photo != null) {
            val pw = (photo.right - photo.left).coerceAtLeast(1e-3f)
            val ph = (photo.bottom - photo.top).coerceAtLeast(1e-3f)
            val sx0 = (bounds[0] - photo.left) / pw * photo.raster.width
            val sx1 = (bounds[2] - photo.left) / pw * photo.raster.width
            val sy0 = (bounds[1] - photo.top) / ph * photo.raster.height
            val sy1 = (bounds[3] - photo.top) / ph * photo.raster.height
            var gray = photo.raster.resampled(sx0.toDouble(), sy0.toDouble(), sx1.toDouble(), sy1.toDouble(), width, height, Raster.Pool.AVERAGE)
                .withContrast(element.photoOptions.contrast)
            val mask = Bits(width, height)
            for (y in 0 until height) for (x in 0 until width) {
                val ax = bounds[0] + (x + 0.5f) / width * boundsWidth
                val ay = bounds[1] + (y + 0.5f) / height * boundsHeight
                mask[x, y] = ax >= photo.left && ax < photo.right && ay >= photo.top && ay < photo.bottom
            }
            if (element.photoOptions.invert) gray = gray.inverted()
            for (y in 0 until height) for (x in 0 until width) if (!mask[x, y]) gray[x, y] = 0f
            photoGray = gray
            inside = mask
        }
        // the drawing: lines stay visible when made smaller, the eraser also takes away the photo
        var drawn: Raster? = null
        var erased: Raster? = null
        element.sketch?.let { sketch ->
            val size = sketch.ink.width.toDouble()
            val x0 = bounds[0] * size; val x1 = bounds[2] * size; val y0 = bounds[1] * size; val y1 = bounds[3] * size
            drawn = sketch.ink.resampled(x0, y0, x1, y1, width, height, Raster.Pool.MAX)
            erased = sketch.erased.resampled(x0, y0, x1, y1, width, height, Raster.Pool.AVERAGE)
        }
        val ramp = (style == ArtStyle.ASCII || style == ArtStyle.CUSTOM && customChars(look).size > 2) &&
            !look.outline && (photo == null || element.photoOptions.dither == Dither.NONE)
        val values: Raster
        if (ramp) {
            values = Raster(width, height)
            for (y in 0 until height) for (x in 0 until width) {
                val wiped = (erased?.get(x, y) ?: 0f) >= 0.5f
                values[x, y] = if (wiped) 0f else max(photoGray?.get(x, y) ?: 0f, if ((drawn?.get(x, y) ?: 0f) >= 0.5f) 1f else 0f)
            }
            var shown = if (look.invert) values.inverted() else values
            if (mirror) shown = mirrored(shown)
            val rows = cells(shown, style, look)
            return Block(rows, rows.maxOfOrNull(TextColumns::of) ?: 0)
        }
        var bits = Bits(width, height)
        photoGray?.let { gray ->
            val photoBits = gray.toBits(element.photoOptions.threshold, element.photoOptions.dither)
            for (y in 0 until height) for (x in 0 until width) bits[x, y] = photoBits[x, y] && inside!![x, y]
        }
        for (y in 0 until height) for (x in 0 until width) {
            if ((drawn?.get(x, y) ?: 0f) >= 0.5f) bits[x, y] = true
            if ((erased?.get(x, y) ?: 0f) >= 0.5f) bits[x, y] = false
        }
        if (look.outline) bits = bits.outline()
        if (look.invert) bits = bits.inverted()
        if (mirror) bits = bits.mirrored()
        val rows = cells(gray(bits), style, look)
        return Block(rows, rows.maxOfOrNull(TextColumns::of) ?: 0)
    }

    private fun mirrored(raster: Raster): Raster {
        val out = Raster(raster.width, raster.height)
        for (y in 0 until raster.height) for (x in 0 until raster.width) out[raster.width - 1 - x, y] = raster[x, y]
        return out
    }

    private fun gray(bits: Bits): Raster {
        val out = Raster(bits.width, bits.height)
        for (y in 0 until bits.height) for (x in 0 until bits.width) if (bits[x, y]) out[x, y] = 1f
        return out
    }

    private fun padToCells(bits: Bits, style: ArtStyle): Bits {
        val w = (bits.width + style.cellWidth - 1) / style.cellWidth * style.cellWidth
        val h = (bits.height + style.cellHeight - 1) / style.cellHeight * style.cellHeight
        return if (w == bits.width && h == bits.height) bits else bits.placed(w, h, 0, 0)
    }

    private fun customChars(look: Look): List<String> =
        TextColumns.graphemes(look.customChars).let { if (it.size >= 2) it else TextColumns.graphemes(Look.DEFAULT_CUSTOM) }

    /** Characters for [values] (0..1 per pixel); its size is a multiple of the style's cell. */
    internal fun cells(values: Raster, style: ArtStyle, look: Look): List<String> {
        val across = values.width / style.cellWidth
        val down = values.height / style.cellHeight
        fun ink(x: Int, y: Int) = values[x, y] >= 0.5f
        fun average(cx: Int, cy: Int): Float {
            var sum = 0f
            for (dy in 0 until style.cellHeight) for (dx in 0 until style.cellWidth) sum += values[cx + dx, cy + dy]
            return sum / (style.cellWidth * style.cellHeight)
        }
        val custom = if (style == ArtStyle.CUSTOM) customChars(look) else emptyList()
        return (0 until down).map { row ->
            val y = row * style.cellHeight
            buildString {
                for (column in 0 until across) {
                    val x = column * style.cellWidth
                    when (style) {
                        ArtStyle.BRAILLE -> {
                            var mask = 0
                            if (ink(x, y)) mask = mask or 0x01
                            if (ink(x, y + 1)) mask = mask or 0x02
                            if (ink(x, y + 2)) mask = mask or 0x04
                            if (ink(x + 1, y)) mask = mask or 0x08
                            if (ink(x + 1, y + 1)) mask = mask or 0x10
                            if (ink(x + 1, y + 2)) mask = mask or 0x20
                            if (ink(x, y + 3)) mask = mask or 0x40
                            if (ink(x + 1, y + 3)) mask = mask or 0x80
                            append((0x2800 + mask).toChar())
                        }
                        ArtStyle.BLOCKS -> {
                            val index = (if (ink(x, y)) 1 else 0) or (if (ink(x + 1, y)) 2 else 0) or
                                (if (ink(x, y + 1)) 4 else 0) or (if (ink(x + 1, y + 1)) 8 else 0)
                            append(if (index == 0) PAD else QUADRANTS[index])
                        }
                        ArtStyle.HALF_BLOCKS -> {
                            val index = (if (ink(x, y)) 1 else 0) or (if (ink(x, y + 1)) 2 else 0)
                            append(if (index == 0) PAD else HALVES[index])
                        }
                        ArtStyle.ASCII -> {
                            val char = ASCII_RAMP[(average(x, y) * (ASCII_RAMP.length - 1)).roundToInt().coerceIn(0, ASCII_RAMP.length - 1)]
                            append(if (char == ' ') PAD else char)
                        }
                        ArtStyle.CUSTOM -> {
                            val level = average(x, y)
                            val index = if (custom.size == 2) (if (level >= 0.5f) 1 else 0)
                                else (level * (custom.size - 1)).roundToInt().coerceIn(0, custom.size - 1)
                            val char = custom[index]
                            append(if (char == " ") PAD.toString() else char)
                        }
                        ArtStyle.EMOJI -> append(if (ink(x, y)) look.emoji.ink else look.emoji.paper)
                    }
                }
            }
        }
    }

    // ---- layout ----

    private fun padding(count: Int) = PAD.toString().repeat(max(0, count))

    private fun stacked(blocks: List<Block>, gap: Int, center: Boolean): List<String> {
        val width = blocks.maxOf { it.width }
        val out = mutableListOf<String>()
        blocks.forEachIndexed { index, block ->
            if (index > 0) repeat(gap) { out += "" }
            for (line in block.lines) {
                val left = if (center) (width - TextColumns.of(line)) / 2 else 0
                out += padding(left) + line
            }
        }
        return out
    }

    private fun sideBySide(blocks: List<Block>, gap: Int, center: Boolean): List<String> {
        val height = blocks.maxOf { it.lines.size }
        val columns = blocks.map { block ->
            val top = (height - block.lines.size) / 2
            List(height) { row ->
                val line = block.lines.getOrNull(row - top)
                if (line == null) padding(block.width) else {
                    val free = block.width - TextColumns.of(line)
                    val left = if (center) free / 2 else 0
                    padding(left) + line + padding(free - left)
                }
            }
        }
        return List(height) { row -> columns.joinToString(padding(gap)) { it[row] } }
    }

    /** [lines] in [frame], at most [width] columns wide as long as the lines fit: the padding gives way first. */
    private fun framed(lines: List<String>, frame: FrameOptions, width: Int): List<String> {
        val unit = frameUnit(frame)
        val widest = lines.maxOfOrNull(TextColumns::of) ?: 0
        // the inside of an emoji frame is a whole number of emojis wide
        fun contentWith(pad: Int): Int { var content = widest; while ((content + 2 * pad) % unit != 0) content++; return content }
        fun widthWith(pad: Int) = contentWith(pad) + 2 * pad + 2 * (if (frame.style == FrameStyle.EMOJI) unit else 1)
        var pad = frame.padding.coerceIn(0, 2)
        while (pad > 0 && widthWith(pad) > width) pad--
        val content = contentWith(pad)
        val inner = mutableListOf<String>()
        repeat(pad) { inner += "" }
        inner += lines
        repeat(pad) { inner += "" }
        if (frame.style == FrameStyle.EMOJI) {
            val emoji = frameEmoji(frame)
            val count = (content + 2 * pad) / unit + 2
            val edge = emoji.repeat(count)
            return listOf(edge) + inner.map { line ->
                emoji + padding(pad) + line + padding(content - TextColumns.of(line)) + padding(pad) + emoji
            } + edge
        }
        val corners = when (frame.style) { FrameStyle.DOUBLE -> "╔╗╚╝"; FrameStyle.ROUND -> "╭╮╰╯"; else -> "┌┐└┘" }
        val horizontal = if (frame.style == FrameStyle.DOUBLE) "═" else "─"
        val vertical = if (frame.style == FrameStyle.DOUBLE) "║" else "│"
        val bar = horizontal.repeat(content + 2 * pad)
        return listOf("${corners[0]}$bar${corners[1]}") + inner.map { line ->
            vertical + padding(pad) + line + padding(content - TextColumns.of(line)) + padding(pad) + vertical
        } + "${corners[2]}$bar${corners[3]}"
    }
}
