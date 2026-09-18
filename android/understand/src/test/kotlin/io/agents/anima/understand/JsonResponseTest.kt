package io.agents.anima.understand

import io.agents.anima.core.InputType
import io.agents.anima.core.ScreenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class JsonResponseTest {

    @Test
    fun `extracts object from json code fence`() {
        val text = "```json\n{\"name\": \"X\"}\n```"
        assertEquals("X", JsonResponse.extractObject(text)?.optString("name"))
    }

    @Test
    fun `extracts object surrounded by prose`() {
        val text = "Here is the JSON you asked for: {\"purpose\": \"does a thing\"} -- hope that helps!"
        assertEquals("does a thing", JsonResponse.extractObject(text)?.optString("purpose"))
    }

    @Test
    fun `object with nested braces inside string values survives`() {
        val text = "{\"purpose\": \"shows {this} literally\", \"kind\": \"list\"}"
        val obj = JsonResponse.extractObject(text)
        assertNotNull(obj)
        assertEquals("shows {this} literally", obj!!.optString("purpose"))
    }

    @Test
    fun `garbage and unbalanced text return null, never throw`() {
        assertNull(JsonResponse.extractObject("not json at all"))
        assertNull(JsonResponse.extractObject("{\"name\": \"x\"]]]]"))
        assertNull(JsonResponse.extractObject(""))
        assertNull(JsonResponse.extractObject(null))
        assertNull(JsonResponse.extractObject("{"))
    }

    @Test
    fun `kind coercion covers the eight wire values and common aliases`() {
        assertEquals(ScreenKind.LIST, JsonResponse.coerceKind("list"))
        assertEquals(ScreenKind.AUTH, JsonResponse.coerceKind("Sign In"))
        assertEquals(ScreenKind.AUTH, JsonResponse.coerceKind("OTP"))
        assertEquals(ScreenKind.ONBOARDING, JsonResponse.coerceKind("onboarding"))
        assertEquals(ScreenKind.SETTINGS, JsonResponse.coerceKind("Preferences"))
        assertEquals(ScreenKind.DIALOG, JsonResponse.coerceKind("bottom_sheet"))
        assertEquals(ScreenKind.FORM, JsonResponse.coerceKind("form"))
        assertEquals(ScreenKind.OTHER, JsonResponse.coerceKind("other"))
        // Unknown kinds are not guessed here; the parse gate repairs them to OTHER.
        assertNull(JsonResponse.coerceKind("???"))
        assertNull(JsonResponse.coerceKind(null))
    }

    @Test
    fun `input type coercion covers all seven types`() {
        assertEquals(InputType.EMAIL, JsonResponse.coerceInputType("email"))
        assertEquals(InputType.PHONE, JsonResponse.coerceInputType("mobile number"))
        assertEquals(InputType.OTP, JsonResponse.coerceInputType("one_time_password"))
        assertEquals(InputType.AMOUNT, JsonResponse.coerceInputType("Amount"))
        assertEquals(InputType.DATE, JsonResponse.coerceInputType("dob"))
        assertEquals(InputType.PASSWORD, JsonResponse.coerceInputType("passcode"))
        assertEquals(InputType.TEXT, JsonResponse.coerceInputType("text"))
        assertNull(JsonResponse.coerceInputType("color"))
    }
}