package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.i18n.tr
import fi.palonkorpi.sidehome.photo.clampFramePan
import fi.palonkorpi.sidehome.photo.rotatedMinZoom

/** The positioner's zoom range. 1 = the cover fit; the source is decoded at ~2× the screen, so
 *  even the top end has real pixels behind it. */
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f

/**
 * The Photo theme's framing step (SID-43 crop flow): the picked image, full-bleed at its cover
 * fit, with pinch-zoom, drag, and a two-finger twist — what is on screen when "Use photo" is
 * pressed is *exactly* what the bake renders ([fi.palonkorpi.sidehome.photo.frameMatrix] mirrors
 * this layer's transform). The home's text lives top-left, so the natural move here is dragging
 * the busy part of the photo down-right; the caption says so once, quietly.
 *
 * A full-screen surface like the icon picker — it detaches the arbiter (ladder rung), owns every
 * touch on it, and Back cancels.
 */
@Composable
internal fun PhotoPositioner(
    source: ImageBitmap,
    accent: Color,
    timeStr: String,
    dateStr: String,
    /** The real home rows for the ghost, glyph to label — so framing happens against the actual
     *  clock and apps, not an imagined layout. */
    items: List<Pair<String, String>>,
    onUse: (zoom: Float, panX: Float, panY: Float, rotDeg: Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var rot by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val outW = constraints.maxWidth
        val outH = constraints.maxHeight

        Image(
            bitmap = source,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, panDelta, zoomDelta, rotDelta ->
                        rot = (rot + rotDelta).mod(360f)
                        // A tilted photo needs more scale to keep covering the screen, so the
                        // zoom floor rises with the twist (and the ceiling never dips below it).
                        val floor = rotatedMinZoom(source.width, source.height, outW, outH, rot)
                            .coerceAtLeast(MIN_ZOOM)
                        zoom = (zoom * zoomDelta).coerceIn(floor, maxOf(MAX_ZOOM, floor))
                        val p = pan + panDelta
                        val clamped = clampFramePan(
                            source.width, source.height, outW, outH, zoom, rot, p.x, p.y,
                        )
                        pan = Offset(clamped[0], clamped[1])
                    }
                }
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    rotationZ = rot
                    translationX = pan.x
                    translationY = pan.y
                },
        )

        // The home's skeleton, full-scale, over the photo being framed — the same HomeMetrics
        // numbers HomePage lays out with, so what lands beside the cat is what will land beside
        // the cat. White ink with a soft shadow whatever the photo: this is a framing aid, not
        // the final ink (that derives from the bake). It consumes no touches; the pinch and drag
        // fall straight through to the photo beneath.
        val ghostInk = Color.White.copy(alpha = 0.95f)
        val ghostShadow = TextStyle(
            shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 7f),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = HomeMetrics.PadStart,
                    end = HomeMetrics.PadEnd,
                    top = HomeMetrics.PadTop,
                    bottom = HomeMetrics.PadBottom,
                ),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                // The big clock only — a 12h " pm" suffix at 78sp would wrap on 480px (the old
                // home-clock bug); the ghost needs the clock's footprint, not its punctuation.
                timeStr.substringBefore(' '),
                color = ghostInk,
                fontSize = HomeMetrics.ClockSp.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.03).em,
                style = ghostShadow,
            )
            Spacer(Modifier.height(HomeMetrics.ClockDateGap))
            Text(
                dateStr,
                color = ghostInk,
                fontSize = HomeMetrics.DateSp.sp,
                style = ghostShadow,
            )
            Spacer(Modifier.height(HomeMetrics.DateRowsGap))
            items.take(HomeMetrics.MockRows).forEach { (glyph, label) ->
                Row(
                    Modifier.padding(horizontal = HomeMetrics.RowPadH, vertical = HomeMetrics.RowPadV),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineIcon(glyph = glyph, color = ghostInk, size = HomeMetrics.RowIcon)
                    Spacer(Modifier.width(HomeMetrics.RowGap))
                    Text(
                        label,
                        color = ghostInk,
                        fontSize = HomeMetrics.RowLabelSp.sp,
                        style = ghostShadow,
                    )
                }
            }
        }

        // Legibility scrims for the positioner's own chrome — gradients, not bars, so the photo
        // stays the surface being framed.
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Column {
                Text(
                    tr("Position photo", "Foto ausrichten"),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    tr(
                        "Pinch to zoom or twist, drag to frame around the clock and apps",
                        "Mit zwei Fingern zoomen oder drehen, ziehen zum Einpassen um Uhr und Apps",
                    ),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tr("Cancel", "Abbrechen"),
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tr("Use photo", "Foto verwenden"),
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onUse(zoom, pan.x, pan.y, rot) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
