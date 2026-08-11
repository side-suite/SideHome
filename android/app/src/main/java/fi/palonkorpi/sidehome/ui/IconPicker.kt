package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.i18n.tr

/**
 * The categorized Iconoir picker: reached from the §1 action strip's Change-icon cell, for an app
 * (the glyph is a property of the app, so the change propagates everywhere it appears) or for a
 * home folder. Frameless like the drawer — the home has already receded beneath it.
 *
 * Lived in EditSheet.kt until SID-28 retired that sheet; it is the one piece of it the strip reuses.
 */
@Composable
fun IconPicker(
    title: String,
    current: String,
    fg: Color,
    accent: Color,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    val enter = rememberOverlayEnter()
    Box(Modifier.fillMaxSize().then(enter)) {
        Column(Modifier.fillMaxSize().padding(top = 36.dp).padding(start = 28.dp, end = 18.dp, bottom = 12.dp)) {
            PickerHeader(title, fg, accent, onClose)
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(5)) {
                ICON_CATEGORIES.forEach { cat ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            cat.name.uppercase(),
                            color = fg.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.12.em,
                            modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(cat.keys.size) { idx ->
                        val g = cat.keys[idx]
                        val on = g == current
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .drawBehind {
                                    drawRoundRectOutline(if (on) accent else fg.copy(alpha = 0.18f))
                                }
                                .clickable { onPick(g) },
                            contentAlignment = Alignment.Center,
                        ) {
                            LineIcon(glyph = g, color = if (on) accent else fg, size = 24.dp, strokeWidthUnits = 1.6f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(title: String, fg: Color, accent: Color, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, color = fg, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(
            tr("Done", "Fertig"), color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onClose() }.padding(4.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectOutline(color: Color) {
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
    )
}
