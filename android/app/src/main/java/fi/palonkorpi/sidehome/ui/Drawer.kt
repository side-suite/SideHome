package fi.palonkorpi.sidehome.ui

import fi.palonkorpi.sidehome.i18n.tr
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.apps.Category
import fi.palonkorpi.sidehome.apps.AppEntry
import fi.palonkorpi.sidehome.apps.FolderItem
import fi.palonkorpi.sidehome.gesture.GestureTuning
import fi.palonkorpi.sidehome.ime.drawerImeOptions
import fi.palonkorpi.sidehome.keynav.KeyIntent
import fi.palonkorpi.sidehome.keynav.KeyNavConfig
import fi.palonkorpi.sidehome.keynav.galleryDpadIntent
import fi.palonkorpi.sidehome.keynav.moveCursor
import fi.palonkorpi.sidehome.keynav.moveGridCursor
import kotlinx.coroutines.delay

private sealed interface DrawerItem
private data class SectionHeader(val letter: String) : DrawerItem
private data class AppRow(val app: AppEntry) : DrawerItem

/**
 * The app drawer / spotlight. Frameless: it never dims or tints the sky — it floats
 * on top while the home recedes beneath. Alphabetical with quiet section letters.
 *
 * Search is a real focusable [BasicTextField] wired to the active IME (JakeType/SideType),
 * so it types correctly across every input config — Compact QWERTY tile, classic tile, T9
 * tile, and virtual keyboard — instead of reading raw `unicodeChar` (which on the compact
 * tile only ever yields each key's left glyph). The on-brand look (search glyph + accent
 * caret) is kept via a custom `decorationBox`; the field just holds the query now.
 *
 * Auto-focus opens the drawer "already listening" whenever a hardware tile is present, so the
 * tile feeds the field with no tap. What shows on screen is the IME's own call: with "show
 * on-screen keyboard" off it's a candidate bar only and the list stays visible (the intended
 * tile-first config); with it on, the full soft keyboard covers the lower list (top hits + Enter
 * still work). Virtual-only devices (no tile) open unfocused ("Tap to search") so the keyboard
 * never covers the list on open. The IME action is Search, so Enter launches the top hit;
 * Backspace edits; swipe-down (from the top) or Back dismisses.
 *
 * The field also hands the IME the ambient colours and an app-name prediction hint via
 * privateImeOptions (SID-18 / SID-20) — SideType tints its tray to match the sky and biases
 * prediction toward installed app names; every other IME ignores it.
 *
 * Holding an app raises the §1 menu ([onAppHold]); moving after that hold lifts the app out of the
 * library and streams it to the host ([onAppPickup] / [onAppDrag] / [onAppDrop]) — the drag-up that
 * carries it onto the home (SID-30). The drawer does not melt itself: the host is what animates it
 * away, and what must keep it composed until the carry lands, or the finger holding the app is
 * cancelled with it.
 *
 * [mode] picks the **browse** state only (SID-23, SPEC §2b): the alphabetical list, or one of the two
 * category-bubble variants. Search overrides it wholesale — a non-empty query always collapses to the
 * flat ranked list, whatever the mode, because ranked results have an order of their own and grouping
 * them would throw it away. It is hoisted rather than owned here for a blunt reason: this drawer
 * remounts on every open, so a mode it held itself would reset to the default every summon, and the
 * side-by-side comparison the mode exists for would be impossible to run.
 *
 * @param mode prototype state, deliberately not persisted: §2b expects to *pick* a winner and delete
 *   the losers, so a [fi.palonkorpi.sidehome.settings.SettingsStore] entry would be built to be thrown away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Drawer(
    apps: List<AppEntry>,
    favouriteKeys: Set<String>,
    // Hidden apps: never part of [apps] (the host filters them out), so they are absent from the
    // A–Z list, the search ranking and the gallery buckets alike. They live only behind the locked
    // "Hidden" row at the list's end; [onVerifyHiddenPin] checks the typed password against the
    // stored hash. Empty list = feature dormant, the row simply isn't there.
    hiddenApps: List<AppEntry> = emptyList(),
    onVerifyHiddenPin: (String) -> Boolean = { false },
    fg: Color,
    accent: Color,
    backdrop: Color? = null,
    visible: Boolean,
    atRest: Boolean,
    tuning: GestureTuning,
    mode: BrowseMode,
    onModeChange: (BrowseMode) -> Unit,
    // Which Gallery bucket is drilled into, hoisted to the host (SID-96) so its Back ladder can step
    // out of the bucket before closing the library. Null = the bubble grid. See [galleryBucket] in the
    // host. Defaults keep the drawer usable in isolation (previews/tests).
    openCategory: Category? = null,
    onOpenCategoryChange: (Category?) -> Unit = {},
    onLaunch: (AppEntry) -> Unit,
    // Key-nav (SID-84): the host calls the handler this registers while the drawer is the active
    // surface, to move a result cursor and launch the selected hit. Enter is caught at the Activity
    // before the predictive IME (the double-Enter fix, SID-91); keyNavActive gates the highlight so
    // touch users never see a cursor.
    onRegisterKeyHandler: ((KeyIntent) -> Unit) -> Unit = {},
    keyNavActive: Boolean = false,
    // Whether the key-nav feature is on (SID-96/SID-97). Gates the App Gallery's browse cursor and
    // its on-screen D-pad hint: the gallery arrives in D-pad mode with the first bubble already
    // marked, so — unlike the list's rocking cursor — its highlight shows whenever key-nav is enabled,
    // not only after a rock. [config] holds the per-view taste defaults (List search-first / Gallery
    // browse-first).
    keyNavEnabled: Boolean = false,
    config: KeyNavConfig = KeyNavConfig.DEFAULT,
    // SID-182: the list-first pref, inverted — false opens the A–Z list quiet (no focus, no IME,
    // typing does nothing) with the search pill as a key-nav target. True is the shipped default
    // (search-first, SID-21).
    searchFirst: Boolean = true,
    onAppHold: (AppEntry, Rect) -> Unit,
    onAppPickup: (AppEntry, Rect) -> Unit,
    onAppDrag: (Offset) -> Unit,
    onAppDrop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    // The hidden area's session state. Unlock lasts only while the drawer is up — [atRest] relocks,
    // so every fresh summon asks for the password again. Nothing here persists.
    var pinPromptOpen by remember { mutableStateOf(false) }
    var hiddenOpen by remember { mutableStateOf(false) }
    var hiddenUnlocked by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val gridScroll = rememberScrollState()

    // SID-79: pull-down-from-anywhere to dismiss. The `detectVerticalDragGestures` on the drawer
    // column below only ever sees drags over the non-scrolling header (the grabber), because the list
    // consumes vertical drags first — which is why closing felt like it required grabbing the top. A
    // downward pull *over the list* shows up here instead: once the list (or the grouped grid) is at
    // the top it can't consume any more, so its leftover downward delta arrives in [onPostScroll].
    // Accumulate that and close past a threshold, swallowing it so the list shows no overscroll glow.
    // No explicit "at top" check is needed: away from the top the list consumes the drag, so nothing
    // is left over here.
    val currentDismiss by rememberUpdatedState(onDismiss)
    val dismissPullPx = with(LocalDensity.current) { 88.dp.toPx() }
    val pullToDismiss = remember(dismissPullPx) {
        object : NestedScrollConnection {
            private var pulled = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) pulled = 0f // scrolling back up cancels an in-progress pull
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                pulled += available.y
                if (pulled >= dismissPullPx) { pulled = 0f; currentDismiss() }
                return Offset(0f, available.y) // swallow the at-top overscroll
            }
        }
    }


    // Notification dots (SID-46). Read from the process-wide badge source rather than passed down:
    // a dot is a property of the *package*, not of this screen — see NotificationDot.kt.
    val badgedPackages = rememberBadgedPackages()

    // Search always collapses to the flat ranked list, whatever the browse mode (SPEC §2b). Computed
    // up here (not just before the list) because the auto-focus decision and the key handler below both
    // turn on which surface — the A–Z list or the grouped gallery — is showing.
    val searching = query.trim().isNotEmpty()
    val grouped = mode != BrowseMode.LIST && !searching

    // Auto-focus is **per view** now (SID-96/SID-97): the A–Z List is search-first so its field takes
    // focus when a hardware tile is present and feeds it with no tap; the App Gallery is browse-y, so
    // its field stays unfocused and the number keys drive the bubble D-pad instead. Virtual-only
    // devices never auto-focus (the keyboard would cover the list on open). This is a typing policy,
    // not a nav one, so it holds whether or not key-nav is enabled.
    val configuration = LocalConfiguration.current
    val hardwareKeys = configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    // SID-182: the list-first pref gates search-first entirely — with it off the list opens quiet
    // (no focus, no IME) and *typing does nothing*: an unfocused field receives no letters, and the
    // ladder keeps them PassThrough, so they die in the view hierarchy rather than quick-launching.
    // Search is then a place you walk to — the pill is a key-nav target (below) — or tap.
    val autoFocus = hardwareKeys && searchFirst &&
        if (grouped) config.searchAutoFocusGallery else config.searchAutoFocusList
    // The App Gallery's browse affordances — the marked bubble/member and the D-pad hint — need both a
    // hardware tile to drive them and key-nav on; a virtual-only device shows neither (SID-96).
    val galleryKeyNav = keyNavEnabled && hardwareKeys

    // Focus — and therefore SideType's candidate bar — only once the pane is actually open, not the
    // instant a drag reveals the warmed drawer. Booting the IME mid-gesture janked the open on the
    // GE8300, and an aborted open (drag up a little, let it fall back) left the bar on screen with no
    // field behind it. Keyed on [visible] (drawer-full) and [autoFocus]: a partial open never focuses;
    // a real close clears it; and flipping to the Gallery mid-open releases focus so the number keys
    // rock the grid instead of typing. "Already listening when open" (SID-21) still holds for the List.
    LaunchedEffect(visible, autoFocus) {
        if (visible && autoFocus) {
            runCatching { focus.requestFocus() }
        } else {
            // clearFocus tears down the field's InputConnection, but on the SP-01 the hardware-keyboard
            // candidate bar can linger a beat after — hanging on the home once the drawer closes. hide()
            // dismisses it now instead of temporarily.
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    // Reset for the next summon once the drawer is fully at rest and off-screen. The pane is warmed
    // once and no longer remounts per open, so nothing else clears the query / scroll / open bucket;
    // doing it while hidden means a reopen always lands clean (empty query, list at top, bubbles not a
    // drilled-in bucket) with no visible flash. atRest covers an aborted partial open too.
    LaunchedEffect(atRest) {
        if (atRest) {
            query = ""
            onOpenCategoryChange(null)
            pinPromptOpen = false
            hiddenOpen = false
            hiddenUnlocked = false
            listState.scrollToItem(0)
            runCatching { gridScroll.scrollTo(0) }
        }
    }

    val ranked = remember(apps, query) { rankApps(apps, query) }
    val items = remember(ranked, query) { toItems(ranked, query) }
    val hint = fg.copy(alpha = 0.22f)

    val buckets = remember(apps) { bucketApps(apps) }
    // Includes the hidden apps: the hidden popover resolves its members through this map too.
    val appsByKey = remember(apps, hiddenApps) { (apps + hiddenApps).associateBy { it.key } }
    // Which bucket's popover is open, matched on id (not the whole [Category]): a bucket's label/glyph
    // can be edited (user buckets) or its members change while it is open, and only the id is stable.
    // Gated on `grouped` in composition, not just cleared by the effect below, so flipping to List with
    // a bucket open can't paint the popover once over the list before the state catches up — and it
    // covers the bucket whose category no longer exists (every member re-categorised out, or folded
    // into Other under MIN_BUCKET_SIZE), which resolves to null and drops back to the bubbles.
    val openBucket = if (!grouped) null else buckets.firstOrNull { it.category.id == openCategory?.id }

    // ---- key-nav (SID-84 / SID-96) ----
    // Two cursors, one per browse surface; the handler routes to whichever is showing. The flat A–Z /
    // search LIST drives a linear cursor over [ranked]; the grouped App GALLERY drives a 2-D cursor
    // over the [buckets] grid. Select launches the ranked hit / opens the bucket, caught at the Activity
    // before the IME so a single Enter works (SID-91). Each resets to its first item on a fresh summon.
    val currentLaunch by rememberUpdatedState(onLaunch)
    val currentHold by rememberUpdatedState(onAppHold)
    var cursor by remember { mutableStateOf(0) }
    // SID-182: in list-first mode the search pill is a key-nav target one step above the top row —
    // walk up from row 0 to select it, Select on it summons search (focus + IME). Never live in
    // search-first mode, where the field is already focused.
    var pillSelected by remember { mutableStateOf(false) }
    var bucketCursor by remember { mutableStateOf(0) }
    // A third cursor for a drilled-in bucket's members (SID-96) — a bucket is a folder-like layer, so
    // once open the keys walk its apps, not the bubble grid behind it.
    var memberCursor by remember { mutableStateOf(0) }
    // The selected row's on-screen rect, so a key-hold can anchor the strip beside it the way a touch
    // hold does. Only the selected row reports up (below); the top hit by default.
    var selectedBounds by remember { mutableStateOf(Rect.Zero) }
    // The arm the D-pad hint flashes: the last direction/select pressed in the gallery, cleared a beat
    // later so it reads as a momentary pulse rather than a stuck state. [pressSeq] re-arms the timer on
    // every press — including auto-repeats of the same key — so a held direction stays lit and only
    // fades once the presses stop.
    var pressedArm by remember { mutableStateOf<KeyIntent?>(null) }
    var pressSeq by remember { mutableStateOf(0) }
    LaunchedEffect(ranked) { cursor = 0 }
    LaunchedEffect(atRest) { if (atRest) { bucketCursor = 0; pillSelected = false } }
    // Typing (having reached the field) is its own selection — the pill's job is done.
    LaunchedEffect(searching) { if (searching) pillSelected = false }
    // Walking onto the pill brings the top of the list with it, so the target is on screen.
    LaunchedEffect(pillSelected) {
        if (pillSelected) runCatching { listState.animateScrollToItem(0) }
    }
    // Land on the first member each time a bucket opens (or changes under an open popover).
    LaunchedEffect(openBucket) { memberCursor = 0 }
    LaunchedEffect(pressSeq) { if (pressedArm != null) { delay(160); pressedArm = null } }
    val selectedKey = ranked.getOrNull(cursor.coerceIn(0, (ranked.size - 1).coerceAtLeast(0)))?.key
    val keyHandler: (KeyIntent) -> Unit = { intent ->
        if (grouped) {
            // App Gallery browse-mode (SID-96): the number grid and the Sundial ring rock a 2-D cursor
            // over the bubbles; Select opens the bucket under it. A number arrives as a QuickIndex and
            // is translated through the gallery cross (2↑ 8↓ 4← 6→ 5●); the ring's Nav*/Select arrive as
            // themselves. ItemMenu is ignored on purpose: the number keys are directions here, and a
            // held one would otherwise fire the launch-key's hold-menu instead of just stepping — a
            // bucket has no strip to raise anyway, opening it IS the action.
            val dir = (intent as? KeyIntent.QuickIndex)?.let { galleryDpadIntent(it.n) } ?: intent
            // Flash the pressed arm on the hint (a direction or select; anything else is inert here).
            when (dir) {
                KeyIntent.NavUp, KeyIntent.NavDown, KeyIntent.NavLeft, KeyIntent.NavRight,
                KeyIntent.Select -> { pressedArm = dir; pressSeq++ }
                else -> {}
            }
            val bucket = openBucket
            if (bucket == null) {
                val n = buckets.size
                val cur = bucketCursor.coerceIn(0, (n - 1).coerceAtLeast(0))
                when (dir) {
                    KeyIntent.NavUp -> bucketCursor = moveGridCursor(cur, -1, 0, n, BUBBLES_PER_ROW)
                    KeyIntent.NavDown -> bucketCursor = moveGridCursor(cur, 1, 0, n, BUBBLES_PER_ROW)
                    KeyIntent.NavLeft -> bucketCursor = moveGridCursor(cur, 0, -1, n, BUBBLES_PER_ROW)
                    KeyIntent.NavRight -> bucketCursor = moveGridCursor(cur, 0, 1, n, BUBBLES_PER_ROW)
                    KeyIntent.Select -> buckets.getOrNull(cur)?.let { onOpenCategoryChange(it.category) }
                    else -> {}
                }
            } else {
                // Inside a drilled-in bucket (SID-96): walk its members like a folder — linear, both
                // axes stepping one (the popover is a small grid; home folders navigate the same way).
                // Select launches and drops the whole library; Back (a host global) steps back out to
                // the bubbles first.
                val members = bucket.apps
                val n = members.size
                val cur = memberCursor.coerceIn(0, (n - 1).coerceAtLeast(0))
                when (dir) {
                    KeyIntent.NavUp, KeyIntent.NavLeft -> memberCursor = moveCursor(cur, -1, n)
                    KeyIntent.NavDown, KeyIntent.NavRight -> memberCursor = moveCursor(cur, 1, n)
                    KeyIntent.Select -> members.getOrNull(cur)?.let { currentLaunch(it); onOpenCategoryChange(null) }
                    else -> {}
                }
            }
        } else {
            val n = ranked.size
            val cur = cursor.coerceIn(0, (n - 1).coerceAtLeast(0))
            when (intent) {
                KeyIntent.NavDown ->
                    if (pillSelected) pillSelected = false   // step off the pill, back to row 0
                    else cursor = moveCursor(cur, 1, n)
                KeyIntent.NavUp ->
                    // List-first (SID-182): one step above the top row sits the search pill.
                    if (!autoFocus && !pillSelected && cur == 0) pillSelected = true
                    else if (!pillSelected) cursor = moveCursor(cur, -1, n)
                KeyIntent.Select ->
                    if (pillSelected) {
                        // Selecting the pill is the deliberate act that summons search — the one
                        // way into it by key when the list opens quiet.
                        pillSelected = false
                        runCatching { focus.requestFocus() }
                    } else {
                        ranked.getOrNull(cur)?.let(currentLaunch)
                    }
                // Hold the launch key (SID-87/SID-98): raise the library strip for the selected hit —
                // the top one on a typing tile, where the cursor never moves (hold-Enter = manage top).
                is KeyIntent.ItemMenu ->
                    if (!pillSelected) ranked.getOrNull(cur)?.let { currentHold(it, selectedBounds) }
                else -> {}
            }
        }
    }
    SideEffect { onRegisterKeyHandler(keyHandler) }
    // Keep the selected row on screen as the cursor walks the list (flat while searching).
    LaunchedEffect(selectedKey, items) {
        val key = selectedKey ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it is AppRow && it.app.key == key }
        if (idx >= 0) runCatching { listState.animateScrollToItem(idx) }
    }

    // Leaving grouped mode — the toggle flipped to List, or a keystroke turned this into a search —
    // forgets the open bucket too, so coming back lands on the bubbles rather than re-opening what
    // you left. The gate above is what makes it disappear; this is what stops it returning.
    LaunchedEffect(grouped) { if (!grouped) onOpenCategoryChange(null) }

    // Back closes the bucket before the drawer. Registered inside the drawer and enabled only while a
    // bucket is open, so it wins over the host's always-enabled handler (registered earlier) exactly
    // then, and the host's chain — which cannot see this state — is left alone otherwise.
    BackHandler(enabled = openCategory != null) { onOpenCategoryChange(null) }

    // Same rule for the hidden area's two surfaces: Back steps out of them before the drawer closes.
    BackHandler(enabled = pinPromptOpen || hiddenOpen) {
        pinPromptOpen = false
        hiddenOpen = false
    }

    // The swipe-down dismissal reads this from inside a pointerInput that never restarts, so it would
    // otherwise capture the mode the drawer opened in and never see a flip.
    val groupedNow by rememberUpdatedState(grouped)

    // Snapshot at open, keyed on [visible]: the IME only reads privateImeOptions when it starts on the
    // field, so the sky's drift while searching can't reach it anyway — and re-deriving it per frame
    // would hand BasicTextField a new options object on every ambient tick. The drawer is now warmed
    // once rather than remounted per open, so this keys on the open transition to re-snapshot the
    // current ambient each summon. (Live drift = SID-18 v2, needs an InputConnection.)
    val imeOptions = remember(visible) {
        PlatformImeOptions(drawerImeOptions(fg = fg, accent = accent, backdrop = backdrop, separator = hint))
    }

    Box(modifier = modifier) {
        // An open bucket replaces the drawer's own content rather than floating over it: DECISIONS
        // says stacked overlays don't bleed — you only ever read one surface — and here the surface
        // underneath is a grid of bubbles the popover is the drill-down *into*, so leaving it showing
        // would be the loudest possible version of that bleed. The drawer itself stays open behind
        // the swap; dismissing the bucket returns to the bubbles. The hidden area's two surfaces
        // (password prompt, then the hidden popover) swap in the same way, and take precedence.
        if (pinPromptOpen) {
            HiddenPinPrompt(
                fg = fg,
                accent = accent,
                onVerify = onVerifyHiddenPin,
                onSuccess = {
                    pinPromptOpen = false
                    hiddenUnlocked = true
                    hiddenOpen = true
                },
                onDismiss = { pinPromptOpen = false },
            )
        } else if (hiddenOpen && hiddenApps.isNotEmpty()) {
            CategoryPopover(
                bucket = remember(hiddenApps) {
                    CategoryBucket(Category("hidden", tr("Hidden", "Versteckt"), "lock", builtIn = true), hiddenApps)
                },
                appsByKey = appsByKey,
                tuning = tuning,
                fg = fg,
                accent = accent,
                selectedIndex = null,
                onLaunch = onLaunch,
                onAppHold = onAppHold,
                onDismiss = { hiddenOpen = false },
            )
        } else if (openBucket != null) {
            CategoryPopover(
                bucket = openBucket,
                appsByKey = appsByKey,
                tuning = tuning,
                fg = fg,
                accent = accent,
                // The member the key cursor is on (SID-96), first-from-entry like the bubble grid;
                // null for touch / no tile, so a finger-only bucket shows no highlight.
                selectedIndex = if (galleryKeyNav) {
                    memberCursor.coerceIn(0, (openBucket.apps.size - 1).coerceAtLeast(0))
                } else null,
                onLaunch = onLaunch,
                onAppHold = onAppHold,
                onDismiss = { onOpenCategoryChange(null) },
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Sit the whole drawer above the SideType IME: the search field auto-focuses so the
                // keyboard is always up here, and without this the last list rows / bottom bubble row
                // hid behind it. imePadding collapses to nothing when the IME is down.
                .imePadding()
                .nestedScroll(pullToDismiss)
                .padding(top = 36.dp)
                .padding(start = 28.dp, end = 18.dp, bottom = 12.dp)
                .pointerInput(Unit) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onVerticalDrag = { _, dy -> acc += dy },
                        onDragEnd = {
                            // Read here, inside the gesture's own coroutine, never in composition:
                            // subscribing composition to a live scroll offset would recompose the
                            // whole drawer on every scrolled pixel.
                            val atTop = if (groupedNow) {
                                gridScroll.value == 0
                            } else {
                                listState.firstVisibleItemIndex == 0 &&
                                    listState.firstVisibleItemScrollOffset == 0
                            }
                            if (acc > 90f && atTop) onDismiss()
                        },
                        onDragCancel = { acc = 0f },
                    )
                },
        ) {
            // grabber
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .size(width = 40.dp, height = 5.dp)
                    .drawBehind { drawRect(fg.copy(alpha = 0.32f)) },
            )

            // "search" line: a real IME-backed field, styled by decorationBox to look exactly
            // like the old static query line (search glyph, live text, accent caret). The field
            // holds the query; the active IME disambiguates the compact tile's key pairs. Live
            // narrowing follows composing text — onValueChange fires on composition, not just on
            // commit — so the list filters as you type, before you accept a candidate.
            val placeholder = if (autoFocus) tr("Type to search", "Einfach lostippen") else tr("Tap to search", "Zum Suchen antippen")
            // The mode toggle rides the search line (SPEC §2b), so the field takes the remaining
            // width rather than the full row. Field and toggle are matching translucent-fg pills, so
            // the search line reads as one set of controls rather than an underlined field beside a box.
            Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 18.sp, fontFamily = Inter),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    platformImeOptions = imeOptions,
                ),
                keyboardActions = KeyboardActions(onSearch = {
                    if (query.isNotBlank()) ranked.firstOrNull()?.let(onLaunch)
                }),
                modifier = Modifier
                    .focusRequester(focus)
                    .weight(1f)
                    // A soft rounded search pill instead of a Material underline, so it reads as a set
                    // with the mode toggle beside it (the same translucent-fg cushion the toggle uses,
                    // per DECISIONS) rather than a bare underlined field next to a boxed control.
                    .clip(RoundedCornerShape(12.dp))
                    // The pill brightens to the rows' own selection fill while it is the key-nav
                    // target (SID-182) — same vocabulary, one extra stop on the walk.
                    .background(fg.copy(alpha = if (keyNavActive && pillSelected) 0.12f else 0.07f))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LineIcon(glyph = "search", color = fg.copy(alpha = 0.85f), size = 22.dp, strokeWidthUnits = 1.7f)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                // One line, no wrap: on a narrow field "Type to search" used to wrap
                                // to two lines and stretch the whole search row tall. The field is
                                // single-line anyway, so the hint must be too.
                                Text(
                                    placeholder,
                                    color = fg.copy(alpha = 0.6f),
                                    fontSize = 18.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            innerTextField()
                        }
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            BrowseModeToggle(mode = mode, onModeChange = onModeChange, fg = fg, accent = accent)
            }

            Spacer(Modifier.height(6.dp))

            // Kill the Android-12 overscroll STRETCH: it wraps the whole list in a per-frame
            // RenderEffect layer (expensive on this GPU) and causes the gap-stretch at the edges.
            // Providing null removes both — the list just stops firmly at its ends. It wraps the
            // bubbles too: the grid scrolls, so it would stretch exactly the same way.
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            if (grouped) {
                GroupedBrowse(
                    buckets = buckets,
                    fg = fg,
                    scrollState = gridScroll,
                    onOpen = { onOpenCategoryChange(it.category) },
                    modifier = Modifier.fillMaxSize(),
                    accent = accent,
                    // First bubble marked from entry (SID-96): the gallery arrives in D-pad mode, so the
                    // highlight shows whenever key-nav is on — no rock needed to reveal it.
                    selectedIndex = if (galleryKeyNav) {
                        bucketCursor.coerceIn(0, (buckets.size - 1).coerceAtLeast(0))
                    } else null,
                )
            } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 24.dp),
            ) {
                if (items.isEmpty()) {
                    item {
                        Text(
                            text = tr("No apps match “${query.trim()}”", "Keine Apps für „${query.trim()}“"),
                            color = fg,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
                        )
                    }
                }
                // stable keys + contentType let the list reuse row compositions as items fly
                // past instead of rebuilding them each frame — the main lever for fling perf
                items(
                    count = items.size,
                    key = { i ->
                        when (val it = items[i]) {
                            is SectionHeader -> "h_${it.letter}"
                            is AppRow -> it.app.key
                        }
                    },
                    contentType = { i -> items[i] is SectionHeader },
                ) { i ->
                    when (val it = items[i]) {
                        is SectionHeader -> Text(
                            text = it.letter,
                            color = fg,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.14.em,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 15.dp, bottom = 5.dp),
                        )
                        is AppRow -> DrawerAppRow(
                            app = it.app,
                            fg = fg,
                            accent = accent,
                            badged = isBadged(it.app, badgedPackages),
                            isFavourite = favouriteKeys.contains(it.app.key),
                            // The pill and a row are never both marked: while the pill is the
                            // target (SID-182), the cursor's row hands its highlight up.
                            selected = keyNavActive && !pillSelected && it.app.key == selectedKey,
                            onBounds = { b -> if (it.app.key == selectedKey) selectedBounds = b },
                            tuning = tuning,
                            onLaunch = { onLaunch(it.app) },
                            onHold = { bounds -> onAppHold(it.app, bounds) },
                            onPickup = { bounds -> onAppPickup(it.app, bounds) },
                            onDrag = onAppDrag,
                            onDrop = onAppDrop,
                        )
                    }
                }
                // The hidden area's one visible trace: a quiet lock row after Z. Browsing only —
                // while searching the list is ranked results and the row would be noise there.
                // Absent entirely while nothing is hidden.
                if (hiddenApps.isNotEmpty() && !searching) {
                    item(key = "hidden_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (hiddenUnlocked) hiddenOpen = true else pinPromptOpen = true
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LineIcon(glyph = "lock", color = fg, size = 24.dp, strokeWidthUnits = 1.6f)
                            Spacer(Modifier.width(15.dp))
                            Text(text = tr("Hidden", "Versteckt"), color = fg, fontSize = 18.sp)
                        }
                    }
                }
            }
            }
            }
        }
        }
        // One shared D-pad hint for the whole gallery (SID-96), pinned to the screen's bottom-end so it
        // holds the exact same spot whether you're on the bubble grid or drilled into a bucket — both
        // surfaces live in this Box, so aligning here (not inside the padded column) is what stops it
        // jumping between the two.
        if (grouped && galleryKeyNav) {
            DpadHint(
                fg = fg,
                accent = accent,
                pressed = pressedArm,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 10.dp),
            )
        }
    }
}

