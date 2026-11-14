// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.agent

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import dev.agentkeyboard.core.clip.ClipCategory
import dev.agentkeyboard.core.clip.ClipClass
import dev.agentkeyboard.core.clip.ClipGuard
import dev.agentkeyboard.core.clip.ClipKind
import dev.agentkeyboard.core.format.PhoneFormat
import helium314.keyboard.agent.commands.ClipCommand
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils

/**
 * Clipboard vault (stage 44), memory only. Every new text clip of the opt-in clipboard history is
 * sorted by [ClipGuard] ([offer]); secrets and one-time codes never go into the history database,
 * they live here: secrets until their first paste from the vault, at the latest
 * [ClipGuard.SECRET_MILLIS] after they were copied, codes [ClipGuard.OTP_MILLIS]. Other kinds stay
 * here [FRESH_MILLIS] for [freshest] (stage 45: "fresh code", "fresh link", "personal item of kind
 * X"). Nothing here is written to disk or logged; [Item.toString] never shows the text.
 *
 * Locked device, incognito and password fields: [isBlocked] — no vault section, no insertion, and
 * nothing is taken in while locked or incognito ([offer] with `allowed = false`).
 */
object ClipVault {
    /** Normal and personal clips are kept this long for [freshest]. */
    const val FRESH_MILLIS = 10 * 60 * 1000L
    /** Items kept at most; the oldest go first. */
    const val MAX_ITEMS = 20

    class Item internal constructor(
        val id: Long,
        val kind: ClipKind,
        internal val text: String,
        /** The code alone for [ClipKind.OTP]. */
        val code: String?,
        /** When the clip was copied (clip time stamp, wall clock). */
        val copiedAt: Long,
    ) {
        /** What the screen and TalkBack may show: masked for secrets, the code for one-time codes. */
        val shown: String get() = when (kind) {
            ClipKind.SECRET -> ClipGuard.mask(text)
            ClipKind.OTP -> code ?: text
            else -> text
        }
        /** What a tap inserts: the code for one-time codes, else the clip. */
        internal val insertText: String get() = if (kind == ClipKind.OTP) code ?: text else text
        override fun toString() = "ClipVault.Item($id, $kind)"
    }

    /** For stage 45: a fresh clip of the asked kind, never a secret. */
    class Fresh(val kind: ClipKind, val text: String, val ageMillis: Long) {
        override fun toString() = "ClipVault.Fresh($kind, ${ageMillis}ms)"
    }

    /** Wall clock, replaced in tests. */
    @JvmStatic var clock: () -> Long = { System.currentTimeMillis() }
    /** Called on the main thread when items expire or change, e.g. to refresh the open history view. */
    @JvmStatic var onChange: (() -> Unit)? = null
    /**
     * The current primary clip for [freshest] when the history is off (read live, never kept):
     * text, copy time and whether it was marked sensitive. Set by the clipboard history manager.
     */
    @JvmStatic var currentClip: (() -> Triple<String, Long, Boolean>?)? = null

    private val items = ArrayList<Item>()
    /** Copy times of secrets already pasted from the vault, so that re-reading the same clip does not bring them back. */
    private val used = LinkedHashSet<Long>()
    /** Copy times of clips sorted as secrets (kept or not), so that [freshest] never offers them from the live clip. */
    private val secretStamps = LinkedHashSet<Long>()
    private var nextId = 1L
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val expire = Runnable { if (prune()) onChange?.invoke() ; schedule() }

    fun lifetime(kind: ClipKind) = when (kind.category) {
        ClipCategory.SECRET -> ClipGuard.SECRET_MILLIS
        ClipCategory.OTP -> ClipGuard.OTP_MILLIS
        else -> FRESH_MILLIS
    }

    /** Region for phone numbers from the keyboard language. */
    fun region(settings: SettingsValues?): String =
        settings?.mLocale?.let { PhoneFormat.region(it.language, it.country) } ?: "DE"

    /**
     * Sorts a new text clip and keeps it in memory when [allowed] (history on, device unlocked, no
     * incognito). Returns the verdict; the caller must not write [ClipCategory.SECRET] or
     * [ClipCategory.OTP] clips to the history, also when they were not kept here.
     */
    @Synchronized
    fun offer(text: String, markedSensitive: Boolean, copiedAt: Long, region: String, allowed: Boolean): ClipClass {
        val result = ClipGuard.classify(text, markedSensitive, region)
        prune()
        if (result.kind == ClipKind.SECRET) {
            secretStamps.add(copiedAt)
            while (secretStamps.size > MAX_ITEMS) secretStamps.remove(secretStamps.first())
        }
        if (!allowed || text.isBlank()) return result
        val now = clock()
        if (now - copiedAt >= lifetime(result.kind)) return result // e.g. the old clip read again when the keyboard starts
        if (result.kind == ClipKind.SECRET && copiedAt in used) return result
        items.removeAll { it.text == text }
        items.add(0, Item(nextId++, result.kind, text, result.code, copiedAt))
        while (items.size > MAX_ITEMS) items.removeAt(items.lastIndex)
        schedule()
        onChange?.invoke()
        return result
    }

    /** Secrets to show (masked), newest first. */
    @Synchronized fun secrets(): List<Item> { prune(); return items.filter { it.kind == ClipKind.SECRET } }

