// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.ContactsContract
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import dev.agentkeyboard.core.DateFormatting
import dev.agentkeyboard.core.LocalMoment
import dev.agentkeyboard.core.clip.SmartAction
import dev.agentkeyboard.core.clip.SmartPaste
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.prefs

/**
 * Stage 40 "Smart Paste": chips for the recognised clip (agent-core [SmartPaste], sorted by the
 * clipboard guard [dev.agentkeyboard.core.clip.ClipGuard]) after the clipboard pill in the strip
 * ([stripRow]) and on the cards of the clipboard history ([bindHistoryRow]). Address → maps (geo:)
 * or save as `@ort` place; phone → dialer (no call permission) or a new contact (the contacts app
 * saves); IBAN → inserted in blocks after the check digits; parcel number → the carrier's page from
 * the fixed table; date → the calendar's "new event" screen (no calendar permission); e-mail →
 * mail app (mailto:); link → inserted without tracking parameters; one-time code of a copied
 * message → its digits. The pill and a tap on a card still insert the clip as it is.
 *
 * Nothing runs by itself: each chip acts once, on its own tap, after checking the field again.
 * Never for secrets ([ClipVault.isSecretClip]: marked, copied in a password field, sorted as a
 * secret), never in password, incognito or no-learning fields, outside text fields or while the
 * device is locked. Inserted text goes in as private text (never composed or learned, P178).
 * Nothing is logged; the clip is read only where the pill or the history already shows it.
 */
object SmartPasteChips {
    const val TAG_STRIP = "agent_smart_paste_strip"
    const val TAG_HISTORY_ROW = "agent_smart_paste_row"
    /** Chips on a history card at most (they share the width of the card). */
    const val MAX_HISTORY_CHIPS = 2

    fun isEnabled(context: Context) = context.prefs().getBoolean(AgentPreferences.SMART_PASTE, true)

    /** Replaced in tests, so that they need no maps, dialer, contacts, mail, calendar or browser app. */
    @JvmStatic var launcher: SearchCommand.Launcher = SearchCommand.SYSTEM_LAUNCHER
    /** Replaced in tests: whether the device (or the user) is locked right now. */
    @JvmStatic var deviceLocked: (Context) -> Boolean = {
        helium314.keyboard.compat.isDeviceLocked(it) || runCatching { helium314.keyboard.compat.isUserLocked(it) }.getOrDefault(false)
    }

    /**
     * No Smart Paste without settings, in incognito (also password and no-learning fields, P139),
     * with the no-learning flag, in password fields, outside text fields and while the device is locked.
     */
    fun isBlocked(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?): Boolean {
        if (FormatGuardChips.isBlocked(settings, editorInfo)) return true
        val type = editorInfo?.inputType ?: return true
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return true
        return deviceLocked(context)
    }

    /** The actions for a clip copied at [copiedAt]; none for a secret or a clip the app [marked] sensitive. */
    fun actions(context: Context, text: String, copiedAt: Long, marked: Boolean, settings: SettingsValues?): List<SmartAction> {
        if (text.isBlank() || text.length > SmartPaste.MAX_TEXT) return emptyList()
        if (ClipVault.isSecretClip(text, copiedAt, marked)) return emptyList()
        val now = LocalMoment.of(DateCommand.clock(), DateCommand.zone())
        return SmartPaste.recognize(text, marked, ClipVault.region(settings), now, settings?.mLocale?.language.orEmpty())
    }

    // --- what a tap does -------------------------------------------------------------------------

