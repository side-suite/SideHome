package fi.palonkorpi.sidehome.ui

import java.time.DayOfWeek
import java.time.Month
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The home date's step-down (the long day/month pair that squeezed the weather glance until its
 * number wrapped a digit per line). The measuring is layout; the order and the fallback are pure.
 */
class DateLineTest {

    private val labels = homeDateLabels(DayOfWeek.WEDNESDAY, 30, Month.SEPTEMBER, Locale.UK)

    @Test
    fun `labels step down month first, then weekday`() {
        assertEquals(3, labels.size)
        assertEquals("Wednesday, 30 September", labels[0])
        assertEquals(true, labels[1].startsWith("Wednesday, 30 Sep"))
        assertEquals(true, labels[2].startsWith("Wed, 30 Sep"))
    }

    @Test
    fun `picks the longest label that fits`() {
        assertEquals(labels[0], pickDateLabel(labels) { true })
        assertEquals(labels[1], pickDateLabel(labels) { it != labels[0] })
    }

    @Test
    fun `falls back to the shortest when nothing fits`() {
        assertEquals(labels.last(), pickDateLabel(labels) { false })
    }

    @Test
    fun `identical forms collapse`() {
        // May is "May" in both forms, so only the weekday step remains.
        val may = homeDateLabels(DayOfWeek.MONDAY, 4, Month.MAY, Locale.UK)
        assertEquals(listOf("Monday, 4 May", may.last()), may)
    }
}
