// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentkeyboard.core.WorkflowDefinition
import helium314.keyboard.agent.*
import helium314.keyboard.agent.providers.*
import helium314.keyboard.agent.security.EncryptedCredentialStore
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.CoroutineScope

internal typealias WorkflowDraftRunnerFactory = (CoroutineScope, () -> Boolean) -> WorkflowDraftRunner
internal val LocalWorkflowDraftRunnerFactory = staticCompositionLocalOf<WorkflowDraftRunnerFactory?> { null }

/** Prompt lives only in this foreground dialog; activation is handled by the separate review screen. */
@Composable
internal fun WorkflowBuilderDialog(model: String, onDismiss: () -> Unit, onPrepared: (WorkflowDefinition) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { context.prefs() }
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentPrepared by rememberUpdatedState(onPrepared)
    val isAllowed = remember(context, lifecycle, preferences) { {
        workflowDraftAllowed(context, lifecycle)
    } }
    val factory = LocalWorkflowDraftRunnerFactory.current
    val runner = remember(scope, context, isAllowed, factory) {
        factory?.invoke(scope, isAllowed) ?: WorkflowDraftRunner(scope,
            { OpenAiProvider(EncryptedCredentialStore(context), CloudHttpClient.callsForContext(context)) },
            isAllowed, EncryptedActionRunStore(context))
    }
    // Never save prompt, approval or result into instance state or a workflow file.
    var prompt by remember { mutableStateOf("") }
    var approval by remember { mutableStateOf<WorkflowDraftApproval?>(null) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableIntStateOf(0) }
    fun close() {
        runner.cancel(); prompt = ""; approval = null; running = false
        currentDismiss()
    }
    DisposableEffect(runner, lifecycle, preferences) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) close()
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Settings.PREF_ALWAYS_INCOGNITO_MODE && !isAllowed()) close()
        }
        lifecycle.addObserver(observer)
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
            runner.cancel(); prompt = ""; approval = null
        }
    }
    LaunchedEffect(runner) { if (!isAllowed()) close() }

    // Read render state here, including null→null configuration and error paths.
    val displayedApproval = approval
    val displayedRunning = running
    val displayedStatus = status
    val displayedPrompt = prompt
    val validPrompt = runCatching { WorkflowBuilderContract.request(displayedPrompt, model) }.isSuccess
    AlertDialog(onDismissRequest = ::close,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(if (displayedApproval == null) R.string.agent_builder_title else R.string.agent_builder_send_review)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.agent_builder_description))
                Text("OpenAI · " + (displayedApproval?.model ?: model), style = MaterialTheme.typography.titleSmall)
                if (displayedApproval == null) {
                    OutlinedTextField(value = displayedPrompt, onValueChange = {
                        if (!running && it.length <= WorkflowBuilderContract.MAX_PROMPT_CHARS) { prompt = it; status = 0 }
                    }, enabled = !displayedRunning,
                        label = { Text(stringResource(R.string.agent_builder_prompt)) }, minLines = 3,
                        // Password input semantics prevent the IME from learning this unmasked instruction.
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("agent_builder_prompt"))
                } else {
                    Text(stringResource(R.string.agent_builder_send_explanation))
                    Text(displayedApproval.prompt, Modifier.testTag("agent_builder_approved_prompt"))
                }
                if (displayedRunning) Text(stringResource(R.string.agent_builder_working))
                if (displayedStatus != 0) Text(stringResource(displayedStatus))
            }
        }, dismissButton = { TextButton(onClick = ::close) { Text(stringResource(android.R.string.cancel)) } },
        confirmButton = {
            TextButton(enabled = !displayedRunning && (displayedApproval != null || validPrompt), onClick = send@ {
                if (running) return@send
                if (!isAllowed()) { close(); return@send }
                if (displayedApproval == null) {
                    approval = runner.prepare(displayedPrompt, model)
                    if (approval == null) status = R.string.agent_builder_unavailable
                } else {
                    running = true; status = 0
                    val started = runner.start(displayedApproval) { result ->
                        running = false; approval = null
                        result.fold(onSuccess = {
                            prompt = ""
                            currentPrepared(it)
                        }, onFailure = { status = (it as? CloudFailure)?.error.cloudMessage() })
                    }
                    if (!started) { running = false; approval = null; status = R.string.agent_builder_unavailable }
                }
            }) { Text(stringResource(if (displayedApproval == null) R.string.agent_builder_review_send else R.string.agent_builder_send)) }
        })
}
