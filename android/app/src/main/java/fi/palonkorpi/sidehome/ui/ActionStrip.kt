package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.i18n.tr
import kotlin.math.roundToInt

// ---- pure logic (Compose-free, so it can be unit-tested) ------------------------------------

/**
 * Which item raised the strip. Each maps to exactly one variant below. [FOLDER_MEMBER] is an app
 * held inside an open folder (SID-31): the folder's contents are managed in the same grammar as the
 * home's — hold the thing itself — so there is no edit surface to go to, here or anywhere.
 */
enum class ActionTarget { LIBRARY_APP, HOME_APP, HOME_FOLDER, FOLDER_MEMBER }

/**
 * One cell of the strip: a glyph over a tiny label. Labels are single words — five cells have to
 * stay one clean row on a 376dp-wide screen, and a wrapped label would break the row's height.
 */
enum class StripAction(private val labelEn: String, private val labelDe: String, val glyph: String) {
    CHANGE_ICON("Icon", "Symbol", "pencil"),
    RENAME("Rename", "Name", "pageedit"),
    ADD_HOME("Home", "Home", "home"),
    ADD_FOLDER("Folder", "Ordner", "folderplus"),
    CATEGORY("Category", "Kategorie", "list"),
    MOVE("Move", "Bewegen", "pin"),
    REMOVE("Remove", "Entfernen", "trash"),
    APP_INFO("Info", "Info", "settings"),
    ;

    // A `get()` rather than a constructor-time value: enum constants are built once at class load,
    // and a frozen label would ignore a language change for the rest of the process.
    val label: String get() = tr(labelEn, labelDe)
}

/** The strip is one row of side-by-side cells, so no variant may exceed this (SID-28). */
const val MAX_STRIP_ACTIONS = 5

/** An action as offered: the strip shows every cell of its variant, dimming the ones that can't run. */
data class StripCell(val action: StripAction, val enabled: Boolean)

/**
 * What the pressed item is, as far as the action list cares. [onHome] gates a library app's
 * Add-to-home; [homeCount] gates Move, which needs somewhere to move to.
 *
 * Notably *not* here: whether the home is at its cap. Add-to-home used to dim there, and that
 * dimming was the dead-end itself — a full home is a question now, not a wall (SID-30): the cell
 * hands the app to the placement flow, where dropping it on a row groups or replaces and frees the
 * slot. The only thing that can still make Add-to-home meaningless is the app already being there.
 */
data class ActionContext(
    val target: ActionTarget,
    val onHome: Boolean = false,
    val homeCount: Int = 0,
)

/**
 * The strip's contents for one pressed item, in order. A library app's Add-to-home stays visible but
 * dimmed when it can't run (the app is already placed) so the row doesn't reflow between apps; same
 * for Move on a home of one.
 */
fun actionsFor(ctx: ActionContext): List<StripCell> = when (ctx.target) {
    // No Uninstall cell: the OS's own PackageInstaller dialog didn't resolve on the de-Googled SP-01,
    // so the launcher hands uninstall off to Info instead — the app's system-details page, which
    // always resolves and carries its own Uninstall button.
    ActionTarget.LIBRARY_APP -> listOf(
        StripCell(StripAction.CHANGE_ICON, true),
        StripCell(StripAction.ADD_HOME, !ctx.onHome),
        StripCell(StripAction.CATEGORY, true),
        StripCell(StripAction.APP_INFO, true),
    )

    ActionTarget.HOME_APP -> listOf(
        StripCell(StripAction.CHANGE_ICON, true),
        StripCell(StripAction.ADD_FOLDER, true),
        StripCell(StripAction.MOVE, ctx.homeCount > 1),
        StripCell(StripAction.REMOVE, true),
        StripCell(StripAction.APP_INFO, true),
    )

    ActionTarget.HOME_FOLDER -> listOf(
        StripCell(StripAction.RENAME, true),
        StripCell(StripAction.CHANGE_ICON, true),
        StripCell(StripAction.MOVE, ctx.homeCount > 1),
        StripCell(StripAction.REMOVE, true),
    )

    // An app inside an open folder (SID-31). [StripAction.ADD_HOME] here means *move* to home — the
    // app leaves the folder — and [StripAction.REMOVE] means out of the folder, not off the home,
    // which is what the surface it was raised on already says. Adding members isn't a cell: you add
    // by dropping an app onto the folder, so there is nothing for a fifth one to do.
    ActionTarget.FOLDER_MEMBER -> listOf(
        StripCell(StripAction.CHANGE_ICON, true),
        // Never dimmed, deliberately: a full home doesn't block it. It routes through the in-hand
        // placement flow instead, where replace/nest/combine all make room (SPEC §1) — the cap is
        // resolvable, never a wall, so there is nothing here to disable.
        StripCell(StripAction.ADD_HOME, true),
        StripCell(StripAction.REMOVE, true),
        StripCell(StripAction.APP_INFO, true),
    )
}

/**
 * Does the strip drop below the pressed item? It sits above by default and only flips when there
 * is no room above [topLimit] — i.e. for an item near the top, which is the case that would
 * otherwise slide under the status bar.
 */
fun stripDropsBelow(itemTop: Float, stripHeight: Float, gap: Float, topLimit: Float): Boolean =
    itemTop - gap - stripHeight < topLimit

