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
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class SnipCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<Pair<String, String>>()
    private var replaceSucceeds = true

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
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        SnipCommand.source = SnipCommand.Source {
            reads++
            if (failing) error("locked")
            snippets
        }
    }

    @After fun tearDown() {
        SnipCommand.source = SnipCommand.Source { SnippetStore(it).read() }
        KeyboardCommands.load(latinIME)
    }

    private fun call(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor) as CommandCall
    private fun labels(chips: List<KeyboardCommands.Chip>) = chips.map { it.label.toString() }

    @Test fun isBuiltInAndProtectsItsArgument() {
        assertTrue(SnipCommand in KeyboardCommands.BUILT_IN)
        assertTrue(SnipCommand.protectsArgument)
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
        for (bad in listOf("{}", """{"storageVersion":2,"snippets":[]}""",
            """{"storageVersion":1,"snippets":[{"name":"a b","text":"x"}]}""",
            """{"storageVersion":1,"snippets":[{"name":"a","text":"x"},{"name":"A","text":"y"}]}""",
            """{"storageVersion":1,"snippets":[{"name":"a","text":"x","url":"y"}]}""")) {
            assertFails(bad) { SnippetStore.decode(bad) }
        }
    }
}
