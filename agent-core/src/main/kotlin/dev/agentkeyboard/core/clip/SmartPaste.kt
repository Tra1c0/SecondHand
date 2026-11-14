// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

import dev.agentkeyboard.core.EventParser
import dev.agentkeyboard.core.LocalMoment
import dev.agentkeyboard.core.Places
import dev.agentkeyboard.core.format.IbanCheck

/**
 * What a tap on a Smart Paste chip may do (stage 40). Nothing runs by itself: the app shows one chip
 * per action and acts only on that chip's tap. Opening apps uses standard intents (maps, dialer,
 * contacts, mail, calendar, browser); inserting puts private text at the cursor. [toString] never
 * shows clip text.
 */
sealed interface SmartAction {
    /** Opens a maps app with a search for [query] (geo: intent). */
    data class OpenMap(val query: String) : SmartAction { override fun toString() = "OpenMap" }
    /** Saves [address] as a place of `@ort`. */
    data class SavePlace(val address: String) : SmartAction { override fun toString() = "SavePlace" }
    /** Opens the dialer with [number]; the user calls there (no call permission). */
    data class Dial(val number: String) : SmartAction { override fun toString() = "Dial" }
    /** Opens the contacts app's "new contact" with [phone]; it saves only if the user does. */
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
        return actions(text, ClipGuard.classify(text, false, region), now, language)
    }

    /** The actions for [text] sorted as [verdict]. */
    fun actions(text: String, verdict: ClipClass, now: LocalMoment, language: String): List<SmartAction> {
        val t = text.trim()
        if (t.isEmpty() || t.length > MAX_TEXT) return emptyList()
        val found: List<SmartAction> = when (verdict.kind) {
            ClipKind.SECRET -> emptyList()
            ClipKind.ADDRESS -> {
                val address = t.lines().map { it.trim().trimEnd(',') }.filter { it.isNotEmpty() }.joinToString(", ")
                listOfNotNull(SmartAction.OpenMap(address), SmartAction.SavePlace(address).takeIf { address.length <= Places.MAX_ADDRESS })
            }
            ClipKind.PHONE -> listOf(SmartAction.Dial(t), SmartAction.AddContact(t))
            ClipKind.IBAN -> iban(t)
            ClipKind.EMAIL -> listOf(SmartAction.WriteMail(t.removePrefix("mailto:")))
            ClipKind.LINK -> listOfNotNull(LinkCleaner.clean(t)?.let { SmartAction.InsertClean(it) })
            // a code alone is what the clipboard pill inserts anyway; from a message only the digits
            ClipKind.OTP -> listOfNotNull(verdict.code?.takeIf { verdict.inText }?.let { SmartAction.InsertCode(it) })
            ClipKind.TEXT -> listOfNotNull(
                TrackingNumbers.find(t)?.let { SmartAction.Track(it) },
                ClipDates.find(t, now, language)?.let { SmartAction.CreateEvent(it) },
                LinkCleaner.cleanText(t)?.let { SmartAction.InsertClean(it) },
            )
        }
        return found.take(MAX_ACTIONS)
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
