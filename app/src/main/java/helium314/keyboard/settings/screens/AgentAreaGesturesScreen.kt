// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.os.Vibrator
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import helium314.keyboard.agent.AgentPreferences
import helium314.keyboard.agent.SpaceAction
import helium314.keyboard.agent.SpaceGesture
import helium314.keyboard.agent.SpaceGestures
import helium314.keyboard.agent.SymbolDrawing
import helium314.keyboard.agent.TouchModel
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.dialogs.SliderDialog
import helium314.keyboard.settings.preferences.getPrefOfType
import java.util.Locale

/**
 * Stage 38e: the area page "Gesten & Gefühl", 1:1 from docs/referenzen/einstellungen-mockup/Gesten.dc.html.
 * Space bar (detent picture, the six gestures with their real actions, each opening the list of the
 * old space bar page), what swiping over the letters does, drawn symbols, vibration and sound,
 * hit zones and swiping. Every value is read from and written to the existing preferences; the
 * dialogs are the existing ones (ListPickerDialog, SliderDialog, the hit zone calibration).
 */
@Composable
fun AgentAreaGesturesScreen(onNavigate: (String) -> Unit, onClickBack: () -> Unit) {
    AgentSettingsTheme { GesturesContent(onNavigate, onClickBack) }
}

@Composable
private fun GesturesContent(onNavigate: (String) -> Unit, onClickBack: () -> Unit) {
    val area = SettingsArea.GESTURES
    val context = LocalContext.current
    val prefs = context.prefs()
    // recompose when a preference changes elsewhere (SettingsActivity counts) or here (P121)
    val changed = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    var written by remember { mutableIntStateOf(0) }
    if ((changed?.value ?: 0) + written < 0) Log.v("irrelevant", "read both counters to recompose on a change")
    fun write(key: String, value: Boolean) { prefs.edit { putBoolean(key, value) }; written++ }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val close: () -> Unit = { dialog = null; written++ }

    run {
        Column(
            Modifier.fillMaxSize().background(AgentSettingsTokens.Background).windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(start = AgentSettingsTokens.PagePadding, end = AgentSettingsTokens.PagePadding, top = 16.dp, bottom = 32.dp)
                .testTag(AREA_TAG + area.route),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackHeader(onClickBack)
            AreaHero(area)
            SpaceCard(prefs) { dialog = it.key }
            SwipeChoice(prefs, onNavigate) { written++ }
            SymbolSection(context, prefs, area, onNavigate, ::write) { dialog = it }
            FeelSection(context, prefs, area, ::write) { dialog = it }
            TouchSection(context, prefs, area, ::write) { dialog = it }
        }
        dialog?.let { open ->
            SpaceGesture.entries.firstOrNull { it.key == open }?.let { GestureDialog(prefs, it, close) }
            GestureSlider.entries.firstOrNull { it.key == open }?.let { SliderSettingDialog(it, close) }
            if (open == TOUCH_CALIBRATION) CalibrationDialog(onDone = close)
        }
    }
}

/** Card "LEERTASTE": the detent picture, its caption and the six gestures in two columns. */
// reads the preferences itself: must not be skipped when only the counters above changed
@NonSkippableComposable
@Composable
private fun SpaceCard(prefs: SharedPreferences, onOpen: (SpaceGesture) -> Unit) {
    FeatureCard(stringResource(R.string.agent_gestures_space), spaced = true, gap = 14.dp) {
        SpaceDetentBar(spaceLanguage(prefs))
        Text(stringResource(R.string.agent_gestures_space_caption), style = AgentSettingsType.Small, color = AgentSettingsTokens.Muted)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpaceGesture.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { gesture ->
                        GestureButton(stringResource(gesture.keyLabel()), gestureValue(prefs, gesture), { onOpen(gesture) },
                            Modifier.weight(1f).fillMaxHeight().testTag(GESTURE_TAG + gesture.name))
                    }
                }
            }
        }
    }
}

/**
 * The space bar of the mockup: 64 dp, the language in the middle, two detent dots (12 dp at 22 %
 * and 42 %) and the glowing last dot (20 dp at 72 %, glow 16 dp) where the action happens.
 */
