package io.agents.anima.engine

/**
 * Extracts dynamic parameter slots out of goals and typed values, so that
 * "set alarm for 7:30 am" compiles into the reusable template "set alarm for {time}".
 *
 * Patterns are ordered; the first one that matches wins. Ported from `anima.py`.
 */
object ParameterExtractor {

    val SLOT_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("""\b(\d{1,2}:\d{2}(?:\s*[ap]m)?)\b""", RegexOption.IGNORE_CASE) to "time",
        Regex("""\b([a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+)\b""", RegexOption.IGNORE_CASE) to "email",
        Regex("""\b(\d{3,})\b""", RegexOption.IGNORE_CASE) to "number",
    )

    const val DEFAULT_SLOT = "value"

    /** Names the slot a raw value belongs to: time / email / number, else "value". */
    fun slotNameFor(value: String): String {
        for ((pattern, name) in SLOT_PATTERNS) {
            if (pattern.containsMatchIn(value)) return name
        }
        return DEFAULT_SLOT
    }

    /**
     * If [inputValue] appears verbatim in [goal], replaces every occurrence with `{slotName}`.
     *
     * @return (templatedGoal, slotName) or (goal, null) when there is nothing to parameterize.
     */
    fun parameterizeGoal(goal: String, inputValue: String?): Pair<String, String?> {
        if (inputValue.isNullOrBlank()) return Pair(goal, null)

        val valClean = inputValue.trim()
        if (!goal.contains(valClean)) return Pair(goal, null)

        val slotName = slotNameFor(valClean)
        return Pair(goal.replace(valClean, "{$slotName}"), slotName)
    }
}

/**
 * Turns a stored template intent such as "set alarm for {time}" into a regex and extracts the
 * slot values out of a concrete user goal.
 *
 * Deviation from Python, documented deliberately: CPython builds *named* capture groups
 * (`(?P<name>.+?)`), but the JVM only accepts group names matching `[a-zA-Z][a-zA-Z0-9]*`, which
 * would reject slot names containing underscores (e.g. `{contact_name}`). We therefore emit plain
 * positional groups `(.+?)` and keep an ordered list of slot names, mapping group N back to
 * slotNames[N-1]. The resulting map is identical to Python's `match.groupdict()` for every slot
 * name Python accepts, and additionally supports underscored names.
 */
object TemplateMatcher {

    /** Matches Python's `re.sub(r"\\\{([a-zA-Z_]+)\\\}", ...)` slot syntax. */
    private val SLOT = Regex("""\{([a-zA-Z_]+)\}""")

    /** Equivalent of the SQL filter `intent LIKE '%{%}%'`. */
    fun isTemplate(intent: String): Boolean {
        val open = intent.indexOf('{')
        if (open < 0) return false
        return intent.indexOf('}', open + 1) > open
    }

    /**
     * Full-match [cleanGoal] against [templateIntent].
     *
     * @return the extracted slot values in template order, or null when it does not match.
     */
    fun match(templateIntent: String, cleanGoal: String): Map<String, String>? {
        val slotNames = ArrayList<String>()
        val pattern = StringBuilder()
        var last = 0
        for (m in SLOT.findAll(templateIntent)) {
            pattern.append(Regex.escape(templateIntent.substring(last, m.range.first)))
            pattern.append("(.+?)")
            slotNames.add(m.groupValues[1])
            last = m.range.last + 1
        }
        if (slotNames.isEmpty()) return null
        pattern.append(Regex.escape(templateIntent.substring(last)))

        val result = try {
            Regex(pattern.toString()).matchEntire(cleanGoal)
        } catch (e: Exception) {
            null
        } ?: return null

        val extracted = LinkedHashMap<String, String>()
        slotNames.forEachIndexed { index, name ->
            extracted[name] = result.groupValues[index + 1]
        }
        return extracted
    }
}
