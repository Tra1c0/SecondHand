// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.PowerManager
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import dev.agentkeyboard.core.RgbEngine
import dev.agentkeyboard.core.RgbEngines
import dev.agentkeyboard.core.RgbTarget
import dev.agentkeyboard.core.RgbRun
import dev.agentkeyboard.core.RgbFramePacer
import dev.agentkeyboard.core.RgbFrameStats
import dev.agentkeyboard.core.RgbSettings
import helium314.keyboard.keyboard.FlatStyle
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.prefs
import java.lang.ref.WeakReference

/**
 * RGB lighting of the keyboard (stage 26): settings, animation clock and frame loop. The maths
 * lives in agent-core ([RgbEngine], one per look: [RgbEngines]); drawing per key in [RgbKeyPainter],
 * the strip in [RgbStrip], the light as a picture in [RgbTexture].
 *
 * Only the main keyboard view (R.id.keyboard_view in main_keyboard_frame) is lit; the bottom
 * rows of the emoji and clipboard views are MainKeyboardViews too, but never get a painter.
 *
 * Battery rules ([RgbRun.mode]): frames only while the keyboard view is shown, at most at the
 * configured rate ([RgbFramePacer]; the next frame is only requested when it is due); one still
 * frame when Android animations are off or (by default) in power saving mode; reactive-only light
 * only draws while an effect fades. Power saving changes arrive by broadcast, the animator scale
 * is read again whenever the keyboard is shown. The light grids and tiles are computed on a
 * background thread ([RgbWork]); the UI thread only advances the clock, takes finished grids and
 * draws. [frameWork] tells how long that takes per frame. Nothing is logged.
 */
object RgbLighting {
    /** One preference with the whole configuration in [RgbSettings.encode] form. */
    const val PREF = "agent_rgb_lighting"
    /** How often power saving and the animator scale are read again while the light runs. */
    private const val SYSTEM_CHECK_MS = 2000L

    private var encoded: String? = null
    /** The own patterns as last read (stage 34): a changed pattern also makes new engines. */
    private var encodedPatterns: String? = null
    var settings: RgbSettings = RgbSettings.DEFAULT
        private set
    /** One engine per distinct look (stage 29), all on one clock. */
    var engines = RgbEngines(settings)
        private set
    /** Engine of the key backgrounds (the shared look unless the elements have own looks). */
    val engine: RgbEngine get() = engines.forTarget(RgbTarget.KEYS)
    fun engineFor(target: RgbTarget): RgbEngine = engines.forTarget(target)

    /** Animation time used for drawing; frozen in [RgbRun.Mode.STATIC]. */
    var drawTimeMs = 0L
        private set
    var mode = RgbRun.Mode.OFF
        private set

    private var keyboardView: WeakReference<KeyboardView>? = null
    /** The one keyboard view that is lit (tests). */
    internal val controlledView: KeyboardView? get() = keyboardView?.get()
    private var strip: WeakReference<View>? = null
    /** The strip last reported by [onStripChanged] (tests). */
    internal val stripView: View? get() = strip?.get()
    private var pacer = RgbFramePacer(settings.fps)
    private var scheduled = false
    private var powerSave = false
    private var animationsOn = true
    private var lastSystemCheck = Long.MIN_VALUE
    /** True while strip views may carry RGB colours, so restoring is only done when needed. */
    private var stripLit = false
    private var inFrame = false
    private val frameCallback = Choreographer.FrameCallback { onFrame(it) }

