package fi.palonkorpi.sidehome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.palonkorpi.sidehome.apps.AppEntry
import fi.palonkorpi.sidehome.apps.AppRepository
import fi.palonkorpi.sidehome.apps.MIN_HIDDEN_PIN_LENGTH
import fi.palonkorpi.sidehome.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Versteckte Apps — the settings side (the drawer side lives in `Drawer.kt`). Three sub-surfaces,
 * each replacing the settings column the way the city composer does: setting/changing the password,
 * the password gate in front of the manage list, and the manage list itself (every app, tap to
 * hide/unhide). Hiding is deliberately settings-only: the library has no "hide" verb, so a curious
 * hand on the drawer can't stumble into hiding — or finding — anything without the password.
 */
enum class HiddenSub { PIN, GATE, MANAGE }

/** The settings column's section: two rows and an explainer, shaped like the weather section's. */
@Composable
fun HiddenAppsSection(
    repo: AppRepository,
    fg: Color,
    accent: Color,
    onOpen: (HiddenSub) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Plain reads, refreshed by recomposition: returning from a sub-surface flips the host's state,
    // which recomposes this section, which re-reads. No listener plumbing needed for two numbers.
    val hasPin = repo.hasHiddenPin()
    val count = repo.hiddenAppKeys().size

    Column(modifier) {
        Text(tr("Hidden apps", "Versteckte Apps"), color = fg, fontWeight = FontWeight.Light, fontSize = 22.sp)
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpen(HiddenSub.PIN) }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("Password", "Passwort"), color = fg, fontSize = 17.sp)
            Text(
                if (hasPin) tr("Set", "Festgelegt") else tr("Not set — tap to choose", "Nicht festgelegt — antippen"),
                color = if (hasPin) accent else fg.copy(alpha = 0.7f),
                fontSize = 15.sp,
                fontWeight = if (hasPin) FontWeight.SemiBold else FontWeight.Normal,
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                // No password yet → the tap lands on password setup first: a hidden area without a
                // lock would just be a second list.
                .clickable { onOpen(if (hasPin) HiddenSub.GATE else HiddenSub.PIN) }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr("Manage apps", "Apps verwalten"), color = fg, fontSize = 17.sp)
            Text(
                if (count == 0) tr("None hidden", "Keine versteckt") else tr("$count hidden", "$count versteckt"),
                color = if (count > 0) accent else fg.copy(alpha = 0.7f),
                fontSize = 15.sp,
                fontWeight = if (count > 0) FontWeight.SemiBold else FontWeight.Normal,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            tr(
                "Hidden apps leave the library, the search and the home. They sit behind the " +
                    "“Hidden” row at the end of the app list — only the password opens it.",
                "Versteckte Apps verschwinden aus Bibliothek, Suche und Startseite. Sie stecken " +
                    "hinter der Zeile „Versteckt“ am Ende der App-Liste — erst das Passwort " +
                    "öffnet sie.",
            ),
            color = fg.copy(alpha = 0.85f),
            fontSize = 13.sp,
            lineHeight = 17.sp,
        )
    }
}

/**
 * The full-screen sub-surfaces. The host swaps this in for its settings column while [sub] is
 * non-null and hands us the navigation between the three ([onChange]) plus the way back ([onClose],
 * also wired to Back).
 */
@Composable
fun HiddenAppsSubScreen(
    repo: AppRepository,
    sub: HiddenSub,
    fg: Color,
    accent: Color,
    onChange: (HiddenSub) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        when (sub) {
            HiddenSub.PIN -> PinSetup(repo, fg, accent, onDone = onClose)
            HiddenSub.GATE -> PinGate(repo, fg, accent, onUnlocked = { onChange(HiddenSub.MANAGE) }, onCancel = onClose)
            HiddenSub.MANAGE -> ManageList(repo, fg, accent, onDone = onClose)
        }
    }
}

