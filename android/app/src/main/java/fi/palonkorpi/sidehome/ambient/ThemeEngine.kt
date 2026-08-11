package fi.palonkorpi.sidehome.ambient

import fi.palonkorpi.sidehome.color.Lch
import fi.palonkorpi.sidehome.color.hexToLch
import fi.palonkorpi.sidehome.i18n.tr

/**
 * The home's four v1 themes (SPEC.md §6). Every theme is a *parameter variant of the one ambient
 * renderer* — none forks the render path. A theme resolves to three pure facts the renderer
 * honours: the effective mood ([themeMood]), whether the solar time-of-day progression still
 * drifts ([driftEnabled]), and whether the ambient is painted edge-only over a dark centre
 * ([edgeOnly]).
 *
 *  1. [AMBIENT]    — the live solar day-cycle. The default. Drifts.
 *  2. [FULL_LIGHT] — a pinned near-white mood; solar drift OFF. Carries the living texture (noise +
 *                    drifting-field gradient + vignette) over a clean white.
 *  3. [FULL_DARK]  — a pinned dark-tinted mood; solar drift OFF. Same living texture; never #000000.
 *  4. [EDGE_DARK]  — the solar ambient kept as an edge vignette fading to a dark centre. Still
 *                    drifts (edge-only). Aesthetic-only on this LCD panel — not a battery feature.
 *  5. [CUSTOM]     — a user-chosen colour: a seed hue plus a pastel↔saturated vividness, resolved to a
 *                    fixed, legible tone by [customBaseColor], so the home simply *is* the colour picked
 *                    — it does not darken with the day. Time-of-day dimming is opt-in ([customDrift]):
 *                    when on, the chosen colour is dimmed toward a clean dark of the same hue as the sky
 *                    falls (lightness and chroma scaled down together — see [themeMood]). The tone is
 *                    engine-bounded so no seed can go illegible or muddy (DECISIONS: text/accent stay
 *                    auto-derived from the resulting mood).
 *
 *  6. [PHOTO]      — a user image as the backdrop (SID-43). The mood swaps source entirely: it is
 *                    *extracted from the image* (average OKLab of the picked photo), frozen all day
 *                    like Full-light/dark — a photo is a fixed backdrop. Ink and accent still derive
 *                    from the mood, so §6's "everything auto-derived" rule holds; the backdrop itself
 *                    is the pre-baked bitmap pair the ui layer paints (see PhotoBake), not the sky.
 */
enum class Theme { AMBIENT, FULL_LIGHT, FULL_DARK, EDGE_DARK, CUSTOM, PHOTO }

/** The theme's human label, shown under its tile in the zoom-out chooser. */
val Theme.displayName: String
    get() = when (this) {
        Theme.AMBIENT -> tr("Ambient", "Ambient")
        Theme.FULL_LIGHT -> tr("Full light", "Hell")
        Theme.FULL_DARK -> tr("Full dark", "Dunkel")
        Theme.EDGE_DARK -> tr("Edge", "Edge")
        Theme.CUSTOM -> tr("Custom", "Eigenes")
        Theme.PHOTO -> tr("Photo", "Foto")
    }

/** The Custom theme's default seed hue (degrees), until the user picks one. A warm amber, clearly
 *  apart from the ambient's cool daytime blue so "Custom" reads as a distinct choice on first sight. */
const val DEFAULT_CUSTOM_HUE_DEG = 30f

/** A user seed hue in degrees (0–360) as an OKLCH hue in radians. [Lch.h] is radians (straight off
 *  atan2), so a degree value must be converted or it wraps silently — the SID-42 keyframe hazard. */
fun customHueRadians(hueDeg: Float): Double = Math.toRadians(hueDeg.toDouble())

/** The Custom theme's saturation slider is a **vividness** lever, sliding the colour along a
 *  *pastel → saturated* axis (not grey → colour): its low end is a pale pastel, its high end a rich
 *  colour. 0 = most pastel, [CUSTOM_SAT_MAX] = most saturated; default 1 sits comfortably between.
 *  See [customBaseColor] for the axis itself. */
const val DEFAULT_CUSTOM_SAT = 1f
const val CUSTOM_SAT_MAX = 1.6f

/**
 * The endpoints of the Custom colour's pastel↔saturated axis, in OKLCH. A pastel is *light and
 * lightly tinted*; a saturated colour is *a touch darker and strongly tinted*. Moving lightness and
 * chroma together — high-L/low-C at the pastel end, lower-L/high-C at the vivid end — is what makes
 * the low end read as a pale tint of the hue rather than neutral grey, and what makes the slider feel
 * like "more colour" rather than "add grey". The pastel end keeps a real chroma floor ([CUSTOM_PASTEL_C]
 * > 0) for exactly that reason. Both ends stay above the dark-ink threshold, so the ink never flips
 * mid-slide. */
