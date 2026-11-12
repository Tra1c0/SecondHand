// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent.commands

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import helium314.keyboard.agent.AgentStorageContexts
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.latin.utils.InputTypeUtils
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Which entries of the keyboard's clipboard history were sensitive when they were copied (review of
 * stage 24f). The upstream history stores every text clip without the sensitive mark, so `@clip`
 * could otherwise offer an old password once a newer clip replaced it as the primary clip.
 *
 * A clip counts as sensitive when the copying app marked it ([ClipboardManagerCompat.getClipSensitivity])
 * or when it was copied while a password field was the current editor. Its text is never stored here:
 * only a salted SHA-256 of it, at most [MAX_ENTRIES], oldest dropped first, in the device-protected
 * preferences [FILE] next to the history database (which holds the clip text itself). The history's
 * own behaviour stays unchanged; [ClipCommand] only skips matching entries.
 */
object SensitiveClips {
    const val FILE = "agent_sensitive_clips"
    private const val KEY_SALT = "salt"
    private const val KEY_HASHES = "hashes"
    /** Hashes kept at most; the history keeps few unpinned entries in practice. */
    const val MAX_ENTRIES = 200

    private fun prefs(context: Context) =
        AgentStorageContexts.keyboard(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Listens for new primary clips while the keyboard runs, like the history itself; returns the
     * call that stops listening. [editorInfo] gives the current editor. Only while the history is on.
     */
    @JvmStatic fun attach(context: Context, editorInfo: () -> EditorInfo?): () -> Unit {
        val historyEnabled = { ClipCommand.historyEnabled(context) }
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return {}
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            if (historyEnabled()) runCatching { note(context, manager.primaryClip, isPasswordField(editorInfo())) }
        }
        manager.addPrimaryClipChangedListener(listener)
        // the history also stores the current clip when the keyboard starts
        if (historyEnabled()) runCatching { note(context, manager.primaryClip, isPasswordField(editorInfo())) }
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
        remember(context, text.toString())
    }

    /** Adds the hash of [text]. */
    @Synchronized fun remember(context: Context, text: String) {
        val prefs = prefs(context)
        val hash = hash(salt(context), text)
        val hashes = prefs.getString(KEY_HASHES, "").orEmpty().split(',').filter { it.isNotEmpty() && it != hash }
        prefs.edit().putString(KEY_HASHES, (hashes + hash).takeLast(MAX_ENTRIES).joinToString(",")).apply()
    }

    /** A test for history texts; reads the stored hashes once. */
    @Synchronized fun matcher(context: Context): (String) -> Boolean {
        val prefs = prefs(context)
        val stored = prefs.getString(KEY_HASHES, null)
        if (stored.isNullOrEmpty()) return { false }
        val hashes = stored.split(',').toHashSet()
        val salt = salt(context)
        return { text -> hash(salt, text) in hashes }
    }

    /** Forgets every hash, e.g. in tests. */
    @Synchronized fun clear(context: Context) { prefs(context).edit().remove(KEY_HASHES).apply() }

    private fun salt(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_SALT, null)?.let { return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salt = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_SALT, salt).apply()
        return salt
    }

    private fun hash(salt: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        digest.update(0)
        return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
