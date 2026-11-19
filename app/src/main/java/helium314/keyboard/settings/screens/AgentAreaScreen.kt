// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.screens.gesturedata.END_DATE_EPOCH_MILLIS
import helium314.keyboard.settings.screens.gesturedata.TWO_WEEKS_IN_MILLIS

/** One row of an area page that opens an existing page ([route]). */
class AreaEntry(val route: String, @StringRes val title: Int, @StringRes val summary: Int, val visible: () -> Boolean = { true })

/**
 * Stage 38a: which existing pages each area opens until its page is built 1:1 from the mockup
 * (38b–38h). Follows the mapping of Struktur.dc.html; every entry of the old start page is here.
 */
object SettingsAreas {
    private val hasGestureLib = { JniUtils.sHaveGestureLib }
    // the data gathering page disappears two weeks after the phase, as on the old start page
    private val gatheringRuns = { JniUtils.sHaveGestureLib && System.currentTimeMillis() < END_DATE_EPOCH_MILLIS + TWO_WEEKS_IN_MILLIS }

    fun entries(area: SettingsArea): List<AreaEntry> = when (area) {
        // 38b: the pages AgentAreaLookScreen opens, in page order ("Alle Designs", RGB card, last row)
        SettingsArea.LOOK -> listOf(
            AreaEntry(SettingsDestination.AgentDesign, R.string.agent_design_title, R.string.agent_area_row_design),
            AreaEntry(SettingsDestination.AgentRgb, R.string.agent_rgb_title, R.string.agent_area_row_rgb),
            AreaEntry(SettingsDestination.Appearance, R.string.settings_screen_appearance, R.string.agent_area_row_appearance),
        )
        SettingsArea.TYPING -> listOf(
            AreaEntry(SettingsDestination.TextCorrection, R.string.settings_screen_correction, R.string.agent_area_row_correction),
            AreaEntry(SettingsDestination.AgentLearning, R.string.agent_learning_title, R.string.agent_area_row_learning),
            AreaEntry(SettingsDestination.AgentMemory, R.string.agent_memory_title, R.string.agent_memory_sub), // stage 49
            AreaEntry(SettingsDestination.Dictionaries, R.string.dictionary_settings_category, R.string.agent_area_row_dictionaries),
        )
        SettingsArea.KEYS -> listOf(
            // 38d: AgentAreaKeysScreen opens these pages from its own rows (tags AREA_ROW_TAG + route)
            // summary: the enabled languages, as on the old start page
            AreaEntry(SettingsDestination.Languages, R.string.language_and_layouts_title, 0),
            AreaEntry(SettingsDestination.Preferences, R.string.settings_screen_preferences, R.string.agent_area_row_options),
            AreaEntry(SettingsDestination.Layouts, R.string.settings_screen_secondary_layouts, R.string.agent_area_row_layouts),
        )
        // 38e: the pages the gesture page opens (AgentAreaGesturesScreen); space bar, vibration and sound are on it
        SettingsArea.GESTURES -> listOf(
            AreaEntry(SettingsDestination.AgentSymbols, R.string.agent_gestures_own, R.string.agent_gestures_own_summary),
            AreaEntry(SettingsDestination.GestureTyping, R.string.agent_gestures_gesture_options, R.string.agent_gestures_gesture_options_summary, hasGestureLib),
        )
        SettingsArea.BAR -> listOf(
            AreaEntry(SettingsDestination.Toolbar, R.string.agent_area_row_bar, R.string.agent_area_row_bar_summary),
            AreaEntry(SettingsDestination.AgentCommands, R.string.agent_commands_title, R.string.agent_area_row_commands),
            AreaEntry(SettingsDestination.AgentScreenshots, R.string.agent_screenshot_title, R.string.agent_area_row_screenshots),
            // stage 45: field and context suggestions with "Meine Daten"
            AreaEntry(SettingsDestination.AgentMyData, R.string.agent_my_data_title, R.string.agent_my_data_row_sub),
            // stage 74a: the prompt store
            AreaEntry(SettingsDestination.AgentPrompts, R.string.agent_prompts_title, R.string.agent_prompts_row_sub),
        )
        // 38g: the pages the AI page opens (AgentAreaAiScreen; also its rows when the device is locked)
        SettingsArea.AI -> listOf(
            AreaEntry(SettingsDestination.AgentAi, R.string.agent_ai_title, R.string.agent_area_row_ai),
            AreaEntry(SettingsDestination.AgentSkills, R.string.agent_skills_title, R.string.agent_area_row_skills),
            AreaEntry(SettingsDestination.AgentWorkflows, R.string.agent_workflows_title, R.string.agent_area_row_workflows),
            AreaEntry(SettingsDestination.AgentDictation, R.string.agent_dictation_title, R.string.agent_area_row_dictation),
        )
        // 38h: the pages AgentAreaSystemScreen opens (rows, card and buttons tagged AREA_ROW_TAG + route)
        SettingsArea.SYSTEM -> listOf(
            AreaEntry(SettingsDestination.AgentProfiles, R.string.agent_profiles_title, R.string.agent_area_row_profiles),
            AreaEntry(SettingsDestination.AgentUpdates, R.string.agent_updates_title, R.string.agent_area_row_updates),
            AreaEntry(SettingsDestination.AgentDiagnose, R.string.agent_diagnose_title, R.string.agent_area_row_diagnose),
            AreaEntry(SettingsDestination.Advanced, R.string.settings_screen_advanced, R.string.agent_area_row_advanced),
            AreaEntry(SettingsDestination.DataGathering, R.string.gesture_data_screen, R.string.agent_area_row_gesture_data, gatheringRuns),
            AreaEntry(SettingsDestination.About, R.string.settings_screen_about, R.string.agent_area_row_about),
        )
    }
}

/**
 * Page of one area. Each of the seven areas has its own screen (stages 38b to 38h); the old
 * transition page with a card of rows for the existing pages is gone (stage 69, E10). The rows of
 * [SettingsAreas] remain the map of which page belongs to which area (tests, settings search).
 */
@Composable
fun AgentAreaScreen(area: SettingsArea, onNavigate: (String) -> Unit, onClickBack: () -> Unit) {
    when (area) {
        SettingsArea.LOOK -> AgentAreaLookScreen(onNavigate, onClickBack) // 38b
        SettingsArea.TYPING -> AgentAreaTypingScreen(onNavigate, onClickBack) // 38c
        SettingsArea.KEYS -> AgentAreaKeysScreen(onNavigate, onClickBack) // 38d, built from Tasten.dc.html
        SettingsArea.GESTURES -> AgentAreaGesturesScreen(onNavigate, onClickBack) // 38e
        SettingsArea.BAR -> AgentAreaBarScreen(onNavigate, onClickBack) // 38f
        SettingsArea.AI -> AgentAreaAiScreen(onNavigate, onClickBack) // 38g
        SettingsArea.SYSTEM -> AgentAreaSystemScreen(onNavigate, onClickBack) // 38h, built from System.dc.html
    }
}

const val AREA_TAG = "agent_area_page_"
const val AREA_ROW_TAG = "agent_area_row_"
