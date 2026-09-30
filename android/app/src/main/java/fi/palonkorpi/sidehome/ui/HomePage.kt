package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle as UiTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.apps.AppEntry
import fi.palonkorpi.sidehome.apps.AppItem
import fi.palonkorpi.sidehome.apps.FolderItem
import fi.palonkorpi.sidehome.apps.HomeItem
import fi.palonkorpi.sidehome.gesture.GestureTuning
import fi.palonkorpi.sidehome.settings.ClockFormat
import fi.palonkorpi.sidehome.settings.formatClock
import java.time.LocalDateTime
import java.util.Locale

/**
 * The §1 home page itself: clock, date, and the capped list of app/folder rows — the *content* of
 * the centre pane. It owns no state; every gesture is reported upward and every edit comes back
 * down as a new [home] list, which is what lets the pager (SPEC §7) host it without knowing what a
 * row is, and lets the placement layer resolve drops without knowing where a row is drawn.
 *
 * Split out of `HomeScreen.kt`, which keeps the scaffold that hosts this.
 */

/**
 * The home block's geometry, in one place. A picker tile's mock is a *geometric* replica of this
 * — every value below times [TILE_WIDTH_FRACTION], the same factor the host zooms the scene by —
 * which is what makes the mock's clock land where the real one left off instead of ghosting
 * against it through the crossfade. That only holds while both read the same numbers, so they
 * live here rather than as literals in each. The mock is still a mock: no real favourites, no
 * real date, no second render — the same skeleton at the same scale.
 */
internal object HomeMetrics {
    val PadStart = 32.dp
    val PadEnd = 28.dp
    val PadTop = 40.dp
    val PadBottom = 44.dp
    const val ClockSp = 78f
    const val DateSp = 17f
    val ClockDateGap = 10.dp
    val DateRowsGap = 22.dp
    val RowIcon = 25.dp
    val RowGap = 16.dp
    val RowPadH = 8.dp
    val RowPadV = 9.dp
    const val RowLabelSp = 19f

    /** The home caps at 4 items (DECISIONS), so a full mock is four rows. */
    const val MockRows = 4

    /** Roughly what a Text's default line height comes to as a multiple of its size. Only the
     *  mock needs it — to reserve the height the real date Text takes, since it draws a bar
     *  instead of text and would otherwise shift everything above and below it. */
    const val DateLineFactor = 1.21f
}

/**
 * What the §1 action strip is currently raised on: which of the four variants it is, plus the
 * bounds of the item that raised it (root coords) so the strip can anchor its Y beside it. Held for
 * as long as the strip is up — the home can change underneath, so every handler re-checks its index.
 *
 * One subject type for every surface that raises the strip, so there is exactly one strip lifecycle
 * — one Back step, one key path, one detach — no matter which surface a hold came from (SID-114). A
 * [FolderMember] rides the same host strip an open folder's contents were the last thing tracked by
 * a machine of their own.
 */
internal sealed interface StripSubject {
    val bounds: Rect
}

internal data class LibraryApp(val app: AppEntry, override val bounds: Rect) : StripSubject
internal data class HomeApp(val app: AppEntry, val index: Int, override val bounds: Rect) : StripSubject
internal data class HomeFolder(val index: Int, override val bounds: Rect) : StripSubject

/** An app held inside an open folder (SID-31), addressed by which folder holds it — its strip's
 *  verbs ([ActionTarget.FOLDER_MEMBER]) are folder-membership lifecycle, not home placement. */