@Composable
fun SpaceDetentBar(language: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(AgentSettingsTokens.RowRadius))
            .background(AgentSettingsTokens.Border).semantics(mergeDescendants = true) { }.testTag(SPACE_BAR_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Text(language, style = AgentSettingsType.Label, color = AgentSettingsTokens.Muted)
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            fun dot(left: Float, top: Float, size: Float, color: Color, glow: Float = 0f) {
                val r = size.dp.toPx() / 2
                val center = Offset(this.size.width * left + r, top.dp.toPx() + r)
                if (glow > 0f) {
                    // CSS box-shadow 0 0 16px: a blur of 16 dp around the dot
                    val outer = r + glow.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(DETENT.copy(alpha = 0.9f), DETENT.copy(alpha = 0.35f), Color.Transparent),
                        center, outer), outer, center)
                }
                drawCircle(color, r, center)
            }
            dot(0.22f, 26f, 12f, DETENT)
            dot(0.42f, 26f, 12f, DETENT)
            dot(0.72f, 22f, 20f, LAST_DETENT, glow = 16f)
        }
    }
}

@Composable
private fun GestureButton(key: String, value: String, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val label = stringResource(R.string.agent_settings_open, key)
    Column(
        modifier.heightIn(min = 56.dp).clip(shape).background(AgentSettingsTokens.Inset).border(1.dp, AgentSettingsTokens.SwitchOff, shape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(key, style = AgentSettingsType.Small.copy(fontSize = 12.sp), color = AgentSettingsTokens.Text, modifier = Modifier.alpha(0.75f))
        Text(value, style = AgentSettingsType.Button.copy(fontWeight = FontWeight.Bold), color = AgentSettingsTokens.Text)
    }
}

/** The list of the old space bar page: same choices, same "Standard" (removes the own value). */
@Composable
private fun GestureDialog(prefs: SharedPreferences, gesture: SpaceGesture, onDone: () -> Unit) {
    val items = SpaceGestures.choices(gesture).map { stringResource(it.labelResource()) to it.name }
    val selected = items.firstOrNull { it.second == getPrefOfType(prefs, gesture.key, SpaceGestures.inherited(prefs, gesture).name) }
    val title = stringResource(gesture.titleResource())
    ListPickerDialog(
        onDismissRequest = onDone,
        items = items,
        onItemSelected = { item ->
            if (item != selected) SpaceGestures.write(prefs, gesture, SpaceAction.valueOf(item.second))
        },
        selectedItem = selected,
        title = { Text(title) },
        getItemName = { it.first },
        onDefault = { SpaceGestures.write(prefs, gesture, null) },
    )
}

/**
 * "ÜBER DIE BUCHSTABEN WISCHEN": nothing, drawn symbols or gesture typing on the two existing
 * switches. Drawn symbols win over gesture typing (stage 31), so choosing them keeps the stored
 * gesture typing value; gesture typing needs the library.
 */
// reads the preferences itself: must not be skipped when only the counters above changed
@NonSkippableComposable
@Composable
private fun SwipeChoice(prefs: SharedPreferences, onNavigate: (String) -> Unit, onWritten: () -> Unit) {
    val hasLib = JniUtils.sHaveGestureLib
    val area = SettingsArea.GESTURES
    ChoiceCards(
        stringResource(R.string.agent_gestures_swipe_title),
        listOf(
            Choice(stringResource(R.string.agent_gestures_choice_none), stringResource(R.string.agent_gestures_choice_none_hint),
                Color(0xFF3A3A42), Color(0xFF6B6B76), SWIPE_NONE),
            Choice(stringResource(R.string.agent_gestures_choice_draw), stringResource(R.string.agent_gestures_choice_draw_hint),
                area.from, area.to, SWIPE_DRAW),
            Choice(stringResource(R.string.agent_gestures_choice_gesture),
                stringResource(if (hasLib) R.string.agent_gestures_choice_gesture_hint else R.string.agent_gestures_choice_gesture_missing),
                Color(0xFF5B8CFF), Color(0xFFA9C7FF), SWIPE_GESTURE, enabled = hasLib),
        ),
        selected = swipeChoice(prefs),
        onSelect = { choice ->
            writeSwipeChoice(prefs, choice)
            onWritten()
        },
    ) {
        if (hasLib) SettingRow(stringResource(R.string.agent_gestures_gesture_options), stringResource(R.string.agent_gestures_gesture_options_summary),
            area.dot, { onNavigate(SettingsDestination.GestureTyping) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.GestureTyping))
    }
}

/** 0 nothing, 1 drawn symbols, 2 gesture typing (only with the library, as in SettingsValues). */
internal fun swipeChoice(prefs: SharedPreferences): Int = when {
    prefs.getBoolean(AgentPreferences.SYMBOL_DRAWING, false) -> 1
    JniUtils.sHaveGestureLib && prefs.getBoolean(Settings.PREF_GESTURE_INPUT, Defaults.PREF_GESTURE_INPUT) -> 2
    else -> 0
}

internal fun writeSwipeChoice(prefs: SharedPreferences, choice: Int) = prefs.edit {
    when (choice) {
        0 -> { putBoolean(AgentPreferences.SYMBOL_DRAWING, false); putBoolean(Settings.PREF_GESTURE_INPUT, false) }
        1 -> putBoolean(AgentPreferences.SYMBOL_DRAWING, true)
        2 -> if (JniUtils.sHaveGestureLib) { putBoolean(AgentPreferences.SYMBOL_DRAWING, false); putBoolean(Settings.PREF_GESTURE_INPUT, true) }
    }
}

// reads the preferences itself: must not be skipped when only the counters above changed
@NonSkippableComposable
@Composable
private fun SymbolSection(
    context: Context, prefs: SharedPreferences, area: SettingsArea, onNavigate: (String) -> Unit,
    putBoolean: (String, Boolean) -> Unit, onDialog: (String) -> Unit,
) {
    // read once per visit: the symbols page may change them, and this page is composed anew after it
    val own = remember { runCatching { SymbolDrawing.ownSymbols(context).size }.getOrDefault(0) }
    SectionCard(stringResource(R.string.agent_gestures_symbols)) {
        SettingRow(stringResource(R.string.agent_gestures_own), stringResource(R.string.agent_gestures_own_summary), area.dot,
            { onNavigate(SettingsDestination.AgentSymbols) }, Modifier.testTag(AREA_ROW_TAG + SettingsDestination.AgentSymbols), own.toString())
        SettingSwitchRow(stringResource(R.string.agent_gestures_learning), stringResource(R.string.agent_gestures_learning_summary), area.dot,
            prefs.getBoolean(AgentPreferences.SYMBOL_LEARNING, false), { putBoolean(AgentPreferences.SYMBOL_LEARNING, it) },
            area.from, area.to, Modifier.testTag(ROW_TAG + AgentPreferences.SYMBOL_LEARNING))
        SettingRow(stringResource(R.string.agent_gestures_calibrate), stringResource(R.string.agent_gestures_calibrate_summary), area.dot,
            { onNavigate(SettingsDestination.AgentSymbols) }, Modifier.testTag(ROW_TAG + SYMBOL_CALIBRATION))
        SettingRow(stringResource(R.string.agent_gestures_trail), stringResource(R.string.agent_gestures_trail_summary), area.dot,
            { onDialog(GestureSlider.TRAIL.key) }, Modifier.testTag(ROW_TAG + GestureSlider.TRAIL.key), GestureSlider.TRAIL.value(prefs))
    }
}

// reads the preferences itself: must not be skipped when only the counters above changed
@NonSkippableComposable
@Composable
private fun FeelSection(context: Context, prefs: SharedPreferences, area: SettingsArea, putBoolean: (String, Boolean) -> Unit, onDialog: (String) -> Unit) {
    val hasVibrator = remember { ContextCompat.getSystemService(context, Vibrator::class.java)?.hasVibrator() == true } // ContextCompat: also on API 21/22
    val vibrate = prefs.getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON)
    val sound = prefs.getBoolean(Settings.PREF_SOUND_ON, Defaults.PREF_SOUND_ON)
    SectionCard(stringResource(R.string.agent_gestures_feel)) {
        // the same conditions as on the options page
        if (hasVibrator) SettingSwitchRow(stringResource(R.string.agent_gestures_vibrate), stringResource(R.string.agent_gestures_vibrate_summary),
            area.dot, vibrate, { putBoolean(Settings.PREF_VIBRATE_ON, it) }, area.from, area.to, Modifier.testTag(ROW_TAG + Settings.PREF_VIBRATE_ON))
        if (vibrate) {
            SettingRow(stringResource(R.string.agent_gestures_duration), stringResource(R.string.agent_gestures_duration_summary), area.dot,
                { onDialog(GestureSlider.VIBRATION.key) }, Modifier.testTag(ROW_TAG + GestureSlider.VIBRATION.key), GestureSlider.VIBRATION.value(prefs))
            SettingSwitchRow(stringResource(R.string.agent_gestures_dnd), stringResource(R.string.agent_gestures_dnd_summary), area.dot,
                prefs.getBoolean(Settings.PREF_VIBRATE_IN_DND_MODE, Defaults.PREF_VIBRATE_IN_DND_MODE),
                { putBoolean(Settings.PREF_VIBRATE_IN_DND_MODE, it) }, area.from, area.to, Modifier.testTag(ROW_TAG + Settings.PREF_VIBRATE_IN_DND_MODE))
        }
        SettingSwitchRow(stringResource(R.string.agent_gestures_sound), stringResource(R.string.agent_gestures_sound_summary), area.dot,
            sound, { putBoolean(Settings.PREF_SOUND_ON, it) }, area.from, area.to, Modifier.testTag(ROW_TAG + Settings.PREF_SOUND_ON))
        if (sound) SettingRow(stringResource(R.string.agent_gestures_volume), stringResource(R.string.agent_gestures_volume_summary), area.dot,
            { onDialog(GestureSlider.VOLUME.key) }, Modifier.testTag(ROW_TAG + GestureSlider.VOLUME.key), GestureSlider.VOLUME.value(prefs))
    }
}

