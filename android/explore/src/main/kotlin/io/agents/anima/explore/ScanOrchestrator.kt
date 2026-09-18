package io.agents.anima.explore

import io.agents.anima.core.AppIdentity
import io.agents.anima.core.Coverage
import io.agents.anima.core.DeviceInfo
import io.agents.anima.core.Element
import io.agents.anima.core.ElementAction
import io.agents.anima.core.ElementRole
import io.agents.anima.core.GraphEdge
import io.agents.anima.core.KnowledgePack
import io.agents.anima.core.PackCompactor
import io.agents.anima.core.PackRepository
import io.agents.anima.core.ScanContext
import io.agents.anima.core.ScanMetadata
import io.agents.anima.core.Screen
import io.agents.anima.core.ScreenGraph
import io.agents.anima.core.ScreenIdentifier
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ScreenProfile
import io.agents.anima.core.ScreenUnderstander
import io.agents.anima.core.UnderstanderInfo
import io.agents.anima.engine.PrunedNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The missing piece between a crawl and a pack.
 *
 * [Explorer] produces a [ScanOutcome] -- raw observations and the transitions
 * actually taken. [ScreenUnderstander] and [ScreenIdentifier] each know how to
 * turn one observation into language or an id, but nothing before this class
 * called them across a whole scan and assembled the result into a
 * [KnowledgePack]. That is all this class does: drive the three interfaces
 * already agreed in `Contracts.kt` over one [ScanOutcome], in order, and hand
 * back a pack plus the screenshot bytes that don't fit in it.
 *
 * **Known gap, not papered over here:** [PackRepository.save] takes a
 * [KnowledgePack] and nothing else. `Pack.kt` is explicitly data-only (see its
 * own doc comment) and has no field for screenshot bytes -- `Screen.screenshot`
 * is a *path* (`"screens/$id.webp"`), not the image itself, matching the
 * `.animapack` zip layout in `KNOWLEDGE_PACK.md` (`pack.json` +
 * `screens/scr_*.webp`). Nothing in the current `:core` contract says who
 * writes those webp files into that zip, or when. [ScanResult.screenshots] is
 * this class being honest about that rather than silently dropping the bytes
 * or reaching into `:store` (out of module bounds) to write them itself. The
 * caller -- whoever owns `.animapack` export -- needs to either grow
 * [PackRepository] to accept images, or take [ScanResult.screenshots] directly
 * off this call and write the zip entries itself.
 */
