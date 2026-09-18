package io.agents.anima.design

import io.agents.anima.core.DesignExtractor
import io.agents.anima.core.DesignSystem
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ToneOfVoice
import io.agents.anima.core.UiMode

/**
 * The `:design` implementation of [DesignExtractor] — the one seam this module
 * exposes to the rest of the scan.
 *
 * Each aspect is its own extractor so each can be tested against a fixture on
 * its own terms; this composes them and fills in the parts of [DesignSystem]
 * that only make sense across the whole set, which is light/dark.
 *
 * `tone_of_voice` is deliberately left null. It is language, not design, it
 * comes from a model, and `ScreenUnderstander.toneOfVoice` already owns it —
 * writing a second, worse copy here would put two answers in the pack for one
 * question.
 */
class AnimaDesignExtractor(
    /**
     * Density of the captures, when the caller knows it.
     *
     * Null means every extractor infers it per frame, which is the production
     * path until `ScreenObservation` carries the real value (Q6).
     */
    private val density: Density? = null,
) : DesignExtractor {

    override fun extract(observations: List<ScreenObservation>): DesignSystem {
        // Sorting first is what makes the output independent of crawl order: the
        // extractors below take medians and modes, and a mode with an even split
        // would otherwise be decided by whichever screen the crawler happened to
        // visit first.
        val ordered = observations.sortedWith(
            compareBy({ it.packageName }, { it.activity ?: "" }, { it.nodes.size }),
        )

        return DesignSystem(
            colors = ColorExtractor().extract(ordered),
            typography = TypographyExtractor(density = density).extract(ordered),
            spacing = SpacingExtractor(density = density).extract(ordered),
            shape = ShapeExtractor(density = density).extract(ordered),
            components = ComponentExtractor(density = density).extract(ordered),
            modes = extractModes(ordered),
            toneOfVoice = TONE_OF_VOICE_BELONGS_TO_THE_UNDERSTANDER,
        )
    }

    /**
     * Per-mode colour, but only for the modes actually captured.
     *
     * Y7 designs for one mode being the normal case. Forcing the map to hold
     * both keys would mean inventing a dark palette from light screens, and a
     * fabricated dark theme is worse than an absent one — it would be wrong in
     * a way a reader could not detect. So a scan that only ever saw light
     * reports only light, and the diff is a bonus when `setUiMode` was
     * available.
     */
    private fun extractModes(observations: List<ScreenObservation>): Map<String, Map<String, String>> {
        val byMode = LinkedHashMap<String, Map<String, String>>()
        for (mode in UiMode.values()) {
            val forMode = observations.filter { it.uiMode == mode }
            if (forMode.isEmpty()) continue
            val colors = ColorExtractor().extract(forMode)
            val entries = LinkedHashMap<String, String>()
            colors.surface?.let { entries["surface"] = it }
            colors.background?.let { entries["background"] = it }
            colors.primary?.let { entries["primary"] = it }
            if (entries.isNotEmpty()) byMode[mode.wire] = entries
        }
        return byMode
    }

    private companion object {
        /** Named rather than a bare null, so the omission reads as a decision. */
        val TONE_OF_VOICE_BELONGS_TO_THE_UNDERSTANDER: ToneOfVoice? = null
    }
}
