// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.providers

import dev.agentkeyboard.core.ModelOutputFormat
import dev.agentkeyboard.core.ModelRequest
import dev.agentkeyboard.core.ModelResponse
import helium314.keyboard.agent.security.Secret
import helium314.keyboard.agent.security.readBounded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The one way any provider talks to its fixed origin: a single non-replayable JSON POST, bounded
 * response, neutral errors. Provider classes only supply the body, the request and the parser.
 */
internal class CloudTransport(
    private val credentials: () -> Secret?,
    private val calls: Call.Factory,
) {
    private val active = AtomicBoolean(false)

    suspend fun post(request: ModelRequest, body: () -> String, build: (Secret, RequestBody) -> Request,
                     parse: (String) -> ModelResponse, allowsDrafts: Boolean = true): ModelResponse {
        if (!active.compareAndSet(false, true)) throw CloudFailure(CloudError.BUSY)
        try {
            if (request.instruction.length !in 1..4096 || request.input.length !in 1..8192 ||
                (!allowsDrafts && request.outputFormat != ModelOutputFormat.TEXT))
                throw CloudFailure(CloudError.INVALID_REQUEST)
            // Includes credential access and async dispatcher queue time.
            return withTimeoutOrNull(45_000) { postOnce(body(), build, parse) } ?: throw CloudFailure(CloudError.TIMEOUT)
        } finally { active.set(false) }
    }

    private suspend fun postOnce(body: String, build: (Secret, RequestBody) -> Request,
                                 parse: (String) -> ModelResponse): ModelResponse {
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
                try { build(it, singleUse) }
                catch (_: Exception) { throw CloudFailure(CloudError.INVALID_REQUEST) }
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
                            parse(bytes.toString(Charsets.UTF_8))
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
}
