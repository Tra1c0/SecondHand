// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.util.TimeZone
import kotlin.test.*

/** The stage 24f commands `@datum`, `@termin` and `@ort`. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class DatePlaceCommandsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val text = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<Pair<String, String>>()
    private val launched = mutableListOf<Intent>()
    private var starts = true
    private var placeReads = 0

    private val host = object : KeyboardCommands.Host {
        override val context: Context get() = latinIME
        override val editorInfo: EditorInfo get() = text
        override var language = "de"
        override fun replace(command: CommandContext, replacement: String): Boolean {
            replaced += command.text to replacement
            return true
        }
        override fun openCalculator(command: CommandCall) = false
        override val canOpenCalculator = false
    }

    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        credentialContext(latinIME).getSharedPreferences(SavedPlaces.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        // Thursday, 12 November 2026, 18:45 in Berlin
        DateCommand.clock = { NOW }
        DateCommand.zone = { berlin }
        val fake = SearchCommand.Launcher { _, intent -> launched += intent; starts }
        EventCommand.launcher = fake
        PlaceCommand.launcher = fake
        SavedPlaces.available = { placeReads++; true }
    }

    @After fun tearDown() {
        DateCommand.clock = { System.currentTimeMillis() }
        DateCommand.zone = { TimeZone.getDefault() }
        EventCommand.launcher = SearchCommand.SYSTEM_LAUNCHER
        PlaceCommand.launcher = SearchCommand.SYSTEM_LAUNCHER
        SavedPlaces.available = { helium314.keyboard.agent.security.credentialAccessAllowed(it) }
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    private fun call(before: String, editor: EditorInfo = text) = KeyboardCommands.find(before, Settings.getValues(), editor) as CommandCall
    private fun chips(before: String) = KeyboardCommands.chipsFor(call(before), host)
    private fun labels(before: String) = chips(before).map { it.label.toString() }
    /** The command's own chips, without the templates shown after them while the argument is empty. */
    private fun own(before: String) = chips(before).filter { it.kind != KeyboardCommands.Chip.Kind.TEMPLATE }.map { it.label.toString() }

    @Test fun commandsAreBuiltInWithDescriptionsAndProtectTheirArgument() {
        for (name in listOf("datum", "termin", "ort")) {
            val command = KeyboardCommands.BUILT_IN.single { it.name == name }
            assertTrue("@$name" in command.description(latinIME), name)
            assertTrue(KeyboardCommands.guardsTextBefore("hi @$name morgen Kino"), name)
            assertNull(command.permission, name)
        }
        KeyboardCommands.setPrefix(latinIME, '#')
        assertTrue("#termin" in EventCommand.description(latinIME))
        assertTrue("#ort" in PlaceCommand.description(latinIME))
    }

    @Test fun dateOffersTodayInTheFormatsOfTheKeyboardLanguage() {
        val expected = listOf("12.11.2026", "Donnerstag, 12. November 2026", "2026-11-12", "18:45", "12.11.2026, 18:45")
        assertEquals(expected, own("Heute ist @datum"))
        assertEquals(expected, own("@datum "))
        host.language = "en"
        assertEquals(listOf("11/12/2026", "Thursday, November 12, 2026", "2026-11-12", "6:45 PM", "11/12/2026, 6:45 PM"), own("@datum"))
        host.language = "es"
        assertEquals(listOf("12/11/2026", "jueves, 12 de noviembre de 2026", "2026-11-12", "18:45", "12/11/2026, 18:45"), own("@datum"))
    }

    @Test fun dateTapReplacesExactlyTheCommandText() {
        val chips = chips("Termin am @datum morgen")
        assertEquals(listOf("13.11.2026", "Freitag, 13. November 2026", "2026-11-13"), chips.map { it.label.toString() })
        assertEquals(latinIME.getString(R.string.agent_command_insert_text, "13.11.2026"), chips[0].description)
        assertTrue(replaced.isEmpty()) // nothing before the tap
        chips[1].onTap()
        assertEquals(listOf("@datum morgen" to "Freitag, 13. November 2026"), replaced)
    }

    @Test fun dateReadsOtherDays() {
        assertEquals("15.11.2026", labels("@datum +3").first())
        assertEquals("13.11.2026", labels("@datum freitag").first())
        assertEquals("24.12.2026", labels("@datum 24.12.").first())
        assertEquals("01.01.2027", labels("@datum 1.1.").first())
        assertEquals(listOf("14.11.2026", "Samstag, 14. November 2026", "2026-11-14", "19:30", "14.11.2026, 19:30"),
            labels("@datum samstag halb acht"))
        assertTrue(labels("@datum quatsch").isEmpty())
        assertTrue(labels("@datum morgen Kino").isEmpty()) // leftover words: that is @termin
        assertTrue(labels("@datum 31.2.").isEmpty())
    }

    @Test fun eventChipOpensTheCalendarDraftOnlyOnTap() {
        val chips = chips("Erinnerung: @termin Samstag halb acht Kino")
        val label = latinIME.getString(R.string.agent_command_event_chip, "Sa 14.11. 19:30", "Kino")
        assertEquals(listOf(label), chips.map { it.label.toString() })
        assertTrue(launched.isEmpty() && replaced.isEmpty())
        chips.single().onTap()
        val intent = launched.single()
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("Kino", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(SATURDAY_19_30, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(SATURDAY_19_30 + 3_600_000, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
        assertFalse(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(listOf("@termin Samstag halb acht Kino" to ""), replaced)
    }

    @Config(qualifiers = "de")
    @Test fun eventChipLabelInGerman() {
        assertEquals(listOf("Termin: Sa 14.11. 19:30 – Kino"), labels("@termin Samstag halb acht Kino"))
        assertEquals(listOf("Termin: Fr 13.11. 18:00"), labels("@termin morgen 18 Uhr"))
    }

    @Test fun eventOtherLanguagesAndAllDay() {
        host.language = "en"
        assertEquals(listOf(latinIME.getString(R.string.agent_command_event_chip, "Fri 11/13 6:00 PM", "dinner")),
            labels("@termin tomorrow 6pm dinner"))
        host.language = "es"
        assertEquals(listOf(latinIME.getString(R.string.agent_command_event_chip, "vie 13/11 08:00", "cena")),
            labels("@termin mañana a las 8 cena"))
        host.language = "de"
        chips("@termin 24.12. Bescherung").single().onTap()
        val intent = launched.single()
        assertTrue(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        assertEquals(DECEMBER_24_MIDNIGHT, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(DECEMBER_25_MIDNIGHT, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
        assertEquals("Bescherung", intent.getStringExtra(CalendarContract.Events.TITLE))
    }

    @Test fun eventWithoutCalendarAppKeepsTheCommand() {
        starts = false
        chips("@termin morgen 18 Uhr Zahnarzt").single().onTap()
        assertEquals(1, launched.size)
        assertTrue(replaced.isEmpty())
        assertEquals(latinIME.getString(R.string.agent_command_event_none), ShadowToast.getTextOfLatestToast())
    }

    @Test fun eventWithoutDateOrWithAnImpossibleOneShowsNothing() {
        assertTrue(own("@termin").isEmpty()) // only templates (CommandTemplatesTest)
        assertTrue(chips("@termin Zahnarzt").isEmpty())
        assertTrue(chips("@termin 31.2. Feier").isEmpty())
        assertTrue(chips("@termin morgen 25 Uhr Feier").isEmpty())
        assertTrue(launched.isEmpty())
    }

    @Test fun eventDraftWithoutTitleLeavesTheTitleOut() {
        val intent = CalendarToolLauncher.eventDraft(" ", 1L, 2L, allDay = false)
        assertFalse(intent.hasExtra(CalendarContract.Events.TITLE))
        assertEquals(1L, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
    }

    @Test fun placeInsertsAMapsLinkOrOpensAMapsApp() {
        val chips = chips("Treffpunkt @ort Hauptstraße 1, Berlin")
        assertEquals(listOf(
            latinIME.getString(R.string.agent_command_place_chip, "Hauptstraße 1, Berlin"),
            latinIME.getString(R.string.agent_command_place_open),
        ), chips.map { it.label.toString() })
        assertTrue(launched.isEmpty() && replaced.isEmpty())
        chips[0].onTap()
        assertEquals(listOf("@ort Hauptstraße 1, Berlin" to "https://www.google.com/maps/search/?api=1&query=Hauptstra%C3%9Fe+1%2C+Berlin"), replaced)
        assertTrue(launched.isEmpty()) // the link is only inserted
        replaced.clear()
        chips[1].onTap()
        val intent = launched.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo:0,0?q=Hauptstra%C3%9Fe%201%2C%20Berlin", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(listOf("@ort Hauptstraße 1, Berlin" to ""), replaced)
    }

    @Test fun placeWithoutMapsAppKeepsTheCommand() {
        starts = false
        chips("@ort Berlin")[1].onTap()
        assertTrue(replaced.isEmpty())
        assertEquals(latinIME.getString(R.string.agent_command_place_none), ShadowToast.getTextOfLatestToast())
    }

    @Test fun savedPlacesStandForTheirAddress() {
        assertTrue(chips("@ort").isEmpty())
        assertEquals(SavedPlaces.SaveResult.SAVED, SavedPlaces.save(latinIME, "Zuhause", "Musterweg 5, Köln"))
        assertEquals(SavedPlaces.SaveResult.SAVED, SavedPlaces.save(latinIME, "Arbeit", "Domplatz 1, Köln"))
        assertEquals(SavedPlaces.SaveResult.INVALID, SavedPlaces.save(latinIME, "", "x"))
        val chips = chips("@ort zuhause")
        assertEquals(latinIME.getString(R.string.agent_command_place_chip, "Zuhause – Musterweg 5, Köln"), chips[0].label.toString())
        chips[0].onTap()
        assertEquals(listOf("@ort zuhause" to "https://www.google.com/maps/search/?api=1&query=Musterweg+5%2C+K%C3%B6ln"), replaced)
        chips[1].onTap()
        assertEquals("geo:0,0?q=Musterweg%205%2C%20K%C3%B6ln", launched.single().dataString)
        // without an argument: one link chip per saved place
        assertEquals(listOf("Zuhause – Musterweg 5, Köln", "Arbeit – Domplatz 1, Köln").map {
            latinIME.getString(R.string.agent_command_place_chip, it)
        }, labels("@ort "))
        assertTrue(SavedPlaces.delete(latinIME, "ARBEIT"))
        assertEquals(1, labels("@ort").size)
    }

    @Test fun savedPlacesLiveInCredentialStorageOnly() {
        SavedPlaces.save(latinIME, "Zuhause", "Musterweg 5")
        val stored = credentialContext(latinIME).getSharedPreferences(SavedPlaces.FILE, Context.MODE_PRIVATE)
        assertTrue(stored.getString("places", "")!!.contains("Musterweg 5"))
        assertFalse(latinIME.prefs().all.values.any { it.toString().contains("Musterweg") })
        SavedPlaces.available = { false }
        assertTrue(SavedPlaces.load(latinIME).isEmpty())
        assertEquals(SavedPlaces.SaveResult.UNAVAILABLE, SavedPlaces.save(latinIME, "Arbeit", "Domplatz"))
        assertEquals(listOf(latinIME.getString(R.string.agent_command_place_chip, "zuhause"), latinIME.getString(R.string.agent_command_place_open)),
            labels("@ort zuhause")) // locked: the word itself is the query
    }

    @Test fun blockedFieldsReadNothing() {
        SavedPlaces.save(latinIME, "Zuhause", "Musterweg 5")
        placeReads = 0
        val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        for (typed in listOf("@ort zuhause", "@termin morgen 18 Uhr Kino", "@datum")) {
            assertNull(KeyboardCommands.find(typed, Settings.getValues(), password), typed)
            assertNull(KeyboardCommands.find(typed, Settings.getValues(), null), typed)
        }
        assertEquals(0, placeReads)
        assertTrue(launched.isEmpty() && replaced.isEmpty())
    }

    @Test fun savedPlacesAreReadOncePerCommandAndAgainAfterAChange() {
        SavedPlaces.save(latinIME, "Zuhause", "Musterweg 5")
        placeReads = 0
        labels("@ort z"); labels("@ort zu"); labels("@ort zuh")
        assertEquals(1, placeReads) // strip updates for the same command read nothing again
        SavedPlaces.save(latinIME, "Arbeit", "Domplatz 1")
        placeReads = 0
        assertEquals(2, labels("@ort ").size) // the change is seen at once
        assertEquals(1, placeReads)
        KeyboardCommands.forget()
        labels("@ort ")
        assertEquals(2, placeReads)
    }

    @Test fun thePlaceCommandItselfReadsNothingInBlockedFields() {
        SavedPlaces.save(latinIME, "Zuhause", "Musterweg 5")
        val typed = call("@ort zuhause")
        placeReads = 0
        val password = object : KeyboardCommands.Host by host {
            override val editorInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        }
        assertTrue(PlaceCommand.chips(password, typed).isEmpty())
        assertEquals(0, placeReads)
    }

    private companion object {
        const val NOW = 1_794_505_500_000L // 2026-11-12T17:45Z
        const val SATURDAY_19_30 = 1_794_681_000_000L // 2026-11-14T18:30Z
        const val DECEMBER_24_MIDNIGHT = 1_798_066_800_000L // 2026-12-23T23:00Z
        const val DECEMBER_25_MIDNIGHT = 1_798_153_200_000L
    }
}
