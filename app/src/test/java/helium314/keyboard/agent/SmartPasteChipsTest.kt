// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.agentkeyboard.core.clip.SmartAction
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.clipboard.ClipboardAdapter
import helium314.keyboard.keyboard.clipboard.ClipboardHistoryView
import helium314.keyboard.keyboard.clipboard.ClipboardLayoutParams
import helium314.keyboard.keyboard.clipboard.OnKeyEventListener
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
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage 40 Smart Paste: chips after the clipboard pill and on history cards, the intents they start
 * (fake launcher), nothing for secrets and protected fields, and insertion only on a tap, once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class SmartPasteChipsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val manager get() = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    /** Saturday, 14 November 2026, 10:00 in Berlin. */
    private val saturday = dev.agentkeyboard.core.LocalMoment(dev.agentkeyboard.core.CivilDate(2026, 11, 14),
        dev.agentkeyboard.core.ClockTime(10, 0)).toEpochMillis(zone)
    private val textField = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "app"; fieldId = 1 }

    private val started = mutableListOf<Intent>()
    private var locked = false
    private val defaultLauncher = SmartPasteChips.launcher
    private val defaultLocked = SmartPasteChips.deviceLocked
    private val defaultPlaces = SavedPlaces.available

    // put together so that the file holds no key-shaped string; not a real key
    private val key = "sk" + "-proj-" + "Ab3dE5fG7hJ9kL1mN3pQ5rS7tU9vW1xY3z" + "7f3a"

    @Before fun setUp() {
        latinIME.prefs().edit().clear().putBoolean(Settings.PREF_SUGGEST_CLIPBOARD_CONTENT, true)
            .putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        load(textField)
        ClipVault.clear()
        helium314.keyboard.agent.commands.SensitiveClips.clear(latinIME)
        ClipboardDao.getInstance(latinIME)?.clear()
        SmartPasteChips.launcher = SearchCommand.Launcher { _, intent -> started += intent; true }
        SmartPasteChips.deviceLocked = { locked }
        SavedPlaces.available = { true }
        SavedPlaces.load(latinIME).forEach { SavedPlaces.delete(latinIME, it.name) }
        DateCommand.clock = { saturday }
        DateCommand.zone = { zone }
    }

    @After fun tearDown() {
        SavedPlaces.load(latinIME).forEach { SavedPlaces.delete(latinIME, it.name) }
        historyManagers.forEach { it.onDestroy() }
        SmartPasteChips.launcher = defaultLauncher
        SmartPasteChips.deviceLocked = defaultLocked
        SavedPlaces.available = defaultPlaces
        DateCommand.clock = { System.currentTimeMillis() }
        DateCommand.zone = { TimeZone.getDefault() }
        ClipVault.clear()
        helium314.keyboard.agent.commands.SensitiveClips.clear(latinIME)
        ClipboardDao.getInstance(latinIME)?.clear()
        latinIME.prefs().edit().clear().commit()
        load(textField)
    }

    private fun load(editor: EditorInfo, incognito: Boolean = false) {
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, incognito).commit()
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editor, false, latinIME.packageName))
    }

    private fun copy(text: String, sensitive: Boolean = false) = manager.setPrimaryClip(ClipData.newPlainText("", text).apply {
        if (sensitive) description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    })

    /** Gives the current clip a wall-clock time stamp, as a device does (the pill compares with the wall clock, P154). */
    private fun stampNow() {
        val description = assertNotNull(manager.primaryClip).description
        android.content.ClipDescription::class.java.getMethod("setTimestamp", Long::class.javaPrimitiveType).invoke(description, System.currentTimeMillis())
    }

    // --- the strip -------------------------------------------------------------------------------

    /** A fake keyboard: records what a chip inserts, refuses while [canInsert] is false. */
    private inner class Host(override var editorInfo: EditorInfo? = textField) : SmartPasteChips.StripHost {
        override val context: Context get() = latinIME
        val inserted = mutableListOf<String>()
        var canInsert = true
        override fun insertPrivate(text: String): Boolean {
            if (!canInsert) return false
            inserted += text
            return true
        }
    }

    private fun stripChips(text: String, host: Host = Host(), marked: Boolean = false, onInserted: () -> Unit = {}) =
        SmartPasteChips.stripChips(host, text, System.currentTimeMillis(), marked, onInserted)

    private fun labels(text: String) = stripChips(text).map { it.label.toString() }

    private fun s(id: Int, vararg args: Any) = latinIME.getString(id, *args)

    @Test fun chipsPerKind() {
        assertEquals(listOf(s(R.string.agent_smart_map), s(R.string.agent_smart_place)), labels("Musterstraße 12, 10115 Berlin"))
        assertEquals(listOf(s(R.string.agent_smart_call), s(R.string.agent_smart_contact)), labels("+49 151 23456789"))
        assertEquals(listOf(s(R.string.agent_smart_iban)), labels("DE89370400440532013000"))
        assertEquals(listOf(s(R.string.agent_smart_iban_invalid)), labels("DE88370400440532013000"))
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, stripChips("DE88370400440532013000").single().kind)
        assertEquals(listOf(s(R.string.agent_smart_mail)), labels("max.mustermann@example.com"))
        assertEquals(listOf(s(R.string.agent_smart_clean)), labels("https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=Ab12Cd34"))
        assertEquals(listOf(s(R.string.agent_smart_code, "123456")), labels("Dein Code lautet 123456"))
        assertEquals(listOf(s(R.string.agent_smart_track, "DHL")), labels("00340434161094042557"))
        assertEquals(listOf(s(R.string.agent_smart_event, "So 15.11. 18:00")), labels("Wir sehen uns morgen um 18 Uhr."))
        // nothing recognised, a code alone (the pill inserts it), a link without tracking: no chips
        for (text in listOf("Hallo, wie geht's?", "482913", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Version 2.1"))
            assertTrue(stripChips(text).isEmpty(), text)
    }

    @Test fun chipsSitNextToThePillOnlyWithTheSwitchOn() {
        val parent = FrameLayout(latinIME)
        val historyManager = ClipboardHistoryManager(latinIME).apply { onCreate() }
        try {
            copy("Musterstraße 12, 10115 Berlin")
            stampNow()
            val view = assertNotNull(historyManager.getClipboardSuggestionView(textField, parent))
            assertEquals(SmartPasteChips.TAG_STRIP, view.tag)
            val line = (view as ViewGroup).getChildAt(0) as LinearLayout
            // the pill first (still inserting the clip as it is), then the chips
            assertEquals("Musterstraße 12, 10115 Berlin", line.getChildAt(0).findViewById<TextView>(R.id.clipboard_suggestion_text).text.toString())
            assertEquals(listOf(s(R.string.agent_smart_map), s(R.string.agent_smart_place)),
                (1 until line.childCount).map { (line.getChildAt(it) as TextView).text.toString() })
            assertEquals(View.NO_ID, line.id) // not taken for the row of a typed command
            // the map chip starts the maps app, nothing is inserted, the pill stays
            line.getChildAt(1).performClick()
            assertEquals(Intent.ACTION_VIEW, started.single().action)
            assertEquals("geo", started.single().data?.scheme)
            // switched off: the pill alone, as before stage 40
            latinIME.prefs().edit().putBoolean(AgentPreferences.SMART_PASTE, false).commit()
            stampNow()
            val pill = assertNotNull(historyManager.getClipboardSuggestionView(textField, parent))
            assertFalse(pill.tag == SmartPasteChips.TAG_STRIP)
            assertNotNull(pill.findViewById<TextView>(R.id.clipboard_suggestion_text))
        } finally {
            historyManager.onDestroy()
        }
    }

    @Test fun intentsAreStandardAndNeedNoPermission() {
        val host = Host()
        fun tap(text: String, index: Int = 0): Intent {
            started.clear()
            stripChips(text, host)[index].onTap()
            return started.single()
        }
        tap("Musterstraße 12, 10115 Berlin").let {
            assertEquals(Intent.ACTION_VIEW, it.action)
            assertEquals("geo:0,0?q=Musterstra%C3%9Fe%2012%2C%2010115%20Berlin", it.dataString)
        }
        tap("+49 151 23456789").let {
            assertEquals(Intent.ACTION_DIAL, it.action) // the dialer: the user calls there, no CALL_PHONE
            assertEquals("tel", it.data?.scheme)
            assertEquals("+49 151 23456789", it.data?.schemeSpecificPart)
        }
        tap("+49 151 23456789", 1).let {
            assertEquals(ContactsContract.Intents.Insert.ACTION, it.action) // the contacts app saves, no WRITE_CONTACTS
            assertEquals(ContactsContract.RawContacts.CONTENT_TYPE, it.type)
            assertEquals("+49 151 23456789", it.getStringExtra(ContactsContract.Intents.Insert.PHONE))
        }
        tap("max.mustermann@example.com").let {
            assertEquals(Intent.ACTION_SENDTO, it.action)
            assertEquals("mailto", it.data?.scheme)
            assertEquals("max.mustermann@example.com", it.data?.schemeSpecificPart)
        }
        tap("Sendung 1Z999AA10123456784 ist unterwegs").let {
            assertEquals(Intent.ACTION_VIEW, it.action)
            assertTrue(it.hasCategory(Intent.CATEGORY_BROWSABLE))
            assertEquals("https://www.ups.com/track?loc=de_DE&tracknum=1Z999AA10123456784", it.dataString)
        }
        tap("Wir sehen uns morgen um 18 Uhr.").let {
            assertEquals(Intent.ACTION_INSERT, it.action) // the calendar app saves, no calendar permission
            assertEquals(CalendarContract.Events.CONTENT_URI, it.data)
            val begin = dev.agentkeyboard.core.LocalMoment(dev.agentkeyboard.core.CivilDate(2026, 11, 15),
                dev.agentkeyboard.core.ClockTime(18, 0)).toEpochMillis(zone)
            assertEquals(begin, it.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
            assertEquals("Wir sehen uns", it.getStringExtra(CalendarContract.Events.TITLE))
        }
        for (intent in listOf(tap("+49 151 23456789"), tap("max.mustermann@example.com")))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        // opening an app inserts nothing
        assertTrue(host.inserted.isEmpty())
        // the app asks for none of the permissions these apps would need to act by themselves
        val requested = latinIME.packageManager.getPackageInfo(latinIME.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertTrue("android.permission.READ_CONTACTS" in requested, requested.toString()) // the list is the real manifest
        for (permission in listOf("android.permission.CALL_PHONE", "android.permission.WRITE_CONTACTS",
            "android.permission.WRITE_CALENDAR", "android.permission.READ_CALENDAR")) assertFalse(permission in requested, permission)
        // a missing app: a toast, nothing else
        SmartPasteChips.launcher = SearchCommand.Launcher { _, _ -> false }
        stripChips("+49 151 23456789", host)[0].onTap()
        assertTrue(host.inserted.isEmpty())
    }

    @Test fun insertOnlyOnATapAndOnce() {
        val host = Host()
        var dismissed = 0
        val chip = stripChips("DE89370400440532013000", host) { dismissed++ }.single()
        assertTrue(host.inserted.isEmpty(), "nothing before the tap")
        chip.onTap()
        chip.onTap()
        assertEquals(listOf("DE89 3704 0044 0532 0130 00"), host.inserted)
        assertEquals(1, dismissed)
        stripChips("https://shop.example.de/p?id=5&utm_source=newsletter", host).single().onTap()
        stripChips("Ihr Bestätigungscode: 482913. Gültig für 10 Minuten.", host).single().onTap()
        assertEquals(listOf("DE89 3704 0044 0532 0130 00", "https://shop.example.de/p?id=5", "482913"), host.inserted)
        // the hint inserts nothing
        stripChips("DE88370400440532013000", host).single().onTap()
        assertEquals(3, host.inserted.size)
        // the cursor moved: refused, and the pill stays
        host.canInsert = false
        stripChips("DE89370400440532013000", host) { dismissed++ }.single().onTap()
        assertEquals(3, host.inserted.size)
        assertEquals(1, dismissed)
        // blocked between showing and tapping (locked now): nothing
        host.canInsert = true
        val late = stripChips("DE89370400440532013000", host).single()
        locked = true
        late.onTap()
        assertEquals(3, host.inserted.size)
    }

    @Test fun nothingForSecretsOrInProtectedFields() {
        assertTrue(stripChips(key).isEmpty())
        assertTrue(stripChips("Musterstraße 12, 10115 Berlin", marked = true).isEmpty())
        // copied in a password field: a secret wherever it is offered later (P155)
        helium314.keyboard.agent.commands.SensitiveClips.remember("+49 151 23456789")
        assertTrue(stripChips("+49 151 23456789").isEmpty())
        val address = "Musterstraße 12, 10115 Berlin"
        val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        load(password)
        assertTrue(stripChips(address, Host(password)).isEmpty())
        load(textField, incognito = true)
        assertTrue(stripChips(address).isEmpty())
        val noLearning = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING }
        load(noLearning)
        assertTrue(stripChips(address, Host(noLearning)).isEmpty())
        val number = EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }
        load(number)
        assertTrue(stripChips(address, Host(number)).isEmpty())
        assertTrue(stripChips(address, Host(null)).isEmpty())
        load(textField)
        locked = true
        assertTrue(stripChips(address).isEmpty())
        locked = false
        assertEquals(2, stripChips(address).size)
        latinIME.prefs().edit().putBoolean(AgentPreferences.SMART_PASTE, false).commit()
        assertTrue(stripChips(address).isEmpty())
    }

    @Test fun aSecretGetsNoPillAndAPasswordFieldNoChips() {
        val parent = FrameLayout(latinIME)
        val historyManager = ClipboardHistoryManager(latinIME).apply { onCreate() }
        try {
            copy(key)
            stampNow()
            assertNull(historyManager.getClipboardSuggestionView(textField, parent))
            copy("Musterstraße 12, 10115 Berlin", sensitive = true)
            stampNow()
            assertNull(historyManager.getClipboardSuggestionView(textField, parent))
            // the pill of a password field shows stars and gets no chips
            copy("Musterstraße 12, 10115 Berlin")
            stampNow()
            val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
            load(password)
            val pill = historyManager.getClipboardSuggestionView(password, parent)
            assertTrue(pill == null || pill.tag != SmartPasteChips.TAG_STRIP)
        } finally {
            historyManager.onDestroy()
        }
    }

    @Test fun aSavedPlaceIsNamedAfterTheStreetAndSavedOnce() {
        val chip = { stripChips("Musterstraße 12, 10115 Berlin")[1] }
        chip().onTap()
        assertEquals(listOf("Musterstraße 12" to "Musterstraße 12, 10115 Berlin"), SavedPlaces.load(latinIME).map { it.name to it.address })
        // the same address again: kept under its name, nothing added
        chip().onTap()
        assertEquals(1, SavedPlaces.load(latinIME).size)
        // locked storage: nothing saved
        SavedPlaces.available = { false }
        stripChips("Hauptstr. 5a, 80331 München")[1].onTap()
        SavedPlaces.available = { true }
        assertEquals(1, SavedPlaces.load(latinIME).size)
        assertTrue(started.isEmpty())
    }

    // --- the clipboard history -------------------------------------------------------------------

    private class Typed : KeyboardActionListener.Adapter() {
        val texts = mutableListOf<String?>()
        val private = mutableListOf<String?>()
        override fun onTextInput(text: String?) { texts += text }
        override fun onPrivateTextInput(text: String?) { private += text }
    }

    private val historyManagers = mutableListOf<ClipboardHistoryManager>()
    private fun historyManager() = ClipboardHistoryManager(latinIME).apply { onCreate() }.also { historyManagers += it }

    private fun list() = RecyclerView(latinIME).apply {
        layoutManager = androidx.recyclerview.widget.StaggeredGridLayoutManager(2, androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL)
    }

    /** Binds the history entry with [text] the way the open history view does, with Smart Paste for [editor]. */
    private fun card(text: String, editor: EditorInfo = textField, typed: Typed = Typed()): View {
        val historyManager = historyManager()
        val adapter = ClipboardAdapter(ClipboardLayoutParams(latinIME), object : OnKeyEventListener {
            override fun onKeyDown(clipId: Long) {}
            override fun onKeyUp(clipId: Long) {}
        })
        adapter.clipboardHistoryManager = historyManager
        adapter.smartPaste = SmartPasteChips.History(latinIME, { editor }) { typed.onPrivateTextInput(it); true }
        val position = (0 until historyManager.getHistorySize()).first { historyManager.getHistoryEntry(it)?.text == text }
        val holder = adapter.onCreateViewHolder(list(), 0)
        adapter.onBindViewHolder(holder, position)
        return holder.itemView
    }

    private fun add(text: String) = ClipboardDao.getInstance(latinIME)!!.addClip(System.currentTimeMillis(), false, text)

    private fun cardChips(view: View): List<TextView> {
        val row = view.findViewWithTag<ViewGroup>(SmartPasteChips.TAG_HISTORY_ROW) ?: return emptyList()
        if (row.visibility != View.VISIBLE) return emptyList()
        return (0 until row.childCount).map { row.getChildAt(it) as TextView }
    }

    @Test fun historyCardsGetShortChipsThatInsertPrivately() {
        add("DE89370400440532013000")
        val typed = Typed()
        val chips = cardChips(card("DE89370400440532013000", typed = typed))
        assertEquals(listOf(s(R.string.agent_smart_iban_short)), chips.map { it.text.toString() })
        assertEquals(s(R.string.agent_smart_iban_description, "DE89 3704 0044 0532 0130 00"), chips.single().contentDescription)
        assertTrue(typed.private.isEmpty())
        chips.single().performClick()
        chips.single().performClick()
        assertEquals(listOf<String?>("DE89 3704 0044 0532 0130 00"), typed.private)
        assertTrue(typed.texts.isEmpty())
        // at most two chips share a card; an address card maps and saves
        add("Musterstraße 12, 10115 Berlin")
        assertEquals(listOf(s(R.string.agent_smart_map_short), s(R.string.agent_smart_place_short)),
            cardChips(card("Musterstraße 12, 10115 Berlin")).map { it.text.toString() })
        // a plain text card has no row
        add("Hallo, wie geht's?")
        assertTrue(cardChips(card("Hallo, wie geht's?")).isEmpty())
    }

    @Test fun historyCardsHaveNoChipsInProtectedFieldsOrForSecrets() {
        add("Musterstraße 12, 10115 Berlin")
        val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        load(password)
        val address = "Musterstraße 12, 10115 Berlin"
        assertTrue(cardChips(card(address, password)).isEmpty())
        load(textField, incognito = true)
        assertTrue(cardChips(card(address)).isEmpty())
        load(textField)
        locked = true
        assertTrue(cardChips(card(address)).isEmpty())
        locked = false
        // shown again; a tap after the field became protected does nothing
        val typed = Typed()
        val chip = cardChips(card(address, typed = typed)).first()
        locked = true
        chip.performClick()
        assertTrue(started.isEmpty())
        locked = false
        // a text copied in a password field before (still in the history from before stage 44) gets nothing
        helium314.keyboard.agent.commands.SensitiveClips.remember("+49 151 23456789")
        add("+49 151 23456789")
        assertTrue(cardChips(card("+49 151 23456789")).isEmpty())
    }

    @Test fun theOpenHistoryViewGivesItsCardsSmartPaste() {
        add("+49 151 23456789")
        val container = LayoutInflater.from(latinIME).inflate(R.layout.strip_container, null) as ViewGroup
        KeyboardSwitcher::class.java.getDeclaredField("mClipboardStripView").apply { isAccessible = true }
            .set(KeyboardSwitcher.getInstance(), container.findViewById<LinearLayout>(R.id.clipboard_strip))
        val view = LayoutInflater.from(latinIME).inflate(R.layout.clipboard_history_view, FrameLayout(latinIME), false) as ClipboardHistoryView
        val typed = Typed()
        view.keyboardActionListener = typed
        view.startClipboardHistory(historyManager(), null, textField, typed)
        val adapter = view.findViewById<RecyclerView>(R.id.clipboard_list).adapter as ClipboardAdapter
        assertNotNull(adapter.smartPaste)
        val holder = adapter.onCreateViewHolder(view.findViewById(R.id.clipboard_list), 0)
        adapter.onBindViewHolder(holder, 0)
        val chips = cardChips(holder.itemView)
        assertEquals(listOf(s(R.string.agent_smart_call_short), s(R.string.agent_smart_contact_short)), chips.map { it.text.toString() })
        chips[0].performClick()
        assertEquals(Intent.ACTION_DIAL, started.single().action)
        view.stopClipboardHistory()
        assertNull(adapter.smartPaste)
        assertTrue(typed.texts.isEmpty() && typed.private.isEmpty())
    }

    @Test fun actionsShowNoClipTextInLogsOrNames() {
        val action: SmartAction = SmartPasteChips.actions(latinIME, "Musterstraße 12, 10115 Berlin", System.currentTimeMillis(), false,
            Settings.getValues()).first()
        assertFalse(action.toString().contains("Muster"))
    }
}
