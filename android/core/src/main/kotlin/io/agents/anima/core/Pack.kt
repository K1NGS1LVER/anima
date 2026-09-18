package io.agents.anima.core

/**
 * The App Knowledge Pack, schema v1 — the team contract.
 *
 * These types are the frozen shape agreed in the Day-0 session and specified in
 * `KNOWLEDGE_PACK.md`. Five modules produce or consume them, so a change here
 * stalls four people at once: it needs a PR approved by **Samuel and Jacob**.
 *
 * This file is deliberately data only. Serialization, canonical ordering and the
 * stable-ID hashing live in `:store` and are Jacob's; the crawler in `:explore`,
 * the model backends in `:understand` and the extractor in `:design` all fill
 * parts of the same document without knowing how it is written to disk.
 *
 * Nullable means "not observed on this scan", and an unobserved field is omitted
 * from the JSON rather than emitted as `null` — the size budget is tight enough
 * that nulls cost real bytes across a 40-screen pack.
 */
data class KnowledgePack(
    val packVersion: String = PACK_VERSION,
    val app: AppIdentity,
    val scan: ScanMetadata,
    val screens: List<Screen>,
    val journeys: List<Journey>,
    val designSystem: DesignSystem?,
    val graph: ScreenGraph,
) {
    companion object {
        const val PACK_VERSION = "1.0"
    }
}

data class AppIdentity(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long?,
)

/**
 * Everything about *this run* rather than about the app.
 *
 * Excluded from the stability diff, and the only part of the pack allowed to
 * differ between two scans of the same app version. Durations, timestamps and
 * counters live here and nowhere else.
 */
data class ScanMetadata(
    val id: String,
    val startedAt: String,
    val durationMs: Long,
    val device: DeviceInfo,
    val coverage: Coverage,
    val understander: UnderstanderInfo?,
)

data class DeviceInfo(
    val model: String,
    val sdk: Int,
    val resolution: List<Int>,
    val locale: String,
)

data class Coverage(
    val screensFound: Int,
    val elementsFound: Int,
    val frontierRemaining: Int,
    val stopReason: String,
)

data class UnderstanderInfo(
    val backend: String,
    val model: String?,
    val cacheHits: Int,
)

/** One screen of the app. [id] is the stable structural hash, `scr_` + 12 hex. */
data class Screen(
    val id: String,
    val name: String?,
    val purpose: String?,
    val kind: ScreenKind,
    val signature: ScreenSignature,
    val elements: List<Element>,
    val screenshot: String?,
    val modesSeen: List<UiMode>,
)

enum class ScreenKind {
    LIST, FORM, DETAIL, DIALOG, ONBOARDING, AUTH, SETTINGS, OTHER;

    /** The pack spells these lower-case; `name` would emit them shouting. */
    val wire: String get() = name.lowercase()
}

enum class UiMode {
    LIGHT, DARK;

    val wire: String get() = name.lowercase()
}

/**
 * What makes a screen *that* screen across scans.
 *
 * [structuralHash] is the full SHA-256; `Screen.id` is its first 12 hex chars.
 * The fingerprint behind it is built from resource-ids, the class-path skeleton
 * and the activity name — never from visible text, counts, bounds or anything
 * else that changes between two runs on the same screen.
 */
data class ScreenSignature(
    val structuralHash: String,
    val anchors: List<String>,
    val activity: String?,
)

/** One interactive or semantically meaningful element. [id] is `el_` + 8 hex. */
data class Element(
    val id: String,
    val role: ElementRole,
    val label: String?,
    val semantic: String?,
    val boundsRel: List<Double>,
    val actions: List<ElementAction>,
    val leadsTo: String?,
    val input: InputSpec?,
)

enum class ElementRole {
    BUTTON, INPUT, TOGGLE, LINK, LIST, LIST_ITEM, TEXT, IMAGE, TAB, NAV;

    val wire: String get() = name.lowercase()
}

enum class ElementAction {
    TAP, LONG_PRESS, INPUT, SCROLL, SWIPE, BACK;

    val wire: String get() = name.lowercase()
}

/** Present only for [ElementRole.INPUT]. */
data class InputSpec(
    val type: InputType,
    val required: Boolean,
    val hint: String?,
    val maxLength: Int?,
)

enum class InputType {
    EMAIL, PHONE, OTP, AMOUNT, DATE, PASSWORD, TEXT;

    val wire: String get() = name.lowercase()
}

/**
 * A path through the app that accomplishes something.
 *
 * [replayable] is not a claim, it is a result: the journey was replayed on a
 * device and it worked. This is what makes the pack executable rather than
 * merely descriptive, and it is the answer to "how do you know this is still
 * true after the app updated".
 */
data class Journey(
    val id: String,
    val name: String,
    val goal: String,
    val preconditions: List<String>,
    val steps: List<JourneyStep>,
    val outcome: String?,
    val replayable: Boolean,
    val verifiedAtScan: String?,
)

data class JourneyStep(
    val screen: String,
    val element: String,
    val action: ElementAction,
    /** A `{placeholder}` filled from test credentials at replay time, never a literal secret. */
    val inputSlot: String? = null,
)

data class DesignSystem(
    val colors: ColorTokens,
    val typography: List<TypeToken>,
    val spacing: SpacingTokens,
    val shape: ShapeTokens,
    val components: List<ComponentToken>,
    val modes: Map<String, Map<String, String>>,
    val toneOfVoice: ToneOfVoice?,
)

data class ColorTokens(
    val primary: String?,
    val onPrimary: String?,
    val surface: String?,
    val background: String?,
    val error: String?,
    val palette: List<String>,
)

data class TypeToken(
    val role: String,
    val family: String?,
    val sizeSp: Double,
    val weight: Int,
)

data class SpacingTokens(val baseDp: Int, val scale: List<Int>)

data class ShapeTokens(val radiusDp: List<Int>)

data class ComponentToken(
    val name: String,
    val fill: String?,
    val text: String?,
    val radiusDp: Int?,
    val heightDp: Int?,
    val seenOn: Int,
)

data class ToneOfVoice(
    val register: String,
    val summary: String,
    val examples: List<String>,
)

/** Screens are nodes, observed transitions are edges. */
data class ScreenGraph(val edges: List<GraphEdge>)

data class GraphEdge(
    val from: String,
    val to: String,
    val via: String,
    val action: ElementAction,
)
