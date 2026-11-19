// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.accessibility.KeyCodeDescriptionMapper
import helium314.keyboard.agent.commands.SensitiveClips
import helium314.keyboard.agent.prompts.PromptsView
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
import org.robolectric.shadows.ShadowToast
import java.io.File
import kotlin.test.*

/** Stage 74b: the prompt window opened by the icon in the comma key's popup, with a simulated field. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowInputMethodService::class])
class PromptsWindowTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val switcher = KeyboardSwitcher.getInstance()
    private lateinit var root: View
    private val window: PromptsView get() = root.findViewById(R.id.agent_prompts)
    private val controller get() = latinIME.agentEditorController
    private val field get() = ShadowInputMethodService.text
    private val store get() = PromptStore(latinIME.applicationContext)
    private val storeFile get() = File(helium314.keyboard.agent.security.credentialContext(latinIME.applicationContext).noBackupFilesDir, PromptStore.FILE_NAME)
    private val clipboard get() = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Before fun setUp() {
        ShadowInputMethodService.reset()
        storeFile.delete(); File(storeFile.path + ".bak").delete()
        shadowOf(latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(false)
        latinIME.prefs().edit(commit = true) { clear() }
        root = switcher.onCreateInputView(latinIME, true)
        latinIME.setInputView(root)
        startField("")
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun startField(text: String, inputType: Int = InputType.TYPE_CLASS_TEXT, imeOptions: Int = 0) {
        ShadowInputMethodService.text = text
        ShadowInputMethodService.selectionStart = text.length
        ShadowInputMethodService.selectionEnd = text.length
        ShadowInputMethodService.currentInputType = inputType
        ShadowInputMethodService.currentImeOptions = imeOptions
        val info = EditorInfo().apply {
            this.inputType = inputType; this.imeOptions = imeOptions
            initialSelStart = text.length; initialSelEnd = text.length
        }
        latinIME.onStartInput(info, false)
        latinIME.onStartInputView(info, false)
        idle()
        switcher.reloadMainKeyboard()
    }

    private fun lock(locked: Boolean) = shadowOf(latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(locked)
    private fun icon() = latinIME.mKeyboardActionListener.onCodeInput(KeyCode.AGENT_PROMPTS, -1, -1, false)
    private fun commaCodes(): List<Int>? = switcher.keyboard!!.sortedKeys.firstOrNull { it.code == ','.code }?.popupKeys?.map { it.mCode }
    private fun views(group: View = window): List<View> = when (group) {
        is ViewGroup -> listOf(group) + (0 until group.childCount).flatMap { views(group.getChildAt(it)) }
        else -> listOf(group)
    }
    private fun rows() = views().filter { it.tag is String && it.hasOnClickListeners() }
    private fun rowTexts() = rows().map { r -> views(r).filterIsInstance<TextView>().map { it.text.toString() } }
    private fun tap(id: Int) = window.findViewById<View>(id).performClick()
    private fun status() = window.findViewById<TextView>(R.id.agent_prompts_status).let { if (it.visibility == View.VISIBLE) it.text.toString() else "" }
    private fun string(id: Int) = latinIME.getString(id)
    private fun copy(text: String) = clipboard.setPrimaryClip(ClipData.newPlainText("t", text))
    private fun ids() = store.read().map { it.id }

    // ---- the popup key ----

    @Test fun theCommaPopupHasThePromptsKeyRightAfterTheClipboardKey() {
        val codes = commaCodes()
        assertNotNull(codes)
        val clipboardAt = codes.indexOf(KeyCode.CLIPBOARD)
        assertTrue(clipboardAt >= 0)
        assertEquals(KeyCode.AGENT_PROMPTS, codes[clipboardAt + 1])
        val key = switcher.keyboard!!.sortedKeys.first { it.code == ','.code }.popupKeys!!.first { it.mCode == KeyCode.AGENT_PROMPTS }
        assertEquals("agent_prompts_key", key.mIconName)
        assertNotNull(helium314.keyboard.keyboard.internal.KeyboardIconsSet.instance.getIconDrawable("agent_prompts_key"), "the icon exists")
    }

    @Test fun theKeyIsHiddenWhenLockedInPasswordFieldsNumberLayoutsAndWithoutLearning() {
        lock(true)
        startField("")
        assertFalse(commaCodes().orEmpty().contains(KeyCode.AGENT_PROMPTS))
        lock(false)
        startField("", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertFalse(commaCodes().orEmpty().contains(KeyCode.AGENT_PROMPTS))
        startField("", InputType.TYPE_CLASS_NUMBER)
        assertNotNull(commaCodes(), "the number layout has a comma key")
        assertFalse(commaCodes()!!.contains(KeyCode.AGENT_PROMPTS))
        startField("", imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertFalse(commaCodes().orEmpty().contains(KeyCode.AGENT_PROMPTS))
        startField("")
        assertTrue(commaCodes()!!.contains(KeyCode.AGENT_PROMPTS))
    }

    @Test fun theKeyCodeOpensTheWindowAndIsDescribed() {
        assertEquals(View.GONE, window.visibility)
        icon()
        assertEquals(View.VISIBLE, window.visibility)
        assertTrue(controller.isBlockingInput)
        assertNotEquals(KeyCodeDescriptionMapper.getInstance().getDescription(latinIME, KeyCode.AGENT_PROMPTS) ?: "", "")
        tap(R.id.agent_prompts_close)
        assertEquals(View.GONE, window.visibility)
        assertFalse(controller.isBlockingInput)
    }

    // ---- the list ----

    @Test fun rowsAreInTheStoresOrderWithTitleAndPreview() {
        store.add("Erste", "eins")
        store.add("", "Zeile A\nZeile B")
        store.add("Dritte", "drei")
        val order = ids()
        icon()
        assertEquals(order, window.shownIds)
        assertEquals(order, rows().map { it.tag })
        val texts = rowTexts()
        assertEquals(listOf("Dritte", "drei"), texts[0])
        assertEquals("Zeile A", texts[1][0]) // no title: the first line
        assertEquals("Zeile A\nZeile B", texts[1][1])
    }

    @Test fun aTapOnARowInsertsExactlyTheStoredTextOnceAndCloses() {
        val text = "Du bist ein Helfer.\n\n  - Punkt 1 😀\n- Punkt 2\t"
        store.add("T", text)
        startField("Hallo ")
        icon()
        assertEquals("Hallo ", field) // opening inserts nothing
        rows().single().performClick()
        idle()
        assertEquals("Hallo $text", field)
        assertEquals(View.GONE, window.visibility)
        assertFalse(controller.isBlockingInput)
    }

    @Test fun aSelectionIsNotReplaced() {
        store.add("T", "neu")
        startField("abc")
        ShadowInputMethodService.selectionStart = 0
        ShadowInputMethodService.selectionEnd = 3
        latinIME.onUpdateSelection(3, 3, 0, 3, -1, -1)
        icon()
        rows().single().performClick()
        assertEquals("abc", field)
        assertEquals(View.VISIBLE, window.visibility)
        assertEquals(string(R.string.agent_prompts_selection), status())
    }

    @Test fun emptyStateAndUnusableStore() {
        icon()
        assertNotNull(window.findViewById(R.id.agent_prompts_empty))
        assertEquals(string(R.string.agent_prompts_window_empty), window.findViewById<TextView>(R.id.agent_prompts_empty).text.toString())
        assertNotNull(window.findViewById(R.id.agent_prompts_save_clip))
        assertNotNull(window.findViewById(R.id.agent_prompts_manage))
        tap(R.id.agent_prompts_close)
        storeFile.parentFile!!.mkdirs(); storeFile.writeText("no json")
        icon()
        assertEquals(string(R.string.agent_prompts_window_unavailable), window.findViewById<TextView>(R.id.agent_prompts_empty).text.toString())
        assertNull(window.findViewById<View>(R.id.agent_prompts_save_clip))
        assertNotNull(window.findViewById(R.id.agent_prompts_manage))
        assertEquals("no json", storeFile.readText()) // opening never changes the file
    }

    @Test fun manageOpensTheSettingsPageButNotWhenLocked() {
        icon()
        tap(R.id.agent_prompts_manage)
        val started = shadowOf(latinIME.application).nextStartedActivity
        assertEquals("agent_prompts", started.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertEquals(View.GONE, window.visibility)
        icon()
        lock(true)
        tap(R.id.agent_prompts_manage)
        assertNull(shadowOf(latinIME.application).nextStartedActivity)
    }

    // ---- refusals to open ----

    @Test fun doesNotOpenWhenLockedInPasswordFieldsOrIncognito() {
        store.add("T", "x")
        lock(true)
        assertFalse(controller.openPrompts())
        assertEquals(View.GONE, window.visibility)
        lock(false)
        startField("", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        assertFalse(controller.openPrompts())
        assertEquals(View.GONE, window.visibility)
        assertEquals(string(R.string.agent_prompts_protected), ShadowToast.getTextOfLatestToast())
        startField("")
        latinIME.prefs().edit(commit = true) { putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true) }
        startField("")
        icon()
        assertEquals(View.GONE, window.visibility)
        latinIME.prefs().edit(commit = true) { putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false) }
        startField("")
        assertTrue(controller.openPrompts())
    }

    // ---- save copied text ----

    @Test fun saveCopiedTextAddsAtTheTopAndRefreshesTheList() {
        store.add("alt", "alt")
        icon()
        copy("Neuer Prompt\nZeile 2")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_saved), status())
        val read = store.read()
        assertEquals(listOf("Neuer Prompt\nZeile 2", "alt"), read.map { it.text })
        assertEquals("", read[0].title)
        assertEquals(read.map { it.id }, window.shownIds)
    }

    @Test fun saveCopiedTextRefusesEmptyNotTextTooLongFullAndSecretClips() {
        icon()
        // nothing copied
        clipboard.setPrimaryClip(ClipData.newPlainText("t", ""))
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_paste_empty), status())
        // not text
        clipboard.setPrimaryClip(ClipData.newUri(latinIME.contentResolver, "img", android.net.Uri.parse("content://x/1")))
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_paste_empty), status())
        assertTrue(store.read().isEmpty())
        // too long
        copy("a".repeat(PromptStore.MAX_TEXT + 1))
        tap(R.id.agent_prompts_save_clip)
        assertEquals(latinIME.getString(R.string.agent_prompts_clip_too_long, PromptStore.MAX_TEXT), status())
        assertTrue(store.read().isEmpty())
        // a secret by its content
        copy("-----BEGIN RSA PRIVATE KEY-----\nMIIEabc\n-----END RSA PRIVATE KEY-----")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_clip_sensitive), status())
        assertTrue(store.read().isEmpty())
        // copied in a password field (remembered by the keyboard)
        SensitiveClips.remember("harmlos aussehender Text")
        copy("harmlos aussehender Text")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_clip_sensitive), status())
        assertTrue(store.read().isEmpty())
        // a normal text works, and a full store refuses
        copy("normal")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(1, store.read().size)
        repeat(PromptStore.MAX_PROMPTS - 1) { store.add("", "p$it") }
        copy("zu viel")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(latinIME.getString(R.string.agent_prompts_full, PromptStore.MAX_PROMPTS), status())
        assertEquals(PromptStore.MAX_PROMPTS, store.read().size)
        assertFalse(store.read().any { it.text == "zu viel" })
    }

    @Test fun theClipboardIsReadOnlyOnTheTap() {
        copy("geheimnisvoller Text")
        icon()
        assertEquals(View.VISIBLE, window.visibility)
        assertTrue(store.read().isEmpty()) // opening did not save the clip
    }
}
