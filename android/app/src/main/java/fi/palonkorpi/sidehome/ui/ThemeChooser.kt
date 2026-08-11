package fi.palonkorpi.sidehome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import fi.palonkorpi.sidehome.ambient.CUSTOM_SAT_MAX
import fi.palonkorpi.sidehome.ambient.Theme
import fi.palonkorpi.sidehome.ambient.customBaseColor
import fi.palonkorpi.sidehome.ambient.customHueRadians
import fi.palonkorpi.sidehome.ambient.displayName
import fi.palonkorpi.sidehome.ambient.edgeOnly
import fi.palonkorpi.sidehome.ambient.fgDarkFor
import fi.palonkorpi.sidehome.ambient.fieldHueSpread
import fi.palonkorpi.sidehome.ambient.foregroundInk
import fi.palonkorpi.sidehome.ambient.themeMood
import fi.palonkorpi.sidehome.color.Lch
import fi.palonkorpi.sidehome.color.toColor
import fi.palonkorpi.sidehome.i18n.tr
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * The theme picker (SPEC.md §6). A browse-then-commit carousel of live theme tiles with peek
 * slivers of the neighbours. Swiping only *browses* — the real home is untouched — and a tap on
 * the centred tile commits + zooms back in. Back / HOME cancel with no change.
 *
 * This is **not an overlay** (DECISIONS): it's the home at another zoom level, so nothing recedes
 * or dims behind it. The host scales the live scene down onto the focused tile's bounds — which is
 * why [onTileBounds] exists and why [screenAspect] does: the tile carries the *screen's* aspect
 * ratio, since a uniform scale can only land on it exactly if the two shapes agree.
 *
 * Every tile is the one true ambient renderer ([AmbientBackground]) fed the theme's resolved mood
 * and edge-flag, so what you preview is exactly what the home becomes. Because that renderer is
 * static-per-mood (no per-frame animation), rendering every tile "live" costs nothing continuous;
 * if a future animated ambient lands, snapshot the off-centre tiles (perf follow-up).
 *
 * [solarMood] is the current live solar mood — the drifting themes (1 & 4) preview against it.
 * [active] is false while the picker is fading out on close: it stays mounted so the carousel can
 * fade *as* the home zooms back in, but must stop taking Back and taps the moment it's dismissed.
 */
