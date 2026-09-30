package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.settings.SettingsStore
import fi.palonkorpi.sidehome.settings.tempUnitAsState
import fi.palonkorpi.sidehome.settings.weatherEnabledAsState
import fi.palonkorpi.sidehome.settings.weatherPlaceAsState
import fi.palonkorpi.sidehome.weather.Forecast
import fi.palonkorpi.sidehome.weather.Place
import fi.palonkorpi.sidehome.weather.TempUnit
import fi.palonkorpi.sidehome.weather.conditionGlyph
import fi.palonkorpi.sidehome.weather.formatTemp
import fi.palonkorpi.sidehome.weather.matches
import fi.palonkorpi.sidehome.weather.rememberWeatherStore

/**
 * The home's **one glanceable line** (DECISIONS.md §2, SID-64) — weather, its first (and for now
 * only) tenant, now that SID-35 shipped the forecast. It renders **inline on the date line** rather
 * than as a line of its own: a separate third line pushed the last favourite off the 640px screen
 * once four favourites were present, so on-device it became a suffix to the date ("Thursday, 16 July
 * · 22°"). Still one glanceable line — it just shares the date's row (and its cascade slot).
 *
 * It is deliberately *less* than the Future pane's tier-1: no "12° · Cloudy", just the temperature
 * and a single thin-line glyph for the condition. This is the quiet page; the number is the whole
 * point and the glyph only says what kind of number it is.
 *
 * Three things it does **not** do, each load-bearing:
 *  - **It never fetches.** SPEC §4a says weather fetches on-view only. The home is not a view of the
 *    weather — it is a read of the cache. It reads [WeatherStore.forecast] (a `StateFlow<Forecast?>`
 *    seeded from disk at construction) via `collectAsState`, and calls *nothing* on the store. The
 *    only method that ever fetches is `refreshIfNeeded`, which the Future pane owns and this file
 *    never touches. A last-known reading is therefore on screen with no request made.
 *  - **No location → the line is absent, not empty.** When there is no place, no cached forecast, a
 *    cache for a different city, an unreadable temperature, or a reading older than
 *    [GLANCE_MAX_AGE_MS], this composable emits **zero nodes** — no placeholder, no "set location"
 *    (that lives on the Future pane). The line simply isn't in the layout, and the favourites close
 *    up under the date exactly as they did before SID-64.
 *  - **It carries no "as of" tell.** The home is an ambient surface and DECISIONS forbids muted or
 *    secondary copy on those, so a stale number here cannot caveat itself the way the Future pane's
 *    can. That is the whole reason [GLANCE_MAX_AGE_MS] exists — past it, a number that can't say how
 *    old it is misleads more than it helps, so it leaves.
 */

internal object GlanceLineMetrics {
    /** Space between the date text and the weather that now shares its line. */
    val LeadGap = 10.dp
    val GlyphSize = 16.dp
    val IconTextGap = 6.dp
}

/**
 * How old a cached reading may be before the home glance drops it. Deliberately much larger than the
 * Future pane's `CACHE_MAX_AGE_MS` (20 min, its on-view refetch cadence) and its `AS_OF_AFTER_MS`
 * (90 min, when it starts annotating its own age) — because those govern a surface that *can* show a
 * spinner-free "as of HH:MM" and keep the stale number honest. The home can't: it has no room for a
 * secondary line and DECISIONS bans muted copy on ambient surfaces. Six hours is the window in which
 * a last-known temperature is still roughly true at a glance; past it (say, this morning's reading
 * shown after dark) the bare number lies, and the tasteful move is to leave rather than to mislead.
 */
const val GLANCE_MAX_AGE_MS: Long = 6 * 60 * 60 * 1000L

