package fi.palonkorpi.sidehome.apps

/**
 * A small `packageId → category` snapshot **baked into the app at build** —
 * NOT a query against a running F-Droid client. It works whether or not F-Droid
 * is installed; it exists so the common de-Googled / AOSP long tail lands in the
 * right bucket even when its name carries no keyword the heuristic recognises
 * (e.g. "Aurora Store", "NewPipe", "OsmAnd").
 *
 * F-Droid's ~17 fine categories are collapsed into our broad buckets. This is a
 * curated seed, not the full index — extend it as real installs are measured on
 * the device (the issue's adb probe). Only packages we are confident about live
 * here; anything unlisted falls through to the keyword heuristic, then Other.
 */
object FdroidCategoryMap {

    /** Bundled snapshot. Package id → broad bucket. */
    private val MAP: Map<String, BuiltInCategory> = buildMap {
        // --- Connectivity / Communication (chat, mail, SIP, feeds) ---
        put("org.thoughtcrime.securesms", BuiltInCategory.COMMUNICATION)   // Signal
        put("im.vector.app", BuiltInCategory.COMMUNICATION)                // Element
        put("org.telegram.messenger", BuiltInCategory.COMMUNICATION)
        put("com.fsck.k9", BuiltInCategory.COMMUNICATION)                  // K-9 Mail
        put("eu.faircode.email", BuiltInCategory.COMMUNICATION)            // FairEmail
        put("ch.protonmail.android", BuiltInCategory.COMMUNICATION)
        put("org.linphone", BuiltInCategory.COMMUNICATION)
        put("com.nextcloud.talk2", BuiltInCategory.COMMUNICATION)
        put("com.simplemobiletools.dialer", BuiltInCategory.COMMUNICATION)
        put("com.simplemobiletools.contacts.pro", BuiltInCategory.COMMUNICATION)
        put("com.simplemobiletools.smsmessenger", BuiltInCategory.COMMUNICATION)

        // --- Internet / Web (browsers) ---
        put("org.mozilla.firefox", BuiltInCategory.WEB)
        put("org.mozilla.fennec_fdroid", BuiltInCategory.WEB)
        put("us.spotco.fennec_dev", BuiltInCategory.WEB)                   // Mull
        put("org.torproject.torbrowser", BuiltInCategory.WEB)
        put("com.duckduckgo.mobile.android", BuiltInCategory.WEB)
        put("acr.browser.lightning", BuiltInCategory.WEB)
        put("org.chromium.chrome", BuiltInCategory.WEB)

        // --- Navigation / Maps (folded into Web & Maps) ---
        put("net.osmand.plus", BuiltInCategory.WEB)                        // OsmAnd
        put("net.osmand", BuiltInCategory.WEB)
        put("de.westnordost.streetcomplete", BuiltInCategory.WEB)
        put("org.organicmaps.map", BuiltInCategory.WEB)

        // --- Multimedia (audio/video/photo) ---
        put("org.schabi.newpipe", BuiltInCategory.MEDIA)                   // NewPipe
        put("com.github.libretube", BuiltInCategory.MEDIA)
        put("org.videolan.vlc", BuiltInCategory.MEDIA)
        put("org.fitchfamily.android.dejavu", BuiltInCategory.MEDIA)
        put("com.kapp.youtube", BuiltInCategory.MEDIA)
        put("code.name.monkey.retromusic", BuiltInCategory.MEDIA)          // Retro Music
        put("org.gnucleus.music", BuiltInCategory.MEDIA)
        put("org.antennapod.audio", BuiltInCategory.MEDIA)
        put("de.danoeh.antennapod", BuiltInCategory.MEDIA)                 // AntennaPod
        put("com.simplemobiletools.gallery.pro", BuiltInCategory.MEDIA)
        put("com.simplemobiletools.musicplayer", BuiltInCategory.MEDIA)
        put("com.simplemobiletools.camera", BuiltInCategory.MEDIA)

        // --- Reading / Writing / Productivity (notes, docs, calendar, ebooks) ---
        put("org.documentfoundation.libreoffice", BuiltInCategory.PRODUCTIVITY)
        put("com.nextcloud.client", BuiltInCategory.PRODUCTIVITY)
        put("it.niedermann.owncloud.notes", BuiltInCategory.PRODUCTIVITY)
        put("org.standardnotes", BuiltInCategory.PRODUCTIVITY)
        put("com.simplemobiletools.notes.pro", BuiltInCategory.PRODUCTIVITY)
        put("com.simplemobiletools.calendar.pro", BuiltInCategory.PRODUCTIVITY)
        put("net.gsantner.markor", BuiltInCategory.PRODUCTIVITY)           // Markor
        put("org.readera", BuiltInCategory.PRODUCTIVITY)
        put("org.koreader.launcher", BuiltInCategory.PRODUCTIVITY)
        put("com.github.axet.bookreader", BuiltInCategory.PRODUCTIVITY)

        // --- Money / Finance (budget, banking, wallets) ---
        put("com.google.android.apps.walletnfcrel", BuiltInCategory.MONEY)
        put("protect.budgetwatch", BuiltInCategory.MONEY)
        put("com.machiav3lli.fdroid.installer", BuiltInCategory.UTILITIES)
        put("de.blau.android", BuiltInCategory.WEB)                        // Vespucci (map editor)

        // --- Health & Fitness ---
        put("com.google.android.apps.fitness", BuiltInCategory.HEALTH)
        put("de.tadris.fitness", BuiltInCategory.HEALTH)                   // FitoTrack
        put("com.mkulesh.micromath.plus", BuiltInCategory.UTILITIES)
        put("nodomain.freeyourgadget.gadgetbridge", BuiltInCategory.HEALTH)

        // --- Games ---
        put("net.minetest.minetest", BuiltInCategory.GAMES)                // Minetest / Luanti
        put("org.supertuxkart.stk", BuiltInCategory.GAMES)                 // SuperTuxKart
        put("io.anuke.mindustry", BuiltInCategory.GAMES)                   // Mindustry
        put("com.unciv.app", BuiltInCategory.GAMES)                        // Unciv
        put("com.shatteredpixel.shatteredpixeldungeon", BuiltInCategory.GAMES)
        put("org.lichess.mobileV2", BuiltInCategory.GAMES)                 // Lichess
        put("org.secuso.privacyfriendly2048", BuiltInCategory.GAMES)
        put("name.boyle.chris.sgtpuzzles", BuiltInCategory.GAMES)          // Sgt. Puzzles
        put("com.dozingcatsoftware.bouncy", BuiltInCategory.GAMES)         // Vector Pinball
        put("com.mojang.minecraftpe", BuiltInCategory.GAMES)               // Minecraft
        put("com.roblox.client", BuiltInCategory.GAMES)

        // --- System / Utilities (stores, files, weather, settings, tools) ---
        put("com.aurora.store", BuiltInCategory.UTILITIES)                 // Aurora Store
        put("org.fdroid.fdroid", BuiltInCategory.UTILITIES)                // F-Droid
        put("com.machiav3lli.backup", BuiltInCategory.UTILITIES)           // Neo Backup
        put("me.zhanghai.android.files", BuiltInCategory.UTILITIES)
        put("com.simplemobiletools.filemanager.pro", BuiltInCategory.UTILITIES)
        put("com.simplemobiletools.clock", BuiltInCategory.UTILITIES)
        put("org.woheller69.weather", BuiltInCategory.UTILITIES)           // Good Weather
        put("net.gsantner.opoc", BuiltInCategory.UTILITIES)
        put("org.fossify.filemanager", BuiltInCategory.UTILITIES)
        put("com.termux", BuiltInCategory.UTILITIES)
    }

    /** Bucket for a package in the bundled snapshot, or null if unlisted. */
    fun categoryFor(packageName: String): BuiltInCategory? = MAP[packageName]

    /** Number of packages in the snapshot (exposed for coverage checks/tests). */
    val size: Int get() = MAP.size
}
