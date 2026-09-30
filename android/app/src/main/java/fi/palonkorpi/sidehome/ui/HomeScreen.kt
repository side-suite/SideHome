package fi.palonkorpi.sidehome.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fi.palonkorpi.sidehome.ambient.DEFAULT_LATITUDE
import fi.palonkorpi.sidehome.ambient.Theme
import fi.palonkorpi.sidehome.ambient.accentColor
import fi.palonkorpi.sidehome.ambient.computeMood
import fi.palonkorpi.sidehome.ambient.customHueRadians
import fi.palonkorpi.sidehome.ambient.edgeOnly
import fi.palonkorpi.sidehome.ambient.fgDarkFor
import fi.palonkorpi.sidehome.ambient.fieldHueSpread
import fi.palonkorpi.sidehome.ambient.foregroundInk
import fi.palonkorpi.sidehome.ambient.sheetTone
import fi.palonkorpi.sidehome.ambient.skyTone
import fi.palonkorpi.sidehome.ambient.themeMood
import fi.palonkorpi.sidehome.apps.AppEntry
import fi.palonkorpi.sidehome.apps.AppItem
import fi.palonkorpi.sidehome.apps.AppRepository
import fi.palonkorpi.sidehome.apps.Category
import fi.palonkorpi.sidehome.apps.FolderItem
import fi.palonkorpi.sidehome.apps.HomeItem
import fi.palonkorpi.sidehome.apps.canPopFromFolder
import fi.palonkorpi.sidehome.apps.defaultFavourites
import fi.palonkorpi.sidehome.apps.folderAt
import fi.palonkorpi.sidehome.apps.moveWithinFolder
import fi.palonkorpi.sidehome.apps.placedKeys
import fi.palonkorpi.sidehome.apps.popFromFolder
import fi.palonkorpi.sidehome.apps.pruneMissing
import fi.palonkorpi.sidehome.apps.removeFromFolder
import fi.palonkorpi.sidehome.apps.removeIndex
import fi.palonkorpi.sidehome.apps.renameFolder
import fi.palonkorpi.sidehome.apps.setFolderGlyph
import fi.palonkorpi.sidehome.gesture.ArbiterHooks
import fi.palonkorpi.sidehome.gesture.DEFAULT_SETTLE_FRACTION
import fi.palonkorpi.sidehome.gesture.GestureHudState
import fi.palonkorpi.sidehome.gesture.GestureScene
import fi.palonkorpi.sidehome.gesture.GestureTuning
import fi.palonkorpi.sidehome.gesture.settleOpen
import fi.palonkorpi.sidehome.MainActivity
import fi.palonkorpi.sidehome.keynav.KeyHandlerSlot
import fi.palonkorpi.sidehome.keynav.KeyIntent
import fi.palonkorpi.sidehome.keynav.KeyNavConfig
import fi.palonkorpi.sidehome.keynav.KeyNavController
import fi.palonkorpi.sidehome.keynav.KeyProfiles
import fi.palonkorpi.sidehome.keynav.moveCursor
import fi.palonkorpi.sidehome.keynav.quickIndexTarget
import fi.palonkorpi.sidehome.nav.BackStep
import fi.palonkorpi.sidehome.nav.CoveringSurface
import fi.palonkorpi.sidehome.nav.HomeKeyAction
import fi.palonkorpi.sidehome.nav.HomeUiState
import fi.palonkorpi.sidehome.nav.KeyRoute
import fi.palonkorpi.sidehome.nav.OverlayStack
import fi.palonkorpi.sidehome.nav.backStep
import fi.palonkorpi.sidehome.nav.canOpenDrawerFromBare
import fi.palonkorpi.sidehome.nav.detachesArbiter
import fi.palonkorpi.sidehome.nav.homeKeyAction
import fi.palonkorpi.sidehome.nav.keyRoute
import fi.palonkorpi.sidehome.nav.overlayTextFieldFocused
import fi.palonkorpi.sidehome.nav.shouldFireCascade
import fi.palonkorpi.sidehome.photo.BAKE_STYLE_VERSION
import fi.palonkorpi.sidehome.photo.PhotoBake
import fi.palonkorpi.sidehome.settings.SettingsStore
import fi.palonkorpi.sidehome.settings.clockFormatAsState
import fi.palonkorpi.sidehome.settings.customDriftAsState
import fi.palonkorpi.sidehome.settings.customHueAsState
import fi.palonkorpi.sidehome.settings.customSatAsState
import fi.palonkorpi.sidehome.settings.drawerListFirstAsState
import fi.palonkorpi.sidehome.settings.formatClock
import fi.palonkorpi.sidehome.settings.keyNavEnabledAsState
import fi.palonkorpi.sidehome.settings.photoGenerationAsState
import fi.palonkorpi.sidehome.settings.photoMoodAsState
import fi.palonkorpi.sidehome.settings.showIndexBadgesAsState
import fi.palonkorpi.sidehome.settings.themeAsState
import fi.palonkorpi.sidehome.settings.wallpaperSyncHomeAsState
import fi.palonkorpi.sidehome.settings.wallpaperSyncLockAsState
import fi.palonkorpi.sidehome.settings.weatherEnabledAsState
import fi.palonkorpi.sidehome.settings.weatherPlaceAsState
import fi.palonkorpi.sidehome.wallpaper.applyWallpaper
import fi.palonkorpi.sidehome.wallpaper.wallpaperDue
import fi.palonkorpi.sidehome.weather.FuturePane
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The picker tile's corner radius. Shared: the scene rounds off to exactly this as it zooms onto
 *  the tile, so the two have to agree or the home lands as a square on a rounded card. */
internal val TILE_CORNER = 22.dp

/** The tile's width as a fraction of the screen's. Doubles as the zoom's target scale — the home
 *  shrinks by exactly this to land on the tile. Kept modest so the picker's title, name and
 *  customization controls all clear the top and bottom edges (and the bottom gesture zone). */
internal const val TILE_WIDTH_FRACTION = 0.50f

/** How far into the picker's zoom the carousel starts to appear (0 = from the first frame). */
private const val CHOOSER_FADE_IN_AT = 0.55f

/** The hairline that keeps a dark tile from bleeding into the picker's black backdrop. */
internal val TILE_BORDER = 1.dp

/**
 * The developer debug panel (long-press the clock) ships **disabled** for v1 — it exposed prototype
 * toggles (the gesture-arbiter flag, the day-scrubber) that have no place in a shipped launcher. The
 * long-press is inert while this is false; the panel and its plumbing stay in the tree, one flag from
 * coming back for development. The theme chooser (long-press *empty* home) is unrelated and stays.
 */
private const val DEBUG_MENU_ENABLED = false

/**
 * The launcher's scaffold: it owns the app list, the home model, and every surface that can cover
 * them — drawer, folder, strip, pickers, theme chooser — plus the ambient they all share.
 *
 * It holds state and routes; it draws almost nothing itself. The §1 page is [HomePage], the
 * placement model is [PlacementState], the pane scaffold is [PaneState] — each in its own file, so
 * that work on one is not work on this.
 */