/**
 * A category bucket opened from a bubble (SID-23) — the folder-open overlay, reused rather than
 * forked. A bucket *is* a group of apps with a name and a glyph, which is precisely what
 * [FolderPopover] draws, so it arrives here as a synthetic [FolderItem] and inherits the header, the
 * staggered rise, the member dots, and tap-to-dismiss for free.
 *
 * The one thing not inherited is the action strip: holds are delegated to the host via [onAppHold],
 * the same callback the drawer's list rows use, so a held app raises the **library-app** strip (Icon /
 * Home / Category / Info) instead of the folder-member one — see [FolderPopover]'s `onMemberHold` for
 * why the folder's verbs are wrong here. That also means the miscategorisation fix §2b asks for is
 * the same Category cell the list already offers, reached the same way.
 *
 * Not offered here, deliberately: dragging an app out onto the home. [FolderPopover] declines it for
 * folders on the grounds that a member has no slot waiting for it, and the argument carries over
 * unchanged. Hold → Home still works, through the placement flow.
 */
@Composable
private fun CategoryPopover(
    bucket: CategoryBucket,
    appsByKey: Map<String, AppEntry>,
    tuning: GestureTuning,
    fg: Color,
    accent: Color,
    onLaunch: (AppEntry) -> Unit,
    onAppHold: (AppEntry, Rect) -> Unit,
    onDismiss: () -> Unit,
    selectedIndex: Int? = null,
) {
    // Rebuilt only when the bucket changes. FolderItem is a data class, so even an identical rebuild
    // compares equal and doesn't re-key the popover's own remembers.
    val folder = remember(bucket) {
        FolderItem(
            name = bucket.category.label,
            appKeys = bucket.apps.map { it.key },
            glyph = bucket.category.glyph,
        )
    }
    FolderPopover(
        folder = folder,
        appsByKey = appsByKey,
        glyphFor = { it.glyph },
        tuning = tuning,
        fg = fg,
        accent = accent,
        selectedIndex = selectedIndex,
        // Only ever the trailing carry chip's background, and a bucket has no reorder, so it is never
        // drawn — a sane value rather than a placeholder, legible if that ever stops being true.
        stripColor = fg.copy(alpha = 0.10f),
        onLaunch = onLaunch,
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxSize(),
        // Every hold delegates to the host, which raises the library-app strip (Icon / Home / Category
        // / Info) — the right verbs for an app seen in the library, not the folder-member ones (SID-114).
        onMemberHold = onAppHold,
        // The closed bubble peeks these apps' real icons; drilling in shows the same, so the popover
        // reads as the bucket opening rather than a different, line-art set of apps.
        realIcons = true,
    )
}

