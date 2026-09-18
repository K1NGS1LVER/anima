package io.agents.anima.understand

import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device LLM backend, talking to a local model server.
 *
 * Deliberately mirrors the repo's existing `LocalLiteRTPlanner` pattern
 * (`anima.py`): a small HTTP client over `http://127.0.0.1:8080` that runs a
 * quantized model the user supplies (Gemma via LiteRT). It is NOT an embedded
 * MediaPipe runtime -- the APK stays dependency-free and offline, and anything
 * heavier than this is a product decision for the app module, not a contract
 * of `:understand`.
 */
class LocalLlm(
    private val endpointUrl: String = "http://127.0.0.1:8080/v1/chat/completions",
    private val modelName: String = "gemma-4-it-q4",
    private val timeoutMs: Int = 8_000,
) : UnderstandingModel {

    override val name: String = "on_device_llm"
    override val model: String? = modelName

    override fun completeText(system: String, user: String, maxOutputTokens: Int): String? {
        val body = JSONObject()
            .put("model", modelName)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
            .put("temperature", 0)
            .put("max_tokens", maxOutputTokens)
            .toString()

        val raw = HttpJson.post(endpointUrl, body, timeoutMs) ?: return null
        return try {
            JSONObject(raw)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
}