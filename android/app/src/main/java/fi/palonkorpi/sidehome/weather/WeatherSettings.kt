package fi.palonkorpi.sidehome.weather

import fi.palonkorpi.sidehome.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.settings.SettingsStore
import fi.palonkorpi.sidehome.settings.tempUnitAsState
import fi.palonkorpi.sidehome.settings.weatherEnabledAsState
import fi.palonkorpi.sidehome.settings.weatherPlaceAsState

/**
 * The §6 settings mirror of the Future pane's two weather settings (SID-35).
 *
 * SPEC §4a asks for the location to be editable **two ways**: from the forecast itself, where you
 * noticed it was wrong, and from the settings screen, where you'd go looking if you didn't think to
 * tap the pane. Both write the same `SettingsStore.weatherPlace`, and the shared-prefs listener is
 * what makes a change made here show up on a pane that is already composed.
 *
 * These rows are deliberately shaped like `SettingsActivity`'s own — a label on the left, the state
 * on the right — rather than exported from it, because that file belongs to the settings screen and
 * this is a weather feature that happens to be visible there.
 */
@Composable
fun WeatherSettingsSection(
    settings: SettingsStore,
    fg: Color,
    accent: Color,
    onEditLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place by settings.weatherPlaceAsState()
    val unit by settings.tempUnitAsState()
    val enabled by settings.weatherEnabledAsState()

    Column(modifier) {
        Text(tr("Weather", "Wetter"), color = fg, fontWeight = FontWeight.Light, fontSize = 22.sp)
        Spacer(Modifier.height(14.dp))

        // SID-181: the master switch. Off removes the weather from the launcher — the home's glance
        // line and the swipe-left pane both — and stops all fetching. The location and unit prefs
        // survive underneath, so on restores exactly what was configured; their rows hide while off
        // because settings for a thing that doesn't exist would be a lie.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("Show weather", "Wetter anzeigen"), color = fg, fontSize = 17.sp)
            OnOffToggle(selected = enabled, fg = fg, accent = accent, onSelect = { settings.weatherEnabled = it })
        }

        if (enabled) {
            Spacer(Modifier.height(20.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onEditLocation)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Location", "Ort"), color = fg, fontSize = 17.sp)
                // Accent when set, plain when not: the same "this is live" mark the notification-dots
                // row uses for its granted state, so the settings screen has one vocabulary and not two.
                Text(
                    place?.name ?: tr("Not set — tap to choose", "Nicht gesetzt — antippen"),
                    color = if (place != null) accent else fg.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    fontWeight = if (place != null) FontWeight.SemiBold else FontWeight.Normal,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                tr(
                "Typed, not sensed: SideHome asks Open-Meteo for the coordinates of a place you name, " +
                    "and never asks Android where you are.",
                "Getippt statt geortet: SideHome fragt Open-Meteo nach den Koordinaten eines Ortes, " +
                    "den du nennst — und fragt Android nie, wo du bist.",
            ),
                color = fg.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tr("Temperature", "Temperatur"), color = fg, fontSize = 17.sp)
                UnitToggle(selected = unit, fg = fg, accent = accent, onSelect = { settings.tempUnit = it })
            }
        }
    }
}

/** Off / On in [UnitToggle]'s exact pill — the weather master switch's control (SID-181). */
@Composable
private fun OnOffToggle(
    selected: Boolean,
    fg: Color,
    accent: Color,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fg.copy(alpha = 0.08f)),
    ) {
        listOf(tr("Off", "Aus") to false, tr("On", "An") to true).forEach { (text, value) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text,
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * °C / °F, shaped like `SettingsActivity`'s private `Segmented` — same pill, same accent cushion on
 * the chosen half. Duplicated rather than shared: the alternative was widening that file's private
 * helper into an API, which is a bigger change to a file this feature has no business owning.
 */
@Composable
private fun UnitToggle(
    selected: TempUnit,
    fg: Color,
    accent: Color,
    onSelect: (TempUnit) -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fg.copy(alpha = 0.08f)),
    ) {
        listOf("°C" to TempUnit.CELSIUS, "°F" to TempUnit.FAHRENHEIT).forEach { (text, value) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text,
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
