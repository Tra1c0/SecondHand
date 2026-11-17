// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.settings.SettingsContainer
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.screens.AGENT_PAGE_ENTRY
import helium314.keyboard.settings.screens.KEYWORDS
import helium314.keyboard.settings.screens.SettingsArea
import helium314.keyboard.settings.screens.SettingsAreas
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Stage 69 (E2): the settings search finds the pages of the fork by title and by German and English words. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "de-w411dp-h1000dp")
class SettingsSearchTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val container by lazy { SettingsContainer(app) }

    private fun pageRoutes(): List<String> =
        (SettingsArea.entries.map { it.route } + SettingsArea.entries.flatMap { SettingsAreas.entries(it) }.filter { it.visible() }.map { it.route })
            .filter { it != SettingsDestination.AgentSymbols } // has its own entry (AGENT_SYMBOLS_ENTRY)
            .distinct()

    @Test fun everyPageHasASearchEntryFoundByItsTitle() {
        pageRoutes().forEach { route ->
            val entry = assertNotNull(container[AGENT_PAGE_ENTRY + route], "no search entry for $route")
            val found = container.filter(entry.title)
            assertTrue(found.any { it.key == entry.key }, "searching \"${entry.title}\" does not find $route")
        }
    }

    @Test fun theNewPagesAreFoundByTheirGermanTitles() {
        mapOf(
            "KI-Anbieter" to SettingsDestination.AgentAi,
            "RGB-Beleuchtung" to SettingsDestination.AgentRgb,
            "Updates" to SettingsDestination.AgentUpdates,
            "Diagnose" to SettingsDestination.AgentDiagnose,
            "Tippen" to SettingsDestination.AreaTyping,
            "Textkorrektur" to SettingsDestination.TextCorrection,
            "Gelernte" to SettingsDestination.AgentLearning,
            "Tastaturprofile" to SettingsDestination.AgentProfiles,
            "Befehle" to SettingsDestination.AgentCommands,
        ).forEach { (term, route) ->
            assertTrue(container.filter(term).any { it.key == AGENT_PAGE_ENTRY + route }, "\"$term\" does not find $route")
        }
    }

    @Test fun englishAndGermanWordsFindThePagesToo() {
        mapOf(
            "openai" to SettingsDestination.AgentAi,
            "beleuchtung" to SettingsDestination.AgentRgb,
            "lighting" to SettingsDestination.AgentRgb,
            "aktualisieren" to SettingsDestination.AgentUpdates,
            "diagnostics" to SettingsDestination.AgentDiagnose,
            "autocorrect" to SettingsDestination.TextCorrection,
            "backup" to SettingsDestination.AreaSystem,
        ).forEach { (term, route) ->
            assertTrue(container.filter(term).any { it.key == AGENT_PAGE_ENTRY + route }, "\"$term\" does not find $route")
        }
    }

    @Test fun everyRouteHasSearchWordsAndTheyAreNotEmpty() {
        pageRoutes().forEach { route ->
            val id = assertNotNull(KEYWORDS[route], "no search words for $route")
            assertTrue(app.getString(id).isNotBlank())
        }
    }

    @Test fun aboutLinksToTheForkAndKeepsTheCreditToHeliBoard() {
        assertNotNull(container[SettingsWithoutKey.FORK_REPO])
        assertEquals("https://github.com/Tra1c0/SecondHandAgenticKeyboard", Links.FORK_REPO)
        assertEquals("Basiert auf HeliBoard", assertNotNull(container[SettingsWithoutKey.GITHUB]).title)
        assertNotNull(container[SettingsWithoutKey.LICENSE])
        // the wiki and community pages of HeliBoard are not offered any more
        assertTrue(container.filter("Community").none { it.key.startsWith("about") || it.key == "community_links" })
        assertEquals(null, container["community_links"])
        assertEquals(null, container["github_wiki"])
    }

    @Test fun theSloganIsGerman() {
        assertEquals("Zu 100 % freie Tastatur, basiert auf AOSP.", app.getString(R.string.app_slogan))
    }
}
