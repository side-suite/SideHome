package fi.palonkorpi.sidehome.weather

import fi.palonkorpi.sidehome.i18n.I18n
import fi.palonkorpi.sidehome.i18n.Lang
import fi.palonkorpi.sidehome.i18n.tr
import fi.palonkorpi.sidehome.settings.ClockFormat
import fi.palonkorpi.sidehome.settings.formatClock
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Future pane's weather model and every decision it makes — all of it pure (SPEC.md §4a,
 * SID-35). Nothing here touches Android, the network, or the clock: times arrive as epoch seconds
 * and a UTC offset, "now" arrives as a parameter. That is deliberate and load-bearing, because it is
 * the only part of this feature that can be unit-tested — the JSON, the socket, and the composables
 * cannot be, on this project's test setup (JVM unit tests, no Robolectric, and `org.json` is an
 * Android stub that throws). So the rule for this package is: the logic lives here, and the layers
 * around it stay thin enough to audit by reading.
 *
 * Everything is stored and cached in **Celsius**, always, and converted at the moment of display.
 * The unit is a rendering preference; making it part of the fetch would mean a cache that a settings
 * toggle silently invalidates, and a 20-minute-old forecast thrown away for a change of glyph.
 */

/** A place the user typed and picked. Coords-only — no location permission is ever requested. */
data class Place(val name: String, val lat: Double, val lon: Double)

/** A geocoding hit: the [place] we'd store, plus the "Uusimaa, Finland" line that disambiguates it. */
data class GeoResult(val place: Place, val region: String)

/** How temperatures render. Persisted in [fi.palonkorpi.sidehome.settings.SettingsStore]. */
enum class TempUnit { CELSIUS, FAHRENHEIT }

/** One hourly sample. [precipProbPct] is 0..100; Open-Meteo sends null for it, which decodes to 0.
 *  Wind (SID-184): NaN / -1 = the feed didn't say, and the hourly strip omits its wind line. */
data class HourPoint(
    val epochSec: Long,
    val tempC: Double,
    val code: Int,
    val precipProbPct: Int,
    val windKmh: Double = Double.NaN,
    val windDirDeg: Int = -1,
)

/** One day. [epochSec] is that day's local midnight. The v1.1 fields ([sunriseSec] onward) default
 *  to absence — 0 / -1 / NaN — and every line that would say them simply doesn't. */
data class DayPoint(
    val epochSec: Long,
    val code: Int,
    val highC: Double,
    val lowC: Double,
    val sunriseSec: Long = 0L,
    val sunsetSec: Long = 0L,
    val precipProbMaxPct: Int = -1,
    val uvMax: Double = Double.NaN,
)

/** One 15-minute precipitation sample — the "next hour" line's raw material. */
data class QuarterPoint(val epochSec: Long, val precipMm: Double)

/**
 * A whole forecast, exactly as cached. It carries its own [placeName] and coords so a cache written
 * for Helsinki can never be rendered under the heading "Berlin" — see [matches].
 *
 * [utcOffsetSeconds] is the *place's* offset (Open-Meteo's `timezone=auto`), which is what makes
 * "rain starts ~16:00" mean 16:00 there. The "as of" stamp is the one time we deliberately use the
 * phone's own zone instead: it says when *you* last had a signal.
 */
data class Forecast(
    val placeName: String,
    val lat: Double,
    val lon: Double,
    val utcOffsetSeconds: Int,
    val nowTimeSec: Long,
    val nowTempC: Double,
    val nowCode: Int,
    val hours: List<HourPoint>,
    val days: List<DayPoint>,
    val fetchedAtMillis: Long,
    // Current wind (SID-184): sustained speed and gusts in km/h, direction in meteorological
    // degrees (whence it blows; 0 = from the north). NaN / -1 = the feed didn't say — a cache from
    // before wind existed decodes to null anyway (version bump), so these defaults mostly serve
    // tests and ragged feeds.
    val nowWindKmh: Double = Double.NaN,
    val nowGustKmh: Double = Double.NaN,
    val nowWindDirDeg: Int = -1,
    /** Apparent temperature (wind chill / humidity folded in); NaN = the feed didn't say. */
    val nowFeelsC: Double = Double.NaN,
    /** The next few hours at 15-minute grain, precipitation only — trimmed to a short horizon at
     *  build time so the cache never carries a week of quarter-hours. */
    val quarters: List<QuarterPoint> = emptyList(),
) {
    /** Today's high/low, if the feed gave us a today at all. */
    val today: DayPoint? get() = days.firstOrNull()

    /** Tomorrow's, likewise. */
    val tomorrow: DayPoint? get() = days.getOrNull(1)
}

