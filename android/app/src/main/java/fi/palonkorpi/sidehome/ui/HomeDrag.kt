package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.apps.Category
import fi.palonkorpi.sidehome.apps.AppItem
import fi.palonkorpi.sidehome.apps.HomeItem
import fi.palonkorpi.sidehome.apps.combineIntoFolder
import fi.palonkorpi.sidehome.i18n.tr
import fi.palonkorpi.sidehome.apps.moveIndex
import fi.palonkorpi.sidehome.apps.nestIntoFolder
import fi.palonkorpi.sidehome.apps.placedKeys
import fi.palonkorpi.sidehome.apps.removeIndex
import fi.palonkorpi.sidehome.apps.replaceIndex
import kotlin.math.abs
import kotlin.math.roundToInt

// ---- pure logic (Compose-free, so it can be unit-tested) ------------------------------------

/**
 * A rectangle in root coordinates, in px. Deliberately not Compose's `Rect`: everything in this
 * section is the *decision* — where a drop lands — and stays unit-agnostic and framework-free so it
 * can be reasoned about without a device, exactly like the arbiter and the strip's anchor.
 */
data class DropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
}

/** One home row as measured on screen, before its drop targets are worked out. */
data class HomeRowInput(val index: Int, val isFolder: Boolean, val bounds: DropRect)

/**
 * One home row as a set of drop targets: the [body] (drop here to nest / combine / reorder) and the
 * [replace] target hung beside it (null on folders, and on the row that is itself in hand).
 */
data class HomeRowTarget(
    val index: Int,
    val isFolder: Boolean,
    val body: DropRect,
    val replace: DropRect?,
)

/**
 * Where the thing in hand came *from*. The two differ in one way, and it decides everything else: an
 * item already on the home keeps its slot for the whole carry, so its landing is one pure edit
 * against that index — while an app arriving out of the library ([Library], SID-30) is in no list
 * yet. It has no index to edit against, it is the only carry that can meet a home that is already
 * full, and it is therefore the only one the replace target is offered to (SPEC §1: replace answers
 * "the home is full, what goes?", a question only something not yet on the home can ask).
 *
 * Keyed by app key rather than by an `AppEntry`, because that is all the home model stores — the
 * glyph and the label are the app's, not the placement's, and looking them up is the caller's job.
 */
sealed interface DropSource {
    /** The item at home index [index] — an app or a folder. */
    data class Home(val index: Int) : DropSource

    /** An app that is not on the home at all, on its way there. Never a folder: folders are made on
     *  the home, and the library holds only apps. */
    data class Library(val key: String) : DropSource

    /** The home index a drop resolves against, or null when it came from off the home — which is
     *  exactly the nullable source [resolveDropTarget] and [homeRowTargets] already read. */
    val homeIndex: Int? get() = (this as? Home)?.index
}

/**
 * Where an in-hand item lands when it is let go. There is no "nowhere": a drop always resolves to
 * something (a stray drop falls into the nearest gap, or onto the nearest row when the home is too
 * full to have gaps), because the whole point of the in-hand model is that placement never dead-ends
 * in an error state (SPEC §1).
 */
sealed interface DropTarget {
    /** Place in the gap *before* home index [index] ([index] == size → below everything). */
    data class Gap(val index: Int) : DropTarget

    /** Drop onto the folder at [index] → nest inside it. */
    data class Nest(val index: Int) : DropTarget

    /** Drop onto the app body at [index] → the two become one folder, freeing a slot. */
    data class Combine(val index: Int) : DropTarget

    /** Drop on the target beside the app at [index] → take its slot; it leaves home for the library. */
    data class Replace(val index: Int) : DropTarget

    /** Drop on the remove zone → off the home (the app stays in the library). */
    object Remove : DropTarget
}

/**
 * How much of a row's height, at each of its edges, reads as the gap between rows rather than as the
 * row's body. The rows tile with no space between them, so this band *is* the "drop between items"
 * target — too small and reordering is impossible, too large and you can never make a folder. A
 * tune-on-device number (rows are only ~43dp tall).
 */
const val DROP_GAP_BAND_FRACTION = 0.3f

