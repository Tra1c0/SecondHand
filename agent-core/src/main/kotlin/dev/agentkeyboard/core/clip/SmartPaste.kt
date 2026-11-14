// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import dev.agentkeyboard.core.EventParser
import dev.agentkeyboard.core.LocalMoment
import dev.agentkeyboard.core.Places
import dev.agentkeyboard.core.format.IbanCheck
import dev.agentkeyboard.core.format.PhoneFormat

/**
 * What a tap on a Smart Paste chip may do (stage 40). Nothing runs by itself: the app shows one chip
 * per action and acts only on that chip's tap. Opening apps uses standard intents (maps, dialer,
 * contacts, mail, calendar, browser); inserting puts text at the cursor, private text only where
 * [insertsPrivately] says so. [toString] never shows clip text.
 */
sealed interface SmartAction {
    /** Opens a maps app with a search for [query] (geo: intent). */
    data class OpenMap(val query: String) : SmartAction { override fun toString() = "OpenMap" }
    /** Saves [address] as a place of `@ort`. */
    data class SavePlace(val address: String) : SmartAction { override fun toString() = "SavePlace" }
    /** Opens the dialer with [number] (E.164, "+4915123456789"); the user calls there (no call permission). */
    data class Dial(val number: String) : SmartAction { override fun toString() = "Dial" }
    /** Opens the contacts app's "new contact" with [phone] (E.164); it saves only if the user does. */
    data class AddContact(val phone: String) : SmartAction { override fun toString() = "AddContact" }
    /** Inserts the IBAN in blocks of four; only offered when its check digits are right. */
    data class InsertIban(val grouped: String) : SmartAction { override fun toString() = "InsertIban" }
    /** A hint: the copied IBAN has wrong check digits; a tap inserts nothing. */
    data object IbanInvalid : SmartAction
    /** Opens the carrier's tracking page (fixed table in [Carrier]) for the number. */
    data class Track(val tracking: Tracking) : SmartAction { override fun toString() = "Track(${tracking.carrier})" }
    /** Opens the calendar app's "new event" screen with this date, time and title. */
    data class CreateEvent(val event: EventParser.Result) : SmartAction { override fun toString() = "CreateEvent" }
    /** Opens a mail app with [address] as recipient (mailto: intent). */
    data class WriteMail(val address: String) : SmartAction { override fun toString() = "WriteMail" }
    /** Inserts the clip with the tracking parameters of its links removed ([LinkCleaner]). */
    data class InsertClean(val text: String) : SmartAction { override fun toString() = "InsertClean" }
    /** Inserts the one-time code of a copied message, digits only. */
    data class InsertCode(val code: String) : SmartAction { override fun toString() = "InsertCode" }

    /** True for actions that put text into the field. */
    val inserts: Boolean get() = this is InsertIban || this is InsertClean || this is InsertCode

    /**
     * True for insertions that go in as private text (never composed, shown in the strip or learned,
     * P178): the one-time code, like a code from the vault, and the IBAN, like the same clip offered
     * as a field suggestion (stage 45). A link without tracking goes in like the clipboard pill
     * (review of stage 40, B2): it takes none of the private-text marks of a field
     * ([PrivateRanges.MAX]), so a vault secret inserted before keeps its mark.
     */
    val insertsPrivately: Boolean get() = this is InsertCode || this is InsertIban
}

/**
 * Smart Paste (stage 40): the actions for a copied text, offline and pure. The kind comes from the
 * clipboard guard ([ClipGuard.classify], stage 44): secrets give nothing; addresses, phone numbers,
 * IBANs, e-mail addresses, links and one-time codes in a message get their actions; in other text
 * a parcel number ([TrackingNumbers]), a date ([ClipDates]) and links with tracking parameters
 * ([LinkCleaner]) are looked for. Doubtful cases give nothing.
 */
object SmartPaste {
    /** Longer clips get no actions (and are not sorted again for them). */
    const val MAX_TEXT = 2000
    /** At most this many actions per clip, the most useful first. */
    const val MAX_ACTIONS = 3

    /** The actions for [text]; [markedSensitive], [region] as for [ClipGuard.classify]; [now], [language] for dates. */
    fun recognize(text: String, markedSensitive: Boolean, region: String, now: LocalMoment, language: String): List<SmartAction> {
        if (text.isBlank() || text.length > MAX_TEXT || markedSensitive) return emptyList()
        return actions(text, ClipGuard.classify(text, false, region), region, now, language)
    }

