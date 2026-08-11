package fi.palonkorpi.sidehome.weather

import fi.palonkorpi.sidehome.i18n.tr
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The launcher's entire network stack (SID-35, SPEC.md §4a): two GETs against **Open-Meteo**, which
 * DECISIONS already picked — free, keyless, coords-only.
 *
 * Deliberately built on `HttpURLConnection` and `org.json` and nothing else. A launcher that makes
 * two requests when you look at one pane does not need a client library, a serialisation framework
 * or an annotation processor; every one of those is APK weight and cold-start cost on a Helio A22,
 * paid forever, to save about forty lines here.
 *
 * **Everything in this file is untestable on this project's setup** — `org.json` is an Android stub
 * that throws in JVM unit tests, and a socket isn't a unit test anyway. So it is written as thin as
 * it can be: pull columns out of the envelope, hand them to [buildForecast], and let `Weather.kt`
 * own every decision. The URL builders are the exception, and they *are* tested, because a typo in a
 * query parameter is silent — it returns 200 with a field quietly missing.
 *
 * Temperatures are always requested in Celsius (Open-Meteo's default): see `Weather.kt` on why the
 * unit is a display concern and must not touch the cache.
 */
object WeatherApi {

    /** Give up rather than hold a coroutine open on a dead network; the pane just keeps its cache. */
    private const val TIMEOUT_MS = 8_000

    /** How many days the week strip needs (SPEC §4a: a 7-cell strip). */
    private const val FORECAST_DAYS = 7

    /** Place search. Coords come back with the name; no key, no permission, no device location.
     *  The geocoder speaks the launcher's language, so a German launcher gets "Köln, Nordrhein-
     *  Westfalen" instead of "Cologne" — the stored place name is what the pane will show. */
    fun geocodeUrl(query: String, count: Int = 6): String =
        "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${URLEncoder.encode(query, "UTF-8")}" +
            "&count=$count&language=${tr("en", "de")}&format=json"

    /**
     * The forecast request. `timezone=auto` resolves the zone *from the coords* — so hours are the
     * place's own — and `timeformat=unixtime` then hands every time back as an epoch, which is the
     * one representation that needs no date parsing and no locale. `utc_offset_seconds` comes with
     * the response and turns those epochs back into local hours ([localHourOfDay]).
     */
    fun forecastUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            ",apparent_temperature" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,wind_speed_10m,wind_direction_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            ",sunrise,sunset,precipitation_probability_max,uv_index_max" +
            "&minutely_15=precipitation" +
            "&timezone=auto&timeformat=unixtime&forecast_days=$FORECAST_DAYS"

    /** Blocking. Call from [kotlinx.coroutines.Dispatchers.IO] — [WeatherStore] is the only caller. */
    fun fetchForecast(place: Place, fetchedAtMillis: Long): Forecast? {
        val body = httpGet(forecastUrl(place.lat, place.lon)) ?: return null
        return parseForecast(body, place, fetchedAtMillis)
    }

    /** Blocking, as above. Empty list = no hits *or* no network; the composer says the same thing. */
    fun search(query: String): List<GeoResult> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val body = httpGet(geocodeUrl(q)) ?: return emptyList()
        return parseGeocoding(body)
    }

    /** Envelope → columns → [buildForecast]. Null if the response isn't a forecast at all. */
    fun parseForecast(json: String, place: Place, fetchedAtMillis: Long): Forecast? = runCatching {
        val root = JSONObject(json)
        val current = root.optJSONObject("current") ?: return null
        val hourly = root.optJSONObject("hourly")
        val daily = root.optJSONObject("daily")
        val minutely = root.optJSONObject("minutely_15")
        buildForecast(
            place = place,
            utcOffsetSeconds = root.optInt("utc_offset_seconds", 0),
            nowTimeSec = current.optLong("time", 0L),
            nowTempC = current.optDouble("temperature_2m", 0.0),
            nowCode = current.optInt("weather_code", -1),
            hourlyTime = longs(hourly, "time"),
            hourlyTempC = doubles(hourly, "temperature_2m"),
            hourlyCode = ints(hourly, "weather_code"),
            hourlyPrecipProb = ints(hourly, "precipitation_probability"),
            dailyTime = longs(daily, "time"),
            dailyCode = ints(daily, "weather_code"),
            dailyMaxC = doubles(daily, "temperature_2m_max"),
            dailyMinC = doubles(daily, "temperature_2m_min"),
            fetchedAtMillis = fetchedAtMillis,
            // Wind (SID-184): NaN / -1 when the column is missing, and the wind line simply
            // doesn't render — never a dash pretending to be a measurement.
            nowWindKmh = current.optDouble("wind_speed_10m", Double.NaN),
            nowGustKmh = current.optDouble("wind_gusts_10m", Double.NaN),
            nowWindDirDeg = current.optInt("wind_direction_10m", -1),
            hourlyWindKmh = doubles(hourly, "wind_speed_10m"),
            hourlyWindDir = ints(hourly, "wind_direction_10m"),
            nowFeelsC = current.optDouble("apparent_temperature", Double.NaN),
            dailySunrise = longs(daily, "sunrise"),
            dailySunset = longs(daily, "sunset"),
            dailyPrecipProbMax = ints(daily, "precipitation_probability_max"),
            dailyUvMax = doubles(daily, "uv_index_max"),
            quarterTime = longs(minutely, "time"),
            quarterPrecip = doubles(minutely, "precipitation"),
        )
    }.getOrNull()

    /** Geocoding hits, in Open-Meteo's own relevance order. No `results` key = no hits. */
    fun parseGeocoding(json: String): List<GeoResult> = runCatching {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name")
            if (name.isNullOrBlank()) return@mapNotNull null
            GeoResult(
                place = Place(
                    name = name,
                    lat = o.optDouble("latitude", Double.NaN),
                    lon = o.optDouble("longitude", Double.NaN),
                ),
                region = regionLabel(
                    admin1 = o.optString("admin1", ""),
                    country = o.optString("country", ""),
                ),
            ).takeUnless { it.place.lat.isNaN() || it.place.lon.isNaN() }
        }
    }.getOrElse { emptyList() }

    // ---- plumbing -------------------------------------------------------------------------------

    /** GET → body, or null for *any* failure. Offline is not an error here; it's Tuesday. */
    private fun httpGet(spec: String): String? = runCatching {
        val conn = (URL(spec).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SideHome")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                conn.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun column(o: JSONObject?, key: String): JSONArray? = o?.optJSONArray(key)

    private fun longs(o: JSONObject?, key: String): List<Long> {
        val a = column(o, key) ?: return emptyList()
        return (0 until a.length()).map { a.optLong(it) }
    }

    private fun doubles(o: JSONObject?, key: String): List<Double> {
        val a = column(o, key) ?: return emptyList()
        return (0 until a.length()).map { a.optDouble(it) }
    }

    /** Open-Meteo sends `null` inside a column when it has no value; `optInt` reads that as 0. */
    private fun ints(o: JSONObject?, key: String): List<Int> {
        val a = column(o, key) ?: return emptyList()
        return (0 until a.length()).map { a.optInt(it) }
    }
}