/**
 * Which drop semantic the point ([x], [y]) means, for the item in hand from [sourceIndex] (null when
 * it came from off the home — a library add, SID-30). [rows] must be in home order. [homeHasRoom] is
 * whether the home is below its cap, i.e. whether a row that isn't there yet could exist.
 *
 * Order matters: the remove zone and the replace targets are *spatially separated* surfaces put on
 * screen for this drag, and they win over the body under them, because make-folder (body) and
 * replace (side target) must never be confused (SPEC §1).
 */
fun resolveDropTarget(
    x: Float,
    y: Float,
    rows: List<HomeRowTarget>,
    sourceIndex: Int?,
    removeZone: DropRect?,
    homeHasRoom: Boolean,
): DropTarget {
    if (removeZone != null && removeZone.contains(x, y)) return DropTarget.Remove

    rows.firstOrNull { it.replace != null && it.replace.contains(x, y) }
        ?.let { return DropTarget.Replace(it.index) }

    // What's in hand, read off the row it was lifted from — it stays in `rows` while carried (that
    // is what the self-drop check below relies on). A null source means it came from off the home,
    // which today only ever means an app (SID-30's library add).
    val sourceIsFolder = rows.firstOrNull { it.index == sourceIndex }?.isFolder == true

    // A gap is a slot that does not exist yet, so it can only be offered to something that would
    // fill one: an arrival, and only while the home is under its cap. A full home has no 5th row to
    // give, so its gaps close and the rows themselves become the answer — group with me, or take my
    // place — which is precisely how a full home stays addable instead of refusing (SPEC §1).
    // Reordering never grows anything, so an item already on the home always keeps its gaps.
    val gapsOpen = sourceIndex != null || homeHasRoom

    val row = rows.firstOrNull { it.body.contains(x, y) }
    if (row != null) {
        // Dropped back on itself: put it down where it was, rather than folding it into itself.
        if (row.index == sourceIndex) return DropTarget.Gap(row.index)
        val band = row.body.height * DROP_GAP_BAND_FRACTION.coerceIn(0f, 0.5f)
        return when {
            gapsOpen && y <= row.body.top + band -> DropTarget.Gap(row.index)
            gapsOpen && y >= row.body.bottom - band -> DropTarget.Gap(row.index + 1)
            // A folder in hand groups nothing: it is already the group. Dropping it on an app would
            // have to swallow that app, and dropping it on a folder would have to merge the two —
            // both read as the folder eating whatever it lands on, which is not what carrying a
            // folder means. Grouping is something you do *to* a folder (drop an app on it), not
            // something a folder does by being moved. So a carried folder only ever travels: gaps,
            // and the remove pill. (It is on the home, so its gaps are never closed above.)
            //
            // It has to decline *here*, in the resolver, not in the model: an affirmative target
            // that lights the accept ring and then no-ops on release is the one outcome §1 rules
            // out. Declining here means the ring never appears.
            sourceIsFolder -> DropTarget.Gap(row.index)
            else -> bodyDrop(row)
        }
    }
    // Off the rows but not on a target: fall into the nearest gap. Never an error, never a no-op
    // you can't see — a stray drop reads as "put it down around here".
    if (gapsOpen) return DropTarget.Gap(rows.count { y > it.body.centerY })
    // With the gaps closed there is nothing to fall *between*, so the same lazy drop reads as the
    // nearest row instead. An empty list can't happen here (a full home has rows), but a home with
    // nothing on it is all room and no cap, so the gap is the honest answer if it ever does.
    val nearest = rows.minByOrNull { abs(y - it.body.centerY) } ?: return DropTarget.Gap(0)
    return bodyDrop(nearest)
}

/** What a release on a row's body means: the two ways two things become one. Both read the same to
 *  the hand, which is why they draw the same ring. */
private fun bodyDrop(row: HomeRowTarget): DropTarget =
    if (row.isFolder) DropTarget.Nest(row.index) else DropTarget.Combine(row.index)

/**
 * The replace target's hit rect for a row: a fixed square hung off the screen's right edge, level
 * with the row. Computed from the row rather than measured, so it exists the instant something is
 * picked up, and so it lands in the same column for every row — the rows are content-width and
 * ragged, and a target that tracked each row's own right edge would wander with the label length.
 *
 * Its height is capped at the row's own: the rows tile with nothing between them and are barely
 * taller than a comfortable touch target, so a square target would spill into its neighbours' bands
 * and the row that replaced would be whichever one the scan reached first — a coin toss, on a
 * gesture that unplaces an app.
 */
