// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.FlatStyle
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Three dots in [color] that rise and brighten one after another, like a wave ([CYCLE_MS] per wave,
 * [STAGGER_MS] between the dots). Shown in the strip while a typed AI request runs. With animations
 * turned off on the device ([FlatStyle.animationsEnabled]) the dots stand still; the animation only
 * runs while the view is attached and stops when it is detached.
 */
class LoadingDotsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    constructor(context: Context, color: Int) : this(context) { this.color = color }

    /** Colour of the dots, usually the keyboard's accent. */
    var color: Int = Color.GRAY
        set(value) { field = value; invalidate() }
    private val density = context.resources.displayMetrics.density
    private val radius = 2.6f * density
    private val gap = 4.4f * density
    private val lift = 2.6f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var phase = 0f
    private var animator: ValueAnimator? = null

    /** True while the wave runs; false when detached or with animations off. */
    val isAnimating: Boolean get() = animator?.isRunning == true

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // the chip's label says what happens
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (DOTS * 2 * radius + (DOTS - 1) * gap).toInt() + paddingLeft + paddingRight + 1
        val height = (2 * radius + 2 * lift).toInt() + paddingTop + paddingBottom + 1
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start() {
        if (animator != null || !FlatStyle.animationsEnabled(context)) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CYCLE_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
        phase = 0f
    }

    /** How far dot [index] is up in its wave at [phase] (0..1 of a cycle): 0 resting, 1 at the top. */
    internal fun wave(index: Int, phase: Float): Float {
        if (animator == null) return STATIC
        val local = ((phase - index * STAGGER_MS.toFloat() / CYCLE_MS) % 1f + 1f) % 1f
        // the dot moves during the first half of its cycle and rests during the second
        return if (local < 0.5f) max(0f, sin(local * 2f * PI.toFloat())) else 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerY = paddingTop + lift + radius
        var x = paddingLeft + radius
        for (index in 0 until DOTS) {
            val up = wave(index, phase)
            paint.color = ColorUtils.setAlphaComponent(color, (255 * (0.4f + 0.6f * up)).toInt())
            // resting dots sit low and rise by up to twice [lift]; standing dots stay in the middle
            val y = if (animator == null) centerY else centerY + lift - up * 2f * lift
            canvas.drawCircle(x, y, radius, paint)
            x += 2 * radius + gap
        }
    }

    companion object {
        const val DOTS = 3
        const val CYCLE_MS = 1200L
        const val STAGGER_MS = 150L
        /** Dots without animation: evenly bright and level. */
        private const val STATIC = 0.6f
    }
}
