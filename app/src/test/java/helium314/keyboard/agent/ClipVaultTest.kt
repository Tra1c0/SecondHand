// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.agentkeyboard.core.clip.ClipCategory
import dev.agentkeyboard.core.clip.ClipGuard
import dev.agentkeyboard.core.clip.ClipKind
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.clipboard.ClipboardHistoryView
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Clipboard vault (stage 44): memory only, masked, expiring, never in the history database. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class ClipVaultTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val manager get() = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val keyguard get() = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    // Robolectric stamps clips with SystemClock.uptimeMillis() (a device uses the wall clock), so the vault clock follows it
    private var now = android.os.SystemClock.uptimeMillis()
    /** Wall clock for history entries, whose retention is checked against the wall clock. */
    private val wall = System.currentTimeMillis()
    private val textField = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }

    // put together so that the file holds no key-shaped string; not a real key
    private val key = "sk" + "-proj-" + "Ab3dE5fG7hJ9kL1mN3pQ5rS7tU9vW1xY3z" + "7f3a"

    @Before fun setUp() {
        latinIME.prefs().edit().clear().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        load(textField)
        ClipVault.clear()
        ClipVault.clock = { now }
        ClipboardDao.getInstance(latinIME)?.clear()
    }

    @After fun tearDown() {
        ClipVault.clear()
        ClipVault.clock = { System.currentTimeMillis() }
        ClipVault.onChange = null
        shadowOf(keyguard).setIsDeviceLocked(false)
        ClipboardDao.getInstance(latinIME)?.clear()
        latinIME.prefs().edit().clear().commit()
        load(textField)
    }

    private fun load(editor: EditorInfo, incognito: Boolean = false) {
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, incognito).commit()
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editor, false, latinIME.packageName))
    }

    private fun offer(text: String, copiedAt: Long = now, marked: Boolean = false, allowed: Boolean = true) =
        ClipVault.offer(text, marked, copiedAt, "DE", allowed)

    private fun copy(text: String, sensitive: Boolean = false) = manager.setPrimaryClip(ClipData.newPlainText("", text).apply {
        if (sensitive) description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    })

    /** Gives the current clip a wall-clock time stamp, as a device does (the strip chip compares with the wall clock). */
    private fun stampNow() {
        val description = assertNotNull(manager.primaryClip).description
        android.content.ClipDescription::class.java.getMethod("setTimestamp", Long::class.javaPrimitiveType).invoke(description, System.currentTimeMillis())
    }

    private fun historyTexts() = ClipboardDao.getInstance(latinIME)!!.getAll().map { it.text }

    // --- memory only -----------------------------------------------------------------------

    @Test fun secretsAndCodesNeverReachTheHistoryDatabase() {
        assertTrue(Settings.getValues().mClipboardHistoryEnabled)
        copy(key)
        copy("Dein Code lautet 482913")
        copy("ein Kennwort", sensitive = true)
        copy("DE89 3704 0044 0532 0130 00")
        copy("Einkaufsliste: Brot")
        val stored = historyTexts()
        assertEquals(setOf("DE89 3704 0044 0532 0130 00", "Einkaufsliste: Brot"), stored.toSet())
        assertFalse(stored.any { it != null && (it.contains("482913") || it.contains("Kennwort") || it.contains("sk-")) })
        // they are in the vault instead
        assertEquals(2, ClipVault.secrets().size)
        assertEquals(listOf("482913"), ClipVault.codes().map { it.shown })
    }

    /** Copied while a password field has the focus: a secret for the history, and kept nowhere (password fields count as incognito). */
    @Test fun aClipCopiedInAPasswordFieldIsKeptNowhere() {
        val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        load(password)
        val field = android.inputmethodservice.InputMethodService::class.java.getDeclaredField("mInputEditorInfo").apply { isAccessible = true }
        field.set(latinIME, password)
        try { copy("ganz normaler Satz") } finally { field.set(latinIME, null) }
        assertTrue(Settings.getValues().mIncognitoModeEnabled)
        assertFalse(historyTexts().contains("ganz normaler Satz"))
        assertTrue(ClipVault.secrets().isEmpty())
        // the verdict itself: a secret whatever the text
        assertEquals(ClipKind.SECRET, offer("ganz normaler Satz", marked = true, allowed = false).kind)
        assertTrue(ClipVault.secrets().isEmpty())
    }

    @Test fun withTheHistoryOffNothingIsKept() {
        latinIME.prefs().edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false).commit()
        load(textField)
        assertFalse(Settings.getValues().mClipboardHistoryEnabled)
        copy(key)
        copy("123456")
        assertTrue(ClipVault.secrets().isEmpty())
        assertTrue(ClipVault.codes().isEmpty())
        assertTrue(historyTexts().isEmpty())
        // a fresh code is still found in the current clip, read live, for stage 45; never a secret
        assertEquals("123456", ClipVault.freshest(latinIME, ClipKind.OTP, 60_000)?.text)
        copy(key)
        assertNull(ClipVault.freshest(latinIME, ClipKind.SECRET, 60_000))
        assertTrue(ClipVault.secrets().isEmpty())
    }

    @Test fun lockedOrIncognitoKeepsNothingAndPersistsNothing() {
        shadowOf(keyguard).setIsDeviceLocked(true)
        copy(key)
        shadowOf(keyguard).setIsDeviceLocked(false)
        load(textField, incognito = true)
        copy("Code: 554433")
        assertTrue(ClipVault.secrets().isEmpty())
        assertTrue(ClipVault.codes().isEmpty())
        assertTrue(historyTexts().none { it == key || it == "Code: 554433" })
    }

    @Test fun oldSecretsLeaveTheHistoryPinnedOnesStay() {
        val dao = ClipboardDao.getInstance(latinIME)!!
        dao.addClip(wall - 3_000, false, key)
        dao.addClip(wall - 2_000, false, "123456")
        dao.addClip(wall - 1_000, false, "Notiz")
        dao.addClip(wall - 500, true, "Tr0ub4dor&3")
        latinIME.clipboardHistoryManager.prepareClipboardHistory()
        assertEquals(setOf("Notiz", "Tr0ub4dor&3"), historyTexts().toSet())
    }

    // --- expiry and first paste ------------------------------------------------------------

    @Test fun secretsExpireAfterTwoMinutesCodesAfterTen() {
        val start = now
        assertEquals(ClipKind.SECRET, offer(key).kind)
        assertEquals(ClipKind.OTP, offer("123456").kind)
        now = start + ClipGuard.SECRET_MILLIS - 1
        assertEquals(1, ClipVault.secrets().size)
        now = start + ClipGuard.SECRET_MILLIS
        assertTrue(ClipVault.secrets().isEmpty())
        assertEquals(1, ClipVault.codes().size)
        now = start + ClipGuard.OTP_MILLIS
        assertTrue(ClipVault.codes().isEmpty())
        // the old clip read again (keyboard restart) does not come back
        offer(key, copiedAt = start)
        assertTrue(ClipVault.secrets().isEmpty())
    }

    @Test fun aSecretIsGoneAfterTheFirstPasteACodeStays() {
        offer(key)
        offer("Dein Code lautet 123456")
        val secret = ClipVault.secrets().single()
        assertSame(secret, ClipVault.take(secret.id))
        assertNull(ClipVault.take(secret.id))
        assertTrue(ClipVault.secrets().isEmpty())
        // the same clip seen again is not taken in again
        offer(key)
        assertTrue(ClipVault.secrets().isEmpty())
        val code = ClipVault.codes().single()
        assertNotNull(ClipVault.take(code.id))
        assertEquals(1, ClipVault.codes().size)
    }

    @Test fun personalAndNormalClipsAreFreshForStage45ButNeverShownInTheVault() {
        offer("https://youtu.be/dQw4w9WgXcQ")
        offer("+49 151 23456789")
        assertEquals(ClipCategory.PERSONAL, offer("max@example.com").category)
        assertTrue(ClipVault.secrets().isEmpty())
        assertTrue(ClipVault.codes().isEmpty())
        now += 30_000
        val link = assertNotNull(ClipVault.freshest(latinIME, ClipKind.LINK, 60_000))
        assertEquals("https://youtu.be/dQw4w9WgXcQ", link.text)
        assertEquals(30_000, link.ageMillis)
        assertEquals("+49 151 23456789", ClipVault.freshest(latinIME, ClipKind.PHONE, 60_000)?.text)
        assertNull(ClipVault.freshest(latinIME, ClipKind.PHONE, 10_000))
        assertFalse(ClipVault.Item(1, ClipKind.SECRET, key, null, now).toString().contains("sk-"))
        assertFalse(link.toString().contains("youtu"))
    }

    // --- the vault rows in the clipboard view ------------------------------------------------

    private class Typed : KeyboardActionListener.Adapter() {
        val texts = mutableListOf<String?>()
        override fun onTextInput(text: String?) { texts += text }
    }

    private fun historyView(editor: EditorInfo = textField, listener: Typed = Typed()): ClipboardHistoryView {
        val container = LayoutInflater.from(latinIME).inflate(R.layout.strip_container, null) as ViewGroup
        KeyboardSwitcher::class.java.getDeclaredField("mClipboardStripView").apply { isAccessible = true }
            .set(KeyboardSwitcher.getInstance(), container.findViewById<LinearLayout>(R.id.clipboard_strip))
        val view = LayoutInflater.from(latinIME).inflate(R.layout.clipboard_history_view, FrameLayout(latinIME), false) as ClipboardHistoryView
        view.keyboardActionListener = listener
        view.startClipboardHistory(ClipboardHistoryManager(latinIME), null, editor, listener)
        return view
    }

    private fun allTexts(view: View): List<String> = buildList {
        if (view is TextView) add(view.text.toString())
        view.contentDescription?.let { add(it.toString()) }
        if (view is ViewGroup) for (i in 0 until view.childCount) addAll(allTexts(view.getChildAt(i)))
    }

    private fun chips(view: View, rowTag: String): List<TextView> {
        val row = view.findViewWithTag<ViewGroup>(rowTag) ?: return emptyList()
        val line = row.getChildAt(0) as ViewGroup
        // stage 45: the "Meine Daten" chip at the end of the last row is no vault item
        return (1 until line.childCount).map { line.getChildAt(it) as TextView }.filter { it.tag != ClipVaultSection.TAG_MY_DATA }
    }

    @Test fun secretsAreMaskedOnScreenAndForTalkBack() {
        offer(key)
        offer("Tr0ub4dor&3")
        offer("Dein Code lautet 123456")
        val view = historyView()
        val secrets = chips(view, ClipVaultSection.TAG_SECRET_ROW)
        assertEquals(listOf(ClipGuard.HIDDEN, "sk-proj-…7f3a"), secrets.map { it.text.toString() })
        assertTrue(secrets.all { it.contentDescription.toString().contains(it.text) })
        assertEquals(listOf("123456"), chips(view, ClipVaultSection.TAG_CODE_ROW).map { it.text.toString() })
        val everything = allTexts(view)
        assertFalse(everything.any { it.contains(key) || it.contains("Ab3dE") || it.contains("Tr0ub4dor") }, everything.toString())
        assertTrue(everything.contains(latinIME.getString(R.string.agent_vault_title)))
        // the section comes first and shortens the list
        assertEquals(ClipVaultSection.TAG_SECTION, view.getChildAt(0).tag)
    }

    @Test fun aTapInsertsTheSecretOnceAndClearsTheSameSystemClip() {
        copy(key)
        assertEquals(1, ClipVault.secrets().size)
        val typed = Typed()
        val view = historyView(listener = typed)
        chips(view, ClipVaultSection.TAG_SECRET_ROW).single().performClick()
        assertEquals(listOf<String?>(key), typed.texts)
        assertTrue(ClipVault.secrets().isEmpty())
        assertNull(view.findViewWithTag<View>(ClipVaultSection.TAG_SECRET_ROW))
        val clip = manager.primaryClip
        assertTrue(clip == null || clip.itemCount == 0 || clip.getItemAt(0).text.isNullOrEmpty())
    }

    @Test fun anotherClipOnTheSystemClipboardIsLeftAlone() {
        offer(key)
        copy("etwas anderes")
        val typed = Typed()
        val view = historyView(listener = typed)
        chips(view, ClipVaultSection.TAG_SECRET_ROW).single().performClick()
        assertEquals(listOf<String?>(key), typed.texts)
        assertEquals("etwas anderes", manager.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test fun aTapOnACodeInsertsOnlyTheCode() {
        offer("Ihr Bestätigungscode: 482913. Gültig für 10 Minuten.")
        val typed = Typed()
        val view = historyView(listener = typed)
        chips(view, ClipVaultSection.TAG_CODE_ROW).single().performClick()
        assertEquals(listOf<String?>("482913"), typed.texts)
    }

    @Test fun noVaultInPasswordFieldsIncognitoOrWhileLocked() {
        offer(key)
        offer("123456")
        val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        load(password)
        assertNull(historyView(password).findViewWithTag<View>(ClipVaultSection.TAG_SECRET_ROW))
        load(textField, incognito = true)
        assertNull(historyView().findViewWithTag<View>(ClipVaultSection.TAG_CODE_ROW))
        load(textField)
        shadowOf(keyguard).setIsDeviceLocked(true)
        assertNull(historyView().findViewWithTag<View>(ClipVaultSection.TAG_SECRET_ROW))
        shadowOf(keyguard).setIsDeviceLocked(false)
        // shown again when unlocked; a chip tapped after the state changed inserts nothing
        val typed = Typed()
        val view = historyView(listener = typed)
        val chip = chips(view, ClipVaultSection.TAG_SECRET_ROW).single()
        shadowOf(keyguard).setIsDeviceLocked(true)
        chip.performClick()
        assertTrue(typed.texts.isEmpty())
        assertEquals(1, ClipVault.secrets().size.also { shadowOf(keyguard).setIsDeviceLocked(false) })
    }

    @Test fun expiredSecretsDisappearFromTheOpenView() {
        offer(key)
        val view = historyView()
        assertEquals(1, chips(view, ClipVaultSection.TAG_SECRET_ROW).size)
        now += ClipGuard.SECRET_MILLIS
        view.refreshVault()
        assertNull(view.findViewWithTag<View>(ClipVaultSection.TAG_SECRET_ROW))
    }

    @Test fun personalClipsAreLabelledInTheHistory() {
        val (text, description) = ClipVaultSection.historyText(latinIME, "DE89 3704 0044 0532 0130 00", ClipKind.IBAN)
        assertEquals("IBAN\nDE89 3704 0044 0532 0130 00", text.toString())
        assertEquals("IBAN: DE89 3704 0044 0532 0130 00", description)
        val (masked, maskedDescription) = ClipVaultSection.historyText(latinIME, key, ClipKind.SECRET)
        assertFalse(masked.toString().contains("Ab3dE"))
        assertFalse(maskedDescription!!.contains("Ab3dE"))
        assertEquals("Hallo" to null, ClipVaultSection.historyText(latinIME, "Hallo", ClipKind.TEXT).let { it.first.toString() to it.second })
    }

    @Test fun aSecretIsNeverOfferedAsStripSuggestion() {
        latinIME.prefs().edit().putBoolean(Settings.PREF_SUGGEST_CLIPBOARD_CONTENT, true).commit()
        load(textField)
        assertTrue(Settings.getValues().mSuggestClipboardContent)
        val parent = FrameLayout(latinIME)
        val historyManager = ClipboardHistoryManager(latinIME).apply { onCreate() }
        try {
            copy("harmloser Text")
            stampNow()
            assertNotNull(historyManager.getClipboardSuggestionView(textField, parent))
            copy(key)
            stampNow()
            assertNull(historyManager.getClipboardSuggestionView(textField, parent))
            copy("irgendwas", sensitive = true)
            stampNow()
            assertNull(historyManager.getClipboardSuggestionView(textField, parent))
        } finally {
            historyManager.onDestroy()
        }
    }

    @Test fun theVaultOpensMyDataFromItsLastRow() {
        offer(key)
        offer("123456")
        val view = historyView()
        // at the end of the last row (codes), not a vault item
        val codes = view.findViewWithTag<ViewGroup>(ClipVaultSection.TAG_CODE_ROW).getChildAt(0) as ViewGroup
        val chip = codes.getChildAt(codes.childCount - 1) as TextView
        assertEquals(ClipVaultSection.TAG_MY_DATA, chip.tag)
        assertEquals(latinIME.getString(R.string.agent_vault_my_data), chip.text.toString())
        assertEquals(listOf("123456"), chips(view, ClipVaultSection.TAG_CODE_ROW).map { it.text.toString() })
        chip.performClick()
        val started = shadowOf(latinIME).nextStartedActivity
        assertEquals(helium314.keyboard.settings.SettingsActivity::class.java.name, started.component?.className)
        assertEquals(AgentPreferences.MY_DATA_DESTINATION, started.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertTrue(AgentPreferences.MY_DATA_DESTINATION in AgentPreferences.SETTINGS_DESTINATIONS)
        // nothing was inserted
        assertEquals(2, ClipVault.secrets().size + ClipVault.codes().size)
    }
}