fun replaceHitRect(row: DropRect, containerWidth: Float, hitPx: Float, marginPx: Float): DropRect {
    val right = containerWidth - marginPx
    val half = minOf(hitPx, row.height) / 2f
    return DropRect(
        left = right - hitPx,
        top = row.centerY - half,
        right = right,
        bottom = row.centerY + half,
    )
}

/** The remove zone: a pill across the bottom, put on screen only while something is in hand. */
fun removeZoneRect(
    containerWidth: Float,
    containerHeight: Float,
    widthPx: Float,
    heightPx: Float,
    bottomMarginPx: Float,
): DropRect = DropRect(
    left = (containerWidth - widthPx) / 2f,
    top = containerHeight - bottomMarginPx - heightPx,
    right = (containerWidth + widthPx) / 2f,
    bottom = containerHeight - bottomMarginPx,
)

/**
 * Turn the measured rows into their drop targets for the item in hand from [sourceIndex].
 *
 * **Replace is only offered to an item arriving from the library** (`sourceIndex == null`), because
 * that is the only situation it answers: the home is full, you want this new app on it, so something
 * has to go. Shuffling items already on the home never needs it — every one of them is already
 * placed, and "swap these two" is just a reorder. Offering it there put a destructive target beside
 * every row for the whole of an ordinary drag, which is a lot of ways to lose an app by a few px.
 *
 * Folders get none either way (SPEC §1: a folder is curated — it may be opened or added to, never
 * obliterated by a mis-drop). Where a replace target overlaps a long row, the body is trimmed to end
 * at it: the two semantics are meant to be spatially separate, so a long label must not be able to
 * swallow the target.
 */
fun homeRowTargets(
    rows: List<HomeRowInput>,
    sourceIndex: Int?,
    containerWidth: Float,
    replaceHitPx: Float,
    replaceMarginPx: Float,
): List<HomeRowTarget> = rows.map { r ->
    val replace = if (r.isFolder || sourceIndex != null) {
        null
    } else {
        replaceHitRect(r.bounds, containerWidth, replaceHitPx, replaceMarginPx)
    }
    val body = if (replace == null) {
        r.bounds
    } else {
        r.bounds.copy(right = minOf(r.bounds.right, replace.left))
    }
    HomeRowTarget(index = r.index, isFolder = r.isFolder, body = body, replace = replace)
}

/**
 * Run a drop: the one place a resolved [target] turns into a new home, whichever [source] the thing
 * in hand came from. Only a gap ever grows the home, and only by one — every other landing folds
 * what is carried into something already there, which is what makes "drop it onto an app" the answer
 * to a full home rather than a wall. The gaps are the resolver's to withhold, so the cap holds
 * without this ever having to refuse.
 *
 * [categoryOf] is how a folder born here gets a name worth reading (SID-31): the home knows apps
 * only by key, so what a key *is* has to be asked of the caller. It defaults to knowing nothing,
 * which is the plain "Folder" fallback.
 */
fun List<HomeItem>.applyDrop(
    source: DropSource,
    target: DropTarget,
    categoryOf: (String) -> Category? = { null },
): List<HomeItem> = when (source) {
    is DropSource.Home -> applyFromHome(source.index, target, categoryOf)
    is DropSource.Library -> applyArrival(source.key, target, categoryOf)
}

/** The item never left the list while it was carried, so its landing is one edit against its slot. */
private fun List<HomeItem>.applyFromHome(
    sourceIndex: Int,
    target: DropTarget,
    categoryOf: (String) -> Category?,
): List<HomeItem> =
    when (target) {
        is DropTarget.Gap -> moveIndex(sourceIndex, target.index)
        is DropTarget.Nest -> nestIntoFolder(sourceIndex, target.index)
        is DropTarget.Combine -> combineIntoFolder(sourceIndex, target.index, categoryOf)
        is DropTarget.Replace -> replaceIndex(sourceIndex, target.index)
        DropTarget.Remove -> removeIndex(sourceIndex)
    }

