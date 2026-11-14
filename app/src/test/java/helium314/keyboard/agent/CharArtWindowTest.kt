// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.edit
import dev.agentkeyboard.core.charart.TargetApp
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.agent.charart.CharArtCanvas
import helium314.keyboard.agent.charart.CharArtImages
import helium314.keyboard.agent.charart.CharArtKey
import helium314.keyboard.agent.charart.CharArtKeys
import helium314.keyboard.agent.charart.CharArtView
import helium314.keyboard.agent.commands.SnippetStore
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.*

/** Stage 53: the character art window in the keyboard, with a simulated field. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowInputMethodService::class])
class CharArtWindowTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val switcher = KeyboardSwitcher.getInstance()
    private lateinit var root: View
    private val window: CharArtView get() = root.findViewById(R.id.agent_char_art)
    private val controller get() = latinIME.agentEditorController
    private val field get() = ShadowInputMethodService.text

    @Before fun setUp() {
        ShadowInputMethodService.reset()
        latinIME.prefs().edit(commit = true) { clear(); putBoolean(Settings.PREF_SHOW_EMOJI_KEY, true) }
        root = switcher.onCreateInputView(latinIME, true)
        latinIME.setInputView(root)
        controller.charArtWorker = { it.run() }
        startField("")
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun startField(text: String, inputType: Int = InputType.TYPE_CLASS_TEXT, imeOptions: Int = 0, packageName: String? = null) {
        ShadowInputMethodService.text = text
        ShadowInputMethodService.selectionStart = text.length
        ShadowInputMethodService.selectionEnd = text.length
        ShadowInputMethodService.currentInputType = inputType
        ShadowInputMethodService.currentImeOptions = imeOptions
        ShadowInputMethodService.currentPackageName = packageName
        val info = EditorInfo().apply {
            this.inputType = inputType; this.imeOptions = imeOptions; this.packageName = packageName
            initialSelStart = text.length; initialSelEnd = text.length
        }
        latinIME.onStartInput(info, false)
        latinIME.onStartInputView(info, false)
        idle()
        switcher.reloadMainKeyboard()
    }

    private fun pen() = latinIME.mKeyboardActionListener.onCodeInput(KeyCode.AGENT_CHAR_ART, -1, -1, false)

    private fun views(group: View = window): List<View> = when (group) {
        is ViewGroup -> listOf(group) + (0 until group.childCount).flatMap { views(group.getChildAt(it)) }
        else -> listOf(group)
    }
    /** Visible up to the window (the window is not attached to a screen in tests, so isShown is false). */
    private fun visible(view: View): Boolean {
        var current: View? = view
        while (current != null && current !== window) {
            if (current.visibility != View.VISIBLE) return false
            current = current.parent as? View
        }
        return current === window && window.visibility == View.VISIBLE
    }
    private fun tap(label: String) = views().filterIsInstance<TextView>().first { it.text.toString() == label && visible(it) }.performClick()
    private fun tap(id: Int) = window.findViewById<View>(id).performClick()
    private fun string(id: Int) = latinIME.getString(id)
    private fun shown(id: Int) = window.findViewById<View>(id).let { it.visibility == View.VISIBLE }

    private fun layoutWindow(width: Int = 1080, height: Int = 820) {
        window.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        window.layout(0, 0, width, height)
        idle()
    }

    private fun draw(vararg points: Pair<Float, Float>) {
        val canvas = window.findViewById<CharArtCanvas>(R.id.agent_char_art_canvas)
        val location = { (x, y): Pair<Float, Float> ->
            val side = minOf(canvas.width, canvas.height).toFloat()
            val left = (canvas.width - side) / 2f; val top = (canvas.height - side) / 2f
            (left + x * side) to (top + y * side)
        }
        val time = SystemClock.uptimeMillis()
        points.forEachIndexed { index, point ->
            val (x, y) = location(point)
            val action = when (index) { 0 -> MotionEvent.ACTION_DOWN; points.lastIndex -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            canvas.dispatchTouchEvent(MotionEvent.obtain(time, time + index * 10L, action, x, y, 0))
        }
        idle()
    }

    @Test fun thePenInTheEmojiKeyOpensTheWindowWithTheFieldText() {
        startField("Hallo")
        val emoji = switcher.keyboard!!.getKey(KeyCode.EMOJI)
        assertNotNull(emoji, "the emoji key is on the keyboard")
        val pen = emoji.popupKeys?.singleOrNull { it.mCode == KeyCode.AGENT_CHAR_ART }
        assertNotNull(pen, "a long press shows the pen")
        assertEquals(CharArtKey.LABEL, pen.mLabel)
        assertEquals(View.GONE, window.visibility)
        pen()
        assertEquals(View.VISIBLE, window.visibility)
        assertEquals("Hallo", window.text)
        assertTrue(controller.isBlockingInput) // the keys of the keyboard do nothing while the window is open
        assertFalse(window.result.isEmpty) // the text in big braille letters right away
        assertEquals("Hallo", field) // opening changes nothing in the field
        tap(R.id.agent_char_art_close)
        assertEquals(View.GONE, window.visibility)
        assertEquals("", window.text) // nothing of the field is kept
        assertFalse(controller.isBlockingInput)
        // a new field closes an open window
        pen()
        startField("anders")
        assertEquals(View.GONE, window.visibility)
    }

    @Test fun longPressOnTheEmojiKeyAndReleaseOnThePen() {
        startField("Hallo")
        val view = switcher.mainKeyboardView
        val key = switcher.keyboard!!.getKey(KeyCode.EMOJI)!!
        val x = (key.x + key.width / 2).toFloat()
        val y = (key.y + key.height / 2).toFloat()
        val down = SystemClock.uptimeMillis()
        view.onTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        // the long press timer fires: the popup with the pen opens instead of the emoji palette
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertTrue(view.isShowingPopupKeysPanel)
        assertEquals(View.GONE, window.visibility)
        view.onTouchEvent(MotionEvent.obtain(down, down + 600, MotionEvent.ACTION_UP, x, y, 0))
        idle()
        assertEquals(View.VISIBLE, window.visibility)
        assertEquals("Hallo", window.text)
        assertEquals("Hallo", field)
    }

    @Test fun neverInPasswordIncognitoOrLockedFields() {
        startField("geheim", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertNull(switcher.keyboard!!.getKey(KeyCode.EMOJI)?.popupKeys?.firstOrNull { it.mCode == KeyCode.AGENT_CHAR_ART })
        assertFalse(controller.openCharArt())
        assertEquals(View.GONE, window.visibility)
        assertEquals("", window.text)

        startField("privat", imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertNull(switcher.keyboard!!.getKey(KeyCode.EMOJI)?.popupKeys?.firstOrNull { it.mCode == KeyCode.AGENT_CHAR_ART })
        pen()
        assertEquals(View.GONE, window.visibility)

        latinIME.prefs().edit(commit = true) { putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true) }
        startField("inkognito")
        pen()
        assertEquals(View.GONE, window.visibility)
        latinIME.prefs().edit(commit = true) { putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false) }

        startField("offen")
        shadowOf(latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(true)
        pen()
        assertEquals(View.GONE, window.visibility)
        // locked while the window is open: the tap inserts nothing
        shadowOf(latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(false)
        pen()
        assertEquals(View.VISIBLE, window.visibility)
        shadowOf(latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(true)
        tap(R.id.agent_char_art_insert)
        assertEquals("offen", field)
        tap(R.id.agent_char_art_copy)
        assertNull((latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip)
        // nor is it saved as a snippet (review of stage 53, N10)
        tap(R.id.agent_char_art_snippet)
        tap(string(R.string.agent_char_art_keys_done))
        idle()
        assertEquals(string(R.string.agent_char_art_protected), ShadowToast.getTextOfLatestToast())
        shadowOf(latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(false)
        assertTrue(SnippetStore(latinIME).read().isEmpty()) // readable only unlocked
    }

    @Test fun drawingUpdatesThePreview() {
        pen()
        layoutWindow()
        assertTrue(window.result.isEmpty)
        val preview = window.findViewById<TextView>(R.id.agent_char_art_preview)
        assertEquals("", preview.text.toString())
        assertEquals(string(R.string.agent_char_art_empty), window.findViewById<TextView>(R.id.agent_char_art_status).text.toString())
        draw(0.1f to 0.1f, 0.5f to 0.5f, 0.9f to 0.9f)
        assertFalse(window.result.isEmpty)
        // the preview shows exactly what would be inserted (review of stage 53, M1)
        assertEquals(window.result.text, preview.text.toString())
        assertTrue(preview.text.any { it in '⠁'..'⣿' }) // braille dots by default
        val first = window.result.text
        // every option renders again at once
        tap(string(R.string.agent_char_art_tab_style))
        tap(string(R.string.agent_char_art_style_blocks))
        assertNotEquals(first, window.result.text)
        assertTrue(window.result.text.any { it in '▀'..'▟' })
        tap(string(R.string.agent_char_art_tab_app))
        tap(string(R.string.agent_char_art_app_discord))
        assertTrue(window.result.text.startsWith("```\n"))
        assertEquals(window.result.text, preview.text.toString()) // the backticks too
        tap(string(R.string.agent_char_art_code_block))
        assertFalse(window.result.text.startsWith("```"))
        window.setWidth(12)
        assertTrue(window.result.columns <= 12)
        assertEquals(12, latinIME.prefs().getInt(AgentPreferences.CHAR_ART_WIDTH_PREFIX + "discord", 0)) // remembered per app
        // undo of the last step, then clear
        tap(string(R.string.agent_char_art_tab_draw))
        tap(string(R.string.agent_char_art_tool_line))
        draw(0.1f to 0.9f, 0.9f to 0.1f)
        val withLine = window.result.text
        tap(string(R.string.agent_char_art_undo_step))
        assertNotEquals(withLine, window.result.text)
        assertEquals(1, window.sketch.ops.size)
        tap(string(R.string.agent_char_art_clear))
        assertTrue(window.result.isEmpty)
        assertEquals("", field) // nothing went to the field
    }

    @Test fun insertsOnlyOnTapExactlyOnceAndUndoes() {
        startField("Hi ")
        pen()
        tap(R.id.agent_char_art_text)
        tap(string(R.string.agent_char_art_keys_done))
        val art = window.result.text
        assertTrue(art.isNotEmpty())
        assertEquals("Hi ", field) // shown, not inserted
        tap(R.id.agent_char_art_insert)
        assertEquals("Hi $art", field)
        assertTrue(window.offersUndo)
        assertEquals(string(R.string.agent_char_art_undo), window.findViewById<TextView>(R.id.agent_char_art_insert).text.toString())
        // the second tap is the undo, never a second insertion
        tap(R.id.agent_char_art_insert)
        assertEquals("Hi ", field)
        assertFalse(window.offersUndo)
        tap(R.id.agent_char_art_insert)
        assertEquals("Hi $art", field)
        // the undo offer ends after a few seconds
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(CharArtView.UNDO_MILLIS + 100))
        assertFalse(window.offersUndo)
        assertEquals(string(R.string.agent_char_art_insert), window.findViewById<TextView>(R.id.agent_char_art_insert).text.toString())
        assertEquals("Hi $art", field)
    }

    @Test fun replaceTakesThePlaceOfTheFieldTextAndUndoBringsItBack() {
        startField("Hallo")
        pen()
        tap(string(R.string.agent_char_art_tab_text))
        tap(string(R.string.agent_char_art_mode_unicode))
        // the text styles of stage 39; since the review of stage 53 (M1) a field without a known app
        // gets "Other" and text styles alone never get a code block, so the art is exactly the bold text
        assertEquals("𝐇𝐚𝐥𝐥𝐨", window.result.text)
        val art = window.result.text
        assertTrue(shown(R.id.agent_char_art_replace))
        tap(R.id.agent_char_art_replace)
        assertEquals(art, field)
        assertFalse(shown(R.id.agent_char_art_replace))
        tap(R.id.agent_char_art_insert) // undo
        assertEquals("Hallo", field)
        assertTrue(shown(R.id.agent_char_art_replace))
        // text after the cursor: only insert, no replace
        ShadowInputMethodService.text = "Hallo Welt"
        ShadowInputMethodService.selectionStart = 5
        ShadowInputMethodService.selectionEnd = 5
        startFieldKeepingText(5)
        pen()
        assertEquals("Hallo Welt", window.text)
        assertFalse(shown(R.id.agent_char_art_replace))
    }

    private fun startFieldKeepingText(cursor: Int) {
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; initialSelStart = cursor; initialSelEnd = cursor }
        latinIME.onStartInput(info, false)
        latinIME.onStartInputView(info, false)
        idle()
    }

    @Test fun textIsTypedWithTheWindowsOwnKeys() {
        pen()
        tap(R.id.agent_char_art_text)
        assertTrue(shown(R.id.agent_char_art_keys))
        listOf("o", "k").forEach(::tap)
        assertEquals("ok", window.text)
        tap("⌫")
        tap("j")
        assertEquals("oj", window.text)
        assertEquals("", field) // the keys of the window never reach the field
        tap(string(R.string.agent_char_art_keys_done))
        assertFalse(shown(R.id.agent_char_art_keys))
        assertFalse(window.result.isEmpty)
        // the text can stay out of the art without losing it
        tap(string(R.string.agent_char_art_text_off))
        assertTrue(window.result.isEmpty)
        assertEquals("oj", window.text)
        tap(string(R.string.agent_char_art_mode_plain))
        assertEquals(listOf("oj"), window.result.lines)
    }

    @Test fun savesTheArtAsSnippet() {
        startField("OK")
        pen()
        val art = window.result.text
        tap(R.id.agent_char_art_snippet)
        assertTrue(shown(R.id.agent_char_art_keys))
        tap(string(R.string.agent_char_art_keys_done))
        idle()
        val saved = SnippetStore(latinIME).read().single()
        assertTrue(saved.name.all { it.isLetter() })
        assertEquals(art, saved.text)
        assertTrue(saved.literal) // inserted exactly as it is, no placeholders
        assertEquals("OK", field)
    }

    @Test fun profileFollowsTheFieldsApp() {
        startField("x", packageName = "com.discord")
        pen()
        assertTrue(window.result.text.startsWith("```\n"))
        tap(R.id.agent_char_art_close)
        startField("x", packageName = "org.thoughtcrime.securesms")
        pen()
        assertFalse(window.result.text.contains("```"))
        assertTrue(window.result.columns <= TargetApp.SIGNAL.defaultWidth)
    }

    @Test fun photoTemplate() {
        pen()
        layoutWindow()
        val photo = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (x in 0 until 32) for (y in 0 until 32) setPixel(x, y, Color.BLACK)
        }
        window.usePhoto(photo)
        assertFalse(window.result.isEmpty)
        tap(string(R.string.agent_char_art_tab_photo))
        tap(string(R.string.agent_char_art_photo_use)) // only a template to draw over
        assertTrue(window.result.isEmpty)
        tap(string(R.string.agent_char_art_photo_template))
        assertFalse(window.result.isEmpty)
        tap(string(R.string.agent_char_art_photo_remove))
        assertTrue(window.result.isEmpty)
    }

    /** A phone in portrait: 1080 px at density 3, the keyboard area 360×273 dp (drawing and preview side by side). */
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "xxhdpi")
    fun renderWindowPreview() {
        startField("Hi")
        pen()
        layoutWindow(1080, 820)
        draw(0.15f to 0.2f, 0.3f to 0.1f, 0.5f to 0.25f, 0.7f to 0.1f, 0.85f to 0.2f)
        window.findViewById<CharArtCanvas>(R.id.agent_char_art_canvas).tool = CharArtCanvas.Tool.CIRCLE
        draw(0.2f to 0.35f, 0.8f to 0.95f)
        layoutWindow(1080, 820)
        val bitmap = Bitmap.createBitmap(1080, 820, Bitmap.Config.ARGB_8888)
        window.draw(Canvas(bitmap))
        File("build/agent-previews").mkdirs()
        File("build/agent-previews/charart-window.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertFalse(window.result.isEmpty)
    }

    // ---- review of stage 53 ----

    /** M1: an app without a profile gets "Other"; text styles alone never get a code block, art does, and the preview shows the backticks. */
    @Test fun unknownAppsGetOtherAndTextStylesNoCodeBlock() {
        startField("Hallo", packageName = "com.example.chat")
        pen()
        tap(string(R.string.agent_char_art_tab_app))
        val other = views().filterIsInstance<TextView>().first { it.text.toString() == string(R.string.agent_char_art_app_generic) }
        assertTrue(other.isSelected)
        assertFalse(window.result.text.contains("```")) // big letters, but "Other" has no code block
        tap(R.id.agent_char_art_close)
        // WhatsApp: the big letters are art and keep the code block, the bold text alone does not
        startField("Hallo", packageName = "com.whatsapp")
        pen()
        val preview = window.findViewById<TextView>(R.id.agent_char_art_preview)
        assertTrue(window.result.codeBlock)
        assertTrue(window.result.text.startsWith("```") && window.result.text.endsWith("```"))
        assertEquals(window.result.text, preview.text.toString())
        tap(string(R.string.agent_char_art_tab_text))
        tap(string(R.string.agent_char_art_mode_unicode))
        assertEquals("𝐇𝐚𝐥𝐥𝐨", window.result.text)
        assertEquals("𝐇𝐚𝐥𝐥𝐨", preview.text.toString())
        tap(R.id.agent_char_art_insert)
        assertEquals("Hallo𝐇𝐚𝐥𝐥𝐨", field) // what the preview showed, nothing more
        // a frame needs equal widths again: the code block comes back
        tap(string(R.string.agent_char_art_tab_frame))
        tap(string(R.string.agent_char_art_frame_line))
        assertTrue(window.result.text.startsWith("```"))
        assertEquals(window.result.text, preview.text.toString())
    }

    private fun keyCells(): List<TextView> {
        val keys = window.findViewById<ViewGroup>(R.id.agent_char_art_keys)
        return (0 until keys.childCount).flatMap { row -> (keys.getChildAt(row) as ViewGroup).let { r -> (0 until r.childCount).map { r.getChildAt(it) as TextView } } }
    }

    /** Letter width and row height of the window's keys in dp while typing, and whether the header stayed. */
    private fun renderTyping(width: Int, height: Int, name: String): Triple<Float, Float, Boolean> {
        startField("Hallo")
        pen()
        layoutWindow(width, height)
        tap(R.id.agent_char_art_text)
        layoutWindow(width, height)
        assertTrue(window.isTyping)
        assertTrue(shown(R.id.agent_char_art_keys))
        // the preview, the options and the buttons give their place to the keys
        assertFalse(visible(window.findViewById(R.id.agent_char_art_preview)))
        assertFalse(visible(window.findViewById(R.id.agent_char_art_options)))
        assertFalse(visible(window.findViewById(R.id.agent_char_art_insert)))
        val density = latinIME.resources.displayMetrics.density
        val keys = window.findViewById<View>(R.id.agent_char_art_keys)
        assertTrue(keys.width >= width - 9 * density, "the keys take the whole width: ${keys.width} of $width")
        val letters = keyCells().filter { it.text.length == 1 && it.text[0].isLetter() }
        assertTrue(letters.isNotEmpty())
        val narrowest = letters.minOf { it.width } / density
        val lowest = keyCells().minOf { it.height } / density
        val header = visible(window.findViewById(R.id.agent_char_art_close))
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        window.draw(Canvas(bitmap))
        File("build/agent-previews").mkdirs()
        File("build/agent-previews/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        // "Done" brings back everything
        tap(string(R.string.agent_char_art_keys_done))
        layoutWindow(width, height)
        assertTrue(visible(window.findViewById(R.id.agent_char_art_preview)))
        assertTrue(visible(window.findViewById(R.id.agent_char_art_insert)))
        assertTrue(visible(window.findViewById(R.id.agent_char_art_close)))
        return Triple(narrowest, lowest, header)
    }

    /**
     * M2: typing on a phone in portrait (360 dp wide, keyboard area 246 dp high at density 3): the keys
     * take the full width; a letter is at least 32 dp wide (eleven German letters in 352 dp, as wide as
     * the keyboard's own keys; 44 dp is not possible with eleven keys in a row) and each row 44 dp high.
     */
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "xxhdpi")
    fun typingModePortrait() {
        val (narrowest, lowest, header) = renderTyping(1080, 738, "charart-typing-portrait")
        assertTrue(narrowest >= 31f, "letter keys $narrowest dp wide")
        assertTrue(lowest >= 43f, "key rows $lowest dp high")
        assertTrue(header) // enough height: tabs and "Close" stay
    }

    /** M2: typing in landscape (640 × 200 dp): the header gives way too, letters are wider than 44 dp, rows at least 36 dp. */
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "xxhdpi")
    fun typingModeLandscape() {
        val (narrowest, lowest, header) = renderTyping(1920, 600, "charart-typing-landscape")
        assertTrue(narrowest >= 44f, "letter keys $narrowest dp wide")
        assertTrue(lowest >= CharArtView.KEY_ROW_MIN_DP.toFloat(), "key rows $lowest dp high")
        assertFalse(header)
    }

    /** M3: a fill is computed once, when the finger lifts, not while it moves. */
    @Test fun fillOnlyWhenTheFingerLifts() {
        pen()
        layoutWindow()
        val canvas = window.findViewById<CharArtCanvas>(R.id.agent_char_art_canvas)
        canvas.tool = CharArtCanvas.Tool.CIRCLE
        draw(0.2f to 0.2f, 0.8f to 0.8f)
        val before = canvas.raster.ink.values.count { it >= 0.5f }
        canvas.tool = CharArtCanvas.Tool.FILL
        val side = minOf(canvas.width, canvas.height).toFloat()
        val x = (canvas.width - side) / 2f + side / 2f; val y = (canvas.height - side) / 2f + side / 2f
        val time = SystemClock.uptimeMillis()
        canvas.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0))
        canvas.dispatchTouchEvent(MotionEvent.obtain(time, time + 10, MotionEvent.ACTION_MOVE, x + 5, y + 5, 0))
        assertEquals(before, canvas.raster.ink.values.count { it >= 0.5f }) // nothing filled yet
        assertEquals(1, window.sketch.ops.size)
        canvas.dispatchTouchEvent(MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_UP, x + 5, y + 5, 0))
        idle()
        assertEquals(2, window.sketch.ops.size)
        assertTrue(window.sketch.ops.last() is dev.agentkeyboard.core.charart.SketchOp.Fill)
        assertTrue(canvas.raster.ink.values.count { it >= 0.5f } > before)
        // a long stroke keeps at most MAX_POINTS points
        canvas.tool = CharArtCanvas.Tool.PEN
        canvas.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        for (i in 1..2_500) canvas.dispatchTouchEvent(MotionEvent.obtain(time, time + i, MotionEvent.ACTION_MOVE, (i % 300).toFloat() + 1f, (i / 300).toFloat() * 20f + 1f, 0))
        canvas.dispatchTouchEvent(MotionEvent.obtain(time, time + 3_000, MotionEvent.ACTION_UP, 1f, 1f, 0))
        val stroke = window.sketch.ops.last() as dev.agentkeyboard.core.charart.SketchOp.Stroke
        assertTrue(stroke.points.size <= dev.agentkeyboard.core.charart.Sketch.MAX_POINTS)
    }

    /** M4: a photo stored sideways with its orientation in MediaStore is turned upright. */
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun aSidewaysPhotoIsTurnedUpright() {
        val app = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://media/external/images/media/7")
        // stored 64 × 32 with the dark half on the left; shown upright it is 32 × 64 with the dark half on top
        val sideways = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (x in 0 until 32) for (y in 0 until 32) setPixel(x, y, Color.BLACK)
        }
        val bytes = ByteArrayOutputStream().also { sideways.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val resolver = shadowOf(app.contentResolver)
        // Robolectric's own media provider knows no orientation: ours answers for these two pictures
        Robolectric.setupContentProvider(OrientationProvider::class.java, "media")
        OrientationProvider.degrees = mapOf(uri to 90)
        resolver.registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        val upright = CharArtImages.Device(app).load(uri, 256)!!
        assertEquals(32, upright.width)
        assertEquals(64, upright.height)
        assertEquals(Color.BLACK, upright.getPixel(16, 8))
        assertEquals(Color.WHITE, upright.getPixel(16, 56))
        // without an orientation it stays as it is
        val plain = Uri.parse("content://media/external/images/media/8")
        resolver.registerInputStreamSupplier(plain) { ByteArrayInputStream(bytes) }
        val unchanged = CharArtImages.Device(app).load(plain, 256)!!
        assertEquals(64, unchanged.width)
        assertEquals(Color.BLACK, unchanged.getPixel(8, 16))
        // upside down: the dark half moves to the right
        assertEquals(Color.BLACK, CharArtImages.rotated(sideways, 180).getPixel(56, 16))
        assertSame(sideways, CharArtImages.rotated(sideways, 0))
    }

    /** N1/N14: the pen is also the emoji key's hint (it shows the long press); a short tap still opens the emojis. */
    @Test fun aShortTapOnTheEmojiKeyStillOpensTheEmojis() {
        startField("Hallo")
        val key = switcher.keyboard!!.getKey(KeyCode.EMOJI)!!
        assertEquals(CharArtKey.LABEL, key.hintLabel)
        val view = switcher.mainKeyboardView
        val x = (key.x + key.width / 2).toFloat()
        val y = (key.y + key.height / 2).toFloat()
        val down = SystemClock.uptimeMillis()
        view.onTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        view.onTouchEvent(MotionEvent.obtain(down, down + 60, MotionEvent.ACTION_UP, x, y, 0))
        idle()
        // isShowingEmojiPalettes needs a window (isShown); the switch itself shows in the views
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.emoji_palettes_view).visibility)
        assertNotEquals(View.VISIBLE, view.visibility)
        assertFalse(view.isShowingPopupKeysPanel)
        assertEquals(View.GONE, window.visibility)
        assertEquals("Hallo", field)
    }

    /** N2: "Replace" only while the cursor is right after the text read at opening and nothing follows it. */
    @Test fun replaceOnlyWhileTheFieldIsAsRead() {
        startField("Hallo")
        pen()
        assertTrue(shown(R.id.agent_char_art_replace))
        // the app adds text after the cursor while the window is open
        ShadowInputMethodService.text = "Hallo Welt"
        tap(R.id.agent_char_art_replace)
        assertEquals("Hallo Welt", field)
        assertFalse(shown(R.id.agent_char_art_replace))
        assertEquals(string(R.string.agent_char_art_expired), ShadowToast.getTextOfLatestToast())
        tap(R.id.agent_char_art_close)
        // opened while the editor's cursor is not where the keyboard expects it: no "Replace"
        startField("Hallo")
        ShadowInputMethodService.text = "Hallo!"
        ShadowInputMethodService.selectionStart = 6
        ShadowInputMethodService.selectionEnd = 6
        pen()
        assertEquals("Hallo!", window.text)
        assertFalse(shown(R.id.agent_char_art_replace))
    }

    /** N8: with a selection the window offers "Replace selection" (through the panel's capture) instead of "Insert". */
    @Test fun replacesTheSelection() {
        ShadowInputMethodService.text = "Hallo Welt"
        ShadowInputMethodService.selectionStart = 6
        ShadowInputMethodService.selectionEnd = 10
        ShadowInputMethodService.currentInputType = InputType.TYPE_CLASS_TEXT
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; initialSelStart = 6; initialSelEnd = 10 }
        latinIME.onStartInput(info, false)
        latinIME.onStartInputView(info, false)
        idle()
        pen()
        assertEquals("Welt", window.text)
        val replace = window.findViewById<TextView>(R.id.agent_char_art_replace)
        assertTrue(shown(R.id.agent_char_art_replace))
        assertEquals(string(R.string.agent_char_art_replace_selection), replace.text.toString())
        assertFalse(shown(R.id.agent_char_art_insert)) // nothing to insert at with a selection
        tap(string(R.string.agent_char_art_tab_text))
        tap(string(R.string.agent_char_art_mode_unicode))
        tap(R.id.agent_char_art_replace)
        assertEquals("Hallo 𝐖𝐞𝐥𝐭", field)
        assertTrue(window.offersUndo)
        assertTrue(shown(R.id.agent_char_art_insert))
        tap(R.id.agent_char_art_insert) // undo
        assertEquals("Hallo Welt", field)
        assertFalse(shown(R.id.agent_char_art_replace)) // the selection is gone: no second replacement
    }

    /** N7: art too large for one commit is refused with its own message, not "the field changed". */
    @Test fun tooLargeArtSaysSo() {
        startField("Hi ")
        pen()
        window.onInsert!!.invoke("⣿".repeat(controller.editor.policy.maxSelectionChars + 1))
        assertEquals("Hi ", field)
        assertEquals(latinIME.getString(R.string.agent_char_art_too_large, controller.editor.policy.maxSelectionChars),
            ShadowToast.getTextOfLatestToast())
        assertNotEquals(string(R.string.agent_char_art_expired), ShadowToast.getTextOfLatestToast())
    }

    /** MediaStore's orientation column for the pictures in [degrees]; null (unknown) for any other. */
    class OrientationProvider : ContentProvider() {
        companion object { var degrees: Map<Uri, Int> = emptyMap() }
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf("orientation")).apply { addRow(arrayOf<Any?>(degrees[uri])) }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private class CountingImages(val pictures: Int) : CharArtImages {
        var reads = 0
        override fun allowed() = true
        override fun recent(limit: Int): List<Uri> { reads++; return (1..pictures).map { Uri.parse("content://media/external/images/media/$it") } }
        override fun load(uri: Uri, maxPx: Int): Bitmap? = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    }

    /** N12: the strip is read once per opening; allowed but empty says so instead of asking for the permission. */
    @Test fun pictureStripIsReadOncePerOpening() {
        pen()
        val images = CountingImages(3)
        window.images = images
        window.background = { it.run() }
        tap(string(R.string.agent_char_art_tab_photo))
        idle()
        tap(string(R.string.agent_char_art_tab_draw))
        tap(string(R.string.agent_char_art_tab_photo))
        idle()
        assertEquals(1, images.reads)
        assertEquals(3, (window.findViewById<ViewGroup>(R.id.agent_char_art_photos).let { views(it) }.count { it is android.widget.ImageView }))
        tap(R.id.agent_char_art_close)
        pen()
        window.images = images
        window.background = { it.run() }
        tap(string(R.string.agent_char_art_tab_photo))
        idle()
        assertEquals(2, images.reads) // opened again: read again
        tap(R.id.agent_char_art_close)
        pen()
        window.images = CountingImages(0)
        window.background = { it.run() }
        tap(string(R.string.agent_char_art_tab_photo))
        idle()
        val notes = views(window.findViewById(R.id.agent_char_art_photos)).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(string(R.string.agent_char_art_photo_empty) in notes, "$notes")
        assertFalse(string(R.string.agent_char_art_photo_none) in notes)
        assertFalse(string(R.string.agent_char_art_photo_allow) in notes)
    }

    /** N13: Shift and ß give ẞ; the width is written when the finger lifts; TalkBack names the agent keys. */
    @Test fun capitalSharpSWidthOnLiftAndTalkBack() {
        // the German keys of the window (the test keyboard is English)
        val keys = CharArtKeys(latinIME).apply { german = true }
        var typed = ""
        keys.onText = { typed += it }
        fun tapKey(label: String) = views(keys).filterIsInstance<TextView>().first { it.text.toString() == label }.performClick()
        tapKey("⇧")
        tapKey("ẞ")
        tapKey("ß") // shift ends after one letter
        assertEquals("ẞß", typed)
        pen()
        // the slider: dragging renders, lifting writes the width once
        tap(string(R.string.agent_char_art_tab_app))
        val bar = window.findViewById<SeekBar>(R.id.agent_char_art_width)
        val listener = shadowOf(bar).onSeekBarChangeListener
        val key = AgentPreferences.CHAR_ART_WIDTH_PREFIX + TargetApp.GENERIC.name.lowercase()
        listener.onStartTrackingTouch(bar)
        for (width in 20..30) listener.onProgressChanged(bar, width - dev.agentkeyboard.core.charart.CharArt.MIN_WIDTH, true)
        assertTrue(window.result.columns <= 30)
        assertFalse(latinIME.prefs().contains(key))
        listener.onStopTrackingTouch(bar)
        assertEquals(30, latinIME.prefs().getInt(key, 0))
        // TalkBack
        val mapper = helium314.keyboard.accessibility.KeyCodeDescriptionMapper.instance
        assertEquals(string(R.string.agent_char_art_key_description), mapper.getDescriptionForCodePoint(latinIME, KeyCode.AGENT_CHAR_ART))
        assertEquals(string(R.string.dictate), mapper.getDescriptionForCodePoint(latinIME, KeyCode.AGENT_DICTATE))
        assertEquals(string(R.string.agent), mapper.getDescriptionForCodePoint(latinIME, KeyCode.AGENT_ACTIONS))
        assertEquals(string(R.string.agent_emoji_mixer_key), mapper.getDescriptionForCodePoint(latinIME, KeyCode.AGENT_EMOJI_MIXER))
    }
}
