package io.agents.anima.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regressions from a physical Redmi Note 11 (MIUI, Android 13).
 *
 * Running "toggle wifi" against the real Wi-Fi settings screen returned
 * "Could not plan an action": MIUI writes the label "Wi-Fi", and `"wifi" in
 * "wi-fi"` is false. The mock fixtures hid this because their resource-ids
 * happen to contain the bare word (`switch_wifi`).
 */
class HeuristicPlannerTest {

    private val planner = HeuristicPlanner()

    private fun node(
        id: Int,
        text: String? = null,
        desc: String? = null,
        resourceId: String? = null,
        clickable: Boolean = false,
        bounds: List<Int>,
    ) = PrunedNode(
        id = id,
        className = if (clickable) "LinearLayout" else "TextView",
        resourceId = resourceId,
        text = text,
        contentDesc = desc,
        clickable = clickable,
        checked = false,
        bounds = bounds,
        center = listOf((bounds[0] + bounds[2]) / 2, (bounds[1] + bounds[3]) / 2),
    )

    @Test
    fun punctuatedLabelsStillMatchTheGoalWord() {
        val nodes = listOf(
            node(1, resourceId = "android:id/row", clickable = true, bounds = listOf(0, 200, 1080, 400)),
            node(2, text = "Wi-Fi", resourceId = "android:id/title", bounds = listOf(72, 260, 600, 340)),
        )

        val planned = planner.planStep("toggle wifi", "", nodes)
        assertNotNull("'Wi-Fi' must match the goal word 'wifi'", planned)
        assertEquals("tap", planned!!.action)
    }

    @Test
    fun aMatchedLabelResolvesToTheClickableRowAroundIt() {
        val screen = node(1, clickable = true, bounds = listOf(0, 0, 1080, 2400))
        val row = node(2, resourceId = "android:id/row", clickable = true, bounds = listOf(0, 200, 1080, 400))
        val label = node(3, text = "Bluetooth", bounds = listOf(72, 260, 600, 340))

        val planned = planner.planStep("open bluetooth", "", listOf(screen, row, label))!!

        // Both the full-screen container and the row contain the label; the
        // tighter one wins, because that is the thing a human would tap.
        assertEquals("android:id/row", planned.target.resourceId)
    }

    @Test
    fun anAlreadyClickableMatchIsLeftAlone() {
        val toggle = node(
            1, desc = "Wi-Fi", resourceId = "com.android.settings:id/switch_wifi",
            clickable = true, bounds = listOf(880, 280, 1020, 400),
        )
        val planned = planner.planStep("toggle wifi", "", listOf(toggle))!!
        assertEquals(toggle, planned.target)
    }

    @Test
    fun anUnrelatedScreenYieldsNoAction() {
        val nodes = listOf(node(1, text = "Storage", clickable = true, bounds = listOf(0, 0, 500, 100)))
        assertNull(planner.planStep("order a coffee", "", nodes))
    }

    @Test
    fun aToggleGoalPrefersTheRowThatOwnsTheSwitch() {
        // Real MIUI Wi-Fi screen shape: the word "Wi-Fi" appears twice -- once in
        // the action bar, once as the label of the row holding the master switch
        // (which MIUI renders as a CheckBox). Only the second one does anything.
        val actionBar = node(1, clickable = true, resourceId = "android:id/action_bar_container", bounds = listOf(0, 0, 1080, 401))
        val actionBarTitle = node(2, text = "Wi-Fi", resourceId = "android:id/title", bounds = listOf(76, 200, 183, 300))
        val toggleRow = node(3, clickable = true, resourceId = "android:id/row", bounds = listOf(0, 421, 1080, 575))
        val rowLabel = node(4, text = "Wi-Fi", resourceId = "android:id/title", bounds = listOf(76, 466, 183, 529))
        val checkBox = PrunedNode(
            id = 5, className = "CheckBox", resourceId = "android:id/checkbox",
            text = null, contentDesc = null, clickable = false, checked = true,
            bounds = listOf(870, 454, 1004, 541), center = listOf(937, 497),
        )
        // A network row whose content-desc merely mentions Wi-Fi, as a distractor.
        val networkRow = node(
            6, desc = "MyNetwork,Connected,Wi-Fi signal full.,Secure network",
            clickable = true, bounds = listOf(0, 600, 1080, 800),
        )

        val nodes = listOf(actionBar, actionBarTitle, toggleRow, rowLabel, checkBox, networkRow)
        val planned = planner.planStep("toggle wifi", "", nodes)!!

        assertEquals("android:id/row", planned.target.resourceId)
    }

    @Test
    fun aNonToggleGoalIgnoresTheSwitchBonus() {
        assertEquals(false, HeuristicPlanner.wantsToggle("open bluetooth settings"))
        assertEquals(true, HeuristicPlanner.wantsToggle("toggle wifi"))
        assertEquals(true, HeuristicPlanner.wantsToggle("turn off bluetooth"))
        assertEquals(true, HeuristicPlanner.wantsToggle("disable airplane mode"))
    }

    @Test
    fun flattenStripsTheTypographyThatBrokeTheRealDevice() {
        assertEquals("wifi", HeuristicPlanner.flatten("Wi-Fi"))
        assertEquals("donotdisturb", HeuristicPlanner.flatten("Do not disturb"))
        assertEquals("bluetoothdevices", HeuristicPlanner.flatten("Bluetooth & devices"))
    }
}