/**
 * An app arriving from the library, landing by exactly the same rules. It has no slot to edit
 * against, so it is staged one past the last row and then lands as if it had been there all along:
 * every outcome an arrival can have — fall into a gap, fold into an app, nest into a folder, take a
 * slot — is already one of the on-home edits, and borrowing them is what keeps the two from drifting
 * apart (and what gets their guards for free). Staging at the end is what makes it safe: the gap
 * indices count the home *as drawn*, and appending past the last row leaves every one of them
 * meaning the slot the user pointed at.
 */
private fun List<HomeItem>.applyArrival(
    key: String,
    target: DropTarget,
    categoryOf: (String) -> Category?,
): List<HomeItem> {
    // Remove is the one target that doesn't translate: there is nothing to take off a home this app
    // never reached. It is the finger's way out — Back is unreachable with a hand in mid-air — so it
    // ends the carry and leaves the home exactly as it was.
    if (target == DropTarget.Remove) return this
    // The same key twice on the home is two rows launching one app, or a folder member with a ghost
    // outside it. The drawer declines to lift an app it already shows as placed, so no target ever
    // lights for this — the guard is the backstop, not the refusal the user sees.
    if (placedKeys().contains(key)) return this
    val arrival = size   // where the staged copy sits: one past the last real row
    return (this + AppItem(key)).applyFromHome(arrival, target, categoryOf)
}

// ---- the in-hand layer ----------------------------------------------------------------------

/** The replace target: a 30dp mark inside a 44dp touch square, hung off the right margin. */
internal val REPLACE_CHIP = 30.dp
internal val REPLACE_HIT = 44.dp
internal val REPLACE_MARGIN = 12.dp

/** The remove pill's geometry, shared by the hit rect and the drawing so they can't drift. */
internal val REMOVE_WIDTH = 148.dp
internal val REMOVE_HEIGHT = 44.dp
internal val REMOVE_BOTTOM_MARGIN = 10.dp

/** How far above the remove pill the carried item parks when there is no finger to follow. */
private val PARKED_CHIP_GAP = 18.dp

/**
 * The in-hand layer: what is on screen while an item is being carried. It never dims or tints the
 * sky (DECISIONS) — the home stays live and un-receded underneath; this only adds the targets that
 * exist for the duration of the carry, the item itself following the finger, and — when no finger is
 * carrying it — the banner that says whose placement this is (SID-30).
 *
 * [chipAt] is called in the draw phase, so the carried item follows the finger without recomposing
 * the tree per pointer event — the same rule the drawer's drag and the picker's zoom follow.
 */
