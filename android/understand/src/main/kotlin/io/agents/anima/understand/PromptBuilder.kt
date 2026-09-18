package io.agents.anima.understand

import io.agents.anima.core.ElementRole
import io.agents.anima.core.ScanContext
import io.agents.anima.core.ScreenObservation
import io.agents.anima.engine.PrunedNode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Prompt construction and token discipline.
 *
 * `:capture` already prunes the raw tree via UIFormer; this builder must not
 * undo that by re-inflating the prompt. The Agent-DOM below is a flat list of
 * the semantic fields a model needs, hard-capped at [MAX_DOM_CHARS] with
 * interactive and input nodes kept ahead of inert ones.
 */
object PromptBuilder {

    const val MAX_DOM_CHARS = 6_000

    /** Agent-DOM: the flat, compact serialization a model consumes. */
    fun dom(nodes: List<PrunedNode>): String {
        // Inert text containers are the first thing to go when the budget is
        // tight; keep every interactive/input node ahead of them.
        val rank = { n: PrunedNode ->
            when {
                n.clickable || Roles.roleOf(n) == ElementRole.INPUT -> 0
                n.text != null || n.contentDesc != null -> 1
                else -> 2
            }
        }
        val ordered = nodes
            .filter { n -> rank(n) <= 1 }
            .sortedWith(compareBy({ rank(it) }, { nodes.indexOf(it) }))
        val arr = JSONArray()
        var size = arr.toString().length
        for (n in ordered) {
            val njson = nodeJson(n)
            val separator = if (arr.length() == 0) 0 else 1
            val bytes = njson.toString().length
            if (size + separator + bytes > MAX_DOM_CHARS) break
            arr.put(njson)
            size += separator + bytes
        }
        return arr.toString()
    }

    fun nodeJson(n: PrunedNode): JSONObject {
        val label = when {
            !n.text.isNullOrBlank() -> n.text
            !n.contentDesc.isNullOrBlank() -> n.contentDesc
            else -> JSONObject.NULL
        }
        return JSONObject()
            .put("id", n.id)
            .put("role", Roles.roleOf(n).wire)
            .put("label", label)
            .put("resource_id", n.resourceId ?: JSONObject.NULL)
            .put("class", n.className)
            .put("bounds_rel", JSONArray(n.relBounds))
            .put("clickable", n.clickable)
            .put("checked", n.checked)
    }

    const val SYSTEM_DESCRIBE = """You are an Android app cartographer. Describe the current screen from its accessibility Agent-DOM.
Return ONLY a JSON object, no prose, no markdown:
{"name": "human screen name, <=5 words",
 "purpose": "what this screen does, <=20 words",
 "kind": "list|form|detail|dialog|onboarding|auth|settings|other",
 "elements": {"<nodeId>": "one-sentence semantic of what tapping/reading this element does"},
 "inputs": {"<nodeId>": {"type": "email|phone|otp|amount|date|password|text", "required": true|false, "hint": "placeholder text or null", "max_length": 32|null}}}
Rules: key "elements"/"inputs" must reference only node ids present in the Agent-DOM. When a field type is unclear choose "text". When a label is unclear leave the semantic generic. Never invent ids."""

    const val SYSTEM_JOURNEY = """You are an Android app cartographer. A journey is a multi-screen path a user takes to accomplish something.
Given the steps, return ONLY a JSON object:
{"name": "short journey name, <=5 words",
 "goal": "what the user accomplishes, <=15 words"}"""

    const val SYSTEM_TONE = """You are an Android app cartographer. Classify the tone of voice of an app from sample strings it shows to users.
Return ONLY a JSON object:
{"register": "formal|friendly|terse|playful",
 "summary": "one sentence, <=20 words",
 "examples": ["up to 3 short verbatim examples"]}"""

    fun describeUser(o: ScreenObservation, ctx: ScanContext): String {
        val known = if (ctx.knownScreenNames.isNotEmpty()) {
            ctx.knownScreenNames.take(8).joinToString(", ")
        } else {
            "(first screen of this scan)"
        }
        return "App: ${ctx.appLabel} (${ctx.appPackage})\n" +
            "Activity: ${o.activity ?: "unknown"}\n" +
            "UI mode: ${o.uiMode.wire}\n" +
            "Already described: $known\n\n" +
            "Agent-DOM:\n${dom(o.nodes)}"
    }

    fun journeyUser(path: List<Pair<String, String>>): String {
        val steps = path.joinToString("\n") { (screen, action) -> "- $action on $screen" }
        return "Steps:\n$steps"
    }

    fun toneUser(copy: List<String>): String {
        return copy.take(40).joinToString("\n") { "\"${it.replace("\"", "'")}\"" }
    }
}