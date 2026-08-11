package fi.palonkorpi.sidehome.settings

import fi.palonkorpi.sidehome.i18n.tr

/** How the home clock renders the time of day. */
enum class ClockFormat { H24, H12 }

/**
 * The status line of the notification-dots row (SID-47), given whether the OS says notification
 * access is granted.
 *
 * Pure, and the only branch in a row that is otherwise framework glue — worth pinning because the
 * two strings are a *promise*: `README.md` and `docs/notification-dots-announcement.md` both tell
 * the reader the row "reads **On**" once granted, and both rest on there being exactly two states.
 * There is deliberately no third, app-side "granted but disabled" state: the system grant is the
 * switch, so [granted] alone decides what the row says.
 */
fun notificationAccessStatus(granted: Boolean): String =
    if (granted) tr("On", "An") else tr("Off — tap to enable", "Aus — antippen")

/**
 * Render an hour/minute pair as the home clock string. Pure — it takes no platform
 * time source and no locale — so the display is deterministic and unit-testable.
 *
 *  - [ClockFormat.H24] → zero-padded `00:00` … `23:59`.
 *  - [ClockFormat.H12] → `12:00 am` … `11:59 pm`, hour un-padded (iOS-style).
 *
 * Inputs outside the usual ranges are wrapped rather than rejected, so a caller
 * can pass raw clock fields without pre-validating.
 */
fun formatClock(hour: Int, minute: Int, format: ClockFormat): String {
    val h = ((hour % 24) + 24) % 24
    val m = ((minute % 60) + 60) % 60
    return when (format) {
        ClockFormat.H24 -> "%02d:%02d".format(h, m)
        ClockFormat.H12 -> {
            val suffix = if (h < 12) "am" else "pm"
            val h12 = (h % 12).let { if (it == 0) 12 else it }
            "%d:%02d %s".format(h12, m, suffix)
        }
    }
}