/**
 * The temperature the glance shows, or **null when the line should be absent** — pure, so the whole
 * present/absent decision is unit-testable without a composable or an Android [SettingsStore].
 *
 * Absent when: no place; no cache or a cache for another city ([matches]); the reading isn't a finite
 * number (a missing feed column decodes to NaN — a dash belongs on the Future pane, never on the
 * quiet home); or the reading is older than [GLANCE_MAX_AGE_MS]. A negative age (the clock moved
 * backwards under a manual change) counts as "can't vouch for freshness" and hides too, matching how
 * `needsRefresh` treats the same case.
 */
fun glanceTemp(
    forecast: Forecast?,
    place: Place?,
    unit: TempUnit,
    nowMillis: Long,
): String? {
    if (place == null) return null
    if (!forecast.matches(place)) return null
    val f = forecast ?: return null
    if (!f.nowTempC.isFinite()) return null
    val age = nowMillis - f.fetchedAtMillis
    if (age < 0 || age >= GLANCE_MAX_AGE_MS) return null
    return formatTemp(f.nowTempC, unit)
}

/**
 * The weather itself, meant to sit **inline after the date** (`HomePage` wraps the two in one Row, so
 * this takes no cascade slot of its own — it rides the date's, index 1). Emits zero nodes when
 * [glanceTemp] says the line should be absent, so the date row is exactly its pre-SID-64 self when
 * there is no weather to show.
 */
@Composable
fun GlanceWeather(fg: Color, modifier: Modifier = Modifier) {
    // A pure cache read — the StateFlow only ever changes when the Future pane fetches (they share one
    // process-wide store, so that fetch reaches here), which cannot be triggered from this file. No
    // refresh method is called.
    val store = rememberWeatherStore()
    val forecast by store.forecast.collectAsState()

    // The place and the unit come from settings without threading a parameter through HomeScreen: a
    // SettingsStore is a thin SharedPreferences wrapper (process-wide by name), so one built here
    // reads the same prefs the settings screen and the Future pane write. No writes happen here.
    val context = LocalContext.current
    val settings = remember(context.applicationContext) { SettingsStore(context) }
    val place by settings.weatherPlaceAsState()
    val unit by settings.tempUnitAsState()
    // SID-181: weather off removes the line entirely (zero nodes, like the no-cache case) — and
    // gates the refresh below, so an off switch also means no fetches.
    val weatherOn by settings.weatherEnabledAsState()

    // Refresh on reveal (SID-64 follow-up): the home is a view of the weather too, so a stale cache
    // refetches when the home comes forward — the same on-view rule the Future pane follows, and still
    // no polling. GlanceWeather re-enters on every enterTick reveal (it lives inside HomePage's
    // key(enterTick) block), so this fires once per reveal; refreshIfNeeded no-ops while the cache is
    // fresh and single-flights against the pane through the shared store's mutex. Only fetches when
    // there's a place and the cache is actually stale for it.
    LaunchedEffect(place, weatherOn) {
        if (weatherOn) place?.let { store.refreshIfNeeded(it) }
    }

    // "Now" is read once per forecast, not ticked: the only thing time decides here is staleness, and
    // a per-minute recomposition of the home is exactly what the A22 can't afford. This subtree lives
    // inside the home's `key(enterTick)` block, so every fresh reveal of the home re-evaluates it —
    // which is when a cache going stale actually matters. Same idiom as FuturePane.
    val nowMillis = remember(forecast) { System.currentTimeMillis() }

    if (!weatherOn) return
    val temp = glanceTemp(forecast, place, unit, nowMillis) ?: return
    val code = forecast?.nowCode ?: return

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(GlanceLineMetrics.LeadGap))
        LineIcon(
            glyph = conditionGlyph(code),
            color = fg,
            size = GlanceLineMetrics.GlyphSize,
            strokeWidthUnits = 1.6f,
        )
        Spacer(Modifier.width(GlanceLineMetrics.IconTextGap))
        Text(
            text = temp,
            color = fg,
            fontSize = HomeMetrics.DateSp.sp,
            fontWeight = FontWeight.Normal,
            // A temperature never breaks across lines: squeezed, it once wrapped a digit per line.
            maxLines = 1,
            softWrap = false,
        )
    }
}