// reads the preferences itself: must not be skipped when only the counters above changed
@NonSkippableComposable
@Composable
private fun TouchSection(context: Context, prefs: SharedPreferences, area: SettingsArea, putBoolean: (String, Boolean) -> Unit, onDialog: (String) -> Unit) {
    val gestures = SpaceGestures.read(prefs)
    val swipes = SpaceGesture.entries.filter { it.isSwipe }.map { gestures[it] }
    SectionCard(stringResource(R.string.agent_gestures_touch)) {
        SettingSwitchRow(stringResource(R.string.agent_gestures_adapt), stringResource(R.string.agent_gestures_adapt_summary), area.dot,
            prefs.getBoolean(AgentPreferences.ADAPT_TOUCH, false),
            { putBoolean(AgentPreferences.ADAPT_TOUCH, it); TouchModel.enabled = it }, area.from, area.to,
            Modifier.testTag(ROW_TAG + AgentPreferences.ADAPT_TOUCH))
        SettingRow(stringResource(R.string.agent_gestures_touch_calibrate), stringResource(R.string.agent_gestures_touch_calibrate_summary), area.dot,
            {
                // as on the learning page: the stored zones first, else applying would drop the keys not typed
                runCatching { TouchModel.loadStored(context) }
                onDialog(TOUCH_CALIBRATION)
            }, Modifier.testTag(ROW_TAG + TOUCH_CALIBRATION))
        SettingSwitchRow(stringResource(R.string.agent_gestures_delete_swipe), stringResource(R.string.agent_gestures_delete_swipe_summary), area.dot,
            prefs.getBoolean(Settings.PREF_DELETE_SWIPE, Defaults.PREF_DELETE_SWIPE), { putBoolean(Settings.PREF_DELETE_SWIPE, it) },
            area.from, area.to, Modifier.testTag(ROW_TAG + Settings.PREF_DELETE_SWIPE))
        SettingRow(stringResource(R.string.agent_gestures_touchpad), stringResource(R.string.agent_gestures_touchpad_summary), area.dot,
            { onDialog(GestureSlider.TOUCHPAD.key) }, Modifier.testTag(ROW_TAG + GestureSlider.TOUCHPAD.key), GestureSlider.TOUCHPAD.value(prefs))
        // the rest of the old space bar page, under the same conditions
        if (SpaceAction.TOUCHPAD_MODE in swipes)
            SettingSwitchRow(stringResource(R.string.agent_gestures_edge_scroll), stringResource(R.string.agent_gestures_edge_scroll_summary), area.dot,
                prefs.getBoolean(Settings.PREF_TOUCHPAD_EDGE_SCROLL, Defaults.PREF_TOUCHPAD_EDGE_SCROLL),
                { putBoolean(Settings.PREF_TOUCHPAD_EDGE_SCROLL, it) }, area.from, area.to, Modifier.testTag(ROW_TAG + Settings.PREF_TOUCHPAD_EDGE_SCROLL))
        if (SpaceAction.SWITCH_LANGUAGE in swipes)
            SettingRow(stringResource(R.string.agent_gestures_language_distance), stringResource(R.string.agent_gestures_language_distance_summary),
                area.dot, { onDialog(GestureSlider.LANGUAGE_DISTANCE.key) }, Modifier.testTag(ROW_TAG + GestureSlider.LANGUAGE_DISTANCE.key),
                GestureSlider.LANGUAGE_DISTANCE.value(prefs))
    }
}

