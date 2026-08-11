package fi.palonkorpi.sidehome.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAppsTest {

    @Test
    fun `same pin hashes the same`() {
        assertEquals(hashHiddenPin("1234"), hashHiddenPin("1234"))
    }

    @Test
    fun `different pins hash differently`() {
        assertNotEquals(hashHiddenPin("1234"), hashHiddenPin("1235"))
    }

    @Test
    fun `hash is hex and never the pin itself`() {
        val h = hashHiddenPin("geheim")
        assertEquals(64, h.length) // SHA-256 → 32 bytes → 64 hex chars
        assertTrue(h.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals("geheim", h)
    }

    @Test
    fun `umlauts and spaces are legal pin characters`() {
        assertEquals(hashHiddenPin("Blümchen 5"), hashHiddenPin("Blümchen 5"))
        assertNotEquals(hashHiddenPin("Blümchen 5"), hashHiddenPin("Blumchen 5"))
    }
}
