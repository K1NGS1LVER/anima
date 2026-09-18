package io.agents.anima.understand

import io.agents.anima.core.ElementAction
import io.agents.anima.core.InputType
import io.agents.anima.core.JourneyStep
import io.agents.anima.core.ScanContext
import io.agents.anima.core.Screen
import io.agents.anima.core.ScreenKind
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ScreenSignature
import io.agents.anima.core.UiMode
import io.agents.anima.engine.PrunedNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HybridUnderstanderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val ctx = ScanContext("com.example.bank", "Example Bank", emptyList())

    private val obs = ScreenObservation(
        "com.example.bank", "com.example.bank.ui.LoginActivity",
        listOf(
            PrunedNode(1, "android.widget.TextView", text = "hi"),
            PrunedNode(2, "android.widget.EditText", text = null, contentDesc = "OTP"),
            PrunedNode(3, "android.widget.Button", clickable = true),
        ),
        null, 1080 to 2400, UiMode.LIGHT,
    )

    /** Deterministic fake: routes by which task the hybrid asked for. */
    private class FakeModel(
        override val name: String = "fake",
        override val model: String? = "fake-model",
        var reply: String? = "{\"name\": \"Sign in\", \"purpose\": \"Authenticate the user.\", \"kind\": \"auth\"}",
        var replyJourney: String? = "{\"name\": \"Transfer money\", \"goal\": \"Send funds to a payee\"}",
        var replyTone: String? = "{\"register\": \"formal\", \"summary\": \"Courteous.\"}",
    ) : UnderstandingModel {
        override fun completeText(system: String, user: String, maxOutputTokens: Int): String? =
            when (system) {
                PromptBuilder.SYSTEM_JOURNEY -> replyJourney
                PromptBuilder.SYSTEM_TONE -> replyTone
                else -> reply
            }
    }

    private fun hybrid(models: List<UnderstandingModel>, cache: UnderstandCache? = null) =
        HybridUnderstander(models, cache)

    @Test
    fun `all models silent degrades to heuristic, never fails`() {
        val h = hybrid(listOf(FakeModel(reply = null)))
        val p = h.describe(obs, "scr_x", ctx)
        assertEquals("heuristic", h.lastBackend)
        assertEquals(ScreenKind.AUTH, p.kind)
        assertEquals(InputType.OTP, p.inputSpecs.getValue(2).type)
    }

    @Test
    fun `a healthy model wins over heuristic`() {
        val h = hybrid(listOf(FakeModel()))
        val p = h.describe(obs, "scr_x", ctx)
        assertEquals("fake", h.lastBackend)
        assertEquals("Sign in", p.name)
        assertEquals(ScreenKind.AUTH, p.kind)
    }

    @Test
    fun `malformed model output degrades to heuristic instead of crashing`() {
        val h = hybrid(listOf(FakeModel(reply = "I am not JSON at all")))
        val p = h.describe(obs, "scr_x", ctx)
        assertEquals("heuristic", h.lastBackend)
        assertTrue(p.purpose.isNotBlank())
    }

    @Test
    fun `first model's junk is skipped in favour of the second model`() {
        val h = hybrid(listOf(
            FakeModel(name = "junk").also { it.reply = "not json" },
            FakeModel(name = "good"),
        ))
        val p = h.describe(obs, "scr_x", ctx)
        assertEquals("good", h.lastBackend)
        assertEquals("Sign in", p.name)
    }

    @Test
    fun `second describe with same screen id reuses cached text verbatim`() {
        val model = FakeModel(reply = "{\"name\": \"Sign in\", \"purpose\": \"Authenticate the user.\", \"kind\": \"auth\", \"elements\": {\"3\": \"Taps to submit\"}}")
        val h = hybrid(listOf(model), UnderstandCache(tmp.newFolder()))
        h.describe(obs, "scr_x", ctx)
        // Different node CONTENT but the same on-screen ids: the cache must win and
        // return the first scan's exact bytes -- the ids stay valid, so the cached
        // element semantics still map onto this observation.
        val changed = obs.copy(nodes = listOf(
            PrunedNode(1, "android.widget.TextView", text = "changed"),
            PrunedNode(2, "android.widget.EditText", text = null, contentDesc = "definitely changed"),
            PrunedNode(3, "android.widget.Button", clickable = true),
        ))
        val again = h.describe(changed, "scr_x", ctx)

        assertEquals("Sign in", again.name)
        assertEquals(1, h.cacheHits)
        assertEquals("fake", h.lastBackend)
        assertTrue("Taps to submit" in again.elementSemantics.values)
    }

    @Test
    fun `journey is named and summarized with a stable id and is not replayable`() {
        val h = hybrid(listOf(FakeModel(replyJourney = "{\"name\": \"Transfer money\", \"goal\": \"Send funds to a payee\"}")))
        val path = listOf(
            JourneyStep("scr_a", "el_1", ElementAction.TAP),
            JourneyStep("scr_b", "el_2", ElementAction.INPUT, "{amount}"),
        )
        val screens = listOf(
            Screen("scr_a", "Home", "Shows the overview.", ScreenKind.LIST, ScreenSignature("h", emptyList(), null), emptyList(), null, listOf(UiMode.LIGHT)),
            Screen("scr_b", "Amount", "Collects the amount.", ScreenKind.FORM, ScreenSignature("h", emptyList(), null), emptyList(), null, listOf(UiMode.LIGHT)),
        )
        val j = h.describeJourney(path, screens, ctx)

        assertTrue(j.id.startsWith("jr_"))
        assertEquals("Transfer money", j.name)
        assertEquals("Send funds to a payee", j.goal)
        assertFalse(j.replayable)
        assertEquals("scr_b", j.outcome)
        // The id derives from the path, so the same path always names the same journey.
        assertEquals(j.id, JourneyLanguage.journeyId(path))
    }

    @Test
    fun `heuristic journey fallback still names the flow`() {
        val h = hybrid(emptyList())
        val path = listOf(JourneyStep("scr_a", "el_1", ElementAction.TAP))
        val screens = listOf(Screen("scr_a", "Home", null, ScreenKind.LIST, ScreenSignature("h", emptyList(), null), emptyList(), null, listOf(UiMode.LIGHT)))
        val j = h.describeJourney(path, screens, ctx)
        assertEquals("heuristic", h.lastBackend)
        assertTrue(j.name.isNotBlank())
        assertTrue(j.goal.isNotBlank())
    }

    @Test
    fun `tone classification is exercised end to end`() {
        val h = hybrid(emptyList())
        val copy = listOf("Please verify your details.", "Thank you for your patience.")
        val t = h.toneOfVoice(copy, ctx)
        assertEquals("formal", t.register)
        assertTrue(t.summary.isNotBlank())
    }

    /** A model that violates its contract by throwing instead of returning null. */
    private class ThrowingModel(
        override val name: String = "thrower",
        override val model: String? = null,
    ) : UnderstandingModel {
        override fun completeText(system: String, user: String, maxOutputTokens: Int): String? =
            throw RuntimeException("simulated model crash")
    }

    @Test
    fun `a throwing backend degrades to the next model, never ends the scan`() {
        val h = hybrid(listOf(ThrowingModel(), FakeModel()))
        val p = h.describe(obs, "scr_x", ctx)
        assertEquals("fake", h.lastBackend)
        assertEquals("Sign in", p.name)
    }

    @Test
    fun `a single throwing backend degrades all the way to heuristic`() {
        val h = hybrid(listOf(ThrowingModel()))
        val p = h.describe(obs, "scr_x", ctx)
        assertEquals("heuristic", h.lastBackend)
        assertTrue(p.purpose.isNotBlank())
    }
}