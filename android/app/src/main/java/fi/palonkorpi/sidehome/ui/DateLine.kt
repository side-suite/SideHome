package fi.palonkorpi.sidehome.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle as UiTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import java.time.DayOfWeek
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * The home's date, longest form first: "Wednesday, 30 September", then "Wednesday, 30 Sep", then
 * "Wed, 30 Sep". The date shares its row with the weather glance (SID-64), and on the 480px home
 * the longest English pair — never mind German or Finnish — leaves the glance too thin to hold its
 * number, so the temperature wrapped a digit per line and the taller row pushed the last favourite
 * off screen. The home steps down this list until the date fits beside the glance instead.
 *
 * Month shortens before weekday: the day name is what the date is glanced at for. Duplicates are
 * dropped (a locale whose short and full forms coincide), so every entry is a real step down.
 */
fun homeDateLabels(dow: DayOfWeek, dayOfMonth: Int, month: Month, locale: Locale): List<String> {
    fun day(style: TextStyle) = dow.getDisplayName(style, locale).replaceFirstChar { it.titlecase(locale) }
    val fullMonth = month.getDisplayName(TextStyle.FULL, locale)
    val shortMonth = month.getDisplayName(TextStyle.SHORT, locale)
    return listOf(
        "${day(TextStyle.FULL)}, $dayOfMonth $fullMonth",
        "${day(TextStyle.FULL)}, $dayOfMonth $shortMonth",
        "${day(TextStyle.SHORT)}, $dayOfMonth $shortMonth",
    ).distinct()
}

/**
 * The first label that [fits], or the last (shortest) when none does — the Text then ellipsizes
 * rather than wraps, so the row stays one line whatever the locale. Pure, so the step-down order is
 * unit-testable without a text measurer.
 */
fun pickDateLabel(labels: List<String>, fits: (String) -> Boolean): String =
    labels.firstOrNull(fits) ?: labels.last()

/**
 * The date Text, choosing its own length from the width it's given. In the home's date row it
 * carries `weight(1f, fill = false)`, which makes the Row measure the weather glance first and hand
 * the date only what's left — so the glance always gets its full width and the date is what yields.
 */
@Composable
internal fun HomeDate(labels: List<String>, style: UiTextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val maxPx = with(density) { maxWidth.roundToPx() }
        val label = remember(labels, style, maxPx) {
            pickDateLabel(labels) { measurer.measure(it, style, maxLines = 1).size.width <= maxPx }
        }
        Text(
            text = label,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
