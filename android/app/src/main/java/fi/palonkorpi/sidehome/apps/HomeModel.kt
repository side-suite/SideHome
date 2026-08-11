package fi.palonkorpi.sidehome.apps

import fi.palonkorpi.sidehome.i18n.tr

/**
 * The home page model: an ordered, capped list of apps and folders. Folders are a
 * launcher-owned construct (no system limit) — a named group of apps with one glyph,
 * not a mini-stack of member icons.
 */
sealed interface HomeItem

data class AppItem(val key: String) : HomeItem

data class FolderItem(
    val name: String,
    val appKeys: List<String>,
    val glyph: String = "folder",
) : HomeItem

/** Every app key referenced on the home page, whether loose or inside a folder. */
fun List<HomeItem>.placedKeys(): Set<String> {
    val s = HashSet<String>()
    for (it in this) when (it) {
        is AppItem -> s.add(it.key)
        is FolderItem -> s.addAll(it.appKeys)
    }
    return s
}

/**
 * Drop the item at [index]. Out-of-range is a no-op rather than a throw: the §1 action strip holds
 * the index it was raised on, and the home can change under an open strip.
 */
fun List<HomeItem>.removeIndex(index: Int): List<HomeItem> =
    if (index !in indices) this else filterIndexed { i, _ -> i != index }

/**
 * What a folder is called when nothing better is known. [folderIdentityFor] decides when a
 * drag-combine can do better than this.
 */
val DEFAULT_FOLDER_NAME: String get() = tr("Folder", "Ordner")
const val DEFAULT_FOLDER_GLYPH = "folder"

/**
 * The folder at [index], or null if that slot holds an app or nothing at all.
 *
 * Both remove paths — the strip's Remove cell and the drag's remove pill — ask this to decide
 * whether the removal needs a confirm, so the two cannot drift into disagreeing about what is
 * curated enough to deserve a question (SPEC §1).
 */
fun List<HomeItem>.folderAt(index: Int): FolderItem? = getOrNull(index) as? FolderItem

/**
 * Move the item at [from] into the gap before [toGap], where the gap is counted in *this* list's
 * indices (0 = above everything, [size] = below everything) — i.e. the gap you point at, before the
 * item leaves its old slot. Out-of-range is a no-op rather than a throw: the in-hand item's source
 * index is held across a drag, and the home can change underneath it.
 */
fun List<HomeItem>.moveIndex(from: Int, toGap: Int): List<HomeItem> {
    if (from !in indices) return this
    val gap = toGap.coerceIn(0, size)
    val out = toMutableList()
    val item = out.removeAt(from)
    // Removing the item first shifts every gap below it up by one.
    out.add(if (gap > from) gap - 1 else gap, item)
    return out
}

/**
 * Drop the *app* at [from] onto the *app* at [onto] → the two become one folder (SPEC §1). This is
 * the move that makes "home full" resolvable: two items become one, so a slot always comes free.
 *
 * Both ends must be apps. A folder in hand does not combine — it is already the group, and having it
 * swallow whatever it lands on reads as the folder eating the home rather than being moved around
 * it; grouping is something you do *to* a folder by dropping an app on it ([nestIntoFolder]). A
 * folder as the *target* nests rather than combines, which is also [nestIntoFolder]'s.
 *
 * A no-op if either index is stale, if they are the same item, or if either end is a folder. The
 * resolver never asks for those (`resolveDropTarget` declines them, so no accept ring is ever shown
 * for a drop that would land here and do nothing) — these guards are the backstop, not the UI.
 */
fun List<HomeItem>.combineIntoFolder(
    from: Int,
    onto: Int,
    categoryOf: (String) -> Category? = { null },
): List<HomeItem> {
    if (from !in indices || onto !in indices || from == onto) return this
    val target = this[onto] as? AppItem ?: return this
    val dragged = this[from] as? AppItem ?: return this
    val out = toMutableList()
    // The home model knows apps only by key, so the smart guess (SPEC §1) needs the category engine
    // reaching in from a caller that has the app list. Its default answers "nothing known", which is
    // the plain "Folder" fallback — so a caller with no lookup still lands its drop.
    val identity = folderIdentityFor(categoryOf(target.key), categoryOf(dragged.key))
    // The target keeps its position, so it reads as "the thing you dropped joined it" — hence the
    // target's key leads the member list.
    out[onto] = FolderItem(identity.name, listOf(target.key, dragged.key), identity.glyph)
    out.removeAt(from)
    return out
}

/**
 * Drop the app at [from] into the folder at [folderIndex] → it nests inside, freeing its slot.
 * A no-op if the target isn't a folder, or if the dragged item is itself a folder: folders don't
 * nest, and merging two of them dissolves a grouping you built — that lives with folder lifecycle
 * (SID-31), not here.
 */
fun List<HomeItem>.nestIntoFolder(from: Int, folderIndex: Int): List<HomeItem> {
    if (from !in indices || from == folderIndex) return this
    val folder = folderAt(folderIndex) ?: return this
    val app = this[from] as? AppItem ?: return this
    val out = toMutableList()
    out[folderIndex] =
        if (folder.appKeys.contains(app.key)) folder else folder.copy(appKeys = folder.appKeys + app.key)
    out.removeAt(from)
    return out
}

/**
 * Drop the item at [from] on the replace target beside the app at [target] → it takes that app's
 * slot and the app leaves the home. The app is only unplaced, never uninstalled — it is still in
 * the library, one tap from being re-added (SPEC §1).
 *
 * Only apps can be replaced: a folder is curated, so it may be opened or added to but never
 * obliterated by a mis-drop (SPEC §1) — a folder target is a no-op here, and the UI gives folders
 * no replace target at all.
 */