internal data class FolderMember(val folderAt: Int, val app: AppEntry, override val bounds: Rect) : StripSubject

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomePage(
    clock: LocalDateTime,
    clockFormat: ClockFormat,
    home: List<HomeItem>,
    appsByKey: Map<String, AppEntry>,
    glyphFor: (AppEntry) -> String,
    fg: Color,
    accent: Color,
    enterTick: Int,
    tuning: GestureTuning,
    onOpenDrawer: () -> Unit,
    onDrawerDrag: (Float) -> Unit,
    onDrawerSettle: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onOpenFolder: (Int) -> Unit,
    onItemHold: (StripSubject) -> Unit,
    onItemPickup: (Int, Rect) -> Unit,
    onItemDrag: (Offset) -> Unit,
    onItemDrop: () -> Unit,
    // SID-112: rows report their bounds straight into the one shared registry; the arbiter, the
    // long-press gate below, placement, and the key-nav strip anchor all read it — no private copies.
    homeItems: HomeItemBounds,
    handIndex: Int?,
    selectedIndex: Int? = null,
    showIndexNumbers: Boolean = false,
    // On the photo theme the labels stand on photo detail, not a flat sky, so each carries a soft
    // halo in the ink's opposite ground (SID-43) — the same trick the positioner ghost uses. Null
    // on the mood themes: the sky guarantees contrast there, and a shadow would read as grime.
    labelHalo: Color? = null,
    onOpenDebug: () -> Unit,
    onClockClick: () -> Unit = {},
    onLongPressBackground: () -> Unit = {},
    modifier: Modifier = Modifier,
    attachDrag: Boolean = true,
) {
    val timeStr = formatClock(clock.hour, clock.minute, clockFormat)
    val dateLabels = remember(clock.toLocalDate(), Locale.getDefault()) {
        homeDateLabels(clock.dayOfWeek, clock.dayOfMonth, clock.month, Locale.getDefault())
    }

    // Notification dots (SID-46). Read from the process-wide badge source rather than threaded down
    // from MainActivity: a dot belongs to the *package*, so the same app lights on its home tile and
    // inside its folder at once, with no per-placement state to keep in sync.
    val badgedPackages = rememberBadgedPackages()

    // SID-93: the theme-chooser long-press spans this whole Box, but a row's own hold (itemPress)
    // does not *consume* the pointer until the finger moves — so a still hold on a row would let this
    // background long-press fire ~50ms later as well and pop the chooser over a lift-to-move. Gate it
    // by the row the press starts on: the Box's own origin converts the local press point into the
    // root space the rows report in, and the shared registry hit-tests it (SID-112). Only bare space
    // falls through to the chooser.
    var pageOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { pageOrigin = it.boundsInRoot().topLeft }
            // Long-press on empty home → the theme chooser (DECISIONS §6: hold empty home → zoom-out
            // themes). The clock and the favourites are deeper children with their own gesture
            // handlers; the clock consumes so it already wins, and a hold that lands on a favourite
            // row is rejected here (SID-93) so only bare space opens the chooser.
            // Attached in both modes, so it works whether or not the arbiter owns the pointer stream.
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { local ->
                        val at = local + pageOrigin
                        if (homeItems.hitTest(at) == null) {
                            onLongPressBackground()
                        }
                    },
                )
            }
            // A vertical drag on the home drives the drawer directly — it rises with the finger and
            // settles open/closed past a threshold on release. When the SID-24 arbiter is active it
            // owns the pointer stream instead, so this detector is omitted (attachDrag = false).
            .then(
                if (attachDrag) {
                    Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dy -> onDrawerDrag(dy) },
                            onDragEnd = { onDrawerSettle() },
                            onDragCancel = { onDrawerSettle() },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = HomeMetrics.PadStart,
                    end = HomeMetrics.PadEnd,
                    top = HomeMetrics.PadTop,
                    bottom = HomeMetrics.PadBottom,
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            // Coming in to the home, the block ripples up into view: clock leads, then the
            // date, then each favourite/folder row cascades just behind it. Keyed on enterTick,
            // which bumps only on a fully-covered→revealed edge (SID-33): cold start, returning
            // from an app, or a full-screen overlay closing — never a bare-home HOME press. The
            // staggered layers are transient (dropped once settled), so idle stays a static frame.
            // leadMs holds the whole cascade back a beat so, when returning from an app, it starts
            // only once the home is actually on screen — not mid-transition behind the closing app.
            val lead = 150
            key(enterTick) {
                // 12h splits into a big time and a small am/pm — the whole "3:56 pm" string is wider
                // than the 480px home at 78sp, so as one Text the suffix wrapped to a second line and
                // read as broken. The meridiem rides the number's baseline at a footnote size instead
                // (iOS-style); 24h has no suffix, so this collapses to the single Text it was.
                val meridiem = if (clockFormat == ClockFormat.H12) timeStr.substringAfter(' ', "") else ""
                val timeMain = if (meridiem.isEmpty()) timeStr else timeStr.substringBefore(' ')
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .then(rememberStaggeredEnter(index = 0, riseDp = 16f, leadMs = lead))
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(onClick = onClockClick, onLongClick = onOpenDebug)
                        .padding(horizontal = 2.dp),
                ) {
                    Text(
                        text = timeMain,
                        color = fg,
                        fontSize = HomeMetrics.ClockSp.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.03).em,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (meridiem.isNotEmpty()) {
                        Text(
                            text = meridiem,
                            color = fg,
                            fontSize = (HomeMetrics.ClockSp * 0.32f).sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier
                                .alignByBaseline()
                                .padding(start = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.size(HomeMetrics.ClockDateGap))
                // The date, with the one glanceable line (DECISIONS §2) — weather (SID-64) — sharing
                // its row rather than taking a line of its own: a separate third line overflowed the
                // 640px home once four favourites were present, so the glance is a suffix to the date.
                // Both ride the date's cascade slot (index 1). The weather is absent, not empty, when
                // there's nothing to show, so a no-weather home is exactly the date row of before.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.then(rememberStaggeredEnter(index = 1, leadMs = lead)),
                ) {
                    // weight(fill = false) makes the Row measure the glance first, so the date is
                    // what yields on a long day/month pair — it steps down to a shorter form rather
                    // than squeezing the glance until its number wraps a digit per line.
                    HomeDate(
                        labels = dateLabels,
                        style = LocalTextStyle.current.merge(
                            UiTextStyle(
                                color = fg,
                                fontSize = HomeMetrics.DateSp.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.01.em,
                            ),
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    GlanceWeather(fg = fg)
                }
                Spacer(Modifier.size(HomeMetrics.DateRowsGap))
                home.forEachIndexed { i, item ->
                    val rowEnter = rememberStaggeredEnter(index = i + 2, leadMs = lead)
                    when (item) {
                        is AppItem -> appsByKey[item.key]?.let { app ->
                            HomeRow(
                                glyph = glyphFor(app),
                                label = app.label,
                                fg = fg,
                                tuning = tuning,
                                enter = rowEnter,
                                halo = labelHalo,
                                lifted = handIndex == i,
                                selected = selectedIndex == i,
                                indexBadge = if (showIndexNumbers && i < 9) i + 1 else null,
                                badge = badgeTint(isBadged(app, badgedPackages), accent),
                                onClick = { onLaunch(app) },
                                onHold = { bounds -> onItemHold(HomeApp(app, i, bounds)) },
                                onPickup = { bounds -> onItemPickup(i, bounds) },
                                onDrag = onItemDrag,
                                onDrop = onItemDrop,
                                onBounds = { b -> homeItems.report(i, b) },
                            )
                        }
                        is FolderItem -> HomeRow(
                            glyph = item.glyph,
                            label = item.name,
                            fg = fg,
                            tuning = tuning,
                            enter = rowEnter,
                            halo = labelHalo,
                            chevron = true,
                            lifted = handIndex == i,
                            selected = selectedIndex == i,
                            indexBadge = if (showIndexNumbers && i < 9) i + 1 else null,
                            // One dot for the whole folder if any member is lit — never a count,
                            // never per-member pips. Which app it is, is what opening it tells you.
                            badge = badgeTint(folderIsBadged(item, appsByKey, badgedPackages), accent),
                            onClick = { onOpenFolder(i) },
                            onHold = { bounds -> onItemHold(HomeFolder(i, bounds)) },
                            onPickup = { bounds -> onItemPickup(i, bounds) },
                            onDrag = onItemDrag,
                            onDrop = onItemDrop,
                            onBounds = { b -> homeItems.report(i, b) },
                        )
                    }
                }
            }
        }

        // No edit affordance: SID-28 retired the pencil and the sheet behind it. Editing is a hold
        // on the item itself → the anchored action strip.

        // summon: bottom-centred "apps"
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable { onOpenDrawer() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineIcon(glyph = "search", color = fg, size = 16.dp, strokeWidthUnits = 1.7f)
            Spacer(Modifier.width(8.dp))
            Text(text = "apps", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.02.em)
        }
    }
}

@Composable
private fun HomeRow(
    glyph: String,
    label: String,
    fg: Color,
    tuning: GestureTuning,
    onClick: () -> Unit,
    onHold: (Rect) -> Unit,
    onPickup: (Rect) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onBounds: (Rect) -> Unit,
    enter: Modifier = Modifier,
    halo: Color? = null,
    chevron: Boolean = false,
    lifted: Boolean = false,
    selected: Boolean = false,
    indexBadge: Int? = null,
    badge: Color? = null,
) {
    // The strip anchors its Y beside the row that raised it, and the drop layer hit-tests against
    // it, so the row reports where it sits (root coords — both are siblings at the top of the scene).
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = Modifier
            .then(enter)
            // While the row is in hand it is being drawn as the carried chip instead; what's left in
            // the flow is the hole it will come back to, not a second copy of the item. The layer
            // exists only for the carry — a persistent one would cost an offscreen render per frame
            // forever, for a row that is opaque 99% of the time (see Anim.kt).
            .then(if (lifted) Modifier.graphicsLayer { alpha = 0.3f } else Modifier)
            .clip(RoundedCornerShape(11.dp))
            // The key-nav cursor's home (SID-84): a quiet fg-tinted fill, clipped to the row's
            // rounded shape. Only ever set when navigating by key — touch never lights it.
            .then(if (selected) Modifier.background(fg.copy(alpha = 0.12f)) else Modifier)
            .onGloballyPositioned {
                // onGloballyPositioned re-fires on every layout pass; only report real movement, or
                // the drop layer's derived targets invalidate in a loop.
                val b = it.boundsInRoot()
                if (b != bounds) {
                    bounds = b
                    onBounds(b)
                }
            }
            .itemPress(
                tuning = tuning,
                onTap = onClick,
                onHold = { onHold(bounds) },
                onPickup = { onPickup(bounds) },
                // itemPress reports positions local to this row; the drop layer thinks in root
                // coords, and this row already knows where it starts.
                onDrag = { p -> onDrag(bounds.topLeft + p) },
                onDrop = onDrop,
            )
            .padding(horizontal = HomeMetrics.RowPadH, vertical = HomeMetrics.RowPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The icon leads, so its left edge lines up with the clock and date above it rather than
        // being indented by the index column. The faint direct-index number (SID-82) — press the
        // matching keypad key to launch — rides between the label and the chevron instead.
        LineIcon(glyph = glyph, color = fg, size = HomeMetrics.RowIcon, badge = badge)
        Spacer(Modifier.width(HomeMetrics.RowGap))
        Text(
            text = label,
            color = fg,
            fontSize = HomeMetrics.RowLabelSp.sp,
            style = if (halo != null) {
                UiTextStyle(shadow = Shadow(color = halo, blurRadius = 7f))
            } else {
                UiTextStyle.Default
            },
        )
        if (indexBadge != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$indexBadge",
                color = fg.copy(alpha = 0.38f),
                fontSize = 13.sp,
            )
        }
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            LineIcon(glyph = "chevron", color = fg.copy(alpha = 0.45f), size = 17.dp, strokeWidthUnits = 1.6f)
        }
    }
}
