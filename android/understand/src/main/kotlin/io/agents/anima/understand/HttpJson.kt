package io.agents.anima.understand

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal stdlib HTTP POST. No third-party client is worth the dependency for
 * two call sites that both send JSON to a chat-completions-style endpoint.
 *
 * Returns the response body, or null on any transport/HTTP error -- the caller
 * treats null as "backend unavailable" and falls back. Never throws.
 */
object HttpJson {

    fun post(url: String, body: String, timeoutMs: Int = 10_000): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { s ->
                BufferedReader(InputStreamReader(s, Charsets.UTF_8)).use { it.readText() }
            }
            if (code in 200..299) text else null
        } catch (e: Exception) {
            null
        }
    }
}