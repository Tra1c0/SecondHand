package dev.personal.secondhand.companion

/** One model bundle the spike may pull: a model id and a chipset id for the SDK's pull. */
data class Candidate(
    val modelName: String,
    val chipset: String,
    val title: String,
    val sizeText: String,
    /** What is different from the other candidates, in the words the confirmation dialog shows. */
    val difference: String,
) {
    val id: String get() = "$modelName|$chipset"
}

// Which bundle to pull, and what to do when a pull fails (stages 80 and 80b). Qualcomm's release manifest
// (platform.json, read 2026-11-19) knows "sm8750" as NO chipset alias; the Snapdragon 8 Elite bundles of
// Qwen3-4B and Qwen3-4B-Instruct-2507 exist only for "qualcomm-snapdragon-8-elite-for-galaxy" (alias
// "sm8750-ac"). A pull with chipset SM8750 therefore always fails with CHIPSET_UNAVAILABLE (-100015), which the
// first run on the Xiaomi 15 showed. The list below is ordered by evidence; it is never pulled on its own:
// every pull is one explicit tap with its own dialog.
object PullPlanner {
    val INSTRUCT_GALAXY = Candidate(
        modelName = "ai-hub-models/Qwen3-4B-Instruct-2507",
        chipset = GeniexConstants.CHIPSET_GALAXY,
        title = "Qwen3-4B-Instruct-2507, Paket für SM8750-AC",
        sizeText = "etwa 2,7 GB",
        difference = "Das ist das Modell, das Qualcomms Android-Anleitung benutzt; es antwortet ohne Denkblock. " +
            "Das Paket ist für die Galaxy-Variante des Chips (SM8750-AC) gebaut, nicht für diese. Es kann scheitern.",
    )
    val QWEN3_GALAXY = Candidate(
        modelName = "ai-hub-models/Qwen3-4B",
        chipset = GeniexConstants.CHIPSET_GALAXY,
        title = "Qwen3-4B, Paket für SM8750-AC",
        sizeText = "etwa 2,7 GB",
        difference = "Das ist ein ANDERES Modell als zuvor (das ursprünglich gewünschte Qwen3-4B, es kann denken; das Denken " +
            "wird abgeschaltet), also noch einmal etwa 2,7 GB Download. Auch dieses Paket ist für die Galaxy-Variante " +
            "des Chips gebaut. Es kann scheitern, auch weil Qualcomms Anleitung nur das Instruct-Modell nennt.",
    )

    /** Best evidence first. */
    val CANDIDATES = listOf(INSTRUCT_GALAXY, QWEN3_GALAXY)

    fun byId(id: String?): Candidate? = CANDIDATES.firstOrNull { it.id == id }

    /** The candidate the next tap on button 1 would pull: the first one not given up. Null when all were given up. */
    fun current(abandoned: Set<String>): Candidate? = CANDIDATES.firstOrNull { it.id !in abandoned }

    /** The candidate after [after] that was not given up, or null. */
    fun next(after: Candidate, abandoned: Set<String>): Candidate? {
        val from = CANDIDATES.indexOfFirst { it.id == after.id }
        return CANDIDATES.drop(from + 1).firstOrNull { it.id !in abandoned }
    }

    /** What the screen offers after a failed pull. */
    data class Advice(
        /** The same candidate will fail the same way: give it up and do not offer a repeat. */
        val giveUp: Boolean,
        /** Offering the same candidate again makes sense (network, server, disk space, a damaged partial download). */
        val repeatUseful: Boolean,
        /** Cleaning up the partial download first is advised. */
        val deleteFirst: Boolean,
        /** True when the user cancelled: no error dialog at all. */
        val cancelled: Boolean,
    )

    fun adviceAfterPullFailure(code: Int?, message: String?): Advice = when {
        code == SdkCodes.CANCELLED -> Advice(giveUp = false, repeatUseful = true, deleteFirst = false, cancelled = true)
        code == SdkCodes.CHIPSET_UNAVAILABLE || code == SdkCodes.HUB_MODEL_NOT_FOUND ->
            Advice(giveUp = true, repeatUseful = false, deleteFirst = false, cancelled = false)
        code == SdkCodes.MODEL_INVALID -> Advice(giveUp = false, repeatUseful = true, deleteFirst = true, cancelled = false)
        code == SdkCodes.INSUFFICIENT_DISK_SPACE -> Advice(giveUp = false, repeatUseful = true, deleteFirst = false, cancelled = false)
        looksLikeChipsetUnavailable(message) -> Advice(giveUp = true, repeatUseful = false, deleteFirst = false, cancelled = false)
        else -> Advice(giveUp = false, repeatUseful = true, deleteFirst = false, cancelled = false)
    }

    /**
     * Fallback for an SDK error without a known code: the SDK's text says that no bundle exists. Strings from
     * libgeniex.so and the SDK's error.rs ("chipset ... not available for this model; supported: ...",
     * "No pre-compiled assets available for ..."), plus "not found" and an HTTP 403/404.
     */
    fun looksLikeChipsetUnavailable(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val m = message.lowercase()
        return "not available for this model" in m ||
            "no pre-compiled assets" in m ||
            "not found" in m ||
            "unsupported" in m ||
            "not supported" in m ||
            Regex("\\b(403|404)\\b").containsMatchIn(m)
    }
}

/** The SDK's error codes of the model manager (sdk/model-manager/src/ffi/types.rs in Qualcomm's GenieX repository). */
object SdkCodes {
    const val UNKNOWN = -100000
    const val INVALID_INPUT = -100001
    const val FILE_NOT_FOUND = -100004
    const val NETWORK = -100005
    const val CANCELLED = -100006
    const val NOT_INITIALIZED = -100007
    const val ALREADY_INITIALIZED = -100008
    const val AUTH = -100009
    const val HUB_MODEL_NOT_FOUND = -100010
    const val RATE_LIMITED = -100011
    const val HUB_SERVER = -100012
    const val MANIFEST_PARSE = -100014
    const val CHIPSET_UNAVAILABLE = -100015
    const val INSUFFICIENT_DISK_SPACE = -100017
    const val MODEL_INVALID = -100203

    private val NAMES = mapOf(
        UNKNOWN to "COMMON_UNKNOWN",
        INVALID_INPUT to "COMMON_INVALID_INPUT",
        FILE_NOT_FOUND to "COMMON_FILE_NOT_FOUND",
        NETWORK to "COMMON_NETWORK",
        CANCELLED to "COMMON_CANCELLED",
        NOT_INITIALIZED to "COMMON_NOT_INITIALIZED",
        ALREADY_INITIALIZED to "COMMON_ALREADY_INITIALIZED",
        AUTH to "COMMON_AUTH",
        HUB_MODEL_NOT_FOUND to "COMMON_HUB_MODEL_NOT_FOUND",
        RATE_LIMITED to "COMMON_RATE_LIMITED",
        HUB_SERVER to "COMMON_HUB_SERVER",
        MANIFEST_PARSE to "COMMON_MANIFEST_PARSE",
        CHIPSET_UNAVAILABLE to "COMMON_CHIPSET_UNAVAILABLE",
        INSUFFICIENT_DISK_SPACE to "COMMON_INSUFFICIENT_DISK_SPACE",
        MODEL_INVALID to "COMMON_MODEL_INVALID",
    )

    fun name(code: Int): String? = NAMES[code]
}
