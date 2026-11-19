package dev.personal.secondhand.companion

import android.content.Context
import com.geniex.sdk.GenieXSdk
import com.geniex.sdk.LlmWrapper
import com.geniex.sdk.ModelManagerWrapper
import com.geniex.sdk.bean.ChatMessage
import com.geniex.sdk.bean.ComputeUnitValue
import com.geniex.sdk.bean.GenerationConfig
import com.geniex.sdk.bean.HubSource
import com.geniex.sdk.bean.LlmCreateInput
import com.geniex.sdk.bean.LlmStreamResult
import com.geniex.sdk.bean.ModelConfig
import com.geniex.sdk.bean.ModelPullInput
import com.geniex.sdk.bean.RuntimeIdValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext

// The ONE file that talks to the GenieX SDK (stage 80). Everything else in the app works with the
// SDK-free types below, so the app logic is unit-tested with a fake of NpuPort (docs/ARCHITECTURE.md ->
// Begleit-App). SDK strings (error codes and messages) are passed on unchanged; no user text is logged.

/** What the SDK reported after its start (all optional: they are only for the report). */
data class InitInfo(
    val sdkVersion: String?,
    val qairtPluginVersion: String?,
    val qairtRuntimePath: String?,
    /** The chipset id the SDK reads from the phone itself (local probe, no network). */
    val detectedChipset: String?,
)

/** One model of Qualcomm's AI Hub list with the chipsets it has a ready NPU bundle for. */
data class HubEntry(val name: String, val chipsets: List<String>)

/** The runtime the SDK's model manager recorded for the pulled bundle ("qairt" = NPU). */
data class LoadInfo(val manifestRuntime: String?, val modelDir: String?)

sealed interface PullUpdate {
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : PullUpdate
    data object Completed : PullUpdate
    data class Failed(val code: Int, val message: String?) : PullUpdate
}

/** The SDK's profiling data of one finished generation (all numbers as given by the SDK). */
data class GenProfile(
    val ttftMs: Double,
    val promptTimeMs: Double,
    val decodeTimeMs: Double,
    val promptTokens: Long,
    val generatedTokens: Long,
    val prefillSpeed: Double,
    val decodingSpeed: Double,
    val stopReason: String?,
)

sealed interface GenEvent {
    data class Token(val text: String) : GenEvent
    data class Done(val profile: GenProfile) : GenEvent
    data class Failed(val error: Throwable) : GenEvent
}

/** An SDK failure, reduced to what the report shows: the original class, the SDK's code and text. */
class NpuException(
    val code: Int?,
    val sdkMessage: String?,
    val originalClass: String,
    cause: Throwable? = null,
) : Exception(sdkMessage ?: originalClass, cause)

/** Everything the app needs from the SDK. The real implementation is GeniexAdapter below. */
interface NpuPort {
    /** Starts the SDK (idempotent). Throws NpuException; on a device without the NPU libraries this fails. */
    suspend fun init(): InitInfo

    /** True when a complete model bundle of this name is in the SDK's model store. */
    suspend fun isModelPresent(modelName: String): Boolean

    /** Downloads the model for the given chipset id (with [listHub], the only call that uses the network). */
    fun pull(modelName: String, chipset: String): Flow<PullUpdate>

    /** Removes the model (also a partial download, as far as the SDK does) from the SDK's store; returns the SDK's result code (0 = ok). */
    suspend fun deleteModel(modelName: String): Int

    /** Asks Qualcomm's release list which models have a ready NPU bundle for which chipsets (a small list, no model data). */
    suspend fun listHub(): List<HubEntry>

    /** Loads the model on the NPU only (runtime qairt). Throws NpuException. */
    suspend fun load(modelName: String): LoadInfo

    /** Templates the two messages (thinking off) and streams the answer. Never throws for SDK errors; emits Failed. */
    fun generate(systemInstruction: String, userText: String, maxTokens: Int): Flow<GenEvent>

    suspend fun stop()

    suspend fun unload()
}

/** Constants of the SDK calls. */
object GeniexConstants {
    const val RUNTIME_QAIRT = "qairt"
    const val CHIPSET_GALAXY = "SM8750-AC"
}

@OptIn(ExperimentalCoroutinesApi::class)
class GeniexAdapter(context: Context) : NpuPort {
    private val appContext: Context = context.applicationContext
    private var llm: LlmWrapper? = null

    private fun wrap(t: Throwable): NpuException = when (t) {
        is NpuException -> t
        is ModelManagerWrapper.GenieXModelError -> NpuException(t.code, t.message, t.javaClass.name, t)
        else -> NpuException(null, t.message, t.javaClass.name, t)
    }