// ---- staleness ---------------------------------------------------------------------------------

/** How old a cached forecast may get before a *viewed* pane refetches it (SPEC §4a: "~20 min"). */
const val CACHE_MAX_AGE_MS: Long = 20 * 60 * 1000L

/**
 * How old it must get before the pane admits it, with a quiet "as of HH:MM". Deliberately much
 * larger than [CACHE_MAX_AGE_MS]: every refetch spends a moment being 20 minutes old and about to
 * be replaced, and stamping that instant would flash "as of" onto a pane that is fine. The stamp is
 * for the case that actually matters — no network, data from this morning — so it appears only once
 * the age is something a person would want to know about.
 */
const val AS_OF_AFTER_MS: Long = 90 * 60 * 1000L

/** Coordinates this close are the same place; below it, float noise from a prefs round-trip. */
private const val COORD_EPSILON = 0.0005

/** Is this cached forecast for [place]? A cache for another city must never render under its name. */
fun Forecast?.matches(place: Place?): Boolean {
    if (this == null || place == null) return false
    return abs(lat - place.lat) < COORD_EPSILON && abs(lon - place.lon) < COORD_EPSILON
}

/**
 * Should a pane that has just come into view fetch? True when there is no cache, when the cache is
 * for somewhere else, or when it is older than [CACHE_MAX_AGE_MS]. A clock that has gone *backwards*
 * (a manual time change) also counts as stale rather than as freshness that lasts until it catches
 * up.
 */
fun needsRefresh(cached: Forecast?, place: Place?, nowMillis: Long): Boolean {
    if (place == null) return false
    if (!cached.matches(place)) return true
    val age = nowMillis - cached!!.fetchedAtMillis
    return age >= CACHE_MAX_AGE_MS || age < 0
}

/** Whether to show the quiet "as of" stamp — see [AS_OF_AFTER_MS]. */
fun showAsOf(fetchedAtMillis: Long, nowMillis: Long): Boolean =
    nowMillis - fetchedAtMillis >= AS_OF_AFTER_MS

/** The stamp itself. Never "error", never a dialog — just when this was true (SPEC §4a). */
fun asOfText(hour: Int, minute: Int, format: ClockFormat): String =
    "${tr("as of", "Stand")} ${formatClock(hour, minute, format)}"

// ---- time ---------------------------------------------------------------------------------------

/** Hour-of-day (0..23) at [utcOffsetSeconds] for an instant given in epoch seconds. */
fun localHourOfDay(epochSec: Long, utcOffsetSeconds: Int): Int =
    (epochSec + utcOffsetSeconds).floorDiv(3600L).mod(24L).toInt()

/** Minute-of-hour (0..59), same terms. */
fun localMinute(epochSec: Long, utcOffsetSeconds: Int): Int =
    (epochSec + utcOffsetSeconds).floorDiv(60L).mod(60L).toInt()

/** Days since the epoch, at [utcOffsetSeconds]. 1970-01-01 = 0. */
fun localEpochDay(epochSec: Long, utcOffsetSeconds: Int): Long =
    (epochSec + utcOffsetSeconds).floorDiv(86_400L)

private val WEEKDAYS_EN = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
private val WEEKDAYS_DE = listOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")
private val WEEKDAYS: List<String> get() = if (I18n.lang == Lang.DE) WEEKDAYS_DE else WEEKDAYS_EN

/**
 * The week strip's two-letter day label. 1970-01-01 was a Thursday, which is where the `+4` comes
 * from; the strip starts at today and runs forward, so no locale's first-day-of-week is involved.
 */
fun weekdayLabel(epochSec: Long, utcOffsetSeconds: Int): String =
    WEEKDAYS[(localEpochDay(epochSec, utcOffsetSeconds) + 4).mod(7L).toInt()]

/**
 * An hour said the way a person would. Noon and midnight get their names — "clearing by noon" is
 * the line SPEC §4a actually asks for — and everything else defers to the user's own clock setting,
 * so the pane never contradicts the home's 12h/24h choice.
 */
