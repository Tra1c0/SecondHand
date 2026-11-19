package dev.personal.secondhand.companion

/** One model bundle the spike may download: a zip of one AI Hub release, imported into the SDK's store under [modelName]. */
data class Candidate(
    /** The name in the SDK's model store ("local/..."): the key of the import, no longer the AI Hub name. */
    val modelName: String,
    /** The AI Hub release the zip belongs to ("v0.66.0"). */
    val release: String,
    /** The QAIRT version (major.minor) the bundle is built with, from the release's own list. */
    val expectedQairt: String,
    val zipUrl: String,
    /** The exact size of the zip on Qualcomm's server (HEAD request); the download is checked against it. */
    val zipBytes: Long,
    /** About how many bytes the unpacked model takes in the SDK's store (for the space check). */
    val unpackedBytes: Long,
    val title: String,
    val sizeText: String,
    /** What is different from the other candidates, in the words the confirmation dialog shows. */
    val difference: String,
) {
    val id: String get() = "$modelName|$release"
    val zipFileName: String get() = zipUrl.substringAfterLast('/')
}

// Which bundle to download, and what to do when a download fails (stages 80, 80b, 80c, 80d). Findings of the device
// runs: a pull with chipset SM8750 fails with -100015 (Qualcomm's chip list knows only sm8750-ac); the newest bundle
// (release v0.67.0, QAIRT 2.51) downloads but SDK 0.10.0 cannot read its metadata.json ("missing 'model_files'
// object": since v0.67.0 `model_files` is a list); GENIEX_AIHUBVERSION did not pin an older release. The releases
// v0.65.0 and v0.66.0 use the object form and were built with QAIRT 2.50.0, the version of the SDK's runtime
// (2.50.40). The app therefore downloads the zip of the release itself (resumable HTTPS from Qualcomm's S3 bucket,
// checked against the known byte count and the zip's CRC32s) and imports it through the SDK's documented LOCALFS
// route. Never started on its own: every download is one explicit tap with its own dialog.
object PullPlanner {
    private const val S3 = "https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models"
    const val MATCHING_RELEASE = "v0.66.0"

    val INSTRUCT_V66 = Candidate(
        modelName = "local/qwen3-4b-instruct-2507-v066",
        release = MATCHING_RELEASE,
        expectedQairt = "2.50",
        zipUrl = "$S3/qwen3_4b_instruct_2507/releases/v0.66.0/qwen3_4b_instruct_2507-geniex_qairt-w4a16-qualcomm_snapdragon_8_elite_for_galaxy.zip",
        zipBytes = 2_659_737_495L,
        unpackedBytes = 3_400_000_000L,
        title = "Qwen3-4B-Instruct-2507, Ausgabe v0.66.0 (QAIRT 2.50)",
        sizeText = "etwa 2,7 GB",
        difference = "Das ist dasselbe Modell wie das zuletzt geladene, aber die ältere Ausgabe v0.66.0. Sie ist mit QAIRT 2.50.0 gebaut, der Version " +
            "der Laufzeit in dieser App (2.50.40), und hat das Metadaten-Format, das das SDK lesen kann (die neuere Ausgabe v0.67.0 hat es nicht). " +
            "Das Paket ist für die Galaxy-Variante des Chips gebaut. Es kann scheitern.",
    )
    val QWEN3_V66 = Candidate(
        modelName = "local/qwen3-4b-v066",
        release = MATCHING_RELEASE,
        expectedQairt = "2.50",
        zipUrl = "$S3/qwen3_4b/releases/v0.66.0/qwen3_4b-geniex_qairt-w4a16-qualcomm_snapdragon_8_elite_for_galaxy.zip",
        zipBytes = 2_660_599_539L,
        unpackedBytes = 3_400_000_000L,
        title = "Qwen3-4B, Ausgabe v0.66.0 (QAIRT 2.50)",
        sizeText = "etwa 2,7 GB",
        difference = "Das ist ein ANDERES Modell (das ursprünglich gewünschte Qwen3-4B, es kann denken; das Denken wird abgeschaltet), wieder die " +
            "Ausgabe v0.66.0 mit QAIRT 2.50.0, also noch einmal etwa 2,7 GB Download. Auch dieses Paket ist für die Galaxy-Variante des Chips gebaut. " +
            "Es kann scheitern.",
    )

    /** Best evidence first. */
    val CANDIDATES = listOf(INSTRUCT_V66, QWEN3_V66)

    /** Names the earlier app versions (0.2 and 0.3) used for their SDK pulls; the bundles may still be on the phone. */
    val LEGACY_MODEL_NAMES = listOf("ai-hub-models/Qwen3-4B-Instruct-2507", "ai-hub-models/Qwen3-4B")

    /** Every name a bundle of this app can have in the SDK's store, preferred first. */
    val ALL_MODEL_NAMES: List<String> = CANDIDATES.map { it.modelName } + LEGACY_MODEL_NAMES

    fun byId(id: String?): Candidate? = CANDIDATES.firstOrNull { it.id == id }

    /** The candidate a bundle on the phone is: the same store name and the QAIRT version (major.minor) the candidate stands for. */
    fun matching(modelName: String?, bundleQairt: String?): Candidate? =
        CANDIDATES.firstOrNull { it.modelName == modelName && it.expectedQairt == QairtVersion.majorMinor(bundleQairt) }

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

    fun adviceAfterDownloadFailure(kind: DownloadException.Kind, httpStatus: Int?): Advice = when (kind) {
        DownloadException.Kind.CANCELLED -> Advice(giveUp = false, repeatUseful = true, deleteFirst = false, cancelled = true)
        DownloadException.Kind.HTTP_STATUS ->
            if (httpStatus == 403 || httpStatus == 404 || httpStatus == 410) Advice(giveUp = true, repeatUseful = false, deleteFirst = false, cancelled = false)
            else Advice(giveUp = false, repeatUseful = true, deleteFirst = false, cancelled = false)
        DownloadException.Kind.ZIP_INVALID, DownloadException.Kind.SIZE_MISMATCH ->
            Advice(giveUp = false, repeatUseful = true, deleteFirst = false, cancelled = false)
        DownloadException.Kind.NETWORK, DownloadException.Kind.NO_SPACE ->
            Advice(giveUp = false, repeatUseful = true, deleteFirst = false, cancelled = false)
    }

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
