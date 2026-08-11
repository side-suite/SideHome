package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.i18n.tr
import fi.palonkorpi.sidehome.settings.SettingsStore

/**
 * The one-time wallpaper consent (SID-180).
 *
 * Overwriting the system wallpaper is the one destructive act in the launcher — `setBitmap`
 * replaces whatever the user had, and there is no reading it back to restore on uninstall. So the
 * sync never simply happens: this modal asks once, with both slots pre-checked because matching is
 * the point of the feature, and *nothing is written to either slot before Approve*. Unchecking a
 * toggle leaves that slot untouched forever (until the settings screen says otherwise).
 *
 * Answering — either way — is what's write-once, mirroring the coach: "Not now" records both
 * slots off and never asks again, because a modal that re-asks is a nag, and the settings screen
 * is the honest place for a changed mind.
 */
@Stable
internal class WallpaperConsentState(
    seen: Boolean,
    private val commit: (home: Boolean, lock: Boolean) -> Unit,
) {
    var visible by mutableStateOf(!seen)
        private set

    /** Approve with the toggles as left; idempotent like the coach's dismiss. */
    fun approve(home: Boolean, lock: Boolean) {
        if (!visible) return
        visible = false
        commit(home, lock)
    }

    /** "Not now": both slots stay untouched, and the question doesn't come back. */
    fun dismiss() {
        if (!visible) return
        visible = false
        commit(false, false)
    }
}

/** Bind the consent to the settings store; the flag is read once, like the coach's. */
@Composable
internal fun rememberWallpaperConsent(settings: SettingsStore): WallpaperConsentState =
    remember(settings) {
        WallpaperConsentState(settings.wallpaperConsentSeen) { home, lock ->
            settings.wallpaperSyncHome = home
            settings.wallpaperSyncLock = lock
            settings.wallpaperConsentSeen = true
        }
    }

/**
 * The modal itself — [ConfirmModal]'s grammar (same float, same panel, same button row) grown two
 * toggle rows. Tap-outside answers "Not now", the safe answer, for the same reason ConfirmModal's
 * outside-tap answers Cancel.
 */
@Composable
internal fun WallpaperConsentModal(
    fg: Color,
    accent: Color,
    panelColor: Color,
    onApprove: (home: Boolean, lock: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val enter = rememberOverlayEnter(riseDp = 10f)
    var home by remember { mutableStateOf(true) }
    var lock by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .then(enter)
                .padding(horizontal = 24.dp)
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(panelColor)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(start = 16.dp, end = 10.dp, top = 15.dp, bottom = 9.dp),
        ) {
            Text(
                text = tr("Match the wallpaper to the theme?", "Hintergrund ans Design anpassen?"),
                color = fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tr(
                    "Closing an app can flash the wallpaper behind SideHome. " +
                        "Approving lets SideHome keep it set to the current theme.",
                    "Beim Schließen einer App kann kurz das alte Hintergrundbild aufblitzen. " +
                        "Mit deiner Zustimmung hält SideHome es passend zum aktuellen Design.",
                ),
                color = fg,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(10.dp))
            ConsentToggle(label = tr("Home wallpaper", "Startbildschirm"), on = home, onToggle = { home = it }, fg = fg)
            Spacer(Modifier.height(4.dp))
            ConsentToggle(label = tr("Lock screen wallpaper", "Sperrbildschirm"), on = lock, onToggle = { lock = it }, fg = fg)
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tr("Not now", "Später"),
                    color = fg,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = tr("Approve", "Erlauben"),
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onApprove(home, lock) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * The chooser's pill switch, re-rolled for the modal — duplicated rather than exported for the
 * same reason WeatherSettings duplicates Segmented: a shared micro-widget couples two surfaces
 * that only coincidentally look alike today.
 *
 * Unlike the chooser's (which always inks light — it sits on black), this one lives on a themed
 * panel where [fg] can be near-black, so the knob derives its colour from the ink instead of
 * hardcoding it: a black knob on a black-ink track was one solid black pill (v1.1 device pass).
 */
@Composable
private fun ConsentToggle(label: String, on: Boolean, onToggle: (Boolean) -> Unit, fg: Color) {
    // The on-track is solid ink, so the on-knob is the ink's own opposite; the off-track is a
    // faint ink wash, so the off-knob is solid ink. Legible in both ink directions.
    val knobOn = if (fg.luminance() < 0.5f) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!on) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (on) fg else fg.copy(alpha = 0.14f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .offset(x = if (on) 18.dp else 0.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (on) knobOn else fg),
            )
        }
    }
}