fun friendlyHour(epochSec: Long, utcOffsetSeconds: Int, format: ClockFormat): String {
    val h = localHourOfDay(epochSec, utcOffsetSeconds)
    val m = localMinute(epochSec, utcOffsetSeconds)
    if (m == 0 && h == 12) return tr("noon", "Mittag")
    if (m == 0 && h == 0) return tr("midnight", "Mitternacht")
    return formatClock(h, m, format)
}

/**
 * The hourly strip's cell label — the tightest an hour can be said and still be read. Six cells share
 * a 480px row, so [friendlyHour]'s "4:00 pm" is four times too wide; this is the hour alone, honouring
 * the clock setting: "15" in 24h, "3p" / "12a" in 12h (a single a/p suffix, no minutes, no space).
 */
fun shortHourLabel(epochSec: Long, utcOffsetSeconds: Int, format: ClockFormat): String {
    val h = localHourOfDay(epochSec, utcOffsetSeconds)
    if (format == ClockFormat.H24) return h.toString()
    val suffix = if (h < 12) "a" else "p"
    val twelve = if (h % 12 == 0) 12 else h % 12
    return "$twelve$suffix"
}

/**
 * The next [count] hourly samples strictly after [nowEpochSec], in time order. Strictly after, so the
 * strip looks *ahead* rather than repeating the Now tier's current hour; fewer than [count] near the
 * end of the feed is returned as-is (the strip renders what it has rather than padding to a fixed
 * width with holes).
 */
fun upcomingHours(hours: List<HourPoint>, nowEpochSec: Long, count: Int): List<HourPoint> =
    hours.asSequence()
        .filter { it.epochSec > nowEpochSec }
        .sortedBy { it.epochSec }
        .take(count)
        .toList()

// ---- temperature --------------------------------------------------------------------------------

/** °C → °F. */
fun toFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

/**
 * A temperature as the pane says it: whole degrees and a ring, no unit letter. The screen is 480px
 * wide and every number on it is the same unit — a "°C" on each would be six characters of saying
 * so. [roundToInt] rounds half-up and lands a small negative on `0`, never `-0`.
 *
 * NaN is a real input, not a paranoid one: a missing column in the feed decodes to `Double.NaN`, and
 * [roundToInt] *throws* on NaN. A dash on the pane; never an exception inside the launcher's draw.
 */
fun formatTemp(celsius: Double, unit: TempUnit): String {
    if (!celsius.isFinite()) return "—"
    val v = if (unit == TempUnit.FAHRENHEIT) toFahrenheit(celsius) else celsius
    return "${v.roundToInt()}°"
}

// ---- wind (SID-184) -----------------------------------------------------------------------------

/**
 * How far gusts must stand above the sustained speed before the line mentions them. In a steady
 * blow the gust figure is the same number said twice; it earns its place when it is the number
 * that knocks your hat off — the Wellington case this feature exists for.
 */
const val GUST_MENTION_DELTA_KMH = 10.0

private val COMPASS_EN = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
private val COMPASS_DE = listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
private val COMPASS: List<String> get() = if (I18n.lang == Lang.DE) COMPASS_DE else COMPASS_EN

/**
 * An 8-wind compass label for a meteorological direction (whence the wind blows), or null when the
 * feed didn't say. "NW 24 km/h" means what a forecast has always meant by it: wind *from* the
 * north-west.
 */
fun compassLabel(dirDeg: Int): String? {
    if (dirDeg < 0) return null
    return COMPASS[(((dirDeg % 360) + 22.5) / 45.0).toInt() % 8]
}

/**
 * The Now tier's wind line — "NW 24 km/h", "NW 24 km/h, gusts 45" — or null when the feed carried
 * no wind (a pane that says "— km/h" is worse than one that says nothing). Speed is always said;
 * direction when known; gusts only when they'd surprise you ([GUST_MENTION_DELTA_KMH]). km/h and
 * never a unit toggle: wind is one number on one line, and Open-Meteo's default unit is the one
 * every forecast in a km/h country already speaks.
 */
fun windLine(speedKmh: Double, gustKmh: Double, dirDeg: Int): String? {
    if (!speedKmh.isFinite()) return null
    val dir = compassLabel(dirDeg)?.let { "$it " } ?: ""
    val gusts =
        if (gustKmh.isFinite() && gustKmh >= speedKmh + GUST_MENTION_DELTA_KMH) {
            ", ${tr("gusts", "Böen")} ${gustKmh.roundToInt()}"
        } else {
            ""
        }
    return "$dir${speedKmh.roundToInt()} km/h$gusts"
}

