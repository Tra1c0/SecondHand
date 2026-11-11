// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.app.Application
import dev.agentkeyboard.core.*
import helium314.keyboard.agent.providers.*
import helium314.keyboard.agent.security.CredentialSlot
import helium314.keyboard.agent.security.Secret
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoreProvidersTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    private class TestCall(private val http: Request, private val respond: (Call, Callback) -> Unit) : Call {
        private var executed = false; private var cancelled = false
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
    private fun answering(body: String, code: Int = 200, sent: (Request) -> Unit = {}) = Call.Factory { http ->
        sent(http)
        TestCall(http) { call, callback ->
            callback.onResponse(call, Response.Builder().request(http).protocol(Protocol.HTTP_1_1).code(code)
                .message("x").body(body.toResponseBody()).build())
        }
    }
    private fun request(model: String, format: ModelOutputFormat = ModelOutputFormat.TEXT) =
        ModelRequest(model, "Correct spelling only.", "Hallo", format)

    private fun claude(text: String, stop: String = "end_turn", type: String = "text") = buildJsonObject {
        put("type", "message"); put("role", "assistant"); put("stop_reason", stop)
        put("content", buildJsonArray {
            add(buildJsonObject { put("type", "thinking"); put("thinking", "hidden reasoning"); put("signature", "s") })
            add(buildJsonObject { put("type", type); put("text", text) })
        })
    }.toString()

    private fun gemini(text: String, finish: String = "STOP") = buildJsonObject {
        put("candidates", buildJsonArray { add(buildJsonObject {
            put("finishReason", finish)
            put("content", buildJsonObject { put("role", "model"); put("parts", buildJsonArray {
                add(buildJsonObject { put("text", "hidden reasoning"); put("thought", true) })
                add(buildJsonObject { put("text", text) })
            }) })
        }) })
    }.toString()

    @Test fun anthropicRequestUsesTheFixedOriginVersionHeaderAndOnlyApprovedText() = runBlocking {
        lateinit var sent: Request
        val bytes = "offline-test-token".toByteArray()
        val provider = AnthropicProvider({ Secret(bytes) }, answering(claude("Hello")) { sent = it })
        assertEquals("claude", provider.id)
        assertFalse(provider.capabilities.workflowDrafts)
        assertEquals("Hello", provider.generate(request("claude-haiku-5-5")) {}.text)
        assertEquals("https://api.anthropic.com/v1/messages", sent.url.toString())
        assertEquals("Bearer offline-test-token", sent.header("Authorization"))
        assertEquals("2026-10-21", sent.header("anthropic-version"))
        assertTrue(sent.body!!.isOneShot())
        val json = Json.parseToJsonElement(Buffer().also { sent.body!!.writeTo(it) }.readUtf8()).jsonObject
        // The 2026-10-21 version rejects temperature, top_p and top_k.
        assertEquals(setOf("model", "max_tokens", "system", "messages", "stream"), json.keys)
        assertEquals("Correct spelling only.", json.getValue("system").jsonPrimitive.content)
        val message = json.getValue("messages").jsonArray.single().jsonObject
        assertEquals("user", message.getValue("role").jsonPrimitive.content)
        assertEquals("Hallo", message.getValue("content").jsonPrimitive.content)
        assertEquals(false, json.getValue("stream").jsonPrimitive.boolean)
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test fun anthropicAcceptsOnlyFinishedTextAndNeverThinkingOrTools() {
        assertEquals("Grüße 😀", AnthropicProvider.parseResponse(claude("Grüße 😀")).text)
        assertEquals(CloudError.REFUSED, assertFailsWith<CloudFailure> {
            AnthropicProvider.parseResponse(claude("x", stop = "refusal")) }.error)
        for (body in listOf("{}", "[broken", claude(""), claude("x", stop = "max_tokens"), claude("x", stop = "tool_use"),
            claude("x", stop = "pause_turn"), claude("x", type = "tool_use"), claude("x".repeat(16385)),
            claude("x").replace("\"assistant\"", "\"user\""), claude("marker").replace("\"marker\"", "42"))) {
            assertFailsWith<CloudFailure> { AnthropicProvider.parseResponse(body) }
        }
    }

    @Test fun geminiKeepsTheKeyOutOfTheUrlAndValidatesTheModelPathSegment() = runBlocking {
        lateinit var sent: Request
        val provider = GeminiProvider({ Secret("offline-test-token".toByteArray()) }, answering(gemini("Hello")) { sent = it })
        assertEquals("Hello", provider.generate(request("gemini-test-1.0")) {}.text)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-test-1.0:generateContent", sent.url.toString())
        assertEquals("offline-test-token", sent.header("x-goog-api-key"))
        assertNull(sent.header("Authorization"))
        assertFalse(sent.url.toString().contains("offline-test-token"))
        val json = Json.parseToJsonElement(Buffer().also { sent.body!!.writeTo(it) }.readUtf8()).jsonObject
        assertEquals(setOf("systemInstruction", "contents", "generationConfig"), json.keys)
        assertEquals("Hallo", json.getValue("contents").jsonArray.single().jsonObject.getValue("parts").jsonArray
            .single().jsonObject.getValue("text").jsonPrimitive.content)
        assertEquals(1, json.getValue("generationConfig").jsonObject.getValue("candidateCount").jsonPrimitive.int)
        var calls = 0
        val guarded = GeminiProvider({ Secret("t".toByteArray()) }, Call.Factory { calls++; error("must not run") })
        for (model in listOf("x/../y", "a:b", "A-model", "a..b", "a b", "a?key=1", "a#b")) {
            assertEquals(CloudError.INVALID_REQUEST, assertFailsWith<CloudFailure> { guarded.generate(request(model)) {} }.error)
        }
        assertEquals(0, calls)
    }

    @Test fun geminiAcceptsOnlyFinishedTextAndNeverThoughtsOrBlockedOutput() {
        assertEquals("Grüße 😀", GeminiProvider.parseResponse(gemini("Grüße 😀")).text)
        for (reason in listOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT")) {
            assertEquals(CloudError.REFUSED, assertFailsWith<CloudFailure> { GeminiProvider.parseResponse(gemini("x", reason)) }.error)
        }
        val blocked = buildJsonObject { put("promptFeedback", buildJsonObject { put("blockReason", "SAFETY") }) }.toString()
        assertEquals(CloudError.REFUSED, assertFailsWith<CloudFailure> { GeminiProvider.parseResponse(blocked) }.error)
        val twoCandidates = gemini("x").replace("\"candidates\":[", "\"candidates\":[{\"finishReason\":\"STOP\"},")
        val functionCall = gemini("x").replace("{\"text\":\"x\"}", "{\"functionCall\":{\"name\":\"n\"}}")
        for (body in listOf("{}", "[broken", gemini(""), gemini("x", "MAX_TOKENS"), gemini("x", "OTHER"), twoCandidates,
            functionCall, gemini("x".repeat(16385)), gemini("marker").replace("\"marker\"", "42"))) {
            assertFailsWith<CloudFailure> { GeminiProvider.parseResponse(body) }
        }
    }

    @Test fun textOnlyProvidersRefuseWorkflowDraftsBeforeAnyCall() = runBlocking {
        var calls = 0
        val factory = Call.Factory { calls++; error("must not run") }
        val secret = { Secret("t".toByteArray()) }
        for (provider in listOf(AnthropicProvider(secret, factory), GeminiProvider(secret, factory))) {
            assertEquals(CloudError.INVALID_REQUEST, assertFailsWith<CloudFailure> {
                provider.generate(request("model-1", ModelOutputFormat.WORKFLOW_DRAFT)) {} }.error)
        }
        assertEquals(0, calls)
    }

    @Test fun httpErrorsStayNeutralForEveryProvider() = runBlocking {
        val secret = { Secret("t".toByteArray()) }
        for ((code, error) in listOf(401 to CloudError.AUTHENTICATION, 429 to CloudError.RATE_LIMIT,
            400 to CloudError.INVALID_REQUEST, 529 to CloudError.SERVICE)) {
            val body = "{\"error\":{\"message\":\"secret-shaped provider detail\"}}"
            for (provider in listOf(AnthropicProvider(secret, answering(body, code)), GeminiProvider(secret, answering(body, code)))) {
                val failure = assertFailsWith<CloudFailure> { provider.generate(request("model-1")) {} }
                assertEquals(error, failure.error)
                assertFalse(failure.message!!.contains("secret"))
            }
        }
    }

    @Test fun eachProviderHasItsOwnSlotModelAndNameAndLocalHasNone() {
        assertEquals(listOf(WorkflowProvider.OPENAI, WorkflowProvider.CLAUDE, WorkflowProvider.GEMINI), CloudProviders.SUPPORTED)
        assertEquals(3, CloudProviders.SUPPORTED.map { CloudProviders.slot(it) }.toSet().size)
        assertEquals(CredentialSlot.ANTHROPIC, CloudProviders.slot(WorkflowProvider.CLAUDE))
        assertFails { CloudProviders.slot(WorkflowProvider.LOCAL) }
        assertFailsWith<CloudFailure> { CloudProviders(app).get(WorkflowProvider.LOCAL) }
        val settings = ModelSettings(app)
        assertEquals(WorkflowProvider.OPENAI, settings.provider)
        assertEquals(ModelSettings.DEFAULT_MODEL, settings.model(WorkflowProvider.OPENAI))
        assertEquals("claude-haiku-5-5", settings.model(WorkflowProvider.CLAUDE))
        assertEquals("", settings.model(WorkflowProvider.GEMINI)) // no unchecked default
        settings.setModel(WorkflowProvider.GEMINI, "gemini-test-1.0")
        settings.setProvider(WorkflowProvider.GEMINI)
        assertEquals(WorkflowProvider.GEMINI, ModelSettings(app).provider)
        assertEquals("gemini-test-1.0", ModelSettings(app).model(WorkflowProvider.GEMINI))
        assertEquals(ModelSettings.DEFAULT_MODEL, ModelSettings(app).openAiModel) // other providers untouched
        assertFails { settings.setModel(WorkflowProvider.GEMINI, "a:b") }
        assertFails { settings.setProvider(WorkflowProvider.LOCAL) }
        assertFails { settings.model(WorkflowProvider.LOCAL) }
    }
}
