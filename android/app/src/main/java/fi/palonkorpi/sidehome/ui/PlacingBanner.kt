package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.i18n.tr

/**
 * SID-30 (SPEC §1): the "Placing X" banner — what says so while something waits, with no finger on
 * it, for a tap to land it.
 *
 * It exists because that carry is the one with nothing to explain it. A finger drag is its own
 * caption: you are holding the thing, and the targets lit under it are obviously about the thing you
 * are holding. The menu's routes (Add to home, Move) hand the item over and then *leave* — the
 * drawer closes, the menu goes, and the home the user was not touching lights up on its own. Without
 * a word for it, the rings read as the home having broken rather than as a question being asked.
 *
 * It also carries the way out. Back cancels a placement (§1), but a banner that only names the app
 * leaves that as folklore, so the line under it says both what to do and how to stop — the two
 * things that are not otherwise on screen.
 *
 * Parked at the top, deliberately: every answer is down among the rows and the remove pill, and a
 * caption must never sit on top of an answer.
 */
@Composable
fun PlacingBanner(
    label: String,
    fg: Color,
    panelColor: Color,
    modifier: Modifier = Modifier,
) {
    // Drops in from above rather than rising: it belongs to the top edge, and a banner that rose
    // would read as having come up off the home, which is where the targets are.
    val enter = rememberOverlayEnter(riseDp = -8f)
    Column(
        modifier = modifier
            .then(enter)
            .clip(RoundedCornerShape(14.dp))
            .background(panelColor)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Full-strength fg for both lines (DECISIONS: no muted text anywhere) — the size difference
        // is the hierarchy; only the pill under them is a translucent mix.
        Text(
            text = tr("Placing $label", "$label platzieren"),
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = tr("Tap where it goes · Back cancels", "Antippen, wo es hin soll · Zurück bricht ab"),
            color = fg,
            fontSize = 11.sp,
        )
    }
}