@Composable
fun SideHomeApp(
    repo: AppRepository,
    settings: SettingsStore,
    homeRecall: Int,
    appReveal: Int,
    packageChange: Int,
    timeResync: Int,
) {
    val clockFormat by settings.clockFormatAsState()
    val theme by settings.themeAsState()   // SID-42: the active §6 theme (default Ambient)
    val customHue by settings.customHueAsState()   // SID-15: the Custom theme's seed hue (degrees)
    val customSat by settings.customSatAsState()   // SID-15: the Custom theme's saturation
    val customDrift by settings.customDriftAsState()   // SID-177: Custom follows the day (opt-in, off)
    val weatherPlace by settings.weatherPlaceAsState()   // its latitude casts the day-cycle (below)
    val weatherEnabled by settings.weatherEnabledAsState()   // SID-181: off disables Future outright
    val drawerListFirst by settings.drawerListFirstAsState()   // SID-182: library opens quiet
    val showIndexBadges by settings.showIndexBadgesAsState()   // SID-183: the 1–9 quick-launch labels
    val photoMoodPref by settings.photoMoodAsState()   // SID-43: the Photo theme's extracted mood
    val photoGeneration by settings.photoGenerationAsState()   // SID-43: bake cache key; 0 = none
    // installed apps (loaded off the main thread) + the home model (apps + folders)
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    var home by remember { mutableStateOf<List<HomeItem>>(emptyList()) }

    // Sandbox mode (dev): the home is reading a scratch key, so placement gestures can be tried on
    // throwaway tiles. Mirrors repo.sandbox rather than owning it — the repo's copy is the one that
    // decides which key is read, this one exists to re-run the load below when it flips.
    var sandbox by remember { mutableStateOf(repo.sandbox) }

    // The one write path for the home: nothing edits `home` without it reaching the repo, so a
    // placement that lands and a launcher that is killed a second later agree. Declared up here
    // because the placement holder below commits through it.
    fun commitHome(new: List<HomeItem>) {
        home = new
        repo.saveHome(new)
    }

    suspend fun reloadApps() {
        apps = withContext(Dispatchers.IO) { repo.loadApps() }
    }

    LaunchedEffect(sandbox) {
        if (apps.isEmpty()) reloadApps()
        val byKey = apps.associateBy { it.key }
        home = if (repo.isFirstRun()) {
            defaultFavourites(apps).map { AppItem(it.key) as HomeItem }.also { repo.saveHome(it) }
        } else {
            // Drop apps that are no longer installed — and then any folder they emptied. Uninstalls
            // have to obey the same lifecycle rule as removals (SID-31), or a folder whose every
            // member is gone keeps a home slot as a dead tile that opens onto nothing.
            repo.loadHome().pruneMissing(byKey.keys)
        }
    }

    // The library moved under us: something was installed or uninstalled, here or anywhere else on
    // the device (SID-12). Re-enumerate, then prune whatever the home lost.
    //
    // A sibling of the load above rather than a key on it: that one is guarded by `apps.isEmpty()`,
    // so re-running it would skip the very re-enumeration this needs. The two do overlap by one
    // prune, and they agree because both call the same [pruneMissing] — which is the reason it is a
    // named function and not an inline `mapNotNull` in either of them.
    LaunchedEffect(packageChange) {
        if (packageChange <= 0) return@LaunchedEffect
        reloadApps()
        commitHome(repo.loadHome().pruneMissing(apps.map { it.key }.toSet()))
    }

    // real clock — the display is only HH:mm, so we wake once per minute (at the minute
    // boundary), not every second. This is the only idle timer, so the home costs ~nothing
    // when nothing is happening: one repaint a minute as the clock + mood evolve.
    //
    // The timer cannot be the only driver: delay() counts uptime, which pauses while the CPU
    // deep-sleeps, so a screen-off doze freezes the pending tick and the clock wakes stale by the
    // whole doze. timeResync (MainActivity: SCREEN_ON / TIME_CHANGED / TIMEZONE_CHANGED) restarts
    // the producer, whose first act is to read now(); this loop stays the only awake heartbeat —
    // deliberately no TIME_TICK, see the receiver in MainActivity for why.
    val clock by produceState(initialValue = LocalDateTime.now(), timeResync) {
        while (true) {
            val now = LocalDateTime.now()
            value = now
            delay(((60 - now.second) * 1000L).coerceAtLeast(1_000L))
        }
    }
    // Debug day-scrubber: when set, overrides the real time so the whole UI can be previewed at
    // any hour of day (opened by long-pressing the clock). Null = follow the real clock.
    var debugHour by remember { mutableStateOf<Float?>(null) }
    val hour = debugHour?.toDouble() ?: (clock.hour + clock.minute / 60.0 + clock.second / 3600.0)
    // SID-42: the solar mood still drives everything, but the active theme resolves the *effective*
    // mood — themes 2/3 pin a keyframe (no drift), themes 1/4 pass the live solar mood through.
    // The day-cycle is cast for the user's own latitude once they've set a weather location, so the
    // sky's day length and twilight match where they are; DEFAULT_LATITUDE until one is set.
    val skyLatitude = weatherPlace?.lat ?: DEFAULT_LATITUDE
    val solarMood = computeMood(hour, skyLatitude, clock.dayOfYear)
    val mood = themeMood(
        theme, solarMood, customHueRadians(customHue), customSat, customDrift,
        photoMood = photoMoodPref,
    )

    // adaptive foreground with hysteresis so it flips decisively, never muddles. The edge/dark-centre
    // theme paints a dark middle regardless of the sky, so its centred home text always inks light.
    var fgDark by remember { mutableStateOf(mood.l > 0.55) }
    LaunchedEffect(mood.l) { fgDark = fgDarkFor(mood.l, fgDark) }
    val inkDark = if (edgeOnly(theme)) false else fgDark
    val fg = foregroundInk(inkDark)
    val accent = accentColor(mood, inkDark)
    val glyphFor: (AppEntry) -> String = { it.glyph }   // overrides already applied at load

    val view = LocalView.current
    val context = LocalContext.current

    // ---- wallpaper sync (SID-180) ----------------------------------------------------------
    // Consent-gated: both flags stay off until the one-time modal's Approve writes them. The
    // perceptual-delta gate inside keeps the actual setBitmap rare — a handful of re-sets around
    // dawn and dusk, silence through midday and midnight — so this effect can simply key on the
    // mood and let almost every evaluation return early.
    val wallpaperSyncHome by settings.wallpaperSyncHomeAsState()
    val wallpaperSyncLock by settings.wallpaperSyncLockAsState()
    LaunchedEffect(mood, theme, wallpaperSyncHome, wallpaperSyncLock, photoGeneration) {
        if (!wallpaperSyncHome && !wallpaperSyncLock) return@LaunchedEffect
        // Photo theme (SID-43): the wallpaper is the crisp bake, set once per pick — nothing
        // drifts here, so the delta gate is replaced by a generation check.
        if (theme == Theme.PHOTO && photoGeneration > 0) {
            if (settings.wallpaperPhotoGeneration == photoGeneration) return@LaunchedEffect
            val ok = withContext(Dispatchers.IO) {
                // Home slot: the crisp bake, zones and all — it must match what the launcher
                // paints, or the go-home transition flashes the seam. Lock slot: the clean crop —
                // the lock screen is a photo, not a launcher, so no baked-in blur.
                val homeOk = !wallpaperSyncHome || PhotoBake.loadCrisp(context)
                    ?.let { applyWallpaper(context, it, home = true, lock = false) } == true
                val lockOk = !wallpaperSyncLock || PhotoBake.loadClean(context)
                    ?.let { applyWallpaper(context, it, home = false, lock = true) } == true
                homeOk && lockOk
            }
            if (ok) settings.wallpaperPhotoGeneration = photoGeneration
            return@LaunchedEffect
        }
        if (!wallpaperDue(mood, settings.lastWallpaperMood)) return@LaunchedEffect
        val metrics = context.resources.displayMetrics
        val ok = withContext(Dispatchers.Default) {
            val frame = renderSkyBitmap(
                mood = mood,
                widthPx = metrics.widthPixels,
                heightPx = metrics.heightPixels,
                edgeOnly = edgeOnly(theme),
                hueSpread = fieldHueSpread(theme),
            )
            applyWallpaper(context, frame, wallpaperSyncHome, wallpaperSyncLock)
        }
        // Recorded only on success so a failed set retries at the next delta rather than never.
        if (ok) settings.lastWallpaperMood = mood
    }

    var folderIndex by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    // SID-43: the Photo theme's image pick — a two-step flow now: pick, then *frame*. The pick
    // only decodes (at ~2× the screen so the positioner's zoom has real pixels behind it); the
    // bake happens when the positioner's "Use photo" commits the framed window, and the theme
    // flips only once that bake lands. SAF's OpenDocument (there is no Google Photo Picker on a
    // de-Googled ROM); the read grant only needs to outlive the decode.
    var photoSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val metrics = context.resources.displayMetrics
        scope.launch {
            photoSource = withContext(Dispatchers.IO) {
                PhotoBake.loadSource(context, uri, metrics.widthPixels * 2, metrics.heightPixels * 2)
            }
        }
    }

    // ---- app library as the up-pane of the cross (SID-60) ----
    // The library stopped being a sheet you pull up and became a place you go, on the same grammar
    // as Past | Home | Future: swipe up commits it like a page. drawerFrac is that pane's position —
    // 0 = home centred, 1 = library fully up — the vertical-axis twin of PaneState.dragPx. During a
    // swipe it tracks the finger; on release it settles to a page (fling or past the settle fraction)
    // with the *same* no-bouncy spring and velocity carry the horizontal pager uses (see settleDrawer
    // / PaneState.goTo). Kept a 0..1 fraction rather than px because every reader is expressed against
    // it — the backdrop alpha, the SID-18 bar tint (backdrop colour), and the SID-30 melt
    // (translationY = (1-frac)·H); only its settle had to change to match the pager, not its unit.
    // Read in derivedStateOf / graphicsLayer so a drag animates without recomposing the tree (only
    // the crossings do).
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var containerH by remember {
        mutableIntStateOf(with(density) { configuration.screenHeightDp.dp.roundToPx() })
    }
    var drawerFrac by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val drawerActive by remember { derivedStateOf { drawerFrac > 0.001f } }
    val drawerFull by remember { derivedStateOf { drawerFrac > 0.999f } }

    // Warm the library off-screen once, shortly after launch, and keep it composed. Mounting on the
    // gesture (drawerActive) meant the whole pane — IME-backed field, glyphs, list — recomposed and
    // re-laid-out from scratch on every open: ~1.6s cold and a few hundred ms warm on this CPU
    // (measured, main-thread layout). Composed once and held, an open is only the translationY
    // animation, no re-layout. Deferred past the entrance cascade so the one-time layout burst lands
    // on an idle frame rather than stealing the cascade's. fg/accent are discrete (mood/theme), so a
    // held-but-closed drawer doesn't recompose per ambient frame; the singleton icon cache is unchanged.
    var drawerWarm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1000); drawerWarm = true }

    // Settle the library pane to a page. It shares the pager's *character* — no bounce (a page is a
    // place, not an elastic object) and the same velocity carry — but a stiffer spring than
    // PaneState.goTo's StiffnessMediumLow. That soft 400 crept for ~150ms before visibly moving when
    // an open starts from rest, and the library's common opens do start from rest: tap-to-open and the
    // slow deliberate swipe both release with ~zero velocity over a full screen height, where the
    // horizontal panes are always let go mid-drag with speed and travel a shorter distance. So the
    // grammar stays identical (no bounce, velocity-carried) while the drawer regains its snap.
    // [velocityFracPerS] is the release speed in this pane's own 0..1/second frame, carried into the
    // spring so a fling continues at the speed the finger already had; the tap / legacy paths pass 0.
    // (visibilityThreshold is fraction-scaled here, not the pager's px 0.5.)
    fun settleDrawer(target: Float, velocityFracPerS: Float = 0f) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                initialValue = drawerFrac,
                targetValue = target,
                initialVelocity = velocityFracPerS,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                    visibilityThreshold = 0.001f,
                ),
            ) { v, _ -> drawerFrac = v }
        }
    }
    fun dragDrawer(dyPx: Float) {
        settleJob?.cancel()
        drawerFrac = (drawerFrac - dyPx / containerH.toFloat()).coerceIn(0f, 1f)
    }

    // ---- gesture-routing arbiter (SID-24, SPEC §7) — on by default ----
    // The pure routing decisions live in fi.palonkorpi.sidehome.gesture; here we hold the surface
    // state the arbiter drives (a 3-position pager on the continuous sky + a spotlight-down stub)
    // and the tunable thresholds. All stubbed until SID-25/14/26 land the real surfaces.
    val tuning = remember { GestureTuning() }
    // On by default now (SID-24 → main): the panes / spotlight / library-pane are the home's gesture
    // model, not a debug preview. The debug toggle (long-press clock → Gesture arbiter) still flips it
    // off for an A/B against the old direct-drag home. Not persisted — every launch starts in gesture
    // mode.
    var arbiterEnabled by remember { mutableStateOf(true) }

    // SID-23's grouped-mode prototype: which way the drawer browses. Hoisted here rather than owned
    // by the drawer, and that is not tidiness — the drawer remounts on every open, so a mode it held
    // itself would snap back to the default each summon and the side-by-side comparison the issue
    // exists for would be unrunnable. Deliberately not persisted: the point of the prototype is that
    // a winner gets picked and this toggle probably goes away, so a saved preference for a control
    // we expect to delete is work built to be thrown away. Change the initial value to test a
    // grouped default.
    // Persisted (SID-23): the mode the user last chose becomes the default next launch; List until then.
    var browseMode by remember {
        mutableStateOf(
            BrowseMode.entries.firstOrNull { it.name == settings.browseModeName } ?: BrowseMode.LIST,
        )
    }
    // The App Gallery (grouped drawer) is browse-first: it arrives in D-pad mode with the first bubble
    // marked, so its search field does not hold focus and the number keys rock the grid. The A–Z List
    // is search-first (focused). This one flag drives the modality, the reveal-first bypass, and the
    // overlay ladder's text-field test (SID-110). Declared here (not by its old use site) so the
    // ladder builder below it can read it.
    val drawerGallery = drawerActive && browseMode != BrowseMode.LIST

    // Which App Gallery bucket is drilled into (SID-96), hoisted from the drawer so it's a first-class
    // surface in the one shared state model (SID-87): a drilled-in bucket is a folder-like layer, so
    // the Back ladder must step out of it before closing the whole library — exactly as it steps out of
    // a home folder. Null = the bubble grid, not a bucket. Reset when the drawer settles closed.
    var galleryBucket by remember { mutableStateOf<Category?>(null) }

    // SID-27's one-time gesture coach. Reads the persisted flag once at first composition; dismissal
    // is the thing that writes, so an install that raised it and never got touched is still coached
    // next launch.
    val coach = rememberCoach(settings)

    // SID-180's one-time wallpaper consent, on the coach's exact contract: the flag is read once,
    // answering (either way) is what writes. It waits its turn behind the coach so a fresh install
    // sees one question at a time.
    val wallpaperConsent = rememberWallpaperConsent(settings)

    // The Future pane's city composer is up (SID-35). Tracked here for one reason: a text field is a
    // surface you are inside, so it detaches the arbiter exactly as the drawer and the strip do —
    // otherwise a stray horizontal drag pages the pane out from under someone mid-word.
    var futureComposerOpen by remember { mutableStateOf(false) }

    var hudVisible by remember { mutableStateOf(false) }
    var containerW by remember {
        mutableIntStateOf(with(density) { configuration.screenWidthDp.dp.roundToPx() })
    }
    // Where the viewport sits across Past | Home | Future, and how a finger moves it (SPEC §7).
    // SID-181: weather off pulls the pager's ceiling in to Home, mirroring the Past floor — the
    // swipe-left page simply stops existing rather than opening onto an apology.
    val panes = rememberPanes(
        containerW = containerW,
        tuning = tuning,
        lastPage = if (weatherEnabled) FUTURE_PAGE else HOME_PAGE,
    )
    // If the toggle flips while the viewport is parked on Future, walk it home; goTo clamps to the
    // new ceiling, so this is a recentre, not a jump to a now-forbidden page.
    LaunchedEffect(weatherEnabled) {
        if (!weatherEnabled && !panes.isCentered) panes.goTo(HOME_PAGE)
    }
    var spotlightFrac by remember { mutableFloatStateOf(0f) }
    var spotlightJob by remember { mutableStateOf<Job?>(null) }
    var hud by remember { mutableStateOf(GestureHudState()) }

    fun dragSpotlight(dyPx: Float) {
        spotlightJob?.cancel()
        spotlightFrac = (spotlightFrac + dyPx / containerH.toFloat()).coerceIn(0f, 1f)
    }

    fun settleSpotlight(openVelocityDpPerS: Float) {
        spotlightJob?.cancel()
        val target = if (settleOpen(spotlightFrac, openVelocityDpPerS, tuning)) 1f else 0f
        spotlightJob = scope.launch {
            animate(spotlightFrac, target, animationSpec = tween(200)) { v, _ -> spotlightFrac = v }
        }
    }

    // One stable hooks object for the arbiter's pointerInput; its lambdas read live state, so the
    // captured reference stays correct across recompositions.
    // The Future pane's scroll, hoisted here because the arbiter owns every touch on the scene and
    // drives the pane by dispatching raw deltas into this state (the pane's own scroll is disabled).
    val futurePaneScroll = rememberScrollState()
    val arbiterHooks = remember {
        ArbiterHooks(
            onCenterPage = { panes.isCentered },
            drawerEngaged = { drawerFrac > 0.001f },
            spotlightEngaged = { spotlightFrac > 0.001f },
            onDrawerDrag = { dragDrawer(it) },
            onDrawerSettle = { v ->
                val h = containerH.toFloat()
                settleDrawer(
                    target = if (settleOpen(drawerFrac, v, tuning)) 1f else 0f,
                    // v is dp/s toward open; this pane travels 0..1 over the screen height, so
                    // frac/s = v(dp/s) · density(px/dp) / height(px). The same conversion dragDrawer
                    // does per frame, one step on, to seed the spring's initial velocity.
                    velocityFracPerS = if (h > 0f) v * density.density / h else 0f,
                )
            },
            onSpotlightDrag = { dragSpotlight(it) },
            onSpotlightSettle = { v -> settleSpotlight(v) },
            onPagerDrag = { dx, edge -> panes.drag(dx, edge) },
            onPagerSettle = { v -> panes.settle(v) },
            // A vertical drag on a side pane scrolls its content. Only the Future pane scrolls today
            // (Past is a placeholder); the sign flips finger-travel into scroll offset (drag up →
            // reveal below). dispatchRawDelta is synchronous, so it tracks the finger frame-for-frame.
            onPaneScroll = { dy ->
                if (panes.pageIndex == FUTURE_PAGE) futurePaneScroll.dispatchRawDelta(-dy)
            },
            // Only mutate the HUD state when the HUD is actually shown. `hud` is read in this
            // composition (passed by value to GestureScene), so a write recomposes the whole launcher
            // — and these fire at touch-down and slop-resolution, i.e. right as a swipe begins. With
            // the debug HUD hidden (the default) that is two full-tree recompositions per swipe for
            // pixels no one sees: a start-of-gesture hitch on top of an already smooth, draw-phase-only
            // pan. Gated off, a swipe writes nothing but draw-phase floats (SID-78).
            onHud = { axis, region, action ->
                if (hudVisible) {
                    hud = hud.copy(
                        axis = axis ?: hud.axis,
                        region = region ?: hud.region,
                        action = action ?: hud.action,
                    )
                }
            },
        )
    }

    // day-scrubber panel + the §1 action strip and the surfaces it raises (SID-28). All lightweight
    // floats: the home stays live and un-receded behind them.
    var debugOpen by remember { mutableStateOf(false) }
    var stripFor by remember { mutableStateOf<StripSubject?>(null) }
    var iconPickerFor by remember { mutableStateOf<AppEntry?>(null) }
    // SID-62: the category picker, raised by the strip's Category cell on a library app. Same
    // state-hoisting shape as iconPickerFor — the app it is picking for, or null when closed.
    var categoryPickerFor by remember { mutableStateOf<AppEntry?>(null) }
    var folderIconFor by remember { mutableStateOf<Int?>(null) }
    var renameFolderAt by remember { mutableStateOf<Int?>(null) }
    // SID-31: a folder removal waiting for an answer. Both paths that can destroy a folder — the
    // strip's Remove cell and a folder dropped on the remove pill — raise this same prompt.
    var confirmRemoveFolder by remember { mutableStateOf<RemoveFolderPrompt?>(null) }
    // SID-42: the zoom-out theme chooser. Opened from the debug panel until SID-26 lands the real
    // long-press-empty trigger; the live home behind recedes (chooserScale) while it browses.
    var themeChooserOpen by remember { mutableStateOf(false) }

    // SID-80: the app a launch is in flight for — non-null drives the full-screen launch-feedback
    // overlay (LaunchFeedback), raised the instant a launch fires. Same state-hoisting shape as the
    // pickers above. Set by launchApp (every launch path routes through it) and cleared when we
    // return to the foreground — the lifecycle observer just below — or by a timeout backstop, so a
    // launch that never foregrounds an app can't strand the overlay up.
    var launchingApp by remember { mutableStateOf<AppEntry?>(null) }

    // Clear the launch overlay on our return to the foreground. Launching an app sends us to
    // onPause/onStop as its window takes over; on ON_RESUME we are back and the overlay has done its
    // job. A LifecycleEventObserver for the discrete ON_RESUME *edge* — but the observer is replayed
    // the current state at registration, and we register while already RESUMED, so it fires one
    // synthetic ON_RESUME immediately; launchOverlayAction gates on having actually left the
    // foreground first so that initial replay can't clear the overlay the instant it appears. The
    // hasLeftForeground latch lives in the observer's own scope (reset on each clear), off the
    // recomposition path. The decision is pure and unit-tested in LaunchFeedbackTest.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var hasLeftForeground = false
        val observer = LifecycleEventObserver { _, event ->
            when (launchOverlayAction(event, hasLeftForeground)) {
                LaunchOverlayAction.MARK_LEFT -> hasLeftForeground = true
                LaunchOverlayAction.CLEAR -> {
                    launchingApp = null
                    hasLeftForeground = false
                }
                LaunchOverlayAction.HOLD -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Timeout backstop: a launch that fails (the app vanished, the intent no-op'd) never foregrounds
    // anything, so the ON_RESUME above never comes and the overlay would hang. Cap the wait — after
    // LAUNCH_FEEDBACK_TIMEOUT_MS with the *same* app still showing, clear it. Keyed on launchingApp
    // so a fresh launch restarts the clock; a normal return clears it long before this fires.
    LaunchedEffect(launchingApp) {
        val pending = launchingApp ?: return@LaunchedEffect
        delay(LAUNCH_FEEDBACK_TIMEOUT_MS)
        if (launchingApp == pending) launchingApp = null
    }

    // SID-112: the one home-item bounds registry. The rows report into it; the arbiter's hit-test,
    // the theme-chooser long-press gate, the placement drop-resolver, and the key-nav strip anchor
    // all read it — one source of "where does row i sit", not four copies fed from one callback.
    val homeItems = remember { HomeItemBounds() }

    // SID-29: the §1 pick-up-&-place model — what is in hand, what a release would mean, and the
    // commit point every landing goes through. It writes the home through commitHome, so a drop is
    // saved the moment it lands.
    val placement = rememberPlacement(
        home = home,
        containerW = containerW,
        containerH = containerH,
        // SID-31: a folder born from a drag-combine gets its name from what the two apps agree they
        // are. The home model knows apps only by key, so what a key *is* has to be asked from here.
        categoryOf = { key -> appsByKey[key]?.category },
        onCommit = { commitHome(it) },
        homeBounds = homeItems,
    )

    // The picker is not an overlay — it is this same view at another zoom level (DECISIONS) — so
    // the home does not recede behind it, it *becomes* the focused tile. The chooser measures that
    // tile and reports it here; the scene then scales onto those exact bounds while the carousel
    // crossfades in over the travel. The zoom waits for the bounds so the first frame can't jump.
    var tileBounds by remember { mutableStateOf<Rect?>(null) }
    val chooserZoom = animateFloatAsState(
        targetValue = if (themeChooserOpen && tileBounds != null) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "chooserZoom",
    )
    // Following drawerFrac's convention above: .value is read only inside graphicsLayer/draw
    // lambdas, so the zoom animates without recomposing the tree every frame. This derived flag is
    // the one composition-scope read, and it only flips twice per open/close.
    val chooserMounted by remember {
        derivedStateOf { themeChooserOpen || chooserZoom.value > 0.001f }
    }

    // Sync the system status/nav bar icon tint to our foreground — the one thing a
    // launcher *can* restyle on the system bars. Dark icons over a light sky, light
    // icons over a dark sky, so the OS clock/battery match the home text.
    // The picker is the exception: it lays black over the whole screen, so the bars must ink light
    // there whatever the home's sky is doing, or a light theme's dark icons vanish into it.
    LaunchedEffect(inkDark, chooserMounted) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val lightBars = inkDark && !chooserMounted
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }

    // Blast the whole overlay stack back to bare home at once (unlike Back, which pops one layer).
    // Both reveal paths need this: a HOME press, and returning from an app — the app may have been
    // launched over a peeked drawer or an open spotlight, or have stolen the foreground (an incoming
    // call) over any surface, and the home must not come back with that surface still up.
    fun resetToBareHome() {
        drawerFrac = 0f; folderIndex = null
        stripFor = null; iconPickerFor = null; categoryPickerFor = null; folderIconFor = null; renameFolderAt = null
        confirmRemoveFolder = null
        debugOpen = false
        spotlightFrac = 0f; panes.reset()
        themeChooserOpen = false
        // SID-80: the launch overlay is transient overlay state like the rest. The lifecycle
        // observer already clears it on ON_RESUME, but returning from an app also runs resetToBareHome
        // (via appReveal), so clear it here too — a reveal must never come back with it still up.
        launchingApp = null
        // An item in hand is transient overlay state like the rest: a reveal must not come back with
        // something still floating over the home. Dropping the hand cancels the carry — the item
        // never left the home list, so it simply stays where it was.
        placement.cancel()
    }

    // ---- entrance cascade: fire only on the fully-covered → revealed edge (SID-33) ----
    // The cascade is a *reveal* animation, so enterTick — which keys the home's key(enterTick)
    // cascade — is bumped only when a full-screen surface that was covering the home has just
    // closed. Reveal sources: an open folder or the spotlight closing (a Compose state edge) and
    // returning from another app (MainActivity's onStop→onNewIntent, surfaced as appReveal).
    //
    // The library (drawerFull) is deliberately NOT in this set anymore — this is where "the cascade
    // edge moves" (SID-60). It stopped being a full-screen surface that covers the home and became
    // the up-pane of the cross, the twin of Past | Home | Future. Paging Home→Future→Home never
    // re-cascades (the home was only off-centre, never gone); the library re-centring to Home is the
    // same motion, so it must not re-cascade either. SPEC §B1's "drawer-full → closed" edge is now a
    // pane re-centring, not a reveal. (drawerFull still gates overlayOpen below — an opaque library
    // means don't bother drawing the home behind it — but that is a draw optimisation, not a reveal.)
    //
    // Lightweight floats (§1 strip / icon picker / rename / debug) are NOT covered either, so
    // dismissing them never replays the cascade. A bare-home HOME press is no edge at all → no replay.
    var enterTick by remember { mutableIntStateOf(0) }
    val fullyCovered by remember {
        derivedStateOf { folderIndex != null || spotlightFrac > 0.999f }
    }
    LaunchedEffect(Unit) {
        var prev = fullyCovered
        snapshotFlow { fullyCovered }.collect { now ->
            // fullyCovered aggregates only full-screen surfaces, so any covered→revealed edge here
            // is a cascade-worthy reveal; classify with a representative full-screen kind.
            if (shouldFireCascade(prev, now, CoveringSurface.DRAWER_FULL)) enterTick += 1
            prev = now
        }
    }
    // Returning from an app: MainActivity consumed its wasBackgrounded flag and bumped appReveal.
    // The reveal resets to bare home first. If a full-screen surface was still up, clearing it is
    // itself a covered→revealed edge and the snapshotFlow above does the bump — bumping here too
    // would re-key the cascade twice and restart it mid-flight.
    LaunchedEffect(appReveal) {
        if (appReveal <= 0) return@LaunchedEffect
        val wasCovered = fullyCovered
        resetToBareHome()
        if (!wasCovered) enterTick += 1
    }

    // ---- HOME key: step the overlay stack back toward bare home, else open the drawer ----
    // homeRecall is bumped by MainActivity on a foreground HOME press. Its one honest job now is to
    // collapse overlays (it no longer keys the cascade). A return-from-app HOME is routed via
    // appReveal instead, so it is never seen here.
    // The HOME action — collapse the overlay stack a step toward bare home, else open the drawer
    // (SID-33). Extracted so both the foreground-HOME signal (homeRecall) and the key-nav Home intent
    // (SID-81) run the one rule rather than two copies that could drift.
    fun runHomeKey() {
        val action = homeKeyAction(
            HomeUiState(
                drawerFull = drawerFull,
                folderOpen = folderIndex != null,
                spotlightOpen = spotlightFrac > 0.999f,
                // SID-28: editOpen is left at its default — the Edit sheet is gone. The rename field
                // has no field of its own in HomeKeyLogic's taxonomy; it is a lightweight float
                // raised *by* the strip, so it rides along on actionMenuOpen and HOME dismisses it.
                actionMenuOpen = stripFor != null || renameFolderAt != null,
                iconPickerOpen = iconPickerFor != null || categoryPickerFor != null || folderIconFor != null,
                debugOpen = debugOpen,
            ),
        )
        // The theme chooser (SID-42) and an item in hand (SID-29) are both outside HomeKeyLogic's
        // overlay taxonomy; when either is the only thing up, homeKeyAction sees bare home and would
        // open the drawer under it. Treat them as overlays to collapse first, so HOME cancels the
        // chooser (zoom in unchanged, §6) and puts a carried item back down rather than burying it.
        if (action == HomeKeyAction.OpenDrawer && !themeChooserOpen && placement.hand == null) {
            settleDrawer(1f)
        } else {
            // Collapsing a full-screen overlay is a fully-covered→revealed edge, so the snapshotFlow
            // above replays the cascade; dismissing a lightweight float is not, so it stays put.
            // This also cancels the chooser, zooming in unchanged (§6).
            resetToBareHome()
        }
    }
    LaunchedEffect(homeRecall) {
        if (homeRecall <= 0) return@LaunchedEffect
        runHomeKey()
    }

    /**
     * SID-31: land a resolved drop, unless it would destroy a folder — in which case the carry ends
     * (the folder is back in its slot, nothing has happened yet) and the question goes up instead.
     *
     * The gate is here, at the commit point, and not in the resolver: a confirm is not a refusal.
     * The pill is a legitimate target for a folder and its ring should light exactly as it does for
     * an app — §1's rule is only that an affirmative target must never no-op, and this doesn't
     * no-op, it asks. Both paths that can destroy a folder come through this one predicate, so the
     * strip's Remove and a mid-drag drop on the pill can't drift apart.
     */
    fun commitDrop(target: DropTarget?) {
        val folderIdx = (placement.hand?.source as? DropSource.Home)
            ?.index
            ?.takeIf { target == DropTarget.Remove }
        val folder = folderIdx?.let { home.folderAt(it) }
        if (folder != null) {
            placement.cancel()
            confirmRemoveFolder = RemoveFolderPrompt(folderIdx, folder.name)
            return
        }
        placement.place(target)
    }

    /**
     * SID-80: the one launch path. Raise the feedback overlay *before* firing the intent, so it is
     * already up over whatever happens next: from a home tile it fills the beat before the app's
     * window appears; from the library it covers the drawer-close → bare-home transition (the caller
     * closes the drawer right after, under the overlay). Cleared on our return to the foreground or a
     * timeout backstop — see [launchingApp]. Every launch site (home tiles, library, folder members)
     * routes through here so the overlay covers them all.
     */
    fun launchApp(app: AppEntry) {
        launchingApp = app
        repo.launch(app)
    }

    /** §3: the OS's own per-app page. Nothing to fall back to if it's absent — swallow quietly. */
    fun openAppInfo(app: AppEntry) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", app.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * SID-90: tapping the home clock opens the device's clock app. Prefer the OS's own "show alarms"
     * action, which resolves to whatever clock app is installed — no hardcoded package on this
     * de-Googled ROM. If nothing handles it, fall back to launching the AOSP deskclock directly; if
     * that is absent too, do nothing (a launcher must never crash on a missing app).
     */
    fun launchClockApp() {
        val opened = runCatching {
            context.startActivity(
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!opened) {
            runCatching {
                context.packageManager.getLaunchIntentForPackage("com.android.deskclock")
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let { context.startActivity(it) }
            }
        }
    }

    /** Handlers whose surface lands in a later §1 sub-issue; the strip itself is complete without them. */
    fun stubAction(what: String) {
        android.util.Log.d("SID28/strip", "$what — lands in a later §1 sub-issue")
    }

    // Run one cell of the strip. The strip closes first: every action either navigates away, opens
    // its own surface, or mutates the home under where the strip was standing.
    fun runStripAction(subject: StripSubject, action: StripAction) {
        stripFor = null
        when (action) {
            StripAction.CHANGE_ICON -> when (subject) {
                is LibraryApp -> iconPickerFor = subject.app
                is HomeApp -> iconPickerFor = subject.app
                is HomeFolder -> folderIconFor = subject.index
                // A member's icon change closes the folder it was held in, then opens the picker on
                // the app — the picker floats over the bare home, not inside the receded folder.
                is FolderMember -> { folderIndex = null; iconPickerFor = subject.app }
            }

            StripAction.RENAME -> if (subject is HomeFolder) renameFolderAt = subject.index

            // SID-30: Add-to-home is tap-to-place. The drawer closes and the app comes with it, in
            // hand and finger-less, so the home lights its targets and the next tap says where it
            // goes — the §1 context jump. It is never dimmed and never dead-ends: on a full home the
            // gaps close and the rows answer instead (group with me, or take my place).
            StripAction.ADD_HOME -> when (subject) {
                is LibraryApp -> {
                    settleDrawer(0f)
                    placement.takeFromLibrary(subject.app.key)
                }
                // Move a member to home — never disabled, so two shapes. With room the app pops out
                // beside its folder and the folder closes, no ceremony. With none it comes out in
                // hand and the home lights its targets (a full home is a question, not a wall). Both
                // close the folder: the placement happens out there (SPEC §1's context jump).
                is FolderMember -> {
                    folderIndex = null
                    if (home.canPopFromFolder(subject.folderAt)) {
                        commitHome(home.popFromFolder(subject.folderAt, subject.app.key))
                    } else {
                        commitHome(home.removeFromFolder(subject.folderAt, subject.app.key))
                        placement.takeFromLibrary(subject.app.key)
                    }
                }
                is HomeApp, is HomeFolder -> {}
            }

            // Add-to-folder is folder management (§1 D); Category (library apps only — the strip
            // offers the cell nowhere else) opens the SID-62 picker to pin this app to a bucket.
            StripAction.ADD_FOLDER -> stubAction("add to folder")
            StripAction.CATEGORY -> if (subject is LibraryApp) categoryPickerFor = subject.app

            // Move is the discoverable, no-ambiguity route into the same in-hand model the
            // long-press-drag enters: it hands the item over with no finger on it, so the targets
            // light up and a tap on one lands it (SID-29). The library's own add paths are SID-30.
            StripAction.MOVE -> when (subject) {
                is HomeApp -> placement.take(subject.index)
                is HomeFolder -> placement.take(subject.index)
                // No Move cell on a member (it reorders in place, or moves to home via Add-to-home).
                is LibraryApp, is FolderMember -> {}
            }

            // Removing an app from home is cheap and reversible — it is still in the library, one
            // tap from coming back — so it needs no confirm. A folder is not: what would be lost is
            // the grouping, which was built by hand and cannot be un-lost (SID-31).
            StripAction.REMOVE -> when (subject) {
                is HomeApp -> commitHome(home.removeIndex(subject.index))
                is HomeFolder -> home.folderAt(subject.index)?.let {
                    confirmRemoveFolder = RemoveFolderPrompt(subject.index, it.name)
                }
                // Out of the folder, not off the home. No confirm: the app is cheap to put back, and
                // the folder auto-deletes if this was its last member — by then it is empty, so there
                // is no grouping left to lose. That auto-delete takes the surface the strip stood in.
                is FolderMember -> {
                    val next = home.removeFromFolder(subject.folderAt, subject.app.key)
                    if (next.folderAt(subject.folderAt) == null) folderIndex = null
                    commitHome(next)
                }
                is LibraryApp -> {}
            }

            // Info is now also the uninstall route: the app's system-details page carries the OS
            // Uninstall button, so the launcher no longer offers its own (dead) Uninstall cell.
            StripAction.APP_INFO -> when (subject) {
                is LibraryApp -> openAppInfo(subject.app)
                is HomeApp -> openAppInfo(subject.app)
                is FolderMember -> openAppInfo(subject.app)
                is HomeFolder -> {}
            }
        }
    }

    // Home is hidden only when an overlay fully covers it (drawer fully open, or a folder). During
    // a partial drawer drag the home stays visible behind the rising sheet.
    val overlayOpen = drawerFull || folderIndex != null

    // SID-110: the one snapshot of the overlay stack. Every "which surface owns input" decision below
    // — the Back ladder, the arbiter-detach test, the key-nav routing, the text-field test, the
    // open-drawer guard — is a pure function of this one value ([fi.palonkorpi.sidehome.nav]), so they
    // can no longer drift the way five hand-copied subsets did. Read live: it captures the holders at
    // the moment it is called (a Back press, a recomposition), off the composition path where needed.
    fun stack() = OverlayStack(
        coach = coach.visible,
        wallpaperConsent = !coach.visible && wallpaperConsent.visible,
        confirmRemoveFolder = confirmRemoveFolder != null,
        hand = placement.hand != null,
        iconPicker = iconPickerFor != null,
        categoryPicker = categoryPickerFor != null,
        photoPositioner = photoSource != null,
        folderIcon = folderIconFor != null,
        rename = renameFolderAt != null,
        strip = stripFor != null,
        debug = debugOpen,
        themeChooser = themeChooserOpen,
        futureComposer = futureComposerOpen,
        folder = folderIndex != null,
        drawerActive = drawerActive,
        drawerGallery = drawerGallery,
        galleryBucket = galleryBucket != null,
        spotlightEngaged = arbiterEnabled && spotlightFrac > 0.001f,
        panesOffCentre = arbiterEnabled && !panes.isCentered,
    )

    // The Back ladder — one step back through whatever is innermost. The *order* now lives in the pure
    // [backStep] (SID-110); here each step is only its dismiss side effect. The key-nav Back intent
    // (SID-81/SID-87) walks this exact same function, so key-nav's Back *is* this, not a second copy.
    fun handleBack() {
        when (backStep(stack())) {
            // The coach is over everything and eats every touch, so it is the only thing a Back can be
            // aimed at while it is up — dismissing it here is the same one way out (Back is a touch).
            BackStep.Coach -> coach.dismiss()
            // The wallpaper consent answers its safe answer, exactly as the confirm modal answers
            // Cancel: Back must never be the thing that overwrites a wallpaper.
            BackStep.WallpaperConsent -> wallpaperConsent.dismiss()
            // A question is the innermost thing on screen and answers Cancel — before the hand, because
            // raising it already put the carry down.
            BackStep.ConfirmRemoveFolder -> confirmRemoveFolder = null
            // Back while carrying = put it back where it came from (SPEC §1: Back cancels a placement).
            BackStep.Hand -> placement.cancel()
            BackStep.IconPicker -> iconPickerFor = null
            BackStep.CategoryPicker -> categoryPickerFor = null
            // Back out of the framing step = don't use the photo; the chooser is still beneath.
            BackStep.PhotoPositioner -> photoSource = null
            BackStep.FolderIcon -> folderIconFor = null
            BackStep.Rename -> renameFolderAt = null
            BackStep.Strip -> stripFor = null
            BackStep.Debug -> debugOpen = false
            // A drilled-in Gallery bucket steps out to the bubble grid first; only a second Back closes
            // the library (SID-96). Touch Back hits the drawer's own BackHandler, which does the same.
            BackStep.GalleryBucket -> galleryBucket = null
            BackStep.Drawer -> settleDrawer(0f)
            // Return-to-center: the residual (directionless) edge-Back and an explicit Back both
            // collapse the arbiter's surfaces toward the center home rather than jumping a pane.
            BackStep.Spotlight -> settleSpotlight(-Float.MAX_VALUE)
            BackStep.RecentrePanes -> panes.goTo(HOME_PAGE)
            BackStep.Folder -> folderIndex = null
            // Bare home: nothing to pop. The launcher still swallows Back (BackHandler is always on).
            BackStep.None -> {}
        }
    }

    // ---- hardware key-navigation (SID-81) ----------------------------------------------------
    // The intents arrive from MainActivity.dispatchKeyEvent via [KeyNavController]; here they mutate
    // the very same holders touch drives (folderIndex / stripFor / drawerFrac / the favourite cursor),
    // so there is one surface-state model, not two (SID-87). The cursor is an app-owned index (SID-84).
    val keyNav = remember { KeyNavController() }
    // The one shipped key-nav setting (SID-97): a global ON/OFF, default on (the SP-01 is a
    // hardware-keypad phone). OFF makes dispatchKeyEvent a full passthrough so it never fights an
    // external Key Mapper app. The profile switcher stays post-MVP (SID-89) — the unified all-tiles
    // default needs none.
    val keyNavEnabled by settings.keyNavEnabledAsState()
    // The per-view taste defaults (SID-97), one flip-ready object. Held as state so the debug toggles
    // (long-press the clock) can flip search-auto-focus per view on-device; whichever the thumb
    // settles on becomes the shipped default and the toggle graduates or is deleted. NOT the
    // keycode→intent map — that's the profile's job.
    var keyNavConfig by remember { mutableStateOf(KeyNavConfig.DEFAULT) }
    // A hardware keypad is attached (the same signal the drawer auto-focus keys off, SID-21). Gates
    // the faint direct-index position numbers (1–N) so a virtual-only device shows none.
    val hardwareKeys = configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    // The favourite cursor, and whether it should show. Hidden until the first key move so the
    // touch-first home is unchanged until someone actually navigates by key.
    var homeCursor by remember { mutableIntStateOf(0) }
    var keyNavActive by remember { mutableStateOf(false) }
    // Row bounds live in the shared [homeItems] registry (SID-112). Prune rows that no longer exist —
    // a stale rect over now-empty space would otherwise make the arbiter yield there and swallow a
    // page swipe (the key-raised strip anchors off the same registry, so it stays correct too).
    LaunchedEffect(home.size) { homeItems.retain(home.indices.toSet()) }
    // Linear cursors for the folder (a vertical list, like the home) and horizontal for the strip's
    // cells. Reset — and made visible — when their surface (re)opens.
    var folderCursor by remember { mutableIntStateOf(0) }
    var stripCursor by remember { mutableIntStateOf(0) }
    // Reset the cursor to the first item on open, but do NOT reveal the highlight — that's the rocking
    // path's job (SID-98). A folder/strip opened by a direct-index number stays highlight-free; the
    // first ring press is what brings the cursor up (at item 1). If key-nav was already active (you
    // rocked home, then selected in), the flag carries and the reset cursor shows at the first item.
    LaunchedEffect(folderIndex) { folderCursor = 0 }
    LaunchedEffect(stripFor) { stripCursor = 0 }
    // The drawer owns its ranked results, so it registers a handler the router calls while the drawer
    // is the active surface (SID-84) — see [KeyHandlerSlot] for why this isn't Compose state.
    val drawerKey = remember { KeyHandlerSlot() }

    // Launching an app ends the key-nav session: drop the highlight so returning to a fresh home — or
    // switching to a direct-index tile — shows no stale cursor (the highlight is a live-rocking
    // affordance only). launchApp itself is declared before keyNavActive, so this wraps it here.
    fun keyLaunch(app: AppEntry) { keyNavActive = false; launchApp(app) }

    // The strip's context for a subject, shared by the render site and key-nav so the cell list they
    // act on is the one list (SID-87). Mirrors the ActionStrip call site's ctx construction.
    fun stripContextFor(subject: StripSubject): ActionContext = when (subject) {
        is LibraryApp -> ActionContext(
            target = ActionTarget.LIBRARY_APP,
            onHome = home.placedKeys().contains(subject.app.key),
            homeCount = home.size,
        )
        is HomeApp -> ActionContext(target = ActionTarget.HOME_APP, homeCount = home.size)
        is HomeFolder -> ActionContext(target = ActionTarget.HOME_FOLDER, homeCount = home.size)
        // A member's verbs are folder-membership lifecycle; none of them gate on the home count or on
        // being placed, so the ctx is bare (Add-to-home is never dimmed — see [actionsFor]).
        is FolderMember -> ActionContext(target = ActionTarget.FOLDER_MEMBER)
    }

    // Act on the home. Up/down rock the favourite cursor; left/right page the panes (SID-82);
    // direct-index self-activates without a cursor.
    fun applyHomeIntent(intent: KeyIntent) {
        if (home.isEmpty()) return
        val sel = homeCursor.coerceIn(0, home.size - 1)
        when (intent) {
            KeyIntent.NavUp -> homeCursor = moveCursor(sel, -1, home.size)
            KeyIntent.NavDown -> homeCursor = moveCursor(sel, 1, home.size)
            // Home's horizontal axis pages Past·Home·Future. Past is disabled for v1, so a left page
            // lands back on Home. This is the Sundial ring's job — QWERTY/T9 have no left/right key.
            KeyIntent.NavLeft -> panes.goTo(FIRST_PAGE)
            KeyIntent.NavRight -> panes.goTo(FUTURE_PAGE)
            KeyIntent.Select -> when (val item = home[sel]) {
                is AppItem -> appsByKey[item.key]?.let { keyLaunch(it) }
                is FolderItem -> folderIndex = sel
            }
            is KeyIntent.ItemMenu -> {
                // Hold-Select targets the cursor; hold-number targets that item (SID-87).
                val target = intent.index?.let { quickIndexTarget(it, home.size) } ?: sel
                val bounds = homeItems.boundsOf(target) ?: Rect.Zero
                when (val item = home[target]) {
                    is AppItem -> appsByKey[item.key]?.let { stripFor = HomeApp(it, target, bounds) }
                    is FolderItem -> stripFor = HomeFolder(target, bounds)
                }
            }
            // Direct-index self-activates and moves no cursor — it isn't a d-pad step (SID-84).
            is KeyIntent.QuickIndex -> quickIndexTarget(intent.n, home.size)?.let { i ->
                when (val item = home[i]) {
                    is AppItem -> appsByKey[item.key]?.let { keyLaunch(it) }
                    is FolderItem -> folderIndex = i
                }
            }
            else -> {}
        }
    }

    // Act on the cursor's member inside an open folder — a vertical list, so the same linear model as
    // the home. Select launches and closes the folder (matching the touch onLaunch).
    fun applyFolderIntent(intent: KeyIntent) {
        val folder = folderIndex?.let { home.folderAt(it) } ?: return
        val members = folder.appKeys.mapNotNull { appsByKey[it] }
        if (members.isEmpty()) return
        val sel = folderCursor.coerceIn(0, members.size - 1)
        when (intent) {
            // A vertical list, so both axes advance the same single cursor (SID-82's 1-D rule) — the
            // Sundial ring can walk it up/down or left/right.
            KeyIntent.NavUp, KeyIntent.NavLeft -> folderCursor = moveCursor(sel, -1, members.size)
            KeyIntent.NavDown, KeyIntent.NavRight -> folderCursor = moveCursor(sel, 1, members.size)
            KeyIntent.Select -> { keyLaunch(members[sel]); folderIndex = null }
            // Direct-index self-activates, no cursor move.
            is KeyIntent.QuickIndex -> quickIndexTarget(intent.n, members.size)?.let {
                keyLaunch(members[it]); folderIndex = null
            }
            // A member's action strip by key (hold) is deferred; Back closes the folder (a global).
            else -> {}
        }
    }

    // Move across the strip's cells and fire one — left/right rock the cursor, a number fires directly.
    fun applyStripIntent(intent: KeyIntent) {
        val subject = stripFor ?: return
        val cells = actionsFor(stripContextFor(subject))
        if (cells.isEmpty()) return
        val sel = stripCursor.coerceIn(0, cells.size - 1)
        // A disabled cell is a visible no-op, exactly as a tap on it is (it isn't clickable).
        fun fire(i: Int) = cells[i].let { if (it.enabled) runStripAction(subject, it.action) }
        when (intent) {
            // A 1-D horizontal surface, so both axes step the single cell (SID-82) — the Sundial ring's
            // up/down drives it just as left/right does.
            KeyIntent.NavLeft, KeyIntent.NavUp -> stripCursor = moveCursor(sel, -1, cells.size)
            KeyIntent.NavRight, KeyIntent.NavDown -> stripCursor = moveCursor(sel, 1, cells.size)
            KeyIntent.Select -> fire(sel)
            is KeyIntent.QuickIndex -> quickIndexTarget(intent.n, cells.size)?.let { fire(it) }
            else -> {}
        }
    }

    fun applyKeyIntent(intent: KeyIntent) {
        // Globals that fire regardless of the surface underneath.
        when (intent) {
            KeyIntent.Home -> { runHomeKey(); return }
            KeyIntent.Back -> { handleBack(); return }
            KeyIntent.OpenDrawer -> {
                // The Sundial's library corner toggles: close the drawer if it's open, else open it
                // from a settled bare home (if anything else is up, Back/Home clear it first). The
                // guard is the pure [canOpenDrawerFromBare] (SID-110).
                if (drawerActive) {
                    settleDrawer(0f)
                } else if (canOpenDrawerFromBare(stack())) {
                    settleDrawer(1f)
                }
                return
            }
            else -> {}
        }
        // The cursor highlight is a rocking (d-pad) affordance only — direct-index self-activates and
        // shows no cursor (SID-84/SID-98). Rocking (or Select) reveals it; a direct-index number press
        // hides it again, so the highlight tracks "am I d-padding right now?".
        val wasActive = keyNavActive
        when (intent) {
            KeyIntent.NavUp, KeyIntent.NavDown, KeyIntent.NavLeft, KeyIntent.NavRight,
            KeyIntent.Select -> keyNavActive = true
            is KeyIntent.QuickIndex -> keyNavActive = false
            else -> {}
        }
        // One routing decision for both the reveal-first gate and the dispatch below: which surface a
        // key-nav intent acts on (SID-110's [keyRoute]).
        val route = keyRoute(stack())
        val isNav = intent == KeyIntent.NavUp || intent == KeyIntent.NavDown ||
            intent == KeyIntent.NavLeft || intent == KeyIntent.NavRight
        // Reveal-first (SID-98): the first rocking press only brings the highlight up on the current
        // cursor — the first item — it does not also step. Later presses move. Only cursor moves are
        // reveal-first; home pages on left/right, so those still page on the first press. The App
        // Gallery is never reveal-first — it arrives in D-pad mode with the first bubble marked (SID-96).
        val movesCursor = when (route) {
            KeyRoute.Strip, KeyRoute.Folder -> isNav
            KeyRoute.ModalGlobalsOnly -> false
            KeyRoute.Drawer -> !drawerGallery && (intent == KeyIntent.NavUp || intent == KeyIntent.NavDown)
            KeyRoute.Home -> intent == KeyIntent.NavUp || intent == KeyIntent.NavDown
        }
        if (movesCursor && !wasActive) return
        // Surface-scoped, innermost first. A carry and the pickers/modals answer only the globals above
        // (Back/Home step them back); the drawer routes through its own registered handler.
        when (route) {
            KeyRoute.Strip -> applyStripIntent(intent)
            KeyRoute.Folder -> applyFolderIntent(intent)
            KeyRoute.ModalGlobalsOnly -> {}
            KeyRoute.Drawer -> drawerKey.handler?.invoke(intent)
            KeyRoute.Home -> applyHomeIntent(intent)
        }
    }

    // Feed the controller live state after each successful recomposition, then hand the Activity its
    // handler. A text field is "focused" (SID-85) whenever a surface that owns one is up — the
    // drawer's search (auto-focused, SID-21), a folder rename, or the Future pane's city composer — so
    // letters type there while the uniform reserved set (D-pad + Enter + Back) still navigates.
    SideEffect {
        keyNav.navEnabled = keyNavEnabled
        // One hold threshold for finger and key (SID-111): feed the key controller the same live
        // GestureTuning.holdTimeoutMs the touch itemPress race reads.
        keyNav.holdTimeoutMs = tuning.holdTimeoutMs
        // A text field is "focused" (SID-85) only where one holds focus: the A–Z List's search
        // (auto-focused, SID-21), a folder rename, or the Future city composer. NOT the App Gallery —
        // it's browse-first (SID-96), so its numbers rock the bubble D-pad instead of typing. The
        // pure [overlayTextFieldFocused] (SID-110) is the one definition.
        keyNav.textFieldFocused = overlayTextFieldFocused(stack())
        keyNav.profile = KeyProfiles.DEFAULT
        keyNav.onIntent = { applyKeyIntent(it) }
    }
    val keyNavHost = remember(context) {
        generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is MainActivity } as? MainActivity
    }
    DisposableEffect(keyNavHost, keyNav) {
        keyNavHost?.keyEventHandler = keyNav::onKeyEvent
        onDispose { keyNavHost?.keyEventHandler = null }
    }

    // A launcher must swallow Back / the edge-swipe gesture. If we only intercept while an overlay
    // is up, Back on the bare home propagates to the system, which finishes this activity and
    // instantly relaunches home — the "closes then reopens" flicker. So we stay always-enabled.
    BackHandler { handleBack() }

    // No blur. Overlays float over a flat, sky-derived solid — the cheapest possible backdrop:
    // one opaque fill, no capture, no texture blit. Tuned to match the home so it doesn't glow.
    // Edge is a dark theme, so its overlays (the drawer, pickers, strips) sit on a dark tint rather
    // than the bright sky the other themes derive — otherwise the drawer would open bright over a
    // black home.
    val backdropColor = if (edgeOnly(theme)) Color(0xFF121316) else skyTone(mood)
    val menuColor = if (edgeOnly(theme)) Color(0xFF1B1D22) else sheetTone(mood)

    // ---- Photo theme runtime (SID-43) --------------------------------------------------------
    // Two static bakes and one crossfade — never a live blur (A22). The bakes load when the Photo
    // theme is live *or* the chooser is up (its tile previews the crisp bake); generation is the
    // cache key, so a re-pick swaps them wholesale.
    val photoBakes = rememberPhotoBakes(
        generation = photoGeneration,
        active = photoGeneration > 0 && (theme == Theme.PHOTO || chooserMounted),
    )
    val photoActive = theme == Theme.PHOTO && photoBakes != null
    // The crossfade to the blurred bake under the backdrop-less floats (strip / rename / confirm /
    // carry) — DECISIONS' photo carve-out. The drawer and folder recede via their OverlayBackdrop
    // fills instead, which also covers the scene being culled outright at drawerFull (overlayOpen).
    val photoBlurTarget = photoActive &&
        (stripFor != null || renameFolderAt != null || confirmRemoveFolder != null || placement.hand != null)
    val photoBlur = animateFloatAsState(
        targetValue = if (photoBlurTarget) 1f else 0f,
        animationSpec = tween(200),
        label = "photoBlur",
    )
    val photoOverlayBake = if (theme == Theme.PHOTO) photoBakes?.blurred else null

    // A shipped bake-style change re-fits the stored crop lazily (SID-43) — no re-pick; the
    // generation bump reloads the bitmaps everywhere, including the home wallpaper.
    var photoRebaking by remember { mutableStateOf(false) }
    LaunchedEffect(photoGeneration, theme) {
        if (theme != Theme.PHOTO || photoGeneration <= 0) return@LaunchedEffect
        if (settings.photoBakeStyle == BAKE_STYLE_VERSION) return@LaunchedEffect
        photoRebaking = true
        val metrics = context.resources.displayMetrics
        val remood = withContext(Dispatchers.Default) {
            PhotoBake.rebake(context, metrics.widthPixels, metrics.heightPixels)
        }
        if (remood != null) {
            // The mood's lightness is ink-band-derived, so a style change can move the ink too.
            settings.photoMood = remood
            settings.photoBakeStyle = BAKE_STYLE_VERSION
            settings.photoGeneration += 1
        }
        photoRebaking = false
    }

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Inter),
        LocalIndication provides NoIndication,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged {
                    if (it.height > 0) containerH = it.height
                    if (it.width > 0) containerW = it.width
                },
        ) {
            // While the picker is up the live ambient behind it is replaced by one flat fill
            // (DECISIONS): a second full-screen render, gone. Black rather than a sky tone, so the
            // tiles read as lit objects against nothing rather than as cards on another sky — the
            // picker is a room of its own. It sits *under* the scene so the shrinking home reveals
            // it, and is gated on the zoom, not themeChooserOpen, so it survives the zoom back in.
            if (chooserMounted) {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }

            // live scene (sky + home): composed always, drawn only while an overlay fully covers it.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The picker's zoom: scale the whole scene — sky included — onto the focused
                    // tile's exact bounds. Uniform, because the tile carries the screen's aspect
                    // ratio; a non-uniform fit would stretch the home. graphicsLayer applies
                    // translation in parent space (unscaled), so the offsets are a plain delta of
                    // centres. It's a transform of an already-rendered layer, never a re-layout —
                    // AmbientBackground's Canvas must not repaint per frame on this hardware.
                    .then(
                        if (chooserMounted) {
                            Modifier.graphicsLayer {
                                val b = tileBounds ?: return@graphicsLayer
                                val p = chooserZoom.value
                                val s = lerp(1f, b.width / size.width, p)
                                scaleX = s
                                scaleY = s
                                translationX = lerp(0f, b.center.x - size.width / 2f, p)
                                translationY = lerp(0f, b.center.y - size.height / 2f, p)
                                // Round off as it shrinks, so it lands as the tile rather than as a
                                // square inset. Divided by the scale because the radius is applied
                                // in the layer's own pre-scale space.
                                clip = true
                                shape = RoundedCornerShape(lerp(0f, TILE_CORNER.toPx(), p) / s)
                            }
                        } else {
                            Modifier
                        },
                    )
                    // Skip the scene entirely once the picker has landed on top of it — same rule
                    // as overlayOpen, and the reason browsing used to cost a full-screen home
                    // render per frame behind the carousel for nothing.
                    .drawWithContent {
                        if (!overlayOpen && chooserZoom.value < 0.999f) drawContent()
                    },
            ) {
                // The Photo theme swaps the sky for its two baked bitmaps (SID-43); every other
                // theme paints the panning sky exactly as before. Same slot, so the photo
                // participates in the chooser zoom and sits under the panes and dots.
                if (photoActive && photoBakes != null) {
                    PhotoBackdrop(
                        bakes = photoBakes,
                        // Two drivers, whichever is stronger: the overlay tween (strip/rename/
                        // confirm/carry), and the pane position — swiping toward the weather
                        // dissolves the photo into its blurred bake in lockstep with the finger,
                        // so the pane's text always lands on calm ground. Draw-phase reads only.
                        blurFrac = {
                            val w = containerW.toFloat()
                            val paneFrac =
                                if (w > 0f) (abs(panes.panPx) / w).coerceIn(0f, 1f) else 0f
                            max(photoBlur.value, paneFrac)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ContinuousSky(
                        mood = mood,
                        containerW = containerW,
                        panPx = { panes.panPx },
                        edgeOnly = edgeOnly(theme),
                        hueSpread = fieldHueSpread(theme),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // The home content, with an optional flag to omit its own vertical drag when the
                // arbiter owns the pointer stream instead.
                val homeContent: @Composable (Boolean) -> Unit = { attachDrag ->
                    HomePage(
                        clock = clock,
                        clockFormat = clockFormat,
                        home = home,
                        appsByKey = appsByKey,
                        glyphFor = glyphFor,
                        fg = fg,
                        accent = accent,
                        enterTick = enterTick,
                        tuning = tuning,
                        attachDrag = attachDrag,
                        onOpenDrawer = { settleDrawer(1f) },
                        onDrawerDrag = { dy -> dragDrawer(dy) },
                        onDrawerSettle = { settleDrawer(if (drawerFrac > DEFAULT_SETTLE_FRACTION) 1f else 0f) },
                        onLaunch = { launchApp(it) },
                        onOpenFolder = { folderIndex = it },
                        onItemHold = { subject -> stripFor = subject },
                        // The hold's strip is already up when the move that makes this a pickup
                        // arrives, so the lift takes it back down.
                        onItemPickup = { i, bounds ->
                            stripFor = null
                            placement.lift(i, bounds.center)
                        },
                        onItemDrag = { p -> placement.drag(p) },
                        onItemDrop = { commitDrop(placement.dropTarget) },
                        homeItems = homeItems,
                        handIndex = placement.hand?.source?.homeIndex,
                        // The key-nav favourite cursor (SID-84), shown only once someone navigates by
                        // key so the touch-first home is untouched until then.
                        selectedIndex = if (keyNavActive && home.isNotEmpty()) {
                            homeCursor.coerceIn(0, home.size - 1)
                        } else {
                            null
                        },
                        // Faint direct-index numbers (1–N) beside each item (SID-82), when a keypad's on.
                        showIndexNumbers = hardwareKeys && showIndexBadges,
                        // Photo theme only: a soft halo under each label in the ink's opposite
                        // ground — light ink gets a dark shadow, dark ink a light glow.
                        labelHalo = if (theme == Theme.PHOTO) {
                            (if (fg.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = 0.6f)
                        } else {
                            null
                        },
                        onOpenDebug = { if (DEBUG_MENU_ENABLED) debugOpen = true },
                        onClockClick = { launchClockApp() },
                        onLongPressBackground = { themeChooserOpen = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (arbiterEnabled) {
                    GestureScene(
                        tuning = tuning,
                        fg = fg,
                        accent = accent,
                        containerW = containerW,
                        containerH = containerH,
                        // Lambdas, not values: these change every frame of a drag, and reading them
                        // here would subscribe the whole launcher to each pointer event. The panes
                        // read them in the draw phase instead (SID-25) — the rule drawerFrac and
                        // chooserZoom already follow.
                        pageIndex = { panes.pageIndex },
                        panPx = { panes.panPx },
                        spotlightFrac = { spotlightFrac },
                        hud = hud,
                        hudVisible = hudVisible,
                        // A live lambda, read at each touch-down inside the (always-mounted) arbiter:
                        // modal overlays / strip / carry / Future composer detach it, the drawer does
                        // not (a down-swipe keeps driving it closed). One definition — [detachesArbiter]
                        // (SID-110). Passed as a lambda, not a value, so raising the strip no longer
                        // unmounts the arbiter node and cancels a home item's itemPress (SID-114).
                        gesturesActive = { !detachesArbiter(stack()) },
                        hooks = arbiterHooks,
                        centerHome = { homeContent(false) },
                        // SID-35. `pageIndex` is read *inside* this lambda, not out here: it is the
                        // pane's own scope, so arriving at Future invalidates the pane rather than
                        // the launcher. It changes twice a swipe rather than every frame, which is
                        // what makes it safe to read in composition at all (SID-25).
                        futurePane = {
                            // SID-181: with weather off the slot stays mounted (PanePager always
                            // fills all three) but empty — and unreachable, the pager's ceiling
                            // sits at Home.
                            if (weatherEnabled) {
                                FuturePane(
                                    settings = settings,
                                    fg = fg,
                                    accent = accent,
                                    visible = panes.pageIndex == FUTURE_PAGE,
                                    onComposerOpenChange = { futureComposerOpen = it },
                                    scrollState = futurePaneScroll,
                                )
                            }
                        },
                        // SID-26/SID-112: a press that lands on a home favourite defers to that item's
                        // own pickup, so a hold-then-drag reorders instead of the arbiter stealing it as
                        // a page swipe. Hit-tested against the one shared registry (root coords).
                        isOverHomeItem = { pos -> homeItems.hitTest(pos) != null },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // SID-27: over the panes, never inside one — dots living on the centre home
                    // would pan away with the very home they report on. A lambda for the same reason
                    // GestureScene takes one: read in the draw phase, so a swipe repaints three
                    // circles instead of recomposing the tree.
                    // SID-181: with weather off, Home is the only navigable page — dots reporting a
                    // position across one place are noise, so they go entirely.
                    if (weatherEnabled) {
                        PageDots(
                            pagePosition = { panes.pagePosition },
                            fg = fg,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                } else {
                    homeContent(true)
                }
            }

            // The drawer backdrop and the sheet slide up together as drawerFrac rises. Both reads
            // happen inside graphicsLayer/draw, so dragging animates without recomposing the tree.
            //
            // It also stays mounted while a finger is carrying an app up out of it, however far it
            // has melted away (SID-30): Compose delivers the rest of a gesture to the node that took
            // the pointer down, so unmounting the drawer as it goes would cancel the very finger
            // doing the carrying. The backdrop is not part of that — it belongs to the open drawer.
            if (drawerWarm || drawerActive || placement.fingerCarryFromLibrary) {
                if (drawerActive) {
                    OverlayBackdrop(
                        color = backdropColor,
                        photo = photoOverlayBake,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = drawerFrac },
                    )
                }
                Drawer(
                    apps = apps,
                    favouriteKeys = home.placedKeys(),
                    fg = fg,
                    accent = accent,
                    // The fill the drawer actually floats on (the sibling Box just above), so
                    // SideType's candidate bar can match the sky rather than guess. Without it we
                    // would send barFg with no barBg and land near-white text on SideType's own
                    // default bar (SID-18).
                    backdrop = backdropColor,
                    visible = drawerFull,
                    atRest = !drawerActive,
                    tuning = tuning,
                    mode = browseMode,
                    onModeChange = { browseMode = it; settings.browseModeName = it.name },
                    // The drilled-in bucket is hoisted (SID-96) so the host's Back ladder can step out of
                    // it, the same way folderIndex works for home folders.
                    openCategory = galleryBucket,
                    onOpenCategoryChange = { galleryBucket = it },
                    // SID-80: overlay first, then melt the library away under it — the full-screen
                    // feedback covers the drawer-close → bare-home flash the issue is about.
                    onLaunch = { keyLaunch(it); drawerFrac = 0f },
                    // Key-nav (SID-84): the drawer owns its ranked results, so it registers a handler
                    // that moves a result cursor and launches the selected hit. Enter is caught at the
                    // Activity before the IME, which is also the double-Enter fix (SID-91). keyNavActive
                    // gates the highlight so touch users never see a cursor.
                    onRegisterKeyHandler = { drawerKey.handler = it },
                    keyNavActive = keyNavActive,
                    keyNavEnabled = keyNavEnabled,
                    config = keyNavConfig,
                    searchFirst = !drawerListFirst,
                    onAppHold = { app, bounds -> stripFor = LibraryApp(app, bounds) },
                    // SID-30: hold a drawer app and drag → it detaches into the hand and the drawer
                    // melts away under it, leaving the app on the home's targets. The hold's strip
                    // is already up when the move arrives, so the lift takes it back down.
                    onAppPickup = { app, bounds ->
                        stripFor = null
                        settleDrawer(0f)
                        placement.liftFromLibrary(app.key, bounds.center)
                    },
                    onAppDrag = { p -> placement.drag(p) },
                    onAppDrop = { commitDrop(placement.dropTarget) },
                    onDismiss = { settleDrawer(0f) },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = (1f - drawerFrac) * containerH.toFloat() },
                )
            }

            // SID-29: something is in hand. A sibling of the scene, like the strip, so it reads the
            // same root coordinates the rows report — and so it floats over the live, un-receded
            // home rather than inside it. Gone the instant the item lands.
            //
            // Above the drawer, not below it: a library carry begins with the drawer still covering
            // the screen, and under it the chip would be invisible until the melt slid past the
            // finger — exactly the moment it exists to explain.
            placement.hand?.let { h ->
                val carried: HomeItem? = when (val src = h.source) {
                    is DropSource.Home -> home.getOrNull(src.index)
                    // Not on the home yet — render it as the thing it is about to become.
                    is DropSource.Library -> AppItem(src.key)
                }
                if (carried != null) {
                    val carriedApp = (carried as? AppItem)?.let { appsByKey[it.key] }
                    // Where the item sits when no finger is carrying it (Move, and Add-to-home).
                    val parked = parkedChipOffset(containerW, placement.removeZone)
                    HandLayer(
                        glyph = when (carried) {
                            is AppItem -> carriedApp?.let(glyphFor) ?: "app"
                            is FolderItem -> carried.glyph
                        },
                        label = when (carried) {
                            is AppItem -> carriedApp?.label.orEmpty()
                            is FolderItem -> carried.name
                        },
                        byFinger = h.byFinger,
                        targets = placement.targets,
                        dropTarget = placement.dropTarget,
                        removeZone = placement.removeZone,
                        // Read in the draw phase: the carried item follows the finger without
                        // recomposing the tree per pointer event.
                        chipAt = { if (h.byFinger) placement.pointer else parked },
                        fg = fg,
                        accent = accent,
                        chipColor = menuColor,
                        onTapPlace = { pos -> commitDrop(placement.resolveAt(pos)) },
                    )
                }
            }

            // opaque backdrop for the folder popover (the drawer has its own faded one)
            if (folderIndex != null) {
                OverlayBackdrop(color = backdropColor, photo = photoOverlayBake, modifier = Modifier.fillMaxSize())
            }

            folderIndex?.let { at ->
                home.folderAt(at)?.let { folder ->
                    FolderPopover(
                        folder = folder,
                        appsByKey = appsByKey,
                        glyphFor = glyphFor,
                        tuning = tuning,
                        fg = fg,
                        accent = accent,
                        stripColor = menuColor,
                        selectedIndex = if (keyNavActive) folderCursor.coerceIn(0, (folder.appKeys.size - 1).coerceAtLeast(0)) else null,
                        showIndexNumbers = hardwareKeys && showIndexBadges,
                        onLaunch = { launchApp(it); folderIndex = null },
                        // SID-114: a member's hold raises the one host strip, as a [FolderMember] —
                        // same lifecycle (Back, key-nav, arbiter-detach) as every other strip. A
                        // pickup that turns the hold into a reorder carry clears it back off.
                        onMemberHold = { app, bounds -> stripFor = FolderMember(at, app, bounds) },
                        onMemberPickup = { stripFor = null },
                        onDismiss = { folderIndex = null },
                        onReorder = { from, toGap -> commitHome(home.moveWithinFolder(at, from, toGap)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // SID-28: hold an item (home row or drawer row) → the anchored action strip, raised
            // beside it. This and the surfaces below replace the pencil + the Edit sheet: editing is
            // direct manipulation of the item you are holding, not a hub you go to.
            stripFor?.let { subject ->
                // ctx built by the shared stripContextFor so key-nav and the render act on one cell
                // list (SID-87): LIBRARY_APP gates Add-to-home on placement; a full home is a
                // question the rows answer, not a wall (SID-30).
                ActionStrip(
                    cells = actionsFor(stripContextFor(subject)),
                    itemBounds = subject.bounds,
                    fg = fg,
                    stripColor = menuColor,
                    selectedIndex = if (keyNavActive) stripCursor else null,
                    showIndexNumbers = hardwareKeys && showIndexBadges,
                    onAction = { runStripAction(subject, it) },
                    onDismiss = { stripFor = null },
                )
            }

            // SID-31: the one question §1 asks. Raised from two places — the strip's Remove cell and
            // a folder dropped on the remove pill — and answered here, once.
            confirmRemoveFolder?.let { prompt ->
                ConfirmModal(
                    message = removeFolderMessage(prompt.name),
                    confirmLabel = "Remove",
                    fg = fg,
                    accent = accent,
                    panelColor = menuColor,
                    onConfirm = {
                        // Re-read the slot rather than trusting the index: the question stood while
                        // the home could change under it (a reload lands, an app uninstalls), and
                        // the name is what the user actually answered about. If it no longer holds
                        // that folder, the answer was to a question that no longer exists.
                        if (home.folderAt(prompt.index)?.name == prompt.name) {
                            commitHome(home.removeIndex(prompt.index))
                        }
                        confirmRemoveFolder = null
                    },
                    onDismiss = { confirmRemoveFolder = null },
                )
            }

            // rename a home folder (the strip's Rename cell)
            renameFolderAt?.let { i ->
                (home.getOrNull(i) as? FolderItem)?.let { folder ->
                    RenamePopover(
                        initial = folder.name,
                        fg = fg,
                        accent = accent,
                        panelColor = menuColor,
                        onCommit = { name ->
                            if (name.isNotBlank()) commitHome(home.renameFolder(i, name.trim()))
                            renameFolderAt = null
                        },
                        onDismiss = { renameFolderAt = null },
                    )
                }
            }

            // icon picker for a specific app (the strip's Change-icon cell). The glyph is a property
            // of the app, so the override propagates everywhere it appears — home and drawer both.
            iconPickerFor?.let { app ->
                Box(Modifier.fillMaxSize()) {
                    OverlayBackdrop(color = backdropColor, photo = photoOverlayBake, modifier = Modifier.fillMaxSize())
                    IconPicker(
                        title = "Change icon",
                        current = glyphFor(app),
                        fg = fg,
                        accent = accent,
                        onPick = { g ->
                            repo.setOverride(app, g)
                            scope.launch { reloadApps() }
                            iconPickerFor = null
                        },
                        onClose = { iconPickerFor = null },
                    )
                }
            }

            // SID-62: category picker for a library app (the strip's Category cell). Frameless on the
            // same opaque backdrop the icon picker uses. Picking writes a per-app override and reloads
            // the library the same way setOverride does; "Use the suggestion" clears it back to the
            // engine's guess; "New category…" makes a user bucket (SID-61) and assigns this app to it.
            categoryPickerFor?.let { app ->
                // Read the user's buckets once per open (JSON-backed) rather than every recomposition.
                val userCats = remember(app) { repo.userCategories() }
                Box(Modifier.fillMaxSize()) {
                    OverlayBackdrop(color = backdropColor, photo = photoOverlayBake, modifier = Modifier.fillMaxSize())
                    CategoryPicker(
                        currentCategoryId = app.category.id,
                        categories = categoryChoices(userCats),
                        fg = fg,
                        accent = accent,
                        panelColor = menuColor,
                        onSelect = { cat ->
                            repo.setCategoryOverride(app, cat.id)
                            scope.launch { reloadApps() }
                            categoryPickerFor = null
                        },
                        onClearOverride = {
                            repo.setCategoryOverride(app, null)
                            scope.launch { reloadApps() }
                            categoryPickerFor = null
                        },
                        onCreateCategory = { label, glyph ->
                            val cat = repo.createUserCategory(label, glyph)
                            repo.setCategoryOverride(app, cat.id)
                            scope.launch { reloadApps() }
                            categoryPickerFor = null
                        },
                        onClose = { categoryPickerFor = null },
                    )
                }
            }

            // icon picker for a home folder — a folder's glyph lives on the home item, not on an app
            folderIconFor?.let { i ->
                (home.getOrNull(i) as? FolderItem)?.let { folder ->
                    Box(Modifier.fillMaxSize()) {
                        OverlayBackdrop(color = backdropColor, photo = photoOverlayBake, modifier = Modifier.fillMaxSize())
                        IconPicker(
                            title = "Folder icon",
                            current = folder.glyph,
                            fg = fg,
                            accent = accent,
                            onPick = { g ->
                                commitHome(home.setFolderGlyph(i, g))
                                folderIconFor = null
                            },
                            onClose = { folderIconFor = null },
                        )
                    }
                }
            }

            // day-scrubber debug knob: a small non-modal strip; the home stays live behind it and
            // repaints as you drag, so you can watch the sky/ink adapt across the day.
            if (debugOpen) {
                DebugDayPanel(
                    hour = hour.toFloat(),
                    isLive = debugHour == null,
                    fg = fg,
                    accent = accent,
                    panelColor = menuColor,
                    arbiterEnabled = arbiterEnabled,
                    hudVisible = hudVisible,
                    sandbox = sandbox,
                    searchAutoFocusList = keyNavConfig.searchAutoFocusList,
                    searchAutoFocusGallery = keyNavConfig.searchAutoFocusGallery,
                    onToggleArbiter = { arbiterEnabled = !arbiterEnabled },
                    onToggleHud = { hudVisible = !hudVisible },
                    // repo first: it owns which key is read, and flipping the mirror is what
                    // re-runs the load — so the repo has to already be pointing at the new home.
                    onToggleSandbox = {
                        resetToBareHome()
                        repo.sandbox = !sandbox
                        sandbox = !sandbox
                    },
                    onResetSandbox = {
                        resetToBareHome()
                        repo.resetSandbox()
                        home = defaultFavourites(apps).map { AppItem(it.key) as HomeItem }
                            .also { repo.saveHome(it) }
                    },
                    onToggleAutoFocusList = {
                        keyNavConfig = keyNavConfig.copy(searchAutoFocusList = !keyNavConfig.searchAutoFocusList)
                    },
                    onToggleAutoFocusGallery = {
                        keyNavConfig = keyNavConfig.copy(searchAutoFocusGallery = !keyNavConfig.searchAutoFocusGallery)
                    },
                    onOpenThemes = { debugOpen = false; themeChooserOpen = true },
                    onScrub = { debugHour = it },
                    onLive = { debugHour = null },
                    onClose = { debugOpen = false },
                )
            }

            // SID-27: the single quiet first-run pass. Any touch takes it down, for ever — the four
            // things it teaches are swipes, so a tap-only dismissal would ignore the very gesture it
            // just asked for. Nothing in resetToBareHome clears it: it is not overlay state a reveal
            // should step back through, it is a thing that happens once and is then gone.
            if (coach.visible) {
                FirstRunCoach(
                    fg = fg,
                    panelColor = menuColor,
                    onDismiss = { coach.dismiss() },
                    weatherEnabled = weatherEnabled,
                    quickLaunch = hardwareKeys,
                )
            }

            // SID-180: the one-time wallpaper consent, behind the coach so a fresh install answers
            // one question at a time. Like the coach, resetToBareHome never clears it — it is a
            // question that happens once, not overlay state a reveal steps back through.
            if (!coach.visible && wallpaperConsent.visible) {
                WallpaperConsentModal(
                    fg = fg,
                    accent = accent,
                    panelColor = menuColor,
                    onApprove = { homeOn, lockOn -> wallpaperConsent.approve(homeOn, lockOn) },
                    onDismiss = { wallpaperConsent.dismiss() },
                )
            }

            // SID-42/SID-50: the theme picker. Swiping only browses; a tap on the centred tile
            // commits (writes settings.theme → the home re-themes live). It stays mounted through
            // the close so the carousel fades out *while* the home zooms back in, instead of
            // popping away first; `active` is what actually gates Back and taps.
            if (chooserMounted) {
                ThemeChooser(
                    active = themeChooserOpen,
                    currentTheme = theme,
                    customHue = customHue,
                    onCustomHue = { settings.customHue = it },
                    customSat = customSat,
                    onCustomSat = { settings.customSat = it },
                    customDrift = customDrift,
                    onCustomDrift = { settings.customDrift = it },
                    photoMood = photoMoodPref,
                    photoPreview = if (photoGeneration > 0) photoBakes?.crisp else null,
                    onChoosePhoto = { photoPicker.launch(arrayOf("image/*")) },
                    solarMood = solarMood,
                    timeStr = formatClock(clock.hour, clock.minute, clockFormat),
                    screenAspect = if (containerH > 0) containerW.toFloat() / containerH else 0.75f,
                    // Not the home's fg/accent: the picker sits on black, not on the sky, so it
                    // inks light whatever the current theme is. Passing the home's would render
                    // near-black text on black the moment a light theme was active.
                    fg = foregroundInk(false),
                    accent = accentColor(mood, false),
                    onTileBounds = { tileBounds = it },
                    onCommit = { settings.theme = it; themeChooserOpen = false },
                    onCancel = { themeChooserOpen = false },
                    // SID-15: the gear opens the launcher's own settings surface (SettingsActivity),
                    // the same page the OS app-info "Settings" deep-link reaches. Close the picker
                    // first so returning from settings lands on the bare home, not the zoom-out.
                    onOpenSettings = {
                        themeChooserOpen = false
                        runCatching {
                            context.startActivity(
                                Intent(context, SettingsActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        // Crossfade in only over the back half of the travel. The mock isn't a
                        // scaled replica of the home — it centres a shorter block (clock + two
                        // bars vs clock + date + favourites), so its clock sits lower than the
                        // real one's. Fading across the whole zoom double-images the clock, the
                        // one element the eye tracks. Landing it late keeps the travel a clean
                        // shrink and only dissolves once the two are near enough in size to read
                        // as the same thing.
                        .graphicsLayer {
                            alpha = ((chooserZoom.value - CHOOSER_FADE_IN_AT) /
                                (1f - CHOOSER_FADE_IN_AT)).coerceIn(0f, 1f)
                        },
                )
            }

            // SID-43: the framing step, above the chooser it was launched from — Cancel (or Back)
            // falls back to the chooser with nothing changed; "Use photo" bakes exactly the framed
            // window off-main and flips the theme once it lands.
            photoSource?.let { src ->
                val sourceImage = remember(src) { src.asImageBitmap() }
                // The ghost's rows are the real home — glyph + label per item, folders included —
                // and its date is HomePage's own wording, so the skeleton being framed against is
                // the one that will actually stand on the photo.
                val ghostDate = remember(clock.dayOfMonth) {
                    // The ghost has no weather beside it, so the full form always fits.
                    homeDateLabels(clock.dayOfWeek, clock.dayOfMonth, clock.month, Locale.getDefault()).first()
                }
                val ghostItems = remember(home, appsByKey) {
                    home.mapNotNull { item ->
                        when (item) {
                            is AppItem -> appsByKey[item.key]?.let { it.glyph to it.label }
                            is FolderItem -> item.glyph to item.name
                        }
                    }
                }
                PhotoPositioner(
                    source = sourceImage,
                    accent = accentColor(mood, false),
                    timeStr = formatClock(clock.hour, clock.minute, clockFormat),
                    dateStr = ghostDate,
                    items = ghostItems,
                    onUse = { zoom, panX, panY, rotDeg ->
                        photoSource = null
                        scope.launch {
                            val metrics = context.resources.displayMetrics
                            val extracted = withContext(Dispatchers.Default) {
                                PhotoBake.bake(
                                    context, src,
                                    metrics.widthPixels, metrics.heightPixels,
                                    zoom, panX, panY, rotDeg,
                                )
                            }
                            if (extracted != null) {
                                settings.photoMood = extracted
                                settings.photoBakeStyle = BAKE_STYLE_VERSION
                                settings.photoGeneration += 1
                                settings.theme = Theme.PHOTO
                            }
                        }
                    },
                    onCancel = { photoSource = null },
                )
            }

            // SID-43: the lazy rebake's corner chip — visible only while the zone blur re-fits a
            // changed home.
            if (photoRebaking) {
                RebakeChip(
                    fg = fg,
                    accent = accent,
                    panelColor = menuColor,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }

            // SID-80: the launch-feedback overlay — the topmost layer, over every surface including
            // a closing library, so a tap on an app never flashes the bare home before the app
            // appears. A quiet fade + slight scale-up on the glyph over a sky-toned fill that covers
            // whatever is receding beneath. Set by launchApp; cleared by the lifecycle observer /
            // timeout above (and by resetToBareHome on a return-from-app reveal).
            launchingApp?.let { app ->
                LaunchFeedback(
                    app = app,
                    fg = fg,
                    accent = accent,
                    backdrop = backdropColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
