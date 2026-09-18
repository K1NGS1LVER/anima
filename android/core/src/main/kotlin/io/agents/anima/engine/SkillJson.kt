package io.agents.anima.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Cross-runtime skill serialization.
 *
 * The on-disk shape is byte-for-byte the shape `anima.py` writes (`SkillDB.save` /
 * `SkillDB.export_json`), so a `skills.json` exported by the Python runtime imports into the phone
 * unchanged and vice versa:
 *
 *   [{"action":"tap",
 *     "locator":{"resource_id":...,"content_desc":...,"text":...,"class_name":...,
 *                "bounds":[...],"rel_bounds":[...],"rel_center":[...]},
 *     "param_slot":null,"value":null}]
 *
 * Writing is done by hand (rather than via `JSONObject.toString()`) because key ordering must be
 * deterministic: `org.json.JSONObject` is backed by a HashMap on the JVM and a LinkedHashMap on
 * Android, so its output order is not stable across the two. Reading uses `org.json`, which is
 * available both on device and in unit tests.
 */
object SkillJson {

    // ----------------------------------------------------------------- write

    fun stepsToJson(steps: List<Step>): String {
        val sb = StringBuilder()
        sb.append('[')
        steps.forEachIndexed { index, step ->
            if (index > 0) sb.append(',')
            appendStep(sb, step)
        }
        sb.append(']')
        return sb.toString()
    }

    private fun appendStep(sb: StringBuilder, step: Step) {
        sb.append('{')
        sb.append("\"action\":").append(jsonQuote(step.action)).append(',')
        sb.append("\"locator\":")
        appendLocator(sb, step.locator)
        sb.append(',')
        sb.append("\"param_slot\":").append(jsonNullable(step.paramSlot)).append(',')
        sb.append("\"value\":").append(jsonNullable(step.value))
        sb.append('}')
    }

    private fun appendLocator(sb: StringBuilder, loc: Locator) {
        sb.append('{')
        sb.append("\"resource_id\":").append(jsonNullable(loc.resourceId)).append(',')
        sb.append("\"content_desc\":").append(jsonNullable(loc.contentDesc)).append(',')
        sb.append("\"text\":").append(jsonNullable(loc.text)).append(',')
        sb.append("\"class_name\":").append(jsonNullable(loc.className)).append(',')
        sb.append("\"bounds\":").append(intArray(loc.bounds)).append(',')
        sb.append("\"rel_bounds\":").append(doubleArray(loc.relBounds)).append(',')
        sb.append("\"rel_center\":").append(doubleArray(loc.relCenter))
        sb.append('}')
    }

    // ------------------------------------------------------------------ read

    fun stepsFromJson(json: String?): List<Step> {
        if (json.isNullOrBlank()) return emptyList()
        val out = ArrayList<Step>()
        val arr = try {
            JSONArray(json)
        } catch (e: Exception) {
            return emptyList()
        }
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            out.add(stepFromJson(obj))
        }
        return out
    }

    fun stepFromJson(obj: JSONObject): Step = Step(
        action = obj.optString("action", "tap"),
        locator = locatorFromJson(obj.optJSONObject("locator")),
        paramSlot = optNullableString(obj, "param_slot"),
        value = optNullableString(obj, "value"),
    )

    fun locatorFromJson(obj: JSONObject?): Locator {
        if (obj == null) return Locator()
        return Locator(
            resourceId = optNullableString(obj, "resource_id"),
            contentDesc = optNullableString(obj, "content_desc"),
            text = optNullableString(obj, "text"),
            className = optNullableString(obj, "class_name"),
            bounds = optIntList(obj, "bounds"),
            relBounds = optDoubleList(obj, "rel_bounds"),
            relCenter = optDoubleList(obj, "rel_center"),
        )
    }

    private fun optNullableString(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.optString(key, "")
    }

    private fun optIntList(obj: JSONObject, key: String): List<Int>? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val arr = obj.optJSONArray(key) ?: return null
        val out = ArrayList<Int>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.optInt(i, 0))
        return out
    }

    private fun optDoubleList(obj: JSONObject, key: String): List<Double>? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val arr = obj.optJSONArray(key) ?: return null
        val out = ArrayList<Double>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.optDouble(i, 0.0))
        return out
    }

    // --------------------------------------------------------------- helpers

    private fun jsonNullable(value: String?): String = if (value == null) "null" else jsonQuote(value)

    private fun intArray(values: List<Int>?): String {
        if (values == null) return "null"
        return values.joinToString(prefix = "[", postfix = "]", separator = ",")
    }

    private fun doubleArray(values: List<Double>?): String {
        if (values == null) return "null"
        return values.joinToString(prefix = "[", postfix = "]", separator = ",") { it.toString() }
    }
}

/**
 * Minimal, deterministic JSON string quoting. Kept dependency-free so serialization behaves
 * identically on the JVM and on Android (the two bundled `org.json` builds escape different
 * optional characters, e.g. Android escapes `/`).
 */
internal fun jsonQuote(value: String): String {
    val sb = StringBuilder(value.length + 2)
    sb.append('"')
    for (c in value) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '' -> sb.append("\\f")
            else -> {
                if (c < ' ') {
                    sb.append("\\u")
                    val hex = Integer.toHexString(c.code)
                    for (i in 0 until 4 - hex.length) sb.append('0')
                    sb.append(hex)
                } else {
                    sb.append(c)
                }
            }
        }
    }
    sb.append('"')
    return sb.toString()
}
