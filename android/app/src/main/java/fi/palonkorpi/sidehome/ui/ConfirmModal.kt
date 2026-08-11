package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.i18n.tr

/**
 * A destructive act that wants an answer before it runs (SPEC §1). A small centred panel on the
 * untouched sky, in [RenamePopover]'s grammar — the same lightweight float, raised the same way, so
 * a question and a rename read as the same kind of moment rather than two different systems.
 *
 * The one thing this is *not* is a refusal. It sits between a legitimate act and its commit: by the
 * time it is on screen the drop target has lit its accept ring and the strip cell has run, because
 * both of those were honest — the act is allowed, it is just expensive enough to be worth a beat.
 * That is why the resolver knows nothing about it and the caller raises it from the commit point.
 */
@Composable
fun ConfirmModal(
    message: String,
    confirmLabel: String,
    fg: Color,
    accent: Color,
    panelColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val enter = rememberOverlayEnter(riseDp = 10f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tapping outside answers Cancel. The answer you can give by accident has to be the
            // safe one — this layer exists precisely because the act underneath it isn't cheap.
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
            Text(text = message, color = fg, fontSize = 16.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Both answers stay full-strength fg (DECISIONS): neither is a state that can't
                // run, so neither is dimmed. Accent marks the act the question is about — the
                // answer that does something — not the one being recommended.
                Text(
                    text = tr("Cancel", "Abbrechen"),
                    color = fg,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = confirmLabel,
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onConfirm() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * A folder removal waiting for an answer: which slot, and the name the question is asking about.
 *
 * Held as data rather than as a captured lambda for the same reason the action strip holds an index:
 * the question stays on screen while the home can change underneath it (a reload lands, an app
 * uninstalls), so the commit re-reads the slot instead of trusting a closure over a list that has
 * since moved on. [name] is what makes that re-read meaningful — it is the question the user
 * actually answered.
 */
internal data class RemoveFolderPrompt(val index: Int, val name: String)

/**
 * How the folder-removal question is worded — in one place, because the two paths that ask it (the
 * strip's Remove cell and a folder dropped on the remove pill) are the same act and must not sound
 * like different ones.
 *
 * The reassurance is the whole point of the copy: what "remove" costs here is the *grouping*, never
 * the apps — they are all still in the library. Without that sentence the question reads as though
 * it might uninstall four things, and the honest answer to it is much scarier than the act.
 */
fun removeFolderMessage(name: String): String =
    tr(
        "Remove folder “$name”? The apps stay in your library.",
        "Ordner „$name“ entfernen? Die Apps bleiben in deiner Bibliothek.",
    )
