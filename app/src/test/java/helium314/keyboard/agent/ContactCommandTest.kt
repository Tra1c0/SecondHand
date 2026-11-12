// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
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
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class ContactCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<Pair<String, String>>()
    private var refreshed = 0

    private val anna = ContactCommand.Contact(1, "Anna Müller")
    private val ann = ContactCommand.Contact(2, "Ann")
    private val hannah = ContactCommand.Contact(3, "Hannah Berg")
    private val bob = ContactCommand.Contact(4, "Bob Annberg")
    private val zoe = ContactCommand.Contact(5, "Zoë Anders")

    private val queries = mutableListOf<String>()
    private val fieldReads = mutableListOf<Long>()
    private val source = object : ContactCommand.Source {
        override fun find(context: Context, query: String, limit: Int): List<ContactCommand.Contact> {
            queries += query
            assertTrue(limit <= ContactCommand.QUERY_LIMIT)
            return listOf(hannah, bob, zoe, anna, ann)
        }
        override fun fields(context: Context, contactId: Long, limit: Int): List<ContactCommand.Field> {
            fieldReads += contactId
            return if (contactId != anna.id) emptyList() else listOf(
                ContactCommand.Field(ContactCommand.Kind.PHONE, "+49 170 1234567", "Mobile"),
                ContactCommand.Field(ContactCommand.Kind.EMAIL, "anna@example.org", "Work"),
                ContactCommand.Field(ContactCommand.Kind.ADDRESS, "Hauptstr. 1, 10115 Berlin", "Home"),
            )
        }
    }

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = editor
        override val language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            replaced += command.text to replacement
            return true
        }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
        override var cursor = 20
        override fun refresh() { refreshed++ }
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        ContactCommand.source = source
        shadowOf(latinIME.application).grantPermissions(Manifest.permission.READ_CONTACTS)
    }

    @After fun tearDown() {
        ContactCommand.source = ContactCommand.PhoneContacts
        KeyboardCommands.load(latinIME)
    }

    private fun call(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor) as CommandCall
    private fun labels(chips: List<KeyboardCommands.Chip>) = chips.map { it.label.toString() }

    @Test fun contactsAreRankedByHowTheNameMatches() {
        assertEquals(listOf("Ann", "Anna Müller", "Bob Annberg", "Hannah Berg"),
            ContactCommand.rank("ann", listOf(hannah, bob, zoe, anna, ann)).map { it.name })
        // case and accents do not matter, every typed word must start a word of the name
        assertEquals(listOf("Anna Müller"), ContactCommand.rank("MULL an", listOf(hannah, anna, ann)).map { it.name })
        assertEquals(listOf("Zoë Anders"), ContactCommand.rank("zoe", listOf(zoe, ann)).map { it.name })
        assertTrue(ContactCommand.rank("  ", listOf(ann)).isEmpty())
        assertTrue(ContactCommand.rank("xyz", listOf(ann)).isEmpty())
        val many = (1..20L).map { ContactCommand.Contact(it, "Ann $it") }
        assertEquals(ContactCommand.MAX_CONTACTS, ContactCommand.rank("ann", many).size)
    }

    @Test fun choosingAContactShowsItsFieldsAndAFieldReplacesTheCommand() {
        val call = call("Hallo @contact Ann")
        val contacts = KeyboardCommands.chipsFor(call, host)
        assertEquals(listOf("Ann", "Anna Müller", "Bob Annberg", "Hannah Berg"), labels(contacts))
        assertEquals(listOf("Ann"), queries)
        assertTrue(fieldReads.isEmpty()) // fields are only read for a chosen contact
        assertEquals(latinIME.getString(R.string.agent_command_contact_show, "Anna Müller"), contacts[1].description)
        assertTrue(replaced.isEmpty())

        contacts[1].onTap()
        assertEquals(1, refreshed)
        assertTrue(replaced.isEmpty()) // choosing a contact changes nothing in the field
        val fields = KeyboardCommands.chipsFor(call, host)
        assertEquals(listOf("←", "Anna Müller", "+49 170 1234567", "anna@example.org", "Hauptstr. 1, 10115 Berlin"), labels(fields))
        assertEquals(latinIME.getString(R.string.agent_command_contact_insert, "Mobile", "+49 170 1234567"), fields[2].description)
        assertEquals(listOf(anna.id), fieldReads)
        KeyboardCommands.chipsFor(call, host)
        assertEquals(listOf(anna.id), fieldReads) // kept in memory while the command stays
        assertEquals(listOf("Ann"), queries)

        fields[2].onTap()
        assertEquals(listOf("@contact Ann" to "+49 170 1234567"), replaced)
        // after inserting, the same text shows the list again
        assertEquals("Ann", labels(KeyboardCommands.chipsFor(call, host)).first())
    }

    @Test fun theBackChipAndAChangedTextShowTheContactsAgain() {
        val call = call("@contact Ann")
        KeyboardCommands.chipsFor(call, host)[1].onTap()
        val back = KeyboardCommands.chipsFor(call, host).first()
        back.onTap()
        assertEquals(2, refreshed)
        assertEquals("Ann", labels(KeyboardCommands.chipsFor(call, host)).first())

        KeyboardCommands.chipsFor(call, host)[1].onTap()
        // one more letter: the contact list for the new text, and the choice is gone
        assertEquals(listOf("Anna Müller", "Hannah Berg"), labels(KeyboardCommands.chipsFor(call("@contact Anna"), host)))
        assertEquals("Ann", labels(KeyboardCommands.chipsFor(call, host)).first())

        KeyboardCommands.chipsFor(call, host)[1].onTap()
        host.cursor = 3 // the same text somewhere else
        assertEquals("Ann", labels(KeyboardCommands.chipsFor(call, host)).first())
    }

    @Test fun withoutPermissionOnlyTheAllowChipIsShown() {
        shadowOf(latinIME.application).denyPermissions(Manifest.permission.READ_CONTACTS)
        for (before in listOf("@contact", "@contact Ann")) {
            val chips = KeyboardCommands.chipsFor(call(before), host)
            assertEquals(listOf(latinIME.getString(R.string.agent_command_contact_allow)), labels(chips))
        }
        assertTrue(queries.isEmpty())
        assertTrue(fieldReads.isEmpty())
        KeyboardCommands.chipsFor(call("@contact Ann"), host).single().onTap()
        val intent = shadowOf(latinIME.application).nextStartedActivity
        assertEquals(SettingsDestination.AgentCommands, intent.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertTrue(SettingsDestination.AgentCommands in AgentPreferences.SETTINGS_DESTINATIONS)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TASK, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertTrue(replaced.isEmpty())
    }

    @Test fun blockedFieldsReadNothing() {
        val typed = call("@contact Ann")
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        assertNull(KeyboardCommands.find("@contact Ann", Settings.getValues(), editor))
        // even when asked directly, the command reads nothing there
        assertTrue(ContactCommand.chips(host, typed).isEmpty())
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertNull(KeyboardCommands.find("@contact Ann", null, editor)) // no settings: treated as blocked
        assertTrue(queries.isEmpty())
        assertTrue(fieldReads.isEmpty())
        // an empty name reads nothing either and leaves the normal strip
        assertTrue(KeyboardCommands.chipsFor(call("@contact "), host).isEmpty())
        assertTrue(queries.isEmpty())
        // switched off, the command is not recognized
        KeyboardCommands.setCommandEnabled(latinIME, "contact", false)
        assertNull(KeyboardCommands.find("@contact Ann", Settings.getValues(), editor))
    }

    @Test fun theTypedNameIsProtectedFromCorrection() {
        assertTrue(ContactCommand.protectsArgument)
        assertTrue(KeyboardCommands.guardsTextBefore("Hi @contact Ann"))
        assertTrue(KeyboardCommands.guardsTextBefore("@contact Anna Mül"))
        assertFalse(KeyboardCommands.guardsTextBefore("@math 3+4")) // math does not protect its argument
        KeyboardCommands.updateGuard("@contact Ann")
        assertTrue(KeyboardCommands.suppressesCorrection())
        KeyboardCommands.updateGuard("contact Ann")
        assertFalse(KeyboardCommands.suppressesCorrection())
    }

    @Test fun forgettingDropsWhatWasFound() {
        val call = call("@contact Ann")
        KeyboardCommands.chipsFor(call, host)[1].onTap()
        KeyboardCommands.forget()
        assertEquals("Ann", labels(KeyboardCommands.chipsFor(call, host)).first())
        assertEquals(listOf("Ann", "Ann"), queries) // asked again: nothing was kept
    }
}