/**
 * The strip's top edge, in px. Only Y moves: the strip keeps its own width and stays horizontally
 * centred, so there is no left/right clipping to solve (SID-28). It sits above the pressed item,
 * drops below when the top has no room, and is clamped into [topLimit]..[bottomLimit] so it can
 * never leave the safe band. The clamp's upper bound is floored at [topLimit] because on a band
 * shorter than the strip the two bounds would cross, and `coerceIn` throws on an inverted range.
 */
fun stripAnchorY(
    itemTop: Float,
    itemBottom: Float,
    stripHeight: Float,
    gap: Float,
    topLimit: Float,
    bottomLimit: Float,
): Float {
    val y = if (stripDropsBelow(itemTop, stripHeight, gap, topLimit)) {
        itemBottom + gap
    } else {
        itemTop - gap - stripHeight
    }
    return y.coerceIn(topLimit, (bottomLimit - stripHeight).coerceAtLeast(topLimit))
}

// ---- the strip ------------------------------------------------------------------------------

/** Semi-fixed geometry: the strip's height never changes and its width only steps with the cell
 *  count, so a variant swap doesn't reflow the bar — only its Y-anchor moves. */
private val CELL_WIDTH = 64.dp
private val STRIP_HEIGHT = 62.dp

/** Breathing room between the strip and the item it points at. */
private val STRIP_GAP = 10.dp

/** The top of the safe band — matches the overlays' 36dp top padding, i.e. clear of the status bar. */
private val STRIP_TOP_LIMIT = 36.dp

private val STRIP_BOTTOM_MARGIN = 8.dp

/**
 * The §1 anchored action strip: a thin row of icon-over-label cells raised beside the item you are
 * holding. Replaces the pencil + EditSheet — editing is direct manipulation of the thing itself now.
 *
 * It never dims or tints the sky (DECISIONS): the full-screen layer below is a bare tap-catcher, and
 * the home stays live and un-receded behind it — the strip is a lightweight float, not an overlay
 * that covers the home.
 */
@Composable
fun ActionStrip(
    cells: List<StripCell>,
    itemBounds: Rect,
    fg: Color,
    stripColor: Color,
    onAction: (StripAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    showIndexNumbers: Boolean = false,
) {
    val enter = rememberOverlayEnter(riseDp = 8f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        val density = LocalDensity.current
        val y = with(density) {
            stripAnchorY(
                itemTop = itemBounds.top,
                itemBottom = itemBounds.bottom,
                stripHeight = STRIP_HEIGHT.toPx(),
                gap = STRIP_GAP.toPx(),
                topLimit = STRIP_TOP_LIMIT.toPx(),
                bottomLimit = constraints.maxHeight - STRIP_BOTTOM_MARGIN.toPx(),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, y.roundToInt()) }
                .then(enter)
                .height(STRIP_HEIGHT)
                .clip(RoundedCornerShape(16.dp))
                .background(stripColor)
                // swallow taps on the strip itself so they don't reach the catcher and dismiss it
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cells.forEachIndexed { index, cell ->
                StripCellView(
                    cell = cell,
                    fg = fg,
                    selected = index == selectedIndex,
                    indexBadge = if (showIndexNumbers && index < 9) index + 1 else null,
                    onClick = { onAction(cell.action) },
                )
            }
        }
    }
}

@Composable
private fun StripCellView(
    cell: StripCell,
    fg: Color,
    onClick: () -> Unit,
    selected: Boolean = false,
    indexBadge: Int? = null,
) {
    // Dimming marks a cell that can't run — a state, not text hierarchy; the enabled labels stay
    // full-strength fg (DECISIONS).
    val a = if (cell.enabled) 1f else 0.35f
    Box(
        modifier = Modifier
            .width(CELL_WIDTH)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            // The key-nav cursor (SID-84): a quiet fg-tinted fill, only ever set by key.
            .then(if (selected) Modifier.background(fg.copy(alpha = 0.14f)) else Modifier)
            .then(if (cell.enabled) Modifier.clickable { onClick() } else Modifier),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LineIcon(glyph = cell.action.glyph, color = fg.copy(alpha = a), size = 22.dp, strokeWidthUnits = 1.6f)
            Spacer(Modifier.height(5.dp))
            Text(cell.action.label, color = fg.copy(alpha = a), fontSize = 11.sp, maxLines = 1)
        }
        // Faint direct-index number (SID-82) in the corner, so pressing that number fires the cell.
        if (indexBadge != null) {
            Text(
                "$indexBadge",
                color = fg.copy(alpha = 0.4f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 5.dp, top = 3.dp),
            )
        }
    }
}

/**
 * Folder rename, raised by the strip's Rename cell. A real [BasicTextField], so it types through
 * the active IME (JakeType) on every input config rather than reading raw key codes — the same
 * reason the drawer's search migrated (SID-21).
 */
@Composable
fun RenamePopover(
    initial: String,
    fg: Color,
    accent: Color,
    panelColor: Color,
    onCommit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // TextFieldValue, not a plain String: the String overload starts its selection at offset 0, so
    // the caret lands *before* the existing name and backspace does nothing until you tap to the
    // end. Renaming is almost always "fix the tail of what's there", so open with the caret after it.
    var text by remember {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val enter = rememberOverlayEnter(riseDp = 10f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .then(enter)
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(panelColor)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 18.sp, fontFamily = Inter),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCommit(text.text) }),
                modifier = Modifier
                    .focusRequester(focus)
                    .weight(1f)
                    .padding(vertical = 6.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                tr("Done", "Fertig"), color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCommit(text.text) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
