package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import fi.palonkorpi.sidehome.apps.AppEntry
import fi.palonkorpi.sidehome.apps.FolderItem
import fi.palonkorpi.sidehome.gesture.GestureTuning
import fi.palonkorpi.sidehome.i18n.tr

/**
 * Folder open: the home has receded behind us; the folder's apps appear centred over the untouched
 * sky — no dim, no card, no size-morph. Tap outside to dismiss.
 *
 * Member tiles carry their **own individual** notification dots (SID-46), which is the whole point
 * of the drill-down: the closed tile out on home only says "something in here", and opening it is
 * what reveals *which* app.
 *
 * SID-31 makes the members gesture-aware, in the home's own grammar rather than in one of their own:
 * tap launches, and a hold raises the same anchored strip a home item's hold does ([itemPress] is
 * literally the shared primitive). That is what lets folder contents be managed in place, with no
 * edit surface anywhere in §1.
 *
 * @param accent the sky-derived accent the dots take. Required, deliberately: defaulting it to [fg]
 *   would let a forgotten call site render dots in the wrong colour and say nothing — the one
 *   failure a compiler can catch for free.
 * @param onMemberHold a member's hold, handed straight to the host, which raises the **one** action
 *   strip beside it (SID-114) — this popover keeps no strip of its own. The host decides which strip
 *   the hold gets from where the popover was opened: a home folder builds a `FolderMember` subject
 *   (Icon / Home / Remove / Info — [ActionTarget.FOLDER_MEMBER]'s folder-membership verbs), a grouped
 *   *category bucket* (SID-23, opened through this same popover) builds a `LibraryApp` one (Icon /
 *   Home / Category / Info). The bucket needs the library verbs, not the folder's: its Remove would
 *   mean *out of this category*, which has no answer (you re-categorise an app, you do not take it out
 *   of Media), and it needs the Category cell a folder member has no use for. Because the choice is
 *   the host's now, the popover reports only the app and its row bounds and is blind to the difference.
 * @param onMemberPickup a member's hold turning into a reorder carry (SID-94) — fired just before the
 *   carry begins so the host can take back down the strip the hold raised. No-op by default: only a
 *   home folder reorders, and only its call site raises a strip that needs clearing.
 * @param realIcons render each member as its **real** launcher icon rather than the line glyph. Off
 *   for home folders (line art is the home's language); on for a grouped-library bucket, whose closed
 *   bubble already peeks these same real icons — so drilling in that shows line glyphs instead read as
 *   a different set of apps. The badge still overlays either way. [glyphFor] is then unused, but stays
 *   required so the folder path is untouched.
 * @param onReorder when non-null, a member can be picked up (hold, then drag) and dropped into a new
 *   position, committed by the host as `(from, toGap)` — the folder's own `moveWithinFolder` grammar
 *   (SID-94), the same hold-then-drag the home rows use. Home folders pass it; a category bucket does
 *   not (its order is the category's, not the user's, and nothing would persist a change), so the
 *   bucket path keeps a member's hold as strip-only with no carry.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderPopover(
    folder: FolderItem,
    appsByKey: Map<String, AppEntry>,
    glyphFor: (AppEntry) -> String,
    tuning: GestureTuning,
    fg: Color,
    accent: Color,
    stripColor: Color,
    onLaunch: (AppEntry) -> Unit,
    onMemberHold: (AppEntry, Rect) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onMemberPickup: () -> Unit = {},
    realIcons: Boolean = false,
    selectedIndex: Int? = null,
    showIndexNumbers: Boolean = false,
    onReorder: ((from: Int, toGap: Int) -> Unit)? = null,
) {
    val members = folder.appKeys.mapNotNull { appsByKey[it] }
    val badgedPackages = rememberBadgedPackages()

    // SID-94: an in-hand member being dragged to a new slot, and each member row's root-coord rect so
    // the drop point can be read against them. Keyed on the folder so an edit underneath clears both.
    // Only ever populated on the home-folder path ([onReorder] != null); a bucket leaves it null.
    val reorderable = onReorder != null
    var carried by remember(folder) { mutableStateOf<CarriedMember?>(null) }
    val memberRects = remember(folder) { mutableStateMapOf<Int, Rect>() }
    // The gap (in member indices, 0 = above all) the pointer currently points at: the count of members
    // whose vertical centre sits above it. Includes the dragged row at its dimmed-but-present slot, so
    // it lines up with [moveWithinFolder]'s gap-before-the-item-leaves convention.
    fun gapAt(y: Float): Int = (0 until members.size).count { (memberRects[it]?.center?.y ?: Float.MAX_VALUE) < y }

    // Hoisted so the overflow chevrons can read whether there is more folder above/below.
    val scroll = rememberScrollState()

    // Key-nav auto-scroll: bring the selected member into view on each cursor move so driving a long
    // folder by key (e.g. the Sundial ring) doesn't run the cursor off the fold. At the ends, snap
    // fully to the edge rather than just far enough to reveal the row — selecting the first member
    // scrolls the header back in (top), the last member scrolls to the bottom — so the overflow
    // chevron actually clears. bringIntoView alone leaves the header/last row flush at the edge with
    // content still beyond it, and the arrow lingers until you finger-scroll the rest of the way.
    val bringSelected = remember { BringIntoViewRequester() }
    LaunchedEffect(selectedIndex) {
        when {
            selectedIndex == null -> {}
            selectedIndex <= 0 -> scroll.animateScrollTo(0)
            selectedIndex >= members.lastIndex -> scroll.animateScrollTo(scroll.maxValue)
            else -> runCatching { bringSelected.bringIntoView() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        // The folder region: left-aligned to the home column, framed away from the screen edges so
        // the title sits at a comfortable height instead of jammed under the status bar (SID-63), and
        // a long folder (Other holds ~20 members) scrolls *within* the frame rather than running off
        // both ends. fillMaxHeight is the scroll bound; a short folder wraps and stays vertically
        // centred. The outer tap-catcher Box stays unpadded so the strip's root-coord anchor is honest.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(vertical = FOLDER_MARGIN)
                .widthIn(max = 300.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                // A plain Column, not a LazyColumn, on purpose: every row composes, so the staggered
                // entrance fires for all of them — a lazy list would only stagger the rows it had
                // composed, and 20 text rows is nothing on this hardware.
                modifier = Modifier
                    .verticalScroll(scroll)
                    .padding(start = 26.dp, end = 24.dp),
            ) {
                // The header leads, then each row ripples in just behind it (staggered rise+fade).
                Text(
                    text = folder.name.uppercase(),
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.07.em,
                    modifier = Modifier
                        .then(rememberStaggeredEnter(index = 0))
                        .padding(start = 6.dp, bottom = 7.dp),
                )
                if (members.isEmpty()) {
                    Text(
                        // Full-strength fg: no muted text on an ambient surface (DECISIONS). Removing
                        // the last member auto-deletes the folder, so this is no longer reachable by
                        // editing; what still reaches it is a member uninstalling between two runs,
                        // which the load prunes out of the member list.
                        text = tr("Empty folder.", "Leerer Ordner."),
                        color = fg,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .then(rememberStaggeredEnter(index = 1))
                            .padding(6.dp),
                    )
                } else {
                    members.forEachIndexed { i, app ->
                        MemberRow(
                            app = app,
                            glyph = glyphFor(app),
                            realIcon = realIcons,
                            fg = fg,
                            tuning = tuning,
                            enter = rememberStaggeredEnter(index = i + 1),
                            badge = badgeTint(isBadged(app, badgedPackages), accent),
                            selected = selectedIndex == i,
                            bringIntoView = if (selectedIndex == i) bringSelected else null,
                            indexBadge = if (showIndexNumbers && i < 9) i + 1 else null,
                            draggable = reorderable,
                            dimmed = carried?.from == i,
                            onBounds = { b -> memberRects[i] = b },
                            onTap = { onLaunch(app) },
                            // The hold is the host's — it raises the one action strip beside the row.
                            onHold = { bounds -> onMemberHold(app, bounds) },
                            // Hold-then-drag lifts the member into a carry (SID-94): the host takes the
                            // strip the hold raised back down ([onMemberPickup]), and the row streams its
                            // finger position (root coords) until it drops into the pointed gap.
                            onPickup = { bounds ->
                                onMemberPickup()
                                carried = CarriedMember(i, app, glyphFor(app), bounds.center)
                            },
                            onDragTo = { at -> carried = carried?.copy(at = at) },
                            onDrop = {
                                carried?.let { onReorder?.invoke(it.from, gapAt(it.at.y)) }
                                carried = null
                            },
                        )
                    }
                }
            }

            // Off-screen indication: a chevron at whichever edge has more folder beyond it.
            // canScroll* flip only at the ends, so reading them here recomposes the popover at those
            // transitions, not per scrolled pixel.
            if (scroll.canScrollBackward) {
                FolderScrollHint(up = true, fg = fg, modifier = Modifier.align(Alignment.TopCenter))
            }
            if (scroll.canScrollForward) {
                FolderScrollHint(up = false, fg = fg, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }

        // SID-94 carry overlays: an accent insertion line at the pointed gap, and the lifted chip
        // trailing the finger. Both sit in root coords, which is this full-screen Box's own space.
        // Carry and the strip are mutually exclusive — a pickup clears the host strip ([onMemberPickup])
        // — so their order here never matters; they draw last so they float over the rows.
        carried?.let { c ->
            val gap = gapAt(c.at.y)
            val edgeY = when {
                members.isEmpty() -> null
                gap <= 0 -> memberRects[0]?.top
                gap >= members.size -> memberRects[members.size - 1]?.bottom
                else -> memberRects[gap]?.top
            }
            val ref = memberRects[c.from] ?: memberRects.values.firstOrNull()
            val density = LocalDensity.current
            if (edgeY != null && ref != null) {
                Box(
                    Modifier
                        .offset { IntOffset(ref.left.roundToInt(), edgeY.roundToInt() - 1.dp.roundToPx()) }
                        .size(width = with(density) { ref.width.toDp() }, height = 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(accent),
                )
            }
            if (ref != null) {
                val halfH = ref.height / 2f
                Row(
                    modifier = Modifier
                        .offset { IntOffset(ref.left.roundToInt(), (c.at.y - halfH).roundToInt()) }
                        .clip(RoundedCornerShape(10.dp))
                        .background(stripColor)
                        .padding(horizontal = 6.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (realIcons) {
                        AppIcon(packageName = c.app.packageName, size = MEMBER_ICON, placeholder = fg.copy(alpha = 0.12f))
                    } else {
                        LineIcon(glyph = c.glyph, color = fg, size = MEMBER_ICON, strokeWidthUnits = 1.7f)
                    }
                    Spacer(Modifier.width(15.dp))
                    Text(c.app.label, color = fg, fontSize = 18.sp)
                }
            }
        }

        // SID-114: the member strip is the host's now, raised via [onMemberHold] and drawn one layer
        // out (over this popover), so its own tap-catcher still takes the dismissing tap before this
        // popover's — tapping away from the strip closes just the strip, not the whole folder.
    }
}

/** The margin between the folder region and the screen's top/bottom edges (below the status bar), so
 *  the folder reads as a centred card rather than running to the physical edges of the 640px screen. */
