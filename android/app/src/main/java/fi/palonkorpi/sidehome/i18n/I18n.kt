package fi.palonkorpi.sidehome.i18n

/**
 * The launcher's languages. English is the **source language**: every string in the codebase is
 * written in English where it is used, exactly as before, and other languages ride along beside it
 * through [tr]. Anything untranslated falls back to English rather than to a key or a blank.
 */
enum class Lang { EN, DE }

/**
 * Which language the launcher speaks — resolved from the device locale once per Activity creation
 * (see `MainActivity` / `SettingsActivity`), never guessed per string.
 *
 * Why this and not `strings.xml`: most of this launcher's copy is composed in **pure logic** that
 * is deliberately Android-free so it can be unit-tested on the JVM — `Weather.kt` builds sentences
 * like "rain starts ~16:00" in code, and a `Context`-bound resource lookup can't reach it without
 * giving up the purity that makes those tests possible (this project runs plain JVM tests, no
 * Robolectric — see `WeatherApi`'s header for the same argument). So the translation table lives
 * in code too: each string is written at its use site as `tr("english", "deutsch")`, greppable,
 * reviewable in one diff line, and testable by setting [lang].
 *
 * A change of device language recreates the Activities, which re-resolve this — the same lifecycle
 * that makes `values-de/` resources (used for the manifest-visible strings) switch over.
 *
 * Tests that assert non-English output must set [lang] and restore it (`@After`), because it is
 * process-global; everything existing asserts English, which is the default.
 */
object I18n {
    @Volatile var lang: Lang = Lang.EN

    /** Map an ISO 639 language code ("de", "de-AT", …) to a [Lang]; anything unknown is English. */
    fun fromLanguageTag(tag: String?): Lang =
        if (tag != null && tag.lowercase().startsWith("de")) Lang.DE else Lang.EN
}

/** The string in the current language: English source first, translations beside it. */
fun tr(en: String, de: String): String = when (I18n.lang) {
    Lang.EN -> en
    Lang.DE -> de
}
