// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

internal fun workflowDraftAllowed(context: Context, lifecycle: Lifecycle): Boolean =
    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && credentialAccessAllowed(context) &&
        !context.prefs().getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE)

/** One continuous protection scope from the first prompt through the unsaved proposal review. */
@Composable
internal fun ProtectWorkflowDraft(onRevoke: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preferences = remember(context) { context.prefs() }
    val currentRevoke by rememberUpdatedState(onRevoke)
    val window = context.draftActivity()?.window
    DisposableEffect(context, lifecycle, preferences, window) {
        SecureWindow.acquire(window)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) currentRevoke()
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Android delivers preference listeners on the main thread. Revoke immediately,
            // including a brief incognito interval followed by a return to normal mode.
            if (key == Settings.PREF_ALWAYS_INCOGNITO_MODE && !workflowDraftAllowed(context, lifecycle)) currentRevoke()
        }
        lifecycle.addObserver(observer)
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            SecureWindow.release(window)
        }
    }
    LaunchedEffect(context, lifecycle) { if (!workflowDraftAllowed(context, lifecycle)) currentRevoke() }
}

private tailrec fun Context.draftActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.draftActivity()
    else -> null
}
