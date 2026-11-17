// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.updates

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.FieldReport
import dev.agentkeyboard.core.UpdateOnOpenPolicy
import helium314.keyboard.agent.LastField
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stage 56 "Neue Builds sofort melden": when the keyboard is shown in a field, ask the public
 * release list once in the background ([Updates.check], with the stored ETag), at most every
 * 15 minutes since the last attempt and longer after failures ([UpdateOnOpenPolicy]). Sends nothing
 * typed: the same single GET as "Jetzt prüfen", no token. A newer build found here shows the
 * update chip ([UpdateChip]) on the empty neutral strip of the same field right away.
 *
 * Called from [LastField.capture] (the stage 55 line in `LatinIME.onStartInputViewInternal`, after
 * the settings for the field were loaded). Never throws; on the main thread it only reads settings,
 * flags and the network state, the request runs on [executor]. Quietly skipped with the switch off
 * ([UpdateSettings.onOpen]), locked (settings and live), incognito, in password fields, with Data
 * Saver restricting the app, without a validated network, and while a check runs.
 */
object UpdateOnOpen {
    /** Why [onStartInputView] started nothing (null = started). For tests; never shown or logged. */
    enum class Skip { NO_FIELD, LOCKED, PASSWORD, INCOGNITO, SWITCH_OFF, THROTTLED, DATA_SAVER, OFFLINE, NO_VERSION, RUNNING, FAILED }

    /** The field a check was started for, to refresh the strip only while the keyboard is still in it. */
    data class FieldKey(val packageName: String?, val fieldId: Int, val inputType: Int) {
        companion object {
            fun of(editorInfo: EditorInfo?): FieldKey? = editorInfo?.let { FieldKey(it.packageName, it.fieldId, it.inputType) }
        }
    }

    /** Replaced in tests: wall clock, persisted across restarts (the 15 minutes survive a restart). */
    @JvmStatic var clock: () -> Long = { System.currentTimeMillis() }
    /** Replaced in tests: a network with internet access that Android validated. */
    @JvmStatic var online: (Context) -> Boolean = ::networkUsable
    /** Replaced in tests: Data Saver is on and restricts this app's background data. */
    @JvmStatic var dataSaver: (Context) -> Boolean = ::dataSaverRestricts
    /** Replaced in tests: the HTTP client (the update check's own, never the AI client). */
    @JvmStatic var client: (Context) -> GitHubUpdateClient = { GitHubUpdateClient(UpdateHttp.calls(it)) }
    /** Replaced in tests: where the request runs, never the main thread. */
    @JvmStatic var executor: Executor = Executor { worker.execute(it) }

