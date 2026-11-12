// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.Preference

/**
 * One entry of the main settings page. The page renders it either as an upstream [Preference]
 * or, in the modern look, as an [AgentMainSettingsRow].
 */
class MainSettingsEntry(
    val name: String,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit,
    val description: String? = null,
    /** outline icon for the modern look, the upstream icon if that is already an outline */
    @DrawableRes val modernIcon: Int = icon,
)

/**
 * Modern main settings page (stage 23e): large collapsing title, tall single-line rows with a
 * monochrome outline icon and no dividers, page background without a separate bar color.
 * Shown while the keyboard uses the flat style; every other style keeps the upstream look.
 */
object AgentMainSettings {
    const val LIST_TAG = "agent_main_settings_list"
    const val ROW_TAG = "agent_main_settings_row"
    val ROW_MIN_HEIGHT = 76.dp
    val ICON_SIZE = 24.dp
    val TITLE_SIZE = 20.sp

    /** Warm dark page background below Android 12, where no dynamic system color exists. */
    val WARM_DARK = Color(0xFF1D1B19)
    val WARM_DARK_CONTAINER = Color(0xFF292623)

    fun isModern(prefs: SharedPreferences): Boolean =
        prefs.getString(Settings.PREF_THEME_STYLE, Defaults.PREF_THEME_STYLE) == KeyboardTheme.STYLE_FLAT
}

/** Whether the main settings page uses the modern look; recomposes when a setting changes. */
@Composable
fun agentMainSettingsModern(): Boolean {
    val ctx = LocalContext.current
    val changed = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((changed?.value ?: 0) < 0)
        Log.v("irrelevant", "read the counter so a preference change triggers recomposition")
    return AgentMainSettings.isModern(ctx.prefs())
}

/**
 * Keeps the system's dynamic colors (Android 12+). Older versions get a warm dark page in dark
 * mode instead of the purple-grey Material default; light mode stays unchanged.
 */
@Composable
fun AgentMainSettingsTheme(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val colors = if (dark && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) scheme.copy(
        background = AgentMainSettings.WARM_DARK,
        surface = AgentMainSettings.WARM_DARK,
        surfaceContainer = AgentMainSettings.WARM_DARK_CONTAINER,
        surfaceContainerHigh = AgentMainSettings.WARM_DARK_CONTAINER,
    ) else scheme
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
}

/** Upstream rendering of the entries, unchanged apart from coming from a list. */
@Composable
fun UpstreamMainSettingsList(entries: List<MainSettingsEntry>) {
    entries.forEach {
        Preference(name = it.name, onClick = it.onClick, description = it.description, icon = it.icon) { NextScreenIcon() }
    }
}

@Composable
fun AgentMainSettingsList(entries: List<MainSettingsEntry>) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
        Column(
            Modifier.testTag(AgentMainSettings.LIST_TAG).verticalScroll(rememberScrollState())
                .padding(innerPadding).padding(vertical = 8.dp)
        ) {
            entries.forEach { AgentMainSettingsRow(it) }
        }
    }
}

@Composable
fun AgentMainSettingsRow(entry: MainSettingsEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AgentMainSettings.ROW_TAG)
            .clickable(onClick = entry.onClick)
            .heightIn(min = AgentMainSettings.ROW_MIN_HEIGHT)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the title next to it already names the entry for screen readers
        Icon(
            painterResource(entry.modernIcon), null,
            Modifier.size(AgentMainSettings.ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = AgentMainSettings.TITLE_SIZE, fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.description != null)
                Text(
                    text = entry.description,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
        }
    }
}
