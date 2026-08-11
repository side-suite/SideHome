package fi.palonkorpi.sidehome.apps

import fi.palonkorpi.sidehome.i18n.tr

/**
 * A category an app can belong to, as it flows through resolution, grouping and the UI (SID-61).
 *
 * Identity is [id] — compare on it, never on [label]/[glyph], which are display-only and, for a user
 * bucket, editable. Built-ins ([builtIn] = true) ship with the app and carry the old enum name as
 * their id, so per-app overrides persisted before SID-61 still resolve; user buckets (SID-61/62)
 * carry a `"user:N"` id.
 *
 * The set of categories is no longer fixed at compile time — that is the whole point of this type
 * replacing the old `AppCategory` enum, since a user-defined bucket cannot be an enum constant. The
 * *built-in* set still is fixed, and stays an enum ([BuiltInCategory]); crucially the automatic
 * resolver layers are typed to that enum, so **they can only ever emit a built-in** — a user bucket
 * is reachable only through an explicit override, by construction rather than by convention.
 */
data class Category(
    val id: String,
    val label: String,
    val glyph: String,
    val builtIn: Boolean,
) {
    /** The honest-fallback bucket, singled out by [FolderNaming] and [bucketApps]. */
    val isOther: Boolean get() = id == BuiltInCategory.OTHER.name
}

/**
 * The broad-few *built-in* app buckets used by grouped mode (SID-11) and, as a side effect, to
 * sharpen glyph inference. Kept deliberately small (calmer than precise-and-sparse on ~40 apps) —
 * each carries a representative line glyph for its bubble. [OTHER] is the honest fallback: an app
 * lands here rather than in a wrong bucket.
 *
 * Names are seeded from the `keywordGlyph` clusters and meant to be curated against the real
 * installed set on-device; treat them as tunable, not final.
 *
 * This is the fixed built-in set only — the app's category *identity* is [Category], which also
 * covers user-defined buckets (SID-61). The auto-resolver layers below are typed to this enum so a
 * user bucket can never be auto-assigned; it is only ever reached through an override.
 */
enum class BuiltInCategory(private val labelEn: String, private val labelDe: String, val glyph: String) {
    COMMUNICATION("Communication", "Kommunikation", "chat"),
    MEDIA("Media", "Medien", "music"),
    WEB("Web & Maps", "Web & Karten", "globe"),
    PRODUCTIVITY("Productivity", "Produktivität", "note"),
    MONEY("Money", "Finanzen", "card"),
    HEALTH("Health & Fitness", "Gesundheit & Fitness", "heart"),
    UTILITIES("Utilities", "Werkzeuge", "sliders"),
    OTHER("Other", "Sonstiges", "app");

    /** Display-only (identity is the enum [name]); resolved per access so a language switch shows. */
    val label: String get() = tr(labelEn, labelDe)

    /** This built-in as a flowing [Category]. [Category.id] is the enum name — stable and persisted. */
    val category: Category get() = Category(name, label, glyph, builtIn = true)

    companion object {
        /** Built-in with this id, or null if unknown/stale (the seam a user id resolves through). */
        fun byId(id: String): BuiltInCategory? = entries.firstOrNull { it.name == id }
    }
}

/**
 * One keyword rule: a set of needles that, if any occurs in the search haystack
 * ("label package", lowercased), pins both a fine-grained [glyph] and a broad
 * [category]. Single source of truth so glyph and category never drift apart —
 * order matters, first match wins (mirrors the historical `keywordGlyph` order).
 */
data class KeywordRule(val glyph: String, val category: BuiltInCategory, val needles: List<String>) {
    fun matches(hay: String): Boolean = needles.any { hay.contains(it) }
}

/**
 * The layered, highest-confidence-wins category resolver behind grouped mode:
 *
 *   user override → intent categories → bundled F-Droid map → keyword heuristic → Other
 *
 * This object is pure (no Android, no I/O) so the priority logic and the keyword
 * table are unit-testable; [AppRepository] feeds it the per-layer inputs it has
 * gathered from `PackageManager` and the persisted stores.
 */
object CategoryEngine {

    private val KEYWORD_RULES: List<KeywordRule> = listOf(
        KeywordRule("phone", BuiltInCategory.COMMUNICATION, listOf("dialer", "phone", "contact")),
        KeywordRule("chat", BuiltInCategory.COMMUNICATION, listOf("message", "sms", "mms", "chat", "whatsapp", "signal", "telegram")),
        KeywordRule("camera", BuiltInCategory.MEDIA, listOf("camera")),
        KeywordRule("calendar", BuiltInCategory.PRODUCTIVITY, listOf("calendar", "agenda")),
        KeywordRule("clock", BuiltInCategory.UTILITIES, listOf("clock", "alarm", "timer")),
        KeywordRule("mail", BuiltInCategory.COMMUNICATION, listOf("mail", "gmail", "email", "outlook")),
        KeywordRule("music", BuiltInCategory.MEDIA, listOf("music", "audio", "spotify", "player", "podcast", "radio")),
        KeywordRule("map", BuiltInCategory.WEB, listOf("map", "navigation", "navigate")),
        KeywordRule("globe", BuiltInCategory.WEB, listOf("browser", "chrome", "firefox", "web", "internet")),
        KeywordRule("photo", BuiltInCategory.MEDIA, listOf("photo", "gallery", "image")),
        KeywordRule("sliders", BuiltInCategory.UTILITIES, listOf("setting")),
        KeywordRule("folder", BuiltInCategory.UTILITIES, listOf("file", "files", "explorer")),
        KeywordRule("card", BuiltInCategory.MONEY, listOf("bank", "wallet", "pay", "card")),
        KeywordRule("note", BuiltInCategory.PRODUCTIVITY, listOf("note", "keep", "memo", "doc")),
        KeywordRule("heart", BuiltInCategory.HEALTH, listOf("health", "fit", "workout")),
        KeywordRule("book", BuiltInCategory.PRODUCTIVITY, listOf("book", "read", "news")),
        KeywordRule("sun", BuiltInCategory.UTILITIES, listOf("weather")),
    )

    /** The one keyword rule this haystack matches, if any (first wins). */
    private fun keywordRule(hay: String): KeywordRule? = KEYWORD_RULES.firstOrNull { it.matches(hay) }

    /** Fine-grained line glyph for a name/package haystack, else null. */
    fun keywordGlyph(hay: String): String? = keywordRule(hay)?.glyph

    /** Broad bucket for a name/package haystack (the last guess before Other), else null. */
    fun keywordCategory(hay: String): BuiltInCategory? = keywordRule(hay)?.category

    /**
     * Resolve the final category, highest confidence first. The auto layers ([intent], [fdroid],
     * keyword) are all built-in by type, so only an explicit [override] can carry a user bucket. Every
     * input except [hay] may be null (that layer simply had nothing to say); an app that matches no
     * layer resolves to Other — never a wrong bucket.
     */
    fun resolve(
        override: Category?,
        intent: BuiltInCategory?,
        fdroid: BuiltInCategory?,
        hay: String,
    ): Category =
        override ?: (intent ?: fdroid ?: keywordCategory(hay))?.category ?: BuiltInCategory.OTHER.category

    /**
     * Choose the glyph for an app given its resolved [category]. A specific
     * keyword hit (camera, dialer, …) beats the bucket's generic glyph; failing
     * both, the category's representative glyph, which for Other is the neutral
     * "app" tile.
     */
    fun glyphFor(hay: String, category: Category): String =
        keywordGlyph(hay) ?: category.glyph
}
