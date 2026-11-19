package dev.personal.secondhand.companion

// Turns a failure into the lines the owner copies into the report (stage 80). The class name, the SDK's
// code and the SDK's text are passed on as given; the "Hinweis" line is this app's own guess and says so.
object ErrorText {
    private val UNSUPPORTED_DEVICE_CLASSES = listOf(
        "UnsatisfiedLinkError",
        "NoClassDefFoundError",
        "ExceptionInInitializerError",
        "NoSuchMethodError",
    )

    fun className(t: Throwable): String = if (t is NpuException) t.originalClass else t.javaClass.name

    fun sdkMessage(t: Throwable): String? = if (t is NpuException) t.sdkMessage else t.message

    fun sdkCode(t: Throwable): Int? = if (t is NpuException) t.code else null

    fun isUnsupportedDevice(t: Throwable): Boolean {
        val chain = generateSequence(t) { it.cause }.take(6).toList()
        return chain.any { c -> UNSUPPORTED_DEVICE_CLASSES.any { className(c).endsWith(it) } }
    }

    /** The one-line headline for an init failure on a device without the NPU libraries. */
    fun unsupportedDeviceLine(t: Throwable): String = "Auf diesem Gerät nicht unterstützt: " + className(t)

    fun describe(step: String, t: Throwable): List<String> {
        val lines = mutableListOf<String>()
        lines += "FEHLER bei Schritt: $step"
        lines += "  Klasse: " + className(t)
        sdkCode(t)?.let { lines += "  SDK-Code: $it" + (SdkCodes.name(it)?.let { n -> " ($n laut GenieX-Quelltext)" } ?: "") }
        lines += "  SDK-Text: " + (sdkMessage(t)?.takeIf { it.isNotBlank() } ?: "(keiner)")
        var cause = t.cause
        var depth = 0
        while (cause != null && depth < 3) {
            lines += "  Ursache: " + className(cause) + ": " + (sdkMessage(cause) ?: "(kein Text)")
            cause = cause.cause
            depth++
        }
        if (isUnsupportedDevice(t)) lines += "  " + unsupportedDeviceLine(t)
        hint(t)?.let { lines += "  Hinweis (Vermutung der App): $it" }
        return lines
    }

    /** A description for a failed pull event, where the SDK gives a code and a text, not an exception. */
    fun describePull(step: String, code: Int, message: String?): List<String> =
        describe(step, NpuException(code, message, "PullEvent.Error"))

    /** "-100015 (COMMON_CHIPSET_UNAVAILABLE): <text>", the short form for the screen and the list of attempts. */
    fun shortError(code: Int?, message: String?, className: String): String {
        val c = code?.let { it.toString() + (SdkCodes.name(it)?.let { n -> " ($n)" } ?: "") }
        val m = message?.takeIf { it.isNotBlank() }
        return listOfNotNull(c, m).joinToString(": ").ifBlank { className }
    }

    fun hint(t: Throwable): String? {
        val text = ((sdkMessage(t) ?: "") + " " + className(t)).lowercase()
        return when {
            sdkCode(t) == SdkCodes.CHIPSET_UNAVAILABLE ->
                "Qualcomm kennt für diese Chip-Kennung kein Paket dieses Modells (bei Qualcomm ist SM8750 keine bekannte Kennung, nur SM8750-AC)."
            sdkCode(t) == SdkCodes.HUB_MODEL_NOT_FOUND -> "Das Modell steht nicht in Qualcomms Liste."
            sdkCode(t) == SdkCodes.INSUFFICIENT_DISK_SPACE -> "Nicht genug freier Speicher."
            sdkCode(t) == SdkCodes.MODEL_INVALID -> "Das Paket ist unvollständig oder beschädigt: angefangenen Download löschen und neu laden."
            sdkCode(t) == SdkCodes.NETWORK -> "Netzwerkproblem: WLAN und Internet prüfen, dann erneut tippen (der Download setzt fort)."
            isUnsupportedDevice(t) -> "Die NPU-Bibliotheken des SDK lassen sich auf diesem Gerät nicht laden."
            "param_not_supported" in text ->
                "Die Modellkonfiguration wurde abgelehnt (bei qairt müssen nCtx und nGpuLayers 0 sein)."
            "lacks features" in text || ("not_supported" in text && "cpu" in text) ->
                "Dem Prozessor fehlen Befehlssätze, die das SDK braucht (fp16, dotprod, i8mm)."
            PullPlanner.looksLikeChipsetUnavailable(sdkMessage(t)) ->
                "Für diese Chip-Bezeichnung gibt es bei Qualcomm kein fertiges Modellpaket."
            "no space left" in text || "enospc" in text -> "Nicht genug freier Speicher."
            "unknownhost" in text || "unable to resolve" in text || "timeout" in text || ("connect" in text && "fail" in text) ->
                "Netzwerkproblem: WLAN und Internet prüfen, dann erneut tippen (der Download setzt fort)."
            "outofmemory" in text -> "Zu wenig Arbeitsspeicher."
            else -> null
        }
    }
}
