// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.commands.ClipCommand
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.database.ClipboardDao
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
class ClipCommandTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private var editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<Pair<String, String>>()

    private val now = System.currentTimeMillis()
    private var clips = listOf(
        ClipCommand.Clip("Termin am Montag um 9", now - 50_000),
        ClipCommand.Clip("https://example.org/termin", now - 40_000),
        ClipCommand.Clip("geheimes Passwort Termin", now - 1_000, sensitive = true),
        ClipCommand.Clip("Einkaufsliste:\nBrot\nMilch", now - 30_000),
        ClipCommand.Clip("   ", now - 20_000),
        ClipCommand.Clip("Rechnung 2026-11", now - 10_000),
        ClipCommand.Clip("Termin am Montag um 9", now - 60_000), // the same text copied earlier
        ClipCommand.Clip("Alte Notiz", now - 90_000),
    )
    private var reads = 0

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
    }

    @Before fun setUp() {
        latinIME.prefs().edit().clear().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true).commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        ClipCommand.source = ClipCommand.Source { reads++; clips }
    }

    @After fun tearDown() {
        ClipCommand.source = ClipCommand.History
        KeyboardCommands.load(latinIME)
    }

    private fun call(before: String) = KeyboardCommands.find(before, Settings.getValues(), editor) as CommandCall
    private fun labels(chips: List<KeyboardCommands.Chip>) = chips.map { it.label.toString() }

    @Test fun isBuiltInAndProtectsItsArgument() {
        assertTrue(ClipCommand in KeyboardCommands.BUILT_IN)
        assertTrue(ClipCommand.protectsArgument)
        assertTrue(KeyboardCommands.guardsTextBefore("@clip Termn"))
    }

    @Test fun withoutWordTheNewestTextClipsAreShownWithoutSensitiveOnes() {
        val chips = KeyboardCommands.chipsFor(call("@clip"), host)
        assertEquals(listOf("Rechnung 2026-11", "Einkaufsliste: Brot Milch", "https://example.org/termin",
            "Termin am Montag um 9", "Alte Notiz"), labels(chips))
        assertEquals(ClipCommand.NEWEST, chips.size)
        assertTrue(replaced.isEmpty())
    }

    @Test fun aWordFiltersAndATapReplacesExactlyTheCommandByTheWholeClip() {
        val chips = KeyboardCommands.chipsFor(call("Siehe @clip TERMIN"), host)
        // the sensitive clip contains the word too, but is never offered
        assertEquals(listOf("https://example.org/termin", "Termin am Montag um 9"), labels(chips))
        assertTrue(replaced.isEmpty()) // nothing happens without a tap
        KeyboardCommands.chipsFor(call("@clip brot"), host).single().onTap()
        assertEquals(listOf("@clip brot" to "Einkaufsliste:\nBrot\nMilch"), replaced)
        assertTrue(KeyboardCommands.chipsFor(call("@clip nirgends"), host).isEmpty())
    }

    @Test fun longClipsAreShortenedInTheLabelOnly() {
        val long = "x".repeat(500)
        clips = listOf(ClipCommand.Clip(long, now))
        val chip = KeyboardCommands.chipsFor(call("@clip"), host).single()
        assertEquals("x".repeat(30) + "…", chip.label)
        chip.onTap()
        assertEquals(long, replaced.single().second)
    }

    @Test fun withTheHistoryOffNothingIsReadAndOneChipOpensTheSetting() {
        latinIME.prefs().edit().putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false).commit()
        for (before in listOf("@clip", "@clip Termin")) {
            val chips = KeyboardCommands.chipsFor(call(before), host)
            assertEquals(listOf(latinIME.getString(R.string.agent_command_clip_enable)), labels(chips))
        }
        assertEquals(0, reads)
        KeyboardCommands.chipsFor(call("@clip"), host).single().onTap()
        val intent = shadowOf(latinIME.application).nextStartedActivity
        assertEquals(SettingsDestination.Preferences, intent.getStringExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION))
        assertTrue(SettingsDestination.Preferences in AgentPreferences.SETTINGS_DESTINATIONS)
        assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TASK, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertTrue(replaced.isEmpty())
    }

    @Test fun blockedFieldsReadNothing() {
        val typed = call("@clip Termin")
        editor = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD }
        assertNull(KeyboardCommands.find("@clip Termin", Settings.getValues(), editor))
        assertTrue(ClipCommand.chips(host, typed).isEmpty())
        assertEquals(0, reads)
    }

    @Test fun theHistorySourceReadsOnlyTextClipsAndMarksTheSensitiveCurrentClip() {
        val dao = assertNotNull(ClipboardDao.getInstance(latinIME))
        dao.clear()
        try {
            // first, as the keyboard's own listener adds a new primary clip to the history with the test clock's time
            val manager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("", "Kennwort123").apply {
                description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            })
            dao.addClip(now - 3_000, false, "erster Text")
            dao.addClip(now - 2_000, false, "Kennwort123")
            dao.insertNewEntry(now - 1_000, false, "label", "image.png", listOf("image/png"), null)
            dao.addClip(now - 60L * 60 * 1000, false, "zu alt") // past the default retention of 10 minutes
            dao.addClip(now - 60L * 60 * 1000, true, "alt, aber angeheftet")
            val read = ClipCommand.History.clips(latinIME)
            assertEquals(setOf("erster Text", "Kennwort123", "alt, aber angeheftet"), read.map { it.text }.toSet())
            assertEquals(listOf("Kennwort123"), read.filter { it.sensitive }.map { it.text })
            assertEquals(listOf("erster Text", "alt, aber angeheftet"), ClipCommand.match("", read).map { it.text })
        } finally {
            dao.clear()
        }
    }
}
