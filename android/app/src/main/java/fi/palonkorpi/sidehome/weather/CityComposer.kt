package fi.palonkorpi.sidehome.weather

import fi.palonkorpi.sidehome.i18n.tr
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.settings.SettingsStore
import fi.palonkorpi.sidehome.ui.Inter
import fi.palonkorpi.sidehome.ui.LineIcon
import kotlinx.coroutines.delay

/**
 * Typing a city (SPEC.md §4a, SID-35). Reached the two ways the spec asks for: the place-name on the
 * forecast is a button, and the §6 settings screen mirrors the same row — so changing city is
 * semi-easy from wherever you noticed it was wrong.
 *
 * **This is the whole location story.** No `ACCESS_COARSE_LOCATION`, no `ACCESS_FINE_LOCATION`, no
 * `LocationManager`. On a de-Googled phone the `NETWORK_PROVIDER` that makes automatic location
 * pleasant is Google's, and it isn't installed; what's left is a GPS fix — tens of seconds, outdoors,
 * with the radio lit — to answer a question whose precision requirement is "which city". Typing it
 * once is faster, works indoors, works on the first run, and asks the user for nothing.
 *
 * Search runs on the physical QWERTY, debounced, against Open-Meteo's geocoder — the same keyless
 * host as the forecast. Picking a hit writes `SettingsStore.weatherPlace` and that write *is* the
 * commit: [WeatherStore] notices the coords moved and refetches on the next look at the pane.
 */
@Composable
fun CityComposer(
    settings: SettingsStore,
    store: WeatherStore,
    fg: Color,
    accent: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<GeoResult>()) }
    var searching by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // Same test the drawer's search field uses (SID-21): with the compact tile present the field
    // takes focus on open and the keyboard is already the input; a virtual-only device opens
    // unfocused rather than throwing a keyboard over its own results.
    val configuration = LocalConfiguration.current
    val autoFocus = configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO

    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }

    BackHandler(enabled = true, onBack = onDismiss)

    // Debounced, so a six-letter city is one request rather than six. The delay sits *before* the
    // call and after the cancellation point, so each keystroke cancels the pending search outright —
    // there is no in-flight request to arrive late and overwrite a newer one's results.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < MIN_QUERY) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(DEBOUNCE_MS)
        results = store.search(q)
        searching = false
    }

    val rule = fg.copy(alpha = 0.22f)

    Column(
        modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 28.dp, top = 40.dp, bottom = 44.dp),
    ) {
        Text(
            tr("Where are you?", "Wo bist du?"),
            color = fg,
            fontFamily = Inter,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(14.dp))

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = fg, fontSize = 18.sp, fontFamily = Inter),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                results.firstOrNull()?.let { commit(settings, it, onDismiss) }
            }),
            modifier = Modifier
                .focusRequester(focus)
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 2.dp)
                .drawBehind {
                    val y = size.height
                    drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                },
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(glyph = "search", color = fg, size = 20.dp, strokeWidthUnits = 1.7f)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                if (autoFocus) tr("Type a city", "Stadt eintippen") else tr("Tap, then type a city", "Antippen, dann Stadt eintippen"),
                                color = fg.copy(alpha = 0.6f),
                                fontFamily = Inter,
                                fontSize = 18.sp,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )

        Spacer(Modifier.height(10.dp))

        results.forEach { hit ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { commit(settings, hit, onDismiss) }
                    .padding(vertical = 9.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(hit.place.name, color = fg, fontFamily = Inter, fontSize = 17.sp)
                if (hit.region.isNotBlank()) {
                    Text(
                        hit.region,
                        color = fg,
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }

        // One quiet status line, and never an error dialog (SPEC §4a). "Nothing came back" is the
        // same sentence whether the geocoder has no such city or the phone has no signal — we can't
        // tell those apart from here, and the user's next move ("check the spelling, or the network")
        // is the same either way.
        val status = when {
            searching && results.isEmpty() -> tr("Searching…", "Suche…")
            query.trim().length >= MIN_QUERY && !searching && results.isEmpty() ->
                tr(
                    "Nothing came back. Check the spelling — or whether you're online.",
                    "Nichts gefunden. Prüf die Schreibweise — oder ob du online bist.",
                )
            else -> null
        }
        if (status != null) {
            Text(
                status,
                color = fg,
                fontFamily = Inter,
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            tr("Cancel", "Abbrechen"),
            color = accent,
            fontFamily = Inter,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(vertical = 6.dp, horizontal = 2.dp),
        )
    }
}

/** Two letters is where a geocoder stops guessing at the whole world. */
private const val MIN_QUERY = 2

/** Long enough to cover a typist's gap between keys, short enough not to feel like waiting. */
private const val DEBOUNCE_MS = 350L

/** Picking a place is the commit: the write moves the coords, and the pane refetches on next view. */
private fun commit(settings: SettingsStore, hit: GeoResult, onDismiss: () -> Unit) {
    settings.weatherPlace = hit.place
    onDismiss()
}