const val CUSTOM_PASTEL_L = 0.88
const val CUSTOM_PASTEL_C = 0.045
const val CUSTOM_VIVID_L = 0.66
const val CUSTOM_VIVID_C = 0.16

/**
 * The chosen Custom colour for a seed [hueRad] and [sat] (0..[CUSTOM_SAT_MAX]) at full daytime — the
 * colour the home *is*, before any optional day-dimming ([customDrift]). [sat] slides the colour along
 * the pastel↔saturated axis between [CUSTOM_PASTEL_L]/[CUSTOM_PASTEL_C] and [CUSTOM_VIVID_L]/[CUSTOM_VIVID_C].
 * The single source of truth for "the colour you chose": the home, the chooser's live tile, and the
 * chooser's slider previews all resolve the seed through this, so the sliders are a true preview.
 */
fun customBaseColor(hueRad: Double, sat: Float): Lch {
    val t = (sat / CUSTOM_SAT_MAX).toDouble().coerceIn(0.0, 1.0)
    return Lch(
        CUSTOM_PASTEL_L + (CUSTOM_VIVID_L - CUSTOM_PASTEL_L) * t,
        CUSTOM_PASTEL_C + (CUSTOM_VIVID_C - CUSTOM_PASTEL_C) * t,
        hueRad,
    )
}

/** Whether the Custom colour follows the time of day. Off by default (SID-177): Custom is a fixed
 *  colour; Ambient is the time-of-day theme. When on, the chosen colour is dimmed by the day cycle. */
const val DEFAULT_CUSTOM_DRIFT = false

/** How dark the Custom colour is dimmed to at deep night when [customDrift] is on: a multiplier on its
 *  base lightness *and* chroma together, so it dims like a dimmer toward a clean dark of the same hue
 *  rather than a muddy low-lightness/high-chroma smear (the old fault: the seed hue laid over the
 *  night sky's own saturated navy at crushed lightness). 1 = full daytime colour. */
const val CUSTOM_NIGHT_DIM = 0.45

/** The Edge theme's rim: a small, intense colour glow. Its lightness is capped at [EDGE_MAX_L] (below
 *  the full daytime sky, so a thin rim isn't a blinding band) and its chroma boosted so the thin rim
 *  still reads vivid. Its overlays stay dark regardless — see HomeScreen's edge backdrop. */
const val EDGE_MAX_L = 0.6
const val EDGE_CHROMA_BOOST = 1.4f

/**
 * The pinned mood keyframes themes 2 & 3 freeze on. Build-time tuning values: pinned *moods*
 * rather than flat greys, so a frozen sky keeps a tint and its living texture.
 *
 * They must read as their own themes, not a lightness nudge on the sky: an earlier cut derived them
 * from [Lively.day] / [Lively.night] with only a lightness shift and was indistinguishable from the
 * ambient it has to contrast with — `#ADD6FD` against a daytime `#A0C8EF` (ΔL 0.04, same hue, same
 * chroma). Full light is now a clean near-white the drifting sky never reaches (its high lightness
 * alone sets it apart), and full dark is neutral ink (C ≈ 0.013) against a saturated navy night
 * (C ≈ 0.091) — distinct even at a similar lightness.
 *
 * Written as hex and converted, like [Lively]'s own keyframes, rather than as literal [Lch]
 * triples: [Lch.h] is in **radians** (it comes straight off `atan2`), so a hand-written degree
 * value silently wraps — `Lch(0.94, 0.020, 80.0)` is 80 *radians*, i.e. hue 264°, which rendered
 * this "warm" keyframe cold blue on the device. Hex has no unit to get wrong.
 */
data class ThemeKeyframes(val light: Lch, val dark: Lch) {
    companion object {
        val Default = ThemeKeyframes(
            // A clean, bright near-white: "Full light" reads as white (the ink flips to near-black),
            // with only a whisper of cool left in it so the accent stays sky-blue and the drifting
            // fields keep a trace of living texture rather than a flat #FFFFFF slab.
            light = hexToLch("#F6F9FF"),
            // Neutral charcoal ink, a whisper of cool left in it. Reads as "full dark" — ink flips
            // to near-white — yet never pure black; the fields and grain keep texture in it.
            dark = hexToLch("#0D0F15"),
        )
    }
}

