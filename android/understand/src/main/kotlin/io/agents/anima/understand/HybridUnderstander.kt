package io.agents.anima.understand

import io.agents.anima.core.Journey
import io.agents.anima.core.JourneyStep
import io.agents.anima.core.ScanContext
import io.agents.anima.core.Screen
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ScreenProfile
import io.agents.anima.core.ScreenUnderstander
import io.agents.anima.core.ToneOfVoice

/**
 * The composed backend chain: cloud → on-device → heuristic.
 *
 * Mirrors Python's `HybridPlanner`: try the real models in order, and on any
 * failure (no key, no network, endpoint down, malformed JSON) degrade rather
 * than fail. Every model result -- and its [cache]d replica -- passes through
 * the same strict-parse gate, so a model that speaks garbage is indistinguishable
 * from a model that is down.
 *
 * [lastBackend] and [cacheHits] are what `:explore`/`:store` read to fill
 * `scan.understander`.
 */
class HybridUnderstander(
    private val models: List<UnderstandingModel>,
    private val cache: UnderstandCache? = null,
    private val heuristic: HeuristicUnderstander = HeuristicUnderstander(),
) : ScreenUnderstander {

    var lastBackend: String = "heuristic"
        private set
    var cacheHits: Int = 0
        private set

    private fun picked(backend: String) {
        if (backend != "heuristic") lastBackend = backend
    }

    override fun describe(observation: ScreenObservation, screenId: String, context: ScanContext): ScreenProfile {
        cache?.get(screenId)?.let { cached ->
            ScreenProfileJson.parse(cached.text, observation)?.let {
                picked(cached.backend)
                if (cached.backend != "heuristic") cacheHits++
                return it
            }
        }

        val user = PromptBuilder.describeUser(observation, context)
        for (m in models) {
            val raw = safeComplete(m, PromptBuilder.SYSTEM_DESCRIBE, user, 512) ?: continue
            val parsed = ScreenProfileJson.parse(raw, observation) ?: continue
            cache?.put(screenId, CachedResponse(m.name, raw))
            picked(m.name)
            return parsed
        }

        return heuristic.describe(observation, screenId, context).also { picked("heuristic") }
    }

    override fun describeJourney(path: List<JourneyStep>, screens: List<Screen>, context: ScanContext): Journey {
        val id = JourneyLanguage.journeyId(path)
        cache?.get(id)?.let { cached ->
            ScreenProfileJson.parseJourney(cached.text)?.let { j ->
                picked(cached.backend)
                if (cached.backend != "heuristic") cacheHits++
                return assembleJourney(id, j.name, j.goal, path)
            }
        }

        val user = PromptBuilder.journeyUser(path.map { it.screen to it.action.wire })
        for (m in models) {
            val raw = safeComplete(m, PromptBuilder.SYSTEM_JOURNEY, user, 128) ?: continue
            val parsed = ScreenProfileJson.parseJourney(raw) ?: continue
            cache?.put(id, CachedResponse(m.name, raw))
            picked(m.name)
            return assembleJourney(id, parsed.name, parsed.goal, path)
        }

        return heuristic.describeJourney(path, screens, context).also { picked("heuristic") }
    }

    override fun toneOfVoice(copy: List<String>, context: ScanContext): ToneOfVoice {
        val key = "tone:" + Sha.sha256(copy.filter { it.isNotBlank() }.distinct().sorted().joinToString("\n")).take(12)
        cache?.get(key)?.let { cached ->
            ScreenProfileJson.parseTone(cached.text)?.let { t ->
                picked(cached.backend)
                if (cached.backend != "heuristic") cacheHits++
                return ToneOfVoice(t.register, t.summary, t.examples)
            }
        }

        val user = PromptBuilder.toneUser(copy)
        for (m in models) {
            val raw = safeComplete(m, PromptBuilder.SYSTEM_TONE, user, 128) ?: continue
            val parsed = ScreenProfileJson.parseTone(raw) ?: continue
            cache?.put(key, CachedResponse(m.name, raw))
            picked(m.name)
            return ToneOfVoice(parsed.register, parsed.summary, parsed.examples)
        }

        return heuristic.toneOfVoice(copy, context).also { picked("heuristic") }
    }

    private fun assembleJourney(id: String, name: String, goal: String, path: List<JourneyStep>): Journey =
        Journey(
            id = id,
            name = name,
            goal = goal,
            preconditions = emptyList(),
            steps = path,
            outcome = path.lastOrNull()?.screen,
            replayable = false,
            verifiedAtScan = null,
        )

    /**
     * A throwing backend must degrade like a silent one: D5's requirement is
     * "each backend failure lands on the next backend, never ends the scan".
     * Models promise null-when-unavailable, but a buggy model may throw instead
     * of returning null, and an exception here would kill the scan it exists to
     * survive.
     */
    private fun safeComplete(m: UnderstandingModel, system: String, user: String, maxTokens: Int): String? =
        try {
            m.completeText(system, user, maxTokens)
        } catch (e: Exception) {
            null
        }
}