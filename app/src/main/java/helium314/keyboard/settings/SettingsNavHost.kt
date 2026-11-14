// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.settings.getTransitionAnimationScale
import helium314.keyboard.settings.screens.AboutScreen
import helium314.keyboard.settings.screens.AgentDictationScreen
import helium314.keyboard.settings.screens.AgentScreenshotScreen
import helium314.keyboard.settings.screens.AgentCommandsScreen
import helium314.keyboard.settings.screens.AgentDesignScreen
import helium314.keyboard.settings.screens.AgentLearningScreen
import helium314.keyboard.settings.screens.AgentProfilesScreen
import helium314.keyboard.settings.screens.AgentSpaceScreen
import helium314.keyboard.settings.screens.AgentAiScreen
import helium314.keyboard.settings.screens.AgentSkillsScreen
import helium314.keyboard.settings.screens.AgentUpdatesScreen
import helium314.keyboard.settings.screens.AgentWorkflowsScreen
import helium314.keyboard.settings.screens.AdvancedSettingsScreen
import helium314.keyboard.settings.screens.AppearanceScreen
import helium314.keyboard.settings.screens.ColorsScreen
import helium314.keyboard.settings.screens.DebugScreen
import helium314.keyboard.settings.screens.DictionaryScreen
import helium314.keyboard.settings.screens.GestureTypingScreen
import helium314.keyboard.settings.screens.LanguageScreen
import helium314.keyboard.settings.screens.MainSettingsScreen
import helium314.keyboard.settings.screens.PersonalDictionariesScreen
import helium314.keyboard.settings.screens.PersonalDictionaryScreen
import helium314.keyboard.settings.screens.PreferencesScreen
import helium314.keyboard.settings.screens.SecondaryLayoutScreen
import helium314.keyboard.settings.screens.SubtypeScreen
import helium314.keyboard.settings.screens.TextCorrectionScreen
import helium314.keyboard.settings.screens.ToolbarScreen
import helium314.keyboard.settings.screens.gesturedata.GestureDataScreen
import helium314.keyboard.settings.screens.gesturedata.ReviewScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun SettingsNavHost(
    onClickBack: () -> Unit,
    startDestination: String? = null,
) {
    val navController = rememberNavController()
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val target = SettingsDestination.navTarget.collectAsState()

    // duration does not change when system setting changes, but that's rare enough to not care
    val duration = (250 * getTransitionAnimationScale(LocalContext.current)).toInt()
    val animation = tween<IntOffset>(durationMillis = duration)

    fun goBack() {
        if (!navController.popBackStack()) onClickBack()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination ?: SettingsDestination.Settings,
        enterTransition = { slideInHorizontally(initialOffsetX = { +it * dir }, animationSpec = animation) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it * dir }, animationSpec = animation) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it * dir }, animationSpec = animation) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { +it * dir }, animationSpec = animation) }
    ) {
        // fork (stage 38a): new start page with seven areas; the old entries are on the area pages
        composable(SettingsDestination.Settings) {
            MainSettingsScreen(onNavigate = { navController.navigate(it) }, onClickBack = ::goBack)
        }
        helium314.keyboard.settings.screens.SettingsArea.entries.forEach { area ->
            composable(area.route) {
                helium314.keyboard.settings.screens.AgentAreaScreen(area, onNavigate = { navController.navigate(it) }, onClickBack = ::goBack)
            }
        }
        composable(SettingsDestination.About) {
            AboutScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.AgentProfiles) {
            AgentProfilesScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.AgentAi) { AgentAiScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentSkills) { AgentSkillsScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentWorkflows) { AgentWorkflowsScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentLearning) { AgentLearningScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentDictation) { AgentDictationScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentScreenshots) { AgentScreenshotScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentCommands) { AgentCommandsScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentDesign) { AgentDesignScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentUpdates) { AgentUpdatesScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentSpace) { AgentSpaceScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentSymbols) { helium314.keyboard.settings.screens.AgentSymbolsScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentRgb) { helium314.keyboard.settings.screens.AgentRgbScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentDiagnose) { helium314.keyboard.settings.screens.AgentDiagnoseScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentMyData) { helium314.keyboard.settings.screens.AgentMyDataScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.AgentMemory) { helium314.keyboard.settings.screens.AgentMemoryScreen(onClickBack = ::goBack) }
        composable(SettingsDestination.TextCorrection) {
            TextCorrectionScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.Preferences) {
            PreferencesScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.Toolbar) {
            ToolbarScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.GestureTyping) {
            GestureTypingScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.DataGathering) {
            GestureDataScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.DataReview) {
            ReviewScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.Advanced) {
            AdvancedSettingsScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.Debug) {
            DebugScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.Appearance) {
            AppearanceScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.PersonalDictionary + "{locale}") {
            val locale = it.arguments?.getString("locale")?.takeIf { loc -> loc.isNotBlank() }?.constructLocale()
            PersonalDictionaryScreen(
                onClickBack = ::goBack,
                locale = locale
            )
        }
        composable(SettingsDestination.PersonalDictionaries) {
            PersonalDictionariesScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.Languages) {
            LanguageScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.Dictionaries) {
            DictionaryScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.Layouts) {
            SecondaryLayoutScreen(onClickBack = ::goBack)
        }
        composable(SettingsDestination.Colors + "{theme}") {
            ColorsScreen(isNight = false, theme = it.arguments?.getString("theme"), onClickBack = ::goBack)
        }
        composable(SettingsDestination.ColorsNight + "{theme}") {
            ColorsScreen(isNight = true, theme = it.arguments?.getString("theme"), onClickBack = ::goBack)
        }
        composable(SettingsDestination.Subtype + "{subtype}") {
            SubtypeScreen(initialSubtype = it.arguments?.getString("subtype")!!.toSettingsSubtype(), onClickBack = ::goBack)
        }
    }
    if (target.value != SettingsDestination.Settings/* && target.value != navController.currentBackStackEntry?.destination?.route*/)
        navController.navigate(route = target.value)
}

