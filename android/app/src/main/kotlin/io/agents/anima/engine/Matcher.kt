package io.agents.anima.engine

import kotlin.math.sqrt

/**
 * Weighted multi-attribute locator matcher (SkillDroid), ported from `anima.py`.
 *
 * The score is normalized by the weight of the locator attributes that are actually present, so a
 * locator carrying only a resource-id can still reach 1.0.
 */
object Matcher {

    const val W_RES = 0.35
    const val W_DESC = 0.25
    const val W_TEXT = 0.20
    const val W_CLS = 0.10
    const val W_SPATIAL = 0.10

    const val DEFAULT_THRESHOLD = 0.50

    /** Distance (in normalized [0,1] screen units) below which a spatial match is considered exact. */
    private const val NEAR_DIST = 0.05

    /** Distance beyond which spatial similarity contributes nothing. */
    private const val FAR_DIST = 0.15

    fun score(loc: Locator, node: PrunedNode): Double {
        var score = 0.0
        var activeWeight = 0.0

        val locRes = loc.resourceId
        if (!locRes.isNullOrEmpty()) {
            activeWeight += W_RES
            if (locRes == node.resourceId) score += W_RES
        }

        val locDesc = loc.contentDesc
        if (!locDesc.isNullOrEmpty()) {
            activeWeight += W_DESC
            val nodeDesc = node.contentDesc
            if (!nodeDesc.isNullOrEmpty()) {
                score += TextRatio.ratio(locDesc.lowercase(), nodeDesc.lowercase()) * W_DESC
            }
        }

        val locText = loc.text
        if (!locText.isNullOrEmpty()) {
            activeWeight += W_TEXT
            val nodeText = node.text
            if (!nodeText.isNullOrEmpty()) {
                score += TextRatio.ratio(locText.lowercase(), nodeText.lowercase()) * W_TEXT
            }
        }

        val locCls = loc.className
        if (!locCls.isNullOrEmpty()) {
            activeWeight += W_CLS
            // Substring containment, not equality: "Switch" matches "android.widget.Switch".
            if (node.className.lowercase().contains(locCls.lowercase())) score += W_CLS
        }

        // Relative spatial matching is preferred: it stays valid across 1080p / 1440p devices.
        val locRelCenter = loc.relCenter
        val locBounds = loc.bounds
        if (locRelCenter != null && locRelCenter.size >= 2) {
            activeWeight += W_SPATIAL
            val nodeRelCenter = node.relCenter
            if (nodeRelCenter.size >= 2) {
                val dx = locRelCenter[0] - nodeRelCenter[0]
                val dy = locRelCenter[1] - nodeRelCenter[1]
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= NEAR_DIST) {
                    score += W_SPATIAL
                } else if (dist <= FAR_DIST) {
                    score += (1.0 - dist / FAR_DIST) * W_SPATIAL
                }
            }
        } else if (locBounds != null && locBounds.isNotEmpty()) {
            activeWeight += W_SPATIAL
            if (locBounds == node.bounds) score += W_SPATIAL
        }

        return if (activeWeight > 0.0) score / activeWeight else 0.0
    }

    /**
     * Returns the highest scoring node, or null when nothing clears [threshold].
     * Ties go to the first node in iteration order (strictly-greater comparison).
     */
    fun findBest(
        loc: Locator,
        nodes: List<PrunedNode>,
        threshold: Double = DEFAULT_THRESHOLD,
    ): PrunedNode? {
        var best: PrunedNode? = null
        var bestScore = 0.0
        for (n in nodes) {
            val s = score(loc, n)
            if (s > bestScore) {
                bestScore = s
                best = n
            }
        }
        return if (bestScore >= threshold) best else null
    }
}
