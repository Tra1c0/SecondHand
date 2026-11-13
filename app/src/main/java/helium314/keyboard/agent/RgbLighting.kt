// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbRun
import dev.agentkeyboard.core.RgbFramePacer
import dev.agentkeyboard.core.RgbSettings
import dev.agentkeyboard.core.RgbTapReaction
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.utils.prefs
import java.lang.ref.WeakReference

/**
 * RGB lighting of the keyboard (stage 26): settings, animation clock and frame loop. The maths
 * lives in agent-core ([RgbEngine]); drawing per key in [RgbKeyPainter], the strip in [RgbStrip].
 *
 * Battery rules ([RgbRun.mode]): frames only while the keyboard view is shown, at most at the
 * configured rate ([RgbFramePacer]); one still frame when Android animations are off or (by
 * default) in power saving mode; reactive-only light only draws while an effect fades.
 * Everything runs on the UI thread. Nothing is logged.
 */
object RgbLighting {
    /** One preference with the whole configuration in [RgbSettings.encode] form. */
    const val PREF = "agent_rgb_lighting"
    /** How often power saving and the animator scale are read again while the light runs. */
    private const val SYSTEM_CHECK_MS = 2000L

    private var encoded: String? = null
    var settings: RgbSettings = RgbSettings.DEFAULT
        private set
    var engine = RgbEngine(settings)
        private set

    /** Animation time used for drawing; frozen in [RgbRun.Mode.STATIC]. */
    var drawTimeMs = 0L
        private set
    var mode = RgbRun.Mode.OFF
        private set

    private var keyboardView: WeakReference<KeyboardView>? = null
    private var strip: WeakReference<View>? = null
    /** The strip last reported by [onStripChanged] (tests). */
    internal val stripView: View? get() = strip?.get()
    private var pacer = RgbFramePacer(settings.fps)
    private var scheduled = false
    private var powerSave = false
    private var animationsOn = true
    private var lastSystemCheck = Long.MIN_VALUE
    private val frameCallback = Choreographer.FrameCallback { onFrame(it) }

    fun read(prefs: SharedPreferences): RgbSettings = RgbSettings.parse(prefs.getString(PREF, null))

    fun write(prefs: SharedPreferences, value: RgbSettings) {
        if (value == RgbSettings.DEFAULT) prefs.edit().remove(PREF).apply()
        else prefs.edit().putString(PREF, value.encode()).apply()
    }

    /** Re-reads the preference; a changed configuration starts with a new engine. */
    fun load(context: Context) {
        val value = context.prefs().getString(PREF, null)
        if (value == encoded) return
        encoded = value
        settings = RgbSettings.parse(value)
        engine = RgbEngine(settings)
        pacer = RgbFramePacer(settings.fps)
    }

    /**
     * Called by [helium314.keyboard.keyboard.MainKeyboardView.setKeyboard]: returns the painter
     * the view should draw with (reusing [current]), or null when nothing on the keyboard is lit.
     */
    @JvmStatic
    fun painterFor(view: KeyboardView, current: RgbKeyPainter?): RgbKeyPainter? {
        load(view.context)
        keyboardView = WeakReference(view)
        // switched off: no system queries, no frames, upstream drawing
        if (settings.enabled) checkSystem(view.context, force = true)
        update()
        if (!settings.lightsKeys()) return null
        return current ?: RgbKeyPainter()
    }

    /** Key pressed on the main keyboard: starts tap effects (stage 26b). */
    @JvmStatic
    fun onKeyPressed(view: KeyboardView, key: Key) {
        if (mode == RgbRun.Mode.OFF || mode == RgbRun.Mode.STATIC) return
        if (settings.tapReaction == RgbTapReaction.NONE && !settings.reactiveOnly) return
        val width = view.width.takeIf { it > 0 } ?: return
        val height = view.height.takeIf { it > 0 } ?: return
        val now = SystemClock.uptimeMillis()
        if (mode != RgbRun.Mode.RUNNING) {
            engine.resetClock()
            drawTimeMs = now
        }
        engine.tap((key.x + key.width / 2f + view.paddingLeft) / width, (key.y + key.height / 2f + view.paddingTop) / height,
            RgbKeyPainter.keyId(key), now)
        update()
    }

    /** Visibility of the keyboard view or its window changed. */
    @JvmStatic
    fun onVisibilityChanged() = update()

    /** The keyboard window was hidden: no more frames until it is shown again. */
    @JvmStatic
    fun onHideWindow() {
        engine.clearTaps()
        stopFrames()
        mode = if (mode == RgbRun.Mode.OFF) RgbRun.Mode.OFF else RgbRun.Mode.PAUSED
    }

    /**
     * The suggestion strip was (re)built or got new suggestions: remember it and colour its text
     * and icons when a strip area is lit (stage 26c). Without frames (still light) this is the
     * only time new suggestions get their colour.
     */
    @JvmStatic
    fun onStripChanged(view: View) {
        strip = WeakReference(view)
        if (mode != RgbRun.Mode.OFF && settings.lightsStrip()) RgbStrip.paint(view, true)
    }

    private fun isVisible(view: View?) = view != null && view.isAttachedToWindow && view.isShown &&
        view.windowVisibility == View.VISIBLE && view.width > 0

    private fun checkSystem(context: Context, force: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        if (!force && lastSystemCheck != Long.MIN_VALUE && now - lastSystemCheck < SYSTEM_CHECK_MS) return
        lastSystemCheck = now
        powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        animationsOn = runCatching { FlatStyle.animationsEnabled(context) }.getOrDefault(true)
    }

    /** Decides the mode and starts or stops frames; redraws once when the picture changes without frames. */
    private fun update() {
        val view = keyboardView?.get()
        val now = SystemClock.uptimeMillis()
        val effect = engine.hasActiveEffect(now)
        val newMode = RgbRun.mode(settings, isVisible(view), powerSave, animationsOn, effect)
        val old = mode
        mode = newMode
        when (newMode) {
            RgbRun.Mode.OFF -> {
                stopFrames()
                if (old != RgbRun.Mode.OFF) strip?.get()?.let { RgbStrip.paint(it, false) }
            }
            RgbRun.Mode.STATIC -> {
                stopFrames()
                engine.clearTaps()
                if (old != RgbRun.Mode.STATIC) redraw(view)
            }
            RgbRun.Mode.PAUSED -> {
                stopFrames()
                // an effect has just faded out: draw its end once
                if (old == RgbRun.Mode.RUNNING) {
                    drawTimeMs = now
                    redraw(view)
                }
            }
            RgbRun.Mode.RUNNING -> {
                if (old != RgbRun.Mode.RUNNING) {
                    engine.resetClock()
                    pacer.reset()
                }
                scheduleFrame()
            }
        }
    }

    private fun scheduleFrame() {
        if (scheduled) return
        scheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrames() {
        if (!scheduled) return
        scheduled = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun onFrame(frameNanos: Long) {
        scheduled = false
        val view = keyboardView?.get()
        if (view != null) checkSystem(view.context)
        update()
        if (mode != RgbRun.Mode.RUNNING || !pacer.shouldRender(frameNanos)) return
        drawTimeMs = SystemClock.uptimeMillis()
        engine.advance(drawTimeMs)
        redraw(view)
    }

    private fun redraw(view: KeyboardView?) {
        if (settings.lightsKeys()) view?.invalidateAllKeys()
        if (settings.lightsStrip()) strip?.get()?.let { RgbStrip.paint(it, true) }
    }
}
