package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.i18n.tr
import fi.palonkorpi.sidehome.settings.SettingsStore

/**
 * The one-time first-run gesture coach (SPEC §7, SID-27). The gestures are all invisible swipes on a
 * bare home, and only one of them ([PageDots]) gets a permanent mark; this is the single quiet pass
 * that names the other three, once, on a fresh install — and then never again. Idle home stays
 * pristine afterwards, which is the whole reason the coach is a *moment* rather than any kind of
 * persistent chrome.
 *
 * It is not the dots' bigger sibling and must not grow into one. The dots report a position across
 * three places; this teaches four gestures and then leaves for ever. They share the §7 subject and
 * nothing else — no shared geometry, no shared vocabulary, no "coach mode" the dots participate in.
 *
 * **Not a full-screen cross of arrows over the live home.** That was the first shape, and it can't
 * work here: the marks would land on the clock and the favourites, and the recede-by-blur rule
 * (DECISIONS) that would normally get the home out from under an overlay isn't ours to invoke — the
 * home's recede belongs to its host. So the coach is a small centred panel in [ConfirmModal]'s
 * grammar instead: the established way this launcher floats a lightweight thing over an untouched
 * sky and a live home. Four rows, each an arrow and what it gets you. Seen once; built accordingly.
 */

/** The direction chevrons, as rotations of the one right-pointing [LineIcon] "chevron" glyph. */
private const val CHEVRON_UP = -90f
private const val CHEVRON_DOWN = 90f
private const val CHEVRON_LEFT = 180f
private const val CHEVRON_RIGHT = 0f

/**
 * One row of the coach: an arrow and its destination.
 *
 * The rule the rows share, and it is uniform on purpose: **the arrow is the direction your finger
 * moves, and the label is what arrives.** Swipe up → apps; swipe left → the weather glance. It is
 * worth naming because the left row reads, at a glance, backwards against the dots: the dots put the
 * weather pane on the *right* (it is page 2, and it genuinely lives over there), while the row that
 * summons it points *left*. Both are true — a finger goes left to pull the right-hand page in, the
 * same way every carousel that has ever existed works — and picking either convention makes the
 * other look wrong. Finger-direction wins because that is what the user has to *do*: the coach is
 * dismissed by doing it, and the dots are still there afterwards to say where they ended up.
 */
internal data class CoachLine(
    val rotation: Float,
    val label: String,
    /** A short text lead instead of the chevron — the 1–9 quick-launch row (SID-183), whose
     *  "direction" is a keypress, not a swipe. Null = the chevron at [rotation]. */
    val badge: String? = null,
)

// Spotlight (swipe-down search) and Past both ship disabled for v1, so the coach teaches only the
// gestures that actually do something: swipe up for apps, and — while the weather exists (SID-181)
// — swipe left for the weather glance. A function, not a constant list, because that second row is
// now conditional; pure so the line set is testable without layout.
internal fun coachLines(weatherEnabled: Boolean, quickLaunch: Boolean = false): List<CoachLine> =
    buildList {
        add(CoachLine(CHEVRON_UP, tr("apps", "Apps")))
        if (weatherEnabled) add(CoachLine(CHEVRON_LEFT, tr("weather", "Wetter")))
        // The 1–9 badges read as mystery counts until someone says otherwise (SID-183) — one row,
        // only when a hardware tile is fitted, because on touch alone the badges never show.
        if (quickLaunch) add(CoachLine(0f, tr("apps by number", "Apps per Ziffer"), badge = "1–9"))
    }

/**
 * Whether the coach is up, and the one-way trip out of it.
 *
 * Takes the flag as a value and the write as a lambda rather than taking a [SettingsStore]: the
 * whole of what this issue can get wrong — showing twice, showing never, burning the one pass on an
 * install that never saw it — lives in these six lines, and this way they are unit-testable with no
 * Android at all. [rememberCoach] does the binding.
 *
 * **Dismissal is what persists, never the mere showing.** An install that raised the coach and was
 * then interrupted before the user touched it — a call lands, the screen sleeps — has not been
 * coached, and writing the flag at show-time would spend the single pass on nobody. So the coach
 * comes back on the next launch and keeps coming back until it is acknowledged, which is both the
 * kinder reading and the honest one: the acceptance criterion is that a *dismissed* coach never
 * returns, and this is the only way to mean it.
 */
@Stable
internal class CoachState(seen: Boolean, private val markSeen: () -> Unit) {

    var visible by mutableStateOf(!seen)
        private set

    /** Idempotent: a second dismissal is not a second write. Nothing ever sets [visible] back. */
    fun dismiss() {
        if (!visible) return
        visible = false
        markSeen()
    }
}

/**
 * Bind the coach to the persisted flag. The flag is read once, at first composition — it can only be
 * written by [CoachState.dismiss] and only ever one way, so there is nothing to observe and no
 * reason for this to be a `State` on the store.
 */
@Composable
internal fun rememberCoach(settings: SettingsStore): CoachState =
    remember(settings) { CoachState(settings.coachSeen) { settings.coachSeen = true } }

/**
 * The coach's one pass. [panelColor] is the host's sheet tone (`sheetTone(mood)`), so it floats on
 * the sky exactly as the §1 strip and the confirm panel do; [fg] is the adaptive foreground, full
 * strength throughout — no muted copy anywhere (DECISIONS), the hint included.
 *
 * **Any touch dismisses it, not just a tap.** The four things it is teaching are swipes, so a tap
 * detector would leave the user swiping at a coach that ignores exactly the gesture it just asked
 * for. Taking the first pointer-down and consuming it means the coach is gone the instant a finger
 * lands, whatever that finger went on to do — and consuming keeps that first touch from also
 * reaching the arbiter underneath, so the swipe that dismisses the coach does not simultaneously
 * page the sky out from under the panel that is still fading away.
 */
@Composable
internal fun FirstRunCoach(
    fg: Color,
    panelColor: Color,
    onDismiss: () -> Unit,
    weatherEnabled: Boolean = true,
    quickLaunch: Boolean = false,
) {
    val enter = rememberOverlayEnter(riseDp = 10f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    onDismiss()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .then(enter)
                .padding(horizontal = 24.dp)
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(panelColor)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = tr("Swipe to get around", "Wischen, um dich umzusehen"),
                color = fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(14.dp))
            coachLines(weatherEnabled, quickLaunch).forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (line.badge != null) {
                        Text(
                            text = line.badge,
                            color = fg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        LineIcon(
                            glyph = "chevron",
                            color = fg,
                            size = 15.dp,
                            strokeWidthUnits = 1.7f,
                            modifier = Modifier.rotate(line.rotation),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(text = line.label, color = fg, fontSize = 15.sp)
                }
                Spacer(Modifier.height(11.dp))
            }
            Spacer(Modifier.height(3.dp))
            Text(text = tr("Touch anywhere to begin", "Tippe irgendwo hin, um zu starten"), color = fg, fontSize = 12.sp)
        }
    }
}
