// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.util.fastAny
import helium314.keyboard.agent.SliderDrag
import helium314.keyboard.agent.SliderFeel
import helium314.keyboard.agent.SliderHaptics

/**
 * Stage 28: the slider of every settings page. A Material 3 slider whose finger drags tick and
 * snap to detents ([SliderFeel], [SliderDrag]). Only values from a finger are snapped: TalkBack and
 * keys set exactly the value they ask for. [defaultValue] is a detent (when it lies in the range),
 * [integer] keeps ticks and round values on whole numbers, [roundStep] overrides the round values.
 * Reusable for any page; the RGB page can switch to it later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    defaultValue: Float? = null,
    integer: Boolean = false,
    roundStep: Float? = null,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    sink: SliderFeel.Sink = rememberSliderSink(),
) {
    val feel = remember(valueRange, steps, defaultValue, integer, roundStep) { SliderFeel(valueRange, steps, defaultValue, integer, roundStep) }
    val drag = remember(feel, sink) { SliderDrag(feel, sink) }
    val current = rememberUpdatedState(value)
    val change = rememberUpdatedState(onValueChange)
    val finger = remember { Finger() }
    val state = remember(steps, valueRange) { SliderState(value, steps, null, valueRange) }
    state.onValueChangeFinished = onValueChangeFinished
    // a drag reports its last moves after the finger is up (isDragging until then); a tap reports
    // before the finger counts as up (Final pass)
    state.onValueChange = { raw -> change.value(if (finger.down || state.isDragging) drag.next(raw) else raw) }
    state.value = value
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        state = state,
        modifier = modifier.pointerInput(drag) { watchFinger(finger) { drag.begin(current.value) } },
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

/** The haptic sink of the sliders on this screen. */
@Composable
fun rememberSliderSink(): SliderFeel.Sink {
    val context = LocalContext.current
    return remember(context) { SliderHaptics.sink(context) }
}

private class Finger { var down = false }

/** Sees every touch first (Initial pass) without taking it; the finger counts as up only after the slider handled the release. */
private suspend fun PointerInputScope.watchFinger(finger: Finger, onDown: () -> Unit) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    finger.down = true
    try {
        onDown()
        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
        } while (event.changes.fastAny { it.pressed })
    } finally {
        finger.down = false
    }
}

/**
 * The same feel for a slider that draws itself (the color picker's alpha and brightness bars, whose
 * library has no hook): [content] gets a modifier for that element which takes over its touches,
 * maps x over its width to [SliderFeel.range] and hands the value to show to [onValue]. The modifier
 * also gives the element slider semantics, so TalkBack can read and set it (exactly, no snapping).
 */
@Composable
fun AgentSliderInput(
    feel: SliderFeel,
    value: () -> Float,
    onValue: (Float) -> Unit,
    label: String,
    describe: (Float) -> String,
    sink: SliderFeel.Sink = rememberSliderSink(),
    content: @Composable (Modifier) -> Unit,
) {
    val drag = remember(feel, sink) { SliderDrag(feel, sink) }
    val latestValue = rememberUpdatedState(value)
    val latestOnValue = rememberUpdatedState(onValue)
    val range = feel.range
    val shown = value()
    val input = Modifier.semantics {
        contentDescription = label
        stateDescription = describe(shown)
        progressBarRangeInfo = ProgressBarRangeInfo(shown.coerceIn(range), range)
        setProgress { target ->
            latestOnValue.value(target.coerceIn(range))
            true
        }
    }.pointerInput(drag) {
        awaitEachGesture {
            fun at(x: Float): Float {
                val fraction = if (size.width > 0) (x / size.width).coerceIn(0f, 1f) else 0f
                return range.start + fraction * feel.span
            }
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            down.consume()
            drag.begin(latestValue.value())
            latestOnValue.value(drag.next(at(down.position.x)))
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val moved = change.positionChanged()
                change.consume()
                if (!change.pressed) break
                if (moved) latestOnValue.value(drag.next(at(change.position.x)))
            }
        }
    }
    content(input)
}