/** Set or change the password: the old one first when there is one, then the new one twice. */
@Composable
private fun PinSetup(repo: AppRepository, fg: Color, accent: Color, onDone: () -> Unit) {
    val hasPin = remember { repo.hasHiddenPin() }
    var current by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        error = when {
            hasPin && !repo.verifyHiddenPin(current) -> tr("The current password is wrong.", "Das aktuelle Passwort stimmt nicht.")
            new1.length < MIN_HIDDEN_PIN_LENGTH -> tr("At least $MIN_HIDDEN_PIN_LENGTH characters.", "Mindestens $MIN_HIDDEN_PIN_LENGTH Zeichen.")
            new1 != new2 -> tr("The repetition doesn’t match.", "Die Wiederholung stimmt nicht überein.")
            else -> null
        }
        if (error == null) {
            repo.setHiddenPin(new1)
            onDone()
        }
    }

    Text(
        if (hasPin) tr("Change password", "Passwort ändern") else tr("Set a password", "Passwort festlegen"),
        color = fg,
        fontWeight = FontWeight.Light,
        fontSize = 26.sp,
    )
    Spacer(Modifier.height(20.dp))
    if (hasPin) {
        PasswordField(value = current, onValue = { current = it; error = null }, hint = tr("Current password", "Aktuelles Passwort"), fg = fg, accent = accent)
        Spacer(Modifier.height(14.dp))
    }
    PasswordField(value = new1, onValue = { new1 = it; error = null }, hint = tr("New password", "Neues Passwort"), fg = fg, accent = accent)
    Spacer(Modifier.height(14.dp))
    PasswordField(
        value = new2,
        onValue = { new2 = it; error = null },
        hint = tr("Repeat new password", "Neues Passwort wiederholen"),
        fg = fg,
        accent = accent,
        imeAction = ImeAction.Done,
        onSubmit = { save() },
    )
    error?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(22.dp))
    ActionLine(tr("Save", "Speichern"), accent) { save() }
    Spacer(Modifier.height(4.dp))
    ActionLine(tr("Cancel", "Abbrechen"), accent, onDone)
}

/** The gate in front of the manage list: one password, no retry limit, never an error dialog. */
@Composable
private fun PinGate(
    repo: AppRepository,
    fg: Color,
    accent: Color,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    fun submit() {
        if (repo.verifyHiddenPin(pin)) onUnlocked() else { wrong = true; pin = "" }
    }

    Text(tr("Hidden apps", "Versteckte Apps"), color = fg, fontWeight = FontWeight.Light, fontSize = 26.sp)
    Spacer(Modifier.height(20.dp))
    PasswordField(
        value = pin,
        onValue = { pin = it; wrong = false },
        hint = tr("Password", "Passwort"),
        fg = fg,
        accent = accent,
        imeAction = ImeAction.Done,
        onSubmit = { submit() },
    )
    if (wrong) {
        Spacer(Modifier.height(10.dp))
        Text(tr("Wrong password", "Falsches Passwort"), color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(22.dp))
    ActionLine(tr("Continue", "Weiter"), accent) { submit() }
    Spacer(Modifier.height(4.dp))
    ActionLine(tr("Cancel", "Abbrechen"), accent, onCancel)
}

/** Every installed app, tap to hide or unhide. Hiding also clears the app off the home (repo rule). */
@Composable
private fun ManageList(repo: AppRepository, fg: Color, accent: Color, onDone: () -> Unit) {
    var all by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var hidden by remember { mutableStateOf(repo.hiddenAppKeys()) }
    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) { repo.loadApps() }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("Hide apps", "Apps verstecken"), color = fg, fontWeight = FontWeight.Light, fontSize = 26.sp)
        ActionLine(tr("Done", "Fertig"), accent, onDone)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        tr("Tapping hides an app or brings it back.", "Antippen versteckt eine App oder holt sie zurück."),
        color = fg.copy(alpha = 0.85f),
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )
    Spacer(Modifier.height(10.dp))
    LazyColumn(Modifier.fillMaxSize()) {
        items(count = all.size, key = { all[it].key }) { i ->
            val app = all[i]
            val isHidden = app.key in hidden
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        repo.setHidden(app.key, !isHidden)
                        hidden = repo.hiddenAppKeys()
                    }
                    .padding(horizontal = 8.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIcon(glyph = app.glyph, color = fg, size = 22.dp, strokeWidthUnits = 1.6f)
                Spacer(Modifier.width(14.dp))
                Text(app.label, color = fg, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (isHidden) {
                    LineIcon(glyph = "lock", color = accent, size = 18.dp, strokeWidthUnits = 1.7f)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Hidden", "Versteckt"), color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** One password field in the settings idiom: underline rule, lock glyph, accent caret. */
@Composable
private fun PasswordField(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    fg: Color,
    accent: Color,
    imeAction: ImeAction = ImeAction.Next,
    onSubmit: () -> Unit = {},
) {
    val rule = fg.copy(alpha = 0.22f)
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(color = fg, fontSize = 17.sp, fontFamily = Inter),
        cursorBrush = SolidColor(accent),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .drawBehind {
                val y = size.height
                drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
            },
        decorationBox = { innerTextField ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(glyph = "lock", color = fg, size = 18.dp, strokeWidthUnits = 1.7f)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(hint, color = fg.copy(alpha = 0.6f), fontSize = 17.sp)
                    }
                    innerTextField()
                }
            }
        },
    )
}

/** An accent action line, the settings screens' quiet button. */
@Composable
private fun ActionLine(label: String, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        color = accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
    )
}
