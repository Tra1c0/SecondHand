// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandContext
import dev.agentkeyboard.core.suggest.FieldClass
import dev.agentkeyboard.core.suggest.SuggestKind
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 45: field and context suggestions with a fake field, the real vault and fake "Meine Daten". */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class FieldSuggestionChipsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val minute = 60_000L
    private var now = 1_000_000_000L

    private fun field(type: Int, hint: String? = null, app: String = "app", name: String? = null) = EditorInfo().apply {
        inputType = type; packageName = app; fieldId = 1; hintText = hint; fieldName = name
    }
    private val email = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
    private val phone = field(InputType.TYPE_CLASS_PHONE)
    private val text = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
    private val chat = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, app = "com.whatsapp")
    private val codeField = field(InputType.TYPE_CLASS_NUMBER, hint = "Bestätigungscode")
    private val number = field(InputType.TYPE_CLASS_NUMBER)

    private var data: MyData? = MyData(name = "Max Mustermann", emails = listOf("max@example.com"), phones = listOf("+49 151 12345678"),
        addresses = listOf("Musterstraße 1, 10115 Berlin"), ibans = listOf("DE89 3704 0044 0532 0130 00"))
    private var dataReads = 0
    private var locked = false

    /** The field: text before and after the cursor. */
    private var before = ""
    private var after = ""
    private val replaced = mutableListOf<Pair<String, String>>()
    private val inserted = mutableListOf<String>()
    private val marked = mutableListOf<String>()

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override var editorInfo: EditorInfo? = email
        override val language = "de"
        // like InputLogic.replaceCommandText: only while exactly the expected text stands before the cursor
        override fun replace(command: CommandContext, replacement: String): Boolean {
            if (!before.endsWith(command.text)) return false
            before = before.dropLast(command.text.length) + replacement
            replaced += command.text to replacement
            return true
        }
        // like AgentEditorController.insertAtChipCursor: one insertion at the cursor the chips were built for
        override fun insertAtCursor(text: String, privateText: Boolean): Boolean {
            // review M4 of stage 46: field suggestions always go in as private text
            assertTrue(privateText, "a field suggestion is private")
            before += text
            inserted += text
            return true
        }
        override fun openCalculator(command: dev.agentkeyboard.core.CommandCall) = false
        override val canOpenCalculator = false
        override fun textBeforeCursor(length: Int): CharSequence = before.takeLast(length)
        override fun textAfterCursor(length: Int): CharSequence = after.take(length)
        override fun markPrivate(text: String) { marked += text }
    }

    private val defaultMyData = FieldSuggestionChips.myData
    private val defaultLocked = FieldSuggestionChips.deviceLocked

    @Before fun setUp() {
        latinIME.prefs().edit().clear().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        ClipVault.clear()
        ClipVault.clock = { now }
        ContextLearning.clock = { now }
        ContextLearning.clear(latinIME)
        FieldSuggestionChips.forget()
        FieldSuggestionChips.myData = { _, _ -> dataReads++; data }
        FieldSuggestionChips.deviceLocked = { locked }
    }

    @After fun tearDown() {
        ClipVault.clear()
        ClipVault.clock = { System.currentTimeMillis() }
        ContextLearning.clock = { System.currentTimeMillis() }
        ContextLearning.clear(latinIME)
        FieldSuggestionChips.myData = defaultMyData
        FieldSuggestionChips.deviceLocked = defaultLocked
        FieldSuggestionChips.forget()
        latinIME.prefs().edit().clear().commit()
    }

    private fun load(editorInfo: EditorInfo) {
        Settings.getInstance().loadSettings(latinIME, Locale.GERMANY, InputAttributes(editorInfo, false, latinIME.packageName))
    }

    private fun copy(text: String, ageMillis: Long) {
        ClipVault.offer(text, false, now - ageMillis, "DE", true)
    }

    private fun chips(typed: String, editor: EditorInfo, behind: String = ""): List<KeyboardCommands.Chip> {
        load(editor)
        host.editorInfo = editor
        before = typed
        after = behind
        return FieldSuggestionChips.stripChips(host)
    }

    private fun labels(chips: List<KeyboardCommands.Chip>) = chips.map { it.label.toString() }

    // --- fields ---

    @Test fun fieldClassesFromTheInputType() {
        assertEquals(FieldClass.EMAIL, FieldSuggestionChips.fieldClass(email))
        assertEquals(FieldClass.PHONE, FieldSuggestionChips.fieldClass(phone))
        assertEquals(FieldClass.NUMBER, FieldSuggestionChips.fieldClass(number))
        assertEquals(FieldClass.TEXT, FieldSuggestionChips.fieldClass(text))
        assertEquals(FieldClass.SHORT_MESSAGE, FieldSuggestionChips.fieldClass(chat))
        assertEquals(FieldClass.OTHER, FieldSuggestionChips.fieldClass(field(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)))
        assertNull(FieldSuggestionChips.fieldClass(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)))
        assertNull(FieldSuggestionChips.fieldClass(field(InputType.TYPE_CLASS_DATETIME)))
        for (type in listOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
            assertNull(FieldSuggestionChips.fieldClass(field(type)), "type $type")
        assertNull(FieldSuggestionChips.fieldClass(null))
    }

    @Test fun anEmptyEmailFieldOffersTheOwnAddressAndAFreshCopiedOne() {
        copy("anna@firma.de", 3 * minute)
        val shown = chips("", email)
        assertEquals(listOf("✉️ max@example.com", "✉️ anna@firma.de"), labels(shown))
        assertTrue(shown[0].description.toString().contains("max@example.com"))
        // nothing happens before a tap
        assertTrue(inserted.isEmpty() && replaced.isEmpty())
        shown[0].onTap()
        assertEquals(listOf("max@example.com"), inserted)
        assertEquals("max@example.com", before)
        // a second tap on the same chip does nothing
        shown[0].onTap()
        assertEquals(listOf("max@example.com"), inserted)
    }

    @Test fun aTapChecksTheFieldAgainAndMarksTheValuePrivate() {
        // review B12: built for an empty field, tapped after the state changed
        var shown = chips("", email)
        locked = true
        shown[0].onTap()
        locked = false
        assertTrue(inserted.isEmpty())
        shown = chips("", email)
        after = "x" // the app put text behind the cursor in the meantime
        shown[0].onTap()
        assertTrue(inserted.isEmpty())
        shown = chips("", email)
        load(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        shown[0].onTap()
        assertTrue(inserted.isEmpty())
        // review B4: a replacement marks the value as private text (the empty field goes through insertAtCursor, private in the real host)
        val typed = chips("Ma", email)
        typed.single().onTap()
        assertEquals(listOf("max@example.com"), marked)
    }

    @Test fun typedTextThatStartsAnEntryIsCompletedOtherTextGetsNothing() {
        val shown = chips("Ma", email)
        assertEquals(listOf("✉️ max@example.com"), labels(shown))
        shown.single().onTap()
        assertEquals(listOf("Ma" to "max@example.com"), replaced)
        assertEquals("max@example.com", before)
        assertTrue(inserted.isEmpty())
        assertEquals(emptyList(), chips("anna", email))
        assertEquals(emptyList(), chips("max@example.com", email))
        // text behind the cursor: not the whole field
        assertEquals(emptyList(), chips("", email, behind = "x"))
    }

    @Test fun aCodeFieldGetsTheFreshCodeFromTheVaultDigitsOnly() {
        copy("Ihr Bestätigungscode: 482-913. Gültig für 10 Minuten.", 4 * minute)
        val shown = chips("", codeField)
        assertEquals(listOf("🔢 482913"), labels(shown))
        shown.single().onTap()
        assertEquals(listOf("482913"), inserted)
        // the same in a text field whose id name says code, and in a phone-pad field with an OTP hint
        assertEquals(listOf("🔢 482913"), labels(chips("", field(InputType.TYPE_CLASS_TEXT, name = "otp_input"))))
        assertEquals(listOf("🔢 482913"), labels(chips("", field(InputType.TYPE_CLASS_PHONE, hint = "OTP"))))
        // own data never goes into a code field
        assertFalse(labels(chips("", codeField)).any { it.contains("151") })
    }

    @Test fun aPlainNumberFieldOnlyGetsAVeryFreshCode() {
        copy("482913", 2 * minute)
        assertEquals(listOf("🔢 482913"), labels(chips("", number)))
        ClipVault.clear()
        copy("482913", 5 * minute)
        assertEquals(emptyList(), chips("", number))
        // with a code hint the code may be older, up to the vault's ten minutes
        assertEquals(listOf("🔢 482913"), labels(chips("", codeField)))
        now += 6 * minute
        assertEquals(emptyList(), chips("", codeField))
    }

    @Test fun phoneAndIbanFieldsGetOwnEntriesAndFreshMatchingClips() {
        copy("0170 9876543", minute)
        assertEquals(listOf("📞 +49 151 12345678", "📞 0170 9876543"), labels(chips("", phone)))
        val iban = field(InputType.TYPE_CLASS_TEXT, hint = "IBAN")
        val shown = chips("", iban)
        assertEquals(listOf("🏦 DE89 …3000"), labels(shown))
        shown.single().onTap()
        assertEquals(listOf("DE89 3704 0044 0532 0130 00"), inserted)
        // a name field, an address field
        assertEquals(listOf("👤 Max Mustermann"), labels(chips("", field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME))))
        assertEquals(listOf("🏠 Musterstraße 1, 10115 Berlin"),
            labels(chips("", field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS))))
    }

    @Test fun withoutMyDataOnlyFreshClipsAndOtherwiseNothing() {
        data = null
        assertEquals(emptyList(), chips("", email))
        copy("anna@firma.de", minute)
        assertEquals(listOf("✉️ anna@firma.de"), labels(chips("", email)))
        data = MyData()
        ClipVault.clear()
        assertEquals(emptyList(), chips("", phone))
    }

    // --- context ---

    @Test fun theOwnersExampleOffersTheLinkJustCopiedAndATapInsertsItOnce() {
        copy("https://www.youtube.com/watch?v=dQw4w9WgXcQ", 2 * minute)
        val shown = chips("guck mal, hier dieses Video ", chat)
        assertEquals(listOf("🔗 youtube.com/…"), labels(shown))
        assertTrue(replaced.isEmpty() && inserted.isEmpty())
        shown.single().onTap()
        assertEquals("guck mal, hier dieses Video https://www.youtube.com/watch?v=dQw4w9WgXcQ", before)
        assertEquals(1, replaced.size)
        shown.single().onTap()
        assertEquals(1, replaced.size)
        // also in a normal text field of another app
        before = ""
        assertEquals(listOf("🔗 youtube.com/…"), labels(chips("guck mal, hier dieses Video ", text)))
    }

    @Test fun noContextChipWithoutCueInTheMiddleOfAWordOrForAnOldClip() {
        copy("https://example.com/artikel", minute)
        assertEquals(emptyList(), chips("Wie geht es dir? ", chat))
        assertEquals(emptyList(), chips("guck mal, hier dieses Video", chat))
        ClipVault.clear()
        copy("https://example.com/artikel", 9 * minute)
        assertEquals(emptyList(), chips("guck mal, hier dieses Video ", text))
        // the link already typed after the cue
        ClipVault.clear()
        copy("https://example.com/artikel", minute)
        assertEquals(emptyList(), chips("guck mal https://example.com/artikel ", chat))
    }

    @Test fun cueWordsOfferOwnData() {
        val number = chips("meine Nummer ist ", chat)
        assertEquals(listOf("📞 +49 151 12345678"), labels(number))
        number.single().onTap()
        assertEquals("meine Nummer ist +49 151 12345678", before)
        val iban = chips("IBAN:", text)
        assertEquals(listOf("🏦 DE89 …3000"), labels(iban))
        iban.single().onTap()
        // a space after the colon
        assertEquals("IBAN: DE89 3704 0044 0532 0130 00", before)
        assertEquals(listOf("🏠 Musterstraße 1, 10115 Berlin"), labels(chips("Adresse: ", text)))
        assertEquals(listOf("✉️ max@example.com"), labels(chips("meine Mail ist ", text)))
        // without own data and without a fresh clip: nothing
        data = null
        assertEquals(emptyList(), chips("meine Nummer ist ", chat))
    }

    @Test fun aCodeCueOffersTheFreshCode() {
        copy("Dein Code lautet 123456", minute)
        val shown = chips("Code: ", chat)
        assertEquals(listOf("🔢 123456"), labels(shown))
        shown.single().onTap()
        assertEquals("Code: 123456", before)
    }

    @Test fun secretsFromTheVaultAreNeverOffered() {
        val key = "sk" + "-proj-" + "Ab3dE5fG7hJ9kL1mN3pQ5rS7tU9vW1xY3z" + "7f3a"
        copy(key, 0)
        copy("Tr0ub4dor&3", 0)
        assertEquals(2, ClipVault.secrets().size)
        data = null
        for ((typed, editor) in listOf("" to codeField, "" to number, "Code: " to chat, "guck mal, hier dieses Video " to chat,
            "" to field(InputType.TYPE_CLASS_TEXT, hint = "IBAN"), "" to email))
            assertEquals(emptyList(), chips(typed, editor), "\"$typed\"")
    }

    // --- review of stage 45 ---

    @Test fun theMyDataFieldsAreNoLearningFields() {
        // B5: the settings page marks its fields; the keyboard counts them as incognito on every field class
        for (type in listOf(InputType.TYPE_CLASS_TEXT, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_PHONE)) {
            val own = field(type).apply { privateImeOptions = AgentPreferences.NO_LEARNING_FIELD }
            assertTrue(InputAttributes(own, false, latinIME.packageName).mNoLearning, "type $type")
            load(own)
            assertTrue(Settings.getValues().mIncognitoModeEnabled, "type $type")
            assertFalse(InputAttributes(field(type), false, latinIME.packageName).mNoLearning, "type $type")
        }
    }

    @Test fun hidingTheKeyboardDropsTheDecryptedData() {
        // B7: the screen goes off (and the device locks) only after the keyboard is hidden
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var reads = 0
        val store = MyDataStore(latinIME, helium314.keyboard.agent.security.AesGcmSecretCipher { reads++; key }) { true }
        val defaultStore = MyDataCache.store
        MyDataCache.store = { store }
        try {
            store.save(MyData(name = "Erika Musterfrau"))
            MyDataCache.drop()
            reads = 0
            assertEquals("Erika Musterfrau", MyDataCache.get(latinIME, false)?.name)
            assertEquals("Erika Musterfrau", MyDataCache.get(latinIME, false)?.name)
            assertEquals(1, reads) // cached
            latinIME.onFinishInputView(false)
            assertEquals("Erika Musterfrau", MyDataCache.get(latinIME, false)?.name)
            assertEquals(2, reads) // read again after the keyboard was hidden
        } finally {
            store.delete()
            MyDataCache.store = defaultStore
            MyDataCache.drop()
        }
    }

    // --- blocked ---

    @Test fun neverInPasswordFieldsIncognitoNoLearningOrLocked() {
        copy("482913", minute)
        copy("https://example.com/x", minute)
        dataReads = 0
        for (type in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            assertEquals(emptyList(), chips("", field(InputType.TYPE_CLASS_TEXT or type, hint = "Code")), "type $type")
        assertEquals(emptyList(), chips("", field(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)))
        // incognito
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        assertEquals(emptyList(), chips("", email))
        assertEquals(emptyList(), chips("guck mal hier ", chat))
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
        // the no-learning flag, also for number and phone fields
        for (editor in listOf(email, phone, codeField, chat)) {
            val noLearning = field(editor.inputType, editor.hintText?.toString(), editor.packageName).apply {
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
            assertEquals(emptyList(), chips("", noLearning), "type ${editor.inputType}")
            assertEquals(emptyList(), chips("meine Nummer ist ", noLearning))
        }
        // locked
        locked = true
        assertEquals(emptyList(), chips("", email))
        assertEquals(emptyList(), chips("", codeField))
        assertEquals(emptyList(), chips("meine Nummer ist ", chat))
        // own data was not even read in a blocked field
        assertEquals(0, dataReads)
        locked = false
        assertEquals(1, chips("", codeField).size)
        // no settings (e.g. before the first unlock)
        assertEquals(emptyList(), FieldSuggestionChips.stripChips(host, null))
    }

    @Test fun theSwitchTurnsEverythingOff() {
        latinIME.prefs().edit().putBoolean(AgentPreferences.FIELD_SUGGESTIONS, false).commit()
        copy("482913", minute)
        assertEquals(emptyList(), chips("", email))
        assertEquals(emptyList(), chips("", codeField))
        assertEquals(emptyList(), chips("meine Nummer ist ", chat))
    }

    // --- learning ---

    @Test fun tappedAndPassedChipsChangeSmallWeightsOnlyWithLearningOn() {
        latinIME.prefs().edit().putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true).commit()
        copy("https://example.com/rezept", minute)
        chips("guck mal, hier dieses Video ", chat).single().onTap()
        val accepted = ContextLearning.weights(latinIME, Settings.getValues()).get("media", SuggestKind.LINK)
        assertTrue(accepted > 0, "$accepted")
        // shown, then typed on: passed by once
        before = ""
        assertEquals(1, chips("das Video ", chat).size)
        assertEquals(emptyList(), chips("das Video ist", chat))
        assertEquals(emptyList(), chips("das Video ist lustig", chat))
        val ignored = ContextLearning.weights(latinIME, Settings.getValues()).get("media", SuggestKind.LINK)
        assertTrue(ignored < accepted, "$ignored < $accepted")
        assertTrue(ignored > 0, "one pass after one tap: $ignored")
        // only ids and numbers are stored, never typed text, the link or the app
        val stored = ContextLearning.stored(latinIME)!!
        assertTrue(Regex("v1;\\d+(;[a-z]+:[A-Z]+=-?\\d+\\.\\d{4})+").matches(stored), stored)
        for (secret in listOf("rezept", "example", "whatsapp", "guck")) assertFalse(stored.lowercase().contains(secret), stored)
        // "Gelernte Wörter löschen" clears them
        ContextLearning.clear(latinIME)
        assertNull(ContextLearning.stored(latinIME))
        assertEquals(0.0, ContextLearning.weights(latinIME, Settings.getValues()).get("media", SuggestKind.LINK))
    }

    @Test fun nothingIsLearnedWithLearningOffOrPaused() {
        copy("https://example.com/rezept", minute)
        // default: personalized suggestions off
        chips("guck mal, hier dieses Video ", chat).single().onTap()
        assertNull(ContextLearning.stored(latinIME))
        latinIME.prefs().edit().putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
            .putBoolean(AgentPreferences.LEARNING_PAUSED, true).commit()
        before = ""
        chips("guck mal, hier dieses Video ", chat).single().onTap()
        assertEquals(1, chips("schau mal ", chat).size)
        assertEquals(emptyList(), chips("schau mal das", chat))
        assertNull(ContextLearning.stored(latinIME))
    }

    @Test fun learnedWeightsDecideBorderlineChips() {
        latinIME.prefs().edit().putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true).commit()
        copy("https://example.com/x", 0)
        // "hier" alone with a link copied just now in a normal text field stays below the threshold
        assertEquals(emptyList(), chips("hier ", text))
        repeat(5) { ContextLearning.record(latinIME, Settings.getValues(), "here", SuggestKind.LINK, accepted = true) }
        assertEquals(listOf("🔗 example.com/…"), labels(chips("hier ", text)))
        // with learning switched off the learned weights are not used
        latinIME.prefs().edit().putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, false).commit()
        assertEquals(emptyList(), chips("hier ", text))
    }

    @Test fun labelsAreShort() {
        assertEquals("youtube.com/…", FieldSuggestionChips.shortLink("https://www.youtube.com/watch?v=x"))
        assertEquals("example.com", FieldSuggestionChips.shortLink("https://example.com/"))
        assertEquals("🏦 DE89 …3000", FieldSuggestionChips.label(SuggestKind.IBAN, "DE89 3704 0044 0532 0130 00"))
    }
}
