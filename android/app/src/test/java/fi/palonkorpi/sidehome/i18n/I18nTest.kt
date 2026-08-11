package fi.palonkorpi.sidehome.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class I18nTest {

    @After
    fun resetLang() {
        // I18n.lang is process-global; a test that leaves DE behind would fail every English
        // assertion that runs after it.
        I18n.lang = Lang.EN
    }

    @Test
    fun `english is the default and the fallback`() {
        assertEquals(Lang.EN, I18n.fromLanguageTag(null))
        assertEquals(Lang.EN, I18n.fromLanguageTag("fi"))
        assertEquals(Lang.EN, I18n.fromLanguageTag("en-GB"))
        assertEquals("Cancel", tr("Cancel", "Abbrechen"))
    }

    @Test
    fun `german locales resolve to german`() {
        assertEquals(Lang.DE, I18n.fromLanguageTag("de"))
        assertEquals(Lang.DE, I18n.fromLanguageTag("de-AT"))
        assertEquals(Lang.DE, I18n.fromLanguageTag("DE"))
    }

    @Test
    fun `tr answers in the active language`() {
        I18n.lang = Lang.DE
        assertEquals("Abbrechen", tr("Cancel", "Abbrechen"))
        I18n.lang = Lang.EN
        assertEquals("Cancel", tr("Cancel", "Abbrechen"))
    }
}
