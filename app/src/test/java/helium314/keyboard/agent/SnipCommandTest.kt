// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.commands.SensitiveClips
import helium314.keyboard.agent.commands.SnipCommand
import helium314.keyboard.agent.commands.Snippet
import helium314.keyboard.agent.commands.SnippetStore
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsDestination
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import dev.agentkeyboard.core.CivilDate
import dev.agentkeyboard.core.ClockTime
import dev.agentkeyboard.core.LocalMoment
import dev.agentkeyboard.core.SnippetApps
import dev.agentkeyboard.core.SnippetTemplate
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.util.TimeZone
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class SnipCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<Pair<String, String>>()
    private var replaceSucceeds = true
    private var canReplace = true

    private val address = Snippet("adresse", "Anna Müller\nHauptstraße 1\n10115 Berlin")
    private val iban = Snippet("iban", "DE02 1203 0000 0000 2020 51")
    private val mail = Snippet("Mailadr", "anna@example.org")
    private var snippets = listOf(address, iban, mail)
    private var reads = 0
    private var failing = false

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = editor
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            replaced += command.text to replacement
            return replaceSucceeds
        }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
        override fun moveCursorBack(count: Int): Boolean { moved += count; return true }
        override val canReplaceNow: Boolean get() = canReplace
    }
    private val moved = mutableListOf<Int>()
    private var clip: SnipCommand.ClipRead = SnipCommand.ClipRead.Missing(SnipCommand.ClipRead.Reason.EMPTY)
    private var clipReads = 0
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    // Thursday, 12 November 2026, 18:45 in Berlin
    private val now = LocalMoment(CivilDate(2026, 11, 12), ClockTime(18, 45)).toEpochMillis(berlin)

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        SnipCommand.source = SnipCommand.Source {
            reads++
            if (failing) error("locked")
            snippets
        }
        SnipCommand.clipboard = SnipCommand.Clipboard { clipReads++; clip }
        SnipCommand.clock = { now }
        SnipCommand.zone = { berlin }
    }

    @After fun tearDown() {
        SnipCommand.source = SnipCommand.Source { SnippetStore(it).read() }
        SnipCommand.clipboard = SnipCommand.SystemClipboard
        SnipCommand.clock = { System.currentTimeMillis() }
        SnipCommand.zone = { TimeZone.getDefault() }
        KeyboardCommands.load(latinIME)
    }

    private fun call(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor) as CommandCall
    private fun labels(chips: List<KeyboardCommands.Chip>) = chips.map { it.label.toString() }

    @Test fun isBuiltInAndProtectsItsArgument() {
        assertTrue(SnipCommand in KeyboardCommands.BUILT_IN)
        assertTrue(SnipCommand.protectsArgument)
        assertTrue(SnipCommand.longArgument) // room for field values (review of stage 42)
        assertTrue(KeyboardCommands.guardsTextBefore("Hi @snip adr"))
        assertTrue(SnipCommand.description(latinIME).contains("@snip"))
    }

    @Test fun matchingPrefersTheSameNameThenTheStartThenAnyPart() {
        assertEquals(listOf("adresse", "iban", "Mailadr"), SnipCommand.match("", snippets).map { it.name })
        assertEquals(listOf("adresse", "Mailadr"), SnipCommand.match("ADR", snippets).map { it.name })
        assertEquals(listOf("iban"), SnipCommand.match(" iban ", snippets).map { it.name })
        assertTrue(SnipCommand.match("xyz", snippets).isEmpty())
        val many = (1..30).map { Snippet("a" + "b".repeat(it), "x") }
        assertEquals(SnipCommand.MAX_CHIPS, SnipCommand.match("a", many).size)
    }

    @Test fun previewIsOneShortLine() {
        assertEquals("Anna Müller Hauptstraße 1 1011…", SnipCommand.preview(address.text))
        assertEquals("kurz", SnipCommand.preview("  kurz \n"))
    }

    @Test fun aTapReplacesExactlyTheCommandByTheWholeSnippet() {
        val chips = KeyboardCommands.chipsFor(call("Meine Adresse: @snip adr"), host)
        assertEquals(listOf("adresse: Anna Müller Hauptstraße 1 1011…", "Mailadr: anna@example.org"), labels(chips))
        assertEquals(latinIME.getString(R.string.agent_command_snip_insert, "adresse"), chips[0].description)
        assertTrue(replaced.isEmpty()) // nothing happens without a tap
        chips[0].onTap()
        assertEquals(listOf("@snip adr" to address.text), replaced)
    }

    @Test fun withoutArgumentAllSnippetsAreListed() {
        assertEquals(3, KeyboardCommands.chipsFor(call("@snip"), host).size)
        assertEquals(3, KeyboardCommands.chipsFor(call("@snip "), host).size)
        assertTrue(KeyboardCommands.chipsFor(call("@snip zzz"), host).isEmpty()) // the normal strip stays
        assertTrue(replaced.isEmpty())
    }

    @Test fun snippetsAreReadOncePerCommandAndForgottenAfterwards() {
        KeyboardCommands.chipsFor(call("@snip a"), host)
        KeyboardCommands.chipsFor(call("@snip ad"), host)
        assertEquals(1, reads)
        KeyboardCommands.forget()
        KeyboardCommands.chipsFor(call("@snip ad"), host)
        assertEquals(2, reads)
        // a successful insert forgets as well
        KeyboardCommands.chipsFor(call("@snip ad"), host)[0].onTap()
        KeyboardCommands.chipsFor(call("@snip ad"), host)
        assertEquals(3, reads)
    }

    @Test fun withoutSnippetsOneChipOpensTheCommandSettings() {
        snippets = emptyList()
        val chip = KeyboardCommands.chipsFor(call("@snip"), host).single()
        assertEquals(latinIME.getString(R.string.agent_command_snip_add), chip.label)
        chip.onTap()
        val intent = shadowOf(latinIME.application).nextStartedActivity
        assertEquals(SettingsDestination.AgentCommands, intent.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TASK, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertTrue(replaced.isEmpty())
    }

    @Test fun unreadableStorageShowsNothing() {
        failing = true
        assertTrue(KeyboardCommands.chipsFor(call("@snip"), host).isEmpty())
    }

    @Test fun blockedFieldsReadNothing() {
        val typed = call("@snip adr")
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        assertNull(KeyboardCommands.find("@snip adr", Settings.getValues(), editor))
        assertTrue(SnipCommand.chips(host, typed).isEmpty())
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }
        assertTrue(SnipCommand.chips(host, typed).isEmpty())
        assertEquals(0, reads)
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        KeyboardCommands.setCommandEnabled(latinIME, "snip", false)
        assertNull(KeyboardCommands.find("@snip adr", Settings.getValues(), editor))
    }

    // stage 42

    private val invitation = Snippet("einladung", "Hallo {?Name}, danke für die Einladung am {datum+2t}, leider kann ich nicht. {cursor}")

    @Test fun aTemplateIsExpandedOnlyOnTheTapWithTheClockOfThatMoment() {
        snippets = listOf(Snippet("gruss", "Gruß vom {wochentag}, {datum-1t} um {uhrzeit}"))
        val chip = KeyboardCommands.chipsFor(call("@snip gr"), host).single()
        assertEquals("gruss: Gruß vom {wochentag}, {datum-1…", chip.label.toString()) // the chip shows the template
        assertTrue(replaced.isEmpty())
        chip.onTap()
        assertEquals(listOf("@snip gr" to "Gruß vom Donnerstag, 11.11.2026 um 18:45"), replaced)
        assertTrue(moved.isEmpty()) // no cursor mark: the cursor stays after the text
        assertEquals(0, clipReads) // no clipboard placeholder: the clipboard is not read
    }

    @Test fun dateAndWeekdayFollowTheKeyboardLanguage() {
        snippets = listOf(Snippet("gruss", "{wochentag} {datum} {uhrzeit}"))
        fun insertIn(lang: String): String {
            replaced.clear()
            val otherHost = object : KeyboardCommands.Host by host { override val language = lang }
            KeyboardCommands.chipsFor(call("@snip gr"), otherHost).single().onTap()
            return replaced.single().second
        }
        assertEquals("Thursday 11/12/2026 6:45 PM", insertIn("en"))
        assertEquals("jueves 12/11/2026 18:45", insertIn("es"))
        assertEquals("Donnerstag 12.11.2026 18:45", insertIn("fr")) // German by default
    }

    @Test fun theCursorMarkMovesTheCursorBackAfterTheInsert() {
        snippets = listOf(Snippet("brief", "Hallo {cursor},\nviele Grüße"))
        KeyboardCommands.chipsFor(call("@snip brief"), host).single().onTap()
        assertEquals("Hallo ,\nviele Grüße", replaced.single().second)
        assertEquals(listOf(",\nviele Grüße".length), moved)
        // nothing replaced, nothing moved
        replaced.clear(); moved.clear(); replaceSucceeds = false
        KeyboardCommands.chipsFor(call("@snip brief"), host).single().onTap()
        assertTrue(moved.isEmpty())
    }

    @Test fun theOwnersExampleAsksTheNameInTheStripThenInserts() {
        snippets = listOf(invitation, address)
        KeyboardCommands.chipsFor(call("Antwort: @snip ein"), host).single().onTap()
        // the command becomes the input for the first field; nothing else changes
        assertEquals(listOf("@snip ein" to "@snip einladung Name: "), replaced)
        replaced.clear()
        val asking = KeyboardCommands.chipsFor(call("Antwort: @snip einladung Name: "), host)
        assertEquals(listOf("Name (1/1): …", latinIME.getString(R.string.agent_snip_field_insert),
            latinIME.getString(R.string.agent_snip_field_cancel)), labels(asking))
        val typed = call("Antwort: @snip einladung Name: Lena")
        assertEquals("Name (1/1): Lena", labels(KeyboardCommands.chipsFor(typed, host))[0])
        assertTrue(KeyboardCommands.guardsTextBefore("Antwort: @snip einladung Name: Lena")) // the value is not corrected
        // Enter inserts (the last field)
        assertTrue(SnipCommand.onEnter(host, typed))
        assertEquals(listOf("@snip einladung Name: Lena" to "Hallo Lena, danke für die Einladung am 14.11.2026, leider kann ich nicht. "), replaced)
        assertTrue(moved.isEmpty()) // {cursor} at the end
        // the values are gone: the same text asks nothing any more
        assertFalse(SnipCommand.onEnter(host, typed))
        assertTrue(KeyboardCommands.chipsFor(typed, host).isEmpty())
    }

    @Test fun enterMovesToTheNextFieldAndTheSameNameIsAskedOnce() {
        snippets = listOf(Snippet("termin", "{?Name}, wir sehen uns in {?Ort}. Bis dann, {?name}! {cursor}Gruß"))
        KeyboardCommands.chipsFor(call("@snip termin"), host).single().onTap()
        assertEquals("@snip termin Name: ", replaced.last().second)
        assertTrue(SnipCommand.onEnter(host, call("@snip termin Name:  Anna ")))
        assertEquals("@snip termin Name:  Anna " to "@snip termin Ort: ", replaced.last())
        assertEquals("Ort (2/2): …", labels(KeyboardCommands.chipsFor(call("@snip termin Ort: "), host))[0])
        // a tap on "Insert" works like Enter
        val chips = KeyboardCommands.chipsFor(call("@snip termin Ort: Rom"), host)
        chips.single { it.label == latinIME.getString(R.string.agent_snip_field_insert) }.onTap()
        assertEquals("@snip termin Ort: Rom" to "Anna, wir sehen uns in Rom. Bis dann, Anna! Gruß", replaced.last())
        assertEquals(listOf("Gruß".length), moved)
    }

    @Test fun aFailedStepKeepsTheFieldAndCancelGoesBackToTheCommand() {
        snippets = listOf(Snippet("termin", "{?Name} in {?Ort}"))
        KeyboardCommands.chipsFor(call("@snip termin"), host).single().onTap()
        replaceSucceeds = false
        assertTrue(SnipCommand.onEnter(host, call("@snip termin Name: Anna")))
        // the field changed meanwhile: still the first field
        assertEquals("Name (1/2): Anna", labels(KeyboardCommands.chipsFor(call("@snip termin Name: Anna"), host))[0])
        replaceSucceeds = true
        KeyboardCommands.chipsFor(call("@snip termin Name: Anna"), host)
            .single { it.label == latinIME.getString(R.string.agent_snip_field_cancel) }.onTap()
        assertEquals("@snip termin Name: Anna" to "@snip termin", replaced.last())
        assertFalse(SnipCommand.onEnter(host, call("@snip termin Name: Anna")))
    }

    @Test fun enterDuringAGestureIsTakenButChangesNothing() {
        // review of stage 42: the replacement would be refused (gesture still being processed); Enter must
        // neither reach the app (it would send the command text) nor lose the field
        snippets = listOf(Snippet("termin", "{?Name} in {?Ort}"))
        KeyboardCommands.chipsFor(call("@snip termin"), host).single().onTap()
        replaced.clear()
        canReplace = false
        assertTrue(SnipCommand.onEnter(host, call("@snip termin Name: Anna")))
        assertTrue(replaced.isEmpty())
        assertNull(ShadowToast.getLatestToast()) // no "expired" note: nothing went wrong
        assertEquals("Name (1/2): Anna", labels(KeyboardCommands.chipsFor(call("@snip termin Name: Anna"), host))[0])
        // once the gesture is done, the next Enter moves on
        canReplace = true
        assertTrue(SnipCommand.onEnter(host, call("@snip termin Name: Anna")))
        assertEquals("@snip termin Name: Anna" to "@snip termin Ort: ", replaced.single())
    }

    @Test fun longFieldValuesStayACommandAndTheChipShowsTheRoomLeft() {
        // review of stage 42: values longer than the normal argument limit (200) dropped the command
        snippets = listOf(Snippet("brief", "Text: {?Inhalt}"))
        KeyboardCommands.chipsFor(call("@snip brief"), host).single().onTap()
        val start = SnipCommand.argumentStart("brief", "Inhalt")
        val long = "x".repeat(dev.agentkeyboard.core.CommandSyntax.MAX_ARGUMENT + 50)
        val typed = call("@snip $start$long")
        assertEquals("$start$long", typed.argument)
        val label = labels(KeyboardCommands.chipsFor(typed, host))[0]
        assertFalse(label.contains(latinIME.getString(R.string.agent_snip_field_room, SnipCommand.roomLeft(typed.argument))), label)
        assertEquals("Inhalt (1/1): …" + "x".repeat(SnipCommand.LABEL_VALUE), label) // only the end of a long value
        // near the limit the chip says how much room is left
        val room = SnipCommand.ROOM_WARNING - 10
        val nearly = "y".repeat(dev.agentkeyboard.core.CommandSyntax.MAX_LONG_ARGUMENT - start.length - room)
        val full = call("@snip $start$nearly")
        assertEquals(room, SnipCommand.roomLeft(full.argument))
        assertTrue(labels(KeyboardCommands.chipsFor(full, host))[0].endsWith(" " + latinIME.getString(R.string.agent_snip_field_room, room)))
        assertTrue(SnipCommand.onEnter(host, full))
        assertEquals("Text: $nearly", replaced.last().second)
    }

    @Test fun enterWorksAsUsualWithoutAFieldAndForgottenCommandsDropTheValues() {
        snippets = listOf(invitation)
        assertFalse(SnipCommand.onEnter(host, call("@snip einladung")))
        KeyboardCommands.chipsFor(call("@snip einladung"), host).single().onTap()
        // the command is gone (cursor moved, other text): the values are dropped
        KeyboardCommands.updateGuard("ganz anderer Text")
        assertFalse(SnipCommand.onEnter(host, call("@snip einladung Name: Lena")))
        // typed by hand without the tap: no field is asked
        assertTrue(KeyboardCommands.chipsFor(call("@snip einladung Name: Lena"), host).isEmpty())
    }

    @Test fun snippetsLimitedToAppsAppearOnlyThere() {
        snippets = listOf(Snippet("wa", "nur WhatsApp", listOf("com.whatsapp")), Snippet("wo", "überall"))
        assertEquals(listOf("wo: überall"), labels(KeyboardCommands.chipsFor(call("@snip w"), host)))
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "com.whatsapp" }
        assertEquals(listOf("wa: nur WhatsApp", "wo: überall"), labels(KeyboardCommands.chipsFor(call("@snip w"), host)))
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "org.telegram.messenger" }
        assertEquals(listOf("wo: überall"), labels(KeyboardCommands.chipsFor(call("@snip w"), host)))
    }

    @Test fun theClipboardIsInsertedOnlyWhenAllowedOtherwiseEmptyWithANote() {
        snippets = listOf(Snippet("link", "Hier: {zwischenablage} ok"))
        clip = SnipCommand.ClipRead.Text("https://example.org")
        KeyboardCommands.chipsFor(call("@snip link"), host).single().onTap()
        assertEquals("Hier: https://example.org ok", replaced.last().second)
        assertEquals(1, clipReads) // read on the tap only
        for ((reason, note) in listOf(
            SnipCommand.ClipRead.Reason.SENSITIVE to R.string.agent_snip_clip_sensitive,
            SnipCommand.ClipRead.Reason.HISTORY_OFF to R.string.agent_snip_clip_history_off,
            SnipCommand.ClipRead.Reason.LOCKED to R.string.agent_snip_clip_locked,
            SnipCommand.ClipRead.Reason.EMPTY to R.string.agent_snip_clip_empty,
            SnipCommand.ClipRead.Reason.TOO_LONG to R.string.agent_snip_clip_too_long,
        )) {
            clip = SnipCommand.ClipRead.Missing(reason)
            KeyboardCommands.chipsFor(call("@snip link"), host).single().onTap()
            assertEquals("Hier:  ok", replaced.last().second, reason.name)
            assertEquals(latinIME.getString(note), ShadowToast.getTextOfLatestToast(), reason.name)
        }
    }

    @Test fun theSystemClipboardFollowsHistoryAndSensitivity() {
        val manager = latinIME.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        manager.setPrimaryClip(android.content.ClipData.newPlainText("x", "öffentlich"))
        fun read() = SnipCommand.SystemClipboard.read(latinIME)
        // the clipboard history is off by default
        assertEquals(SnipCommand.ClipRead.Missing(SnipCommand.ClipRead.Reason.HISTORY_OFF), read())
        latinIME.prefs().edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        Settings.getInstance().loadSettings(latinIME, Settings.getValues().mLocale, helium314.keyboard.latin.InputAttributes(editor, false, latinIME.packageName))
        assertEquals("öffentlich", (read() as SnipCommand.ClipRead.Text).text)
        // copied as sensitive earlier (password field or marked): never inserted
        SensitiveClips.remember(latinIME, "öffentlich")
        assertEquals(SnipCommand.ClipRead.Missing(SnipCommand.ClipRead.Reason.SENSITIVE), read())
        SensitiveClips.clear(latinIME)
        // marked sensitive by the copying app
        val marked = android.content.ClipData.newPlainText("x", "geheim").apply {
            description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        manager.setPrimaryClip(marked)
        assertEquals(SnipCommand.ClipRead.Missing(SnipCommand.ClipRead.Reason.SENSITIVE), read())
        manager.setPrimaryClip(android.content.ClipData.newPlainText("x", "x".repeat(SnippetStore.MAX_TEXT + 1)))
        assertEquals(SnipCommand.ClipRead.Missing(SnipCommand.ClipRead.Reason.TOO_LONG), read())
        manager.clearPrimaryClip()
        assertEquals(SnipCommand.ClipRead.Missing(SnipCommand.ClipRead.Reason.EMPTY), read())
        // locked device
        manager.setPrimaryClip(android.content.ClipData.newPlainText("x", "öffentlich"))
        val keyguard = latinIME.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        shadowOf(keyguard).setIsDeviceLocked(true)
        try {
            Settings.getInstance().loadSettings(latinIME, Settings.getValues().mLocale, helium314.keyboard.latin.InputAttributes(editor, false, latinIME.packageName))
            assertTrue(Settings.getValues().mIsLocked)
            assertEquals(SnipCommand.ClipRead.Missing(SnipCommand.ClipRead.Reason.LOCKED), read())
        } finally {
            shadowOf(keyguard).setIsDeviceLocked(false)
            Settings.getInstance().loadSettings(latinIME, Settings.getValues().mLocale, helium314.keyboard.latin.InputAttributes(editor, false, latinIME.packageName))
            assertFalse(Settings.getValues().mIsLocked)
        }
    }

    @Test fun nothingIsInsertedInAPasswordField() {
        snippets = listOf(Snippet("link", "{zwischenablage}"), invitation)
        val linkChip = KeyboardCommands.chipsFor(call("@snip link"), host).single()
        KeyboardCommands.chipsFor(call("@snip einladung"), host).single().onTap()
        replaced.clear()
        // the field turned into a password field after the chips were built
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        linkChip.onTap()
        assertFalse(SnipCommand.onEnter(host, CommandCall(0, "@snip einladung Name: x", '@', "snip", "einladung Name: x")))
        assertTrue(SnipCommand.chips(host, CommandCall(0, "@snip einladung Name: x", '@', "snip", "einladung Name: x")).isEmpty())
        assertTrue(replaced.isEmpty())
        assertEquals(0, clipReads)
    }

    @Test fun oldSnippetsAreInsertedExactlyAsBefore() {
        snippets = listOf(Snippet("alt", "Code {datum} und {{x}} {?Name} {cursor}", literal = true))
        KeyboardCommands.chipsFor(call("@snip alt"), host).single().onTap()
        assertEquals(listOf("@snip alt" to "Code {datum} und {{x}} {?Name} {cursor}"), replaced)
        assertTrue(moved.isEmpty())
        assertEquals(0, clipReads)
    }

    @Test fun theEditorPreviewShowsFieldsClipboardAndCursorWithoutReadingAnything() {
        val preview = SnipCommand.editorPreview(SnippetTemplate.parse(invitation.text + " {zwischenablage} {wochentag}"), "de",
            "‹Zwischenablage›", now, berlin)
        assertEquals("Hallo ‹Name›, danke für die Einladung am 14.11.2026, leider kann ich nicht. ${SnipCommand.PREVIEW_CURSOR} ‹Zwischenablage› Donnerstag",
            preview)
        assertEquals("{x}", SnipCommand.editorPreview(SnippetTemplate.parse("{x}"), "de", "c", now, berlin))
        assertEquals(0, clipReads)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SnippetStoreTest {
    private val app get() = org.robolectric.RuntimeEnvironment.getApplication()
    private var accessible = true
    private val store get() = SnippetStore(app) { accessible }
    private val file get() = File(credentialContext(app).noBackupFilesDir, SnippetStore.FILE_NAME)

    @Before fun clear() { file.delete(); File(file.path + ".bak").delete() }

    @Test fun roundTripInCredentialProtectedNoBackupStorage() {
        assertTrue(store.read().isEmpty())
        store.put(Snippet("zeta", "zwei\nZeilen"))
        store.put(Snippet("Adresse", "x".repeat(SnippetStore.MAX_TEXT)))
        assertEquals(listOf("Adresse", "zeta"), store.read().map { it.name })
        assertEquals("zwei\nZeilen", store.read()[1].text)
        assertTrue(file.exists())
        assertFalse(credentialContext(app).isDeviceProtectedStorage)
        assertTrue(file.path.contains("no_backup"))
        assertFalse(store.read()[1].toString().contains("Zeilen")) // never shows the text
    }

    @Test fun namesAreOneWordOfLettersAndUnique() {
        assertTrue(SnippetStore.isValidName("Straße"))
        for (bad in listOf("", "zwei worte", "adr1", "a-b", "x".repeat(SnippetStore.MAX_NAME + 1)))
            assertFalse(SnippetStore.isValidName(bad), bad)
        assertFalse(SnippetStore.isValidText(" \n "))
        assertFalse(SnippetStore.isValidText("x".repeat(SnippetStore.MAX_TEXT + 1)))
        assertFailsWith<IllegalArgumentException> { store.put(Snippet("adr1", "text")) }
        store.put(Snippet("adr", "eins"))
        assertFailsWith<IllegalArgumentException> { store.put(Snippet("ADR", "zwei")) }
        assertEquals("eins", store.read().single().text)
    }

    @Test fun editRenamesAndDeleteRemoves() {
        store.put(Snippet("adr", "eins"))
        store.put(Snippet("mail", "m"))
        store.put(Snippet("anschrift", "neu"), previousName = "adr")
        assertEquals(listOf("anschrift", "mail"), store.read().map { it.name })
        assertEquals("neu", store.read()[0].text)
        assertFailsWith<IllegalArgumentException> { store.put(Snippet("mail", "x"), previousName = "anschrift") }
        // an edit of a snippet deleted meanwhile does not come back as a new one
        assertFailsWith<IllegalArgumentException> { store.put(Snippet("weg", "x"), previousName = "geloescht") }
        store.delete("MAIL")
        assertEquals(listOf("anschrift"), store.read().map { it.name })
    }

    @Test fun theNumberIsBounded() {
        val letters = ('a'..'z').toList()
        repeat(SnippetStore.MAX_SNIPPETS) { store.put(Snippet("s" + letters[it / 26] + letters[it % 26], "t")) }
        assertFailsWith<IllegalArgumentException> { store.put(Snippet("eins", "t")) }
        assertEquals(SnippetStore.MAX_SNIPPETS, store.read().size)
    }

    @Test fun lockedStorageIsNeitherReadNorWritten() {
        store.put(Snippet("adr", "eins"))
        accessible = false
        assertFailsWith<IllegalStateException> { store.read() }
        assertFailsWith<IllegalStateException> { store.put(Snippet("neu", "x")) }
        accessible = true
        assertEquals(listOf("adr"), store.read().map { it.name })
    }

    @Test fun malformedFilesAreRejected() {
        // stage 42: version 2 exists now, so an unknown version is 3; version 1 knows no apps
        for (bad in listOf("{}", """{"storageVersion":3,"snippets":[]}""", """{"storageVersion":0,"snippets":[]}""",
            """{"storageVersion":1,"snippets":[{"name":"a b","text":"x"}]}""",
            """{"storageVersion":1,"snippets":[{"name":"a","text":"x"},{"name":"A","text":"y"}]}""",
            """{"storageVersion":1,"snippets":[{"name":"a","text":"x","url":"y"}]}""",
            """{"storageVersion":1,"snippets":[{"name":"a","text":"x","apps":["com.x"]}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a b","text":"x"}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a","text":"x","url":"y"}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a"}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a","text":"x","apps":[]}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a","text":"x","apps":["whatsapp"]}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a","text":"x","apps":["com.x","com.x"]}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a","text":"x","apps":[1]}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a","text":"x","apps":"com.x"}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a","text":"x","literal":false}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a","text":"x","literal":"true"}]}""",
            """{"storageVersion":2,"snippets":[{"name":"a","text":"x","apps":[${(0..SnippetApps.MAX_APPS).joinToString(",") { "\"com.a$it\"" }}]}]}""")) {
            assertFails(bad) { SnippetStore.decode(bad) }
        }
    }

    @Test fun version1FilesStayReadableAndTheirSnippetsLiteral() {
        val old = """{"storageVersion":1,"snippets":[{"name":"adr","text":"Am {datum} {{x}}"},{"name":"mail","text":"a@b.de"}]}"""
        file.parentFile!!.mkdirs()
        file.writeText(old)
        val read = store.read()
        assertEquals(listOf("adr", "mail"), read.map { it.name })
        assertTrue(read.all { it.literal && it.apps.isEmpty() })
        assertEquals("Am {datum} {{x}}", read[0].template.expand(SnippetTemplate.Values(CivilDate(2026, 1, 1), ClockTime(0, 0),
            dev.agentkeyboard.core.DateFormatting.Language.DE)).text)
        // adding another snippet keeps the old ones literal (written as version 2)
        store.put(Snippet("neu", "{datum}"))
        val again = store.read()
        assertEquals(listOf(true, true, false), again.map { it.literal })
        assertTrue(file.readText().contains("\"storageVersion\":2"))
        // saved again in the editor: a template from now on
        store.put(Snippet("adr", "Am {datum} {{x}}"), previousName = "adr")
        assertFalse(store.read().first().literal)
    }

    @Test fun version2KeepsAppsAndLooksLikeVersion1ForPlainSnippets() {
        store.put(Snippet("wa", "Hallo {?Name}", listOf("com.whatsapp", "org.telegram.messenger")))
        store.put(Snippet("plain", "x"))
        val read = store.read()
        assertEquals(listOf("com.whatsapp", "org.telegram.messenger"), read.single { it.name == "wa" }.apps)
        assertTrue(read.single { it.name == "plain" }.apps.isEmpty())
        val encoded = SnippetStore.encode(listOf(Snippet("plain", "x")))
        assertEquals("""{"storageVersion":2,"snippets":[{"name":"plain","text":"x"}]}""", encoded)
        assertEquals("""{"storageVersion":2,"snippets":[{"name":"a","text":"y","apps":["com.x"],"literal":true}]}""",
            SnippetStore.encode(listOf(Snippet("a", "y", listOf("com.x"), literal = true))))
        assertFailsWith<IllegalArgumentException> { store.put(Snippet("bad", "x", listOf("nopackage"))) }
        assertFailsWith<IllegalArgumentException> { store.put(Snippet("bad", "x", List(SnippetApps.MAX_APPS + 1) { "com.a$it" })) }
        assertFalse(Snippet("wa", "geheim", listOf("com.x")).toString().contains("geheim"))
    }
}
