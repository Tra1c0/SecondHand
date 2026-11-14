// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import helium314.keyboard.agent.ModelSettings
import dev.agentkeyboard.core.TranslationLanguage
import dev.agentkeyboard.core.WritingTone
import helium314.keyboard.agent.titleResource
import helium314.keyboard.agent.providers.CloudProviders
import helium314.keyboard.agent.security.CredentialFailure
import helium314.keyboard.agent.security.CredentialError
import helium314.keyboard.agent.security.EncryptedCredentialStore
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.latin.R
import helium314.keyboard.settings.SearchSettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun AgentAiScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var accessible by remember { mutableStateOf(credentialAccessAllowed(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accessible = credentialAccessAllowed(context)
            if (event == Lifecycle.Event.ON_STOP) accessible = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!accessible) {
        // Do not even construct ModelSettings: opening CE preferences before
        // the first unlock throws on real Direct Boot devices.
        SearchSettingsScreen(onClickBack, stringResource(R.string.agent_ai_title), emptyList()) {
            Text(stringResource(R.string.agent_ai_unlock), Modifier.padding(16.dp))
        }
        return
    }
    AgentAiUnlockedScreen(onClickBack)
}

@Composable
private fun AgentAiUnlockedScreen(onClickBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { EncryptedCredentialStore(context) }
    val models = remember { ModelSettings(context) }
    val scope = rememberCoroutineScope()
    // Deliberately not rememberSaveable: never put a key in saved instance state.
    var apiKey by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(models.provider) }
    var providerMenu by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(models.model(provider)) }
    var language by remember { mutableStateOf(models.translationLanguage) }
    var languageMenu by remember { mutableStateOf(false) }
    var tone by remember { mutableStateOf(models.writingTone) }
    var toneMenu by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableIntStateOf(0) }
    val window = context.activity()?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) apiKey = ""
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(window) {
        SecureWindow.acquire(window)
        onDispose {
            apiKey = ""
            SecureWindow.release(window)
        }
    }
    LaunchedEffect(store, provider) {
        configured = null
        val slot = CloudProviders.slot(provider)
        runCatching { withContext(Dispatchers.IO) { store.contains(slot) } }
            .onSuccess { configured = it }.onFailure { status = R.string.agent_ai_unavailable }
    }

    SearchSettingsScreen(onClickBack, stringResource(R.string.agent_ai_title), emptyList()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.agent_ai_description))
            Text(stringResource(R.string.agent_ai_provider))
            Box {
                OutlinedButton(enabled = !busy, onClick = { providerMenu = true }) {
                    Text(CloudProviders.displayName(provider))
                }
                DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                    CloudProviders.SUPPORTED.forEach { target ->
                        DropdownMenuItem(text = { Text(CloudProviders.displayName(target)) }, onClick = {
                            providerMenu = false
                            // A typed key belongs to the provider it was typed for: never carry it over.
                            apiKey = ""
                            runCatching { models.setProvider(target); models.model(target) }.onSuccess {
                                provider = target; model = it; status = 0
                            }.onFailure { status = R.string.agent_ai_unavailable }
                        })
                    }
                }
            }
            Text(stringResource(R.string.agent_ai_provider_note), style = MaterialTheme.typography.bodySmall)
            Text(CloudProviders.displayName(provider), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(when (configured) {
                true -> R.string.agent_ai_key_present
                false -> R.string.agent_ai_key_missing
                null -> R.string.agent_ai_key_unknown
            }))
            OutlinedTextField(value = apiKey, onValueChange = { if (it.length <= 4096) apiKey = it },
                label = { Text(stringResource(R.string.agent_ai_key)) }, singleLine = true,
                enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && apiKey.isNotEmpty(), onClick = {
                val chars = apiKey.toCharArray()
                val slot = CloudProviders.slot(provider) // the provider shown when the button was pressed
                apiKey = ""
                busy = true
                // Enter the try/finally immediately so disposal before dispatch
                // cannot strand the mutable key copy in an unstarted coroutine.
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        withContext(Dispatchers.IO) { store.save(slot, chars) }
                        configured = true
                        status = R.string.agent_ai_saved
                    } catch (failure: Exception) {
                        status = if ((failure as? CredentialFailure)?.error == CredentialError.INVALID_KEY)
                            R.string.agent_ai_invalid_key else R.string.agent_ai_unavailable
                    }
                    finally { chars.fill('\u0000'); busy = false }
                }
            }) { Text(stringResource(R.string.agent_ai_save_key)) }
            TextButton(enabled = !busy && configured == true, onClick = {
                val slot = CloudProviders.slot(provider)
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { store.delete(slot) }
                        configured = false
                        status = R.string.agent_ai_deleted
                    } catch (_: Exception) { status = R.string.agent_ai_unavailable }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.agent_ai_delete_key)) }
            OutlinedTextField(value = model, onValueChange = { if (it.length <= 100) model = it },
                label = { Text(stringResource(R.string.agent_ai_model)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
            if (ModelSettings.defaultModel(provider).isEmpty()) Text(stringResource(R.string.agent_ai_model_none),
                style = MaterialTheme.typography.bodySmall)
            Button(enabled = !busy && CloudProviders.validModel(provider, model), onClick = {
                runCatching { models.setModel(provider, model) }.onSuccess { status = R.string.agent_ai_model_saved }
                    .onFailure { status = R.string.agent_ai_unavailable }
            }) { Text(stringResource(R.string.agent_ai_save_model)) }
            Text(stringResource(R.string.agent_ai_translation_language))
            Box {
                OutlinedButton(enabled = !busy, onClick = { languageMenu = true }) {
                    Text(java.util.Locale.forLanguageTag(language.code).getDisplayLanguage(java.util.Locale.getDefault()))
                }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                    TranslationLanguage.entries.forEach { target ->
                        DropdownMenuItem(text = { Text(java.util.Locale.forLanguageTag(target.code).getDisplayLanguage(java.util.Locale.getDefault())) },
                            onClick = {
                                languageMenu = false
                                runCatching { models.setTranslationLanguage(target) }.onSuccess { language = target }
                                    .onFailure { status = R.string.agent_ai_unavailable }
                            })
                    }
                }
            }
            Text(stringResource(R.string.agent_ai_writing_tone))
            Box {
                OutlinedButton(enabled = !busy, onClick = { toneMenu = true }) {
                    Text(stringResource(tone.titleResource()))
                }
                DropdownMenu(expanded = toneMenu, onDismissRequest = { toneMenu = false }) {
                    WritingTone.entries.forEach { target ->
                        DropdownMenuItem(text = { Text(stringResource(target.titleResource())) }, onClick = {
                            toneMenu = false
                            runCatching { models.setWritingTone(target) }.onSuccess { tone = target }
                                .onFailure { status = R.string.agent_ai_unavailable }
                        })
                    }
                }
            }
            if (status != 0) Text(stringResource(status))
            AgentRunStatus()
        }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