class ScanOrchestrator(
    private val explorer: Explorer,
    private val identifier: ScreenIdentifier,
    private val understander: ScreenUnderstander,
    private val repository: PackRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Runs [explorer], describes every screen through [understander], assigns
     * element ids through [identifier], and returns both the finished pack and
     * the raw screenshot bytes collected along the way.
     *
     * Does **not** call [PackRepository.save] itself -- [repository] is held
     * only for the interface it is typed against and future use (`load` /
     * `latest` / `diff` are natural next steps for a caller), but saving is
     * deliberately left to the caller of [scan]. That keeps this method a pure
     * function of its inputs: build a fake [ScreenUnderstander] and a fake
     * [PackRepository] is not even required to unit test it, since nothing here
     * calls the repository at all.
     *
     * Screenshots are returned separately rather than folded into
     * [KnowledgePack] on purpose: the pack is data-only by design (see
     * Pack.kt's own doc comment), and [PackRepository.save] -- Jacob's contract
     * -- does not currently accept images at all. That is a real gap, not
     * something to paper over from this side of the interface: see the class
     * doc above.
     */
    fun scan(
        app: AppIdentity,
        device: DeviceInfo,
        credentials: Map<String, String> = emptyMap(),
        understanderInfo: UnderstanderInfo? = null,
    ): ScanResult {
        val startedAt = clock()
        val outcome = explorer.scan(app.packageName, credentials)

        val describedNames = ArrayList<String>()
        val screens = ArrayList<Screen>()
        val screenshots = LinkedHashMap<String, ByteArray>()
        // screenId -> (elementKey -> Element), kept so the graph pass below can
        // look elements back up by the same Candidate.keyFor() key their leadsTo
        // resolution used, without recomputing anything.
        val elementsByScreen = LinkedHashMap<String, List<Pair<String, Element>>>()

        for ((screenId, observation) in outcome.screens) {
            val context = ScanContext(
                appPackage = app.packageName,
                appLabel = app.label,
                knownScreenNames = describedNames.toList(),
            )
            val profile = understander.describe(observation, screenId, context)
            describedNames.add(profile.name)

            val built = buildAllElements(screenId, observation, profile, outcome.edges)
            elementsByScreen[screenId] = built

            val screenshot = observation.screenshot
            val screenshotPath = if (screenshot != null) {
                screenshots[screenId] = screenshot
                "screens/$screenId.webp"
            } else {
                null
            }

            screens.add(
                Screen(
                    id = screenId,
                    name = profile.name,
                    purpose = profile.purpose,
                    kind = profile.kind,
                    signature = identifier.signature(observation),
                    // built carries one (actionKey -> element) pair per action a
                    // node has, so a multi-action node (e.g. clickable AND
                    // scrollable) appears more than once here; distinctBy id
                    // collapses that back to one Element per PrunedNode.
                    elements = built.distinctBy { it.second.id }.map { it.second }.sortedBy { it.id },
                    screenshot = screenshotPath,
                    modesSeen = listOf(observation.uiMode),
                )
            )
        }

        val graph = buildGraph(outcome.edges, elementsByScreen)

        val pack = KnowledgePack(
            app = app,
            scan = ScanMetadata(
                id = "scan_" + scanTimestamp(startedAt),
                startedAt = isoInstant(startedAt),
                durationMs = outcome.durationMs,
                device = device,
                coverage = Coverage(
                    screensFound = outcome.screenCount,
                    elementsFound = outcome.elementCount,
                    frontierRemaining = outcome.frontierRemaining,
                    stopReason = outcome.stopReason.wire,
                ),
                // :explore has no way to know which concrete ScreenUnderstander
                // it was handed -- backend, model name and cache-hit count all
                // live on the implementation, which lives in :understand, a
                // module this one cannot see. The caller supplies this metadata
                // if it wants it recorded; ScanOrchestrator only carries it
                // through.
                understander = understanderInfo,
            ),
            screens = screens.sortedBy { it.id },
            // TODO(S5): journey extraction is a separate task. Leaving this
            // empty is not a stub for the real thing -- it is the documented
            // scope boundary for this class.
            journeys = emptyList(),
            // TODO: :design's DesignSystem output is composed in by whoever
            // wires :design into the scan, not by ScanOrchestrator -- :explore
            // has no dependency on :design and no way to produce or reach one.
            designSystem = null,
            graph = graph,
        )

        return ScanResult(PackCompactor.compact(pack), screenshots)
    }

    /**
     * One [Element] per [PrunedNode] on [observation] -- every pruned node is
     * meaningful by construction, so none are filtered here -- paired with the
     * [Candidate.keyFor] key for each action the node supports (a node with no
     * actions is keyed by its own element id instead, a key [buildGraph] never
     * looks up, just so it still has an entry here). [buildGraph] uses these
     * pairs to resolve an [ObservedEdge.viaActionKey] back to the [Element] that
     * caused it, without recomputing anything.
     */
    private fun buildAllElements(
        screenId: String,
        observation: ScreenObservation,
        profile: ScreenProfile,
        edges: List<ObservedEdge>,
    ): List<Pair<String, Element>> {
        val rankByKey = HashMap<String, Int>()
        val out = ArrayList<Pair<String, Element>>()

        for (node in observation.nodes) {
            val key = node.resourceId ?: node.className
            val rank = rankByKey.getOrDefault(key, 0)
            rankByKey[key] = rank + 1
            val structuralPath = "$key#$rank"

            val elementId = identifier.elementId(screenId, node, structuralPath)
            val actions = actionsFor(node)
            val actionKeys = actions.map { action -> Candidate.keyFor(screenId, node, action) }
            val leadsTo = actionKeys
                .mapNotNull { actionKey -> edges.firstOrNull { it.from == screenId && it.viaActionKey == actionKey } }
                .firstOrNull()
                ?.to

            val element = Element(
                id = elementId,
                role = roleFor(node),
                label = node.text ?: node.contentDesc,
                semantic = profile.elementSemantics[node.id],
                boundsRel = node.relBounds,
                actions = actions,
                leadsTo = leadsTo,
                input = profile.inputSpecs[node.id],
            )

            // One (actionKey -> element) pair per action, so buildGraph can find
            // this element from an ObservedEdge.viaActionKey. A node with no
            // actions still needs to appear in Screen.elements, so it is keyed
            // by its own element id as a fallback that buildGraph never uses.
            if (actionKeys.isEmpty()) {
                out.add(elementId to element)
            } else {
                actionKeys.forEach { out.add(it to element) }
            }
        }
        return out
    }

    private fun buildGraph(
        edges: List<ObservedEdge>,
        elementsByScreen: Map<String, List<Pair<String, Element>>>,
    ): ScreenGraph {
        val graphEdges = edges.mapNotNull { edge ->
            val lookup = elementsByScreen[edge.from].orEmpty()
            val via = lookup.firstOrNull { (key, _) -> key == edge.viaActionKey }?.second?.id
                ?: return@mapNotNull null
            GraphEdge(from = edge.from, to = edge.to, via = via, action = edge.action)
        }
        return ScreenGraph(
            edges = graphEdges.sortedWith(compareBy({ it.from }, { it.to }, { it.via })),
        )
    }

    private fun actionsFor(node: PrunedNode): List<ElementAction> {
        val actions = ArrayList<ElementAction>()
        if (node.clickable) actions.add(ElementAction.TAP)
        if (node.editable) actions.add(ElementAction.INPUT)
        if (node.scrollable) actions.add(ElementAction.SCROLL)
        return actions
    }

    private fun roleFor(node: PrunedNode): ElementRole {
        val className = node.className
        return when {
            node.editable -> ElementRole.INPUT
            node.checked ||
                className.contains("Switch") ||
                className.contains("CheckBox") ||
                className.contains("ToggleButton") -> ElementRole.TOGGLE
            className.contains("RecyclerView") ||
                className.contains("ListView") ||
                className.contains("GridView") -> ElementRole.LIST
            className.contains("Button") -> ElementRole.BUTTON
            className.contains("ImageView") -> ElementRole.IMAGE
            className.contains("Tab") -> ElementRole.TAB
            className.contains("BottomNavigationView") ||
                className.contains("NavigationView") ||
                className.contains("Toolbar") ||
                className.contains("ActionBar") -> ElementRole.NAV
            node.clickable -> ElementRole.LINK
            else -> ElementRole.TEXT
        }
    }

    /** `"2026_09_18T04_12Z"` style id fragment -- colons and dots are unsafe in filenames. */
    private fun scanTimestamp(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy_MM_dd'T'HH_mm'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(epochMs))
    }

    private fun isoInstant(epochMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(epochMs))
    }
}

/**
 * [pack] is saved via [PackRepository.save] by the caller of [ScanOrchestrator.scan],
 * not by `scan()` itself -- see the kdoc on [ScanOrchestrator.scan] for why.
 * [screenshots] is keyed by screen id; nothing in the current `:core` contract
 * has anywhere to put these yet (see [ScanOrchestrator]'s class doc). The
 * caller is responsible for writing them into the `.animapack` zip's
 * `screens/` webp entries.
 */
data class ScanResult(val pack: KnowledgePack, val screenshots: Map<String, ByteArray>)
