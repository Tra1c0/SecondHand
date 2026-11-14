// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

/**
 * Parcel services with a fixed tracking page (stage 40, Smart Paste). The pages live only in this
 * table; a clip contributes nothing but a number that matched a strict pattern ([TrackingNumbers]),
 * so no content can choose a host, a path or a parameter.
 */
enum class Carrier(val label: String, private val page: String) {
    DHL("DHL", "https://www.dhl.de/de/privatkunden/pakete-empfangen/verfolgen.html?piececode="),
    // letters (Einschreiben, international mail) are tracked on the same DHL page
    DEUTSCHE_POST("Deutsche Post", "https://www.dhl.de/de/privatkunden/pakete-empfangen/verfolgen.html?piececode="),
    HERMES("Hermes", "https://www.myhermes.de/empfangen/sendungsverfolgung/sendungsinformation/#"),
    DPD("DPD", "https://tracking.dpd.de/status/de_DE/parcel/"),
    GLS("GLS", "https://gls-group.com/DE/de/paketverfolgung?match="),
    UPS("UPS", "https://www.ups.com/track?loc=de_DE&tracknum="),
    ;

    /** The tracking page for [number]; only capital letters and digits are accepted, anything else gives null. */
    fun trackingUrl(number: String): String? =
        if (number.length in 8..35 && number.all { it in 'A'..'Z' || it in '0'..'9' }) page + number else null
}

/** A recognised tracking number. [toString] never shows the number. */
class Tracking(val carrier: Carrier, val number: String) {
    /** Never null: [TrackingNumbers] only returns numbers the carrier's page accepts. */
    val url: String get() = carrier.trackingUrl(number)!!
    override fun equals(other: Any?) = other is Tracking && other.carrier == carrier && other.number == number
    override fun hashCode() = carrier.hashCode() * 31 + number.hashCode()
    override fun toString() = "Tracking($carrier)"
}

/**
 * Parcel tracking numbers in a copied text (stage 40), conservative by design. Without the name of
 * the carrier in the text only formats that cannot be anything else count: UPS `1Z…` with its check
 * digit, a Deutsche Post item number `XX123456785DE` (UPU S10, check digit), DHL `JJD…`/`JVGL…`,
 * DHL's 20-digit `00340434…` (GS1 SSCC check digit) and Hermes `H` plus 19 digits. Plain digit
 * runs (12 digits DHL, 14 digits DPD or Hermes, 11–12 digits GLS) are only taken when the carrier
 * is named ("DPD", "Hermes", …): a customer, order or account number alone stays a number.
 * Postcodes, phone numbers and amounts never match (too short, other characters). Review of stage
 * 40 (B5): no number right after a customer, order, invoice or tax number label ("Kundennummer",
 * "Bestellung", "Rechnungs-Nr.", "order number", …), and GLS's 8-character id only near
 * "Track-ID" and never in the form of a BIC ("GENODEM1").
 */
object TrackingNumbers {
    /** Longer clips are no shipping message; nothing is looked for. */
    const val MAX_LENGTH = 300
    /** A clip of at most this many characters made of letters, digits and spaces is also read without its spaces ("RR 1234 5678 5DE"). */
    private const val MAX_GROUPED = 40

    private val UPS = Regex("1Z[0-9A-Z]{16}")
    private val S10_DE = Regex("[A-Z]{2}\\d{9}DE")
    private val DHL_PREFIXED = Regex("(?:JJD|JVGL)\\d{12,22}")
    private val DHL_SSCC = Regex("00340434\\d{12}")
    private val HERMES_H = Regex("H\\d{19}")

    private val NAMES = listOf(
        Carrier.DHL to Regex("(?i)(?<![\\p{L}\\d])dhl(?![\\p{L}\\d])"),
        Carrier.HERMES to Regex("(?i)(?<![\\p{L}\\d])hermes(?![\\p{L}\\d])"),
        Carrier.DPD to Regex("(?i)(?<![\\p{L}\\d])dpd(?![\\p{L}\\d])"),
        Carrier.GLS to Regex("(?i)(?<![\\p{L}\\d])gls(?![\\p{L}\\d])"),
    )
    /** Formats taken only when the carrier is named in the text. */
    private val NAMED: Map<Carrier, Regex> = mapOf(
        Carrier.DHL to Regex("\\d{12}|\\d{20}"),
        Carrier.HERMES to Regex("\\d{14}|\\d{16}"),
        Carrier.DPD to Regex("\\d{14}"),
        Carrier.GLS to Regex("\\d{11,12}|(?=[A-Z0-9]{8}$)(?=.*\\d)(?=.*[A-Z])[A-Z0-9]{8}"),
    )
    private val GLS_ID = Regex("[A-Z0-9]{8}")
    /** A BIC ("GENODEM1"): six letters and two letters or digits; never a GLS id. */
    private val BIC = Regex("[A-Z]{6}[A-Z0-9]{2}")
    /** GLS's 8-character id counts only with this label at most [TRACK_ID_REACH] characters before it ("GLS Track-ID ZFG2D3K1"). */
    private val TRACK_ID = Regex("(?i)(?<![\\p{L}])track(?:ing)?[\\s-]?id(?![\\p{L}])")
    private const val TRACK_ID_REACH = 20
    /** A customer, order, invoice, contract or tax number label right before the number: it is that number, no parcel. */
    private val LABELLED = Regex(
        "(?i)(?<![\\p{L}])(?:(?:kunden|bestell|auftrags|rechnungs|vertrags|mitglieds|steuer|konto|referenz|kd\\.?|best\\.?)[\\s-]?" +
            "(?:nummer|nr\\.?)|kunde|bestellung|auftrag|rechnung|" +
            "(?:customer|order|invoice|account|reference)(?:\\s+(?:number|no\\.?|nr\\.?|id|#))?|" +
            "n[º°o]\\.?\\s*de\\s+(?:pedido|cliente|factura)|pedido|factura|cliente)\\s*(?:[:#.]\\s*)*$"
    )
    private const val LABEL_REACH = 40