/**
 * An hourly cell's wind, at the tightest it can be said: compass letter(s) and the speed, no unit
 * — "NW 18". Six cells share a 480px row and every number in the strip is km/h, so the unit is
 * said once by the Now tier. Null (no line at all) when the feed carried no hourly wind.
 */
fun windCellLabel(windKmh: Double, dirDeg: Int): String? {
    if (!windKmh.isFinite()) return null
    val speed = windKmh.roundToInt().toString()
    return compassLabel(dirDeg)?.let { "$it $speed" } ?: speed
}

// ---- feels-like, sun, rain odds, UV (v1.1's second weather pass) --------------------------------

/** How far apart the felt and measured temperatures must be before the line exists — the same
 *  restraint rule as gusts: say it when it would surprise you, stay quiet when it's the same
 *  number twice. */
const val FEELS_MENTION_DELTA_C = 2.0

/** "feels −12°", or null when the feed didn't say / it wouldn't surprise you. */
fun feelsLine(nowC: Double, feelsC: Double, unit: TempUnit): String? {
    if (!feelsC.isFinite() || !nowC.isFinite()) return null
    if (abs(feelsC - nowC) < FEELS_MENTION_DELTA_C) return null
    return "${tr("feels", "gefühlt")} ${formatTemp(feelsC, unit)}"
}

/** "↑ 5:12   ↓ 21:48" in the user's clock, or null when the feed carried no sun times. */
fun sunLine(sunriseSec: Long, sunsetSec: Long, utcOffsetSeconds: Int, format: ClockFormat): String? {
    if (sunriseSec <= 0L || sunsetSec <= 0L) return null
    val rise = formatClock(localHourOfDay(sunriseSec, utcOffsetSeconds), localMinute(sunriseSec, utcOffsetSeconds), format)
    val set = formatClock(localHourOfDay(sunsetSec, utcOffsetSeconds), localMinute(sunsetSec, utcOffsetSeconds), format)
    return "↑ $rise   ↓ $set"
}

/** Below this, a rain chance is background noise the week strip shouldn't say. */
const val WEEK_PROB_THRESHOLD = 30

/** A week cell's rain chance — "60%" — or null when it's low or unknown. The *strip* holds this
 *  threshold; the expanded day says the number whenever it exists. */
fun weekProbLabel(probPct: Int): String? =
    if (probPct >= WEEK_PROB_THRESHOLD) "$probPct%" else null

/** UV worth mentioning starts at "moderate" (WHO scale). */
const val UV_MENTION = 3.0

/** "UV 6", or null when unknown or too low to matter. */
fun uvLabel(uvMax: Double): String? =
    if (uvMax.isFinite() && uvMax >= UV_MENTION) "UV ${uvMax.roundToInt()}" else null

/** The expanded day's small facts line — "rain 60% · UV 6" — or null when there is nothing to say.
 *  Unlike the strip, the detail says any known rain chance: opening the day is asking. */
fun dayFactsLine(day: DayPoint): String? {
    val parts = listOfNotNull(
        day.precipProbMaxPct.takeIf { it >= 0 }?.let { "${tr("rain", "Regen")} $it%" },
        uvLabel(day.uvMax),
    )
    return parts.joinToString(" · ").ifEmpty { null }
}

// ---- the next hour, at quarter grain ------------------------------------------------------------

/** A quarter-hour with at least this much precipitation is "raining". */
const val QUARTER_WET_MM = 0.1

/** How far past "now" the quarters survive the build — enough for the next-hour line with slack
 *  for a 20-minute cache, not a week of noise. */
const val QUARTER_HORIZON_SEC = 4 * 3600L

/**
 * The line above the hourly tiers when the next 60 minutes hold news — "rain in ~30 min",
 * "rain easing in ~15 min", "rain for the next hour" — or null when they don't (dry and staying
 * dry). Quarter-grain is what the hour-grain change-line can't say; when this line exists the
 * change-line stands down (the pane says one thing about the near future, not two).
 */
