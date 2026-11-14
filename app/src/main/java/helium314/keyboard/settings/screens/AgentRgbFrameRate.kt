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
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.latin.R
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Stage 37 (owner's sketch docs/referenzen/2026-11-13-rgb-bildwiederholrate.jpg): the frame rate of
 * the RGB lighting as a card with two big colour areas instead of chips.
 */

/** The rates of [dev.agentkeyboard.core.RgbSettings.fps] (normalised to these two). */
internal val FRAME_RATES = listOf(30, 60)

/** One round trip of the 30 Hz light; the 60 Hz light makes two in the same time. */
internal const val LIGHT_TRIP_MS = 2400

/** The moment of the trip shown without motion, and where the motion starts (no jump): both lights on their way. */
internal const val STILL_PHASE = 0.18f

/** Tag of the card; the areas are tagged [FRAME_RATE_AREA_TAG] + rate, as the chips before them. */
const val FRAME_RATE_TAG = "agent_rgb_fps_card"
const val FRAME_RATE_AREA_TAG = "agent_rgb_fps_"

/** How often [FrameRateCard] was composed (tests: frames of the light must not compose it). */
@VisibleForTesting
internal var frameRateCompositions = 0

/** Whether the light of the last composed [FrameRateCard] moves (tests: only on screen and with motion allowed). */
@VisibleForTesting
internal var frameRateRunning = false

/** Where in its round trip (0..1) each light was drawn last, by index in [FRAME_RATES]. */
@VisibleForTesting
internal val frameRateTrips = FloatArray(FRAME_RATES.size)

/** Turquoise for 30 Hz, orange to yellow for 60 Hz (the sketch's colours). */
private val AREA_COLOURS = listOf(
    Color(0xFF00C2A8) to Color(0xFF5BE6BE),
    Color(0xFFFFA23F) to Color(0xFFFFD45C),
)
private val AREA_HINTS = listOf(R.string.agent_rgb_fps_30_hint, R.string.agent_rgb_fps_60_hint)

/** Earlier places of the light drawn behind it, [TRAIL_GAP] of a 30 Hz trip apart (about 50 ms). */
private const val TRAIL = 5
private const val TRAIL_GAP = 0.021f

/**
 * The frame rate as a card "Bildwiederholrate" (bold, spaced heading) with two big rounded colour
 * areas, "30 HERTZ" and "60 HERTZ". A glowing drop in each shows the tempo: it travels to and fro
 * with a fading trail, at 60 Hz twice as fast. One infinite transition drives both drops and is read
 * only while drawing, so frames redraw the two areas and compose nothing. It exists only while the
 * areas are on screen and motion is allowed (Android animations on, no battery saver); otherwise
 * the drops stand still at [STILL_PHASE]. The chosen area at full opacity with an outline, the other
 * dimmed (as [ChoiceCards]); TalkBack: radio buttons in one group. [below] follows inside the card.
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
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(top = 12.dp).testTag(FRAME_RATE_TAG),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.agent_rgb_fps),
                style = AgentSettingsType.SpacedTitle.copy(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 2.dp).semantics { heading() },
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
                FRAME_RATES.forEachIndexed { i, rate -> RateArea(rate, i, rate == fps, phase, outline) { onPick(rate) } }
            }
            below()
        }
    }
}

@Composable
private fun RateArea(rate: Int, index: Int, selected: Boolean, phase: State<Float>?, outline: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AgentSettingsTokens.ChoiceRadius)
    val (from, to) = AREA_COLOURS[index]
    val label = stringResource(R.string.agent_rgb_fps_hertz, rate)
    val hint = stringResource(R.string.agent_rgb_fps_value, rate) + " · " + stringResource(AREA_HINTS[index])
    val speed = rate / FRAME_RATES[0]
    Box(
        Modifier.fillMaxWidth()
            // outline 3 dp, 2 dp outside the area, drawn before the clip (as ChoiceCards)
            .drawWithContent {
                drawContent()
                if (selected) {
                    val w = 3.dp.toPx(); val off = 2.dp.toPx() + w / 2
                    drawRoundRect(outline, topLeft = Offset(-off, -off), size = Size(size.width + 2 * off, size.height + 2 * off),
                        cornerRadius = CornerRadius(AgentSettingsTokens.ChoiceRadius.toPx() + off), style = Stroke(w))
                }
            }
            .alpha(if (selected) 1f else 0.55f)
            .clip(shape).background(cssGradient(120f, from, to))
            // the clock is read here only: a frame redraws this area, nothing is composed
            .drawBehind {
                val p = phase?.value ?: STILL_PHASE
                frameRateTrips[index] = ((p * speed) % 1f + 1f) % 1f
                drawLight(p, speed)
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
 * The glowing drop near the top of an area at [phase] (in 30 Hz trips; [speed] trips per 30 Hz
 * trip), travelling to and fro, with a fading trail of where it was a moment before and a halo
 * that pulses twice per trip. A few ovals, no shaders or allocations of note per frame.
 */
private fun DrawScope.drawLight(phase: Float, speed: Int) {
    val rx = 14.dp.toPx()
    val ry = 8.5.dp.toPx()
    val left = 18.dp.toPx() + rx * 1.6f
    val right = size.width - left
    val y = 28.dp.toPx()
    val tau = 2f * PI.toFloat()
    fun x(p: Float) = left + (right - left) * (0.5f - 0.5f * cos(tau * p * speed))
    fun oval(color: Color, cx: Float, scale: Float) =
        drawOval(color, topLeft = Offset(cx - rx * scale, y - ry * scale), size = Size(2 * rx * scale, 2 * ry * scale))
    for (k in TRAIL downTo 1) {
        val f = 1f - k / (TRAIL + 1f)
        oval(Color.White.copy(alpha = 0.34f * f), x(phase - k * TRAIL_GAP), 0.45f + 0.5f * f)
    }
    val cx = x(phase)
    val pulse = 0.5f + 0.5f * sin(tau * 2f * phase * speed)
    oval(Color.White.copy(alpha = 0.12f + 0.1f * pulse), cx, 2.1f + 0.25f * pulse)
    oval(Color.White.copy(alpha = 0.3f), cx, 1.45f)
    oval(Color.White.copy(alpha = 0.93f), cx, 1f)
}

/** Android animations on and no battery saver; follows the battery saver while shown. */
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
        onDispose { if (registered) runCatching { app.unregisterReceiver(receiver) } }
    }
    return animations && !saving
}

private fun powerSaving(context: Context) = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
