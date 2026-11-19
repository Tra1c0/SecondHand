package dev.personal.secondhand.companion

// Which chipset id to pull, and what to do when a pull fails (stage 80). Qualcomm publishes the stock
// Qwen3-4B bundle only for the Galaxy variant of the Snapdragon 8 Elite ("SM8750-AC"); the Xiaomi 15
// reports plain "SM8750". The documented id is tried first; the Galaxy bundle is only ever OFFERED
// (second explicit tap, own dialog) and never pulled on its own.
object PullPlanner {
    sealed interface Offer {
        /** Plain SM8750: the documented request. */
        data object Primary : Offer

        /** SM8750-AC: built for another variant of the chip, may not work. */
        data object GalaxyBundle : Offer

        /** Both ids were already tried and failed. */
        data object Exhausted : Offer
    }

    sealed interface AfterFailure {
        /** The error is something else (network, storage, ...): show it, the same tap can be repeated. */
        data object ShowError : AfterFailure

        /** The SM8750 request found no bundle: offer SM8750-AC with its own dialog. */
        data object OfferGalaxyBundle : AfterFailure
    }

    /** The first id the next tap on button 1 would use, given the ids that failed (a failed pull or a failed load). */
    fun nextOffer(failedChipsets: Set<String>): Offer = when {
        GeniexConstants.CHIPSET_PRIMARY !in failedChipsets -> Offer.Primary
        GeniexConstants.CHIPSET_GALAXY !in failedChipsets -> Offer.GalaxyBundle
        else -> Offer.Exhausted
    }

    fun afterPullFailure(chipset: String, code: Int?, message: String?): AfterFailure =
        if (chipset == GeniexConstants.CHIPSET_PRIMARY && looksLikeChipsetUnavailable(message)) {
            AfterFailure.OfferGalaxyBundle
        } else {
            AfterFailure.ShowError
        }

    /**
     * True when the SDK's text says that no bundle exists for the id. The strings come from libgeniex.so
     * ("Requested chipset not available for this model", "No pre-compiled assets available for ..."),
     * plus "not found"/"unsupported" and an HTTP 403/404 (the missing S3 object answers 403).
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