fun nextHourLine(quarters: List<QuarterPoint>, nowEpochSec: Long): String? {
    val ahead = quarters
        .filter { it.epochSec > nowEpochSec && it.epochSec <= nowEpochSec + 3600L }
        .sortedBy { it.epochSec }
    if (ahead.isEmpty()) return null
    fun minutes(q: QuarterPoint) = ((q.epochSec - nowEpochSec) / 60L).toInt()

    val wetNow = ahead.first().precipMm >= QUARTER_WET_MM
    if (!wetNow) {
        val onset = ahead.firstOrNull { it.precipMm >= QUARTER_WET_MM } ?: return null
        return tr("rain in ~${minutes(onset)} min", "Regen in ~${minutes(onset)} Min")
    }
    // Raining: find the quarter it stops and *stays* stopped (a single dry quarter inside a shower
    // is not the rain ending — the change-line's own rule, at this grain).
    val stops = ahead.indexOfFirst { it.precipMm < QUARTER_WET_MM }
    if (stops >= 0 && ahead.drop(stops).all { it.precipMm < QUARTER_WET_MM }) {
        return tr("rain easing in ~${minutes(ahead[stops])} min", "Regen hört in ~${minutes(ahead[stops])} Min auf")
    }
    return tr("rain for the next hour", "Regen die nächste Stunde")
}

// ---- the expanded day ---------------------------------------------------------------------------

/**
 * The expanded day's mini strip: that day's waking hours at three-hour steps (6, 9, … 21 local).
 * Night hours are noise in a plan; six cells is the same rhythm the Now tier's hourly strip has.
 */
fun dayStrip(hours: List<HourPoint>, dayEpochSec: Long, utcOffsetSeconds: Int): List<HourPoint> =
    hours.asSequence()
        .filter { it.epochSec >= dayEpochSec && it.epochSec < dayEpochSec + 86_400L }
        .filter { localHourOfDay(it.epochSec, utcOffsetSeconds).let { h -> h in 6..21 && h % 3 == 0 } }
        .sortedBy { it.epochSec }
        .toList()

// ---- conditions ---------------------------------------------------------------------------------

/**
 * WMO weather-interpretation codes, the vocabulary Open-Meteo speaks. Grouped rather than listed
 * one-by-one because every question this pane asks is about the *family* ("is it wet?", "is it
 * clear?"), and a code we've never seen should answer those sensibly instead of falling off the end
 * of a `when`.
 */
private val DRIZZLE = setOf(51, 53, 55, 56, 57)
private val RAIN = setOf(61, 63, 65, 66, 67, 80, 81, 82)
private val SNOW = setOf(71, 73, 75, 77, 85, 86)
private val STORM = setOf(95, 96, 99)

/** Is something falling out of the sky? */
fun isWet(code: Int): Boolean = code in DRIZZLE || code in RAIN || code in SNOW || code in STORM

/** Clear or nearly so — the state "clearing by noon" is about arriving at. */
fun isClear(code: Int): Boolean = code == 0 || code == 1

/** Cloud, overcast or fog: dry, but the thing clearing would clear. */
fun isDull(code: Int): Boolean = code == 2 || code == 3 || code == 45 || code == 48

/** The word the Now line uses. Unknown codes get an em dash rather than a lie. */
fun conditionWord(code: Int): String = when (code) {
    0 -> tr("Clear", "Klar")
    1 -> tr("Mostly clear", "Fast klar")
    2 -> tr("Partly cloudy", "Teils bewölkt")
    3 -> tr("Cloudy", "Bewölkt")
    45, 48 -> tr("Fog", "Nebel")
    51, 53 -> tr("Drizzle", "Nieselregen")
    55 -> tr("Heavy drizzle", "Starker Niesel")
    56, 57 -> tr("Freezing drizzle", "Gefrierender Niesel")
    61 -> tr("Light rain", "Leichter Regen")
    63 -> tr("Rain", "Regen")
    65 -> tr("Heavy rain", "Starkregen")
    66, 67 -> tr("Freezing rain", "Gefrierender Regen")
    71 -> tr("Light snow", "Leichter Schnee")
    73, 77 -> tr("Snow", "Schnee")
    75 -> tr("Heavy snow", "Starker Schneefall")
    80, 81 -> tr("Showers", "Schauer")
    82 -> tr("Heavy showers", "Starke Schauer")
    85 -> tr("Snow showers", "Schneeschauer")
    86 -> tr("Heavy snow showers", "Starke Schneeschauer")
    95 -> tr("Thunderstorm", "Gewitter")
    96, 99 -> tr("Thunderstorm, hail", "Gewitter, Hagel")
    else -> "—"
}

