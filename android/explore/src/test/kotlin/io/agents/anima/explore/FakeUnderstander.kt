package io.agents.anima.explore

import io.agents.anima.core.InputSpec
import io.agents.anima.core.InputType
import io.agents.anima.core.Journey
import io.agents.anima.core.JourneyStep
import io.agents.anima.core.ScanContext
import io.agents.anima.core.Screen
import io.agents.anima.core.ScreenKind
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ScreenProfile
import io.agents.anima.core.ScreenUnderstander
import io.agents.anima.core.ToneOfVoice

/**
 * A stand-in for Daniel's [ScreenUnderstander].
 *
 * Every field is derived purely from [ScreenObservation] content -- node
 * count, editability, class names -- never from call count, call order, or
 * randomness. That is what makes [ScanOrchestratorTest]'s headline test mean
 * anything: two independent scans of the same [FakeApp] must hand this the
 * same observations in the same order, and this must answer identically both
 * times without leaning on any hidden state of its own.
 */
class FakeUnderstander : ScreenUnderstander {

    override fun describe(observation: ScreenObservation, screenId: String, context: ScanContext): ScreenProfile {
        val editableNodes = observation.nodes.filter { it.editable }
        val scrollableNodes = observation.nodes.filter { it.scrollable }

        val kind = when {
            editableNodes.isNotEmpty() -> ScreenKind.FORM
            scrollableNodes.isNotEmpty() -> ScreenKind.LIST
            else -> ScreenKind.OTHER
        }

        val elementSemantics = observation.nodes
            .filter { it.text != null || it.contentDesc != null }
            .associate { it.id to "semantic:${it.text ?: it.contentDesc}" }

        val inputSpecs = editableNodes.associate { node ->
            node.id to InputSpec(
                type = InputType.TEXT,
                required = false,
                hint = node.text ?: node.contentDesc,
                maxLength = null,
            )
        }

        return ScreenProfile(
            name = "Screen ${observation.nodes.size}",
            purpose = "A screen with ${observation.nodes.size} elements on ${observation.activity ?: "unknown activity"}.",
            kind = kind,
            elementSemantics = elementSemantics,
            inputSpecs = inputSpecs,
        )
    }

    override fun describeJourney(path: List<JourneyStep>, screens: List<Screen>, context: ScanContext): Journey =
        Journey(
            id = "jrn_" + path.joinToString("_") { it.screen },
            name = "Journey through ${path.size} steps",
            goal = "Reach ${path.lastOrNull()?.screen ?: "nowhere"}",
            preconditions = emptyList(),
            steps = path,
            outcome = null,
            replayable = false,
            verifiedAtScan = null,
        )

    override fun toneOfVoice(copy: List<String>, context: ScanContext): ToneOfVoice =
        ToneOfVoice(
            register = "neutral",
            summary = "Derived from ${copy.size} strings.",
            examples = copy.take(3),
        )
}
