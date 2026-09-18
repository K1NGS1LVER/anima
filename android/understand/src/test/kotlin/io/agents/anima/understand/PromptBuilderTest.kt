package io.agents.anima.understand

import io.agents.anima.core.ScanContext
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.UiMode
import io.agents.anima.engine.PrunedNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    private val ctx = ScanContext("com.example.bank", "Example Bank", emptyList())

    @Test
    fun `dom stays under the char budget no matter how many nodes`() {
        val nodes = (1..500).map {
            PrunedNode(
                id = it,
                className = "android.widget.FrameLayout",
                resourceId = null,
                text = if (it % 8 == 0) "a very long inert label that repeats ".repeat(6) else null,
                contentDesc = null,
                clickable = it % 5 == 0,
            )
        }
        val dom = PromptBuilder.dom(nodes)
        assertTrue("dom len ${dom.length}", dom.length <= PromptBuilder.MAX_DOM_CHARS)
    }

    @Test
    fun `interactive nodes survive truncation ahead of inert containers`() {
        val nodes = (1..400).map {
            PrunedNode(it, "android.widget.TextView", text = "text ".repeat(4), clickable = it % 3 == 0)
        }
        val dom = PromptBuilder.dom(nodes)
        val arr = org.json.JSONArray(dom)
        assertTrue("first serialized node must be interactive", arr.getJSONObject(0).getBoolean("clickable"))
    }

    @Test
    fun `every serialized node carries a numeric id, a role and a class`() {
        val o = ScreenObservation(
            "com.example.bank", "com.example.bank.ui.LoginActivity",
            listOf(
                PrunedNode(7, "android.widget.EditText", text = null, contentDesc = "Password"),
                PrunedNode(9, "android.widget.Button", text = "Sign in", clickable = true),
            ),
            null, 1080 to 2400, UiMode.LIGHT,
        )
        val user = PromptBuilder.describeUser(o, ctx)
        assertTrue(user.contains("\"id\":7"))
        assertTrue(user.contains("\"role\":\"input\""))
        assertTrue(user.contains("\"class\":\"android.widget.EditText\""))
        assertTrue(user.contains("com.example.bank.ui.LoginActivity"))
        assertTrue(user.length < 20_000)
    }

    @Test
    fun `journey and tone prompts mention their task`() {
        val j = PromptBuilder.journeyUser(listOf("scr_a" to "tap", "scr_b" to "input"))
        assertTrue(j.contains("tap"))
        assertTrue(j.contains("input"))
        val t = PromptBuilder.toneUser(listOf("Your money is on its way"))
        assertTrue(t.contains("Your money is on its way"))
    }
}