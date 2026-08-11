package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.i18n.tr
import fi.palonkorpi.sidehome.photo.PhotoBake
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Photo theme's runtime face (SID-43): two static bitmaps and one crossfade.
 *
 * The pick-time pipeline ([PhotoBake]) left a crisp bake (zone scrims for the home) and a blurred
 * bake (what the frameless overlays stand on) on disk. Here they are loaded once per pick and
 * painted where [ContinuousSky] would be; the only motion ever applied is the blurred layer's
 * alpha — a brief tween when a backdrop-less float (strip, rename, confirm, carry) opens, per
 * the photo-theme carve-out in DECISIONS. Never a live blur (A22).
 */

/** The two bakes, in memory. Immutable so a swap recomposes cleanly. */
@Immutable
data class PhotoBakes(val crisp: ImageBitmap, val blurred: ImageBitmap)

/**
 * The bakes for [generation], loaded off the main thread; null while loading, when [active] is
 * false, or when no photo has ever been baked. [generation] is the cache key — a re-pick bumps it
 * and the old bitmaps fall away with the previous produceState value.
 */
@Composable
fun rememberPhotoBakes(generation: Int, active: Boolean): PhotoBakes? {
    val context = LocalContext.current.applicationContext
    val bakes by produceState<PhotoBakes?>(initialValue = null, generation, active) {
        value = if (!active || generation <= 0) null else withContext(Dispatchers.IO) {
            PhotoBake.load(context)?.let { (crisp, blur) ->
                PhotoBakes(crisp.asImageBitmap(), blur.asImageBitmap())
            }
        }
    }
    return bakes
}

/**
 * The backdrop itself: crisp bake always, blurred bake crossfaded over it by [blurFrac] — read
 * **only in the draw phase**, because it now moves with the finger: the pane position drives it
 * (swiping toward the weather dissolves the photo into its blurred bake, so the pane's text lands
 * on calm ground), alongside the overlay tween. The blurred layer stays mounted at alpha 0 rather
 * than unmounting — a fully transparent RenderNode is culled for free, and keeping it avoids a
 * composition subscription to the per-frame pane offset, which is the one thing this layer must
 * never create (the A22 pays for recomposition, not for a static layer).
 */
@Composable
fun PhotoBackdrop(bakes: PhotoBakes, blurFrac: () -> Float, modifier: Modifier = Modifier) {
    Box(modifier) {
        Image(
            bitmap = bakes.crisp,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = blurFrac() }) {
            Image(
                bitmap = bakes.blurred,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The lazy rebake's quiet corner chip (SID-43): the only feedback that the zone blur is being
 * re-fitted to a changed home. A tiny spinner and five words, gone the moment the bake lands.
 */
@Composable
fun RebakeChip(fg: Color, accent: Color, panelColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(panelColor)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(11.dp),
            color = accent,
            strokeWidth = 1.5.dp,
        )
        Spacer(Modifier.width(7.dp))
        Text(tr("updating background blur", "Hintergrund wird aktualisiert"), color = fg, fontSize = 11.sp)
    }
}

/**
 * An overlay's opaque backdrop: the flat sky-derived fill on the mood themes, the blurred bake on
 * the Photo theme — one seam for every site that used to paint `background(backdropColor)`, so the
 * drawer/folder/picker fills all recede to the same photo instead of a flat colour that would
 * replace it (the "no overlay ever dims or tints" rule's photo carve-out, DECISIONS 2026-08-03).
 */
@Composable
fun OverlayBackdrop(color: Color, photo: ImageBitmap?, modifier: Modifier = Modifier) {
    if (photo != null) {
        Image(
            bitmap = photo,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(color))
    }
}
