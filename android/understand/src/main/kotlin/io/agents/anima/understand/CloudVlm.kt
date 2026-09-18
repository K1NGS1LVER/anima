package io.agents.anima.understand

import org.json.JSONArray
import org.json.JSONObject

/**
 * Cloud VLM backend (Gemini 2.5 Flash) on the plain REST API.
 *
 * Direct reference: `GeminiPlanner` in `anima.py` -- pure stdlib, no SDK. The
 * one thing added for the stability requirement is a hard `temperature: 0`.
 * Needs a key; without one [completeText] returns null and the hybrid chain
 * simply skips it. INTERNET permission itself is the app's concern, not this
 * module's.
 */
class CloudVlm(
    private val apiKey: String?,
    private val endpoint: String = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
    private val timeoutMs: Int = 12_000,
) : UnderstandingModel {

    override val name: String = "cloud_vlm"
    override val model: String? = "gemini-2.5-flash"

    override fun completeText(system: String, user: String, maxOutputTokens: Int): String? {
        val key = apiKey?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))
            ))
            .put("generationConfig", JSONObject().put("temperature", 0).put("maxOutputTokens", maxOutputTokens))
            .toString()

        val raw = HttpJson.post(endpoint + "?key=$key", body, timeoutMs) ?: return null
        return try {
            val root = JSONObject(raw)
            root.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}