    /** The actions for [text] sorted as [verdict]; [region] for phone numbers without a country code. */
    fun actions(text: String, verdict: ClipClass, region: String, now: LocalMoment, language: String): List<SmartAction> {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_TEXT) return emptyList()
        val found: List<SmartAction> = when (verdict.kind) {
            ClipKind.SECRET -> emptyList()
            ClipKind.ADDRESS -> {
                val address = AddressQuery.of(t) ?: return emptyList()
                listOfNotNull(SmartAction.OpenMap(address.query),
                    SmartAction.SavePlace(address.query).takeIf { address.place && address.query.length <= Places.MAX_ADDRESS })
            }
            // the guard's verdict (and its label "Telefon" in the history) stays; a call needs a number written as one
            ClipKind.PHONE -> dialable(t, region)?.let { listOf(SmartAction.Dial(it), SmartAction.AddContact(it)) } ?: textActions(t, now, language)
            ClipKind.IBAN -> iban(t)
            ClipKind.EMAIL -> listOf(SmartAction.WriteMail(t.removePrefix("mailto:")))
            ClipKind.LINK -> listOfNotNull(LinkCleaner.clean(t)?.let { SmartAction.InsertClean(it) })
            // a code alone is what the clipboard pill inserts anyway; from a message only the digits
            ClipKind.OTP -> listOfNotNull(verdict.code?.takeIf { verdict.inText }?.let { SmartAction.InsertCode(it) })
            ClipKind.TEXT -> textActions(t, now, language)
        }
        return found.take(MAX_ACTIONS)
    }

    private fun textActions(t: String, now: LocalMoment, language: String): List<SmartAction> = listOfNotNull(
        TrackingNumbers.find(t)?.let { SmartAction.Track(it) },
        ClipDates.find(t, now, language)?.let { SmartAction.CreateEvent(it) },
        LinkCleaner.cleanText(t)?.let { SmartAction.InsertClean(it) },
    )

    /**
     * The E.164 form of a phone number written as one (review of stage 40, B1): it starts with "+",
     * "00" or the trunk prefix "0" ("(030) 12345678" too). A bare digit run that libphonenumber also
     * takes for a number ("4500012345", "340434161094", "30 12345678") is as often an order, customer
     * or parcel number and gets no call chip.
     */
    private fun dialable(t: String, region: String): String? {
        val first = t.trimStart('(', ' ').firstOrNull() ?: return null
        if (first != '+' && first != '0') return null
        return PhoneFormat.e164(t, region)
    }

    /** Formatted insertion only with right check digits; an IBAN that is already written so gets nothing. */
    private fun iban(t: String): List<SmartAction> {
        val compact = IbanCheck.normalize(t.removePrefix("IBAN:").removePrefix("IBAN").trim()) ?: return emptyList()
        if (!IbanCheck.isValid(compact)) return listOf(SmartAction.IbanInvalid)
        val grouped = IbanCheck.group(compact)
        return if (grouped == t) emptyList() else listOf(SmartAction.InsertIban(grouped))
    }

    /**
     * A name for a place saved from an address: its first part ("Hauptstraße 5" from "Hauptstraße 5,
     * 10115 Berlin"), at most [Places.MAX_NAME] characters, with " 2", " 3" … when [taken] (any case)
     * already has it.
     */
    fun placeName(address: String, taken: Collection<String>): String {
        val first = address.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: address.trim()
        val base = first.replace(Regex("\\s+"), " ").take(Places.MAX_NAME).trim()
        val used = taken.map { it.trim().lowercase() }.toSet()
        if (base.lowercase() !in used) return base
        for (n in 2..99) {
            val suffix = " $n"
            val name = base.take(Places.MAX_NAME - suffix.length).trim() + suffix
            if (name.lowercase() !in used) return name
        }
        return base
    }
}

/**
 * The map search of an address clip (review of stage 40, B7). Only the street and the postcode with
 * the city go in, in the order of the clip; names, companies, countries, labels ("Lieferadresse:"),
 * phone and e-mail lines stay out. The clip is read in parts (lines, then commas; a bare house number
 * after a comma stays with its street, "Calle Mayor, 5"). [place] is false when the address stands
 * in running text ("Treffen am Marktplatz 5, 10115 Berlin"): the map then gets the street and the
 * postcode picked out of it, and nothing is offered as a place of `@ort`.
 */
internal class AddressQuery(val query: String, val place: Boolean) {
    override fun toString() = "AddressQuery(place=$place)"

