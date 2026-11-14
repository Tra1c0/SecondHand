// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
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
        SettingsArea.LOOK -> listOf(
            AreaEntry(SettingsDestination.AgentDesign, R.string.agent_design_title, R.string.agent_area_row_design),
            AreaEntry(SettingsDestination.AgentRgb, R.string.agent_rgb_title, R.string.agent_area_row_rgb),
            AreaEntry(SettingsDestination.Appearance, R.string.settings_screen_appearance, R.string.agent_area_row_appearance),
        )
        SettingsArea.TYPING -> listOf(
            AreaEntry(SettingsDestination.TextCorrection, R.string.settings_screen_correction, R.string.agent_area_row_correction),
            AreaEntry(SettingsDestination.AgentLearning, R.string.agent_learning_title, R.string.agent_area_row_learning),
            AreaEntry(SettingsDestination.Dictionaries, R.string.dictionary_settings_category, R.string.agent_area_row_dictionaries),
        )
        SettingsArea.KEYS -> listOf(
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
        )
        SettingsArea.AI -> listOf(
            AreaEntry(SettingsDestination.AgentAi, R.string.agent_ai_title, R.string.agent_area_row_ai),
            AreaEntry(SettingsDestination.AgentSkills, R.string.agent_skills_title, R.string.agent_area_row_skills),
            AreaEntry(SettingsDestination.AgentWorkflows, R.string.agent_workflows_title, R.string.agent_area_row_workflows),
            AreaEntry(SettingsDestination.AgentDictation, R.string.agent_dictation_title, R.string.agent_area_row_dictation),
        )
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
 * Page of one area: back, the gradient hero and, until the area is built from the mockup, a card
 * with rows to the existing pages ([SettingsAreas]).
 */
@Composable
fun AgentAreaScreen(area: SettingsArea, onNavigate: (String) -> Unit, onClickBack: () -> Unit) {
    if (area == SettingsArea.TYPING) return AgentAreaTypingScreen(onNavigate, onClickBack) // stage 38c
    if (area == SettingsArea.GESTURES) return AgentAreaGesturesScreen(onNavigate, onClickBack) // 38e
    AgentSettingsTheme {
        Column(
            Modifier.fillMaxSize().background(AgentSettingsTokens.Background).windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(start = AgentSettingsTokens.PagePadding, end = AgentSettingsTokens.PagePadding, top = 16.dp, bottom = 32.dp)
                .testTag(AREA_TAG + area.route),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackHeader(onClickBack)
            AreaHero(area)
            SectionCard(stringResource(R.string.agent_area_pages)) {
                Text(stringResource(R.string.agent_area_pages_note), style = AgentSettingsType.Small, color = AgentSettingsTokens.Muted,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp))
                SettingsAreas.entries(area).filter { it.visible() }.forEach { entry ->
                    val summary = if (entry.summary != 0) stringResource(entry.summary)
                        else SubtypeSettings.getEnabledSubtypes(true).joinToString(", ") { it.displayName() }
                    SettingRow(stringResource(entry.title), summary, area.dot, { onNavigate(entry.route) },
                        Modifier.testTag(AREA_ROW_TAG + entry.route))
                }
            }
        }
    }
}

const val AREA_TAG = "agent_area_page_"
const val AREA_ROW_TAG = "agent_area_row_"
