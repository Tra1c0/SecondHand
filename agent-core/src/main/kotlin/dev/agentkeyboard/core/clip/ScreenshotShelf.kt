// SPDX-License-Identifier: GPL-3.0-only
package dev.agentkeyboard.core.clip

/**
 * Stage 102: the screenshots of the clipboard, as a model without Android (`docs/SCREENSHOTS-KONZEPT.md`).
 *
 * The screenshots themselves stay in the media store; the shelf only knows their ids and where each one lies: "fresh"
 * (new and not bundled yet), "loose" (put into the normal clipboard by the user, never bundled again) or in a folder.
 * Three fresh screenshots make the automatic folder; while it exists, every new screenshot goes into it, at the front.
 * A folder that is renamed or pinned belongs to the user: new screenshots then start fresh again (E4).
 *
 * Visible is a screenshot that exists and is pinned, lies in a pinned folder, or is younger than the retention and not
 * older than the last clearing ([isVisible]). [sync] applies a complete answer of the media store: it forgets ids the
 * store no longer has (never on an empty answer, which may be a failure), places new screenshots and, with `expire`,
 * forgets what is no longer visible (the clipboard does that only when its view opens, never while it is shown).
 *
 * Every operation returns a new state; an operation that changes nothing returns the same instance, so the caller can
 * tell a no-op ("a cancelled gesture changes nothing") by identity.
 */
data class ShelfShot(val id: Long, val addedMillis: Long)

data class ShelfFolder(
    val id: Long,
    /** The user's name, or null for the default name ([auto]: "Screenshots", else "Folder [number]"). */
    val name: String?,
    val number: Int,
    val pinned: Boolean,
    val auto: Boolean,
    /** In the user's order; new ones come first. */
    val members: List<Long>,
)

data class ShelfState(
    val folders: List<ShelfFolder> = emptyList(),
    /** Put into the normal clipboard by the user (dragged out of a folder or placed there). */
    val loose: List<Long> = emptyList(),
    /** New screenshots that are not bundled yet, newest first. */
    val fresh: List<Long> = emptyList(),
    val pinnedShots: Set<Long> = emptySet(),
    /** Screenshots added before this time (ms, whole seconds) were removed by "clear the clipboard", unless protected. */
    val clearedBefore: Long = 0L,
    val nextFolderId: Long = 1L,
    /**
     * Never shown (E10): screenshots the keyboard saw being taken while the device was locked, in incognito or in a private
     * (password, no-learning) field. Forgotten only when the media store no longer has them.
     */
    val hidden: Set<Long> = emptySet(),
) {
    fun folder(id: Long) = folders.firstOrNull { it.id == id }
    val autoFolder get() = folders.firstOrNull { it.auto }
    /** Where [shot] lies: the folder id, [LOOSE], [FRESH], or null when the shelf does not know it. */
    fun placeOf(shot: Long): Long? = when {
        shot in fresh -> FRESH
        shot in loose -> LOOSE
        else -> folders.firstOrNull { shot in it.members }?.id
    }
    fun isProtected(shot: Long) = shot in pinnedShots || folders.any { it.pinned && shot in it.members }
    /** The pinned screenshots and the members of pinned folders. */
    fun protectedIds(): Set<Long> = buildSet { addAll(pinnedShots); folders.forEach { if (it.pinned) addAll(it.members) } }
    /** All ids the shelf places. */
    fun placed(): Set<Long> = buildSet { addAll(fresh); addAll(loose); folders.forEach { addAll(it.members) } }

    override fun toString() = "ShelfState(folders=${folders.size}, loose=${loose.size}, fresh=${fresh.size}, pinned=${pinnedShots.size})"

    companion object {
        const val LOOSE = -1L
        const val FRESH = -2L
    }
}

/** What the clipboard shows of the shelf, in order. */
sealed class ShelfItem {
    abstract val pinned: Boolean
    abstract val recency: Long
    data class Folder(val folder: ShelfFolder, val shots: List<ShelfShot>) : ShelfItem() {
        override val pinned get() = folder.pinned
        override val recency get() = shots.maxOfOrNull { it.addedMillis } ?: 0L
    }
    data class Card(val shot: ShelfShot, override val pinned: Boolean, val fresh: Boolean) : ShelfItem() {
        override val recency get() = shot.addedMillis
    }
}

