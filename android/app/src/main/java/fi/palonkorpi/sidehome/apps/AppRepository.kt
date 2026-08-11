package fi.palonkorpi.sidehome.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import fi.palonkorpi.sidehome.i18n.tr
import org.json.JSONArray
import org.json.JSONObject

/**
 * One launchable app, already resolved to a line glyph and a broad category.
 */
data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
    val glyph: String,
    val category: Category,
) {
    val key: String get() = "$packageName/$className"
}

/**
 * Enumerates real launchable apps and resolves each to an Iconoir line glyph
 * (category → glyph, with a keyword nudge, else the generic app tile). Also owns
 * the small persisted state: per-app glyph overrides and the home favourites.
 */
class AppRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("sidehome", Context.MODE_PRIVATE)

    fun loadApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val self = context.packageName
        val glyphOverrides = glyphOverrides()
        val categoryOverrides = categoryOverrides(userCategories())
        val intentRoles = intentRoleCategories(pm)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != self }
            .map { ri ->
                val label = ri.loadLabel(pm).toString().ifBlank { ri.activityInfo.packageName }
                val pkg = ri.activityInfo.packageName
                val cls = ri.activityInfo.name
                val key = "$pkg/$cls"
                val hay = "$label $pkg".lowercase()
                val category = CategoryEngine.resolve(
                    override = categoryOverrides[key],
                    intent = intentRoles[pkg] ?: appInfoCategory(ri.activityInfo.applicationInfo),
                    fdroid = FdroidCategoryMap.categoryFor(pkg),
                    hay = hay,
                )
                val glyph = glyphOverrides[key] ?: CategoryEngine.glyphFor(hay, category)
                AppEntry(label, pkg, cls, glyph, category)
            }
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.key }
            .toList()
    }

    fun launch(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(entry.packageName, entry.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // app vanished between listing and tap — ignore quietly
        }
    }

    // ---- category resolution (Layer 2: intent roles) ----

    /**
     * Layer 2 of the category chain: ask `PackageManager` which apps declare an
     * `Intent.CATEGORY_APP_*` role (no network, no permission). Returns package →
     * bucket; if an app declares several roles the highest-priority one (earliest
     * in [INTENT_ROLE_CATEGORIES]) wins.
     */
    private fun intentRoleCategories(pm: PackageManager): Map<String, BuiltInCategory> {
        val out = HashMap<String, BuiltInCategory>()
        for ((role, category) in INTENT_ROLE_CATEGORIES) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(role)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                out.putIfAbsent(ri.activityInfo.packageName, category)
            }
        }
        return out
    }

    /**
     * Layer-2 fallback: the app's declared `ApplicationInfo.category` mapped into our buckets,
     * consulted only when the app exposes no `Intent.CATEGORY_APP_*` role. Restores the
     * category/glyph signal the pre-SID-22 launcher read from this field (e.g. an AUDIO app with
     * no name keyword still lands in Media → "music"). Games / unknown → null (no bucket).
     */
    private fun appInfoCategory(info: ApplicationInfo): BuiltInCategory? = when (info.category) {
        ApplicationInfo.CATEGORY_GAME -> BuiltInCategory.GAMES
        ApplicationInfo.CATEGORY_AUDIO,
        ApplicationInfo.CATEGORY_VIDEO,
        ApplicationInfo.CATEGORY_IMAGE -> BuiltInCategory.MEDIA
        ApplicationInfo.CATEGORY_SOCIAL -> BuiltInCategory.COMMUNICATION
        ApplicationInfo.CATEGORY_MAPS -> BuiltInCategory.WEB
        ApplicationInfo.CATEGORY_NEWS,
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> BuiltInCategory.PRODUCTIVITY
        // Pre-category games set only the manifest's isGame flag; still a game.
        else -> if ((info.flags and ApplicationInfo.FLAG_IS_GAME) != 0) BuiltInCategory.GAMES else null
    }

    // ---- persisted state ----

    private fun glyphOverrides(): Map<String, String> = readKvSet(KEY_OVERRIDES)

    fun setOverride(entry: AppEntry, glyph: String) {
        val current = glyphOverrides().toMutableMap()
        current[entry.key] = glyph
        writeKvSet(KEY_OVERRIDES, current)
    }

    /**
     * Layer 1: per-app category overrides, resolved to their [Category]. The store keeps a category
     * *id* per app key (a built-in name like "MEDIA", or a "user:N" id); an id that resolves to
     * neither a built-in nor a live user bucket — a stale write, or an override onto a since-deleted
     * user bucket — is dropped here, so the app falls back through the normal resolver chain (worst
     * case Other) rather than throwing. That is the old `fromName` contract, widened to user ids.
     */
    private fun categoryOverrides(userCategories: List<Category>): Map<String, Category> {
        val byId = (BuiltInCategory.entries.map { it.category } + userCategories).associateBy { it.id }
        return readKvSet(KEY_CATEGORY_OVERRIDES).mapNotNull { (key, id) ->
            byId[id]?.let { key to it }
        }.toMap()
    }

    /** Persist (or clear, when [categoryId] is null) a per-app category override for [entry]. */
    fun setCategoryOverride(entry: AppEntry, categoryId: String?) {
        val current = readKvSet(KEY_CATEGORY_OVERRIDES).toMutableMap()
        if (categoryId == null) current.remove(entry.key) else current[entry.key] = categoryId
        writeKvSet(KEY_CATEGORY_OVERRIDES, current)
    }

    // ---- user-defined categories (SID-61) ----

    /**
     * The user's own buckets, in creation order. Persisted as JSON (not the "key=value" string-set
     * the override stores use) because a [Category.label] is free text — it can hold the '=' and ','
     * that the string-set encoding reserves. A malformed entry is skipped, never thrown.
     *
     * These persist independently of membership: a bucket the user made stays even when its last app
     * leaves — unlike a folder, which auto-deletes on empty. A category named on purpose is not an
     * incidental drag-combine, and a user who created one expects it to survive going empty.
     */
    fun userCategories(): List<Category> {
        val raw = prefs.getString(KEY_USER_CATEGORIES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                Category(id, o.optString("label", tr("Category", "Kategorie")), o.optString("glyph", "app"), builtIn = false)
            }
        }.getOrElse { emptyList() }
    }

    private fun saveUserCategories(cats: List<Category>) {
        val arr = JSONArray()
        for (c in cats) arr.put(JSONObject().put("id", c.id).put("label", c.label).put("glyph", c.glyph))
        prefs.edit().putString(KEY_USER_CATEGORIES, arr.toString()).apply()
    }

    /**
     * Make a new user bucket and return it. Its id is a monotonic "user:N" — the counter only ever
     * climbs, never reused on delete, so an override still pointing at a deleted bucket can never be
     * silently reattached to a different one that happened to inherit its id.
     */
    fun createUserCategory(label: String, glyph: String): Category {
        val n = prefs.getInt(KEY_USER_CATEGORY_SEQ, 0) + 1
        prefs.edit().putInt(KEY_USER_CATEGORY_SEQ, n).apply()
        val cat = Category("user:$n", label, glyph, builtIn = false)
        saveUserCategories(userCategories() + cat)
        return cat
    }

    fun renameUserCategory(id: String, label: String) = updateUserCategory(id) { it.copy(label = label) }

    fun setUserCategoryGlyph(id: String, glyph: String) = updateUserCategory(id) { it.copy(glyph = glyph) }

    private fun updateUserCategory(id: String, edit: (Category) -> Category) {
        saveUserCategories(userCategories().map { if (it.id == id) edit(it) else it })
    }

    /**
     * Forget a user bucket. Any per-app override still pointing at it is intentionally left in place:
     * it stops resolving (its id is now unknown), so those apps fall back through the resolver chain
     * on the next load — the same path any stale id takes — with no sweep needed here.
     */
    fun deleteUserCategory(id: String) {
        saveUserCategories(userCategories().filterNot { it.id == id })
    }

    /** Parse a "key=value" string-set pref into a map; drops malformed entries. */
    private fun readKvSet(prefKey: String): Map<String, String> {
        val raw = prefs.getStringSet(prefKey, emptySet()) ?: emptySet()
        return raw.mapNotNull {
            val idx = it.lastIndexOf('=')
            if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
        }.toMap()
    }

    /** Persist a map as a "key=value" string-set pref (inverse of [readKvSet]). */
    private fun writeKvSet(prefKey: String, map: Map<String, String>) {
        prefs.edit().putStringSet(prefKey, map.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    /**
     * Sandbox mode: a scratch home for trying the §1 placement gestures — drag, group, replace,
     * remove — on throwaway tiles instead of the shortcuts you actually reach for.
     *
     * It is a *key swap*, not a copy or a snapshot: while it's on, every read and write goes to
     * [KEY_HOME_SANDBOX] and [KEY_HOME] is never opened. That's the whole safety argument — the real
     * home comes back untouched because nothing was ever in a position to touch it, so there is no
     * backup to restore and no restore step to get wrong.
     *
     * Persisted rather than remembered, so a launcher process death mid-experiment doesn't silently
     * drop you back onto your real home and let you keep dragging, thinking you're still in the
     * sandbox — the one way this feature could do the exact damage it exists to prevent.
     *
     * Dev-only, and deliberately reachable only from the debug panel (long-press the clock).
     */
    var sandbox: Boolean
        get() = prefs.getBoolean(KEY_SANDBOX, false)
        set(value) = prefs.edit().putBoolean(KEY_SANDBOX, value).apply()

    /** Which home the reads and writes below land on. The only place [sandbox] has any effect. */
    private val homeKey: String get() = if (sandbox) KEY_HOME_SANDBOX else KEY_HOME

    /** Throw the scratch home away, so the next load re-seeds it. Never touches the real home. */
    fun resetSandbox() = prefs.edit().remove(KEY_HOME_SANDBOX).apply()

    fun isFirstRun(): Boolean =
        // In the sandbox "first run" means "no scratch home yet" → seed one. The legacy favourites
        // list is the real home's history and has no business seeding a throwaway.
        if (sandbox) !prefs.contains(KEY_HOME_SANDBOX)
        else !prefs.contains(KEY_HOME) && !prefs.contains(KEY_FAVS)

    // ---- home model (apps + folders), JSON-persisted ----

    fun loadHome(): List<HomeItem> {
        prefs.getString(homeKey, null)?.let { return parseHome(it) }
        if (sandbox) return emptyList()
        // migrate the old flat favourites list, if any
        prefs.getString(KEY_FAVS, null)?.let { raw ->
            return raw.split('\n').filter { it.isNotBlank() }.map { AppItem(it) }
        }
        return emptyList()
    }

    fun saveHome(items: List<HomeItem>) {
        val arr = JSONArray()
        for (it in items) {
            val o = JSONObject()
            when (it) {
                is AppItem -> {
                    o.put("t", "app"); o.put("k", it.key)
                }
                is FolderItem -> {
                    o.put("t", "folder"); o.put("n", it.name); o.put("g", it.glyph)
                    o.put("a", JSONArray(it.appKeys))
                }
            }
            arr.put(o)
        }
        prefs.edit().putString(homeKey, arr.toString()).apply()
    }

    private fun parseHome(raw: String): List<HomeItem> = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            when (o.optString("t")) {
                "app" -> AppItem(o.getString("k"))
                "folder" -> {
                    val a = o.optJSONArray("a") ?: JSONArray()
                    FolderItem(
                        name = o.optString("n", tr("Folder", "Ordner")),
                        appKeys = (0 until a.length()).map { a.getString(it) },
                        glyph = o.optString("g", "folder"),
                    )
                }
                else -> null
            }
        }
    }.getOrElse { emptyList() }

    companion object {
        private const val KEY_OVERRIDES = "glyph_overrides"
        private const val KEY_CATEGORY_OVERRIDES = "category_overrides"
        private const val KEY_USER_CATEGORIES = "user_categories"       // SID-61: user-made buckets
        private const val KEY_USER_CATEGORY_SEQ = "user_category_seq"   // monotonic id counter
        private const val KEY_FAVS = "favourites"       // legacy (pre-folders)
        private const val KEY_HOME = "home_v2"
        private const val KEY_HOME_SANDBOX = "home_sandbox"   // dev-only scratch home
        private const val KEY_SANDBOX = "sandbox_mode"
        const val MAX_HOME = 4

        /**
         * Intent app-role → bucket, in priority order (an app declaring several
         * roles takes the first). Roles are OS-declared and authoritative, so this
         * whole layer outranks the bundled map and the keyword heuristic. All
         * constants exist since API 30; minSdk is 31.
         */
        private val INTENT_ROLE_CATEGORIES: List<Pair<String, BuiltInCategory>> = listOf(
            Intent.CATEGORY_APP_BROWSER to BuiltInCategory.WEB,
            Intent.CATEGORY_APP_MAPS to BuiltInCategory.WEB,
            Intent.CATEGORY_APP_EMAIL to BuiltInCategory.COMMUNICATION,
            Intent.CATEGORY_APP_MESSAGING to BuiltInCategory.COMMUNICATION,
            Intent.CATEGORY_APP_CONTACTS to BuiltInCategory.COMMUNICATION,
            Intent.CATEGORY_APP_MUSIC to BuiltInCategory.MEDIA,
            Intent.CATEGORY_APP_GALLERY to BuiltInCategory.MEDIA,
            Intent.CATEGORY_APP_CALENDAR to BuiltInCategory.PRODUCTIVITY,
            Intent.CATEGORY_APP_FITNESS to BuiltInCategory.HEALTH,
            Intent.CATEGORY_APP_FILES to BuiltInCategory.UTILITIES,
            Intent.CATEGORY_APP_WEATHER to BuiltInCategory.UTILITIES,
            Intent.CATEGORY_APP_CALCULATOR to BuiltInCategory.UTILITIES,
            Intent.CATEGORY_APP_MARKET to BuiltInCategory.UTILITIES,
        )
    }
}

/** Pick sensible starter favourites the first time, before the user edits home. */
fun defaultFavourites(all: List<AppEntry>): List<AppEntry> {
    if (all.isEmpty()) return emptyList()
    val wanted = listOf("phone", "chat", "camera", "globe")
    val picked = LinkedHashSet<AppEntry>()
    for (g in wanted) all.firstOrNull { it.glyph == g }?.let { picked.add(it) }
    for (a in all) {
        if (picked.size >= AppRepository.MAX_HOME) break
        picked.add(a)
    }
    return picked.take(AppRepository.MAX_HOME)
}