    companion object {
        private const val STREET_WORD =
            "(?:straße|strasse|str\\.|weg|gasse|platz|allee|ring|damm|ufer|steig|chaussee|markt|graben|kai|zeile|promenade)"
        private const val HOUSE = "\\d{1,4}\\s?[a-z]?(?:\\s*[-–/]\\s*\\d{1,4}\\s?[a-z]?)?"
        /** A German street part: the words before the name ([1]), the name ending in a street word ([2]), the house number. */
        private val STREET_DE = Regex("(?i)^(.*?)(?<![\\p{L}\\-.'])([\\p{L}\\-.']*?$STREET_WORD)\\s*$HOUSE$")
        private val STREET_WORD_ALONE = Regex("(?i)^$STREET_WORD$")
        private val STREET_ES = Regex(
            "(?i)^(?:calle|c/|avenida|avda\\.?|plaza|pza\\.|paseo|carrer|camino|ronda|travesía)\\s*[\\p{L} .'\\-]{2,40}?,?\\s*" +
                "(?:n[ºo°.]\\s*)?\\d{1,4}\\s?[a-z]?(?:\\s*,?\\s*\\d{1,2}\\s*[º°ª]\\s*[a-z]?)?$"
        )
        private val STREET_EN = Regex(
            "(?i)^\\d{1,5}\\s+[\\p{L} .'\\-]{2,40}?\\s(?:street|st\\.?|avenue|ave\\.?|road|rd\\.?|lane|ln\\.?|drive|dr\\.?|boulevard|blvd\\.?)$"
        )
        private const val CITY_WORD = "(?:\\p{Lu}[\\p{L}.'\\-]*|\\(\\p{Lu}[\\p{L}.'\\-]*\\)|am|an|der|die|de|del|la|las|los|el|im|in|ob|bei|a\\.|i\\.)"
        /** "10115 Berlin", "D-10115 Berlin", "60311 Frankfurt am Main", "06108 Halle (Saale)". */
        private val POSTCODE = Regex("^(?:[A-Z]{1,3}-)?\\d{4,5}\\s+\\p{Lu}[\\p{L}.'\\-]*(?:\\s+$CITY_WORD){0,4}$")
        /** "Springfield IL 62704", "IL 62704" (a state is needed: a bare five-digit number is too often something else). */
        private val ZIP_US = Regex("^(?:\\p{Lu}[\\p{L}.'\\-]*\\s+){0,3}[A-Z]{2}\\s+\\d{5}(?:-\\d{4})?$")
        private val STATE_ZIP = Regex("^[A-Z]{2}\\s+\\d{5}(?:-\\d{4})?$")
        private val CITY = Regex("^\\p{Lu}[\\p{L}.'\\-]*(?:\\s+\\p{Lu}[\\p{L}.'\\-]*){0,2}$")
        private val ZIP_UK = Regex("^(?:\\p{Lu}[\\p{L}.'\\-]*\\s+){0,2}[A-Z]{1,2}\\d[A-Z\\d]?\\s\\d[A-Z]{2}$")
        private val HOUSE_ONLY = Regex("(?i)^(?:n[ºo°.]\\s*)?\\d{1,4}\\s?[a-z]?(?:\\s*\\d{1,2}\\s*[º°ª]\\s*[a-z]?)?$")
        private const val LABEL_WORD = "[\\p{L}\\-]*(?:adresse|anschrift|address|dirección|direccion|domicilio)"
        private val LABEL = Regex("(?i)^$LABEL_WORD\\s*:\\s*")
        private val LABEL_ONLY = Regex("(?i)^$LABEL_WORD\\s*:?$")
        /** Phone, mail and web parts ("Tel. 0151 23456789", "E-Mail: …", "www…"). */
        private val CONTACT = Regex(
            "(?i)(?<![\\p{L}])(?:tel|telefon|telephone|phone|fon|mobil|mobile|handy|fax|tlf|tfno|teléfono|telefono|móvil|movil|cell|" +
                "e-?mail|mail|web|www)(?![\\p{L}])|@|://"
        )
        private val PHONE_ONLY = Regex("^[+\\d][\\d ()/.\\-]{5,}$")
        /** Words a German street name may start with in any case ("Am Marktplatz", "An der Alten Mühle", "Alte Landstraße"). */
        private val LEADS = setOf(
            "am", "an", "auf", "im", "in", "zum", "zur", "beim", "bei", "hinter", "unter", "vor", "über", "ueber", "alte", "alter",
            "altes", "alten", "neue", "neuer", "neues", "neuen", "große", "großer", "grosse", "grosser", "kleine", "kleiner", "lange",
            "langer", "st.", "sankt",
        )
        private val ARTICLES = setOf("der", "die", "das", "dem", "den", "des")
        private val SPACES = Regex("\\s+")

        /** The search for [text] (sorted as an address by [ClipGuard]), or null if no street and postcode are found. */
        fun of(text: String): AddressQuery? {
            val parts = ArrayList<String>()
            var streets = 0
            var postcodes = 0
            var running = false
            val pieces = pieces(text).map { LABEL.replace(it, "").trim().trimEnd(',', ';').trim() }
            for ((i, p) in pieces.withIndex()) {
                if (p.isEmpty() || LABEL_ONLY.matches(p) || CONTACT.containsMatchIn(p) || PHONE_ONLY.matches(p)) continue
                when {
                    isStreet(p) -> { parts += p; streets++ }
                    isPostcode(p) -> { parts += p; postcodes++ }
                    // "Springfield" before "IL 62704" is the city
                    CITY.matches(p) && pieces.getOrNull(i + 1)?.let { STATE_ZIP.matches(it) } == true && streets > 0 -> parts += p
                    else -> {
                        val both = streetThenPostcode(p)
                        if (both != null) { parts += both.first; parts += both.second; streets++; postcodes++ }
                        else if (hasAddressPart(p)) running = true
                        // else a name, a company or a country: left out
                    }
                }
            }
            if (!running && streets > 0 && postcodes > 0) return AddressQuery(parts.joinToString(", "), place = true)
            return picked(text)
        }

        /** Lines, then commas; a bare house number after a comma joins the part before it on its line. */
        private fun pieces(text: String): List<String> {
            val out = ArrayList<String>()
            for (line in text.lines()) {
                val lineStart = out.size
                for (raw in line.split(',')) {
                    val piece = raw.trim()
                    if (piece.isEmpty()) continue
                    if (out.size > lineStart && HOUSE_ONLY.matches(piece)) out[out.lastIndex] = out.last() + ", " + piece
                    else out += piece
                }
            }
            return out
        }

        private fun isStreet(p: String): Boolean {
            if (STREET_ES.matches(p) || STREET_EN.matches(p)) return true
            val m = STREET_DE.matchEntire(p) ?: return false
            val words = m.groupValues[1].trim().split(SPACES).filter { it.isNotEmpty() }
            return startsLikeAStreet(words, separate = STREET_WORD_ALONE.matches(m.groupValues[2]))
        }

        /**
         * The words before the street name: a compound name ("Hauptstraße") takes at most a leading
         * "Am"/"An der"; a street word on its own ("Straße", "Allee") up to three words, after such a
         * lead only in capitals ("Berliner Straße", "Am Alten Markt"). "Treffen am Marktplatz 5" is running text.
         */
        private fun startsLikeAStreet(words: List<String>, separate: Boolean): Boolean {
            if (words.isEmpty()) return true
            if (words.size > 3) return false
            var i = 0
            if (words[0].lowercase() in LEADS) {
                i = 1
                if (i < words.size && words[i].lowercase() in ARTICLES) i++
            }
            if (!separate) return i == words.size
            return words.subList(i, words.size).all { it.first().isUpperCase() }
        }

        private fun isPostcode(p: String) = POSTCODE.matches(p) || ZIP_US.matches(p) || ZIP_UK.matches(p)

        /** "Musterstraße 12 10115 Berlin" without a comma. */
        private fun streetThenPostcode(p: String): Pair<String, String>? {
            val m = ClipGuard.POSTCODE_CITY.find(p) ?: return null
            val street = p.substring(0, m.range.first).trim().trimEnd(',').trim()
            val postcode = p.substring(m.range.first).trim()
            return if (street.isNotEmpty() && isStreet(street) && isPostcode(postcode)) street to postcode else null
        }

        private fun hasAddressPart(p: String) = ClipGuard.POSTCODE_CITY.containsMatchIn(p) || ClipGuard.STREET_DE.containsMatchIn(p) ||
            ClipGuard.STREET_ES.containsMatchIn(p) || ClipGuard.STREET_EN.containsMatchIn(p)

        /** Running text: the first street and the first postcode with its city, nothing else. */
        private fun picked(text: String): AddressQuery? {
            val flat = text.replace(SPACES, " ")
            val street = pickedStreet(flat) ?: return null
            val postcode = (ClipGuard.POSTCODE_CITY.find(flat) ?: ClipGuard.ZIP_EN.find(flat))?.value?.trim() ?: return null
            return AddressQuery("$street, $postcode", place = false)
        }

        private fun pickedStreet(flat: String): String? {
            val m = ClipGuard.STREET_DE.find(flat) ?: return (ClipGuard.STREET_ES.find(flat) ?: ClipGuard.STREET_EN.find(flat))?.value?.trim()
            val street = m.value.trim()
            // "in der Berliner Straße 5": a street word on its own takes up to two words in capitals before it
            if (!STREET_WORD_ALONE.matches(street.substringBefore(' '))) return street
            val names = flat.substring(0, m.range.first).trim().split(' ').takeLastWhile { it.isNotEmpty() && it.first().isUpperCase() }.takeLast(2)
            return (names + street).joinToString(" ")
        }
    }
}