/** The noun the change-line uses for what's falling: "rain starts ~16:00", "snow starts ~16:00".
 *  German capitalises its nouns, so the pairs differ in case as well as language. */
fun precipNoun(code: Int): String = when {
    code in SNOW -> tr("snow", "Schnee")
    code in STORM -> tr("storms", "Gewitter")
    code in DRIZZLE -> tr("drizzle", "Niesel")
    else -> tr("rain", "Regen")
}

/**
 * Which glyph a condition wears — keys into `ui/Glyphs.kt`. Every family now has its **own shape**
 * (rain and storm are real cloud glyphs added to the set), so a condition reads by silhouette alone
 * and nothing here relies on colour: the old accent-tinted-cloud stand-in for rain is gone, and with
 * it the highlight that made half the cells glow. Drizzle draws as rain — at glyph size the two
 * silhouettes are the same, and the word tier already carries the difference.
 */
fun conditionGlyph(code: Int): String = when {
    code in SNOW -> "snow"
    code in STORM -> "storm"
    code in DRIZZLE || code in RAIN -> "rain"
    isClear(code) -> "sun"
    else -> "cloud"
}

// ---- the one smart change-line -------------------------------------------------------------------

/** How likely rain must be before we promise it. Below this, "rain starts ~16:00" is a rumour. */
private const val RAIN_PROB_THRESHOLD = 55

/** How far ahead the change-line looks. Past this, "later today" stops being a thing you'd act on. */
private const val HORIZON_HOURS = 12

/**
 * The single line under Now — "rain starts ~16:00", "clearing by noon" — or **null when there is
 * nothing to say** (SPEC §4a). The null case is the whole point: a calm pane earns its calm by
 * leaving the space empty, not by filling it with "conditions remain cloudy".
 *
 * Three rules, in the order a person would care:
 *  1. dry now, wet within the horizon → when it starts;
 *  2. wet now, dry within the horizon → when it stops;
 *  3. dull now, clear within the horizon → when it clears.
 *
 * Rules 2 and 3 require the new state to *hold* for the following sample as well. A single dry hour
 * inside an afternoon of rain is not the rain stopping, and announcing it at 14:00 only to be raining
 * again at 16:00 is worse than having said nothing — which is the bar this line has to clear.
 */
fun changeLine(
    nowEpochSec: Long,
    nowCode: Int,
    hours: List<HourPoint>,
    utcOffsetSeconds: Int,
    format: ClockFormat,
): String? {
    val ahead = hours
        .filter { it.epochSec > nowEpochSec && it.epochSec <= nowEpochSec + HORIZON_HOURS * 3600L }
        .sortedBy { it.epochSec }
    if (ahead.isEmpty()) return null

    if (!isWet(nowCode)) {
        val onset = ahead.firstOrNull { isWet(it.code) && it.precipProbPct >= RAIN_PROB_THRESHOLD }
        if (onset != null) {
            val h = friendlyHour(onset.epochSec, utcOffsetSeconds, format)
            return tr("${precipNoun(onset.code)} starts ~$h", "${precipNoun(onset.code)} ab ~$h")
        }
    } else {
        val stops = ahead.indexOfFirst { !isWet(it.code) }
        if (stops >= 0 && holds(ahead, stops) { !isWet(it.code) }) {
            val h = friendlyHour(ahead[stops].epochSec, utcOffsetSeconds, format)
            return tr("${precipNoun(nowCode)} stops ~$h", "${precipNoun(nowCode)} endet ~$h")
        }
    }

    if (isDull(nowCode)) {
        val clears = ahead.indexOfFirst { isClear(it.code) }
        if (clears >= 0 && holds(ahead, clears) { isClear(it.code) }) {
            val h = friendlyHour(ahead[clears].epochSec, utcOffsetSeconds, format)
            return tr("clearing by $h", "klart auf bis ~$h")
        }
    }
    return null
}

/** Does the state at [index] still hold at the next sample? (The last sample holds by default.) */
private inline fun holds(ahead: List<HourPoint>, index: Int, predicate: (HourPoint) -> Boolean): Boolean {
    val next = ahead.getOrNull(index + 1) ?: return true
    return predicate(next)
}

