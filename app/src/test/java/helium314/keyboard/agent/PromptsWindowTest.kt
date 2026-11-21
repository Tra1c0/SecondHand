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
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.prefs
import org.junit.After
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
        ClipVault.clear()
        root = switcher.onCreateInputView(latinIME, true)
        latinIME.setInputView(root)
        startField("")
    }

    @After fun tearDown() { ClipVault.clear() }

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
        assertEquals(string(R.string.agent_prompts_key_description),
            KeyCodeDescriptionMapper.instance.getDescriptionForCodePoint(latinIME, KeyCode.AGENT_PROMPTS))
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

    /** A clip whose text would be stored on a tap; opening the window alone must not store it (a read cannot be observed in Robolectric). */
    @Test fun openingTheWindowDoesNotSaveTheClipboard() {
        copy("geheimnisvoller Text")
        icon()
        assertEquals(View.VISIBLE, window.visibility)
        assertTrue(store.read().isEmpty()) // opening did not save the clip
        idle()
        assertTrue(store.read().isEmpty())
        tap(R.id.agent_prompts_save_clip) // only the tap saves it
        assertEquals(listOf("geheimnisvoller Text"), store.read().map { it.text })
    }

    // ---- the clipboard history's rule (review of 74b) ----

    private fun clipStamp() = helium314.keyboard.compat.ClipboardManagerCompat.getClipTimestamp(clipboard.primaryClip!!)

    @Test fun aClipTheVaultSortedAsSecretWhenCopiedIsRefusedLaterInANormalField() {
        icon()
        copy("harmloser Satz im Passwortfeld kopiert")
        // what the history does on the copy in a password field: offer it to the vault as marked
        ClipVault.offer("harmloser Satz im Passwortfeld kopiert", true, clipStamp(), "DE", true)
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_clip_sensitive), status())
        assertTrue(store.read().isEmpty())
    }

    @Test fun aClipTheSystemMarkedSensitiveIsRefused() {
        icon()
        val clip = ClipData.newPlainText("t", "ein ganz normaler Satz")
        clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_clip_sensitive), status())
        assertTrue(store.read().isEmpty())
    }

    // put together so that the file holds no key-shaped string; not real secrets
    private val secretSamples = mapOf(
        "password" to "x7#Kq9" + "!mZ",
        "api key" to "sk" + "-proj-" + "Ab3dE5fG7hJ9kL1mN3pQ5rS7tU9vW1xY3z" + "7f3a",
        "token" to "Zq8Lm2Xv9R" + "t4Pk7Wn3Bs6Hd1",
        "private key" to "-----BEGIN RSA PRIV" + "ATE KEY-----\nMIIEabc\n-----END RSA PRIV" + "ATE KEY-----",
        "jwt" to "eyJhbGciOiJI" + "UzI1NiJ9.eyJzdWIiOiIx" + "MjM0NTY3ODkwIn0.abcdefghij" + "klmnop",
        "one-time code" to "482915",
        "one-time code with spaces" to "482 915",
    )

    @Test fun everyCategoryTheHistoryKeepsOutIsRefusedHere() {
        icon()
        for ((name, sample) in secretSamples) {
            assertTrue(dev.agentkeyboard.core.clip.ClipGuard.classify(sample).let { it.keepsOutOfHistory }, "$name is kept out of the history")
            copy(sample)
            tap(R.id.agent_prompts_save_clip)
            assertEquals(string(R.string.agent_prompts_clip_sensitive), status(), name)
            assertTrue(store.read().isEmpty(), name)
        }
    }

    @Test fun aNormalSentenceIsStored() {
        icon()
        copy("Fasse den folgenden Text in drei Sätzen zusammen.")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_saved), status())
        assertEquals(listOf("Fasse den folgenden Text in drei Sätzen zusammen."), store.read().map { it.text })
    }

    @Test fun aBareOneTimeCodeIsRefused() {
        icon()
        copy("123456")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_clip_sensitive), status())
        assertTrue(store.read().isEmpty())
    }

    /** The history keeps such texts, and prompts about code with a number in them are everyday prompts. */
    @Test fun aPromptThatMentionsCodeAndANumberIsStoredLikeTheHistoryKeepsIt() {
        val samples = listOf(
            "Write Python code that prints 10000 random numbers",
            "Refactor this code and keep it under 2000 lines",
            "Dein Code lautet 123456, gib ihn niemandem weiter.",
        )
        for ((index, sample) in samples.withIndex()) {
            assertTrue(!dev.agentkeyboard.core.clip.ClipGuard.classify(sample).keepsOutOfHistory, sample)
            icon()
            copy(sample)
            tap(R.id.agent_prompts_save_clip)
            assertEquals(index + 1, store.read().size, sample)
            assertEquals(sample, store.read().first().text)
        }
    }

    // ---- saving the same text twice ----

    @Test fun savingTheNewestPromptsTextAgainAddsNothing() {
        store.add("alt", "alt")
        icon()
        copy("Neuer Text")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_saved), status())
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_already_saved), status())
        assertEquals(listOf("Neuer Text", "alt"), store.read().map { it.text })
        // a text that is only in second place is added again
        copy("alt")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(listOf("alt", "Neuer Text", "alt"), store.read().map { it.text })
    }

    // ---- guards on the tap on a row (review of 74b) ----

    @Test fun aFieldChangeClosesTheWindowAndAStaleRowCannotInsert() {
        store.add("T", "eingefügt")
        startField("Hallo ")
        icon()
        val row = rows().single()
        startField("anders")
        assertEquals(View.GONE, window.visibility)
        assertFalse(controller.isBlockingInput)
        row.performClick()
        idle()
        assertEquals("anders", field)
    }

    @Test fun aLockAfterOpeningMeansATapInsertsNothing() {
        store.add("T", "eingefügt")
        startField("Hallo ")
        icon()
        lock(true)
        rows().single().performClick()
        idle()
        assertEquals("Hallo ", field)
        assertEquals(View.GONE, window.visibility)
    }

    @Test fun aDoubleTapInsertsOnce() {
        store.add("T", "X")
        startField("")
        icon()
        val row = rows().single()
        row.performClick()
        row.performClick()
        idle()
        assertEquals("X", field)
    }

    @Test fun aRowOfAPromptDeletedMeanwhileInsertsNothingAndSaysSo() {
        store.add("T", "weg")
        store.add("U", "bleibt")
        startField("Hallo ")
        icon()
        val gone = store.read().first { it.text == "weg" }
        val row = rows().first { it.tag == gone.id }
        assertTrue(store.delete(gone.id).ok)
        row.performClick()
        idle()
        assertEquals("Hallo ", field)
        assertEquals(View.VISIBLE, window.visibility)
        assertEquals(string(R.string.agent_prompts_gone), status())
        assertEquals(listOf("bleibt"), rowTexts().map { it[1] })
    }

    @Test fun theWindowDoesNotOpenInAnyPasswordVariation() {
        store.add("T", "x")
        for (type in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )) {
            startField("", type)
            assertFalse(controller.openPrompts(), "type $type")
            icon()
            assertEquals(View.GONE, window.visibility, "type $type")
            assertFalse(controller.isBlockingInput, "type $type")
        }
        startField("")
        assertTrue(controller.openPrompts())
    }

    // ---- stage 79b: number layouts ----

    @Test fun theWindowDoesNotOpenInANumberLayoutAndNoNoticeIsShown() {
        store.add("T", "x")
        startField("", InputType.TYPE_CLASS_NUMBER)
        assertTrue(switcher.keyboard!!.mId.element.isNumberLayout)
        ShadowToast.reset()
        assertFalse(controller.openPrompts())
        icon()
        assertEquals(View.GONE, window.visibility)
        assertFalse(controller.isBlockingInput)
        assertNull(ShadowToast.getLatestToast())
        startField("")
        assertTrue(controller.openPrompts())
    }

    @Test fun theBarsStoreToolFollowsTheSwitchToTheNumpadWithoutAStripUpdate() {
        latinIME.prefs().edit(commit = true) {
            putString(AgentPreferences.BAR_TOOLS, helium314.keyboard.latin.utils.BarTools.format(
                listOf(helium314.keyboard.latin.utils.ToolbarKey.PROMPTS, helium314.keyboard.latin.utils.ToolbarKey.UNDO)))
        }
        // the strip reads the settings when it is created: a fresh input view after the bar was chosen
        Settings.getInstance().loadSettings(latinIME, java.util.Locale.GERMANY, helium314.keyboard.latin.InputAttributes(EditorInfo(), false, latinIME.packageName))
        root = switcher.onCreateInputView(latinIME, true)
        latinIME.setInputView(root)
        startField("")
        val strip = root.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
        fun storeTool() = strip.findViewById<ViewGroup>(R.id.toolbar).findViewWithTag<View>(helium314.keyboard.latin.utils.ToolbarKey.PROMPTS)
        assertNotNull(storeTool(), "the bar has the store tool")
        assertNull(helium314.keyboard.latin.suggestions.FixedBar.reasonOf(storeTool()))
        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.NUMPAD, -1, -1, false)
        assertTrue(switcher.keyboard!!.mId.element.isNumberLayout, "the numpad is shown")
        assertNotNull(helium314.keyboard.latin.suggestions.FixedBar.reasonOf(storeTool()), "dimmed at once")
        latinIME.mKeyboardActionListener.onCodeInput(KeyCode.NUMPAD, -1, -1, false) // back
        assertFalse(switcher.keyboard!!.mId.element.isNumberLayout)
        assertNull(helium314.keyboard.latin.suggestions.FixedBar.reasonOf(storeTool()), "available again")
    }

    // ---- PromptsKey.shown ----

    @Test fun thePopupKeyIsShownOnlyWhereTheWindowCanOpen() {
        val id = switcher.keyboard!!.mId
        assertTrue(helium314.keyboard.agent.prompts.PromptsKey.shown(id))
        assertFalse(helium314.keyboard.agent.prompts.PromptsKey.shown(id.copy(deviceLocked = true)), "locked")
        assertFalse(helium314.keyboard.agent.prompts.PromptsKey.shown(
            id.copy(inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)), "password")
        assertFalse(helium314.keyboard.agent.prompts.PromptsKey.shown(
            id.copy(element = helium314.keyboard.keyboard.KeyboardElement.NUMBER)), "number layout")
        assertFalse(helium314.keyboard.agent.prompts.PromptsKey.shown(
            id.copy(imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)), "no personalized learning")
    }

    // ---- stage 77: folders ----

    @Before fun forgetTheRememberedChip() { PromptsView.rememberedFilter = PromptFilter.All }

    private fun chipRow() = window.findViewById<View>(R.id.agent_prompts_chips).parent as View
    private fun chips() = window.findViewById<ViewGroup>(R.id.agent_prompts_chips).let { row -> (0 until row.childCount).map { row.getChildAt(it) as TextView } }
    private fun chipTexts() = chips().map { it.text.toString() }
    private fun selectedChip() = chips().filter { it.isSelected }.map { it.text.toString() }
    private fun tapChip(text: String) { chips().single { it.text.toString() == text }.performClick(); idle() }
    private fun shownTitles() = store.read().filter { it.id in window.shownIds }.map { it.title }
    private fun folder(name: String) = store.addFolder(name).folder!!
    private fun promptIn(title: String, folder: PromptFolder?) = store.add(title, "text of $title", folder?.id).prompt!!

    @Test fun withoutFoldersThereIsNoChipRowAndTheWindowLooksAsBefore() {
        promptIn("a", null); promptIn("b", null)
        icon()
        assertEquals(View.GONE, chipRow().visibility)
        assertTrue(chips().isEmpty())
        assertEquals(store.read().map { it.id }, window.shownIds)
        assertEquals(PromptFilter.All, window.filter)
        assertNotNull(window.findViewById<View>(R.id.agent_prompts_save_clip))
        // with no prompt either: the old empty text, no chips
        tap(R.id.agent_prompts_close)
        storeFile.delete()
        icon()
        assertEquals(View.GONE, chipRow().visibility)
        assertEquals(string(R.string.agent_prompts_window_empty), (window.findViewById<TextView>(R.id.agent_prompts_empty)).text.toString())
    }

    @Test fun theChipsAreAllEachFolderInTheUsersOrderAndNoFolderOnlyWhenSomePromptHasNone() {
        val a = folder("Arbeit"); val b = folder("Privat")
        promptIn("p1", a)
        icon()
        assertEquals(View.VISIBLE, chipRow().visibility)
        assertEquals(listOf(string(R.string.agent_prompts_chip_all), "Arbeit", "Privat"), chipTexts(), "no prompt without folder: no such chip")
        assertEquals(listOf(string(R.string.agent_prompts_chip_all)), selectedChip())
        tap(R.id.agent_prompts_close)
        promptIn("p2", null)
        store.reorderFolders(listOf(b.id, a.id))
        icon()
        assertEquals(listOf(string(R.string.agent_prompts_chip_all), "Privat", "Arbeit", string(R.string.agent_prompts_chip_none)), chipTexts())
        // accessibility: every chip speaks, the selected one says so
        assertTrue(chips().all { it.isClickable && it.contentDescription?.isNotEmpty() == true })
        assertEquals(latinIME.getString(R.string.agent_prompts_filter_folder_description, "Arbeit"), chips()[2].contentDescription)
        assertEquals(string(R.string.agent_prompts_filter_none_description), chips()[3].contentDescription)
        assertEquals(string(R.string.agent_prompts_state_selected), androidx.core.view.ViewCompat.getStateDescription(chips()[0]))
        assertEquals(null, androidx.core.view.ViewCompat.getStateDescription(chips()[1]))
    }

    @Test fun aTapOnAChipFiltersTheListInTheStoresOrder() {
        val a = folder("A"); val b = folder("B"); val empty = folder("Leer")
        promptIn("a1", a); promptIn("b1", b); promptIn("n1", null); promptIn("a2", a)
        icon()
        assertEquals(listOf("a2", "n1", "b1", "a1"), shownTitles())
        tapChip("A")
        assertEquals(listOf("a2", "a1"), shownTitles())
        assertEquals(listOf("A"), selectedChip())
        assertEquals(PromptFilter.Folder(a.id), window.filter)
        tapChip("B")
        assertEquals(listOf("b1"), shownTitles())
        tapChip(string(R.string.agent_prompts_chip_none))
        assertEquals(listOf("n1"), shownTitles())
        tapChip("Leer")
        assertEquals(emptyList(), window.shownIds)
        assertEquals(string(R.string.agent_prompts_folder_empty), window.findViewById<TextView>(R.id.agent_prompts_empty).text.toString())
        assertNotNull(window.findViewById<View>(R.id.agent_prompts_save_clip), "an empty folder still has the save row")
        tapChip(string(R.string.agent_prompts_chip_all))
        assertEquals(listOf("a2", "n1", "b1", "a1"), shownTitles())
        assertEquals(listOf(empty.name, a.name, b.name).sorted(), chipTexts().drop(1).dropLast(1).sorted())
    }

    @Test fun aTapOnARowOfAFilteredListInsertsThatPromptOnce() {
        val a = folder("A")
        promptIn("x", null)
        val p = store.add("t", "Text im Ordner", a.id).prompt!!
        startField("Hallo ")
        icon()
        tapChip("A")
        assertEquals(listOf(p.id), rows().map { it.tag })
        rows().single().performClick()
        idle()
        assertEquals("Hallo Text im Ordner", field)
        assertEquals(View.GONE, window.visibility)
    }

    @Test fun saveCopiedTextGoesIntoTheFolderShown() {
        val a = folder("Arbeit"); val b = folder("Privat")
        promptIn("n", null)
        icon()
        // all: no folder
        copy("Text eins")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_saved), status())
        assertEquals(null, store.read().first().folderId)
        // a folder: into that folder, first in it, and the window shows it
        tapChip("Arbeit")
        copy("Text zwei")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(latinIME.getString(R.string.agent_prompts_saved_in_folder, "Arbeit"), status())
        val saved = store.read().first()
        assertEquals("Text zwei", saved.text)
        assertEquals(a.id, saved.folderId)
        assertEquals("", saved.title)
        assertEquals(listOf(saved.id), window.shownIds)
        // "already saved" is judged by the list shown: the same text in the other folder is new there
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_already_saved), status())
        tapChip("Privat")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(latinIME.getString(R.string.agent_prompts_saved_in_folder, "Privat"), status())
        assertEquals(listOf(b.id, a.id, null, null), store.read().map { it.folderId })
        // "no folder": no folder
        tapChip(string(R.string.agent_prompts_chip_none))
        copy("Text drei")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_saved), status())
        assertEquals(null, store.read().first().folderId)
        assertEquals("Text drei", store.read().first().text)
    }

    @Test fun theChosenChipIsRememberedWhileTheProcessLivesAndNeverSaved() {
        val a = folder("A"); promptIn("a1", a); promptIn("n1", null)
        val fileBefore = storeFile.readBytes()
        val prefsBefore = latinIME.prefs().all.toMap()
        icon()
        tapChip("A")
        tap(R.id.agent_prompts_close)
        icon()
        assertEquals(listOf("A"), selectedChip())
        assertEquals(listOf("a1"), shownTitles())
        // a new window view (the keyboard view was rebuilt) starts on it too
        val fresh = PromptsView(window.context)
        fresh.show(store.loadLibrary())
        assertEquals(PromptFilter.Folder(a.id), fresh.filter)
        // nothing was written for it: not the prompt file, not the settings
        assertTrue(fileBefore.contentEquals(storeFile.readBytes()))
        assertEquals(prefsBefore, latinIME.prefs().all.toMap())
        // the folder is deleted meanwhile: the window opens on "all"
        tap(R.id.agent_prompts_close)
        store.deleteFolder(a.id)
        icon()
        assertEquals(PromptFilter.All, window.filter)
        assertEquals(2, window.shownIds.size)
        assertEquals(View.GONE, chipRow().visibility, "and with no folder left the chip row is gone")
    }

    @Test fun noFolderIsRememberedOnlyWhileSomePromptHasNone() {
        val a = folder("A"); val p = promptIn("n", null); promptIn("a", a)
        icon()
        tapChip(string(R.string.agent_prompts_chip_none))
        tap(R.id.agent_prompts_close)
        store.update(p.id, "n", "text of n", a.id) // nothing is without folder any more
        icon()
        assertEquals(PromptFilter.All, window.filter)
        assertFalse(chipTexts().contains(string(R.string.agent_prompts_chip_none)))
    }

    @Test fun aChipTapAfterTheFieldBecameProtectedClosesTheWindowAndReadsNothing() {
        val a = folder("A"); promptIn("a1", a)
        icon()
        val chip = chips().first { it.text.toString() == "A" }
        startField("", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        // a field change closes the window; a stale chip of it must change nothing, not even the remembered filter
        assertEquals(View.GONE, window.visibility)
        assertEquals(PromptFilter.All, PromptsView.rememberedFilter)
        chip.performClick()
        idle()
        assertEquals(View.GONE, window.visibility)
        assertEquals(PromptFilter.All, PromptsView.rememberedFilter, "a tap in a hidden window moved the remembered filter")
        assertEquals(PromptFilter.All, window.filter)
        // and a lock after opening closes it on the tap
        startField("")
        icon()
        lock(true)
        tapChip("A") // a real change of the filter, so the tap goes on to the guard
        assertEquals(View.GONE, window.visibility)
        assertEquals(string(R.string.agent_prompts_protected), ShadowToast.getTextOfLatestToast())
    }

    @Test fun savingIntoAFolderStillNeedsAReadableClipAndRefusesSecretsAndLockedFields() {
        val a = folder("A")
        icon()
        tapChip("A")
        copy("482915")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_clip_sensitive), status())
        assertTrue(store.read().isEmpty())
        copy("ein Satz")
        lock(true)
        tap(R.id.agent_prompts_save_clip)
        assertEquals(View.GONE, window.visibility)
        lock(false)
        assertTrue(store.read().isEmpty())
        assertEquals(listOf(a.id), store.readFolders().map { it.id })
    }

    @Test fun aFolderDeletedWhileTheWindowIsOpenSavesNothingAndSaysSoAndTheWindowFallsBackToAll() {
        val a = folder("A")
        promptIn("n", null)
        icon()
        tapChip("A")
        store.deleteFolder(a.id)
        copy("ein Satz")
        tap(R.id.agent_prompts_save_clip)
        assertEquals(string(R.string.agent_prompts_folder_gone), status())
        assertEquals(listOf("n"), store.read().map { it.title }, "the clip was not put anywhere else")
        assertEquals(PromptFilter.All, window.filter)
        assertEquals(View.GONE, chipRow().visibility)
    }
}
