package io.agents.anima.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression from a physical device, and the most dangerous bug found so far.
 *
 * The compiled "toggle wifi" skill replayed on the *Bluetooth* settings screen
 * and turned Bluetooth off, reporting `WARM_REPLAY success=true llmCalls=0`.
 *
 * MIUI's toggle row has no resource-id, no text and no content-desc of its own,
 * so the stored locator held only a class name and a position — and active-
 * weight renormalization rescaled that to a perfect 1.0 against any
 * LinearLayout in the same place, on any screen in any app.
 *
 * A wrong action is far worse than no action: it is the difference between an
 * agent that fails visibly and one that quietly does something the user never
 * asked for.
 */
class WrongScreenGuardTest {

    private val wifiScreen = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" class="android.widget.FrameLayout" bounds="[0,0][1080,2400]">
    <node index="0" class="android.widget.LinearLayout" clickable="true" bounds="[0,421][1080,575]">
      <node index="0" class="android.widget.TextView" resource-id="android:id/title" text="Wi-Fi" bounds="[76,466][183,529]" />
      <node index="1" class="android.widget.CheckBox" resource-id="android:id/checkbox" checkable="true" checked="true" bounds="[870,454][1004,541]" />
    </node>
  </node>
</hierarchy>
"""

    /** Same OEM, same layout, same coordinates — a different setting. */
    private val bluetoothScreen = wifiScreen.replace("Wi-Fi", "Bluetooth")

    private fun toggleRow(xml: String): PrunedNode =
        UIFormer.prune(xml, 1080 to 2400).first { it.clickable }

    @Test
    fun aLabellessRowInheritsTheLabelInsideIt() {
        assertEquals("Wi-Fi", toggleRow(wifiScreen).text)
        assertEquals("Bluetooth", toggleRow(bluetoothScreen).text)
    }

    @Test
    fun aWifiSkillDoesNotFireOnTheBluetoothScreen() {
        val locator = Locator.fromNode(toggleRow(wifiScreen))

        assertEquals(1.0, Matcher.score(locator, toggleRow(wifiScreen)), 1e-9)
        assertEquals(0.0, Matcher.score(locator, toggleRow(bluetoothScreen)), 1e-9)
        assertNull(
            "replaying the Wi-Fi skill on the Bluetooth screen must drift, not tap",
            Matcher.findBest(locator, listOf(toggleRow(bluetoothScreen))),
        )
    }

    @Test
    fun aContradictedLabelVetoesTheWholeScore() {
        // Everything else about these two agrees: same id, same class, same spot.
        // Only the label differs, and that is enough to make them different things.
        val loc = Locator(
            resourceId = "android:id/row", contentDesc = null, text = "Wi-Fi",
            className = "LinearLayout", bounds = null,
            relBounds = null, relCenter = listOf(0.5, 0.2075),
        )
        val imposter = PrunedNode(
            id = 1, className = "LinearLayout", resourceId = "android:id/row",
            text = "Bluetooth", contentDesc = null, clickable = true, checked = false,
            bounds = listOf(0, 421, 1080, 575), center = listOf(540, 498),
            relBounds = listOf(0.0, 0.1754, 1.0, 0.2396), relCenter = listOf(0.5, 0.2075),
        )
        assertEquals(0.0, Matcher.score(loc, imposter), 1e-9)
    }

    @Test
    fun aSlightlyDifferentLabelIsStillTheSameElement() {
        // The veto must not fire on ordinary wording drift, or self-healing
        // would trigger on every minor copy change.
        val loc = Locator(
            resourceId = null, contentDesc = null, text = "Wi-Fi",
            className = "LinearLayout", bounds = null,
            relBounds = null, relCenter = listOf(0.5, 0.2075),
        )
        val renamed = PrunedNode(
            id = 1, className = "LinearLayout", resourceId = null,
            text = "WiFi", contentDesc = null, clickable = true, checked = false,
            bounds = listOf(0, 421, 1080, 575), center = listOf(540, 498),
            relBounds = listOf(0.0, 0.1754, 1.0, 0.2396), relCenter = listOf(0.5, 0.2075),
        )
        assert(Matcher.score(loc, renamed) >= Matcher.DEFAULT_THRESHOLD) {
            "'WiFi' vs 'Wi-Fi' should still match, got ${Matcher.score(loc, renamed)}"
        }
    }
}