/** Where a dragged screenshot was let go, see [ScreenshotShelf.drop]. */
sealed class ShelfTarget {
    /** Nothing (outside the view, or no target): nothing changes. */
    object None : ShelfTarget()
    /** Another screenshot card: a new folder with both. */
    data class Card(val shot: Long) : ShelfTarget()
    data class Folder(val folder: Long) : ShelfTarget()
    object NewFolder : ShelfTarget()
    /** The normal clipboard: the list outside every tile, or "In die Ablage". */
    object Out : ShelfTarget()
    /** A new order of a folder, made while dragging inside it. */
    data class Order(val folder: Long, val members: List<Long>) : ShelfTarget()
}

object ScreenshotShelf {
    const val BUNDLE_AT = 3
    const val MAX_NAME = 40

    /** Visible now, see the class comment. [retentionMillis] null: no limit. */
    fun isVisible(state: ShelfState, shot: ShelfShot, now: Long, retentionMillis: Long?): Boolean =
        isVisible(state.protectedIds(), state.clearedBefore, shot, now, retentionMillis)

    private fun isVisible(safe: Set<Long>, clearedBefore: Long, shot: ShelfShot, now: Long, retentionMillis: Long?) =
        shot.id in safe || (shot.addedMillis >= clearedBefore && (retentionMillis == null || now - shot.addedMillis <= retentionMillis))

    private val newestFirst = compareByDescending<ShelfShot> { it.addedMillis }.thenByDescending { it.id }

    /**
     * Applies a complete answer of the media store ([shots]: every screenshot that exists). [expire]: also forget what is
     * no longer visible (only while the view is not shown).
     */
    fun sync(state: ShelfState, shots: List<ShelfShot>, now: Long, retentionMillis: Long?, expire: Boolean): ShelfState {
        val byId = shots.associateBy { it.id }
        var next = state
        // an empty answer may be a failure (no permission, a busy store): it never deletes anything
        if (shots.isNotEmpty()) next = next.keepOnly { it in byId }.let { kept ->
            if (kept.hidden.all { it in byId }) kept else kept.copy(hidden = kept.hidden.filterTo(HashSet()) { it in byId })
        }
        if (expire) {
            val safe = next.protectedIds()
            val cleared = next.clearedBefore
            next = next.keepOnly { id -> byId[id]?.let { isVisible(safe, cleared, it, now, retentionMillis) } ?: true }
        }
        val placed = next.placed()
        val safe = next.protectedIds()
        val incoming = shots.filter { it.id !in placed && it.id !in next.hidden && isVisible(safe, next.clearedBefore, it, now, retentionMillis) }
            .sortedWith(newestFirst).map { it.id }
        if (incoming.isNotEmpty()) {
            val auto = next.autoFolder
            next = if (auto != null) next.withFolder(auto.copy(members = incoming + auto.members))
            else next.copy(fresh = (incoming + next.fresh).distinct())
        }
        if (next.autoFolder == null && next.fresh.size >= BUNDLE_AT) {
            val members = next.fresh.mapNotNull { byId[it] }.sortedWith(newestFirst).map { it.id } +
                next.fresh.filter { it !in byId } // not in this answer (empty answer): kept, after the known ones
            next = next.copy(fresh = emptyList(), nextFolderId = next.nextFolderId + 1,
                folders = next.folders + ShelfFolder(next.nextFolderId, null, 0, pinned = false, auto = true, members = members))
        }
        val known = next.placed()
        next = next.withoutEmptyFolders().copy(pinnedShots = next.pinnedShots.filterTo(HashSet()) { it in known })
        return if (next == state) state else next
    }

    /** What the clipboard shows: folders with their existing screenshots, then cards; pinned first if [pinnedFirst] (else last, as the clipboard). */
    fun items(state: ShelfState, shots: List<ShelfShot>, pinnedFirst: Boolean): List<ShelfItem> {
        val byId = shots.associateBy { it.id }
        val items = ArrayList<ShelfItem>()
        for (folder in state.folders) {
            val members = folder.members.mapNotNull { byId[it] }
            if (members.isNotEmpty() || folder.pinned) items += ShelfItem.Folder(folder, members)
        }
        for (id in state.loose) byId[id]?.let { items += ShelfItem.Card(it, id in state.pinnedShots, fresh = false) }
        for (id in state.fresh) byId[id]?.let { items += ShelfItem.Card(it, id in state.pinnedShots, fresh = true) }
        val pinned = compareBy<ShelfItem> { if (it.pinned == pinnedFirst) 0 else 1 }
        return items.sortedWith(pinned.thenByDescending { it.recency }.thenBy { if (it is ShelfItem.Folder) 0 else 1 }
            .thenByDescending { when (it) { is ShelfItem.Folder -> it.folder.id; is ShelfItem.Card -> it.shot.id } })
    }