    /** Application context the power-save receiver is registered with, null while unregistered. */
    private var receiverContext: WeakReference<Context>? = null
    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val view = keyboardView?.get() ?: return
            checkSystem(view.context, force = true)
            update()
        }
    }
    /** Whether the power-save receiver is registered (tests). */
    internal val listensForPowerSave: Boolean get() = receiverContext != null

    /** The setting with its own patterns (stage 34) taken from [RgbOwnPatterns] by id. */
    fun read(prefs: SharedPreferences): RgbSettings =
        RgbSettings.parse(prefs.getString(PREF, null)).let { s -> if (s.ownIds().isEmpty()) s else s.resolve(RgbOwnPatterns.library(prefs)) }

    fun write(prefs: SharedPreferences, value: RgbSettings) {
        if (value == RgbSettings.DEFAULT) prefs.edit().remove(PREF).apply()
        else prefs.edit().putString(PREF, value.encode()).apply()
    }

    /** Re-reads the preference; a changed configuration starts with a new engine. */
    fun load(context: Context) {
        val prefs = context.prefs()
        var value = prefs.getString(PREF, null)
        var patterns = prefs.getString(RgbOwnPatterns.PREF, null)
        if (value == encoded && patterns == encodedPatterns) return
        if (patterns != encodedPatterns && patterns != null) {
            // own patterns of the first build of stage 34 get random ids once (the setting follows)
            RgbOwnPatterns.migrate(context)
            value = prefs.getString(PREF, null)
            patterns = prefs.getString(RgbOwnPatterns.PREF, null)
        }
        encoded = value
        encodedPatterns = patterns
        settings = read(prefs)
        engines = RgbEngines(settings)
        pacer = RgbFramePacer(settings.fps)
        // tiles are made in the background ahead of the first frame; switched off they are dropped
        if (settings.enabled) {
            RgbWork.addListener(onTilesReady)
            RgbTiles.request(engines.all.mapNotNull { it.tilePattern })
        } else RgbTiles.clear()
        // strip areas deselected while the keys stay lit: give the strip its own colours back
        if (!settings.lightsStrip()) restoreStrip()
    }

    /**
     * A tile or a light grid was made in the background: draw it now when no frames run (still
     * light, an effect that just faded, the first picture); running frames pick it up anyway.
     */
    private val onTilesReady: () -> Unit = {
        val view = keyboardView?.get()
        if (mode != RgbRun.Mode.OFF && mode != RgbRun.Mode.RUNNING && isVisible(view) && settings.lightsKeys()) view?.invalidateAllKeys()
    }

    /** Lighting work of the keyboard per frame: UI thread and background grids (build-14 lag). */
    val frameStats = RgbFrameStats()

    /** Average and maximum lighting work per frame over the last seconds, for the Diagnose page. */
    @JvmStatic
    fun frameWork(): RgbFrameStats.Summary = frameStats.summary(SystemClock.uptimeMillis())

    /** The same for the last seconds the keyboard was drawn with light (it may be hidden now). */
    @JvmStatic
    fun lastFrameWork(): RgbFrameStats.Summary = frameStats.summaryOfLastActivity()

    /** Low memory (LatinIME.onTrimMemory): drops the tiles; the next frame asks for them again. */
    @JvmStatic
    fun onTrimMemory() = RgbTiles.clear()

    /** True for the one view that may be lit: the keyboard of main_keyboard_frame. */
    @JvmStatic
    fun isMainKeyboard(view: View) = view.id == R.id.keyboard_view

    /**
     * Called by [helium314.keyboard.keyboard.MainKeyboardView.setKeyboard]: returns the painter
     * the view should draw with (reusing [current]), or null when nothing on the keyboard is lit
     * or [view] is not the main keyboard (emoji and clipboard bottom rows).
     */
    @JvmStatic
    fun painterFor(view: KeyboardView, current: RgbKeyPainter?): RgbKeyPainter? {
        if (!isMainKeyboard(view)) return null
        load(view.context)
        keyboardView = WeakReference(view)
        forgetTapsOfAnotherField()
        // switched off: no system queries, no frames, upstream drawing
        if (settings.enabled) checkSystem(view.context, force = true)
        update()
        if (!settings.lightsKeys()) return null
        return current ?: RgbKeyPainter()
    }

    /** Key pressed on the main keyboard: starts tap effects (stage 26b). */
    @JvmStatic
    fun onKeyPressed(view: KeyboardView, key: Key) {
        if (view !== keyboardView?.get()) return
        if (mode == RgbRun.Mode.OFF || mode == RgbRun.Mode.STATIC) return
        if (!settings.reactsToTaps()) return
        forgetTapsOfAnotherField()
        // password fields and incognito: the light never shows which key was typed (ring, spot, glow)
        if (privateField(view)) return
        val width = view.width.takeIf { it > 0 } ?: return
        val height = view.height.takeIf { it > 0 } ?: return
        val now = SystemClock.uptimeMillis()
        if (mode != RgbRun.Mode.RUNNING) {
            engines.resetClock()
            drawTimeMs = now
        }
        engines.tap((key.x + key.width / 2f + view.paddingLeft) / width, (key.y + key.height / 2f + view.paddingTop) / height,
            RgbKeyPainter.keyId(key), now)
        update()
    }

    /** The settings of the field the taps belong to: new ones (another field, onStartInputView) drop the old taps. */
    private var tapField = WeakReference<SettingsValues>(null)

    /**
     * Drops the taps of the previous field. LatinIME loads new [SettingsValues] for every other
     * field before it sets the keyboard, so a new instance means a new field (or new settings):
     * a spot or ring of the last field never shows in the next one.
     */
    private fun forgetTapsOfAnotherField() {
        val values: SettingsValues? = Settings.getValues()
        if (values === tapField.get()) return
        tapField = WeakReference(values)
        engines.clearTaps()
    }

    /** True in a password field or in incognito (also fields that ask for no learning). */
    private fun privateField(view: KeyboardView): Boolean {
        if (view.keyboard?.mId?.isPasswordInput == true) return true
        val values: SettingsValues = Settings.getValues() ?: return false
        return values.mIncognitoModeEnabled || values.mInputAttributes.mIsPasswordField ||
            InputTypeUtils.isAnyPasswordInputType(values.mInputAttributes.mInputType)
    }

    /**
     * Visibility, window visibility, attachment or size of the keyboard view changed (the first
     * layout gives the view its size: only then can the light start). Becoming visible also reads
     * power saving and the animator scale again.
     */
    @JvmStatic
    fun onVisibilityChanged(view: View) {
        if (view !== keyboardView?.get()) return
        if (settings.enabled && isVisible(view)) checkSystem(view.context, force = true)
        update()
    }

    /**
     * The keyboard view is being detached. It still counts as attached during
     * View.onDetachedFromWindow, so this stops frames and the receiver explicitly.
     */
    @JvmStatic
    fun onDetached(view: View) {
        if (view !== keyboardView?.get()) return
        stopFrames()
        engines.clearTaps()
        if (mode != RgbRun.Mode.OFF) mode = RgbRun.Mode.PAUSED
        updateReceiver(null)
    }

    /** The keyboard window was hidden: no more frames until it is shown again. */
    @JvmStatic
    fun onHideWindow(view: View) {
        if (view !== keyboardView?.get()) return
        engines.clearTaps()
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
        RgbStrip.invalidate(view)
        if (mode != RgbRun.Mode.OFF && settings.lightsStrip()) paintStrip(view) else restoreStrip()
    }

    private fun paintStrip(view: View) {
        stripLit = true
        RgbStrip.paint(view, true)
    }

    /** Gives the strip its own colours back if it may still carry light. */
    private fun restoreStrip() {
        if (!stripLit) return
        stripLit = false
        strip?.get()?.let { RgbStrip.paint(it, false) }
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
        val effect = engines.hasActiveEffect(now)
        val newMode = RgbRun.mode(settings, isVisible(view), powerSave, animationsOn, effect)
        val old = mode
        mode = newMode
        updateReceiver(view)
        when (newMode) {
            RgbRun.Mode.OFF -> {
                stopFrames()
                restoreStrip()
            }
            RgbRun.Mode.STATIC -> {
                stopFrames()
                engines.clearTaps()
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
                    engines.resetClock()
                    pacer.reset()
                }
                // inside a frame the next one is asked for after the pacer has seen this one
                if (!inFrame) scheduleFrame()
            }
        }
    }

    /**
     * Listens for power saving changes while the light is on and the keyboard view is attached;
     * unregisters as soon as either ends (application context, so no view or service leaks).
     */
    private fun updateReceiver(view: View?) {
        val wanted = settings.enabled && view != null && view.isAttachedToWindow
        val registered = receiverContext?.get()
        if (wanted && registered == null) {
            val app = view?.context?.applicationContext ?: return
            // a protected system broadcast: no export flag needed
            runCatching { app.registerReceiver(powerSaveReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)) }
                .onSuccess { receiverContext = WeakReference(app) }
        } else if (!wanted && registered != null) {
            receiverContext = null
            runCatching { registered.unregisterReceiver(powerSaveReceiver) }
        }
    }

    /** Asks for the next frame only when the pacer will accept it, not on every vsync. */
    private fun scheduleFrame() {
        if (scheduled) return
        scheduled = true
        val delayMs = pacer.nanosUntilDue(System.nanoTime()) / 1_000_000
        if (delayMs > 0) Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, delayMs)
        else Choreographer.getInstance().postFrameCallback(frameCallback)
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
        inFrame = true
        try { update() } finally { inFrame = false }
        if (mode != RgbRun.Mode.RUNNING) return
        if (pacer.shouldRender(frameNanos)) {
            drawTimeMs = SystemClock.uptimeMillis()
            engines.advance(drawTimeMs)
            redraw(view)
        }
        scheduleFrame()
    }

    private fun redraw(view: KeyboardView?) {
        if (settings.lightsKeys()) view?.invalidateAllKeys()
        if (settings.lightsStrip()) strip?.get()?.let { paintStrip(it) } else restoreStrip()
    }
}