    private const val TRIM = ".,;:!?()[]{}\"'«»„“”‚‘’<>"
    private val WORD = Regex("\\S+")

    /** A possible number and where it starts in the text. */
    private class Candidate(val number: String, val start: Int)

    /** The tracking number in [text], or null. */
    fun find(text: String): Tracking? {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_LENGTH) return null
        val candidates = candidates(t).filter { !LABELLED.containsMatchIn(t.substring(maxOf(0, it.start - LABEL_REACH), it.start)) }
        for (candidate in candidates) distinctive(candidate.number)?.let { return Tracking(it, candidate.number) }
        // the carrier named in the text, in the order of the text
        val named = NAMES.mapNotNull { (carrier, name) -> name.find(t)?.let { carrier to it.range.first } }.sortedBy { it.second }
        for ((carrier, _) in named) {
            val pattern = NAMED.getValue(carrier)
            candidates.firstOrNull { pattern.matches(it.number) && (carrier != Carrier.GLS || glsTaken(t, it)) }
                ?.let { return Tracking(carrier, it.number) }
        }
        return null
    }

    /** GLS digits always; the 8-character id only near "Track-ID" and never in the form of a BIC. */
    private fun glsTaken(t: String, c: Candidate): Boolean {
        if (!GLS_ID.matches(c.number) || c.number.all { it.isDigit() }) return true
        return !BIC.matches(c.number) && TRACK_ID.containsMatchIn(t.substring(maxOf(0, c.start - TRACK_ID_REACH), c.start))
    }

    /** Words of the text without surrounding punctuation, plus the whole short clip without its spaces. */
    private fun candidates(t: String): List<Candidate> {
        val found = ArrayList<Candidate>()
        if (t.length <= MAX_GROUPED && ' ' in t && t.all { it.isLetterOrDigit() || it == ' ' }) found += Candidate(t.replace(" ", ""), 0)
        for (m in WORD.findAll(t)) {
            var start = m.range.first
            var end = m.range.last + 1
            while (start < end && t[start] in TRIM) start++
            while (end > start && t[end - 1] in TRIM) end--
            if (end - start in 8..35) found += Candidate(t.substring(start, end), start)
        }
        // capitals as written: "1z…" or "rr…de" in a sentence are taken in capitals, "Hermes" stays a word
        return found.map { c -> if (c.number.any { it.isDigit() } && c.number.count { it.isLetter() } <= 4) Candidate(c.number.uppercase(), c.start) else c }
            .filter { c -> c.number.all { it in 'A'..'Z' || it in '0'..'9' } }.distinctBy { it.number }
    }

    private fun distinctive(n: String): Carrier? = when {
        UPS.matches(n) && upsValid(n) -> Carrier.UPS
        S10_DE.matches(n) && s10Valid(n) -> Carrier.DEUTSCHE_POST
        DHL_PREFIXED.matches(n) -> Carrier.DHL
        DHL_SSCC.matches(n) && gs1Valid(n.substring(2)) -> Carrier.DHL
        HERMES_H.matches(n) -> Carrier.HERMES
        else -> null
    }

    /** UPS `1Z` numbers: the last character is a check digit over the 15 before it (letters count as (code − 63) mod 10). */
    fun upsValid(n: String): Boolean {
        if (!UPS.matches(n)) return false
        val body = n.substring(2, 17)
        var sum = 0
        body.forEachIndexed { i, c ->
            val value = if (c.isDigit()) c - '0' else (c.code - 63) % 10
            sum += if (i % 2 == 1) value * 2 else value
        }
        return n[17].isDigit() && (10 - sum % 10) % 10 == n[17] - '0'
    }

    /** UPU S10 item numbers: two letters, 8 digits, a check digit (weights 8 6 4 2 3 5 9 7, mod 11), two letters. */
    fun s10Valid(n: String): Boolean {
        if (n.length != 13 || !n.substring(2, 11).all { it.isDigit() }) return false
        val weights = intArrayOf(8, 6, 4, 2, 3, 5, 9, 7)
        val sum = (0 until 8).sumOf { (n[2 + it] - '0') * weights[it] }
        val check = when (val c = 11 - sum % 11) { 10 -> 0; 11 -> 5; else -> c }
        return check == n[10] - '0'
    }

    /** GS1 check digit (e.g. an 18-digit SSCC): weights 3 and 1 from the right, the last digit is the check digit. */
    fun gs1Valid(digits: String): Boolean {
        if (digits.length < 2 || !digits.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until digits.length - 1) {
            val fromRight = digits.length - 2 - i
            sum += (digits[i] - '0') * if (fromRight % 2 == 0) 3 else 1
        }
        return (10 - sum % 10) % 10 == digits.last() - '0'
    }
}