    /** The standard intent of an action that opens an app, or null for the others. No permission is needed for any of them. */
    fun intent(action: SmartAction): Intent? = when (action) {
        is SmartAction.OpenMap -> PlaceCommand.geoIntent(action.query)
        is SmartAction.Dial -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        is SmartAction.AddContact -> Intent(ContactsContract.Intents.Insert.ACTION)
            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, action.phone)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        is SmartAction.WriteMail -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", action.address, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // the page comes from the fixed carrier table; the clip gave only the checked number
        is SmartAction.Track -> Intent(Intent.ACTION_VIEW, Uri.parse(action.tracking.url))
            .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        is SmartAction.CreateEvent -> EventCommand.intent(action.event)
        else -> null
    }

    /**
     * Runs [action] after the user's tap: [insert] puts private text at the cursor, an app is
     * started through [launcher], a place is saved. True if it happened.
     */
    fun perform(context: Context, action: SmartAction, insert: (String) -> Boolean): Boolean = when (action) {
        is SmartAction.InsertIban -> insert(action.grouped)
        is SmartAction.InsertClean -> insert(action.text)
        is SmartAction.InsertCode -> insert(action.code)
        is SmartAction.SavePlace -> savePlace(context, action.address)
        SmartAction.IbanInvalid -> { toast(context, context.getString(R.string.agent_smart_iban_invalid_toast)); false }
        else -> {
            val intent = intent(action)
            if (intent != null && launcher.start(context, intent)) true
            else { toast(context, context.getString(R.string.agent_smart_none)); false }
        }
    }

    /** Saves [address] as a place of `@ort`, named after its first part; an address saved before keeps its name. */
    private fun savePlace(context: Context, address: String): Boolean {
        if (!SavedPlaces.available(context)) {
            toast(context, context.getString(R.string.agent_smart_place_unavailable))
            return false
        }
        val places = SavedPlaces.load(context)
        places.firstOrNull { it.address.equals(address.trim(), ignoreCase = true) }?.let {
            toast(context, context.getString(R.string.agent_smart_place_exists, it.name))
            return true
        }
        val name = SmartPaste.placeName(address, places.map { it.name })
        return when (SavedPlaces.save(context, name, address)) {
            SavedPlaces.SaveResult.SAVED -> { toast(context, context.getString(R.string.agent_smart_place_saved, name)); true }
            SavedPlaces.SaveResult.FULL -> { toast(context, context.getString(R.string.agent_smart_place_full)); false }
            else -> { toast(context, context.getString(R.string.agent_smart_place_unavailable)); false }
        }
    }

    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // --- labels ----------------------------------------------------------------------------------

    /** Day and time of an event in the keyboard language. */
    private fun whenText(action: SmartAction.CreateEvent): String =
        DateFormatting.short(action.event.date, action.event.time, DateFormatting.Language.of(Settings.getValues()?.mLocale?.language.orEmpty()))

    /** Chip text: the long form next to the pill, the [short] form on a history card. */
    fun label(context: Context, action: SmartAction, short: Boolean): String = when (action) {
        is SmartAction.OpenMap -> context.getString(if (short) R.string.agent_smart_map_short else R.string.agent_smart_map)
        is SmartAction.SavePlace -> context.getString(if (short) R.string.agent_smart_place_short else R.string.agent_smart_place)
        is SmartAction.Dial -> context.getString(if (short) R.string.agent_smart_call_short else R.string.agent_smart_call)
        is SmartAction.AddContact -> context.getString(if (short) R.string.agent_smart_contact_short else R.string.agent_smart_contact)
        is SmartAction.InsertIban -> context.getString(if (short) R.string.agent_smart_iban_short else R.string.agent_smart_iban)
        SmartAction.IbanInvalid -> context.getString(if (short) R.string.agent_smart_iban_invalid_short else R.string.agent_smart_iban_invalid)
        is SmartAction.Track -> context.getString(if (short) R.string.agent_smart_track_short else R.string.agent_smart_track, action.tracking.carrier.label)
        is SmartAction.CreateEvent -> if (short) context.getString(R.string.agent_smart_event_short)
            else context.getString(R.string.agent_smart_event, whenText(action))
        is SmartAction.WriteMail -> context.getString(if (short) R.string.agent_smart_mail_short else R.string.agent_smart_mail)
        is SmartAction.InsertClean -> context.getString(if (short) R.string.agent_smart_clean_short else R.string.agent_smart_clean)
        is SmartAction.InsertCode -> if (short) context.getString(R.string.agent_smart_code_short)
            else context.getString(R.string.agent_smart_code, action.code)
    }

    /** What TalkBack reads for a chip. */
    fun description(context: Context, action: SmartAction): String = when (action) {
        is SmartAction.OpenMap -> context.getString(R.string.agent_smart_map_description)
        is SmartAction.SavePlace -> context.getString(R.string.agent_smart_place_description)
        is SmartAction.Dial -> context.getString(R.string.agent_smart_call_description)
        is SmartAction.AddContact -> context.getString(R.string.agent_smart_contact_description)
        is SmartAction.InsertIban -> context.getString(R.string.agent_smart_iban_description, action.grouped)
        SmartAction.IbanInvalid -> context.getString(R.string.agent_smart_iban_invalid_description)
        is SmartAction.Track -> context.getString(R.string.agent_smart_track_description, action.tracking.carrier.label)
        is SmartAction.CreateEvent -> context.getString(R.string.agent_smart_event_description, whenText(action))
        is SmartAction.WriteMail -> context.getString(R.string.agent_smart_mail_description)
        is SmartAction.InsertClean -> context.getString(R.string.agent_smart_clean_description)
        is SmartAction.InsertCode -> context.getString(R.string.agent_smart_code_description, action.code)
    }

    // --- the strip -------------------------------------------------------------------------------

    /** What the strip chips need from the keyboard; the real one is [imeHost]. */
    interface StripHost {
        val context: Context
        /** The field the chips are for (checked again at tap time with the settings and lock state then). */
        val editorInfo: EditorInfo?
        /** Inserts [text] as private text at the cursor the chips were built for; false if it cannot now. */
        fun insertPrivate(text: String): Boolean
    }

    /**
     * The keyboard as [StripHost] for the field [editorInfo] the pill is built for: inserts like a
     * field suggestion in an empty field (P162), only at the same plain cursor. The strip is built
     * again for a new field, so its chips never act on another one.
     */
    fun imeHost(ime: LatinIME, editorInfo: EditorInfo?): StripHost {
        val position = ime.agentCommandCursor
        return object : StripHost {
            override val context: Context get() = ime
            override val editorInfo: EditorInfo? = editorInfo
            override fun insertPrivate(text: String): Boolean {
                if (text.isEmpty() || position < 0 || !ime.canReplaceAgentCommand() || ime.agentCommandCursor != position) return false
                ime.onPrivateTextInput(text) // never composed or learned (P178)
                return true
            }
        }
    }

    /**
     * The chips for the clip of the pill; empty when off, blocked, a secret or nothing recognised.
     * [onInserted] runs after an insertion (the pill goes like after its own tap).
     */
    fun stripChips(host: StripHost, text: String, copiedAt: Long, marked: Boolean, onInserted: () -> Unit): List<KeyboardCommands.Chip> {
        val context = host.context
        val settings = Settings.getValues()
        if (!isEnabled(context) || isBlocked(context, settings, host.editorInfo)) return emptyList()
        return actions(context, text, copiedAt, marked, settings).map { action ->
            var used = false
            KeyboardCommands.Chip(label(context, action, short = false), description(context, action),
                if (action == SmartAction.IbanInvalid) KeyboardCommands.Chip.Kind.MUTED else KeyboardCommands.Chip.Kind.ACTION) {
                // one tap, one attempt; the field is checked again (locked, incognito, password by now?)
                if (!used) {
                    used = true
                    if (isEnabled(context) && !isBlocked(context, Settings.getValues(), host.editorInfo)) {
                        val done = perform(context, action, host::insertPrivate)
                        if (done && action.inserts) onInserted()
                    }
                }
            }
        }
    }

    /**
     * The pill [pill] followed by its Smart Paste chips in one scrolling row, or null when there are
     * none (then the pill stands alone as before). [pillText] is narrowed so that the chips stay visible.
     */
    fun stripRow(host: StripHost, parent: ViewGroup, pill: View, pillText: TextView, text: String, copiedAt: Long, marked: Boolean,
                 onInserted: () -> Unit): View? {
        val chips = stripChips(host, text, copiedAt, marked, onInserted)
        if (chips.isEmpty()) return null
        val row = KeyboardCommands.chipRow(parent, chips) as HorizontalScrollView
        row.tag = TAG_STRIP
        val line = row.getChildAt(0) as LinearLayout
        line.id = View.NO_ID // not the row of a typed command
        pillText.maxWidth = (parent.width.takeIf { it > 0 } ?: parent.resources.displayMetrics.widthPixels) * 2 / 5
        val height = pill.layoutParams?.height ?: ViewGroup.LayoutParams.MATCH_PARENT
        (pill.parent as? ViewGroup)?.removeView(pill)
        line.addView(pill, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height).apply { marginEnd = parent.dp(6f) })
        return row
    }

    // --- the clipboard history -------------------------------------------------------------------

    /**
     * One visit of the clipboard history view: the actions per entry (sorted once), and what a tap
     * does. [editorInfo] is the field the view inserts into; [insert] puts private text there.
     */
    class History(private val context: Context, private val editorInfo: () -> EditorInfo?, private val insert: (String) -> Boolean) {
        private val cache = HashMap<Long, List<SmartAction>>()
        private val allowed = isEnabled(context) && !isBlocked(context, Settings.getValues(), editorInfo())

        fun actions(id: Long, text: String, copiedAt: Long): List<SmartAction> {
            if (!allowed) return emptyList()
            return cache.getOrPut(id) { actions(context, text, copiedAt, false, Settings.getValues()).take(MAX_HISTORY_CHIPS) }
        }

        /** The tap on a card chip: the field is checked again first. */
        fun perform(action: SmartAction): Boolean {
            if (!isEnabled(context) || isBlocked(context, Settings.getValues(), editorInfo())) return false
            return perform(context, action, insert)
        }
    }

    /**
     * Shows the chips of the entry [entryId] under [text] on its card (a row of at most
     * [MAX_HISTORY_CHIPS] that share the width), or hides the row: no [history], a picture or nothing recognised.
     */
    fun bindHistoryRow(text: TextView, entryId: Long, clip: String?, copiedAt: Long, history: History?) {
        val column = text.parent as? LinearLayout ?: return
        val existing = column.findViewWithTag<LinearLayout>(TAG_HISTORY_ROW)
        val actions = if (history == null || clip.isNullOrEmpty()) emptyList() else history.actions(entryId, clip, copiedAt)
        if (actions.isEmpty()) {
            existing?.removeAllViews()
            existing?.visibility = View.GONE
            return
        }
        val context = text.context
        val row = existing ?: LinearLayout(context).apply {
            tag = TAG_HISTORY_ROW
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            column.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        row.removeAllViews()
        row.visibility = View.VISIBLE
        row.setPadding(text.paddingLeft, 0, text.paddingRight, column.dp(6f))
        val color = text.currentTextColor
        for (action in actions) {
            var used = false
            row.addView(TextView(context).apply {
                this.text = label(context, action, short = true)
                contentDescription = description(context, action)
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
                typeface = text.typeface
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(column.dp(8f), column.dp(4f), column.dp(8f), column.dp(4f))
                background = GradientDrawable().apply { cornerRadius = column.dp(12f).toFloat(); setColor(ColorUtils.setAlphaComponent(color, 0x26)) }
                if (action == SmartAction.IbanInvalid) alpha = 0.7f
                isClickable = true
                isFocusable = true
                // one tap, one attempt, like the chips in the strip
                setOnClickListener { if (!used) { used = true; history?.perform(action) } }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = column.dp(4f) })
        }
    }

    private fun View.dp(value: Float) = Math.round(value * resources.displayMetrics.density)
}
