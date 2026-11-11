// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import helium314.keyboard.agent.security.credentialContext
import dev.agentkeyboard.core.TranslationLanguage
import dev.agentkeyboard.core.WritingTone

class ModelSettings(context: Context) {
    private val storage = credentialContext(context).getSharedPreferences("agent_models", Context.MODE_PRIVATE)
    val openAiModel: String get() = storage.getString("openai_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
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
    fun setOpenAiModel(model: String) {
        require(validModel(model))
        check(storage.edit().putString("openai_model", model).commit())
    }
    companion object {
        const val DEFAULT_MODEL = "gpt-6-luna"
        fun validModel(model: String) = dev.agentkeyboard.core.ModelRequest.validModel(model)
    }
}
