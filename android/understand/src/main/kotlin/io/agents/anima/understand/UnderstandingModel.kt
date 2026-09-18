package io.agents.anima.understand

/**
 * A model backend that can complete a completion-style prompt.
 *
 * Implementations must set generation temperature 0: a non-zero temperature is
 * a silent stability bug, not a tuning knob. They return raw model text or null
 * when the model cannot answer (no key, no network, endpoint down) -- the
 * [HybridUnderstander] owns fallback, and a null here must never throw.
 */
interface UnderstandingModel {
    /** Reported to the pack's `scan.understander.backend`. */
    val name: String

    /** Reported to the pack's `scan.understander.model`. */
    val model: String?

    /** Returns the model's raw completion text, or null when unavailable. */
    fun completeText(system: String, user: String, maxOutputTokens: Int): String?
}

/** A cached model response, keyed by screen/journey/tone id. */
data class CachedResponse(
    val backend: String,
    val text: String,
)