/**
 * The hidden area's gate: one password field, a quiet wrong-password line, and Cancel. Replaces the
 * drawer's content like every other surface swap here — no dialog chrome, no dimmed layer. The
 * field auto-focuses when a hardware tile is present (the drawer's own rule), so the password can
 * be typed with no extra tap; a wrong entry clears the field and says so, never locks anyone out.
 */
@Composable
private fun HiddenPinPrompt(
    fg: Color,
    accent: Color,
    onVerify: (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val hardwareKeys = configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    LaunchedEffect(Unit) { if (hardwareKeys) runCatching { focus.requestFocus() } }

    fun submit() {
        if (onVerify(pin)) onSuccess() else { wrong = true; pin = "" }
    }

    val rule = fg.copy(alpha = 0.22f)
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 28.dp, top = 48.dp, bottom = 44.dp),
    ) {
        Text(
            tr("Hidden apps", "Versteckte Apps"),
            color = fg,
            fontFamily = Inter,
            fontSize = 22.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(14.dp))
        BasicTextField(
            value = pin,
            onValueChange = { pin = it; wrong = false },
            singleLine = true,
            textStyle = TextStyle(color = fg, fontSize = 18.sp, fontFamily = Inter),
            cursorBrush = SolidColor(accent),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .focusRequester(focus)
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 2.dp)
                .drawBehind {
                    val y = size.height
                    drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
                },
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(glyph = "lock", color = fg, size = 20.dp, strokeWidthUnits = 1.7f)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (pin.isEmpty()) {
                            Text(
                                tr("Password", "Passwort"),
                                color = fg.copy(alpha = 0.6f),
                                fontFamily = Inter,
                                fontSize = 18.sp,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
        if (wrong) {
            Spacer(Modifier.height(8.dp))
            Text(
                tr("Wrong password", "Falsches Passwort"),
                color = accent,
                fontFamily = Inter,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            tr("Cancel", "Abbrechen"),
            color = accent,
            fontFamily = Inter,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(vertical = 6.dp, horizontal = 2.dp),
        )
    }
}

@Composable
private fun DrawerAppRow(
    app: AppEntry,
    fg: Color,
    accent: Color,
    badged: Boolean,
    isFavourite: Boolean,
    tuning: GestureTuning,
    onLaunch: () -> Unit,
    onHold: (Rect) -> Unit,
    onPickup: (Rect) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    selected: Boolean = false,
    onBounds: (Rect) -> Unit = {},
) {
    // The strip anchors its Y beside the row that raised it, and a carry lifted off this row is
    // seeded from it, so the row reports where it sits. Root coords, because both are siblings of
    // the drawer at the top of the scene.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            // The key-nav result cursor (SID-84): a quiet fg-tinted fill, only ever set by key.
            .then(if (selected) Modifier.background(fg.copy(alpha = 0.12f)) else Modifier)
            .onGloballyPositioned { bounds = it.boundsInRoot(); onBounds(bounds) }
            .itemPress(
                tuning = tuning,
                onTap = onLaunch,
                onHold = { onHold(bounds) },
                // An app already on the home is not arriving anywhere, so it never lifts: the hold's
                // menu simply stays up instead. This is the refusal, and it happens before a single
                // target lights — the alternative is a carry that has to no-op on release, or a
                // second row launching the same app. Moving a placed app is done from the home
                // itself; lifting one out of a folder is folder lifecycle (SID-31).
                onPickup = { if (!isFavourite) onPickup(bounds) },
                // itemPress reports positions local to this row; the drop layer thinks in root
                // coords, and this row already knows where it starts. Both are inert until something
                // is actually in hand, which is what lets the pickup above decline on its own.
                onDrag = { p -> onDrag(bounds.topLeft + p) },
                onDrop = onDrop,
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(
            glyph = app.glyph,
            // In-home apps render like any other library row — no accent mark (Oliver's call). The
            // row still declines a pickup (it's already placed); it just no longer says so in colour.
            color = fg,
            size = 24.dp,
            strokeWidthUnits = 1.6f,
            badge = badgeTint(badged, accent),
        )
        Spacer(Modifier.width(15.dp))
        Text(
            text = app.label,
            color = fg,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Rank matches so what the user is typing leads: exact prefix first, then a word inside
 * the label that starts with the term, then any substring — alphabetical within each tier.
 */
internal fun rankApps(apps: List<AppEntry>, query: String): List<AppEntry> {
    val term = query.trim().lowercase()
    if (term.isEmpty()) return apps  // already alphabetical from the repository
    return apps.mapNotNull { app ->
        val label = app.label.lowercase()
        val tier = when {
            label.startsWith(term) -> 0
            label.split(' ', '-', '_', '.', '/').any { it.startsWith(term) } -> 1
            label.contains(term) -> 2
            else -> -1
        }
        if (tier < 0) null else Triple(tier, label, app)
    }.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
}

private fun toItems(ranked: List<AppEntry>, query: String): List<DrawerItem> {
    if (ranked.isEmpty()) return emptyList()
    // while searching, results are relevance-ranked → a flat list, no A–Z headers
    if (query.trim().isNotEmpty()) return ranked.map { AppRow(it) }
    // browsing: quiet A–Z section letters
    val out = ArrayList<DrawerItem>()
    var last: String? = null
    for (a in ranked) {
        val letter = a.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
        if (letter != last) {
            out.add(SectionHeader(letter))
            last = letter
        }
        out.add(AppRow(a))
    }
    return out
}