object SettingsDestination {
    const val Settings = "settings"
    const val AgentProfiles = "agent_profiles"
    const val AgentAi = "agent_ai"
    const val AgentSkills = "agent_skills"
    const val AgentWorkflows = "agent_workflows"
    const val AgentLearning = "agent_learning"
    const val AgentDictation = "agent_dictation"
    const val AgentScreenshots = "agent_screenshots"
    const val AgentCommands = "agent_commands"
    const val AgentDesign = "agent_design"
    const val AgentUpdates = helium314.keyboard.agent.AgentPreferences.UPDATES_DESTINATION
    const val AgentSpace = "agent_space"
    const val AgentRgb = "agent_rgb"
    const val AgentSymbols = helium314.keyboard.agent.SymbolDrawing.DESTINATION
    const val AgentDiagnose = "agent_diagnose"
    const val AgentMyData = helium314.keyboard.agent.AgentPreferences.MY_DATA_DESTINATION
    const val AgentMemory = "agent_memory" // stage 46
    // fork (stage 38a): the seven areas of the new settings
    const val AreaLook = "area_look"
    const val AreaTyping = "area_typing"
    const val AreaKeys = "area_keys"
    const val AreaGestures = "area_gestures"
    const val AreaBar = "area_bar"
    const val AreaAi = "area_ai"
    const val AreaSystem = "area_system"
    const val About = "about"
    const val TextCorrection = "text_correction"
    const val Preferences = "preferences"
    const val Toolbar = "toolbar"
    const val GestureTyping = "gesture_typing"
    const val DataGathering = "data_gathering" // remove when data gathering phase is done (end of 2026 latest)
    const val DataReview = "data_review" // remove when data gathering phase is done (end of 2026 latest)
    const val Advanced = "advanced"
    const val Debug = "debug"
    const val Appearance = "appearance"
    const val Colors = "colors/"
    const val ColorsNight = "colors_night/"
    const val PersonalDictionaries = "personal_dictionaries"
    const val PersonalDictionary = "personal_dictionary/"
    const val Languages = "languages"
    const val Subtype = "subtype/"
    const val Layouts = "layouts"
    const val Dictionaries = "dictionaries"
    val navTarget = MutableStateFlow(Settings)

    private val navScope = CoroutineScope(Dispatchers.Default)
    fun navigateTo(target: String) {
        if (navTarget.value == target) {
            // triggers recompose twice, but that's ok as it's a rare event
            navTarget.value = Settings
            navScope.launch { delay(10); navTarget.value = target }
        } else
            navTarget.value = target
        navScope.launch { delay(50); navTarget.value = Settings }
    }
}