fun List<HomeItem>.replaceIndex(from: Int, target: Int): List<HomeItem> {
    if (from !in indices || target !in indices || from == target) return this
    if (this[target] !is AppItem) return this
    val out = toMutableList()
    // Write before removing: the write is by the caller's indices, and only the removal shifts them.
    out[target] = out[from]
    out.removeAt(from)
    return out
}

/**
 * Take [appKey] out of the folder at [folderIndex] — the member strip's Remove cell (SPEC §1 D).
 * The app is only unplaced, never uninstalled; it stays in the library, one tap from coming back.
 *
 * **Emptying the folder deletes it**, freeing its home slot: with nothing left inside, the tile is
 * one of four home slots spent on nothing, and there is no content left to justify it. A folder
 * merely *down* to one app is left standing — dissolving it back into a loose app would be the
 * launcher second-guessing an edit half-made (the next thing you do is usually drop another app in)
 * and would burn the name and glyph on the way.
 *
 * A no-op if the folder isn't there, isn't a folder, or never held [appKey]: the strip holds the
 * index it was raised on and the home can change under it.
 */
fun List<HomeItem>.removeFromFolder(folderIndex: Int, appKey: String): List<HomeItem> {
    val folder = folderAt(folderIndex) ?: return this
    if (appKey !in folder.appKeys) return this
    val rest = folder.appKeys.filterNot { it == appKey }
    return if (rest.isEmpty()) {
        removeIndex(folderIndex)
    } else {
        toMutableList().also { it[folderIndex] = folder.copy(appKeys = rest) }
    }
}

/**
 * Move [appKey] out of the folder at [folderIndex] and onto the home as a loose item — the member
 * strip's Move-to-home, for the case where the home has room.
 *
 * It lands in the slot the folder occupies (if that was its last member, so the folder auto-deletes
 * out from under it) or immediately below it, so the app reads as stepping *out of* the folder.
 * Appended to the bottom it would read as a new arrival from the library, which is not what happened.
 *
 * Ungrouping is the one move that **costs** a slot — the mirror of the property §1 rests on — so on
 * a full home this is a no-op and the caller routes the app through the in-hand placement flow
 * instead ([canPopFromFolder] is that question asked ahead of time). The strip never dims the cell
 * for it: a full home is resolvable by replace/nest/combine, not a wall.
 */
fun List<HomeItem>.popFromFolder(folderIndex: Int, appKey: String): List<HomeItem> {
    val folder = folderAt(folderIndex) ?: return this
    if (appKey !in folder.appKeys) return this
    val without = removeFromFolder(folderIndex, appKey)
    if (without.size >= AppRepository.MAX_HOME) return this
    val folderDeleted = without.size < size
    return without.toMutableList()
        .also { it.add(if (folderDeleted) folderIndex else folderIndex + 1, AppItem(appKey)) }
}

/**
 * Is there room to pop a member out of the folder at [folderIndex]? A one-app folder always has
 * room, whatever the home's size: its last member leaving empties it, so it auto-deletes and hands
 * its own slot straight over.
 */
fun List<HomeItem>.canPopFromFolder(folderIndex: Int): Boolean {
    val folder = folderAt(folderIndex) ?: return false
    return folder.appKeys.size == 1 || size < AppRepository.MAX_HOME
}

/**
 * Drop every folder with nothing left inside it. Auto-delete on the remove path is
 * [removeFromFolder]'s own business; this is the *other* way a folder empties — every member
 * uninstalled between two runs, which the load notices and prunes from the member list. Same rule,
 * same outcome, so the two cannot disagree about whether an empty folder is a thing to keep.
 */
fun List<HomeItem>.dropEmptyFolders(): List<HomeItem> =
    filter { it !is FolderItem || it.appKeys.isNotEmpty() }

/**
 * Reorder within the folder at [folderIndex]: move its member at [from] into the gap before [toGap],
 * counted in the member list's own indices (0 = above every member, member-count = below them all) —
 * the folder-local mirror of [moveIndex], and the same gap-before-the-item-leaves convention, so the
 * shift-by-one when dragging downward is handled the one way. A no-op if the folder isn't there, isn't
 * a folder, or [from] is out of range: a drag holds indices the home can change under it (SID-94).
 */
fun List<HomeItem>.moveWithinFolder(folderIndex: Int, from: Int, toGap: Int): List<HomeItem> {
    val folder = folderAt(folderIndex) ?: return this
    if (from !in folder.appKeys.indices) return this
    val gap = toGap.coerceIn(0, folder.appKeys.size)
    val keys = folder.appKeys.toMutableList()
    val key = keys.removeAt(from)
    keys.add(if (gap > from) gap - 1 else gap, key)
    return toMutableList().also { it[folderIndex] = folder.copy(appKeys = keys) }
}

/** Rename the folder at [index]. A no-op if it isn't there, or isn't a folder. */
fun List<HomeItem>.renameFolder(index: Int, name: String): List<HomeItem> =
    mapFolder(index) { it.copy(name = name) }

/** Re-glyph the folder at [index]. A no-op if it isn't there, or isn't a folder. */
fun List<HomeItem>.setFolderGlyph(index: Int, glyph: String): List<HomeItem> =
    mapFolder(index) { it.copy(glyph = glyph) }

private inline fun List<HomeItem>.mapFolder(index: Int, f: (FolderItem) -> FolderItem): List<HomeItem> {
    val folder = folderAt(index) ?: return this
    return toMutableList().also { it[index] = f(folder) }
}