/**
 * The effective mood the renderer paints for [theme] at this moment. Drifting themes (1 & 4)
 * pass the live [solarMood] straight through; pinned themes (2 & 3) return their frozen keyframe,
 * ignoring the solar input entirely (so the sky is identical at every hour); Custom (5) is a fixed
 * chosen colour that only reads [solarMood] when the user opts into day-adjust ([customDrift]).
 */
fun themeMood(
    theme: Theme,
    solarMood: Lch,
    customHueRad: Double = customHueRadians(DEFAULT_CUSTOM_HUE_DEG),
    customSat: Float = DEFAULT_CUSTOM_SAT,
    customDrift: Boolean = DEFAULT_CUSTOM_DRIFT,
    keyframes: ThemeKeyframes = ThemeKeyframes.Default,
    photoMood: Lch? = null,
): Lch = when (theme) {
    Theme.AMBIENT -> solarMood
    // Photo (SID-43): the image-derived mood, frozen — a photo is a fixed backdrop, so its ink and
    // accent are too. Null until a photo has been picked and baked; falling back to the live solar
    // mood keeps a Photo commit without a photo rendering as the ambient rather than a surprise.
    Theme.PHOTO -> photoMood ?: solarMood
    Theme.FULL_LIGHT -> keyframes.light
    Theme.FULL_DARK -> keyframes.dark
    // Edge: the live drifting sky, its lightness capped below full daytime brightness (so the thin
    // rim is intense but not a blinding band) and its chroma boosted so the glow reads vivid.
    Theme.EDGE_DARK -> solarMood.copy(
        l = solarMood.l.coerceAtMost(EDGE_MAX_L),
        c = solarMood.c * EDGE_CHROMA_BOOST,
    )
    // The chosen colour ([customBaseColor]): the seed hue, with lightness+chroma set by the pastel↔
    // saturated slider. This is what the home *is* by default — it does not track the sun, so the
    // colour you picked is the colour you get at every hour (ink and accent derive from it).
    // When [customDrift] is on, that colour is dimmed by the day cycle: as the sky darkens, its
    // lightness and chroma scale down *together* toward a clean dark of the same hue — a dimmer,
    // not the old fault of the seed hue smeared over the night sky's saturated navy at crushed
    // lightness (which read muddy). The dim factor is read off the solar mood's own lightness,
    // normalised across the night→day swing, so it needs no separate solar input here.
    Theme.CUSTOM -> {
        val base = customBaseColor(customHueRad, customSat)
        if (!customDrift) {
            base
        } else {
            val span = Lively.day.l - Lively.night.l
            val dayness =
                if (span == 0.0) 1.0
                else ((solarMood.l - Lively.night.l) / span).coerceIn(0.0, 1.0)
            val dim = CUSTOM_NIGHT_DIM + (1.0 - CUSTOM_NIGHT_DIM) * dayness
            base.copy(l = base.l * dim, c = base.c * dim)
        }
    }
}

/** Whether the solar time-of-day progression still moves under this theme. Ambient (1) and Edge (4)
 *  always drift; Custom (5) drifts only when the user opts in ([customDrift]); the pinned themes never. */
fun driftEnabled(theme: Theme, customDrift: Boolean = DEFAULT_CUSTOM_DRIFT): Boolean = when (theme) {
    Theme.AMBIENT, Theme.EDGE_DARK -> true
    Theme.CUSTOM -> customDrift
    Theme.FULL_LIGHT, Theme.FULL_DARK, Theme.PHOTO -> false
}

/** Whether the ambient is painted as an edge glow fading to a dark centre (theme 4 only). */
fun edgeOnly(theme: Theme): Boolean = theme == Theme.EDGE_DARK

/**
 * Whether the sky's colour fields stay locked to the mood's single hue (Custom only, SID-177). The
 * ambient themes spread the fields across analogous hues (each field rotates the mood hue by its own
 * offset) for a living, multi-hue sky — that richness is the point. But a *chosen* colour has to read
 * as that one colour: a pink pick drifting to an orange glow on one side breaks the consistency the
 * user asked for. So Custom zeroes the fields' hue spread and lets them vary only in lightness and
 * chroma — a darker or lighter, more or less vivid *tone of the pick*, never a neighbouring hue. */
fun hueLocked(theme: Theme): Boolean = theme == Theme.CUSTOM

/** The multiplier applied to each sky field's hue offset for [theme]: 0 locks every field to the
 *  mood's hue ([hueLocked]), 1 gives the ambient's full analogous-hue spread. */
fun fieldHueSpread(theme: Theme): Float = if (hueLocked(theme)) 0f else 1f
