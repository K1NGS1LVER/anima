package io.agents.anima.understand

import org.json.JSONObject
import java.io.File

/**
 * Durable, file-backed cache of model responses keyed by stable id.
 *
 * The stability requirement is a *durability* requirement -- two separate scan
 * runs must produce identical text, so an in-memory map is not a cache here.
 * Each id is a thin JSON file recording which backend answered and the raw text
 * verbatim, so a rescan reuses the exact bytes the first scan produced. The
 * durable SQLite store is Jacob's; this is the smallest thing that satisfies
 * the contract without reaching into `:store`.
 */
class UnderstandCache(private val dir: File) {

    init {
        dir.mkdirs()
    }

    fun get(key: String): CachedResponse? {
        val file = fileFor(key)
        if (!file.isFile) return null
        return try {
            val obj = JSONObject(file.readText())
            val text = obj.optString("text")
            if (text.isBlank()) null else CachedResponse(obj.optString("backend", "heuristic"), text)
        } catch (e: Exception) {
            null
        }
    }

    fun put(key: String, response: CachedResponse) {
        try {
            fileFor(key).writeText(
                JSONObject()
                    .put("backend", response.backend)
                    .put("text", response.text)
                    .toString()
            )
        } catch (e: Exception) {
            // A cache write must never fail a scan.
        }
    }

    private fun fileFor(key: String): File = File(dir, sanitize(key) + ".json")

    private fun sanitize(key: String): String = key.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}