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
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Stage 59: the size, token and time budgets of the three providers, cut-off answers that are never
 * a result, and a cancelled run that really cancels the call. Only fake HTTP doubles, no network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LongTextBudgetTest {
    private class TestCall(private val http: Request, private val respond: (Call, Callback) -> Unit) : Call {
        @Volatile private var cancelled = false
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

    private fun ok(http: Request, body: String) = Response.Builder().request(http).protocol(Protocol.HTTP_1_1)
        .code(200).message("OK").body(body.toResponseBody()).build()

    private fun factory(sent: MutableList<Request>, body: String) = Call.Factory { http ->
        sent += http
        TestCall(http) { call, callback -> callback.onResponse(call, ok(http, body)) }
    }

    private fun bodyOf(request: Request) = Json.parseToJsonElement(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).jsonObject

    private fun openAi(text: String) = buildJsonObject {
        put("status", "completed")
        put("output", buildJsonArray { add(buildJsonObject {
            put("type", "message"); put("role", "assistant"); put("status", "completed")
            put("content", buildJsonArray { add(buildJsonObject { put("type", "output_text"); put("text", text) }) })
        }) })
    }.toString()

    private fun claude(text: String, stop: String = "end_turn") = buildJsonObject {
        put("type", "message"); put("role", "assistant"); put("stop_reason", stop)
        put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) })
    }.toString()

    private fun gemini(text: String, finish: String = "STOP", thought: String? = null) = buildJsonObject {
        put("candidates", buildJsonArray { add(buildJsonObject {
            put("finishReason", finish)
            put("content", buildJsonObject { put("parts", buildJsonArray {
                if (thought != null) add(buildJsonObject { put("text", thought); put("thought", true) })
                add(buildJsonObject { put("text", text) })
            }) })
        }) })
    }.toString()

    private fun request(chars: Int, format: ModelOutputFormat = ModelOutputFormat.TEXT) =
        ModelRequest("gpt-6-luna", "Correct spelling only.", "x".repeat(chars), format)

    private val key = { Secret("offline-test-token".toByteArray()) }

    // --- request bodies ---

    @Test fun openAiOutputTokensScaleWithTheInputAndDraftsKeepTheirBudget() = runBlocking {
        for ((chars, expected) in listOf(5 to 8192, 8192 to 8192, 20_000 to 12_048, 32_768 to 16_384)) {
            val sent = mutableListOf<Request>()
            assertEquals("ok", OpenAiProvider(key, factory(sent, openAi("ok"))).generate(request(chars)) {}.text)
            assertEquals(expected, bodyOf(sent.single()).getValue("max_output_tokens").jsonPrimitive.int, "chars=$chars")
        }
        val sent = mutableListOf<Request>()
        val proposal = """{"name":"Clean","steps":[{"id":"clean","input":"selection","condition":null,"kind":"local","action":"local.clean_spaces"}]}"""
        OpenAiProvider(key, factory(sent, openAi(proposal))).generate(WorkflowBuilderContract.request("Clean", "approved-model")) {}
        assertEquals(4096, bodyOf(sent.single()).getValue("max_output_tokens").jsonPrimitive.int)
    }

    @Test fun geminiAndAnthropicSendAtLeastEightThousandAndScaleBeyondIt() = runBlocking {
        for ((chars, expected) in listOf(5 to 8192, 10_000 to 8192, 20_000 to 12_048, 32_768 to 16_384)) {
            val geminiSent = mutableListOf<Request>()
            assertEquals("ok", GeminiProvider(key, factory(geminiSent, gemini("ok"))).generate(
                ModelRequest("gemini-3.8-flash-lite", "Correct.", "x".repeat(chars))) {}.text)
            val config = bodyOf(geminiSent.single()).getValue("generationConfig").jsonObject
            assertEquals(expected, config.getValue("maxOutputTokens").jsonPrimitive.int, "gemini chars=$chars")
            // the long text really is in the body, in the one user part
            assertEquals(chars, bodyOf(geminiSent.single()).getValue("contents").jsonArray.single().jsonObject
                .getValue("parts").jsonArray.single().jsonObject.getValue("text").jsonPrimitive.content.length)
            val claudeSent = mutableListOf<Request>()
            AnthropicProvider(key, factory(claudeSent, claude("ok"))).generate(request(chars)) {}
            assertEquals(expected, bodyOf(claudeSent.single()).getValue("max_tokens").jsonPrimitive.int, "claude chars=$chars")
        }
    }

    @Test fun inputUpToThirtyTwoKiBIsSentAndOneMoreIsRefusedBeforeAnyCall() = runBlocking {
        var created = 0
        val provider = GeminiProvider(key, Call.Factory { created++; error("must not run") })
        val failure = assertFailsWith<CloudFailure> { provider.generate(request(ModelBudget.MAX_INPUT_CHARS + 1)) {} }
        assertEquals(CloudError.INVALID_REQUEST, failure.error)
        assertEquals(0, created)
        val sent = mutableListOf<Request>()
        GeminiProvider(key, factory(sent, gemini("ok"))).generate(request(ModelBudget.MAX_INPUT_CHARS)) {}
        assertEquals(1, sent.size)
    }

    // --- answers ---

    @Test fun aResultUpToSixtyFiveThousandCharactersIsAcceptedAndOneMoreIsTooLarge() {
        val full = "y".repeat(65_536)
        assertEquals(65_536, OpenAiProvider.parseResponse(openAi(full)).text.length)
        assertEquals(65_536, AnthropicProvider.parseResponse(claude(full)).text.length)
        assertEquals(65_536, GeminiProvider.parseResponse(gemini(full)).text.length)
        for (body in listOf(
            { OpenAiProvider.parseResponse(openAi("y".repeat(65_537))) },
            { AnthropicProvider.parseResponse(claude("y".repeat(65_537))) },
            { GeminiProvider.parseResponse(gemini("y".repeat(65_537))) },
        )) assertEquals(CloudError.TOO_LARGE, assertFailsWith<CloudFailure> { body() }.error)
    }

    @Test fun cutOffAnswersAreNeverAResultOfAnyProvider() {
        fun error(block: () -> Unit) = assertFailsWith<CloudFailure> { block() }.error
        val incomplete = buildJsonObject {
            put("status", "incomplete")
            put("incomplete_details", buildJsonObject { put("reason", "max_output_tokens") })
            put("output", buildJsonArray { add(buildJsonObject {
                put("type", "message"); put("role", "assistant"); put("status", "incomplete")
                put("content", buildJsonArray { add(buildJsonObject { put("type", "output_text"); put("text", "partial answ") }) })
            }) })
        }.toString()
        assertEquals(CloudError.CUT_OFF, error { OpenAiProvider.parseResponse(incomplete) })
        // reasoning used the whole budget: no message at all
        assertEquals(CloudError.CUT_OFF, error { OpenAiProvider.parseResponse(
            """{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[{"type":"reasoning"}]}""") })
        // an incomplete answer the provider filtered is a refusal, not a cut-off
        assertEquals(CloudError.REFUSED, error { OpenAiProvider.parseResponse(
            """{"status":"incomplete","incomplete_details":{"reason":"content_filter"},"output":[]}""") })
        assertEquals(CloudError.CUT_OFF, error { AnthropicProvider.parseResponse(claude("partial", "max_tokens")) })
        assertEquals(CloudError.CUT_OFF, error { GeminiProvider.parseResponse(gemini("partial answ", "MAX_TOKENS")) })
        // thinking used the whole budget: MAX_TOKENS without any content
        assertEquals(CloudError.CUT_OFF, error { GeminiProvider.parseResponse(
            """{"candidates":[{"finishReason":"MAX_TOKENS"}]}""") })
    }

    @Test fun geminiThoughtsAreNotTheResultAndBlockedAnswersAreRefusals() {
        assertEquals("the answer", GeminiProvider.parseResponse(gemini("the answer", thought = "let me think")).text)
        for (reason in listOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII"))
            assertEquals(CloudError.REFUSED, assertFailsWith<CloudFailure> {
                GeminiProvider.parseResponse("""{"candidates":[{"finishReason":"$reason"}]}""") }.error, reason)
        assertEquals(CloudError.REFUSED, assertFailsWith<CloudFailure> {
            GeminiProvider.parseResponse("""{"promptFeedback":{"blockReason":"SAFETY"}}""") }.error)
        assertEquals(CloudError.INVALID_RESPONSE, assertFailsWith<CloudFailure> {
            GeminiProvider.parseResponse(gemini("x", "OTHER")) }.error)
    }

    @Test fun theCutOffErrorHasItsOwnMessagesAndAQuestionAsksForANarrowerOne() {
        val cloud = CloudError.CUT_OFF.cloudMessage()
        assertNotEquals(CloudError.TOO_LARGE.cloudMessage(), cloud)
        assertNotEquals(CloudError.NETWORK.cloudMessage(), cloud)
        // review of stage 59: for a question "select less text" makes no sense
        val ask = CloudError.CUT_OFF.cloudMessage(AiTextAction.ASK)
        assertNotEquals(cloud, ask)
        assertEquals(CloudError.NETWORK.cloudMessage(), CloudError.NETWORK.cloudMessage(AiTextAction.ASK))
        // a typed translation has its own text too (nothing is marked), and so has a refused request
        val translate = CloudError.CUT_OFF.cloudMessage(AiTextAction.TRANSLATE_STYLED)
        assertNotEquals(cloud, translate); assertNotEquals(ask, translate)
        assertNotEquals(CloudError.NETWORK.cloudMessage(), CloudError.INVALID_REQUEST.cloudMessage())
        assertNotEquals(helium314.keyboard.latin.R.string.agent_cloud_invalid_request, CloudError.INVALID_REQUEST.cloudMessage())
    }

    @Test fun aCutOffAnswerStaysUnknownInTheJournalBecauseTheRequestWasPaidFor() = runBlocking {
        val journal = TestRunJournal()
        val provider = GeminiProvider(key, factory(mutableListOf(), gemini("partial", "MAX_TOKENS")))
        val failure = assertFailsWith<CloudFailure> {
            JournaledModelCall(journal).execute(AiTextAction.CORRECT, request(20), provider) {}
        }
        assertEquals(CloudError.CUT_OFF, failure.error)
        assertEquals(listOf(ActionRunState.UNKNOWN), journal.modelStates.values.toList())
    }

    // --- time ---

    @Test fun theClientWaitsLongEnoughForANonStreamedAnswer() {
        val client = CloudHttpClient.newBuilder().build() // not the shared instance: others assert it is never initialized
        assertEquals(120_000, client.readTimeoutMillis)
        assertEquals(150_000, client.callTimeoutMillis)
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(10_000, client.writeTimeoutMillis)
        // unchanged guards
        assertFalse(client.followRedirects); assertFalse(client.followSslRedirects); assertFalse(client.retryOnConnectionFailure)
        assertEquals(1, client.dispatcher.maxRequests)
        assertEquals(TimeUnit.SECONDS.toMillis(120), ModelBudget.READ_TIMEOUT_MS)
    }

    @Test fun theTransportEndsAHangingCallWithATimeoutAndCancelsIt() = runBlocking {
        lateinit var call: TestCall
        val started = CompletableDeferred<Unit>()
        val transport = CloudTransport(key, Call.Factory { http -> TestCall(http) { _, _ -> started.complete(Unit) }.also { call = it } }, 150)
        val failure = assertFailsWith<CloudFailure> {
            transport.post(request(5), { "{}" }, { _, body -> Request.Builder().url("https://example.invalid/").post(body).build() },
                { ModelResponse(it) })
        }
        assertEquals(CloudError.TIMEOUT, failure.error)
        assertTrue(started.isCompleted)
        assertTrue(call.isCanceled())
    }

    @Test fun aWorkflowMayTakeThreeHundredTenSecondsAndNotMore() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            ApprovedActionRunner(scope, { error("unused") }, { true }, TestRunJournal(), workflowTimeoutMillis = 310_000)
            assertFailsWith<IllegalArgumentException> {
                ApprovedActionRunner(scope, { error("unused") }, { true }, TestRunJournal(), workflowTimeoutMillis = 310_001)
            }
        } finally { scope.cancel() }
    }

    // --- cancel ---

    @Test fun cancellingARunningRequestCancelsTheActualCallAndPublishesNothing() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        lateinit var call: TestCall
        val provider = OpenAiProvider(key, Call.Factory { http -> TestCall(http) { _, _ -> started.complete(Unit) }.also { call = it } })
        val published = mutableListOf<Result<String>>()
        val session = EditorSession().apply { start() }
        val source = (ExecutionPolicy().capture(session.token!!, EditorAccess(isText = true), 0, 5, "Hallo") as CaptureResult.Captured).snapshot
        val runner = ApprovedActionRunner(scope, { provider }, { true }, TestRunJournal())
        try {
            assertTrue(runner.start(source, "gpt-6-luna", "Correct.") { published += it })
            runBlocking { withTimeout(10_000) { started.await() } }
            assertTrue(runner.isRunning)
            runner.cancel()
            assertFalse(runner.isRunning)
            runBlocking { withTimeout(10_000) { while (!call.isCanceled()) delay(10) } }
            assertTrue(call.isCanceled())
            assertTrue(published.isEmpty())
            // a new request may start at once
            assertTrue(runner.start(source, "gpt-6-luna", "Correct.") { published += it })
        } finally { runner.cancel(); scope.cancel() }
    }
}
