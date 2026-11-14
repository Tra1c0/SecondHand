// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.edit
import dev.agentkeyboard.core.charart.TargetApp
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.agent.charart.CharArtCanvas
import helium314.keyboard.agent.charart.CharArtKey
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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
        assertEquals(window.result.lines.joinToString("\n"), preview.text.toString())
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
        assertEquals("𝐇𝐚𝐥𝐥𝐨", window.result.text.removeSurrounding("```")) // the text styles of stage 39
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
}
