// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import helium314.keyboard.agent.providers.CloudProviders
import helium314.keyboard.agent.security.credentialContext
import dev.agentkeyboard.core.TranslationLanguage
import dev.agentkeyboard.core.WorkflowProvider
import dev.agentkeyboard.core.WritingTone

/** Non-secret AI choices in credential-protected preferences; never part of keyboard exports. */
class ModelSettings(context: Context) {
    private val storage = credentialContext(context).getSharedPreferences("agent_models", Context.MODE_PRIVATE)
    val openAiModel: String get() = model(WorkflowProvider.OPENAI)

    /** The provider the keyboard's single AI actions use. Workflow steps name their own. */
    val provider: WorkflowProvider get() =
        CloudProviders.SUPPORTED.firstOrNull { it.id == storage.getString("provider", null) } ?: WorkflowProvider.OPENAI

    fun setProvider(provider: WorkflowProvider) {
        require(provider in CloudProviders.SUPPORTED)
        check(storage.edit().putString("provider", provider.id).commit())
    }

    /** Empty when no model was entered and the provider has no checked default. */
    fun model(provider: WorkflowProvider): String {
        require(provider in CloudProviders.SUPPORTED)
        return storage.getString(provider.id + "_model", null) ?: defaultModel(provider)
    }

    fun setModel(provider: WorkflowProvider, model: String) {
        require(CloudProviders.validModel(provider, model))
        check(storage.edit().putString(provider.id + "_model", model).commit())
    }

    val translationLanguage: TranslationLanguage get() =
        TranslationLanguage.fromCode(storage.getString("translation_language", null)) ?: TranslationLanguage.ENGLISH

    fun setTranslationLanguage(language: TranslationLanguage) {
        check(storage.edit().putString("translation_language", language.code).commit())
    }
    val writingTone: WritingTone get() = WritingTone.entries.firstOrNull {
        it.name == storage.getString("writing_tone", null)
    } ?: WritingTone.NEUTRAL

    fun setWritingTone(tone: WritingTone) {
        check(storage.edit().putString("writing_tone", tone.name).commit())
    }
    fun setOpenAiModel(model: String) = setModel(WorkflowProvider.OPENAI, model)

    companion object {
        const val DEFAULT_MODEL = "gpt-6-luna"
        /** Listed in the Anthropic model catalogue on 2026-11-11 as the fastest model. */
        const val DEFAULT_ANTHROPIC_MODEL = "claude-haiku-5-5"
        fun validModel(model: String) = dev.agentkeyboard.core.ModelRequest.validModel(model)
        /** Gemini has no default: its current model names could not be checked (docs/PROVIDERS.md). */
        fun defaultModel(provider: WorkflowProvider) = when (provider) {
            WorkflowProvider.OPENAI -> DEFAULT_MODEL
            WorkflowProvider.CLAUDE -> DEFAULT_ANTHROPIC_MODEL
            else -> ""
        }
    }
}