@Composable
fun ThemeChooser(
    active: Boolean,
    currentTheme: Theme,
    customHue: Float,
    onCustomHue: (Float) -> Unit,
    customSat: Float,
    onCustomSat: (Float) -> Unit,
    customDrift: Boolean,
    onCustomDrift: (Boolean) -> Unit,
    photoMood: Lch?,
    photoPreview: ImageBitmap?,
    onChoosePhoto: () -> Unit,
    solarMood: Lch,
    timeStr: String,
    screenAspect: Float,
    fg: Color,
    accent: Color,
    onTileBounds: (Rect) -> Unit,
    onCommit: (Theme) -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val themes = Theme.entries
    val pagerState = rememberPagerState(initialPage = currentTheme.ordinal) { themes.size }
    val scope = rememberCoroutineScope()
    val focused = themes[pagerState.currentPage]

    // Back and HOME both cancel (zoom in, unchanged). HOME is additionally handled by the host's
    // homeRecall reset; this covers the system Back gesture while the chooser is up.
    BackHandler(enabled = active, onBack = onCancel)

    // No scrim and no background: the host paints one flat fill under the whole scene instead, so
    // the picker has nothing of its own to dim. A scrim here would darken that fill and the
    // zooming home along with it.
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val tileW = maxWidth * TILE_WIDTH_FRACTION
        val tileH = tileW / screenAspect
        val sidePad = (maxWidth - tileW) / 2
        val tileWpx: Float
        val tileHpx: Float
        with(LocalDensity.current) {
            tileWpx = tileW.toPx()
            tileHpx = tileH.toPx()
        }

        Column(
            // Centre within the safe area, not the raw screen: the near-screen-tall stack would
            // otherwise ride the "Theme" title up under the status bar / clock on this 640px screen.
            // The pager's bounds are still measured live below, so the zoom target stays aligned.
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("Theme", "Design"),
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.18.em,
            )
            Spacer(Modifier.height(12.dp))

            HorizontalPager(
                state = pagerState,
                // the side padding is whatever the centred tile doesn't use, so the neighbours
                // peek by exactly that much on each edge
                contentPadding = PaddingValues(horizontal = sidePad),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tileH)
                    // The centred tile's rect, derived from the pager's own static box — never
                    // from a page. Pages slide under the finger, so reporting a page's bounds
                    // republished this every frame of a swipe: it dragged the scaled home along
                    // behind the carousel (visible wherever a tile's peek alpha dropped below 1)
                    // and, because the host reads it at composition scope, recomposed the tree on
                    // every frame. The centred slot never moves, so this fires once per layout.
                    .onGloballyPositioned { coords ->
                        val r = coords.boundsInRoot()
                        onTileBounds(
                            Rect(
                                left = r.center.x - tileWpx / 2f,
                                top = r.center.y - tileHpx / 2f,
                                right = r.center.x + tileWpx / 2f,
                                bottom = r.center.y + tileHpx / 2f,
                            ),
                        )
                    },
            ) { page ->
                val theme = themes[page]
                val isFocused = page == pagerState.currentPage
                // distance of this page from the settled centre (0 = centred, 1 = a full page away)
                val offset =
                    ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                val scale = lerp(0.82f, 1f, 1f - offset)
                val alpha = lerp(0.5f, 1f, 1f - offset)

                ThemeTile(
                    theme = theme,
                    mood = themeMood(
                        theme, solarMood, customHueRadians(customHue), customSat, customDrift,
                        photoMood = photoMood,
                    ),
                    edgeOnly = edgeOnly(theme),
                    photo = if (theme == Theme.PHOTO) photoPreview else null,
                    isCurrent = theme == currentTheme,
                    accent = accent,
                    timeStr = timeStr,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .clickable(enabled = active) {
                            // tap centred = commit; tap a peeking neighbour = browse to it
                            if (isFocused) onCommit(theme)
                            else scope.launch { pagerState.animateScrollToPage(page) }
                        },
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                focused.displayName,
                color = fg,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            // Per-theme customization slot: the Custom theme's seed-hue picker, an empty cushion for
            // the rest (photo picker etc. land later, SID-43).
            Spacer(Modifier.height(10.dp))
            CustomizationSlot(
                isCustom = focused == Theme.CUSTOM,
                isPhoto = focused == Theme.PHOTO,
                photoPicked = photoPreview != null,
                onChoosePhoto = onChoosePhoto,
                hue = customHue,
                onHue = onCustomHue,
                sat = customSat,
                onSat = onCustomSat,
                drift = customDrift,
                onDrift = onCustomDrift,
                fg = fg,
            )

            Spacer(Modifier.height(14.dp))
            Text(
                if (focused == currentTheme) {
                    tr("Current theme", "Aktuelles Design")
                } else {
                    tr("Tap to apply", "Antippen zum Anwenden")
                },
                color = fg,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }

        // SID-15: the "customise your home" gear. The theme picker is the home at another zoom, so
        // this is where settings live — §1 keeps them off the home itself. Only live while the
        // picker is; inset so it clears the OS clock on this 640px screen, like the header above.
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(10.dp),
            ) {
                LineIcon(glyph = "settings", color = fg, size = 22.dp)
            }
        }
    }
}

