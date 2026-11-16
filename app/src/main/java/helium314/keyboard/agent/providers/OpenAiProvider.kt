// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.providers

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.ModelSettings
import helium314.keyboard.agent.WorkflowBuilderContract
import helium314.keyboard.agent.security.CredentialSlot
import helium314.keyboard.agent.security.EncryptedCredentialStore
import helium314.keyboard.agent.security.Secret
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit

enum class CloudError { BUSY, CREDENTIALS, INVALID_REQUEST, AUTHENTICATION, RATE_LIMIT, SERVICE, NETWORK, TIMEOUT, REFUSED, INVALID_RESPONSE, TOO_LARGE, STORAGE,
    /** Stage 59: the answer stopped at the output limit (also reasoning tokens count); never shown as a result. */
    CUT_OFF }
class CloudFailure(val error: CloudError, val httpStatus: Int? = null) : Exception(error.name)

/** Constructed lazily by an explicit action, never by ordinary typing. */
object CloudHttpClient {
    fun callsForContext(context: android.content.Context): Call.Factory {
        val application = context.applicationContext
        return Call.Factory { request -> forContext(application).newCall(request) }
    }
    fun forContext(context: android.content.Context): OkHttpClient {
        OkHttp.initialize(context.applicationContext)
        return client
    }
    /** The configuration of the client, separate from the lazily built instance so tests can inspect it without initializing it. */
    internal fun newBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        // stage 59: with stream:false the first byte of the answer only comes when the model is done
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(ModelBudget.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS).callTimeout(ModelBudget.CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .dispatcher(Dispatcher().apply { maxRequests = 1; maxRequestsPerHost = 1 })
    private val delegate = lazy { newBuilder().build() }
    val client: OkHttpClient get() = delegate.value
    val isInitialized: Boolean get() = delegate.isInitialized()
}

class OpenAiProvider(
    credentials: () -> Secret?,
    calls: Call.Factory,
) : ModelProvider {
    constructor(store: EncryptedCredentialStore, calls: Call.Factory) : this({ store.read(CredentialSlot.OPENAI) }, calls)
    override val id = "openai"
    override val capabilities = ProviderCapabilities(workflowDrafts = true)
    private val transport = CloudTransport(credentials, calls)

    override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
        if (!ModelSettings.validModel(request.model)) throw CloudFailure(CloudError.INVALID_REQUEST)
        return transport.post(request, {
            buildJsonObject {
                put("model", request.model)
                put("instructions", request.instruction)
                put("input", buildJsonArray { add(buildJsonObject {
                    put("role", "user"); put("content", request.input)
                }) })
                put("max_output_tokens", ModelBudget.outputTokens(request))
                if (request.outputFormat == ModelOutputFormat.WORKFLOW_DRAFT) put("text", buildJsonObject {
                    put("format", buildJsonObject {
                        put("type", "json_schema"); put("name", WorkflowBuilderContract.SCHEMA_NAME)
                        put("strict", true); put("schema", WorkflowBuilderContract.schema())
                    })
                })
                put("store", false)
                put("stream", false)
            }.toString()
        }, { secret, body ->
            Request.Builder().url("https://api.openai.com/v1/responses")
                .header("Authorization", "Bearer ${secret.value()}")
                .post(body).build()
        }, ::parseResponse)
    }

    companion object {
        internal fun parseResponse(body: String): ModelResponse {
            try {
                val root = Json.parseToJsonElement(body).jsonObject
                when (root["status"]?.jsonPrimitive?.content) {
                    "completed" -> Unit
                    // stage 59: stopped at the output limit (reasoning tokens count against it) or by the provider
                    "incomplete" -> throw CloudFailure(
                        if (root["incomplete_details"]?.let { (it as? JsonObject)?.get("reason")?.jsonPrimitive?.content } == "content_filter")
                            CloudError.REFUSED else CloudError.CUT_OFF)
                    else -> throw CloudFailure(CloudError.INVALID_RESPONSE)
                }
                val text = StringBuilder()
                root.getValue("output").jsonArray.forEach { item ->
                    val message = item.jsonObject
                    require(message["type"]?.jsonPrimitive?.content in setOf("message", "reasoning"))
                    if (message["type"]?.jsonPrimitive?.content == "message") {
                        require(message["role"]?.jsonPrimitive?.content == "assistant")
                        if (message["status"]?.jsonPrimitive?.content == "incomplete") throw CloudFailure(CloudError.CUT_OFF)
                        require(message["status"]?.jsonPrimitive?.content == "completed")
                        message.getValue("content").jsonArray.forEach { part ->
                            val content = part.jsonObject
                            when (content["type"]?.jsonPrimitive?.content) {
                                "refusal" -> throw CloudFailure(CloudError.REFUSED)
                                "output_text" -> {
                                    val value = content.getValue("text").jsonPrimitive
                                    require(value.isString)
                                    text.append(value.content)
                                }
                                else -> throw CloudFailure(CloudError.INVALID_RESPONSE)
                            }
                            if (text.length > ModelBudget.MAX_RESULT_CHARS) throw CloudFailure(CloudError.TOO_LARGE)
                        }
                    }
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
