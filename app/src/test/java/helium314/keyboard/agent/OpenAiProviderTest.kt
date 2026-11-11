// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import dev.agentkeyboard.core.*
import helium314.keyboard.agent.providers.*
import helium314.keyboard.agent.security.Secret
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OpenAiProviderTest {
    private class TestCall(private val http: Request, private val respond: (Call, Callback) -> Unit) : Call {
        private var cancelled = false
        private var executed = false
        override fun request() = http
        override fun execute(): Response = error("Only asynchronous calls expected")
        override fun enqueue(responseCallback: Callback) { executed = true; respond(this, responseCallback) }
        override fun cancel() { cancelled = true }
        override fun isCanceled() = cancelled
        override fun isExecuted() = executed
        override fun timeout() = okio.Timeout()
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: kotlin.reflect.KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun clone(): Call = TestCall(http, respond)
    }
    private fun request(): ModelRequest {
        val session = EditorSession().apply { start() }
        val snapshot = (ExecutionPolicy().capture(session.token!!, EditorAccess(isText = true), 0, 5, "Hallo") as CaptureResult.Captured).snapshot
        return ModelRequest("gpt-6-luna", "Correct spelling only.", snapshot)
    }
    private fun completed(text: String) = buildJsonObject {
        put("status", "completed")
        put("output", buildJsonArray {
            add(buildJsonObject { put("type", "reasoning") })
            add(buildJsonObject {
                put("type", "message"); put("role", "assistant"); put("status", "completed")
                put("content", buildJsonArray { add(buildJsonObject { put("type", "output_text"); put("text", text) }) })
            })
        })
    }.toString()

    @Test fun fixedOriginRequestSendsOnlyApprovedSourceAndDisablesStorage() = runBlocking {
        lateinit var sent: Request
        val callerThread = Thread.currentThread().id
        var constructionThread = callerThread
        val factory = Call.Factory { http ->
            constructionThread = Thread.currentThread().id
            sent = http
            TestCall(http) { call, callback ->
                callback.onResponse(call, Response.Builder().request(http)
                    .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(completed("Hello").toResponseBody()).build())
            }
        }
        val bytes = "offline-test-token".toByteArray()
        val provider = OpenAiProvider({ Secret(bytes) }, factory)
        assertEquals("Hello", provider.generate(request()) {}.text)
        assertNotEquals(callerThread, constructionThread)
        assertEquals("https://api.openai.com/v1/responses", sent.url.toString())
        assertEquals("Bearer offline-test-token", sent.header("Authorization"))
        assertTrue(sent.body!!.isOneShot())
        val body = Buffer().also { sent.body!!.writeTo(it) }.readUtf8()
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals(setOf("model", "instructions", "input", "max_output_tokens", "store", "stream"), json.keys)
        assertEquals(false, json.getValue("store").jsonPrimitive.boolean)
        assertEquals("Hallo", json.getValue("input").jsonArray.single().jsonObject.getValue("content").jsonPrimitive.content)
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test fun workflowDraftUsesOnlyTheOwnedStructuredSchemaAndPreservesTransportGuards() = runBlocking {
        lateinit var sent: Request
        val proposal = """{"name":"Clean","steps":[{"id":"clean","input":"selection","condition":null,"kind":"local","action":"local.clean_spaces"}]}"""
        val provider = OpenAiProvider({ Secret("offline-test-token".toByteArray()) }, Call.Factory { http ->
            sent = http
            TestCall(http) { call, callback ->
                callback.onResponse(call, Response.Builder().request(http).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(completed(proposal).toResponseBody()).build())
            }
        })
        val request = WorkflowBuilderContract.request("Clean the selected text", "approved-model")
        assertEquals(proposal, provider.generate(request) {}.text)
        val body = Json.parseToJsonElement(Buffer().also { sent.body!!.writeTo(it) }.readUtf8()).jsonObject
        assertEquals("https://api.openai.com/v1/responses", sent.url.toString())
        assertTrue(sent.body!!.isOneShot())
        assertEquals(JsonPrimitive(false), body["store"])
        assertEquals(JsonPrimitive(false), body["stream"])
        assertEquals(JsonPrimitive(4096), body["max_output_tokens"])
        assertEquals("Clean the selected text", body.getValue("input").jsonArray.single().jsonObject.getValue("content").jsonPrimitive.content)
        val format = body.getValue("text").jsonObject.getValue("format").jsonObject
        assertEquals(setOf("type", "name", "strict", "schema"), format.keys)
        assertEquals(JsonPrimitive("json_schema"), format["type"])
        assertEquals(JsonPrimitive(WorkflowBuilderContract.SCHEMA_NAME), format["name"])
        assertEquals(JsonPrimitive(true), format["strict"])
        assertEquals(WorkflowBuilderContract.schema(), format["schema"])
        assertTrue(provider.capabilities.workflowDrafts)
    }

    @Test fun incompleteRefusalMalformedAndOversizedResponsesFailClosed() {
        for (body in listOf("{}", "[broken", completed(""), completed("x").replace("completed", "incomplete"),
            completed("x").replace("output_text", "refusal"), completed("x".repeat(16385)))) {
            assertFailsWith<CloudFailure> { OpenAiProvider.parseResponse(body) }
        }
        assertEquals("Grüße 😀", OpenAiProvider.parseResponse(completed("Grüße 😀")).text)
        for (value in listOf("42", "true", "null")) {
            assertFailsWith<CloudFailure> { OpenAiProvider.parseResponse(completed("marker").replace("\"marker\"", value)) }
        }
    }

    @Test fun cancellingSuspendedRequestCancelsTheActualCall() = runBlocking {
        val started = CompletableDeferred<Unit>()
        lateinit var call: TestCall
        val provider = OpenAiProvider({ Secret("offline-token".toByteArray()) }, Call.Factory { http ->
            TestCall(http) { _, _ -> started.complete(Unit) }.also { call = it }
        })
        val job = launch { provider.generate(request()) {} }
        started.await()
        job.cancelAndJoin()
        assertTrue(call.isCanceled())
    }

    @Test fun missingCredentialsDoNotConstructAnyCall() = runBlocking {
        var created = 0
        val provider = OpenAiProvider({ null }, Call.Factory { created++; error("must not run") })
        val failure = assertFailsWith<CloudFailure> { provider.generate(request()) {} }
        assertEquals(CloudError.CREDENTIALS, failure.error)
        assertEquals(0, created)
        assertNull(failure.cause)
    }

    @Test fun synchronousTransportFailureNeverLeaksItsMessage() = runBlocking {
        val provider = OpenAiProvider({ Secret("offline-token".toByteArray()) }, Call.Factory { error("secret-shaped-internal-error") })
        val failure = assertFailsWith<CloudFailure> { provider.generate(request()) {} }
        assertEquals(CloudError.NETWORK, failure.error)
        assertEquals("NETWORK", failure.message)
        assertNull(failure.cause)
    }

    @Test fun secondConcurrentGenerationIsRejectedBeforeQueueing() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var created = 0
        val provider = OpenAiProvider({ Secret("offline-token".toByteArray()) }, Call.Factory { http ->
            created++
            TestCall(http) { _, _ -> started.complete(Unit) }
        })
        val first = launch { provider.generate(request()) {} }
        started.await()
        assertEquals(CloudError.BUSY, assertFailsWith<CloudFailure> { provider.generate(request()) {} }.error)
        assertEquals(1, created)
        first.cancelAndJoin()
    }

    @Test fun serviceRetryAfterZeroDoesNotReplayThePost() = runBlocking {
        val server = java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1")).apply { soTimeout = 1000 }
        val received = java.util.concurrent.atomic.AtomicInteger()
        val thread = Thread {
            server.use {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: java.net.SocketTimeoutException) { break }
                    socket.use {
                        it.soTimeout = 2000
                        val reader = it.getInputStream().bufferedReader()
                        var length = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                        }
                        repeat(length) { reader.read() }
                        received.incrementAndGet()
                        it.getOutputStream().write("HTTP/1.1 503 Service Unavailable\r\nRetry-After: 0\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        val client = CloudHttpClient.forContext(org.robolectric.RuntimeEnvironment.getApplication())
        // Only this test factory changes the fixed production URL to a loopback fixture.
        val factory = Call.Factory { request -> client.newCall(request.newBuilder()
            .url("http://127.0.0.1:${server.localPort}/fixture").build()) }
        val provider = OpenAiProvider({ Secret("offline-token".toByteArray()) }, factory)
        try {
            val failure = assertFailsWith<CloudFailure> { provider.generate(request()) {} }
            assertEquals(CloudError.SERVICE, failure.error)
            assertEquals(503, failure.httpStatus)
            withContext(Dispatchers.IO) { thread.join(4000) }
            assertEquals(1, received.get())
        } finally { if (!server.isClosed) server.close() }
    }
}