    /** One-time codes to show, newest first. */
    @Synchronized fun codes(): List<Item> { prune(); return items.filter { it.kind == ClipKind.OTP } }

    /**
     * The item [id] for insertion by the user's tap, or null if it is gone. A secret leaves the vault
     * now (first paste); a code stays until it expires.
     */
    @Synchronized fun take(id: Long): Item? {
        prune()
        val item = items.firstOrNull { it.id == id } ?: return null
        if (item.kind == ClipKind.SECRET) {
            items.remove(item)
            used.add(item.copiedAt)
            while (used.size > MAX_ITEMS) used.remove(used.first())
        }
        return item
    }

    /**
     * For stage 45: the newest clip of [kind] at most [maxAgeMillis] old, never a secret. With the
     * history on from the vault, otherwise only the current primary clip, read and sorted now.
     */
    fun freshest(context: Context, kind: ClipKind, maxAgeMillis: Long): Fresh? {
        if (kind.category == ClipCategory.SECRET) return null
        val now = clock()
        if (ClipCommand.historyEnabled(context)) {
            synchronized(this) {
                prune()
                items.firstOrNull { it.kind == kind && now - it.copiedAt <= maxAgeMillis }
                    ?.let { return Fresh(kind, it.insertText, now - it.copiedAt) }
            }
        }
        val (text, copiedAt, marked) = runCatching { currentClip?.invoke() }.getOrNull() ?: return null
        if (now - copiedAt > maxAgeMillis) return null
        // the live clip has only the app's mark: a clip copied in a password field, or one the vault
        // sorted as secret (kept, pasted or not), is never offered (review of stage 44, B1)
        if (isSecretClip(text, copiedAt, marked)) return null
        val result = ClipGuard.classify(text, false)
        if (result.kind != kind) return null
        return Fresh(kind, if (kind == ClipKind.OTP) result.code ?: text else text, now - copiedAt)
    }

    /**
     * Whether the clip [text] copied at [copiedAt] is a secret: marked by the app ([marked]), copied
     * in a password field ([helium314.keyboard.agent.commands.SensitiveClips]), sorted as secret by
     * [offer] before, or a secret by its content. For the live clip (field suggestions, strip chip).
     */
    fun isSecretClip(text: String, copiedAt: Long, marked: Boolean): Boolean {
        if (marked || helium314.keyboard.agent.commands.SensitiveClips.isSensitive(text)) return true
        if (synchronized(this) { copiedAt in secretStamps }) return true
        return ClipGuard.classify(text).kind == ClipKind.SECRET
    }

    /**
     * No vault in these states: device locked, incognito (also no-learning fields), password fields,
     * unknown field.
     */
    fun isBlocked(context: Context, settings: SettingsValues?, editorInfo: EditorInfo?): Boolean {
        if (settings == null || settings.mIncognitoModeEnabled || settings.mIsLocked) return true
        if (helium314.keyboard.compat.isDeviceLocked(context)) return true
        val inputType = editorInfo?.inputType ?: return true
        return InputTypeUtils.isAnyPasswordInputType(inputType)
    }

    /** Whether a clip seen now may be kept: device unlocked and no incognito. */
    fun mayKeep(context: Context, settings: SettingsValues?): Boolean =
        settings != null && !settings.mIncognitoModeEnabled && !helium314.keyboard.compat.isDeviceLocked(context)

    /**
     * After a secret was pasted from the vault: clears the system clipboard, but only if it still
     * holds exactly that clip (same text and, from Android 8, same copy time). Writing the clipboard
     * needs no focus (AOSP `ClipboardService`, `OP_WRITE_CLIPBOARD`); a refusal is silent, so the
     * result is read back. Returns true when the clipboard no longer holds the secret.
     */
    fun clearSystemClip(context: Context, item: Item): Boolean = runCatching {
        if (item.kind != ClipKind.SECRET) return false
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = manager.primaryClip ?: return false
        if (clip.itemCount == 0) return false
        if (clip.getItemAt(0)?.coerceToText(context)?.toString() != item.text) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && clip.description.timestamp > 0
            && clip.description.timestamp != item.copiedAt) return false
        ClipboardManagerCompat.clearPrimaryClip(manager)
        val after = manager.primaryClip
        after == null || after.itemCount == 0 || after.getItemAt(0)?.coerceToText(context)?.toString() != item.text
    }.getOrDefault(false)

    /** Forgets everything (tests, history cleared). */
    @Synchronized fun clear() {
        items.clear()
        used.clear()
        secretStamps.clear()
        handler.removeCallbacks(expire)
    }

    /** Removes expired items; true if one was removed. */
    @Synchronized private fun prune(): Boolean {
        val now = clock()
        return items.removeAll { now - it.copiedAt >= lifetime(it.kind) || it.copiedAt > now + FRESH_MILLIS }
    }

    /** Wakes up when the next item expires, so that nothing stays longer than its lifetime. */
    @Synchronized private fun schedule() {
        handler.removeCallbacks(expire)
        val now = clock()
        val next = items.minOfOrNull { it.copiedAt + lifetime(it.kind) } ?: return
        handler.postDelayed(expire, (next - now).coerceIn(0L, FRESH_MILLIS) + 50L)
    }
}
