// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.providers

import android.content.Context
import dev.agentkeyboard.core.ModelProvider
import dev.agentkeyboard.core.WorkflowProvider
import helium314.keyboard.agent.security.CredentialSlot
import helium314.keyboard.agent.security.EncryptedCredentialStore

/**
 * The providers that have a real adapter. Each is constructed lazily on its first approved
 * request and reads only its own credential slot. Nothing here runs during ordinary typing.
 */
class CloudProviders(context: Context) {
    private val application = context.applicationContext
    private val instances = HashMap<WorkflowProvider, ModelProvider>()

    @Synchronized fun get(provider: WorkflowProvider): ModelProvider = instances.getOrPut(provider) {
        val store = EncryptedCredentialStore(application)
        val calls = CloudHttpClient.callsForContext(application)
        when (provider) {
            WorkflowProvider.OPENAI -> OpenAiProvider(store, calls)
            WorkflowProvider.CLAUDE -> AnthropicProvider(store, calls)
            WorkflowProvider.GEMINI -> GeminiProvider(store, calls)
            WorkflowProvider.LOCAL -> throw CloudFailure(CloudError.INVALID_REQUEST)
        }
    }

    companion object {
        /** Order shown in settings. LOCAL has no adapter yet. */
        val SUPPORTED: List<WorkflowProvider> = listOf(WorkflowProvider.OPENAI, WorkflowProvider.CLAUDE, WorkflowProvider.GEMINI)

        fun slot(provider: WorkflowProvider): CredentialSlot = when (provider) {
            WorkflowProvider.OPENAI -> CredentialSlot.OPENAI
            WorkflowProvider.CLAUDE -> CredentialSlot.ANTHROPIC
            WorkflowProvider.GEMINI -> CredentialSlot.GEMINI
            WorkflowProvider.LOCAL -> throw IllegalArgumentException("NO_CREDENTIAL")
        }

        /** Company names are proper nouns and are not translated. */
        fun displayName(provider: WorkflowProvider): String = when (provider) {
            WorkflowProvider.OPENAI -> "OpenAI"
            WorkflowProvider.CLAUDE -> "Anthropic"
            WorkflowProvider.GEMINI -> "Google Gemini"
            WorkflowProvider.LOCAL -> "Local"
        }

        fun validModel(provider: WorkflowProvider, model: String): Boolean = when (provider) {
            WorkflowProvider.GEMINI -> GeminiProvider.validModel(model)
            else -> dev.agentkeyboard.core.ModelRequest.validModel(model)
        }
    }
}
