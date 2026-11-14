// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * Stage 37 (owner's sketch docs/referenzen/2026-11-13-rgb-bildwiederholrate.jpg): the frame rate of
 * the RGB lighting as a card with two big colour areas instead of chips.
 */

/** The rates of [dev.agentkeyboard.core.RgbSettings.fps] (normalised to these two). */
internal val FRAME_RATES = listOf(30, 60)

/**
 * One round trip of both lights. They travel at the same speed on the same track (owner,
 * 2026-11-14) and differ only in how often they move: 30 or 60 times a second ([lightPhase]).
 */
internal const val LIGHT_TRIP_MS = 2400

/** The moment of the trip shown without motion, and where the motion starts (no jump): both lights on their way. */
internal const val STILL_PHASE = 0.18f

/** Tag of the card; the areas are tagged [FRAME_RATE_AREA_TAG] + rate, as the chips before them. */
const val FRAME_RATE_TAG = "agent_rgb_fps_card"
const val FRAME_RATE_AREA_TAG = "agent_rgb_fps_"
const val FRAME_RATE_HEADING_TAG = "agent_rgb_fps_heading"

/** The heading: bold, spaced in em so that the spacing shrinks with the text; its size is set by auto-size. */
internal val FRAME_RATE_HEADING_STYLE = AgentSettingsType.SpacedTitle.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.19.em)
internal val FRAME_RATE_HEADING_MAX = 21.sp
internal val FRAME_RATE_HEADING_MIN = 12.sp

/** How often [FrameRateCard] was composed (tests: frames of the light must not compose it). */
@VisibleForTesting
internal var frameRateCompositions = 0

/** Whether the light of the last composed [FrameRateCard] moves (tests: only on screen and with motion allowed). */
@VisibleForTesting
internal var frameRateRunning = false

/** Where in its round trip (0..1) each light was drawn last, by index in [FRAME_RATES]. */
@VisibleForTesting
internal val frameRateTrips = FloatArray(FRAME_RATES.size)

/** The shared clock (in trips, from [STILL_PHASE]) as the area of each rate read it last, by index in [FRAME_RATES]. */
@VisibleForTesting
internal val frameRatePhases = FloatArray(FRAME_RATES.size)

/** Orange to yellow for 30 Hz, turquoise to green for 60 Hz (owner, 2026-11-14; the sketch had them the other way round). */
internal val FRAME_RATE_COLOURS = listOf(
    Color(0xFFFFA23F) to Color(0xFFFFD45C),
    Color(0xFF00C2A8) to Color(0xFF5BE6BE),
)
private val AREA_HINTS = listOf(R.string.agent_rgb_fps_30_hint, R.string.agent_rgb_fps_60_hint)

/** Earlier places of the light drawn behind it, one 30 Hz frame (1/72 of a trip, 33 ms) apart. */
private const val TRAIL = 5
private const val TRAIL_GAP = 1f / 72

/**
 * Where a light that moves [rate] times a second shows the shared clock [phase] (in trips): held
 * for 1/[rate] s, in whole steps from the start of the trip (72 places per trip at 30 Hz, 144 at
 * 60 Hz). Quantised here, not by the display, which may run at 60, 90 or 120 Hz.
 */
internal fun lightPhase(phase: Float, rate: Int): Float {
    val steps = rate * LIGHT_TRIP_MS / 1000
    // a hair over the step so that a clock exactly on a step is not floored one step back
    return STILL_PHASE + floor((phase - STILL_PHASE) * steps + 1e-3f) / steps
}

/**
 * The frame rate as a card "Bildwiederholrate" (bold, spaced heading on one line, smaller where it
 * would not fit) with two big rounded colour areas, "30 HERTZ" and "60 HERTZ", one above the other.
 * A glowing drop in each travels to and fro with a fading trail; both on the same track at the same
 * speed from the same clock, so they stay above each other, but the 30 Hz drop moves in 33 ms steps
 * and the 60 Hz drop in 17 ms steps ([lightPhase]). One infinite transition drives both drops and is
 * read only while drawing, so frames redraw the two areas and compose nothing. It exists only while
 * the areas are on screen and motion is allowed (Android animations on when the card is first
 * shown, no battery saver, followed live); otherwise the drops stand still at [STILL_PHASE]. Chosen
 * area and the dimmed other one as [choiceSurface] (the text stays opaque); TalkBack: radio buttons
 * in one group. [below] follows inside the card.
 */