@Composable
fun HandLayer(
    glyph: String,
    label: String,
    byFinger: Boolean,
    targets: List<HomeRowTarget>,
    dropTarget: DropTarget?,
    removeZone: DropRect,
    chipAt: () -> Offset,
    fg: Color,
    accent: Color,
    chipColor: Color,
    onTapPlace: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // A finger drag already owns the pointer stream (the row captured it at the pickup), so
            // the layer stays inert and the home underneath keeps working. The menu's Move row has
            // no finger to follow, so there the layer is what catches the placing tap.
            .then(
                if (byFinger) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) { detectTapGestures { onTapPlace(it) } }
                },
            ),
    ) {
        // The one place the drop's *answer* is drawn: the gap it would fall into, or the item it
        // would fold into. Both are accent, because both are "this is what your release does".
        // A DrawScope is a Density, so the dp floors convert without reaching outside.
        Canvas(Modifier.fillMaxSize()) {
            val gapLine = 2.dp.toPx()
            val ringInset = 3.dp.toPx()
            when (dropTarget) {
                is DropTarget.Gap -> {
                    val y = targets.firstOrNull { it.index == dropTarget.index }?.body?.top
                        ?: targets.lastOrNull()?.body?.bottom
                        ?: return@Canvas
                    val left = targets.minOf { it.body.left }
                    val right = targets.maxOf { it.body.right }
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left, y - gapLine / 2f),
                        size = Size(right - left, gapLine),
                        cornerRadius = CornerRadius(gapLine / 2f),
                    )
                }

                is DropTarget.Combine -> drawTargetRing(targets, dropTarget.index, accent, ringInset)
                is DropTarget.Nest -> drawTargetRing(targets, dropTarget.index, accent, ringInset)
                else -> {}
            }
        }

        // One replace target per app row. Apps only: a folder has none, deliberately (SPEC §1).
        targets.forEach { t ->
            t.replace?.let { rect ->
                ReplaceTarget(
                    rect = rect,
                    active = (dropTarget as? DropTarget.Replace)?.index == t.index,
                    fg = fg,
                    accent = accent,
                    chipColor = chipColor,
                )
            }
        }

        RemovePill(
            rect = removeZone,
            active = dropTarget == DropTarget.Remove,
            fg = fg,
            accent = accent,
            chipColor = chipColor,
        )

        // No finger on it → nothing on screen says what the lit home is asking, so the banner does
        // (SID-30). It is drawn *here* rather than wired in by the host, because it is part of what
        // being in hand without a finger looks like — the same condition that parks the chip and
        // makes this layer catch the placing tap.
        if (!byFinger) {
            PlacingBanner(
                label = label,
                fg = fg,
                panelColor = chipColor,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
            )
        }

        // The carried item itself. Centred on the finger — it *is* where your hand is.
        Row(
            modifier = Modifier
                .graphicsLayer {
                    val p = chipAt()
                    translationX = p.x - size.width / 2f
                    translationY = p.y - size.height / 2f
                }
                .clip(RoundedCornerShape(12.dp))
                .background(chipColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(glyph = glyph, color = fg, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(text = label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** The ring that says "let go here and these two become one thing" — folder-nest and make-folder
 *  read the same, because they are the same act to the hand. */
private fun DrawScope.drawTargetRing(
    targets: List<HomeRowTarget>,
    index: Int,
    accent: Color,
    inset: Float,
) {
    val row = targets.firstOrNull { it.index == index } ?: return
    drawRoundRect(
        color = accent,
        topLeft = Offset(row.body.left - inset, row.body.top - inset),
        size = Size(row.body.width + inset * 2f, row.body.height + inset * 2f),
        cornerRadius = CornerRadius(11.dp.toPx()),
        style = Stroke(width = 2.dp.toPx()),
    )
}

/** The replace mark beside an app: drop here and the carried item takes this slot. */
@Composable
private fun ReplaceTarget(
    rect: DropRect,
    active: Boolean,
    fg: Color,
    accent: Color,
    chipColor: Color,
) {
    val enter = rememberOverlayEnter(riseDp = 0f)
    Box(
        modifier = Modifier
            // The lambda's receiver is a Density, and it runs in the layout phase — no composition
            // read per frame, the same rule the strip's own anchor follows.
            .offset {
                val half = REPLACE_CHIP.toPx() / 2f
                IntOffset((rect.centerX - half).roundToInt(), (rect.centerY - half).roundToInt())
            }
            .then(enter)
            .size(REPLACE_CHIP)
            .clip(RoundedCornerShape(999.dp))
            .background(chipColor)
            .then(
                if (active) {
                    Modifier.border(2.dp, accent, RoundedCornerShape(999.dp))
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Full-strength fg, accent only when it is the live answer: every target is equally real
        // until the finger says otherwise, and nothing here is "muted" (DECISIONS).
        LineIcon(
            glyph = "download",
            color = if (active) accent else fg,
            size = 15.dp,
            strokeWidthUnits = 1.7f,
        )
    }
}

/** Drop here and it comes off the home. No confirm — an app is cheap to put back (SPEC §1). */
@Composable
private fun RemovePill(
    rect: DropRect,
    active: Boolean,
    fg: Color,
    accent: Color,
    chipColor: Color,
) {
    val enter = rememberOverlayEnter(riseDp = 6f)
    Row(
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .then(enter)
            .size(width = REMOVE_WIDTH, height = REMOVE_HEIGHT)
            .clip(RoundedCornerShape(999.dp))
            .background(chipColor)
            .then(
                if (active) {
                    Modifier.border(2.dp, accent, RoundedCornerShape(999.dp))
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(
            glyph = "trash",
            color = if (active) accent else fg,
            size = 17.dp,
            strokeWidthUnits = 1.6f,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = tr("Remove", "Entfernen"),
            color = if (active) accent else fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Where the carried item parks when nothing is dragging it: over the home, clear of the pill. */
@Composable
fun parkedChipOffset(containerWidth: Int, removeZone: DropRect): Offset {
    val gap = with(LocalDensity.current) { PARKED_CHIP_GAP.toPx() }
    return Offset(containerWidth / 2f, removeZone.top - gap)
}
