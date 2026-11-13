// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.SearchManager
import android.content.ActivityNotFoundException
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import kotlin.test.*

/** The stage 24f commands `@such`, `@zeichen`, `@emoji` and `@conv`. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class MoreCommandsTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val text = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
    private val replaced = mutableListOf<Pair<String, String>>()
    private val launched = mutableListOf<Intent>()
    /** Actions the fake launcher accepts; anything else counts as "no app". */
    private var handled = setOf(Intent.ACTION_WEB_SEARCH, Intent.ACTION_VIEW)

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
        SearchCommand.launcher = SearchCommand.Launcher { _, intent ->
            launched += intent
            intent.action in handled
        }
    }

    @After fun tearDown() {
        SearchCommand.launcher = SearchCommand.SYSTEM_LAUNCHER
        KeyboardCommands.registry = KeyboardCommands.BUILT_IN
        KeyboardCommands.load(latinIME)
    }

    private fun call(before: String) = KeyboardCommands.find(before, Settings.getValues(), text) as CommandCall
    private fun chips(before: String) = KeyboardCommands.chipsFor(call(before), host)
    private fun labels(before: String) = chips(before).map { it.label.toString() }
    /** The command's own chips, without the templates shown after them while the argument is empty. */
    private fun own(before: String) = chips(before).filter { it.kind != KeyboardCommands.Chip.Kind.TEMPLATE }

    @Test fun commandsAreBuiltInWithDescriptionsAndProtectTheirArgument() {
        val names = KeyboardCommands.BUILT_IN.map { it.name }
        assertTrue(names.containsAll(listOf("math", "such", "zeichen", "emoji", "conv")))
        for (name in listOf("such", "zeichen", "emoji", "conv")) {
            val command = KeyboardCommands.BUILT_IN.single { it.name == name }
            assertTrue("@$name" in command.description(latinIME), name)
            assertTrue(KeyboardCommands.guardsTextBefore("hi @$name Wort"), name)
        }
        KeyboardCommands.setPrefix(latinIME, '#')
        assertTrue("#conv 5 oz" in ConvertCommand.description(latinIME))
        assertTrue("#such" in SearchCommand.description(latinIME))
    }

    @Test fun searchStartsTheWebSearchOnlyOnTapAndThenRemovesTheCommand() {
        val chips = chips("Hallo @such Wetter Berlin ")
        assertEquals(listOf(latinIME.getString(R.string.agent_command_search_chip, "Wetter Berlin")), chips.map { it.label.toString() })
        assertTrue(launched.isEmpty() && replaced.isEmpty()) // nothing happens before the tap
        chips.single().onTap()
        val intent = launched.single()
        assertEquals(Intent.ACTION_WEB_SEARCH, intent.action)
        assertEquals("Wetter Berlin", intent.getStringExtra(SearchManager.QUERY))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(listOf("@such Wetter Berlin " to ""), replaced)
    }

    @Test fun searchFallsBackToABrowserAndKeepsTheCommandWithoutAnyApp() {
        handled = setOf(Intent.ACTION_VIEW)
        chips("@such a&b über").single().onTap()
        assertEquals(listOf(Intent.ACTION_WEB_SEARCH, Intent.ACTION_VIEW), launched.map { it.action })
        val view = launched[1]
        assertEquals("https://duckduckgo.com/?q=a%26b+%C3%BCber", view.dataString)
        assertTrue(view.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(view.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(listOf("@such a&b über" to ""), replaced)

        launched.clear(); replaced.clear()
        handled = emptySet()
        chips("@such Wetter").single().onTap()
        assertEquals(2, launched.size)
        assertTrue(replaced.isEmpty()) // the command stays when nothing could be started
        assertEquals(latinIME.getString(R.string.agent_command_search_none), ShadowToast.getTextOfLatestToast())
    }

    @Test fun searchWithoutATermShowsNoChip() {
        assertTrue(chips("@such").isEmpty())
        assertTrue(chips("@such    ").isEmpty())
    }

    @Test fun theSystemLauncherReportsWhetherAnAppTookTheIntent() {
        val intent = SearchCommand.intents("Wetter").first()
        assertTrue(SearchCommand.SYSTEM_LAUNCHER.start(latinIME, intent))
        assertEquals(Intent.ACTION_WEB_SEARCH, shadowOf(latinIME.application).nextStartedActivity.action)
        val failing = object : android.content.ContextWrapper(latinIME) {
            override fun startActivity(intent: Intent?) { throw ActivityNotFoundException() }
        }
        assertFalse(SearchCommand.SYSTEM_LAUNCHER.start(failing, intent))
    }

    @Test fun charactersAreOfferedByNameAndReplaceTheCommandOnTap() {
        val chips = chips("Weg @zeichen pfeil")
        assertEquals(listOf("→", "←", "↑", "↓", "↔", "⇒"), chips.map { it.label.toString() })
        assertEquals(latinIME.getString(R.string.agent_command_symbol_insert, "→", "Pfeil rechts"), chips[0].description)
        assertTrue(replaced.isEmpty())
        chips[0].onTap()
        assertEquals(listOf("@zeichen pfeil" to "→"), replaced)
        assertEquals("°", labels("@zeichen Grad").first())
        assertEquals(listOf("≠"), labels("@zeichen ungleich"))
        assertTrue(chips("@zeichen gibtsnicht").isEmpty())
        assertTrue(own("@zeichen").isEmpty()) // only templates (CommandTemplatesTest)
    }

    @Test fun emojisAreOfferedByKeywordAndReplaceTheCommandOnTap() {
        val chips = chips("Super @emoji daumen")
        assertEquals(listOf("👍", "👎", "🤞"), chips.map { it.label.toString() })
        assertTrue(replaced.isEmpty())
        chips[0].onTap()
        assertEquals(listOf("@emoji daumen" to "👍"), replaced)
        assertEquals("❤️", labels("@emoji heart").first())
        assertTrue(chips("@emoji qqq").isEmpty())
    }

    @Test fun conversionsUseTheDecimalMarkOfTheLanguageAndReplaceTheCommandOnTap() {
        val chips = chips("Das sind @conv 5 oz")
        assertEquals(listOf("5 oz = 141,7 g", "5 oz = 0,3125 lb"), chips.map { it.label.toString() })
        assertEquals(latinIME.getString(R.string.agent_command_insert_text, "5 oz = 141,7 g"), chips[0].description)
        assertTrue(replaced.isEmpty())
        chips[0].onTap()
        assertEquals(listOf("@conv 5 oz" to "5 oz = 141,7 g"), replaced)
        assertEquals(listOf("100 °F = 37,78 °C"), labels("@conv 100 f"))
        host.language = "en"
        assertEquals(listOf("100 °F = 37.78 °C"), labels("@conv 100 f"))
        assertTrue(chips("@conv 5 €").isEmpty()) // no currencies
        assertTrue(own("@conv").isEmpty()) // only templates (CommandTemplatesTest)
    }
}