@Composable
internal fun FrameRateCard(
    fps: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val motion = rememberMotionAllowed()
    var onScreen by remember { mutableStateOf(false) }
    val phase: State<Float>? = if (motion && onScreen) rememberInfiniteTransition(label = "frame rate")
        .animateFloat(STILL_PHASE, STILL_PHASE + 1f, infiniteRepeatable(tween(LIGHT_TRIP_MS, easing = LinearEasing)), label = "light")
    else null
    SideEffect { frameRateCompositions++; frameRateRunning = phase != null }
    val outline = MaterialTheme.colorScheme.onSurface
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = base,
        modifier = modifier.fillMaxWidth().padding(top = 12.dp).testTag(FRAME_RATE_TAG),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // one line, never broken inside the word: smaller (the spacing in em shrinks along) where
            // 21 sp do not fit, e.g. at 360 dp with font scale 1.3
            Text(
                stringResource(R.string.agent_rgb_fps),
                style = FRAME_RATE_HEADING_STYLE,
                color = MaterialTheme.colorScheme.onSurface,
                autoSize = TextAutoSize.StepBased(minFontSize = FRAME_RATE_HEADING_MIN, maxFontSize = FRAME_RATE_HEADING_MAX, stepSize = 0.5.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 2.dp).semantics { heading() }.testTag(FRAME_RATE_HEADING_TAG),
            )
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).selectableGroup()
                    // on screen: not scrolled out of the page (the scroll container clips the bounds)
                    .onGloballyPositioned {
                        val bounds = it.boundsInWindow()
                        val shown = bounds.width > 0f && bounds.height > 0f
                        if (shown != onScreen) onScreen = shown
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FRAME_RATES.forEachIndexed { i, rate -> RateArea(rate, i, rate == fps, phase, outline, base) { onPick(rate) } }
            }
            below()
        }
    }
}

@Composable
private fun RateArea(rate: Int, index: Int, selected: Boolean, phase: State<Float>?, outline: Color, base: Color, onClick: () -> Unit) {
    val (from, to) = FRAME_RATE_COLOURS[index]
    val label = stringResource(R.string.agent_rgb_fps_hertz, rate)
    val hint = stringResource(R.string.agent_rgb_fps_line, rate, stringResource(AREA_HINTS[index]))
    val dim = choiceDim(selected, from, to, base)
    Box(
        Modifier.fillMaxWidth()
            .choiceSurface(selected, from, to, outline, base)
            // the clock is read here only: a frame redraws this area, nothing is composed
            .drawBehind {
                val p = phase?.value ?: STILL_PHASE
                val shown = lightPhase(p, rate)
                frameRatePhases[index] = p
                frameRateTrips[index] = (shown % 1f + 1f) % 1f
                drawLight(shown, dim)
            }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 104.dp)
            .testTag(FRAME_RATE_AREA_TAG + rate),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, top = 52.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // spaced capitals on screen, "30 Hertz" for TalkBack
            Text(label.uppercase(Locale.getDefault()), style = AgentSettingsType.ChoiceLabel.copy(letterSpacing = 4.sp),
                color = AgentSettingsTokens.OnGradient, modifier = Modifier.clearAndSetSemantics { contentDescription = label })
            Text(hint, style = AgentSettingsType.Small.copy(fontWeight = FontWeight.Medium), color = AgentSettingsTokens.OnGradient)
        }
    }
}

/**
 * The glowing drop near the top of an area at [phase] (in trips, already stepped by [lightPhase]),
 * travelling to and fro, with a fading trail of where it was in the last 30 Hz frames and a halo
 * that pulses twice per trip; [dim] as the gradient below. A few ovals, no shaders or allocations
 * of note per frame.
 */
private fun DrawScope.drawLight(phase: Float, dim: Float) {
    val rx = 14.dp.toPx()
    val ry = 8.5.dp.toPx()
    val left = 18.dp.toPx() + rx * 1.6f
    val right = size.width - left
    val y = 28.dp.toPx()
    val tau = 2f * PI.toFloat()
    fun x(p: Float) = left + (right - left) * (0.5f - 0.5f * cos(tau * p))
    fun oval(color: Color, cx: Float, scale: Float) =
        drawOval(color, topLeft = Offset(cx - rx * scale, y - ry * scale), size = Size(2 * rx * scale, 2 * ry * scale))
    for (k in TRAIL downTo 1) {
        val f = 1f - k / (TRAIL + 1f)
        oval(Color.White.copy(alpha = 0.34f * f * dim), x(phase - k * TRAIL_GAP), 0.45f + 0.5f * f)
    }
    val cx = x(phase)
    val pulse = 0.5f + 0.5f * sin(tau * 2f * phase)
    oval(Color.White.copy(alpha = (0.12f + 0.1f * pulse) * dim), cx, 2.1f + 0.25f * pulse)
    oval(Color.White.copy(alpha = 0.3f * dim), cx, 1.45f)
    oval(Color.White.copy(alpha = 0.93f * dim), cx, 1f)
}

/** Android animations on (read when the card is first shown) and no battery saver (followed live while shown). */
@Composable
private fun rememberMotionAllowed(): Boolean {
    val context = LocalContext.current
    val animations = remember { FlatStyle.animationsEnabled(context) }
    var saving by remember { mutableStateOf(powerSaving(context)) }
    DisposableEffect(context) {
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { saving = powerSaving(app) }
        }
        val registered = runCatching { app.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)) }.isSuccess
        // a change between the first read and the registration sends this receiver no broadcast
        if (registered) saving = powerSaving(app)
        onDispose { if (registered) runCatching { app.unregisterReceiver(receiver) } }
    }
    return animations && !saving
}

private fun powerSaving(context: Context) = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
