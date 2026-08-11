package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.apps.BuiltInCategory
import fi.palonkorpi.sidehome.apps.Category
import fi.palonkorpi.sidehome.i18n.tr

// ---- pure logic (Compose-free, so it can be unit-tested) ------------------------------------

/**
 * The ordered choice list the picker offers: the fixed built-ins first, in their enum order, then
 * the user's own buckets in creation order (SID-61/62).
 *
 * Assembled from a *source* — never a hardcoded enum — so a bucket made from the §1 menu shows up
 * the moment it exists, and so the built-in set can grow without this drifting. [userCategories] is
 * appended as-is: its ids are `"user:N"` and can never collide with a built-in's enum-name id, so no
 * de-duplication is needed and creation order is preserved.
 */
fun categoryChoices(userCategories: List<Category>): List<Category> =
    BuiltInCategory.entries.map { it.category } + userCategories

/**
 * Whether [candidate] is the app's currently-resolved category — the one the picker marks with the
 * accent. Compared on [Category.id] (identity), never label/glyph, which are display-only and, for a
 * user bucket, editable.
 */
fun isCurrentCategory(candidate: Category, currentCategoryId: String): Boolean =
    candidate.id == currentCategoryId

// ---- the picker surface ---------------------------------------------------------------------

/**
 * The category picker: reached from the §1 action strip's Category cell on a *library* app (SID-62).
 * A full-screen, frameless surface like [IconPicker] — the home has already receded beneath it — that
 * lists every category (built-ins then the user's own buckets) and lets the app be pinned to one.
 *
 * The app's currently-resolved category ([currentCategoryId] = `entry.category.id`) is marked with
 * the accent, so the standing choice is legible; picking any row writes a per-app override. Two extra
 * affordances close the loop: **Use the suggestion** clears the override so the app falls back to the
 * engine's guess, and **New category…** creates a user bucket (SID-61) and assigns this app to it in
 * one move — the name via a real [BasicTextField] (through the active IME, like [RenamePopover]) and
 * the glyph via the same categorized [IconPicker] the Change-icon cell uses.
 */
@Composable
fun CategoryPicker(
    currentCategoryId: String,
    categories: List<Category>,
    fg: Color,
    accent: Color,
    panelColor: Color,
    onSelect: (Category) -> Unit,
    onClearOverride: () -> Unit,
    onCreateCategory: (label: String, glyph: String) -> Unit,
    onClose: () -> Unit,
) {
    // The new-category sub-flow. Hoisted to the picker (not the sheet) so the typed name and chosen
    // glyph survive the round-trip out to the glyph picker and back — the sheet unmounts while that
    // is up, and state it held itself would reset each time.
    var creating by remember { mutableStateOf(false) }
    var pickingGlyph by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf(TextFieldValue("")) }
    var newGlyph by remember { mutableStateOf(DEFAULT_NEW_CATEGORY_GLYPH) }

    val enter = rememberOverlayEnter()
    Box(Modifier.fillMaxSize().then(enter)) {
        // Glyph picking replaces the whole surface rather than layering over it: the picker already
        // sits on an opaque backdrop (its call site), so rendering only the IconPicker here means the
        // rows aren't composed behind it — the "stacked overlays don't bleed" rule, for free.
        if (pickingGlyph) {
            IconPicker(
                title = tr("Category icon", "Kategorie-Symbol"),
                current = newGlyph,
                fg = fg,
                accent = accent,
                onPick = { g -> newGlyph = g; pickingGlyph = false },
                onClose = { pickingGlyph = false },
            )
            return@Box
        }

        // The list layer. Recedes (fades) when the new-category sheet floats over it, so only one
        // surface reads at a time — no live blur on the A22, a plain alpha drop instead. The fading
        // layer is applied *only* while the sheet is up: a persistent compositing layer would render
        // the scrolling list offscreen every frame and tank the fling on this hardware (see Anim.kt).
        Column(
            Modifier
                .fillMaxSize()
                .then(if (creating) Modifier.graphicsLayer { alpha = 0.4f } else Modifier)
                .padding(top = 36.dp)
                .padding(start = 28.dp, end = 18.dp, bottom = 12.dp),
        ) {
            CategoryPickerHeader(tr("Category", "Kategorie"), fg, accent, onClose)
            Spacer(Modifier.height(10.dp))
            LazyColumn {
                items(categories, key = { it.id }) { cat ->
                    CategoryRow(
                        glyph = cat.glyph,
                        label = cat.label,
                        current = isCurrentCategory(cat, currentCategoryId),
                        fg = fg,
                        accent = accent,
                        onClick = { onSelect(cat) },
                    )
                }
                // The two loop-closing affordances, set a little apart from the choices proper.
                // One item wrapping a Column: a lazy item with several direct children stacks them,
                // so the rows share a real column to lay out one under the other.
                item {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        // Clear the override → the engine's guess resolves again. Never marked
                        // current: the picker only sees the *resolved* category, not whether an
                        // override produced it, so it can't honestly claim this is the standing state.
                        CategoryRow(
                            glyph = "app",
                            label = tr("Use the suggestion", "Vorschlag übernehmen"),
                            current = false,
                            fg = fg,
                            accent = accent,
                            onClick = onClearOverride,
                        )
                        CategoryRow(
                            glyph = "folderplus",
                            label = tr("New category…", "Neue Kategorie…"),
                            current = false,
                            fg = fg,
                            accent = accent,
                            onClick = { creating = true },
                        )
                    }
                }
            }
        }

        // The new-category sheet: a lightweight centred float in [RenamePopover]'s grammar, over the
        // faded list. Its name + glyph live in the picker's state above, so it is a pure view of them.
        if (creating) {
            fun dismissCreate() { creating = false }
            NewCategorySheet(
                name = newLabel,
                glyph = newGlyph,
                fg = fg,
                accent = accent,
                panelColor = panelColor,
                onNameChange = { newLabel = it },
                onGlyphClick = { pickingGlyph = true },
                onCreate = {
                    val label = newLabel.text.trim()
                    // Blank names simply close (no dead-end, no dimmed button) — the same shape
                    // RenamePopover's commit takes. A real name creates the bucket and assigns it.
                    if (label.isNotBlank()) onCreateCategory(label, newGlyph) else dismissCreate()
                },
                onDismiss = { dismissCreate() },
            )
        }
    }
}