    /** The screenshots of [folder] that exist, in its order. */
    fun folderShots(state: ShelfState, folder: Long, shots: List<ShelfShot>): List<ShelfShot> {
        val byId = shots.associateBy { it.id }
        return state.folder(folder)?.members?.mapNotNull { byId[it] }.orEmpty()
    }

    fun togglePinShot(state: ShelfState, shot: Long): ShelfState {
        if (state.placeOf(shot) == null) return state
        val pins = HashSet(state.pinnedShots)
        if (!pins.remove(shot)) pins += shot
        return state.copy(pinnedShots = pins)
    }

    /** Pinning (or unpinning) a folder makes it the user's: the automatic folder stops taking new screenshots. */
    fun togglePinFolder(state: ShelfState, folder: Long): ShelfState {
        val found = state.folder(folder) ?: return state
        return state.withFolder(found.copy(pinned = !found.pinned, auto = false)).withoutEmptyFolders()
    }

    /** A clean name ([MAX_NAME] UTF-16 units after trimming, one line, no control character), or null. */
    fun cleanName(name: String): String? {
        val clean = name.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty() || clean.length > MAX_NAME || clean.any { Character.isISOControl(it) }) return null
        for (i in clean.indices) {
            val c = clean[i]
            if (Character.isHighSurrogate(c) && (i + 1 >= clean.length || !Character.isLowSurrogate(clean[i + 1]))) return null
            if (Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(clean[i - 1]))) return null
        }
        return clean
    }

    /** Renames [folder] (it becomes the user's); an unusable name changes nothing. */
    fun rename(state: ShelfState, folder: Long, name: String): ShelfState {
        val found = state.folder(folder) ?: return state
        val clean = cleanName(name) ?: return state
        if (clean == found.name && !found.auto) return state
        return state.withFolder(found.copy(name = clean, auto = false))
    }

    fun moveToFolder(state: ShelfState, shot: Long, folder: Long): ShelfState {
        val target = state.folder(folder) ?: return state
        if (state.placeOf(shot) == null || shot in target.members) return state
        val removed = state.without(shot)
        return removed.withFolder(removed.folder(folder)!!.let { it.copy(members = listOf(shot) + it.members) }).withoutEmptyFolders()
    }

    fun moveToLoose(state: ShelfState, shot: Long): ShelfState {
        val place = state.placeOf(shot) ?: return state
        if (place == ShelfState.LOOSE) return state
        val removed = state.without(shot)
        return removed.copy(loose = listOf(shot) + removed.loose).withoutEmptyFolders()
    }

    /** A new folder of the user with [shots] in this order (the first is the drop target); returns the state and the folder's id. */
    fun newFolder(state: ShelfState, shots: List<Long>): Pair<ShelfState, Long?> {
        val ids = shots.distinct()
        if (ids.isEmpty() || ids.any { state.placeOf(it) == null }) return state to null
        var next = state
        for (id in ids) next = next.without(id)
        next = next.withoutEmptyFolders() // a folder this drop empties frees its number (review of 102, finding 9)
        val used = next.folders.filter { !it.auto }.mapTo(HashSet()) { it.number }
        val number = generateSequence(1) { it + 1 }.first { it !in used }
        val id = next.nextFolderId
        next = next.copy(nextFolderId = id + 1, folders = next.folders + ShelfFolder(id, null, number, pinned = false, auto = false, members = ids))
        return next.withoutEmptyFolders() to id
    }

    /** Only a new order of exactly the folder's members is taken. */
    fun reorder(state: ShelfState, folder: Long, members: List<Long>): ShelfState {
        val found = state.folder(folder) ?: return state
        if (members == found.members || members.size != found.members.size || members.toSet() != found.members.toSet()) return state
        return state.withFolder(found.copy(members = members))
    }

    /**
     * The drop of [shot] on [target]. On its own card or its own folder, outside every target, or into the place it already
     * has, nothing changes (the same instance is returned). Returns the state and, for a new folder, its id.
     */
    fun drop(state: ShelfState, shot: Long, target: ShelfTarget): Pair<ShelfState, Long?> = when (target) {
        ShelfTarget.None -> state to null
        is ShelfTarget.Card -> if (target.shot == shot || state.placeOf(target.shot) == null) state to null
            else newFolder(state, listOf(target.shot, shot))
        is ShelfTarget.Folder -> moveToFolder(state, shot, target.folder) to null
        ShelfTarget.NewFolder -> newFolder(state, listOf(shot))
        ShelfTarget.Out -> moveToLoose(state, shot) to null
        is ShelfTarget.Order -> reorder(state, target.folder, target.members) to null
    }

    /** "Clear the clipboard": unprotected screenshots added before [now] (whole seconds) leave it; [sync] with `expire` removes them. */
    fun clear(state: ShelfState, now: Long): ShelfState = state.copy(clearedBefore = now / 1000 * 1000)

    /** Switching the screenshots or the history off: only what is pinned (and pinned folders) stays. */
    fun keepPinnedOnly(state: ShelfState, now: Long): ShelfState {
        val kept = state.copy(
            fresh = state.fresh.filter { it in state.pinnedShots },
            loose = state.loose.filter { it in state.pinnedShots },
            folders = state.folders.map { f -> if (f.pinned) f else f.copy(members = f.members.filter { it in state.pinnedShots }, auto = false) },
            clearedBefore = now / 1000 * 1000,
        )
        return kept.withoutEmptyFolders()
    }

    /** How long after it was taken a media change still counts as the one that reported a screenshot (as the history before 102). */
    const val PRIVATE_WINDOW_MILLIS = 60_000L

    /**
     * E10: the screenshots of [shots] that a media change at one of [privateChanges] (wall clock) reported while the keyboard saw
     * a private state: taken at most [PRIVATE_WINDOW_MILLIS] before the change (the store's time is whole seconds, floored, so a
     * screenshot is never later than the change that reports it; one taken after it has a change of its own) and not known to the
     * shelf yet. A screenshot the shelf already shows is never taken away by a later change.
     */
    fun takenPrivately(state: ShelfState, shots: List<ShelfShot>, privateChanges: List<Long>): Set<Long> {
        if (privateChanges.isEmpty()) return emptySet()
        val known = state.placed()
        return shots.filter { shot -> shot.id !in known && shot.id !in state.hidden &&
            privateChanges.any { change -> shot.addedMillis in (change - PRIVATE_WINDOW_MILLIS)..change } }
            .mapTo(HashSet()) { it.id }
    }

    /** Hides [ids] for good (E10). */
    fun hide(state: ShelfState, ids: Set<Long>): ShelfState {
        if (ids.isEmpty() || ids.all { it in state.hidden }) return state
        return state.keepOnly { it !in ids }.copy(hidden = state.hidden + ids)
    }

    /** Places [shot] as a pinned loose screenshot if the shelf does not know it yet, else pins it where it lies (migration, K4). */
    fun adoptPinned(state: ShelfState, shot: Long): ShelfState {
        val placed = if (state.placeOf(shot) == null) state.copy(loose = listOf(shot) + state.loose) else state
        return if (shot in placed.pinnedShots) placed else placed.copy(pinnedShots = placed.pinnedShots + shot)
    }

    /** A screenshot copy in the clipboard history from before stage 102 (`agent_screenshot_history_entries` names it). */
    data class OldCopy(val entryId: Long, val time: Long, val pinned: Boolean, val unchanged: Boolean, val checksum: String?)

    /**
     * Migration (E13, K4): the unchanged, unpinned old copies with a screenshot of exactly their time (candidates: the caller
     * compares the checksum before anything goes), and the pinned ones for the checksum search. Copies without an original stay.
     */
    fun planMigration(copies: List<OldCopy>, shots: List<ShelfShot>): Pair<List<OldCopy>, List<OldCopy>> {
        val times = shots.mapTo(HashSet()) { it.addedMillis }
        val drop = copies.filter { it.unchanged && !it.pinned && it.checksum != null && it.time in times }
        val search = copies.filter { it.pinned && it.checksum != null }
        return drop to search
    }

    // ---- helpers

    private fun ShelfState.keepOnly(keep: (Long) -> Boolean) = copy(
        fresh = fresh.filter(keep), loose = loose.filter(keep),
        folders = folders.map { it.copy(members = it.members.filter(keep)) },
        pinnedShots = pinnedShots.filterTo(HashSet(), keep),
    ).withoutEmptyFolders()

    private fun ShelfState.without(shot: Long) = copy(
        fresh = fresh.filter { it != shot }, loose = loose.filter { it != shot },
        folders = folders.map { if (shot in it.members) it.copy(members = it.members - shot) else it },
    )

    private fun ShelfState.withFolder(folder: ShelfFolder) = copy(folders = folders.map { if (it.id == folder.id) folder else it })

    private fun ShelfState.withoutEmptyFolders() = copy(folders = folders.filter { it.members.isNotEmpty() || it.pinned })
}
