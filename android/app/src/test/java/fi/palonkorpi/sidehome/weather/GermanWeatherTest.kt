package fi.palonkorpi.sidehome.weather

import fi.palonkorpi.sidehome.i18n.I18n
import fi.palonkorpi.sidehome.i18n.Lang
import fi.palonkorpi.sidehome.settings.ClockFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * A German sample of the weather copy. Not a mirror of [WeatherTest] — that suite owns the logic
 * and asserts the English source strings; this one only pins that the translated surface actually
 * switches, one line per mechanism (word table, weekday table, compass table, composed sentence).
 */
class GermanWeatherTest {

    @Before fun german() { I18n.lang = Lang.DE }
    @After fun english() { I18n.lang = Lang.EN }

    @Test
    fun `condition words speak german`() {
        assertEquals("Klar", conditionWord(0))
        assertEquals("Teils bewölkt", conditionWord(2))
        assertEquals("Gewitter, Hagel", conditionWord(99))
    }

    @Test
    fun `weekday and compass tables switch`() {
        // 1970-01-01 was a Thursday.
        assertEquals("Do", weekdayLabel(0L, 0))
        assertEquals("Mi", weekdayLabel(6 * 86_400L, 0))
        assertEquals("NO", compassLabel(45))
        assertEquals("O", compassLabel(90))
    }

    @Test
    fun `composed sentences read german`() {
        assertEquals("Stand 09:05", asOfText(9, 5, ClockFormat.H24))
        assertEquals("gefühlt -12°", feelsLine(nowC = -7.0, feelsC = -12.4, unit = TempUnit.CELSIUS))
        assertEquals("NW 32 km/h, Böen 58", windLine(speedKmh = 32.0, gustKmh = 58.0, dirDeg = 315))
        assertEquals("Regen in ~45 Min", nextHourLine(
            listOf(
                QuarterPoint(1000L + 900L, 0.0),
                QuarterPoint(1000L + 1800L, 0.0),
                QuarterPoint(1000L + 2700L, 0.8),
            ),
            nowEpochSec = 1000L,
        ))
    }

    @Test
    fun `the change line uses german grammar not word swaps`() {
        // "Regen ab ~16:00" — noun capitalised, "starts" becomes "ab", not a literal translation.
        val hours = (1..12).map { i ->
            HourPoint(epochSec = i * 3600L, tempC = 10.0, code = if (i >= 4) 63 else 2, precipProbPct = if (i >= 4) 80 else 0)
        }
        assertEquals(
            "Regen ab ~04:00",
            changeLine(nowEpochSec = 0L, nowCode = 2, hours = hours, utcOffsetSeconds = 0, format = ClockFormat.H24),
        )
    }
}
