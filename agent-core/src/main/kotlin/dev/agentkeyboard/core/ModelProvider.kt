// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core

/** Provider implementations are added individually. This contract performs no I/O. */
interface ModelProvider {
    val id: String
    val capabilities: ProviderCapabilities
    suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse
}

data class ProviderCapabilities(
    val streaming: Boolean = false,
    val tools: Boolean = false,
    val local: Boolean = false,
    val workflowDrafts: Boolean = false,
)

enum class ModelOutputFormat { TEXT, WORKFLOW_DRAFT }

/** Text data only; a provider never receives the token authorizing editor changes. */
class ModelRequest(val model: String, val instruction: String, val input: String,
                   val outputFormat: ModelOutputFormat = ModelOutputFormat.TEXT) {
    constructor(model: String, instruction: String, source: TextSnapshot) : this(model, instruction, source.text)
    init {
        require(model.isNotBlank() && instruction.isNotBlank())
    }

    override fun toString() = "ModelRequest(model=$model, inputChars=${input.length})"

    companion object {
        fun validModel(model: String) = model.matches(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))
    }
}

class ModelResponse(val text: String) {
    override fun toString() = "ModelResponse(chars=${text.length})"
}