/**
 * The sliders of the page with exactly the key, default, range, steps and live feedback of their
 * old rows (PreferencesScreen, GestureTypingScreen, AdvancedScreen). [describe] is the old text in
 * the dialog, [value] the short value on the row.
 */
internal enum class GestureSlider(val key: String, val title: Int, val default: Number, val range: ClosedFloatingPointRange<Float>, val step: Int? = null) {
    TRAIL(Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION, R.string.gesture_trail_fadeout_duration, Defaults.PREF_GESTURE_TRAIL_FADEOUT_DURATION, 100f..1900f, 10),
    VIBRATION(Settings.PREF_VIBRATION_DURATION_SETTINGS, R.string.prefs_keypress_vibration_duration_settings, Defaults.PREF_VIBRATION_DURATION_SETTINGS, -1f..100f),
    VOLUME(Settings.PREF_KEYPRESS_SOUND_VOLUME, R.string.prefs_keypress_sound_volume_settings, Defaults.PREF_KEYPRESS_SOUND_VOLUME, -0.01f..1f),
    TOUCHPAD(Settings.PREF_TOUCHPAD_SENSITIVITY, R.string.touchpad_sensitivity, Defaults.PREF_TOUCHPAD_SENSITIVITY, 0f..100f),
    LANGUAGE_DISTANCE(Settings.PREF_LANGUAGE_SWIPE_DISTANCE, R.string.prefs_language_swipe_distance, Defaults.PREF_LANGUAGE_SWIPE_DISTANCE, 2f..18f);

