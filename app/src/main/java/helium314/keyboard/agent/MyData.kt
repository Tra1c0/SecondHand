// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import dev.agentkeyboard.core.suggest.SuggestKind
import helium314.keyboard.agent.security.CredentialError
import helium314.keyboard.agent.security.CredentialFailure
import helium314.keyboard.agent.security.SecretCipher
import helium314.keyboard.agent.security.androidSecretCipher
import helium314.keyboard.agent.security.credentialAccessAllowed
import helium314.keyboard.agent.security.credentialContext
import helium314.keyboard.agent.security.readBounded
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stage 45 "Meine Daten": the owner's own name, e-mail addresses, phone numbers, addresses and
 * IBANs, optional and empty at first. Offered as strip chips in matching fields and after cue words
 * ([FieldSuggestionChips]); never secrets, never passwords. [toString] shows no value.
 */
class MyData(
    val name: String = "",
    val emails: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val addresses: List<String> = emptyList(),
    val ibans: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = name.isEmpty() && emails.isEmpty() && phones.isEmpty() && addresses.isEmpty() && ibans.isEmpty()

    /** The entries for [kind], in the order typed; empty for kinds that only come from clips. */
    fun entries(kind: SuggestKind): List<String> = when (kind) {
        SuggestKind.NAME -> listOfNotNull(name.takeIf { it.isNotEmpty() })
        SuggestKind.EMAIL -> emails
        SuggestKind.PHONE -> phones
        SuggestKind.ADDRESS -> addresses
        SuggestKind.IBAN -> ibans
        SuggestKind.OTP, SuggestKind.LINK -> emptyList()
    }

    /** Trimmed, single-line, bounded, without empty or repeated entries. */
    fun cleaned(): MyData = MyData(clean(name), list(emails), list(phones), list(addresses), list(ibans))

    override fun toString() = "MyData(${if (isEmpty) "empty" else "set"})"
    override fun equals(other: Any?) = other is MyData && other.name == name && other.emails == emails && other.phones == phones &&
        other.addresses == addresses && other.ibans == ibans
    override fun hashCode() = listOf(name, emails, phones, addresses, ibans).hashCode()

    companion object {
        /** Entries per kind. */
        const val MAX_ENTRIES = 3
        /** Characters per entry. */
        const val MAX_LENGTH = 200

        private fun clean(value: String) = value.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(MAX_LENGTH)
        private fun list(values: List<String>) = values.map(::clean).filter { it.isNotEmpty() }.distinct().take(MAX_ENTRIES)

        internal fun fromJson(json: String): MyData {
            val o = JSONObject(json)
            require(o.optInt("v") == 1)
            fun array(key: String) = o.optJSONArray(key)?.let { a -> (0 until minOf(a.length(), MAX_ENTRIES * 2)).map { a.optString(it) } }.orEmpty()
            return MyData(o.optString("name"), array("emails"), array("phones"), array("addresses"), array("ibans")).cleaned()
        }
    }

    internal fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("name", name)
        put("emails", JSONArray(emails))
        put("phones", JSONArray(phones))
        put("addresses", JSONArray(addresses))
        put("ibans", JSONArray(ibans))
    }.toString()
}

/**
 * Encrypted store of [MyData], the same mechanism as the AI keys ([helium314.keyboard.agent.security.EncryptedCredentialStore]):
 * AES-256-GCM with its own key in the Android Keystore, file `agent_my_data/data.sealed` in the
 * credential-protected (CE) `noBackupFilesDir`, so never in a backup and never before the first
 * unlock (Direct Boot, P134/P145). Every access first checks [unlocked]; locked means
 * [CredentialError.LOCKED] before any file or key is touched.
 */
class MyDataStore(
    context: Context,
    cipher: SecretCipher? = null,
    private val unlocked: () -> Boolean = { credentialAccessAllowed(context) },
) {
    private val storageContext = credentialContext(context.applicationContext)
    private val directory get() = File(storageContext.noBackupFilesDir, DIRECTORY)
    private val cipher = cipher ?: androidSecretCipher(KEY_ALIAS) { directory.listFiles().isNullOrEmpty() }
    private val file get() = AtomicFile(File(directory, FILE))

    private fun checkAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) throw CredentialFailure(CredentialError.UNSUPPORTED)
        if (!runCatching(unlocked).getOrDefault(false)) throw CredentialFailure(CredentialError.LOCKED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && storageContext.isDeviceProtectedStorage)
            throw CredentialFailure(CredentialError.UNAVAILABLE)
    }

    /** The stored data, or empty data when nothing is stored. Throws [CredentialFailure] when locked or unreadable. */
    fun read(): MyData = synchronized(lock) {
        checkAccess()
        val target = file
        if (!target.baseFile.exists() && !File(target.baseFile.path + ".bak").exists()) return@synchronized MyData()
        try {
            val sealed = target.openRead().use { it.readBounded(MAX_FILE_BYTES) }
            val plain = cipher.open(sealed, AAD)
            try { MyData.fromJson(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
        } catch (_: Exception) { throw CredentialFailure(CredentialError.UNAVAILABLE) }
    }

    /** Saves [data] (cleaned); empty data removes the file. */
    fun save(data: MyData) = synchronized(lock) {
        checkAccess()
        val cleaned = data.cleaned()
        if (cleaned.isEmpty) return@synchronized delete()
        val bytes = cleaned.toJson().toByteArray(Charsets.UTF_8)
        try {
            val sealed = cipher.seal(bytes, AAD)
            check(sealed.size <= MAX_FILE_BYTES)
            check(directory.isDirectory || directory.mkdirs())
            val target = file
            val output = target.startWrite()
            try { output.write(sealed); target.finishWrite(output) }
            catch (failure: Exception) { target.failWrite(output); throw failure }
        } catch (_: Exception) {
            throw CredentialFailure(CredentialError.UNAVAILABLE)
        } finally {
            bytes.fill(0)
            MyDataCache.drop()
        }
    }

    fun delete() = synchronized(lock) {
        checkAccess()
        file.delete()
        MyDataCache.drop()
        if (file.baseFile.exists()) throw CredentialFailure(CredentialError.UNAVAILABLE)
    }

    companion object {
        const val DIRECTORY = "agent_my_data"
        const val FILE = "data.sealed"
        private const val KEY_ALIAS = "agent.mydata.v1"
        private val AAD = "AgentKeyboard/MyData/v1".toByteArray(Charsets.UTF_8)
        private const val MAX_FILE_BYTES = 16384
        private val lock = Any()
    }
}

/**
 * What the keyboard read from [MyDataStore], kept in memory only while the device stays unlocked:
 * [get] drops it as soon as it is asked while locked, and every save or delete drops it. Read only
 * when a field or a cue asks for own data, never at start.
 */
object MyDataCache {
    @Volatile private var cached: MyData? = null
    /** Replaced in tests. */
    @JvmStatic var store: (Context) -> MyDataStore = { MyDataStore(it) }

    /** The owner's data, or null while locked or unreadable. */
    fun get(context: Context, locked: Boolean): MyData? {
        if (locked) { drop(); return null }
        cached?.let { return it }
        val read = runCatching { store(context).read() }.getOrNull() ?: return null
        cached = read
        return read
    }

    fun drop() { cached = null }
}
