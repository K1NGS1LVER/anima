package io.agents.anima.understand

import io.agents.anima.core.InputType
import io.agents.anima.core.ScreenKind
import org.json.JSONObject

/**
 * Strict-parse helpers for model output.
 *
 * Models are not parsers. Everything the model writes passes through here, and
 * a malformed response must produce a null (so the caller degrades to the next
 * backend, then to heuristics) -- never an exception, never a half-read object.
 */
object JsonResponse {

    /**
     * Extracts the first balanced `{...}` object from raw model text.
     *
     * Tolerates the two most common model habits: wrapping the object in a
     * ```json``` code fence, and adding prose around it ("Here is the JSON:").
     * Uses brace-depth scanning rather than regex, so nested objects inside
     * string values survive.
     */
    fun extractObject(text: String?): JSONObject? {
        if (text == null) return null
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) {
                        return try {
                            JSONObject(text.substring(start, i + 1))
                        } catch (e: org.json.JSONException) {
                            null
                        }
                    }
                }
            }
        }
        return null
    }

    /** First string field, cleaned, or null when blank/absent. */
    fun string(obj: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!obj.has(key)) continue
            val v = obj.opt(key)
            if (v is String && v.isNotBlank()) return v.trim()
        }
        return null
    }

    /** Coerces a model's kind label into the frozen enum, null when unknown. */
    fun coerceKind(any: Any?): ScreenKind? {
        val raw = (any as? String)?.trim()?.lowercase() ?: return null
        val s = raw.replace("_", " ").replace("-", " ").trim()
        return when (s) {
            "list", "listing", "catalog", "feed", "dashboard", "home" -> ScreenKind.LIST
            "form", "formview" -> ScreenKind.FORM
            "detail", "details", "detailview", "item", "detail screen", "content" -> ScreenKind.DETAIL
            "dialog", "popup", "alert", "modal", "popover", "bottomsheet", "bottom sheet", "snackbar" -> ScreenKind.DIALOG
            "onboarding", "welcome", "intro", "tour", "getting started", "permission", "permission gating" -> ScreenKind.ONBOARDING
            "auth", "login", "signin", "sign in", "signup", "sign up", "registration",
            "register", "verify", "verification", "otp", "two factor", "2fa", "password recovery",
            "forgot password", "welcome back" -> ScreenKind.AUTH
            "settings", "preferences", "config", "configuration", "options", "setup" -> ScreenKind.SETTINGS
            "other", "unknown", "landing", "generic" -> ScreenKind.OTHER
            else -> null
        }
    }

    /** Coerces a model's input-type label into the frozen enum, null when unknown. */
    fun coerceInputType(any: Any?): InputType? {
        val raw = (any as? String)?.trim()?.lowercase() ?: return null
        val s = raw.replace("_", " ").replace("-", " ").trim()
        return when (s) {
            "email", "e mail" -> InputType.EMAIL
            "phone", "telephone", "mobile", "mobile number", "phone number", "tel" -> InputType.PHONE
            "otp", "one time password", "verification code", "verify code", "security code", "sms", "pin" -> InputType.OTP
            "amount", "money", "currency", "price", "payment amount" -> InputType.AMOUNT
            "date", "dob", "birthdate", "expiry" -> InputType.DATE
            "password", "passcode", "secret", "pin code" -> InputType.PASSWORD
            "text", "string", "name", "search", "username", "full name", "textarea", "multiline" -> InputType.TEXT
            else -> null
        }
    }
}