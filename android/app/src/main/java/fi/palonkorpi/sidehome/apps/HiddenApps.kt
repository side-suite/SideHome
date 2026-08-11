package fi.palonkorpi.sidehome.apps

import java.security.MessageDigest

/**
 * The hidden area's password hash — pure, so the round-trip (set → verify) is unit-testable
 * without Android prefs. SHA-256 over a fixed app-local salt: this is a family lock against a
 * curious eye on the launcher, not a vault against someone with the phone's filesystem (root can
 * read the prefs anyway). The salt only keeps the hash from being a bare rainbow-table lookup.
 */
fun hashHiddenPin(pin: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest("sidehome:$pin".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** The minimum password length the settings screen accepts. */
const val MIN_HIDDEN_PIN_LENGTH = 4
