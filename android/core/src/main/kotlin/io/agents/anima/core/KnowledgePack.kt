package io.agents.anima.core

data class AppInfo(
    val `package`: String,
    val label: String,
    val version_name: String,
    val version_code: Int
)

data class DeviceInfo(
    val model: String,
    val sdk: Int,
    val resolution: List<Int>,
    val locale: String
)

data class CoverageInfo(
    val screens_found: Int,
    val elements_found: Int,
    val frontier_remaining: Int,
    val stop_reason: String
)

data class UnderstanderInfo(
    val backend: String,
    val model: String,
    val cache_hits: Int
)

data class ScanInfo(
    val id: String,
    val started_at: String,
    val duration_ms: Long,
    val device: DeviceInfo,
    val coverage: CoverageInfo,
    val understander: UnderstanderInfo
)

data class ElementInput(
    val type: String,
    val required: Boolean,
    val hint: String?,
    val max_length: Int?
)

data class ElementInfo(
    val id: String,
    val role: String,
    val label: String?,
    val semantic: String?,
    val bounds_rel: List<Double>?,
    val actions: List<String>?,
    val leads_to: String?,
    val input: ElementInput?
)

data class ScreenSignature(
    val structural_hash: String,
    val anchors: List<String>,
    val activity: String?
)

data class ScreenInfo(
    val id: String,
    val name: String?,
    val purpose: String?,
    val kind: String?,
    val signature: ScreenSignature,
    val elements: List<ElementInfo>,
    val screenshot: String?,
    val modes_seen: List<String>
)

data class JourneyStep(
    val screen: String,
    val element: String,
    val action: String,
    val input_slot: String?
)

data class JourneyInfo(
    val id: String,
    val name: String?,
    val goal: String,
    val preconditions: List<String>?,
    val steps: List<JourneyStep>,
    val outcome: String,
    val replayable: Boolean,
    val verified_at_scan: String?
)

data class DesignColors(
    val primary: String,
    val on_primary: String,
    val surface: String,
    val background: String,
    val error: String,
    val palette: List<String>
)

data class TypographyInfo(
    val role: String,
    val family: String,
    val size_sp: Int,
    val weight: Int
)

data class SpacingInfo(
    val base_dp: Int,
    val scale: List<Int>
)

data class ShapeInfo(
    val radius_dp: List<Int>
)

data class ComponentInfo(
    val name: String,
    val fill: String?,
    val text: String?,
    val radius_dp: Int?,
    val height_dp: Int?,
    val seen_on: Int
)

data class DesignModes(
    val light: Map<String, String>?,
    val dark: Map<String, String>?
)

data class ToneOfVoice(
    val register: String,
    val summary: String,
    val examples: List<String>
)

data class DesignSystem(
    val colors: DesignColors,
    val typography: List<TypographyInfo>,
    val spacing: SpacingInfo,
    val shape: ShapeInfo,
    val components: List<ComponentInfo>,
    val modes: DesignModes,
    val tone_of_voice: ToneOfVoice
)

data class GraphEdge(
    val from: String,
    val to: String,
    val via: String,
    val action: String
)

data class GraphInfo(
    val edges: List<GraphEdge>
)

data class KnowledgePack(
    val pack_version: String,
    val app: AppInfo,
    val scan: ScanInfo,
    val screens: List<ScreenInfo>,
    val journeys: List<JourneyInfo>,
    val design_system: DesignSystem?,
    val graph: GraphInfo?
)
