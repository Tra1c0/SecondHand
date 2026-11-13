// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.CommandCall
import dev.agentkeyboard.core.CommandContext
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.agent.DateCommand
import helium314.keyboard.agent.KeyboardCommands
import helium314.keyboard.agent.SearchCommand
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

/** `@wecker` and `@timer`: chips, the clock app intents and when the command text goes. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class ClockCommandsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val text = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<Pair<String, String>>()
    private val launched = mutableListOf<Intent>()
    private var starts = true

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

    @Before fun setUp() {
        latinIME.prefs().edit().clear().commit()
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
        // Thursday, 12 November 2026, 18:45 in Berlin
        DateCommand.clock = { NOW }
        DateCommand.zone = { TimeZone.getTimeZone("Europe/Berlin") }
        val fake = SearchCommand.Launcher { _, intent -> launched += intent; starts }
        AlarmCommand.launcher = fake
        TimerCommand.launcher = fake
    }

    @After fun tearDown() {
        DateCommand.clock = { System.currentTimeMillis() }
        DateCommand.zone = { TimeZone.getDefault() }
        AlarmCommand.launcher = SearchCommand.SYSTEM_LAUNCHER
        TimerCommand.launcher = SearchCommand.SYSTEM_LAUNCHER
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    private fun call(before: String, editor: EditorInfo = text) = KeyboardCommands.find(before, Settings.getValues(), editor) as CommandCall
    private fun chips(before: String) = KeyboardCommands.chipsFor(call(before), host)
    private fun labels(before: String) = chips(before).map { it.label.toString() }

    @Test fun commandsAreBuiltInWithDescriptionsAndProtectTheirArgument() {
        assertTrue(AlarmCommand in KeyboardCommands.BUILT_IN)
        assertTrue(TimerCommand in KeyboardCommands.BUILT_IN)
        for (command in listOf(AlarmCommand, TimerCommand)) {
            assertTrue("@${command.name}" in command.description(latinIME), command.name)
            assertTrue(KeyboardCommands.guardsTextBefore("hi @${command.name} 10 min Nudeln"), command.name)
            assertNull(command.permission, command.name) // SET_ALARM is granted at install, no prompt
        }
        KeyboardCommands.setPrefix(latinIME, '#')
        assertTrue("#wecker" in AlarmCommand.description(latinIME))
        assertTrue("#timer" in TimerCommand.description(latinIME))
    }

    @Test fun alarmChipSetsTheAlarmOnlyOnTapAndThenRemovesTheCommand() {
        val chips = chips("Morgen @wecker 6:30 Training")
        val label = latinIME.getString(R.string.agent_command_alarm_chip, "06:30", "Training")
        assertEquals(listOf(label), chips.map { it.label.toString() })
        assertEquals(latinIME.getString(R.string.agent_command_alarm_open, label), chips.single().description)
        assertEquals(KeyboardCommands.Chip.Kind.ACTION, chips.single().kind)
        assertTrue(launched.isEmpty() && replaced.isEmpty()) // nothing happens before the tap
        chips.single().onTap()
        val intent = launched.single()
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(6, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertEquals("Training", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertTrue(intent.hasExtra(AlarmClock.EXTRA_SKIP_UI))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true)) // the clock app shows it for confirmation
        assertFalse(intent.hasExtra(AlarmClock.EXTRA_DAYS))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(listOf("@wecker 6:30 Training" to ""), replaced)
    }

    @Test fun alarmReadsColloquialTimesAsMorningAndLeavesOutAnEmptyLabel() {
        assertEquals(listOf(latinIME.getString(R.string.agent_command_alarm_chip_untitled, "07:30")), labels("@wecker halb acht"))
        assertEquals(listOf(latinIME.getString(R.string.agent_command_alarm_chip_untitled, "07:00")), labels("@wecker 7 Uhr"))
        assertEquals(listOf(latinIME.getString(R.string.agent_command_alarm_chip_untitled, "06:45")), labels("@wecker 6:45 morgen"))
        host.language = "en"
        assertEquals(listOf(latinIME.getString(R.string.agent_command_alarm_chip, "6:00 PM", "gym")), labels("@wecker 6pm gym"))
        host.language = "de"
        chips("@wecker 7:15").single().onTap()
        assertFalse(launched.single().hasExtra(AlarmClock.EXTRA_MESSAGE))
        assertEquals(7, launched.single().getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(15, launched.single().getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
    }

    @Test fun aDayThatCannotBeSetIsShownAsIgnored() {
        val plain = latinIME.getString(R.string.agent_command_alarm_chip, "07:00", "Arzt")
        val label = latinIME.getString(R.string.agent_command_alarm_chip_day_ignored, plain)
        val chips = chips("@wecker Montag 7:00 Arzt")
        assertEquals(listOf(label), chips.map { it.label.toString() })
        chips.single().onTap()
        assertFalse(launched.single().hasExtra(AlarmClock.EXTRA_DAYS))
        assertEquals(latinIME.getString(R.string.agent_command_alarm_day_note, "07:00"), ShadowToast.getTextOfLatestToast())
        assertEquals(listOf("@wecker Montag 7:00 Arzt" to ""), replaced)
    }

    @Test fun timerChipSetsTheTimerOnlyOnTapAndThenRemovesTheCommand() {
        val chips = chips("Essen: @timer 10 min Nudeln")
        val label = latinIME.getString(R.string.agent_command_timer_chip, "10:00", "Nudeln")
        assertEquals(listOf(label), chips.map { it.label.toString() })
        assertEquals(latinIME.getString(R.string.agent_command_timer_open, label), chips.single().description)
        assertTrue(launched.isEmpty() && replaced.isEmpty())
        chips.single().onTap()
        val intent = launched.single()
        assertEquals(AlarmClock.ACTION_SET_TIMER, intent.action)
        assertEquals(600, intent.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertEquals("Nudeln", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertTrue(intent.hasExtra(AlarmClock.EXTRA_SKIP_UI))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(listOf("@timer 10 min Nudeln" to ""), replaced)
    }

    @Test fun timerFormatsTheLengthAndLeavesOutAnEmptyLabel() {
        assertEquals(listOf(latinIME.getString(R.string.agent_command_timer_chip_untitled, "1:30")), labels("@timer 90 s"))
        assertEquals(listOf(latinIME.getString(R.string.agent_command_timer_chip_untitled, "1:20:00")), labels("@timer 1 h 20"))
        assertEquals(listOf(latinIME.getString(R.string.agent_command_timer_chip_untitled, "30:00")), labels("@timer halbe Stunde"))
        chips("@timer 24 h").single().onTap()
        assertEquals(24 * 3600, launched.single().getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertFalse(launched.single().hasExtra(AlarmClock.EXTRA_MESSAGE))
    }

    @Test fun aFailedStartKeepsTheCommandAndSaysSo() {
        starts = false
        chips("@wecker 6:30").single().onTap()
        assertEquals(latinIME.getString(R.string.agent_command_clock_none), ShadowToast.getTextOfLatestToast())
        chips("@timer 5 min").single().onTap()
        assertEquals(latinIME.getString(R.string.agent_command_clock_none), ShadowToast.getTextOfLatestToast())
        assertEquals(2, launched.size)
        assertTrue(replaced.isEmpty())
    }

    @Test fun withoutATimeOrDurationOnlyAMutedHintThatChangesNothing() {
        // an empty argument offers only templates, nothing that starts the clock app
        assertTrue(chips("@wecker ").all { it.kind == KeyboardCommands.Chip.Kind.TEMPLATE } && chips("@wecker ").isNotEmpty())
        assertTrue(chips("@timer ").all { it.kind == KeyboardCommands.Chip.Kind.TEMPLATE } && chips("@timer ").isNotEmpty())
        for ((typed, hint, usage) in listOf(
            Triple("@wecker Training", R.string.agent_command_alarm_hint, R.string.agent_command_alarm_usage),
            Triple("@wecker 25 Uhr", R.string.agent_command_alarm_hint, R.string.agent_command_alarm_usage),
            Triple("@timer Nudeln", R.string.agent_command_timer_hint, R.string.agent_command_timer_usage),
            Triple("@timer 25 h", R.string.agent_command_timer_hint, R.string.agent_command_timer_usage),
        )) {
            val chip = chips(typed).single()
            assertEquals(latinIME.getString(hint), chip.label.toString(), typed)
            assertEquals(KeyboardCommands.Chip.Kind.MUTED, chip.kind, typed)
            chip.onTap()
            assertEquals(latinIME.getString(usage, "@"), ShadowToast.getTextOfLatestToast(), typed)
        }
        assertTrue(launched.isEmpty() && replaced.isEmpty())
    }

    @Test fun blockedFieldsShowNoCommand() {
        val password = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        for (typed in listOf("@wecker 6:30", "@timer 10 min")) {
            assertNull(KeyboardCommands.find(typed, Settings.getValues(), password), typed)
        }
    }

    companion object {
        const val NOW = 1_794_505_500_000L // 2026-11-12T17:45Z, 18:45 in Berlin
    }

    @Test fun templatesLeadToRealChips() {
        for (template in AlarmCommand.templates(host)) {
            val call = dev.agentkeyboard.core.CommandCall(0, "@wecker " + template.text, '@', "wecker", template.text)
            assertTrue(AlarmCommand.chips(host, call).first().label.toString().startsWith("⏰"), template.text)
        }
        for (template in TimerCommand.templates(host)) {
            val call = dev.agentkeyboard.core.CommandCall(0, "@timer " + template.text, '@', "timer", template.text)
            assertTrue(TimerCommand.chips(host, call).first().label.toString().startsWith("⏱"), template.text)
        }
    }
}