private val FOLDER_MARGIN = 44.dp

/**
 * The "there is more folder this way" chevron, shown at the top or bottom edge of the region only when
 * the list can still scroll that way. A quiet affordance, not text — DECISIONS' no-muted-copy rule is
 * about words, and this is the shape the eye needs to know the folder continues off-screen.
 */
@Composable
private fun FolderScrollHint(up: Boolean, fg: Color, modifier: Modifier) {
    LineIcon(
        glyph = "chevron",
        color = fg,
        size = 20.dp,
        strokeWidthUnits = 1.8f,
        modifier = modifier
            .padding(2.dp)
            .rotate(if (up) -90f else 90f),
    )
}

/** The member being carried to a new slot: its source index, what to draw for the trailing chip, and
 *  the live finger position in root coords. */
private data class CarriedMember(val from: Int, val app: AppEntry, val glyph: String, val at: Offset)

/** A member icon's size, shared by the line-glyph and real-icon renderings so a row is the same
 *  height whichever it draws. */
private val MEMBER_ICON = 24.dp

/** Real-icon badge diameter as a fraction of [MEMBER_ICON], picked to match the dot a line-glyph
 *  member carries (stroke 1.7 × [BadgeMetrics.DiameterStrokes] ≈ 5dp on a 24dp icon). The icon has no
 *  stroke to size it against, so it is anchored to the icon instead; the same [BadgeMetrics.Overhang]
 *  pokes it past the corner onto the sky where an accent dot reads against a busy icon. */
