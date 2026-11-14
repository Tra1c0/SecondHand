// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import helium314.keyboard.agent.AgentStorageContexts
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.utils.InputTypeUtils
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Which clips were sensitive when they were copied (review of stage 24f): marked so by the copying
 * app ([ClipboardManagerCompat.getClipSensitivity]) or copied while a password field was the current
 * editor. The system clip keeps only the app's mark, so `@clip`, `{zwischenablage}`, the clipboard
 * strip chip and the field suggestions (stage 45) ask here as well.
 *
 * Memory only since the review of stages 44/45 (B3): a salted SHA-256 of each such text with a salt
 * made per process, at most [MAX_ENTRIES], oldest dropped first, nothing on disk. A restart of the
 * keyboard forgets them; that is enough because since stage 44 such clips never reach the history
 * database ([helium314.keyboard.agent.ClipVault] sorts them as secrets), so only the current system
 * clip can still be one, and its own mark or the vault decides. The hashes stage 24f kept in the
 * device-protected preferences [FILE] (readable before the first unlock, a fast hash with its salt
 * beside it) are used once by [migrateLegacy] and then deleted, except those of pinned history
 * entries, whose text lies in the history database next to it anyway.
 */
object SensitiveClips {
    /** The preferences of the stage 24f hashes; only read by [migrateLegacy] and [matcher], never written. */
    const val FILE = "agent_sensitive_clips"
    private const val KEY_SALT = "salt"
    private const val KEY_HASHES = "hashes"
    /** Hashes kept at most. */
    const val MAX_ENTRIES = 200

    private val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private val hashes = LinkedHashSet<String>()

    private fun prefs(context: Context) =
        AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Listens for new primary clips while the keyboard runs; returns the call that stops listening.
     * [editorInfo] gives the current editor. Also with the history off: nothing is stored on disk,
     * and the field suggestions read the current clip with the history off as well.
     */
    @JvmStatic fun attach(context: Context, editorInfo: () -> EditorInfo?): () -> Unit {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return {}
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            runCatching { note(context, manager.primaryClip, isPasswordField(editorInfo())) }
        }
        manager.addPrimaryClipChangedListener(listener)
        // the clip that is current when the keyboard starts
        runCatching { note(context, manager.primaryClip, isPasswordField(editorInfo())) }
        return { manager.removePrimaryClipChangedListener(listener) }
    }

    private fun isPasswordField(info: EditorInfo?) = info != null && InputTypeUtils.isAnyPasswordInputType(info.inputType)

    /** Remembers the text of [clip] if it is sensitive; non-text clips are left alone (`@clip` shows text only). */
    fun note(context: Context, clip: ClipData?, inPasswordField: Boolean) {
        if (clip == null || clip.itemCount == 0) return
        val marked = ClipboardManagerCompat.getClipSensitivity(clip.description) == true
        if (!marked && !inPasswordField) return
        if (clip.description?.hasMimeType("text/*") != true) return
        // the same conversion as the history uses when it stores the clip
        val text = clip.getItemAt(0)?.coerceToText(context)
        if (TextUtils.isEmpty(text)) return
        remember(text.toString())
    }

    /** Adds [text] (as its hash, in memory). */
    @Synchronized fun remember(text: String) {
        val hash = hash(salt, text)
        hashes.remove(hash)
        hashes.add(hash)
        while (hashes.size > MAX_ENTRIES) hashes.remove(hashes.first())
    }

    /** Whether [text] was copied sensitive while this keyboard process ran. */
    @Synchronized fun isSensitive(text: String): Boolean = hashes.isNotEmpty() && hash(salt, text) in hashes

    /** A test for history texts: the clips noted in memory and the pinned ones of the stage 24f record. */
    @Synchronized fun matcher(context: Context): (String) -> Boolean {
        val noted = HashSet(hashes)
        val legacy = runCatching { prefs(context) }.getOrNull()
        val legacySalt = legacy?.getString(KEY_SALT, null)
        val legacyHashes = legacy?.getString(KEY_HASHES, null)?.split(',')?.filter { it.isNotEmpty() }?.toHashSet().orEmpty()
        if (noted.isEmpty() && (legacySalt == null || legacyHashes.isEmpty())) return { false }
        return { text ->
            (noted.isNotEmpty() && hash(salt, text) in noted) ||
                (legacySalt != null && legacyHashes.isNotEmpty() && legacyHash(legacySalt, text) in legacyHashes)
        }
    }

    /**
     * Once after the update (and at every start while the file exists): unpinned history entries
     * the stage 24f record matches are deleted, the hashes of pinned ones stay, every other hash and
     * the file itself go. Only while the history view is not shown. True if something was deleted.
     */
    @Synchronized fun migrateLegacy(context: Context, dao: ClipboardDao?): Boolean {
        // no history readable (e.g. before the first unlock): keep the file for a later start (second review M2)
        if (dao == null) return false
        val prefs = runCatching { prefs(context) }.getOrNull() ?: return false
        if (prefs.all.isEmpty()) return false
        val legacySalt = prefs.getString(KEY_SALT, null)
        val old = prefs.getString(KEY_HASHES, null)?.split(',')?.filter { it.isNotEmpty() }?.toHashSet().orEmpty()
        val keep = HashSet<String>()
        var deleted = false
        if (legacySalt != null && old.isNotEmpty()) {
            for (index in dao.count() - 1 downTo 0) {
                val entry = dao.getAt(index)
                val text = entry.text
                if (entry.filename != null || text.isNullOrEmpty()) continue
                val hash = legacyHash(legacySalt, text)
                if (hash !in old) continue
                if (entry.isPinned) keep += hash
                else { dao.deleteClipAt(index); deleted = true }
            }
        }
        if (keep.isEmpty()) {
            prefs.edit().clear().commit()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                runCatching { AgentStorageContexts.keyboard(context).deleteSharedPreferences(FILE) }
            return true
        }
        if (keep.size != old.size) {
            prefs.edit().putString(KEY_HASHES, keep.joinToString(",")).commit()
            return true
        }
        return deleted
    }

    /** Forgets every hash, in memory and the stage 24f file (history cleared, tests). */
    @Synchronized fun clear(context: Context) {
        hashes.clear()
        runCatching { prefs(context).edit().clear().commit() }
    }

    private fun hash(salt: ByteArray, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(0)
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** The hash of stage 24f: the salt as its hex string. */
    private fun legacyHash(salt: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        digest.update(0)
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