    private val worker by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "agent-update-on-open").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }
    }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val running = AtomicBoolean(false)

    /** From [LastField.capture] for every field the keyboard is shown in. Never throws, returns at once. */
    @JvmStatic fun onStartInputView(context: Context, editorInfo: EditorInfo?, settings: SettingsValues?): Skip? =
        helium314.keyboard.agent.TypingPerf.measure(helium314.keyboard.agent.TypingPerf.Hook.FIELD_START) {
            runCatching { start(context, editorInfo, settings) }.getOrDefault(Skip.FAILED)
        }

    @Volatile private var unlockedOnce = false

    /** Whether credential-protected storage is readable; true stays true for the process (Direct Boot only ends). */
    private fun userUnlocked(context: Context): Boolean {
        if (unlockedOnce) return true
        val unlocked = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N || runCatching {
            (context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager)?.isUserUnlocked == true
        }.getOrDefault(false)
        if (unlocked) unlockedOnce = true
        return unlocked
    }

    private fun start(context: Context, editorInfo: EditorInfo?, settings: SettingsValues?): Skip? {
        val values = settings ?: return Skip.NO_FIELD
        if (editorInfo == null) return Skip.NO_FIELD
        if (values.mIsLocked) return Skip.LOCKED
        if (isPassword(values, editorInfo)) return Skip.PASSWORD
        if (isIncognito(values, editorInfo)) return Skip.INCOGNITO
        if (running.get()) return Skip.RUNNING
        // review of stage 56 (4): only what the credential-protected settings need before reading them
        // (one call, remembered once true); the keyguard questions come after the switch and the 15 minutes
        if (!userUnlocked(context)) return Skip.LOCKED
        val stored = UpdateSettings(context)
        if (!stored.onOpen) return Skip.SWITCH_OFF
        val now = clock()
        if (!UpdateOnOpenPolicy.due(now, stored.openAttemptAt, stored.openFailures)) return Skip.THROTTLED
        // live: the device may have locked since the field started
        if (UpdateChip.locked(context)) return Skip.LOCKED
        if (dataSaver(context)) return Skip.DATA_SAVER
        if (!online(context)) return Skip.OFFLINE
        val installed = UpdateChip.installed(context)
        if (installed <= 0 || installed == Long.MAX_VALUE) return Skip.NO_VERSION
        if (!running.compareAndSet(false, true)) return Skip.RUNNING
        val app = context.applicationContext ?: context
        val keyboard = WeakReference(context)
        val field = FieldKey.of(editorInfo)
        try {
            executor.execute { run(app, keyboard, field, now, installed) }
        } catch (_: Exception) {
            running.set(false)
            return Skip.FAILED
        }
        return null
    }

    /** On [executor]: the attempt is stored first, so a kill during the request still counts for the limit. */
    private fun run(app: Context, keyboard: WeakReference<Context>, field: FieldKey?, now: Long, installed: Long) {
        try {
            runCatching {
                val stored = UpdateSettings(app)
                stored.openAttemptAt = now
                val before = stored.newerBuild(installed)
                val status = Updates.check(app, { client(app) }, installed)
                // 403/429 and every other failure keep the known state (Updates.check) and double the wait
                stored.openFailures = UpdateOnOpenPolicy.failuresAfter(stored.openFailures, status !is UpdateStatus.Failed)
                val found = stored.newerBuild(installed)
                // postAtTime, not post: tests replace posted messages of all handlers (P51)
                if (found != null && found != before)
                    mainHandler.postAtTime({ refresh(keyboard.get(), field) }, SystemClock.uptimeMillis())
            }
        } finally {
            running.set(false)
        }
    }

    /**
     * On the main thread after a check found a build not known before: shows the chip at once if
     * the keyboard is still shown in [field] and its strip is the empty neutral strip
     * ([LastField.stripNeutral]); never instead of words, chips, a pill or the toolbar, never
     * locked ([UpdateChip.build]). Otherwise the chip comes with the next neutral strip.
     */
    internal fun refresh(context: Context?, field: FieldKey?): Boolean = runCatching {
        val ime = context as? LatinIME ?: return false
        if (field == null || !ime.isInputViewShown || FieldKey.of(ime.currentInputEditorInfo) != field) return false
        if (!LastField.stripNeutral()) return false
        if (UpdateChip.build(ime, Settings.getValues()) == null) return false
        ime.setNeutralSuggestionStrip()
        true
    }.getOrDefault(false)

    /** Like [LastField.snapshotOf]: the engine's flag, every password variation, also in the field's own `inputType`. */
    private fun isPassword(settings: SettingsValues, editorInfo: EditorInfo): Boolean {
        val attributes = settings.mInputAttributes
        return attributes.mIsPasswordField || InputTypeUtils.isAnyPasswordInputType(attributes.mInputType)
            || FieldReport.isPassword(attributes.mInputType) || FieldReport.isPassword(editorInfo.inputType)
    }

    /** The incognito setting, or a field that forbids learning. */
    private fun isIncognito(settings: SettingsValues, editorInfo: EditorInfo): Boolean =
        settings.mIncognitoModeEnabled || settings.mInputAttributes.mNoLearning
            || editorInfo.imeOptions and FieldReport.IME_FLAG_NO_PERSONALIZED_LEARNING != 0

    /** A network with internet access that Android validated; unknown counts as none. */
    internal fun networkUsable(context: Context): Boolean = runCatching {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork ?: return false) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivity.activeNetworkInfo?.isConnected == true
        }
    }.getOrDefault(false)

    /** Data Saver is on and this app is not exempt (Android 7+); unknown counts as restricted. */
    internal fun dataSaverRestricts(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    }.getOrDefault(true)

    /** Tests: wait for nothing, forget a check that was left running. */
    internal fun resetForTest() {
        running.set(false)
    }
}
