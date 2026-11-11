// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.providers

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.ModelSettings
import helium314.keyboard.agent.security.CredentialSlot
import helium314.keyboard.agent.security.EncryptedCredentialStore
import helium314.keyboard.agent.security.Secret
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Request

/** Anthropic Messages API, version 2026-10-21. Text actions only; contract: docs/PROVIDERS.md. */
class AnthropicProvider(
    credentials: () -> Secret?,
    calls: Call.Factory,
) : ModelProvider {
    constructor(store: EncryptedCredentialStore, calls: Call.Factory) : this({ store.read(CredentialSlot.ANTHROPIC) }, calls)
    override val id = "claude"
    override val capabilities = ProviderCapabilities()
    private val transport = CloudTransport(credentials, calls)

    override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
        if (!ModelSettings.validModel(request.model)) throw CloudFailure(CloudError.INVALID_REQUEST)
        return transport.post(request, {
            buildJsonObject {
                put("model", request.model)
                put("max_tokens", 2048)
                put("system", request.instruction)
                put("messages", buildJsonArray { add(buildJsonObject {
                    put("role", "user"); put("content", request.input)
                }) })
                put("stream", false)
            }.toString()
        }, { secret, body ->
            Request.Builder().url("https://api.anthropic.com/v1/messages")
                .header("Authorization", "Bearer ${secret.value()}")
                .header("anthropic-version", API_VERSION)
                .post(body).build()
        }, ::parseResponse, allowsDrafts = false)
    }

    companion object {
        const val API_VERSION = "2026-10-21"

        internal fun parseResponse(body: String): ModelResponse {
            try {
                val root = Json.parseToJsonElement(body).jsonObject
                require(root["type"]?.jsonPrimitive?.content == "message")
                require(root["role"]?.jsonPrimitive?.content == "assistant")
                when (root["stop_reason"]?.jsonPrimitive?.content) {
                    "end_turn" -> Unit
                    "refusal" -> throw CloudFailure(CloudError.REFUSED)
                    else -> throw CloudFailure(CloudError.INVALID_RESPONSE) // truncated, paused or tool use
                }
                val text = StringBuilder()
                root.getValue("content").jsonArray.forEach { block ->
                    val content = block.jsonObject
                    when (content["type"]?.jsonPrimitive?.content) {
                        "text" -> {
                            val value = content.getValue("text").jsonPrimitive
                            require(value.isString)
                            text.append(value.content)
                        }
                        "thinking", "redacted_thinking" -> Unit // never part of the result
                        else -> throw CloudFailure(CloudError.INVALID_RESPONSE)
                    }
                    if (text.length > 16384) throw CloudFailure(CloudError.TOO_LARGE)
                }
                if (text.isBlank() || ExecutionPolicy().resultDenial(text.toString()) != null)
                    throw CloudFailure(CloudError.INVALID_RESPONSE)
                return ModelResponse(text.toString())
            } catch (failure: Exception) {
                throw if (failure is CloudFailure) failure else CloudFailure(CloudError.INVALID_RESPONSE)
            }
        }
    }
}
