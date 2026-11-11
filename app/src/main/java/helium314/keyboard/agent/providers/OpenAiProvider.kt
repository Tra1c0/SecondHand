// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.providers

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.ModelSettings
import helium314.keyboard.agent.WorkflowBuilderContract
import helium314.keyboard.agent.security.CredentialSlot
import helium314.keyboard.agent.security.EncryptedCredentialStore
import helium314.keyboard.agent.security.Secret
import helium314.keyboard.agent.security.readBounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class CloudError { BUSY, CREDENTIALS, INVALID_REQUEST, AUTHENTICATION, RATE_LIMIT, SERVICE, NETWORK, TIMEOUT, REFUSED, INVALID_RESPONSE, TOO_LARGE, STORAGE }
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
    private val delegate = lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .dispatcher(Dispatcher().apply { maxRequests = 1; maxRequestsPerHost = 1 })
            .build()
    }
    val client: OkHttpClient get() = delegate.value
    val isInitialized: Boolean get() = delegate.isInitialized()
}

class OpenAiProvider(
    private val credentials: () -> Secret?,
    private val calls: Call.Factory,
) : ModelProvider {
    constructor(store: EncryptedCredentialStore, calls: Call.Factory) : this({ store.read(CredentialSlot.OPENAI) }, calls)
    override val id = "openai"
    override val capabilities = ProviderCapabilities(workflowDrafts = true)
    private val active = AtomicBoolean(false)

    override suspend fun generate(request: ModelRequest, onTextDelta: (String) -> Unit): ModelResponse {
        if (!active.compareAndSet(false, true)) throw CloudFailure(CloudError.BUSY)
        try {
            // Includes credential access and async dispatcher queue time.
            return withTimeoutOrNull(45_000) { generateOnce(request) } ?: throw CloudFailure(CloudError.TIMEOUT)
        } finally { active.set(false) }
    }

    private suspend fun generateOnce(request: ModelRequest): ModelResponse {
        if (!ModelSettings.validModel(request.model) || request.instruction.length !in 1..4096 ||
            request.input.length !in 1..8192) throw CloudFailure(CloudError.INVALID_REQUEST)
        val body = buildJsonObject {
            put("model", request.model)
            put("instructions", request.instruction)
            put("input", buildJsonArray { add(buildJsonObject {
                put("role", "user"); put("content", request.input)
            }) })
            put("max_output_tokens", if (request.outputFormat == ModelOutputFormat.WORKFLOW_DRAFT) 4096 else 2048)
            if (request.outputFormat == ModelOutputFormat.WORKFLOW_DRAFT) put("text", buildJsonObject {
                put("format", buildJsonObject {
                    put("type", "json_schema"); put("name", WorkflowBuilderContract.SCHEMA_NAME)
                    put("strict", true); put("schema", WorkflowBuilderContract.schema())
                })
            })
            put("store", false)
            put("stream", false)
        }.toString()
        val httpRequest = withContext(Dispatchers.IO) {
            val secret = try { credentials() ?: throw CloudFailure(CloudError.CREDENTIALS) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                throw CloudFailure(CloudError.CREDENTIALS)
            }
            secret.use {
                val content = body.toRequestBody("application/json; charset=utf-8".toMediaType())
                val singleUse = object : RequestBody() {
                    override fun contentType() = content.contentType()
                    override fun contentLength() = content.contentLength()
                    override fun writeTo(sink: okio.BufferedSink) = content.writeTo(sink)
                    override fun isOneShot() = true
                }
                Request.Builder().url("https://api.openai.com/v1/responses")
                    .header("Authorization", "Bearer ${it.value()}")
                    .post(singleUse).build()
            }
        }
        // TLS/client initialization may load certificates. Do it off the IME's
        // main thread; a call is not enqueued until cancellation is registered.
        val call = withContext(Dispatchers.IO) {
            try { calls.newCall(httpRequest) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                throw CloudFailure(CloudError.NETWORK)
            }
        }
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            val callback = object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(CloudFailure(
                        if (e is InterruptedIOException) CloudError.TIMEOUT else CloudError.NETWORK))
                }
                override fun onResponse(call: Call, response: Response) {
                    val outcome = runCatching {
                        response.use {
                            if (it.code !in 200..299) throw CloudFailure(when (it.code) {
                                401, 403 -> CloudError.AUTHENTICATION
                                429 -> CloudError.RATE_LIMIT
                                400, 404, 422 -> CloudError.INVALID_REQUEST
                                else -> CloudError.SERVICE
                            }, it.code)
                            val stream = it.body.byteStream()
                            val bytes = try { stream.readBounded(1_048_576) }
                            catch (_: IllegalArgumentException) { throw CloudFailure(CloudError.TOO_LARGE) }
                            parseResponse(bytes.toString(Charsets.UTF_8))
                        }
                    }.recoverCatching { failure ->
                        throw when (failure) {
                            is CloudFailure -> failure
                            is InterruptedIOException -> CloudFailure(CloudError.TIMEOUT)
                            is IOException -> CloudFailure(CloudError.NETWORK)
                            else -> CloudFailure(CloudError.INVALID_RESPONSE)
                        }
                    }
                    if (continuation.isActive) outcome.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) })
                }
            }
            try { call.enqueue(callback) }
            catch (failure: Exception) {
                runCatching { call.cancel() }
                if (failure is CancellationException) throw failure
                if (continuation.isActive) continuation.resumeWithException(CloudFailure(CloudError.NETWORK))
            }
        }
    }

    companion object {
        internal fun parseResponse(body: String): ModelResponse {
            try {
                val root = Json.parseToJsonElement(body).jsonObject
                if (root["status"]?.jsonPrimitive?.content != "completed") throw CloudFailure(CloudError.INVALID_RESPONSE)
                val text = StringBuilder()
                root.getValue("output").jsonArray.forEach { item ->
                    val message = item.jsonObject
                    require(message["type"]?.jsonPrimitive?.content in setOf("message", "reasoning"))
                    if (message["type"]?.jsonPrimitive?.content == "message") {
                        require(message["role"]?.jsonPrimitive?.content == "assistant")
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
                            if (text.length > 16384) throw CloudFailure(CloudError.TOO_LARGE)
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
