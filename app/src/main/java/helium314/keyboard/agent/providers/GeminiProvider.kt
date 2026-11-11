// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.providers

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.security.CredentialSlot
import helium314.keyboard.agent.security.EncryptedCredentialStore
import helium314.keyboard.agent.security.Secret
import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.Request

/** Gemini generateContent (v1beta). Text actions only; contract and its check date: docs/PROVIDERS.md. */
class GeminiProvider(
    credentials: () -> Secret?,
    calls: Call.Factory,
) : ModelProvider {
    constructor(store: EncryptedCredentialStore, calls: Call.Factory) : this({ store.read(CredentialSlot.GEMINI) }, calls)
    override val id = "gemini"
    override val capabilities = ProviderCapabilities()
    private val transport = CloudTransport(credentials, calls)

    override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
        // The model becomes a URL path segment: stricter than the general model pattern.
        if (!validModel(request.model)) throw CloudFailure(CloudError.INVALID_REQUEST)
        return transport.post(request, {
            buildJsonObject {
                put("systemInstruction", buildJsonObject { put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", request.instruction) })
                }) })
                put("contents", buildJsonArray { add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", request.input) }) })
                }) })
                put("generationConfig", buildJsonObject { put("maxOutputTokens", MAX_TOKENS); put("candidateCount", 1) })
            }.toString()
        }, { secret, body ->
            Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${request.model}:generateContent")
                .header("x-goog-api-key", secret.value()) // never in the URL
                .post(body).build()
        }, ::parseResponse, allowsDrafts = false)
    }

    companion object {
        /** Thought tokens count against this cap on thinking models; leave room for them. */
        const val MAX_TOKENS = 8192

        fun validModel(model: String) = model.matches(Regex("[a-z0-9][a-z0-9.-]{0,99}")) && !model.contains("..")

        private val blockedReasons = setOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY")

        internal fun parseResponse(body: String): ModelResponse {
            try {
                val root = Json.parseToJsonElement(body).jsonObject
                val feedback = root["promptFeedback"]
                if (feedback is JsonObject && feedback["blockReason"] != null) throw CloudFailure(CloudError.REFUSED)
                val candidate = root.getValue("candidates").jsonArray.single().jsonObject
                when (candidate["finishReason"]?.jsonPrimitive?.content) {
                    "STOP" -> Unit
                    in blockedReasons -> throw CloudFailure(CloudError.REFUSED)
                    "MAX_TOKENS" -> throw CloudFailure(CloudError.TOO_LARGE) // cut off
                    else -> throw CloudFailure(CloudError.INVALID_RESPONSE)
                }
                val text = StringBuilder()
                candidate.getValue("content").jsonObject.getValue("parts").jsonArray.forEach { part ->
                    val content = part.jsonObject
                    val value = content["text"]?.jsonPrimitive ?: throw CloudFailure(CloudError.INVALID_RESPONSE)
                    require(value.isString)
                    val thought = content["thought"]?.jsonPrimitive?.booleanOrNull == true
                    if (!thought) text.append(value.content) // thought summaries are never part of the result
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