    override suspend fun init(): InitInfo = withContext(Dispatchers.IO) {
        try {
            val done = CompletableDeferred<String?>()
            // may throw UnsatisfiedLinkError / NoClassDefFoundError on a device without the NPU libraries
            GenieXSdk.getInstance().init(appContext, object : GenieXSdk.InitCallback {
                override fun onSuccess() {
                    done.complete(null)
                }

                override fun onFailure(reason: String) {
                    done.complete(reason)
                }
            })
            val failure = done.await()
            if (failure != null) throw NpuException(null, failure, "GenieXSdk.InitCallback.onFailure")
            val sdk = GenieXSdk.getInstance()
            InitInfo(
                sdkVersion = runCatching { sdk.getSdkVersion() }.getOrNull(),
                qairtPluginVersion = runCatching { sdk.getPluginVersion(GeniexConstants.RUNTIME_QAIRT) }.getOrNull(),
                qairtRuntimePath = runCatching { sdk.getQairtRuntimePath() }.getOrNull(),
                detectedChipset = runCatching { ModelManagerWrapper.detectChipset(true) }.getOrNull(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw wrap(t)
        }
    }

    override suspend fun isModelPresent(modelName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            ModelManagerWrapper.getPaths(modelName) != null
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw wrap(t)
        }
    }

    override fun pull(modelName: String, chipset: String): Flow<PullUpdate> =
        ModelManagerWrapper.pullFlow(
            ModelPullInput(model_name = modelName, hub = HubSource.AUTO, chipset = chipset),
        ).transformWhile { event ->
            when (event) {
                is ModelManagerWrapper.PullEvent.Progress -> {
                    val total = event.files.sumOf { if (it.total_bytes > 0) it.total_bytes else 0L }
                    val done = event.files.sumOf { it.downloaded_bytes }
                    emit(PullUpdate.Progress(done, total))
                    true
                }
                is ModelManagerWrapper.PullEvent.Completed -> {
                    emit(PullUpdate.Completed)
                    false
                }
                is ModelManagerWrapper.PullEvent.Error -> {
                    emit(PullUpdate.Failed(event.code, event.message))
                    false
                }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun deleteModel(modelName: String): Int = withContext(Dispatchers.IO) {
        try {
            ModelManagerWrapper.remove(modelName)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw wrap(t)
        }
    }

    override suspend fun listHub(): List<HubEntry> = withContext(Dispatchers.IO) {
        try {
            ModelManagerWrapper.listHubModels().map { HubEntry(it.name, it.chipsets.toList()) }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw wrap(t)
        }
    }

    override suspend fun load(modelName: String): LoadInfo = withContext(Dispatchers.IO) {
        try {
            val paths = ModelManagerWrapper.getPaths(modelName)
                ?: throw NpuException(null, "Modell nicht im Modellspeicher des SDK", "NoModel")
            // qairt rejects nCtx != 0 and nGpuLayers != 0 (PARAM_NOT_SUPPORTED), but 0.10.0's defaults are
            // 2048 and -1: set both to 0 explicitly. runtime qairt + compute unit NPU: no CPU/GPU path.
            val input = LlmCreateInput(
                model_path = paths.model_path,
                tokenizer_path = paths.tokenizer_path,
                config = ModelConfig(nCtx = 0, nGpuLayers = 0),
                runtime_id = RuntimeIdValue.QAIRT.value,
                compute_unit = ComputeUnitValue.NPU.value,
            )
            val wrapper = LlmWrapper.builder().llmCreateInput(input).build().getOrThrow()
            llm = wrapper
            LoadInfo(manifestRuntime = paths.runtime_id, modelDir = paths.model_dir)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw wrap(t)
        }
    }

    override fun generate(systemInstruction: String, userText: String, maxTokens: Int): Flow<GenEvent> = flow {
        val wrapper = llm ?: throw NpuException(null, "Modell nicht geladen", "NotLoaded")
        // the chat-templated prompt is mandatory (raw text gives degenerate output); thinking off
        val messages = arrayOf(ChatMessage("system", systemInstruction), ChatMessage("user", userText))
        val templated = wrapper.applyChatTemplate(messages, null, false).getOrThrow()
        emitAll(
            wrapper.generateStreamFlow(
                prompt = templated.formattedText,
                config = GenerationConfig(maxTokens = maxTokens),
            ).transformWhile { r ->
                when (r) {
                    is LlmStreamResult.Token -> {
                        emit(GenEvent.Token(r.text))
                        true
                    }
                    is LlmStreamResult.Completed -> {
                        val p = r.profile
                        emit(
                            GenEvent.Done(
                                GenProfile(
                                    ttftMs = p.ttftMs,
                                    promptTimeMs = p.promptTimeMs,
                                    decodeTimeMs = p.decodeTimeMs,
                                    promptTokens = p.promptTokens,
                                    generatedTokens = p.generatedTokens,
                                    prefillSpeed = p.prefillSpeed,
                                    decodingSpeed = p.decodingSpeed,
                                    stopReason = p.stopReason,
                                ),
                            ),
                        )
                        false
                    }
                    is LlmStreamResult.Error -> {
                        emit(GenEvent.Failed(wrap(r.throwable)))
                        false
                    }
                }
            },
        )
    }.catch { emit(GenEvent.Failed(wrap(it))) }.flowOn(Dispatchers.IO)

    override suspend fun stop() {
        withContext(Dispatchers.IO) {
            runCatching { llm?.stopStream() }
        }
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            val wrapper = llm
            llm = null
            if (wrapper != null) {
                runCatching { wrapper.stopStream() }
                runCatching { wrapper.destroy() }
            }
        }
    }
}
