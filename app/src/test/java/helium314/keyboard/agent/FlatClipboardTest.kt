// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.clipboard.ClipboardHistoryView
import helium314.keyboard.keyboard.clipboard.FlatClipboard
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.ClipboardThumbnails
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.getEnabledClipboardToolbarKeys
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Clipboard view of the Flat style at the size of the reference screenshot (1200 px wide,
 * 3.25 px per dp) and the thumbnail cache that all styles use.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w369dp-h821dp-520dpi", shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class FlatClipboardTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val context get() = ContextThemeWrapper(latinIME, R.style.KeyboardTheme_Flat)
    private val colors get() = Settings.getValues().mColors

    @Before fun applyPreset() {
        latinIME.prefs().edit().clear().commit()
        latinIME.getSharedPreferences(FlatPreset.BACKUP_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        FlatPreset(latinIME).apply()
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
        KeyboardIconsSet.instance.loadIcons(context)
        ClipboardThumbnails.clear()
    }


    /** The settings singleton outlives a test; later tests must not inherit the Flat style. */
    @After fun restoreDefaults() {
        latinIME.prefs().edit().clear().commit()
        KeyboardIconsSet.needsReload = true
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
        KeyboardSwitcher::class.java.getDeclaredField("mClipboardStripView").apply { isAccessible = true }.set(KeyboardSwitcher.getInstance(), null)
    }

    @Test fun sampleSizeKeepsThePictureAtLeastAsWideAsItIsShown() {
        // the old formula gave 0 for a 1200 px screenshot on a 1200 px screen, i.e. full resolution
        assertEquals(2, ClipboardThumbnails.sampleSize(1200, 600))
        assertEquals(1, ClipboardThumbnails.sampleSize(1200, 601))
        assertEquals(4, ClipboardThumbnails.sampleSize(4000, 600))
        assertEquals(1, ClipboardThumbnails.sampleSize(300, 600))
        assertEquals(1, ClipboardThumbnails.sampleSize(0, 600))
        assertEquals(1, ClipboardThumbnails.sampleSize(1200, 0))
    }

    @Test fun thumbnailsAreDecodedSmallerOnceAndForgottenOnClear() {
        val file = File(latinIME.cacheDir, "clip.png")
        Bitmap.createBitmap(1200, 2670, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.also { source ->
            file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val first = assertNotNull(ClipboardThumbnails.fromFile(file.absolutePath, 600))
        assertEquals(600, first.width)
        assertEquals(1335, first.height)
        assertSame(first, ClipboardThumbnails.fromFile(file.absolutePath, 600))
        ClipboardThumbnails.clear()
        assertNotSame(first, ClipboardThumbnails.fromFile(file.absolutePath, 600))
        assertNull(ClipboardThumbnails.fromFile(File(latinIME.cacheDir, "missing.png").absolutePath, 600))
        // a deleted file is not served from memory, a replaced one is decoded again
        val cached = assertNotNull(ClipboardThumbnails.fromFile(file.absolutePath, 600))
        Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888).also { other -> file.outputStream().use { other.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        file.setLastModified(file.lastModified() + 5000)
        val replaced = assertNotNull(ClipboardThumbnails.fromFile(file.absolutePath, 600))
        assertNotSame(cached, replaced)
        assertEquals(600, replaced.height)
        file.delete()
        assertNull(ClipboardThumbnails.fromFile(file.absolutePath, 600))
    }

    @Test fun presetPutsTwoRoundKeysIntoTheClipboardHeader() {
        assertEquals(listOf(ToolbarKey.CLEAR_CLIPBOARD, ToolbarKey.SETTINGS), getEnabledClipboardToolbarKeys(latinIME.prefs()))
    }

    @Test fun headerSitsWhereTheReferenceHasIt() {
        val container = LayoutInflater.from(context).inflate(R.layout.strip_container, null) as ViewGroup
        container.findViewById<View>(R.id.suggestion_strip_view).visibility = View.GONE
        container.findViewById<View>(R.id.emoji_tab_strip).visibility = View.GONE
        val strip = container.findViewById<LinearLayout>(R.id.clipboard_strip)
        val keys = getEnabledClipboardToolbarKeys(latinIME.prefs()).map { createToolbarKey(context, it) }
        var backClicks = 0
        FlatClipboard.buildHeader(strip, keys, colors) { backClicks++ }
        container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(STRIP, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, WIDTH, STRIP)

        fun bounds(view: View): List<Int> {
            val left = generateSequence(view) { it.parent as? View }.takeWhile { it !== container }.sumOf { it.left }
            val top = generateSequence(view) { it.parent as? View }.takeWhile { it !== container }.sumOf { it.top }
            return listOf(left, top, left + view.width, top + view.height)
        }
        fun assertBounds(expected: List<Int>, view: View) = bounds(view).let { actual ->
            assertTrue(expected.zip(actual).all { (e, a) -> abs(e - a) <= 2 }, "expected $expected, was $actual")
        }
        val back = strip.findViewById<ImageButton>(R.id.agent_clipboard_back)
        val title = strip.findViewById<TextView>(R.id.agent_clipboard_title)
        // reference: circles of 111 px at x 20..131, 906..1017 and 1069..1180, centred 71.5 px below the upper edge
        assertBounds(listOf(20, 16, 131, 127), back)
        assertBounds(listOf(906, 16, 1017, 127), keys[0])
        assertBounds(listOf(1069, 16, 1180, 127), keys[1])
        assertTrue(abs(bounds(title)[0] - 173) <= 2, "title starts at ${bounds(title)[0]}")
        assertTrue(title.text.isNotBlank())
        assertNotNull(back.contentDescription)
        back.performClick()
        assertEquals(1, backClicks)

        // with the default eleven clipboard keys the header takes three and never the close key
        val many = listOf(ToolbarKey.CLOSE_HISTORY, ToolbarKey.CLEAR_CLIPBOARD, ToolbarKey.UP, ToolbarKey.DOWN, ToolbarKey.LEFT, ToolbarKey.RIGHT)
            .map { createToolbarKey(context, it) }
        FlatClipboard.buildHeader(strip, many, colors) { }
        assertEquals(listOf(ToolbarKey.CLEAR_CLIPBOARD, ToolbarKey.UP, ToolbarKey.DOWN), (2 until strip.childCount).map { strip.getChildAt(it).tag })
        container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(STRIP, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, WIDTH, STRIP)
        assertTrue(strip.width <= WIDTH, "header is ${strip.width} wide")
        FlatClipboard.buildHeader(strip, keys, colors) { backClicks++ }
        container.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(STRIP, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, WIDTH, STRIP)

        val picture = Bitmap.createBitmap(WIDTH, STRIP, Bitmap.Config.ARGB_8888)
        container.draw(Canvas(picture))
        save(picture, "clipboard-header.png")
        fun hex(x: Int, y: Int) = String.format("#%06X", picture.getPixel(x, y) and 0xFFFFFF)
        assertEquals("#4C4C4C", hex(75, 24))   // inside the back circle, above the arrow
        assertEquals("#4C4C4C", hex(961, 24))
        assertEquals("#4C4C4C", hex(1124, 24))
        assertEquals("#000000", hex(24, 20))   // outside the circle, inside its square
        assertEquals("#000000", hex(600, 120))
    }

    @Test fun cardsFormTwoColumnsWithSquarePictures() {
        // list as in the clipboard view: side padding 7 px, two columns
        val list = RecyclerView(context).apply {
            layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
            setPadding(7, 0, 7, 0)
        }
        val file = File(latinIME.cacheDir, "clip-card.png")
        Bitmap.createBitmap(600, 1335, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }.also { source ->
            file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val colors = colors
        class Holder(view: View) : RecyclerView.ViewHolder(view)
        list.adapter = object : RecyclerView.Adapter<Holder>() {
            override fun getItemCount() = 4
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
                val card = LayoutInflater.from(parent.context).inflate(R.layout.clipboard_entry_key, parent, false)
                val text = card.findViewById<TextView>(R.id.clipboard_entry_text_content)
                val image = card.findViewById<ImageView>(R.id.clipboard_entry_image_content)
                card.findViewById<View>(R.id.clipboard_entry_pinned_icon).visibility = View.GONE
                FlatClipboard.styleCard(card, text, image, colors, parent.width)
                return Holder(card)
            }
            override fun onBindViewHolder(holder: Holder, position: Int) {
                val text = holder.itemView.findViewById<TextView>(R.id.clipboard_entry_text_content)
                val image = holder.itemView.findViewById<ImageView>(R.id.clipboard_entry_image_content)
                if (position < 2) {
                    image.visibility = View.VISIBLE
                    text.visibility = View.GONE
                    image.setImageBitmap(ClipboardThumbnails.fromFile(file.absolutePath, 600))
                    FlatClipboard.squareImage(image, list.width, 2, list.paddingLeft + list.paddingRight)
                } else {
                    image.visibility = View.GONE
                    text.visibility = View.VISIBLE
                    text.text = "Beispieltext $position"
                }
            }
        }
        val frame = FrameLayout(context).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR; addView(list) }
        repeat(2) {
            frame.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, WIDTH, 900)
        }
        val cards = (0 until list.childCount).map(list::getChildAt).sortedBy { it.top * 10000 + it.left }
        assertEquals(4, cards.size)
        // reference: columns x 22..585 and 615..1178, square pictures of 563 px, 26 px between rows
        assertTrue(abs(cards[0].left - 22) <= 2 && abs(cards[0].right - 585) <= 2, "${cards[0].left}..${cards[0].right}")
        assertTrue(abs(cards[1].left - 615) <= 2 && abs(cards[1].right - 1178) <= 2, "${cards[1].left}..${cards[1].right}")
        assertTrue(abs(cards[0].height - 563) <= 2, "picture card is ${cards[0].height} high")
        assertTrue(abs(cards[2].top - cards[0].bottom - 26) <= 1, "row gap ${cards[2].top - cards[0].bottom}")

        val picture = Bitmap.createBitmap(WIDTH, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        frame.draw(Canvas(picture))
        save(picture, "clipboard-cards.png")
        fun hex(x: Int, y: Int) = String.format("#%06X", picture.getPixel(x, y) and 0xFFFFFF)
        assertEquals("#00FF00", hex(300, cards[0].top + 280)) // the picture fills the card
        // the rounded corner clips the picture only with hardware rendering, which a test bitmap does not have
        assertTrue(cards[0].clipToOutline)
        assertEquals("#000000", hex(cards[2].left + 2, cards[2].top + 2)) // rounded corner of a text card
        assertEquals("#000000", hex(600, cards[0].top + 280)) // gap between the columns
        assertEquals("#4C4C4C", hex(cards[2].right - 30, cards[2].top + cards[2].height / 2)) // text card
    }

    @Test fun clipboardViewGetsHeaderSectionLabelAndAListDownToTheLowerEdge() {
        val container = LayoutInflater.from(context).inflate(R.layout.strip_container, null) as ViewGroup
        val strip = container.findViewById<LinearLayout>(R.id.clipboard_strip)
        // the view fetches the strip from the switcher, which has no input view in this test
        KeyboardSwitcher::class.java.getDeclaredField("mClipboardStripView").apply { isAccessible = true }
            .set(KeyboardSwitcher.getInstance(), strip)
        val view = LayoutInflater.from(context).inflate(R.layout.clipboard_history_view, FrameLayout(context), false) as ClipboardHistoryView
        val codes = mutableListOf<Int>()
        view.keyboardActionListener = object : KeyboardActionListener.Adapter() {
            override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) { codes += primaryCode }
        }
        view.setHardwareAcceleratedDrawingEnabled(true)
        view.startClipboardHistory(ClipboardHistoryManager(latinIME), null, EditorInfo(), view.keyboardActionListener)

        assertEquals(View.LAYER_TYPE_NONE, view.layerType)
        assertEquals(View.GONE, view.findViewById<View>(R.id.bottom_row_keyboard).visibility)
        val label = view.getChildAt(0) as TextView
        assertEquals(R.id.agent_clipboard_section, label.id)
        assertTrue(label.text.isNotBlank())
        val list = view.findViewById<View>(R.id.clipboard_list)
        val keyboardHeight = ResourceUtils.getSecondaryKeyboardHeight(context.resources, Settings.getValues())
        assertEquals(keyboardHeight - label.layoutParams.height, list.layoutParams.height)
        assertTrue(abs(label.layoutParams.height - 81) <= 1, "label band ${label.layoutParams.height}") // 25 dp

        // header: back, title and the two keys of the preset, nothing else
        assertEquals(listOf(R.id.agent_clipboard_back, R.id.agent_clipboard_title), listOf(strip.getChildAt(0).id, strip.getChildAt(1).id))
        assertEquals(listOf(ToolbarKey.CLEAR_CLIPBOARD, ToolbarKey.SETTINGS), listOf(strip.getChildAt(2).tag, strip.getChildAt(3).tag))
        assertEquals(4, strip.childCount)
        strip.findViewById<View>(R.id.agent_clipboard_back).performClick()
        assertEquals(listOf(KeyCode.CLIPBOARD), codes)

        // starting again must not add a second header or label
        view.startClipboardHistory(ClipboardHistoryManager(latinIME), null, EditorInfo(), view.keyboardActionListener)
        assertEquals(4, strip.childCount)
        assertEquals(R.id.clipboard_list, (view.getChildAt(1) as ViewGroup).getChildAt(1).id)
    }

    @Test fun withoutAStripTheClipboardViewKeepsItsBottomRow() {
        latinIME.prefs().edit().putString(Settings.PREF_TOOLBAR_MODE, "HIDDEN").commit()
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(EditorInfo(), false, latinIME.packageName))
        assertFalse(Settings.getValues().isSecondaryStripVisible)
        val container = LayoutInflater.from(context).inflate(R.layout.strip_container, null) as ViewGroup
        KeyboardSwitcher::class.java.getDeclaredField("mClipboardStripView").apply { isAccessible = true }
            .set(KeyboardSwitcher.getInstance(), container.findViewById<LinearLayout>(R.id.clipboard_strip))
        val view = LayoutInflater.from(context).inflate(R.layout.clipboard_history_view, FrameLayout(context), false) as ClipboardHistoryView
        view.keyboardActionListener = KeyboardActionListener.Adapter()
        view.startClipboardHistory(ClipboardHistoryManager(latinIME), null, EditorInfo(), view.keyboardActionListener)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.bottom_row_keyboard).visibility)
        assertNull(view.findViewById<View>(R.id.agent_clipboard_section))
    }

    @Test fun chipIsADarkPillWithARoundThumbnailAndNoCloseCross() {
        val chip = LayoutInflater.from(context).inflate(R.layout.clipboard_suggestion, LinearLayout(context), false) as ViewGroup
        val text = chip.findViewById<TextView>(R.id.clipboard_suggestion_text)
        val image = chip.findViewById<ImageView>(R.id.clipboard_suggestion_image)
        val close = chip.findViewById<View>(R.id.clipboard_suggestion_close)
        var dismissed = 0
        FlatClipboard.styleChip(chip, text, image, close, hasImage = true, imageLabel = null) { dismissed++ }
        assertEquals(View.GONE, close.visibility)
        assertSame(image, chip.getChildAt(0))
        assertTrue(abs(image.layoutParams.width - 85) <= 1 && image.layoutParams.width == image.layoutParams.height) // 26 dp
        assertTrue(image.clipToOutline)
        assertTrue(abs(chip.layoutParams.height - 111) <= 1) // 34 dp
        val pill = chip.background as android.graphics.drawable.GradientDrawable
        assertEquals(0xFF191919.toInt(), pill.color?.defaultColor)
        assertTrue(pill.cornerRadius >= 55f)
        assertEquals(context.getString(R.string.agent_clipboard_image), text.text.toString())
        assertTrue(text.performLongClick())
        assertEquals(1, dismissed)

        val labelled = LayoutInflater.from(context).inflate(R.layout.clipboard_suggestion, LinearLayout(context), false) as ViewGroup
        val labelledText = labelled.findViewById<TextView>(R.id.clipboard_suggestion_text)
        FlatClipboard.styleChip(labelled, labelledText, labelled.findViewById(R.id.clipboard_suggestion_image),
            labelled.findViewById(R.id.clipboard_suggestion_close), hasImage = true, imageLabel = "Screenshot") { }
        assertEquals("Screenshot", labelledText.text.toString())

        // a text clip keeps its text
        val plain = LayoutInflater.from(context).inflate(R.layout.clipboard_suggestion, LinearLayout(context), false) as ViewGroup
        val plainText = plain.findViewById<TextView>(R.id.clipboard_suggestion_text).apply { this.text = "kopiert" }
        FlatClipboard.styleChip(plain, plainText, plain.findViewById(R.id.clipboard_suggestion_image),
            plain.findViewById(R.id.clipboard_suggestion_close), hasImage = false, imageLabel = null) { }
        assertEquals("kopiert", plainText.text.toString())
        assertFalse(plain.getChildAt(0) is ImageView)
    }

    private fun save(picture: Bitmap, fileName: String) {
        File("build/reports/flat-style").apply { mkdirs() }.resolve(fileName).outputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private companion object {
        const val WIDTH = 1200
        const val STRIP = 130
    }
}
