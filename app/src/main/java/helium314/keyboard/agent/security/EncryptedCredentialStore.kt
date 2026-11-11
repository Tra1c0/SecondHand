// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.security

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import android.app.KeyguardManager
import android.os.UserManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

enum class CredentialSlot { OPENAI }
enum class CredentialError { LOCKED, UNSUPPORTED, INVALID_KEY, UNAVAILABLE }
class CredentialFailure(val error: CredentialError) : Exception(error.name)

/** Best-effort erasure of the mutable copy. Platform HTTP APIs still require a temporary String. */
class Secret internal constructor(private val bytes: ByteArray) : AutoCloseable {
    private var closed = false
    fun value(): String { check(!closed); return bytes.toString(Charsets.UTF_8) }
    override fun close() { bytes.fill(0); closed = true }
    override fun toString() = "Secret(redacted)"
}

fun credentialContext(context: Context): Context {
    val app = context.applicationContext as? helium314.keyboard.latin.App
    val storage = app?.credentialStorageContext ?: context
    check(Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !storage.isDeviceProtectedStorage)
    return storage
}

/** Fixed slots and bounded atomic files; never uses the keyboard's device-protected preferences. */
class EncryptedCredentialStore(
    context: Context,
    cipher: SecretCipher? = null,
    private val unlocked: () -> Boolean = { credentialAccessAllowed(context) },
) {
    private val storageContext = credentialContext(context.applicationContext)
    private val directory get() = File(storageContext.noBackupFilesDir, "agent_credentials")
    private val cipher = cipher ?: androidSecretCipher { directory.listFiles().isNullOrEmpty() }

    private fun checkAccess() {
        if (!supportsSdk(Build.VERSION.SDK_INT)) throw CredentialFailure(CredentialError.UNSUPPORTED)
        if (!runCatching(unlocked).getOrDefault(false)) throw CredentialFailure(CredentialError.LOCKED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && storageContext.isDeviceProtectedStorage)
            throw CredentialFailure(CredentialError.UNAVAILABLE)
    }
    private fun file(slot: CredentialSlot) = AtomicFile(File(directory, slot.name.lowercase(java.util.Locale.ROOT) + ".sealed"))
    private fun aad(slot: CredentialSlot) = "AgentKeyboard/Credential/v1/${slot.name}".toByteArray(Charsets.UTF_8)

    fun contains(slot: CredentialSlot): Boolean = synchronized(lock) {
        checkAccess()
        file(slot).baseFile.exists() || File(file(slot).baseFile.path + ".bak").exists()
    }

    fun save(slot: CredentialSlot, key: CharArray) = synchronized(lock) {
        checkAccess()
        if (key.isEmpty() || key.size > 4096 || key.any { it.code !in 33..126 })
            throw CredentialFailure(CredentialError.INVALID_KEY)
        val bytes = key.concatToString().toByteArray(Charsets.UTF_8)
        try {
            val encrypted = cipher.seal(bytes, aad(slot))
            check(encrypted.size <= MAX_FILE_BYTES)
            check(directory.isDirectory || directory.mkdirs())
            val target = file(slot)
            val output = target.startWrite()
            try { output.write(encrypted); target.finishWrite(output) }
            catch (failure: Exception) { target.failWrite(output); throw failure }
        } catch (_: Exception) {
            throw CredentialFailure(CredentialError.UNAVAILABLE)
        } finally { bytes.fill(0) }
    }

    fun read(slot: CredentialSlot): Secret? = synchronized(lock) {
        checkAccess()
        if (!contains(slot)) return@synchronized null
        try {
            val encrypted = file(slot).openRead().use { it.readBounded(MAX_FILE_BYTES) }
            val plain = cipher.open(encrypted, aad(slot))
            if (plain.isEmpty() || plain.size > 4096 || plain.any { it.toInt() !in 33..126 }) {
                plain.fill(0)
                throw CredentialFailure(CredentialError.UNAVAILABLE)
            }
            Secret(plain)
        } catch (_: Exception) { throw CredentialFailure(CredentialError.UNAVAILABLE) }
    }

    fun delete(slot: CredentialSlot) = synchronized(lock) {
        checkAccess()
        file(slot).delete()
        if (contains(slot)) throw CredentialFailure(CredentialError.UNAVAILABLE)
    }

    companion object {
        fun supportsSdk(sdk: Int): Boolean = sdk >= Build.VERSION_CODES.M
        private val lock = Any()
        private const val MAX_FILE_BYTES = 8192
    }
}

internal fun credentialAccessAllowed(context: Context): Boolean = runCatching {
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return@runCatching false
    if (keyguard.isKeyguardLocked) return@runCatching false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && keyguard.isDeviceLocked) return@runCatching false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val users = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return@runCatching false
        if (!users.isUserUnlocked) return@runCatching false
    }
    true
}.getOrDefault(false)

internal fun InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(minOf(limit + 1, 8192))
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (count < 0) break
        require(output.size().toLong() + count <= limit)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
