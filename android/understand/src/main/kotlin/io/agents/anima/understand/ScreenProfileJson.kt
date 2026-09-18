package io.agents.anima.understand

import io.agents.anima.core.InputSpec
import io.agents.anima.core.InputType
import io.agents.anima.core.ScreenKind
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ScreenProfile
import org.json.JSONObject

/**
 * Model response → frozen [ScreenProfile] types.
 *
 * Requires a non-blank `name` and `purpose` -- a model response missing either
 * is degenerate and the whole parse fails (null), so the hybrid chain degrades
 * to the next backend and finally to heuristics. Single bad fields are repaired
 * in place instead: an unknown `kind` becomes [ScreenKind.OTHER], an unknown
 * `input.type` drops that one input rather than failing the screen.
 */
object ScreenProfileJson {

    data class ParseResult(
        val profile: ScreenProfile,
        val returnedModelKind: Boolean,
    )

    fun parse(raw: String, o: ScreenObservation): ScreenProfile? {
        val obj = JsonResponse.extractObject(raw) ?: return null
        val name = JsonResponse.string(obj, "name", "screen_name") ?: return null
        val purpose = JsonResponse.string(obj, "purpose", "description") ?: return null
        val kind = JsonResponse.coerceKind(obj.opt("kind")) ?: ScreenKind.OTHER

        val semantics = parseSemantics(obj.optJSONObject("elements"), o)
        val inputs = parseInputs(obj.optJSONObject("inputs"), o)
        return ScreenProfile(name, purpose, kind, semantics, inputs)
    }

    private fun parseSemantics(elements: JSONObject?, o: ScreenObservation): Map<Int, String> {
        if (elements == null) return emptyMap()
        val ids = o.nodes.map { it.id }.toSet()
        val out = LinkedHashMap<Int, String>()
        for (key in elements.keys()) {
            val id = key.toIntOrNull() ?: continue
            if (id !in ids) continue
            val semantic = elements.optString(key).trim()
            if (semantic.isNotEmpty()) out[id] = semantic
        }
        return out
    }

    private fun parseInputs(inputs: JSONObject?, o: ScreenObservation): Map<Int, InputSpec> {
        if (inputs == null) return emptyMap()
        val ids = o.nodes.map { it.id }.toSet()
        val out = LinkedHashMap<Int, InputSpec>()
        for (key in inputs.keys()) {
            val id = key.toIntOrNull() ?: continue
            if (id !in ids) continue
            val spec = inputs.optJSONObject(key) ?: continue
            val type = JsonResponse.coerceInputType(spec.opt("type")) ?: continue
            out[id] = InputSpec(
                type = type,
                required = spec.optBoolean("required", false),
                hint = spec.optString("hint").takeUnless { it.isBlank() },
                maxLength = (spec.opt("max_length") as? Number)?.toInt(),
            )
        }
        return out
    }

    /** Journey naming response: `{"name", "goal"}`. Null when degenerate. */
    data class JourneyName(val name: String, val goal: String)

    fun parseJourney(raw: String): JourneyName? {
        val obj = JsonResponse.extractObject(raw) ?: return null
        val name = JsonResponse.string(obj, "name") ?: return null
        val goal = JsonResponse.string(obj, "goal") ?: return null
        return JourneyName(name, goal)
    }

    /** Tone response: `{"register", "summary", "examples"}`. Null when degenerate. */
    data class ToneResult(val register: String, val summary: String, val examples: List<String>)

    private val TONE_REGISTERS = listOf("formal", "friendly", "terse", "playful")

    fun parseTone(raw: String): ToneResult? {
        val obj = JsonResponse.extractObject(raw) ?: return null
        val register = JsonResponse.string(obj, "register")?.lowercase() ?: return null
        if (register !in TONE_REGISTERS) return null
        val summary = JsonResponse.string(obj, "summary") ?: return null
        val arr = obj.optJSONArray("examples")
        val examples = if (arr == null) emptyList() else
            (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }.take(3)
        return ToneResult(register, summary, examples)
    }
}