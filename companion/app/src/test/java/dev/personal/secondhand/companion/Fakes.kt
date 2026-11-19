package dev.personal.secondhand.companion

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeClock(var now: Long = 0) : Clock {
    override fun nowMs(): Long = now
}

class FakeProbe(var temp: Double? = 31.0) : Probe {
    private var call = 0
    override fun memory(): MemSnapshot {
        call++
        // the second call (after the run) sees more memory in use
        return MemSnapshot(100L * 1048576 * call, 200L * 1048576 * call, 6000L * 1048576 / call)
    }

    override fun batteryTempC(): Double? = temp?.also { temp = it + 1.5 }
    var free = 90L * 1073741824
    var network = "WLAN"
    override fun freeStorageBytes(): Long = free
    override fun networkType(): String = network
}

class FakeTracker : StepTracker {
    val events = mutableListOf<String>()
    override fun begin(step: String) { events += "begin:$step" }
    override fun end(step: String) { events += "end:$step" }
    override fun failed(step: String, errorClass: String) { events += "failed:$step:$errorClass" }
}

/** A scripted NpuPort: records the calls and replays what the test sets. */
class FakePort(private val clock: FakeClock) : NpuPort {
    val calls = mutableListOf<String>()
    var initError: Throwable? = null
    /** Model names that are in the store. */
    val present = mutableSetOf(PullPlanner.INSTRUCT_GALAXY.modelName)
    var hub: List<HubEntry> = listOf(HubEntry("qwen3_4b", listOf("qualcomm-snapdragon-8-elite-for-galaxy")))
    var hubError: Throwable? = null
    val loadedNames = mutableListOf<String>()
    val deletedNames = mutableListOf<String>()
    val deleteResults = mutableMapOf<String, Int>()
    var pullUpdates: List<PullUpdate> = listOf(PullUpdate.Completed)
    var pullThrows: Throwable? = null
    var loadInfo = LoadInfo("qairt", "/models/x")
    var loadError: Throwable? = null
    var loadTakesMs = 4000L
    var tokens = listOf("Wir ", "haben ", "gestern ", "einen ", "langen ", "Spaziergang ", "gemacht ", "und ", "danach ", "Kaffee ", "getrunken.")
    var tokenGapMs = 50L
    var profile: GenProfile? = GenProfile(310.0, 280.0, 5000.0, 40, 11, 142.0, 2.2, "stop")
    var generateEvents: List<GenEvent>? = null
    var deleteResult = 0
    val pulledChipsets = mutableListOf<String>()

    override suspend fun init(): InitInfo {
        calls += "init"
        initError?.let { throw it }
        return InitInfo("0.10.0", "2.50", "/data/qairt", "SM8750")
    }

    override suspend fun isModelPresent(modelName: String): Boolean { calls += "present:$modelName"; return modelName in present }

    override fun pull(modelName: String, chipset: String): Flow<PullUpdate> = flow {
        calls += "pull:$modelName:$chipset"
        pulledChipsets += chipset
        pullThrows?.let { throw it }
        pullUpdates.forEach { emit(it) }
    }

    override suspend fun deleteModel(modelName: String): Int {
        calls += "delete:$modelName"
        deletedNames += modelName
        return deleteResults[modelName] ?: deleteResult
    }

    override suspend fun listHub(): List<HubEntry> {
        calls += "listHub"
        hubError?.let { throw it }
        return hub
    }

    override suspend fun load(modelName: String): LoadInfo {
        calls += "load"
        loadedNames += modelName
        clock.now += loadTakesMs
        loadError?.let { throw it }
        return loadInfo
    }

    override fun generate(systemInstruction: String, userText: String, maxTokens: Int): Flow<GenEvent> = flow {
        calls += "generate:$maxTokens"
        clock.now += 300 // time to the first token
        generateEvents?.let { list -> list.forEach { emit(it) }; return@flow }
        tokens.forEach {
            emit(GenEvent.Token(it))
            clock.now += tokenGapMs
        }
        profile?.let { emit(GenEvent.Done(it)) }
    }

    override suspend fun stop() { calls += "stop" }
    override suspend fun unload() { calls += "unload" }
}
