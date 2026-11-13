// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.screens.gesturedata.END_DATE_EPOCH_MILLIS
import helium314.keyboard.settings.screens.gesturedata.TWO_WEEKS_IN_MILLIS

@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickDataGathering: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickBack: () -> Unit,
    onClickAgentProfiles: () -> Unit = {},
    onClickAgentAi: () -> Unit = {},
    onClickAgentSkills: () -> Unit = {},
    onClickAgentWorkflows: () -> Unit = {},
    onClickAgentLearning: () -> Unit = {},
    onClickAgentDictation: () -> Unit = {},
    onClickAgentScreenshots: () -> Unit = {},
    onClickAgentCommands: () -> Unit = {},
    onClickAgentUpdates: () -> Unit = {},
    onClickAgentSymbols: () -> Unit = {},
) {
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
    // fork: entries as a list, rendered in the upstream or the modern look (AgentMainSettings)
    val entries = listOfNotNull(
        MainSettingsEntry(stringResource(R.string.agent_ai_title), R.drawable.ic_agent_actions, onClickAgentAi,
            modernIcon = R.drawable.ic_agent_settings_ai),
        MainSettingsEntry(stringResource(R.string.agent_skills_title), R.drawable.ic_skill_1, onClickAgentSkills,
            modernIcon = R.drawable.ic_agent_settings_skills),
        MainSettingsEntry(stringResource(R.string.agent_workflows_title), R.drawable.ic_agent_actions, onClickAgentWorkflows,
            modernIcon = R.drawable.ic_agent_settings_workflows),
        MainSettingsEntry(stringResource(R.string.agent_dictation_title), R.drawable.ic_agent_dictate, onClickAgentDictation),
        MainSettingsEntry(stringResource(R.string.agent_screenshot_title), R.drawable.sym_keyboard_paste, onClickAgentScreenshots,
            modernIcon = R.drawable.ic_agent_settings_screenshot),
        MainSettingsEntry(stringResource(R.string.agent_commands_title), R.drawable.ic_agent_actions, onClickAgentCommands,
            modernIcon = R.drawable.ic_agent_settings_commands),
        MainSettingsEntry(stringResource(R.string.agent_learning_title), R.drawable.ic_settings_correction, onClickAgentLearning,
            modernIcon = R.drawable.ic_agent_settings_learning),
        MainSettingsEntry(stringResource(R.string.agent_profiles_title), R.drawable.ic_settings_appearance, onClickAgentProfiles,
            modernIcon = R.drawable.ic_agent_settings_profiles),
        MainSettingsEntry(stringResource(R.string.agent_updates_title), R.drawable.ic_agent_settings_updates, onClickAgentUpdates),
        MainSettingsEntry(
            stringResource(R.string.language_and_layouts_title), R.drawable.ic_settings_languages, onClickLanguage,
            description = enabledSubtypes.joinToString(", ") { it.displayName() },
        ),
        MainSettingsEntry(stringResource(R.string.settings_screen_preferences), R.drawable.ic_settings_preferences, onClickPreferences),
        MainSettingsEntry(stringResource(R.string.settings_screen_appearance), R.drawable.ic_settings_appearance, onClickAppearance),
        MainSettingsEntry(stringResource(R.string.settings_screen_toolbar), R.drawable.ic_settings_toolbar, onClickToolbar,
            modernIcon = R.drawable.ic_agent_settings_toolbar),
        if (JniUtils.sHaveGestureLib)
            MainSettingsEntry(stringResource(R.string.settings_screen_gesture), R.drawable.ic_settings_gesture, onClickGestureTyping)
        else null,
        // stage 27: also without the gesture library, which HeliBoard does not ship
        MainSettingsEntry(stringResource(R.string.agent_symbols_title), R.drawable.ic_settings_gesture, onClickAgentSymbols),
        // we don't even show the menu if data gathering phase ended more than 2 weeks ago
        if (JniUtils.sHaveGestureLib && System.currentTimeMillis() < END_DATE_EPOCH_MILLIS + TWO_WEEKS_IN_MILLIS)
            MainSettingsEntry(stringResource(R.string.gesture_data_screen), R.drawable.ic_settings_gesture, onClickDataGathering)
        else null,
        MainSettingsEntry(stringResource(R.string.settings_screen_correction), R.drawable.ic_settings_correction, onClickTextCorrection),
        MainSettingsEntry(stringResource(R.string.settings_screen_secondary_layouts), R.drawable.ic_settings_layout, onClickLayouts,
            modernIcon = R.drawable.ic_agent_settings_layouts),
        MainSettingsEntry(stringResource(R.string.dictionary_settings_category), R.drawable.ic_dictionary, onClickDictionaries),
        MainSettingsEntry(stringResource(R.string.settings_screen_advanced), R.drawable.ic_settings_advanced, onClickAdvanced),
        MainSettingsEntry(stringResource(R.string.settings_screen_about), R.drawable.ic_settings_about, onClickAbout),
    )
    if (agentMainSettingsModern()) {
        AgentMainSettingsTheme {
            // same search as SearchSettingsScreen, with the large collapsing title
            SearchScreen(
                onClickBack = onClickBack,
                title = { Text(stringResource(R.string.ime_settings)) },
                filteredItems = { SettingsActivity.settingsContainer.filter(it) },
                itemContent = { it.Preference() },
                content = { AgentMainSettingsList(entries) },
                largeTitle = true,
            )
        }
    } else SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ime_settings),
        settings = emptyList(),
    ) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).then(Modifier.padding(innerPadding))
            ) {
                UpstreamMainSettingsList(entries)
            }
        }
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
