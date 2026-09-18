package io.agents.anima.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * Storage contract for compiled skills.
 *
 * Intent keys are ALWAYS `trim().lowercase()` on both save and lookup, exactly as `SkillDB` does
 * in `anima.py`. Implementations are interchangeable: [SkillStore] (SQLite, on device) and
 * [MemorySkillStore] (hermetic, used by the 3-act demo and unit tests).
 */
interface SkillRepository {

    fun get(intent: String): Skill?

    fun save(skill: Skill)

    fun allSkills(): List<Skill>

    /**
     * Exact match first, then parameter-slot template match ("set alarm for {time}").
     *
     * @return (skill, extractedParams); (null, emptyMap) when nothing matches.
     */
    fun findMatch(userGoal: String): Pair<Skill?, Map<String, String>>
}

/**
 * In-memory twin of [SkillStore].
 *
 * Deliberately stores serialized rows (intent -> steps_json + counters) exactly like the SQLite
 * table does, so replay semantics — including copy-on-read, which matters for mid-run locator
 * healing — are identical between the two implementations.
 */
class MemorySkillStore : SkillRepository {

    private data class Row(val stepsJson: String, val successCount: Int, val failureCount: Int)

    private val rows = LinkedHashMap<String, Row>()

    override fun get(intent: String): Skill? {
        val clean = intent.trim().lowercase()
        val row = rows[clean] ?: return null
        return Skill(
            intent = clean,
            steps = SkillJson.stepsFromJson(row.stepsJson),
            successCount = row.successCount,
            failureCount = row.failureCount,
        )
    }

    override fun save(skill: Skill) {
        rows[skill.intent.trim().lowercase()] =
            Row(SkillJson.stepsToJson(skill.steps), skill.successCount, skill.failureCount)
    }

    override fun allSkills(): List<Skill> = rows.map { (intent, row) ->
        Skill(intent, SkillJson.stepsFromJson(row.stepsJson), row.successCount, row.failureCount)
    }

    override fun findMatch(userGoal: String): Pair<Skill?, Map<String, String>> {
        val clean = userGoal.trim().lowercase()
        val exact = get(clean)
        if (exact != null) return Pair(exact, emptyMap())

        for ((intent, row) in rows) {
            if (!TemplateMatcher.isTemplate(intent)) continue
            val extracted = TemplateMatcher.match(intent, clean) ?: continue
            val skill = Skill(
                intent = intent,
                steps = SkillJson.stepsFromJson(row.stepsJson),
                successCount = row.successCount,
                failureCount = row.failureCount,
            )
            return Pair(skill, extracted)
        }
        return Pair(null, emptyMap())
    }

    fun clear() = rows.clear()

    fun size(): Int = rows.size
}

/**
 * Skill library import / export in the exact format `SkillDB.export_json` writes, so a library can
 * move between the Python runtime and the phone in either direction.
 *
 *   [{"intent": "...", "steps": [...], "success": 1, "failure": 0}, ...]
 */
object SkillsJsonIo {

    fun export(repo: SkillRepository): String {
        val sb = StringBuilder()
        sb.append('[')
        repo.allSkills().forEachIndexed { index, skill ->
            if (index > 0) sb.append(',')
            sb.append('{')
            sb.append("\"intent\":").append(jsonQuote(skill.intent)).append(',')
            sb.append("\"steps\":").append(SkillJson.stepsToJson(skill.steps)).append(',')
            sb.append("\"success\":").append(skill.successCount).append(',')
            sb.append("\"failure\":").append(skill.failureCount)
            sb.append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    /** @return the number of skills imported. */
    fun import(repo: SkillRepository, json: String?): Int {
        if (json.isNullOrBlank()) return 0
        val arr = try {
            JSONArray(json)
        } catch (e: Exception) {
            return 0
        }
        var count = 0
        for (i in 0 until arr.length()) {
            val item: JSONObject = arr.optJSONObject(i) ?: continue
            val intent = item.optString("intent", "")
            if (intent.isEmpty()) continue
            val stepsArr = item.optJSONArray("steps") ?: JSONArray()
            val steps = ArrayList<Step>(stepsArr.length())
            for (j in 0 until stepsArr.length()) {
                val stepObj = stepsArr.optJSONObject(j) ?: continue
                steps.add(SkillJson.stepFromJson(stepObj))
            }
            repo.save(
                Skill(
                    intent = intent,
                    steps = steps,
                    successCount = item.optInt("success", 0),
                    failureCount = item.optInt("failure", 0),
                )
            )
            count += 1
        }
        return count
    }
}