    fun read(prefs: SharedPreferences): Float = getPrefOfType(prefs, key, default).toFloat()

    @Composable
    fun describe(v: Float): String = when (this) {
        TRAIL -> stringResource(R.string.abbreviation_unit_milliseconds, (v.toInt() + 100).toString())
        VIBRATION -> if (v < 0) stringResource(R.string.settings_system_default) else stringResource(R.string.abbreviation_unit_milliseconds, v.toInt().toString())
        VOLUME -> if (v < 0) stringResource(R.string.settings_system_default) else (v * 100).toInt().toString()
        TOUCHPAD, LANGUAGE_DISTANCE -> v.toInt().toString()
    }

    @Composable
    fun value(prefs: SharedPreferences): String {
        val v = read(prefs)
        return when (this) {
            // the trail stays 100 ms longer than the stored value (GestureTypingScreen)
            TRAIL -> stringResource(R.string.agent_gestures_seconds, String.format(Locale.getDefault(), "%.1f", (v + 100f) / 1000f))
            VIBRATION -> if (v < 0) stringResource(R.string.agent_gestures_system) else describe(v)
            VOLUME -> if (v < 0) stringResource(R.string.agent_gestures_system) else "${(v * 100).toInt()} %"
            TOUCHPAD, LANGUAGE_DISTANCE -> describe(v)
        }
    }
}

