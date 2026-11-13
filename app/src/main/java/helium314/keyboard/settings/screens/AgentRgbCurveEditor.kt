// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agentkeyboard.core.RgbCurve
import helium314.keyboard.latin.R
import kotlin.math.hypot

/** Inner margin of the curve area: room for the handles at the edges. */
internal val CURVE_PAD = 22.dp
internal val CURVE_HEIGHT = 200.dp

/**
 * Own curve of the RGB lighting (stage 29d), drawn like the owner's sketch: two axes, the curve and
 * its control points as circles with a cross. Drag a point to move it (the ends only up and down),
 * tap an empty spot to add a point there, tap or long press a point to select it, drag it out of the
 * area to remove it (never the ends, never below two points); a long press never removes, so a
 * finger resting on a point loses nothing. [onChange] gets every intermediate curve with
 * commit = false while dragging and the final one with commit = true. [selected] is the point the
 * "remove" button acts on; TalkBack offers moving it in steps of [A11Y_STEP] and removing it as
 * custom actions on the editor.
 */
@Composable
fun RgbCurveEditor(
    curve: RgbCurve,
    selected: Int,
    onSelect: (Int) -> Unit,
    onChange: (RgbCurve, Boolean) -> Unit,
    axisColor: Color,
    curveColor: Color,
    accent: Color,
    removeColor: Color,
    description: String,
    xLabel: String,
    yLabel: String,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(curve)
    val change by rememberUpdatedState(onChange)
    val select by rememberUpdatedState(onSelect)
    var dragging by remember { mutableIntStateOf(-1) }
    var outside by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val upLabel = stringResource(R.string.agent_rgb_curve_up)
    val downLabel = stringResource(R.string.agent_rgb_curve_down)
    val leftLabel = stringResource(R.string.agent_rgb_curve_left)
    val rightLabel = stringResource(R.string.agent_rgb_curve_right)
    val removeLabel = stringResource(R.string.agent_rgb_curve_remove)
    val selectedText = stringResource(R.string.agent_rgb_curve_selected, selected + 1)
    /** Moves the selected point by ([dx], [dy]) and saves (TalkBack). */
    fun nudge(dx: Float, dy: Float): Boolean {
        val p = current.points.getOrNull(selected) ?: return false
        change(current.move(selected, p.x + dx, p.y + dy), true)
        return true
    }
    Canvas(
        modifier.fillMaxWidth().height(CURVE_HEIGHT).testTag("agent_rgb_curve")
            .semantics {
                contentDescription = description
                val points = curve.points.joinToString(", ") { "${(it.x * 100).toInt()} %: ${(it.y * 100).toInt()} %" }
                stateDescription = if (selected in curve.points.indices) "$selectedText. $points" else points
                if (selected in curve.points.indices) customActions = listOfNotNull(
                    CustomAccessibilityAction(upLabel) { nudge(0f, A11Y_STEP) },
                    CustomAccessibilityAction(downLabel) { nudge(0f, -A11Y_STEP) },
                    // the ends stay at the left and right edge
                    CustomAccessibilityAction(leftLabel) { nudge(-A11Y_STEP, 0f) }.takeIf { selected > 0 && selected < curve.points.size - 1 },
                    CustomAccessibilityAction(rightLabel) { nudge(A11Y_STEP, 0f) }.takeIf { selected > 0 && selected < curve.points.size - 1 },
                    CustomAccessibilityAction(removeLabel) {
                        change(current.remove(selected), true)
                        select(-1)
                        true
                    }.takeIf { curve.canRemove(selected) },
                )
            }
            .pointerInput(Unit) {
                val pad = CURVE_PAD.toPx()
                val hitRadius = 26.dp.toPx()
                val removeDistance = 36.dp.toPx()
                fun toX(px: Float) = (px - pad) / (size.width - 2 * pad)
                fun toY(py: Float) = 1f - (py - pad) / (size.height - 2 * pad)
                fun hit(at: Offset): Int {
                    var best = -1
                    var bestD = hitRadius
                    current.points.forEachIndexed { i, p ->
                        val d = hypot(at.x - (pad + p.x * (size.width - 2 * pad)), at.y - (pad + (1 - p.y) * (size.height - 2 * pad)))
                        if (d <= bestD) { best = i; bestD = d }
                    }
                    return best
                }
                fun isOutside(at: Offset) = at.y < -removeDistance || at.y > size.height + removeDistance ||
                    at.x < -removeDistance || at.x > size.width + removeDistance
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = down.position
                    val index = hit(start)
                    if (index >= 0) down.consume()
                    // wait: lifted (tap), moved past the slop (drag), or held (long press: select only)
                    val first = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var result = 0
                        while (result == 0) {
                            val change = awaitPointerEvent().changes.first()
                            // a gesture on a point belongs to the editor, never to the page scroll
                            if (index >= 0) change.consume()
                            when {
                                !change.pressed -> result = TAP
                                (change.position - start).getDistance() > viewConfiguration.touchSlop -> result = DRAG
                            }
                        }
                        result
                    }
                    when {
                        first == null -> { // long press: selects the point, removing is the button's or dragging out
                            if (index >= 0) select(index)
                            do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
                        }
                        first == TAP && index >= 0 -> select(index)
                        first == TAP -> {
                            val (added, at) = current.add(toX(start.x), toY(start.y))
                            if (at >= 0) {
                                change(added, true)
                                select(at)
                            }
                        }
                        index >= 0 -> { // drag a point
                            dragging = index
                            select(index)
                            var moved = current
                            while (true) {
                                val event = awaitPointerEvent()
                                val c = event.changes.first()
                                if (!c.pressed) break
                                c.consume()
                                outside = isOutside(c.position) && moved.canRemove(index)
                                moved = moved.move(index, toX(c.position.x), toY(c.position.y))
                                change(moved, false)
                            }
                            if (outside) {
                                change(moved.remove(index), true)
                                select(-1)
                            } else change(moved, true)
                            dragging = -1
                            outside = false
                        }
                        // a drag on empty space is left to the page (scrolling)
                    }
                }
            }
    ) {
        val pad = CURVE_PAD.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        if (w <= 0f || h <= 0f) return@Canvas
        fun at(x: Float, y: Float) = Offset(pad + x * w, pad + (1 - y) * h)
        // light grid, then the two axes of the sketch
        val grid = axisColor.copy(alpha = 0.25f)
        for (i in 1..3) {
            drawLine(grid, at(i / 4f, 0f), at(i / 4f, 1f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            drawLine(grid, at(0f, i / 4f), at(1f, i / 4f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        drawLine(axisColor, at(0f, 0f), at(0f, 1.04f), 2.dp.toPx(), StrokeCap.Round)
        drawLine(axisColor, at(0f, 0f), at(1.04f, 0f), 2.dp.toPx(), StrokeCap.Round)
        val style = TextStyle(color = axisColor, fontSize = 11.sp)
        val xText = measurer.measure(xLabel, style)
        drawText(xText, topLeft = Offset(pad + w - xText.size.width, pad + h + 4.dp.toPx()))
        drawText(measurer.measure(yLabel, style), topLeft = Offset(pad + 6.dp.toPx(), 2.dp.toPx()))
        // the curve
        val path = Path()
        val samples = 160
        for (i in 0..samples) {
            val x = i / samples.toFloat()
            val y = if (i == samples) curve.points.last().y else curve.value(x)
            val p = at(x, y)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, curveColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        // control points: circles with a cross
        val r = 10.dp.toPx()
        val cross = 6.dp.toPx()
        curve.points.forEachIndexed { i, p ->
            val c = at(p.x, p.y)
            val color = when {
                i == dragging && outside -> removeColor
                i == selected || i == dragging -> accent
                else -> curveColor
            }
            if (i == selected || i == dragging) drawCircle(color.copy(alpha = 0.25f), r * 1.6f, c)
            drawCircle(color, r, c, style = Stroke(2.dp.toPx()))
            drawLine(color, Offset(c.x - cross, c.y), Offset(c.x + cross, c.y), 2.dp.toPx())
            drawLine(color, Offset(c.x, c.y - cross), Offset(c.x, c.y + cross), 2.dp.toPx())
        }
    }
}

/** Screen position of point [index] of [curve] inside an editor of [widthPx] x [heightPx] (tests). */
internal fun curvePointOffset(curve: RgbCurve, index: Int, widthPx: Float, heightPx: Float, padPx: Float): Offset {
    val p = curve.points[index]
    return Offset(padPx + p.x * (widthPx - 2 * padPx), padPx + (1 - p.y) * (heightPx - 2 * padPx))
}

/** Step of the TalkBack actions: a twentieth of the area. */
internal const val A11Y_STEP = 0.05f

private const val TAP = 1
private const val DRAG = 2
