// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import helium314.keyboard.agent.DesignTiles
import helium314.keyboard.agent.DesignTiles.Section
import helium314.keyboard.agent.DesignTiles.Tile
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.preferences.Preference

/** Key of the entry on the appearance screen that opens [AgentDesignScreen]. */
const val AGENT_DESIGN_SETTING = "agent_design_tiles"

/** Entry at the top of the appearance screen (also found by the settings search). */
fun createAgentDesignSetting(context: Context) =
    Setting(context, AGENT_DESIGN_SETTING, R.string.agent_design_title, R.string.agent_design_entry_description) { setting ->
        Preference(
            name = setting.title,
            description = setting.description,
            onClick = { SettingsDestination.navigateTo(SettingsDestination.AgentDesign) },
            modifier = Modifier.testTag(AGENT_DESIGN_SETTING),
        )
    }

/**
 * Design page (stage 23f1): a grid of tiles, three per row. Each tile shows a theme in miniature
 * (background, a bar as space key, a dot in the accent colour as Enter); a tap selects it.
 */
@Composable
fun AgentDesignScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.prefs()
    // the settings activity reports every preference change; the counter covers taps without it (tests)
    val changed = (context.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()?.value ?: 0
    var taps by remember { mutableIntStateOf(0) }
    val selected = remember(changed, taps) { DesignTiles.selectedId(prefs) }
    val sections = remember(changed, taps) { DesignTiles.sections(prefs) }
    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_design_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            sections.forEach { (section, tiles) ->
                if (tiles.isEmpty()) return@forEach
                Text(
                    stringResource(sectionTitle(section)),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
                )
                if (section == Section.TEMPLATES)
                    Text(stringResource(R.string.agent_design_flat_note), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 10.dp))
                tiles.chunked(COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { tile ->
                            DesignTile(tile, tile.id == selected, Modifier.weight(1f)) {
                                if (tile.id != selected) {
                                    DesignTiles.select(context, tile)
                                    taps++
                                }
                            }
                        }
                        repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Preference(
                name = stringResource(R.string.agent_design_more),
                onClick = { SettingsDestination.navigateTo(SettingsDestination.Appearance) },
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp).testTag("agent_design_more"),
            )
        }
    }
}

private const val COLUMNS = 3

private fun sectionTitle(section: Section) = when (section) {
    Section.STANDARD -> R.string.agent_design_section_standard
    Section.TEMPLATES -> R.string.agent_design_section_templates
    Section.COLORS -> R.string.agent_design_section_colors
}

@Composable
private fun tileLabel(tile: Tile): String = when (tile) {
    is Tile.Flat -> stringResource(R.string.agent_design_flat)
    is Tile.System -> stringResource(R.string.agent_design_system)
    is Tile.Colors -> tile.theme.getStringResourceOrName("theme_name_", LocalContext.current)
}

@Composable
private fun DesignTile(tile: Tile, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val swatches = remember(tile.id) { DesignTiles.swatches(context, tile) }
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag("agent_design_tile_${tile.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.25f)
                .then(if (selected) Modifier.border(3.dp, accent, shape) else Modifier)
                .padding(if (selected) 3.dp else 0.dp)
                .clip(RoundedCornerShape(if (selected) 9.dp else 12.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) { drawTile(swatches) }
            if (selected) CheckMark(accent, Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
        Text(
            tileLabel(tile),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Background, space bar and Enter dot; with two swatches the left half is day, the right half night. */
private fun DrawScope.drawTile(swatches: List<DesignTiles.Swatch>) {
    val half = size.width / swatches.size
    swatches.forEachIndexed { i, swatch ->
        drawRect(Color(swatch.background), Offset(half * i, 0f), Size(half, size.height))
    }
    // space bar and Enter like a bottom row: bar in the middle, dot to the right of it
    val barHeight = size.height * 0.16f
    val barWidth = size.width * 0.48f
    val barTop = size.height * 0.62f
    val barLeft = (size.width - barWidth) / 2 - size.width * 0.06f
    val dotRadius = barHeight * 0.62f
    val dotCenter = Offset(barLeft + barWidth + size.width * 0.06f + dotRadius, barTop + barHeight / 2)
    val radius = CornerRadius(barHeight / 2, barHeight / 2)
    if (swatches.size == 1) {
        drawRoundRect(Color(swatches[0].spaceBar), Offset(barLeft, barTop), Size(barWidth, barHeight), radius)
        drawCircle(Color(swatches[0].accent), dotRadius, dotCenter)
    } else {
        // each half keeps its own colours; the clip splits the bar at the middle of the tile
        swatches.forEachIndexed { i, swatch ->
            clipRect(half * i, 0f, half * (i + 1), size.height) {
                drawRoundRect(Color(swatch.spaceBar), Offset(barLeft, barTop), Size(barWidth, barHeight), radius)
                drawCircle(Color(swatch.accent), dotRadius, dotCenter)
            }
        }
    }
}

@Composable
private fun CheckMark(accent: Color, modifier: Modifier) {
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier.size(20.dp).clip(CircleShape)) {
        drawCircle(accent)
        val path = Path().apply {
            moveTo(size.width * 0.27f, size.height * 0.52f)
            lineTo(size.width * 0.43f, size.height * 0.68f)
            lineTo(size.width * 0.74f, size.height * 0.36f)
        }
        drawPath(path, onAccent, style = Stroke(width = size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
