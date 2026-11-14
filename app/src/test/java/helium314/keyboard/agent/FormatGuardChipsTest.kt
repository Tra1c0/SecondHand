// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.text.InputType
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandContext
import dev.agentkeyboard.core.format.FieldKind
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.latin.InputAttributes
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stage 41: the glue of the format guard with a fake field (the real host: InputLogicTest.formatGuard*). */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class FormatGuardChipsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private fun field(type: Int) = EditorInfo().apply { inputType = type; packageName = "app"; fieldId = 1 }
    private val email = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
    private val webEmail = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
    private val phone = field(InputType.TYPE_CLASS_PHONE)
    private val decimal = field(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    private val text = field(InputType.TYPE_CLASS_TEXT)

    /** The field: text before and after the cursor. */
    private var before = ""
    private var after = ""
    private val replaced = mutableListOf<String>()

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override var editorInfo: EditorInfo? = email
        override val language = "de"
        // like InputLogic.replaceCommandText: only while exactly the expected text stands before the cursor
        override fun replace(command: CommandContext, replacement: String): Boolean {
            if (!before.endsWith(command.text)) return false
            before = before.dropLast(command.text.length) + replacement
            replaced += replacement
            return true
        }
        override fun openCalculator(command: dev.agentkeyboard.core.CommandCall) = false
        override val canOpenCalculator = false
        override fun textBeforeCursor(length: Int): CharSequence = before.takeLast(length)
        override fun textAfterCursor(length: Int): CharSequence = after.take(length)
    }

    private val defaultSystemCountry = FormatGuardChips.systemCountry

    @After fun tearDown() {
        FormatGuardChips.systemCountry = defaultSystemCountry
        latinIME.prefs().edit().clear().commit()
        val keyguard = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        shadowOf(keyguard).setIsDeviceLocked(false)
    }

    private fun load(editorInfo: EditorInfo, locale: Locale = Locale.GERMANY) {
        Settings.getInstance().loadSettings(latinIME, locale, InputAttributes(editorInfo, false, latinIME.packageName))
    }

    private fun chips(typed: String, editor: EditorInfo, behind: String = "", locale: Locale = Locale.GERMANY): List<KeyboardCommands.Chip> {
        load(editor, locale)
        host.editorInfo = editor
        before = typed
        after = behind
        return FormatGuardChips.stripChips(host)
    }

    private fun labels(chips: List<KeyboardCommands.Chip>) = chips.map { it.label.toString() }

    @Test fun fieldKindsOnlyForUnambiguousTypes() {
        assertEquals(FieldKind.EMAIL, FormatGuardChips.fieldKind(email))
        assertEquals(FieldKind.EMAIL, FormatGuardChips.fieldKind(webEmail))
        assertEquals(FieldKind.PHONE, FormatGuardChips.fieldKind(phone))
        assertEquals(FieldKind.DECIMAL, FormatGuardChips.fieldKind(decimal))
        assertEquals(FieldKind.TEXT, FormatGuardChips.fieldKind(text))
        assertNull(FormatGuardChips.fieldKind(field(InputType.TYPE_CLASS_NUMBER)))
        assertNull(FormatGuardChips.fieldKind(field(InputType.TYPE_CLASS_DATETIME)))
        assertNull(FormatGuardChips.fieldKind(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)))
        for (type in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            assertNull(FormatGuardChips.fieldKind(field(InputType.TYPE_CLASS_TEXT or type)), "type $type")
        assertNull(FormatGuardChips.fieldKind(field(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            or InputType.TYPE_NUMBER_FLAG_DECIMAL)))
        assertNull(FormatGuardChips.fieldKind(null))
    }

    @Test fun chipsOnlyForTheRightInputType() {
        assertEquals(listOf("gmail.com?"), labels(chips("max@gmial.com", email)))
        assertEquals(listOf("gmail.com?"), labels(chips("max@gmial.com", webEmail)))
        // the same text elsewhere: no e-mail hint
        for (editor in listOf(text, phone, decimal, field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)))
            assertEquals(emptyList(), chips("max@gmial.com", editor), "type ${editor.inputType}")
        assertEquals(listOf("📞 +49 1512 3456789"), labels(chips("015123456789", phone)))
        assertEquals(emptyList(), chips("015123456789", text))
        assertEquals(emptyList(), chips("015123456789", email))
        assertEquals(listOf(latinIME.getString(R.string.agent_format_decimal, "12,5")), labels(chips("12.5", decimal)))
        assertEquals(emptyList(), chips("12.5", field(InputType.TYPE_CLASS_NUMBER)))
        assertEquals(emptyList(), chips("12.5", text))
    }

    @Test fun neverInPasswordFieldsIncognitoOrLocked() {
        for (type in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            assertEquals(emptyList(), chips("DE89370400440532013000", field(InputType.TYPE_CLASS_TEXT or type)))
        assertEquals(emptyList(), chips("12.5", field(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)))
        // incognito
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, true).commit()
        assertEquals(emptyList(), chips("max@gmial.com", email))
        latinIME.prefs().edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, false).commit()
        // a field that forbids learning counts as incognito
        val noLearning = field(email.inputType).apply { imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING }
        assertEquals(emptyList(), chips("max@gmial.com", noLearning))
        // locked
        val keyguard = latinIME.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        shadowOf(keyguard).setIsDeviceLocked(true)
        assertEquals(emptyList(), chips("max@gmial.com", email))
        shadowOf(keyguard).setIsDeviceLocked(false)
        assertEquals(listOf("gmail.com?"), labels(chips("max@gmial.com", email)))
        // no settings (e.g. before the first unlock)
        assertTrue(FormatGuardChips.isBlocked(null, email))
        assertEquals(emptyList(), FormatGuardChips.stripChips(host, null))
    }

    @Test fun noLearningFlagBlocksPhoneAndNumberFieldsToo() {
        // review of stage 41: InputAttributes folds the flag into incognito only for text fields
        val phoneNoLearning = field(InputType.TYPE_CLASS_PHONE).apply { imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING }
        val decimalNoLearning = field(decimal.inputType).apply { imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING }
        assertEquals(emptyList(), chips("015123456789", phoneNoLearning))
        assertTrue(FormatGuardChips.isBlocked(Settings.getValues(), phoneNoLearning))
        assertEquals(emptyList(), chips("12.5", decimalNoLearning))
        assertTrue(FormatGuardChips.isBlocked(Settings.getValues(), decimalNoLearning))
        // the same fields without the flag still get their hint
        assertEquals(1, chips("015123456789", phone).size)
        assertFalse(FormatGuardChips.isBlocked(Settings.getValues(), phone))
        assertEquals(1, chips("12.5", decimal).size)
    }

    @Test fun phoneCountryFromTheSubtypeThenTheSystemThenTheLanguage() {
        // a German keyboard without a country on an Austrian system: an Austrian mobile number
        FormatGuardChips.systemCountry = { "AT" }
        assertEquals(listOf("\uD83D\uDCDE +43 664 1234567"), labels(chips("0664 1234567", phone, locale = Locale.GERMAN)))
        // the keyboard's own country wins over the system's
        assertEquals(listOf("\uD83D\uDCDE +41 44 668 18 00"), labels(chips("044 668 18 00", phone, locale = Locale("de", "CH"))))
        // and the field's hint locale over both
        val swiss = field(phone.inputType).apply { hintLocales = android.os.LocaleList(Locale("de", "CH")) }
        assertEquals(listOf("\uD83D\uDCDE +41 44 668 18 00"), labels(chips("044 668 18 00", swiss, locale = Locale("de", "AT"))))
        // no country anywhere: the language decides (German: Germany)
        FormatGuardChips.systemCountry = { "" }
        assertEquals(listOf("\uD83D\uDCDE +49 1512 3456789"), labels(chips("015123456789", phone, locale = Locale.GERMAN)))
        load(phone, Locale("de", "AT"))
        assertEquals("AT", FormatGuardChips.country(null, Settings.getValues()))
        assertEquals("", FormatGuardChips.country(null, null))
    }

    @Test fun switchOffShowsNothing() {
        latinIME.prefs().edit().putBoolean(AgentPreferences.FORMAT_GUARD, false).commit()
        assertEquals(emptyList(), chips("max@gmial.com", email))
        latinIME.prefs().edit().putBoolean(AgentPreferences.FORMAT_GUARD, true).commit()
        assertEquals(1, chips("max@gmial.com", email).size)
    }

    @Test fun onlyTheWholeField() {
        assertEquals(emptyList(), chips("max@gmial.com", email, behind = "x"))
        assertEquals(emptyList(), chips("x".repeat(dev.agentkeyboard.core.format.FormatGuard.MAX_LENGTH) + "@gmial.com", email))
        assertEquals(emptyList(), chips("", email))
    }

    @Test fun buildingChipsChangesNothingAndATapAppliesExactlyOnce() {
        val shown = chips("max@gmial.com", email)
        assertEquals(emptyList(), replaced, "no automatic change")
        assertEquals("max@gmial.com", before)
        shown.single().onTap()
        assertEquals(listOf("max@gmail.com"), replaced)
        assertEquals("max@gmail.com", before)
        shown.single().onTap() // the old chip: the field changed, nothing happens
        assertEquals(listOf("max@gmail.com"), replaced)
        assertEquals("max@gmail.com", before)
        assertEquals(emptyList(), FormatGuardChips.stripChips(host))
    }

    @Test fun aTapAfterTextWasAddedBehindTheCursorChangesNothing() {
        val shown = chips("max@gmial.com", email)
        after = " und mehr" // e.g. pasted behind the cursor after the chips were built
        shown.single().onTap()
        assertEquals(emptyList(), replaced)
        assertEquals("max@gmial.com", before)
        after = ""
        shown.single().onTap()
        assertEquals(listOf("max@gmail.com"), replaced)
    }

    @Test fun ibanDotIsGreenOrRedAndOnlyAValidOneGroups() {
        val valid = chips("DE89370400440532013000", text).single()
        assertEquals("● " + latinIME.getString(R.string.agent_format_iban_group), valid.label.toString())
        assertEquals(KeyboardCommands.Chip.Kind.ACTION, valid.kind)
        assertEquals(0xFF34C759.toInt(), dotColor(valid))
        valid.onTap()
        assertEquals("DE89 3704 0044 0532 0130 00", before)
        val grouped = FormatGuardChips.stripChips(host).single()
        assertEquals("● " + latinIME.getString(R.string.agent_format_iban_valid), grouped.label.toString())
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, grouped.kind)
        grouped.onTap()
        assertEquals(listOf("DE89 3704 0044 0532 0130 00"), replaced)

        val wrong = chips("AT611904300234573202", text).single()
        assertEquals("● " + latinIME.getString(R.string.agent_format_iban_invalid), wrong.label.toString())
        assertEquals(0xFFFF453A.toInt(), dotColor(wrong))
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, wrong.kind)
        wrong.onTap()
        assertEquals("AT611904300234573202", before)
        assertEquals(1, replaced.size)
        // Spanish IBAN in the field of a German keyboard
        assertEquals(0xFF34C759.toInt(), dotColor(chips("ES9121000418450200051332", text).single()))
    }

    @Test fun decimalNoteNeverChangesTheField() {
        val note = chips("3.50", decimal).single()
        assertEquals(KeyboardCommands.Chip.Kind.MUTED, note.kind)
        note.onTap()
        assertEquals("3.50", before)
        assertEquals(emptyList(), replaced)
        assertFalse(note.description.isEmpty())
    }

    @Test fun fieldLanguageComesFromTheHintLocale() {
        val english = field(decimal.inputType).apply {
            hintLocales = android.os.LocaleList(Locale.US)
        }
        assertEquals(emptyList(), chips("3.50", english))
        assertEquals(listOf(latinIME.getString(R.string.agent_format_decimal, "3.50")), labels(chips("3,50", english)))
    }

    private fun dotColor(chip: KeyboardCommands.Chip): Int =
        (chip.label as Spanned).getSpans(0, 1, ForegroundColorSpan::class.java).single().foregroundColor
}