// ---- assembling a forecast from decoded feed columns ---------------------------------------------

/**
 * Build a [Forecast] from Open-Meteo's column-per-field arrays. Pure, and separated from the JSON
 * on purpose: this is where the zipping, the truncation and the ordering can go wrong, and this is
 * the half of parsing that a unit test can actually reach. [WeatherApi] only pulls arrays out of the
 * envelope and hands them here.
 *
 * Ragged columns are truncated to the shortest rather than rejected — a feed that sends 24 temps and
 * 23 codes should cost us one hour, not the whole pane.
 */
fun buildForecast(
    place: Place,
    utcOffsetSeconds: Int,
    nowTimeSec: Long,
    nowTempC: Double,
    nowCode: Int,
    hourlyTime: List<Long>,
    hourlyTempC: List<Double>,
    hourlyCode: List<Int>,
    hourlyPrecipProb: List<Int>,
    dailyTime: List<Long>,
    dailyCode: List<Int>,
    dailyMaxC: List<Double>,
    dailyMinC: List<Double>,
    fetchedAtMillis: Long,
    nowWindKmh: Double = Double.NaN,
    nowGustKmh: Double = Double.NaN,
    nowWindDirDeg: Int = -1,
    hourlyWindKmh: List<Double> = emptyList(),
    hourlyWindDir: List<Int> = emptyList(),
    nowFeelsC: Double = Double.NaN,
    dailySunrise: List<Long> = emptyList(),
    dailySunset: List<Long> = emptyList(),
    dailyPrecipProbMax: List<Int> = emptyList(),
    dailyUvMax: List<Double> = emptyList(),
    quarterTime: List<Long> = emptyList(),
    quarterPrecip: List<Double> = emptyList(),
): Forecast {
    val hn = minOf(hourlyTime.size, hourlyTempC.size, hourlyCode.size, hourlyPrecipProb.size)
    val dn = minOf(dailyTime.size, dailyCode.size, dailyMaxC.size, dailyMinC.size)
    return Forecast(
        placeName = place.name,
        lat = place.lat,
        lon = place.lon,
        utcOffsetSeconds = utcOffsetSeconds,
        nowTimeSec = nowTimeSec,
        nowTempC = nowTempC,
        nowCode = nowCode,
        hours = (0 until hn).map {
            HourPoint(
                hourlyTime[it], hourlyTempC[it], hourlyCode[it], hourlyPrecipProb[it],
                // Wind columns degrade to absence, not truncation: a feed without them should
                // cost the strip its wind line, never its hours.
                windKmh = hourlyWindKmh.getOrElse(it) { Double.NaN },
                windDirDeg = hourlyWindDir.getOrElse(it) { -1 },
            )
        },
        days = (0 until dn).map {
            DayPoint(
                dailyTime[it], dailyCode[it], dailyMaxC[it], dailyMinC[it],
                // The v1.1 columns degrade to absence, never truncate the week.
                sunriseSec = dailySunrise.getOrElse(it) { 0L },
                sunsetSec = dailySunset.getOrElse(it) { 0L },
                precipProbMaxPct = dailyPrecipProbMax.getOrElse(it) { -1 },
                uvMax = dailyUvMax.getOrElse(it) { Double.NaN },
            )
        },
        fetchedAtMillis = fetchedAtMillis,
        nowWindKmh = nowWindKmh,
        nowGustKmh = nowGustKmh,
        nowWindDirDeg = nowWindDirDeg,
        nowFeelsC = nowFeelsC,
        // The feed sends quarter-hours for the whole forecast span; only the near ones are the
        // next-hour line's business, and only they earn a place in the cache.
        quarters = (0 until minOf(quarterTime.size, quarterPrecip.size))
            .map { QuarterPoint(quarterTime[it], quarterPrecip[it]) }
            .filter { it.epochSec > nowTimeSec - 900L && it.epochSec <= nowTimeSec + QUARTER_HORIZON_SEC },
    )
}

/** The "Uusimaa, Finland" line under a geocoding hit. Either half may be missing; neither is fatal. */
fun regionLabel(admin1: String?, country: String?): String =
    listOfNotNull(admin1?.takeIf { it.isNotBlank() }, country?.takeIf { it.isNotBlank() })
        .joinToString(", ")
