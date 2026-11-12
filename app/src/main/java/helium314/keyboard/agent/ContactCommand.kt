// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.util.Log
import androidx.core.content.ContextCompat
import dev.agentkeyboard.core.CommandCall
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import java.text.Normalizer
import java.util.Locale

/**
 * `@contact Name` (stage 24c): contacts from the phone's address book whose name matches the typed
 * argument, as chips; a tap on a contact shows its fields, a tap on a field replaces the command
 * by that text. The address book is only read while the command is typed, with a bounded query,
 * and nothing is stored or logged; what was found lives in memory until the command is gone.
 */
object ContactCommand : KeyboardCommands.Command {
    private const val TAG = "ContactCommand"
    /** Chips with contacts, like word suggestions. */
    const val MAX_CONTACTS = 6
    /** Rows asked from the address book before ranking. */
    const val QUERY_LIMIT = 30
    /** Fields read for one contact. */
    const val MAX_FIELDS = 20

    class Contact(val id: Long, val name: String)

    enum class Kind { NAME, PHONE, EMAIL, ADDRESS }

    /** A value to insert; [type] is the label shown in the description, e.g. "Mobile". */
    class Field(val kind: Kind, val text: String, val type: String?)

    /** Where contacts come from; replaced in tests, which have no address book. */
    interface Source {
        /** Contacts whose name may match [query], at most [limit]. */
        fun find(context: Context, query: String, limit: Int): List<Contact>
        /** The phone numbers, mail and postal addresses of a contact, at most [limit]. */
        fun fields(context: Context, contactId: Long, limit: Int): List<Field>
    }

    @JvmStatic var source: Source = PhoneContacts

    /** A contact chosen for exactly this command text at this cursor; anything else shows the list again. */
    private class Chosen(val text: String, val cursor: Int, val contact: Contact)

    private var chosen: Chosen? = null
    private var cachedQuery: String? = null
    private var cachedMatches: List<Contact> = emptyList()
    private var cachedFieldsId: Long? = null
    private var cachedFields: List<Field> = emptyList()

    override val name = "contact"
    override val protectsArgument = true
    override val permission: String = Manifest.permission.READ_CONTACTS

    override fun description(context: Context) =
        context.getString(R.string.agent_command_contact, KeyboardCommands.prefix(context).toString())

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun reset() {
        chosen = null
        cachedQuery = null
        cachedMatches = emptyList()
        cachedFieldsId = null
        cachedFields = emptyList()
    }

    override fun chips(host: KeyboardCommands.Host, call: CommandCall): List<KeyboardCommands.Chip> {
        // find() already refuses these fields; checked again so that nothing is read there
        if (KeyboardCommands.isBlocked(Settings.getValues(), host.editorInfo)) { reset(); return emptyList() }
        val context = host.context
        if (!hasPermission(context)) {
            reset()
            return listOf(KeyboardCommands.Chip(context.getString(R.string.agent_command_contact_allow),
                context.getString(R.string.agent_command_contact_allow_description)) { openSettings(context) })
        }
        val query = call.argument.trim()
        if (query.isEmpty()) { reset(); return emptyList() }
        chosen?.let { selected ->
            if (selected.text == call.text && selected.cursor == host.cursor) {
                return fieldChips(host, call, selected.contact)
            }
            chosen = null
        }
        return matches(context, query).map { contact ->
            KeyboardCommands.Chip(contact.name, context.getString(R.string.agent_command_contact_show, contact.name)) {
                chosen = Chosen(call.text, host.cursor, contact)
                host.refresh()
            }
        }
    }

    /** A back chip, then the name and the fields of a chosen contact. */
    private fun fieldChips(host: KeyboardCommands.Host, call: CommandCall, contact: Contact): List<KeyboardCommands.Chip> {
        val context = host.context
        if (cachedFieldsId != contact.id) {
            cachedFields = read { source.fields(context, contact.id, MAX_FIELDS) }.orEmpty()
            cachedFieldsId = contact.id
        }
        val fields = (listOf(Field(Kind.NAME, contact.name, context.getString(R.string.agent_command_contact_name))) + cachedFields)
            .filter { it.text.isNotBlank() }
            .distinctBy { it.kind to it.text }
        val back = KeyboardCommands.Chip("←", context.getString(R.string.agent_command_contact_back)) {
            chosen = null
            host.refresh()
        }
        return listOf(back) + fields.map { field ->
            val type = field.type ?: context.getString(R.string.agent_command_contact_name)
            KeyboardCommands.Chip(field.text, context.getString(R.string.agent_command_contact_insert, type, field.text)) {
                if (host.replace(call, field.text)) reset()
            }
        }
    }

