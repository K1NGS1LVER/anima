package io.agents.anima.understand

import io.agents.anima.core.InputType
import io.agents.anima.core.ScanContext
import io.agents.anima.core.ScreenKind
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.UiMode
import io.agents.anima.engine.PrunedNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicUnderstanderTest {

    private val ctx = ScanContext("com.example.bank", "Example Bank", emptyList())

    private fun obs(
        activity: String?,
        nodes: List<PrunedNode>,
        packageName: String = "com.example.bank",
    ) = ScreenObservation(
        packageName, activity, nodes, null, 1080 to 2400, UiMode.LIGHT,
    )

    private fun node(
        id: Int,
        className: String,
        text: String? = null,
        contentDesc: String? = null,
        resourceId: String? = null,
        clickable: Boolean = false,
    ) = PrunedNode(
        id = id,
        className = className,
        resourceId = resourceId,
        text = text,
        contentDesc = contentDesc,
        clickable = clickable,
        checked = false,
        bounds = listOf(0, 0, 0, 0),
        center = listOf(540, 400),
        relBounds = listOf(0.0, 0.0, 0.0, 0.0),
        relCenter = listOf(0.5, 0.5),
    )

    private val u = HeuristicUnderstander()

    @Test
    fun `login screen is AUTH with typed email and password`() {
        val o = obs("com.example.bank.ui.LoginActivity", listOf(
            node(1, "android.widget.TextView", text = "Welcome back"),
            node(2, "android.widget.EditText", contentDesc = "Registered email"),
            node(3, "android.widget.EditText", contentDesc = "Enter password"),
            node(4, "android.widget.Button", text = "Sign in", clickable = true),
        ))
        val p = u.describe(o, "scr_x", ctx)

        assertEquals(ScreenKind.AUTH, p.kind)
        assertEquals("Login", p.name)
        assertEquals(InputType.EMAIL, p.inputSpecs.getValue(2).type)
        assertEquals(InputType.PASSWORD, p.inputSpecs.getValue(3).type)
        assertTrue(p.elementSemantics.getValue(4).startsWith("Taps"))
    }

    @Test
    fun `unlabelled role-less fields are still inputs typed from class plus hint`() {
        val o = obs("com.example.bank.ui.PayActivity", listOf(
            node(1, "android.widget.EditText", contentDesc = "Amount"),
            node(2, "android.widget.EditText", contentDesc = "Your 6-digit code"),
            node(3, "android.widget.EditText", contentDesc = "Date of birth"),
            node(4, "android.widget.Button", clickable = true),
        ))
        val p = u.describe(o, "scr_x", ctx)

        assertEquals(InputType.AMOUNT, p.inputSpecs.getValue(1).type)
        assertEquals(InputType.OTP, p.inputSpecs.getValue(2).type)
        assertEquals(InputType.DATE, p.inputSpecs.getValue(3).type)
        assertEquals(ScreenKind.FORM, p.kind)
    }

    @Test
    fun `a bare EditText with no text no labels no ids degrades to TEXT and never crashes`() {
        val o = obs(null, listOf(
            node(1, "android.widget.EditText"),
            node(2, "android.widget.Button", clickable = true),
            node(3, "android.widget.TextView"),
        ))
        val p = u.describe(o, "scr_x", ctx)

        assertEquals(InputType.TEXT, p.inputSpecs.getValue(1).type)
        assertEquals(ScreenKind.FORM, p.kind)
        assertTrue(p.elementSemantics.size == 3)
    }

    @Test
    fun `recyclerview screen is LIST`() {
        val o = obs("com.example.bank.ui.HistoryActivity", listOf(
            node(1, "androidx.recyclerview.widget.RecyclerView"),
            node(2, "android.widget.TextView", text = "No transactions yet"),
        ))
        assertEquals(ScreenKind.LIST, u.describe(o, "scr_x", ctx).kind)
    }

    @Test
    fun `settings activity is SETTINGS`() {
        val o = obs("com.example.bank.ui.SettingsActivity", listOf(
            node(1, "android.widget.TextView", text = "Preferences"),
        ))
        assertEquals(ScreenKind.SETTINGS, u.describe(o, "scr_x", ctx).kind)
    }

    @Test
    fun `onboarding is ONBOARDING`() {
        val o = obs("com.example.bank.ui.OnboardingActivity", listOf(
            node(1, "android.widget.TextView", text = "Welcome to Example Bank"),
        ))
        assertEquals(ScreenKind.ONBOARDING, u.describe(o, "scr_x", ctx).kind)
    }

    @Test
    fun `activity name becomes a human screen name`() {
        val o = obs("com.example.bank.ui.AccountOverviewActivity", listOf(
            node(1, "android.widget.TextView", text = "Balance"),
        ))
        assertEquals("Account Overview", u.describe(o, "scr_x", ctx).name)
    }

    @Test
    fun `deterministic same output on same input`() {
        val o = obs("com.example.bank.ui.LoginActivity", listOf(
            node(2, "android.widget.EditText", contentDesc = "Registered email"),
            node(3, "android.widget.EditText", contentDesc = "Enter password"),
        ))
        val a = u.describe(o, "scr_x", ctx)
        val b = u.describe(o, "scr_x", ctx)
        assertEquals(a, b)
        assertNotNull(a.inputSpecs.getValue(2).hint)
    }
}