/** The dialog of SliderPreference for one [GestureSlider], with its title. */
@Composable
private fun SliderSettingDialog(slider: GestureSlider, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    val integer = slider.default is Int
    val title = stringResource(slider.title)
    SliderDialog(
        onDismissRequest = onDone,
        onDone = {
            prefs.edit { if (integer) putInt(slider.key, it.toInt()) else putFloat(slider.key, it) }
            if (slider == GestureSlider.TRAIL) KeyboardSwitcher.getInstance().setThemeNeedsReload()
        },
        initialValue = slider.read(prefs),
        range = slider.range,
        title = { Text(title) },
        positionString = { slider.describe(if (integer) it.toInt().toFloat() else it) },
        onValueChanged = { v ->
            when (slider) {
                GestureSlider.VIBRATION -> AudioAndHapticFeedbackManager.getInstance().vibrate(v.toLong())
                GestureSlider.VOLUME -> (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                    ?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, v)
                else -> Unit
            }
        },
        showDefault = true,
        onDefault = {
            prefs.edit { remove(slider.key) }
            if (slider == GestureSlider.TRAIL) KeyboardSwitcher.getInstance().setThemeNeedsReload()
        },
        intermediateSteps = slider.step?.let { ((slider.range.endInclusive - slider.range.start) / it - 1).toInt() },
        defaultValue = slider.default.toFloat(),
        integerValues = integer,
    )
}

/** What a gesture does now; a double tap without action is HeliBoard's "period + space" when that is on. */
@Composable
private fun gestureValue(prefs: SharedPreferences, gesture: SpaceGesture): String {
    val action = SpaceGestures.resolve(prefs, gesture)
    if (gesture == SpaceGesture.DOUBLE_TAP && action == SpaceAction.NONE &&
        prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, Defaults.PREF_KEY_USE_DOUBLE_SPACE_PERIOD))
        return stringResource(R.string.agent_gestures_double_space_period)
    return stringResource(action.shortLabel())
}

/** The label of the old list, shortened where it does not fit a half-width button (mockup). */
private fun SpaceAction.shortLabel() = when (this) {
    SpaceAction.COMMAND_LIST -> R.string.agent_gestures_short_command_list
    SpaceAction.HIDE_KEYBOARD -> R.string.agent_gestures_short_hide
    SpaceAction.SWITCH_INPUT_METHOD -> R.string.agent_gestures_short_input_method
    SpaceAction.TOGGLE_NUMPAD -> R.string.agent_gestures_short_numpad
    SpaceAction.TOGGLE_SYMBOLS -> R.string.agent_gestures_short_symbols
    SpaceAction.TOGGLE_DPAD -> R.string.agent_gestures_short_dpad
    else -> labelResource()
}

private fun SpaceGesture.keyLabel() = when (this) {
    SpaceGesture.SWIPE_LEFT -> R.string.agent_gestures_key_left
    SpaceGesture.SWIPE_RIGHT -> R.string.agent_gestures_key_right
    SpaceGesture.SWIPE_UP -> R.string.agent_gestures_key_up
    SpaceGesture.SWIPE_DOWN -> R.string.agent_gestures_key_down
    SpaceGesture.HOLD -> R.string.agent_gestures_key_hold
    SpaceGesture.DOUBLE_TAP -> R.string.agent_gestures_key_double_tap
}

/** The language on the space bar, as the keyboard shows it. */
private fun spaceLanguage(prefs: SharedPreferences): String = runCatching {
    SubtypeLocaleUtils.getSubtypeLanguageDisplayName(SubtypeSettings.getSelectedSubtype(prefs).locale())
}.getOrDefault("")

private val DETENT = Color(0xFFC084FC)
private val LAST_DETENT = Color(0xFFE9D5FF)

const val GESTURE_TAG = "agent_gestures_space_"
const val SPACE_BAR_TAG = "agent_gestures_space_bar"
const val ROW_TAG = "agent_gestures_row_"
const val SWIPE_NONE = "swipe_none"
const val SWIPE_DRAW = "swipe_draw"
const val SWIPE_GESTURE = "swipe_gesture"
const val TOUCH_CALIBRATION = "touch_calibration"
const val SYMBOL_CALIBRATION = "symbol_calibration"
