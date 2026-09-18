package io.agents.anima.core

import io.agents.anima.engine.PrunedNode

/**
 * The seams between the five modules, agreed in the Day-0 session.
 *
 * Signatures only — every implementation lives in its owner's module, and no
 * module imports another's. If you need something from someone else's module,
 * that is a change here, discussed, not a reach into their files.
 *
 * Owners: [ScreenIdentifier] Jacob · [ScreenUnderstander] Daniel ·
 * [DesignExtractor] Jiya · [PackRepository] Jacob. The producer of
 * [ScreenObservation] is Samuel's crawler.
 */

/**
 * One captured screen, exactly as the crawler saw it.
 *
 * This is the single input every other module works from, which is why it
 * carries both the pruned tree and the pixels: the tree is what makes output
 * stable and cheap, the screenshot is what makes design extraction and VLM
 * understanding possible at all.
 */
data class ScreenObservation(
    val packageName: String,
    val activity: String?,
    /** Pruned Agent-DOM for this screen, in capture order. */
    val nodes: List<PrunedNode>,
    /** PNG/WebP bytes, or null when the screenshot API refused this frame. */
    val screenshot: ByteArray?,
    val screenSize: Pair<Int, Int>,
    val uiMode: UiMode,
) {
    // Generated equals/hashCode on a ByteArray compares references, which is a
    // silent correctness trap in a data class. Compare what actually identifies
    // an observation instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScreenObservation) return false
        return packageName == other.packageName &&
            activity == other.activity &&
            nodes == other.nodes &&
            screenSize == other.screenSize &&
            uiMode == other.uiMode
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + (activity?.hashCode() ?: 0)
        result = 31 * result + nodes.hashCode()
        result = 31 * result + screenSize.hashCode()
        result = 31 * result + uiMode.hashCode()
        return result
    }
}

/**
 * Turns an observation into the stable identity the whole pack is keyed by.
 *
 * **Do not port `compute_screen_signature` from `anima.py:869`.** It uses
 * Python's `hash()`, which is salted per process, so two runs of the same scan
 * produce different screen IDs — the exact opposite of the requirement. The
 * spec for the real thing is in `KNOWLEDGE_PACK.md`.
 */
interface ScreenIdentifier {
    /** `scr_` + first 12 hex of SHA-256 over the canonical structural fingerprint. */
    fun screenId(observation: ScreenObservation): String

    /** `el_` + first 8 hex of SHA-256 over screen id, resource-id-or-role and structural path. */
    fun elementId(screenId: String, node: PrunedNode, structuralPath: String): String

    /** The full signature block, for `Screen.signature`. */
    fun signature(observation: ScreenObservation): ScreenSignature
}

/** Read-only context an understander may use to write better descriptions. */
data class ScanContext(
    val appPackage: String,
    val appLabel: String,
    /** Names of screens already described this scan, for consistent naming. */
    val knownScreenNames: List<String>,
)

/** What a model contributes to one screen. Every field is LLM-authored. */
data class ScreenProfile(
    val name: String,
    val purpose: String,
    val kind: ScreenKind,
    /** Keyed by `PrunedNode.id`, so the crawler can attach these without re-matching. */
    val elementSemantics: Map<Int, String>,
    /** Keyed by `PrunedNode.id`. Only entries for nodes that really are inputs. */
    val inputSpecs: Map<Int, InputSpec>,
)

/**
 * Language over a screen. Backed by a cloud VLM, an on-device LLM, or pure
 * heuristics — composed with fallback so a model failure degrades the output
 * instead of ending the scan.
 *
 * Implementations **must** cache by screen id: temperature 0 is necessary but
 * not sufficient for the byte-identical requirement.
 */
interface ScreenUnderstander {
    fun describe(observation: ScreenObservation, screenId: String, context: ScanContext): ScreenProfile

    /** Names and summarizes a path through the graph. */
    fun describeJourney(path: List<JourneyStep>, screens: List<Screen>, context: ScanContext): Journey

    /** Classifies the app's copy register from every string the scan collected. */
    fun toneOfVoice(copy: List<String>, context: ScanContext): ToneOfVoice
}

/**
 * Extracts the app's visual language. Testable from a static screenshot and a
 * static node list — no device required, which is why this module can be built
 * against fixtures from day one.
 */
interface DesignExtractor {
    fun extract(observations: List<ScreenObservation>): DesignSystem
}

/** Persistence, assembly, diffing and export. */
interface PackRepository {
    fun save(pack: KnowledgePack)

    fun load(packageName: String, scanId: String): KnowledgePack?

    fun latest(packageName: String): KnowledgePack?

    /** The canonical, byte-stable JSON for a pack. Two equal packs give equal strings. */
    fun toCanonicalJson(pack: KnowledgePack): String

    /** What changed between two scans — the answer to "the app updated". */
    fun diff(old: KnowledgePack, new: KnowledgePack): PackDiff
}

data class PackDiff(
    val screensAdded: List<String>,
    val screensRemoved: List<String>,
    val screensChanged: List<String>,
    val journeysBroken: List<String>,
)