private const val MEMBER_ICON_BADGE_FRACTION = 0.21f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberRow(
    app: AppEntry,
    glyph: String,
    realIcon: Boolean,
    fg: Color,
    tuning: GestureTuning,
    enter: Modifier,
    badge: Color?,
    onTap: () -> Unit,
    onHold: (Rect) -> Unit,
    selected: Boolean = false,
    bringIntoView: BringIntoViewRequester? = null,
    indexBadge: Int? = null,
    draggable: Boolean = false,
    dimmed: Boolean = false,
    onBounds: (Rect) -> Unit = {},
    onPickup: (Rect) -> Unit = {},
    onDragTo: (Offset) -> Unit = {},
    onDrop: () -> Unit = {},
) {
    // The strip anchors its Y beside the row that raised it, in root coords — the popover is a
    // sibling of the scene, so its rows and the strip share the same origin.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = Modifier
            .then(enter)
            // While this member is the one in hand it is drawn as the trailing chip instead; what's
            // left in the flow is the dimmed hole it will come back to (SID-94).
            .then(if (dimmed) Modifier.alpha(0.3f) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            // The key-nav cursor (SID-84): a quiet fg-tinted fill, only ever set by key.
            .then(if (selected) Modifier.background(fg.copy(alpha = 0.12f)) else Modifier)
            // The selected row carries the requester so key-nav auto-scrolls the folder to it.
            .then(if (bringIntoView != null) Modifier.bringIntoViewRequester(bringIntoView) else Modifier)
            .onGloballyPositioned {
                // onGloballyPositioned re-fires on every layout pass; only report real movement.
                val b = it.boundsInRoot()
                if (b != bounds) {
                    bounds = b
                    onBounds(b)
                }
            }
            .itemPress(
                tuning = tuning,
                onTap = onTap,
                onHold = { onHold(bounds) },
                // A home folder lets a member be carried to a new slot (SID-94): the hold raises the
                // strip, and a drag after it lifts the member into a reorder carry, streaming the
                // finger in root coords. A category bucket passes draggable = false — its order is the
                // category's, not the user's — so the hold there stays strip-only.
                onPickup = if (draggable) ({ onPickup(bounds) }) else ({}),
                onDrag = if (draggable) ({ p -> onDragTo(bounds.topLeft + p) }) else ({}),
                onDrop = if (draggable) onDrop else ({}),
            )
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Faint direct-index number (SID-82), so the matching keypad key is discoverable.
        if (indexBadge != null) {
            Text("$indexBadge", color = fg.copy(alpha = 0.38f), fontSize = 13.sp, modifier = Modifier.width(16.dp))
        }
        if (realIcon) {
            // The bucket's real icon, so drilling in matches the bubble that peeked it. The badge is
            // a Compose overlay for the same reason it is on a LineIcon — a sibling dot that fades and
            // drifts with the accent independently of the icon it sits on.
            Box(Modifier.size(MEMBER_ICON)) {
                AppIcon(packageName = app.packageName, size = MEMBER_ICON, placeholder = fg.copy(alpha = 0.12f))
                if (badge != null) {
                    val diameter = MEMBER_ICON * MEMBER_ICON_BADGE_FRACTION
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = diameter * BadgeMetrics.Overhang, y = -diameter * BadgeMetrics.Overhang)
                            .size(diameter)
                            .background(badge, CircleShape),
                    )
                }
            }
        } else {
            LineIcon(
                glyph = glyph,
                color = fg,
                size = MEMBER_ICON,
                strokeWidthUnits = 1.7f,
                badge = badge,
            )
        }
        Spacer(Modifier.width(15.dp))
        Text(app.label, color = fg, fontSize = 18.sp)
    }
}
