// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.preferences.Preference

/**
 * Stage 69 (E2): the settings search lists the pages of the fork, which have no setting of their
 * own: the seven areas and every page of [SettingsAreas] (AI, RGB, updates, diagnostics, typing,
 * correction and the others), each with its title, its summary and search words in German and
 * English ([KEYWORDS]). A tap opens the page. The space bar and symbol pages have their own entries
 * ([createAgentSpaceSettings], [createAgentSymbolSettings]).
 */
fun createAgentPageSettings(context: Context): List<Setting> {
    val own = setOf(SettingsDestination.AgentSymbols, SettingsDestination.AgentSpace)
    val areas = SettingsArea.entries.map { Triple(it.route, it.title, it.subtitle) }
    val pages = SettingsArea.entries.flatMap { SettingsAreas.entries(it) }
        .filter { it.visible() && it.route !in own }
        .map { Triple(it.route, it.title, it.summary) }
    return (areas + pages).distinctBy { it.first }.map { (route, title, summary) ->
        Setting(context, AGENT_PAGE_ENTRY + route, title, summary.takeIf { it != 0 }, KEYWORDS[route]) {
            Preference(
                name = it.title,
                description = it.description,
                onClick = { SettingsDestination.navigateTo(route) },
            ) { NextScreenIcon() }
        }
    }
}

/** Key prefix of the search entries of the pages (followed by the route). */
const val AGENT_PAGE_ENTRY = "agent_page_"

/** Extra search words per route (German and English), see agent_search_strings.xml. */
internal val KEYWORDS = mapOf(
    SettingsDestination.AreaLook to R.string.agent_search_kw_area_look,
    SettingsDestination.AreaTyping to R.string.agent_search_kw_area_typing,
    SettingsDestination.AreaKeys to R.string.agent_search_kw_area_keys,
    SettingsDestination.AreaGestures to R.string.agent_search_kw_area_gestures,
    SettingsDestination.AreaBar to R.string.agent_search_kw_area_bar,
    SettingsDestination.AreaAi to R.string.agent_search_kw_area_ai,
    SettingsDestination.AreaSystem to R.string.agent_search_kw_area_system,
    SettingsDestination.AgentDesign to R.string.agent_search_kw_agent_design,
    SettingsDestination.AgentRgb to R.string.agent_search_kw_agent_rgb,
    SettingsDestination.Appearance to R.string.agent_search_kw_appearance,
    SettingsDestination.TextCorrection to R.string.agent_search_kw_text_correction,
    SettingsDestination.AgentLearning to R.string.agent_search_kw_agent_learning,
    SettingsDestination.AgentMemory to R.string.agent_search_kw_agent_memory,
    SettingsDestination.Dictionaries to R.string.agent_search_kw_dictionaries,
    SettingsDestination.Languages to R.string.agent_search_kw_languages,
    SettingsDestination.Preferences to R.string.agent_search_kw_preferences,
    SettingsDestination.Layouts to R.string.agent_search_kw_layouts,
    SettingsDestination.GestureTyping to R.string.agent_search_kw_gesture_typing,
    SettingsDestination.Toolbar to R.string.agent_search_kw_toolbar,
    SettingsDestination.AgentCommands to R.string.agent_search_kw_agent_commands,
    SettingsDestination.AgentScreenshots to R.string.agent_search_kw_agent_screenshots,
    SettingsDestination.AgentMyData to R.string.agent_search_kw_agent_my_data,
    SettingsDestination.AgentPrompts to R.string.agent_search_kw_agent_prompts,
    SettingsDestination.AgentAi to R.string.agent_search_kw_agent_ai,
    SettingsDestination.AgentSkills to R.string.agent_search_kw_agent_skills,
    SettingsDestination.AgentWorkflows to R.string.agent_search_kw_agent_workflows,
    SettingsDestination.AgentDictation to R.string.agent_search_kw_agent_dictation,
    SettingsDestination.AgentProfiles to R.string.agent_search_kw_agent_profiles,
    SettingsDestination.AgentUpdates to R.string.agent_search_kw_agent_updates,
    SettingsDestination.AgentDiagnose to R.string.agent_search_kw_agent_diagnose,
    SettingsDestination.Advanced to R.string.agent_search_kw_advanced,
    SettingsDestination.DataGathering to R.string.agent_search_kw_data_gathering,
    SettingsDestination.About to R.string.agent_search_kw_about,
)