/** The picker's title row + Done, mirroring [IconPicker]'s own (private) header. */
@Composable
private fun CategoryPickerHeader(title: String, fg: Color, accent: Color, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = fg, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(
            tr("Done", "Fertig"), color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClose() }.padding(4.dp),
        )
    }
}

/** One category choice: its glyph in an outlined tile, its label, and — when current — an accent tick. */
@Composable
private fun CategoryRow(
    glyph: String,
    label: String,
    current: Boolean,
    fg: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .drawBehind { drawPickerTileOutline(if (current) accent else fg.copy(alpha = 0.18f)) },
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(glyph = glyph, color = if (current) accent else fg, size = 22.dp, strokeWidthUnits = 1.6f)
        }
        Spacer(Modifier.width(14.dp))
        // Full-strength fg always (DECISIONS: no muted text); the accent is carried by the tile and
        // the trailing tick, never by dimming the copy.
        Text(label, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (current) {
            LineIcon(glyph = "check", color = accent, size = 20.dp, strokeWidthUnits = 1.8f)
        }
    }
}

/**
 * Name-and-glyph for a brand-new user bucket (SID-61). A [BasicTextField] for the name — routed
 * through the active IME like [RenamePopover], not raw key codes — beside a tappable glyph tile that
 * opens the categorized [IconPicker]. State is hoisted to [CategoryPicker], so this is a pure view.
 */
@Composable
private fun NewCategorySheet(
    name: TextFieldValue,
    glyph: String,
    fg: Color,
    accent: Color,
    panelColor: Color,
    onNameChange: (TextFieldValue) -> Unit,
    onGlyphClick: () -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The glyph the bucket will carry — tap to reassign via the same picker Change-icon uses.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .drawBehind { drawPickerTileOutline(fg.copy(alpha = 0.18f)) }
                    .clickable { onGlyphClick() },
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(glyph = glyph, color = fg, size = 22.dp, strokeWidthUnits = 1.6f)
            }
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 18.sp, fontFamily = Inter),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCreate() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (name.text.isEmpty()) {
                            Text(tr("New category", "Neue Kategorie"), color = fg.copy(alpha = 0.4f), fontSize = 18.sp)
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .focusRequester(focus)
                    .weight(1f)
                    .padding(vertical = 6.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                tr("Create", "Erstellen"), color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCreate() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Sensible glyph a new bucket carries until the user picks another — the neutral tile. */
private const val DEFAULT_NEW_CATEGORY_GLYPH = "app"

/** The rounded outline shared by the picker's tiles (mirrors [IconPicker]'s). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPickerTileOutline(color: Color) {
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
    )
}