    private fun matches(context: Context, query: String): List<Contact> {
        if (query != cachedQuery) {
            cachedMatches = rank(query, read { source.find(context, query, QUERY_LIMIT) }.orEmpty())
            cachedQuery = query
        }
        return cachedMatches
    }

    /** Runs a query; a failure (e.g. permission taken away) shows nothing. Names and numbers are never logged. */
    private fun <T> read(query: () -> T): T? = try {
        query()
    } catch (e: Exception) {
        Log.w(TAG, "contacts could not be read: ${e.javaClass.simpleName}")
        null
    }

    /**
     * Contacts whose name matches [query], best first: the whole name, then the start of the name,
     * then the start of a word (every typed word must start a word of the name), then any part.
     * Case and accents do not matter. At most [MAX_CONTACTS].
     */
    fun rank(query: String, contacts: List<Contact>): List<Contact> {
        val typed = normalize(query)
        if (typed.isEmpty()) return emptyList()
        val typedWords = typed.split(' ').filter { it.isNotEmpty() }
        return contacts.asSequence()
            .filter { it.name.isNotBlank() }
            .distinctBy { it.id }
            .mapNotNull { contact ->
                val name = normalize(contact.name)
                val words = name.split(' ', '-').filter { it.isNotEmpty() }
                val score = when {
                    name == typed -> 0
                    name.startsWith(typed) -> 1
                    typedWords.all { t -> words.any { it.startsWith(t) } } -> 2
                    name.contains(typed) -> 3
                    else -> return@mapNotNull null
                }
                Triple(contact, score, name)
            }
            .sortedWith(compareBy({ it.second }, { it.third }, { it.first.id }))
            .take(MAX_CONTACTS)
            .map { it.first }
            .toList()
    }

    private val MARKS = Regex("\\p{Mn}+")
    private val SPACES = Regex("\\s+")

    private fun normalize(text: String) =
        MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).replace(SPACES, " ").trim()

    /** The keyboard cannot ask for a permission itself: the settings page for commands does. */
    private fun openSettings(context: Context) {
        context.startActivity(
            Intent(context, SettingsActivity::class.java)
                // an existing settings task would only come to the front and ignore the destination (P39)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(AgentPreferences.OPEN_SETTINGS_DESTINATION, SettingsDestination.AgentCommands)
        )
    }

    /** The phone's address book, which includes synced Google contacts. */
    object PhoneContacts : Source {
        override fun find(context: Context, query: String, limit: Int): List<Contact> {
            val uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendPath(query)
                .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, limit.toString())
                .build()
            val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val result = mutableListOf<Contact>()
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                while (result.size < limit && cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    result += Contact(cursor.getLong(0), name)
                }
            }
            return result
        }

        override fun fields(context: Context, contactId: Long, limit: Int): List<Field> {
            val projection = arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2, ContactsContract.Data.DATA3)
            val selection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)"
            val args = arrayOf(contactId.toString(), Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE)
            val resources = context.resources
            val result = mutableListOf<Field>()
            context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args,
                "${ContactsContract.Data.IS_SUPER_PRIMARY} DESC, ${ContactsContract.Data.IS_PRIMARY} DESC")?.use { cursor ->
                while (result.size < limit && cursor.moveToNext()) {
                    val value = cursor.getString(1)?.trim().orEmpty()
                    if (value.isEmpty()) continue
                    val type = if (cursor.isNull(2)) 0 else cursor.getInt(2)
                    val label = cursor.getString(3)
                    result += when (cursor.getString(0)) {
                        Phone.CONTENT_ITEM_TYPE -> Field(Kind.PHONE, value, Phone.getTypeLabel(resources, type, label).toString())
                        Email.CONTENT_ITEM_TYPE -> Field(Kind.EMAIL, value, Email.getTypeLabel(resources, type, label).toString())
                        // one line: an Enter in a chat field could send the message
                        else -> Field(Kind.ADDRESS, value.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "),
                            StructuredPostal.getTypeLabel(resources, type, label).toString())
                    }
                }
            }
            // phones first, then mail, then postal addresses
            return result.sortedBy { it.kind.ordinal }
        }
    }
}