/** A single carousel card: the real ambient renderer, framed, with a mini home preview on top. */
@Composable
private fun ThemeTile(
    theme: Theme,
    mood: Lch,
    edgeOnly: Boolean,
    isCurrent: Boolean,
    accent: Color,
    timeStr: String,
    modifier: Modifier = Modifier,
    photo: ImageBitmap? = null,
) {
    // The tile's own foreground ink, chosen the same way the home does — so the preview also shows
    // how each theme adapts its text. Edge-dark sits over a dark centre, so it always inks light.
    val tileFg =
        if (edgeOnly) foregroundInk(false)
        else foregroundInk(fgDarkFor(mood.l, mood.l > 0.55))

    Box(
        modifier
            .clip(RoundedCornerShape(TILE_CORNER))
            .background(Color.Black)
            // A hairline rim, because the backdrop is black: Full dark and Edge would otherwise
            // bleed straight into it and lose their shape. Translucent white, so it does its work
            // exactly where it's needed — plain on the dark tiles, near-invisible on the light
            // ones, which already separate from black on their own. A border is a surface, not
            // copy, so DECISIONS' no-muted rule doesn't bind it.
            .border(TILE_BORDER, Color.White.copy(alpha = 0.20f), RoundedCornerShape(TILE_CORNER)),
    ) {
        // The Photo tile previews the crisp bake itself — the truest possible preview, and the sky
        // renderer never runs for it. Before a photo is picked it falls through to the ambient (its
        // mood fallback), so the tile is never a black hole.
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AmbientBackground(
                mood = mood,
                edgeOnly = edgeOnly,
                hueSpread = fieldHueSpread(theme),
                modifier = Modifier.fillMaxSize(),
            )
        }

        // A mock of the home — no real favourites, no real date, no second render — but a
        // *geometrically* exact one: every value is [HomeMetrics] times [TILE_WIDTH_FRACTION],
        // the same factor the host zooms the scene by. So the mock's skeleton is the home's
        // skeleton at the size the home arrives at, and the clock lands on itself rather than
        // ghosting a few dp above its double through the crossfade.
        //
        // The clock shows the *real* time. It was a hardcoded "9:41", which popped the instant
        // the home zoomed into the tile.
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = HomeMetrics.PadStart * TILE_WIDTH_FRACTION,
                    end = HomeMetrics.PadEnd * TILE_WIDTH_FRACTION,
                    top = HomeMetrics.PadTop * TILE_WIDTH_FRACTION,
                    bottom = HomeMetrics.PadBottom * TILE_WIDTH_FRACTION,
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            // Mirrors the home's split clock (HomePage) so the mock's clock lands on the real one
            // through the zoom instead of ghosting a wrapped "pm" against a single-line time.
            val meridiem = timeStr.substringAfter(' ', "")
            val timeMain = if (meridiem.isEmpty()) timeStr else timeStr.substringBefore(' ')
            Row(
                modifier = Modifier.padding(horizontal = 2.dp * TILE_WIDTH_FRACTION),
            ) {
                Text(
                    timeMain,
                    color = tileFg,
                    fontSize = (HomeMetrics.ClockSp * TILE_WIDTH_FRACTION).sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.03).em,
                    modifier = Modifier.alignByBaseline(),
                )
                if (meridiem.isNotEmpty()) {
                    Text(
                        meridiem,
                        color = tileFg,
                        fontSize = (HomeMetrics.ClockSp * 0.32f * TILE_WIDTH_FRACTION).sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = 8.dp * TILE_WIDTH_FRACTION),
                    )
                }
            }
            Spacer(Modifier.height(HomeMetrics.ClockDateGap * TILE_WIDTH_FRACTION))
            PreviewDate(tileFg)
            Spacer(Modifier.height(HomeMetrics.DateRowsGap * TILE_WIDTH_FRACTION))
            repeat(HomeMetrics.MockRows) { PreviewRow(tileFg) }
        }

        // The applied theme wears an accent check badge so the current choice is unmistakable.
        if (isCurrent) {
            Box(
                Modifier
                    .padding(10.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("✓", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * The mock's date line. Draws a bar, but reserves exactly the height the home's date Text takes
 * at the tile's scale — the clock above and the rows below only land on their real counterparts
 * if this occupies the same space they push against.
 */
@Composable
private fun PreviewDate(tileFg: Color) {
    Box(
        Modifier.height(
            HomeMetrics.DateSp.dp * HomeMetrics.DateLineFactor * TILE_WIDTH_FRACTION,
        ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .height(4.dp)
                .fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(999.dp))
                .background(tileFg.copy(alpha = 0.55f)),
        )
    }
}

/**
 * One faux favourite row: an icon block and a label bar, tinted with the tile's ink. Its height is
 * the real [HomeRow]'s — icon plus its vertical padding, the icon being taller than the label —
 * scaled to the tile, so four of these stack to the same block the home's four favourites do.
 */
@Composable
private fun PreviewRow(tileFg: Color) {
    Row(
        Modifier
            .height((HomeMetrics.RowIcon + HomeMetrics.RowPadV * 2) * TILE_WIDTH_FRACTION)
            .padding(horizontal = HomeMetrics.RowPadH * TILE_WIDTH_FRACTION),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(HomeMetrics.RowIcon * TILE_WIDTH_FRACTION)
                .clip(RoundedCornerShape(4.dp))
                .background(tileFg.copy(alpha = 0.9f)),
        )
        Spacer(Modifier.width(HomeMetrics.RowGap * TILE_WIDTH_FRACTION))
        Box(
            Modifier
                .height(5.dp)
                .fillMaxWidth(0.55f)
                .clip(RoundedCornerShape(999.dp))
                .background(tileFg.copy(alpha = 0.75f)),
        )
    }
}

/**
 * The per-theme customization slot below the name. For the Custom theme it holds the seed-hue picker
 * (SID-15); for every other theme it is the empty cushion it has always been — reserved layout for
 * later per-theme controls (e.g. the photo picker for the deferred Photo theme, SID-43). The cushion
 * is a translucent surface fill, not copy: DECISIONS' no-muted-text rule binds here, so the slot stays
 * wordless rather than carrying a dimmed label.
 */
@Composable
private fun CustomizationSlot(
    isCustom: Boolean,
    isPhoto: Boolean,
    photoPicked: Boolean,
    onChoosePhoto: () -> Unit,
    hue: Float,
    onHue: (Float) -> Unit,
    sat: Float,
    onSat: (Float) -> Unit,
    drift: Boolean,
    onDrift: (Boolean) -> Unit,
    fg: Color,
) {
    // The full hue wheel at the current vividness — the colours the seed can be, resolved through the
    // same [customBaseColor] the home uses, so it previews exactly. Re-derived when the saturation
    // slider moves (the strip shifts pastel↔vivid with it); kept unconditional (not inside the
    // isCustom branch) so the remember never moves.
    val hueSpectrum = remember(sat) {
        Brush.horizontalGradient(
            (0..360 step 30).map { deg ->
                customBaseColor(customHueRadians(deg.toFloat()), sat).toColor()
            },
        )
    }
    // The Photo theme's one control (SID-43): pick the image. Sized exactly like the cushion so
    // the slot never jumps between pages; a labelled surface rather than a wordless fill because
    // this one *is* an action, and DECISIONS' no-muted rule is met with full-strength ink.
    if (isPhoto) {
        Box(
            Modifier
                .fillMaxWidth(0.72f)
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(fg.copy(alpha = 0.10f))
                .clickable(onClick = onChoosePhoto),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(glyph = "photo", color = fg, size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (photoPicked) tr("Change image", "Bild ändern") else tr("Choose image", "Bild wählen"),
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        return
    }
    if (!isCustom) {
        Box(
            Modifier
                .fillMaxWidth(0.72f)
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(fg.copy(alpha = 0.06f)),
        )
        return
    }
    // Two seed controls (SID-15): hue (which colour) and saturation — a pastel→saturated vividness axis
    // (SID-177), not grey→colour. Both are previewed through [customBaseColor], the same resolver the
    // home uses, so the sliders show exactly what the home becomes. Below them, the day-adjust toggle
    // (SID-177): off = the fixed colour, on = it dims with the day. Lightness stays engine-controlled,
    // so nothing picked here can go illegible.
    Column(Modifier.fillMaxWidth(0.72f)) {
        GradientSlider(
            fraction = (hue / 360f).coerceIn(0f, 1f),
            track = hueSpectrum,
            onFraction = { onHue(it * 360f) },
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
        Spacer(Modifier.height(8.dp))
        GradientSlider(
            fraction = (sat / CUSTOM_SAT_MAX).coerceIn(0f, 1f),
            // Pastel (sat 0) → saturated (sat max) at the chosen hue — the true axis, endpoints resolved
            // through the same helper the home uses.
            track = Brush.horizontalGradient(
                listOf(
                    customBaseColor(customHueRadians(hue), 0f).toColor(),
                    customBaseColor(customHueRadians(hue), CUSTOM_SAT_MAX).toColor(),
                ),
            ),
            onFraction = { onSat(it * CUSTOM_SAT_MAX) },
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
        Spacer(Modifier.height(12.dp))
        DayAdjustToggle(on = drift, onToggle = onDrift, fg = fg)
    }
}

/**
 * The Custom theme's "Adjust with the day" toggle (SID-177). Off by default: Custom is the colour you
 * chose, unchanging; Ambient is the theme that tracks the sun. On dims the chosen colour by the day
 * cycle. A label plus a pill switch — full-strength ink (DECISIONS' no-muted-text rule binds the label);
 * the track carries the on/off state in fill, not a dimmed word.
 */
@Composable
private fun DayAdjustToggle(on: Boolean, onToggle: (Boolean) -> Unit, fg: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!on) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr("Adjust with the day", "Folgt dem Tageslauf"),
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        // A compact pill switch: the track fills with the ink when on, sits as a faint outline when off;
        // the knob slides end to end. No third-party Switch — this matches the chooser's own visual weight.
        Box(
            Modifier
                .width(40.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (on) fg else fg.copy(alpha = 0.14f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .offset(x = if (on) 18.dp else 0.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (on) Color.Black else Color.White),
            )
        }
    }
}

/**
 * A horizontal slider over a colour [track] (SID-15): touch-down or drag anywhere sets the value from
 * the x fraction (0..1), with a thumb at the current fraction. Drives the Custom theme's hue and
 * saturation. Key-nav for these is a device follow-up — the chooser is touch-browsed today.
 */
@Composable
private fun GradientSlider(
    fraction: Float,
    track: Brush,
    onFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(track)
            .pointerInput(Unit) {
                // One gesture owns both tap and drag: set on touch-down, then track every move until
                // the finger lifts. Consuming keeps it off the arbiter under the chooser.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onFraction((down.position.x / size.width).coerceIn(0f, 1f))
                    down.consume()
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        onFraction((change.position.x / size.width).coerceIn(0f, 1f))
                        change.consume()
                    }
                }
            },
    ) {
        // The thumb, kept fully inside the bar at both ends (its offset spans 0..width − thumb).
        val thumb = 16.dp
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth - thumb) * fraction.coerceIn(0f, 1f))
                .size(thumb)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.3f), CircleShape),
        )
    }
}
