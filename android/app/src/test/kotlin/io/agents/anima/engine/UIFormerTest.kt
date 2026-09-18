package io.agents.anima.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UIFormerTest {

    @Test
    fun containersWithoutSemanticsArePruned() {
        val nodes = UIFormer.prune(DemoDevice.WIFI_XML, Pair(1080, 2400))

        // The FrameLayout and LinearLayout wrappers are dropped; only the label and the switch stay.
        assertEquals(2, nodes.size)
        assertEquals("TextView", nodes[0].className)
        assertEquals("Network & internet", nodes[0].text)
        assertEquals("Switch", nodes[1].className)
        assertEquals("Wi-Fi", nodes[1].contentDesc)
        assertTrue(nodes[1].clickable)
        assertFalse(nodes[1].checked)
    }

    @Test
    fun geometryIsNormalizedIntoUnitCoordinates() {
        val switch = UIFormer.prune(DemoDevice.WIFI_XML, Pair(1080, 2400))[1]

        assertEquals(listOf(850, 220, 1000, 340), switch.bounds)
        assertEquals(listOf(925, 280), switch.center)
        assertEquals(0.8565, switch.relCenter[0], 1e-9)
        assertEquals(0.1167, switch.relCenter[1], 1e-9)
    }

    @Test
    fun idsAreOneBasedAndSequential() {
        val nodes = UIFormer.prune(DemoDevice.WIFI_XML, Pair(1080, 2400))
        assertEquals(1, nodes[0].id)
        assertEquals(2, nodes[1].id)
    }

    @Test
    fun blankOrMalformedXmlYieldsNoNodes() {
        assertTrue(UIFormer.prune(null).isEmpty())
        assertTrue(UIFormer.prune("").isEmpty())
        assertTrue(UIFormer.prune("   ").isEmpty())
        assertTrue(UIFormer.prune("<hierarchy><node ").isEmpty())
    }

    @Test
    fun boundsParsing() {
        val (bounds, center) = UIFormer.parseBounds("[10,20][30,60]")
        assertEquals(listOf(10, 20, 30, 60), bounds)
        assertEquals(listOf(20, 40), center)

        val (empty, origin) = UIFormer.parseBounds("garbage")
        assertEquals(listOf(0, 0, 0, 0), empty)
        assertEquals(listOf(0, 0), origin)
    }

    @Test
    fun compactDomDropsEmptyFieldsAndShortensResourceIds() {
        val json = UIFormer.toCompactJson(UIFormer.prune(DemoDevice.WIFI_XML, Pair(1080, 2400)))

        assertTrue(json.contains("\"res_id\":\"switch_wifi\""))
        assertTrue(json.contains("\"desc\":\"Wi-Fi\""))
        assertTrue(json.contains("\"click\":true"))
        assertTrue(json.contains("\"center\":[925,280]"))
        // The label has no resource id, so the key is omitted entirely.
        assertTrue(json.startsWith("[{\"id\":1,\"cls\":\"TextView\",\"text\":\"Network & internet\""))
    }
}

class GuardsTest {

    @Test
    fun biometricMarkersAreCaseInsensitive() {
        // The Python original compared mixed-case markers against a lower-cased dump, which made
        // some of them unreachable. This port lower-cases both sides.
        assertTrue(BiometricGuard.detect("com.android.systemui:id/biometric_prompt"))
        assertTrue(BiometricGuard.detect("COM.ANDROID.SYSTEMUI:ID/BIOMETRIC_PROMPT"))
        assertTrue(BiometricGuard.detect("android:id/passwordEntry"))
        assertTrue(BiometricGuard.detect("Confirm your pattern"))
        assertTrue(BiometricGuard.detect("Use Biometrics to continue"))
    }

    @Test
    fun ordinaryScreensDoNotTripTheGuard() {
        assertFalse(BiometricGuard.detect(DemoDevice.WIFI_XML))
        assertFalse(BiometricGuard.detect(DemoDevice.POPUP_XML))
        assertFalse(BiometricGuard.detect(null))
        assertFalse(BiometricGuard.detect(""))
    }

    @Test
    fun popupInterceptorDismissesPermissionDialogs() {
        val device = DemoDevice()
        device.hasPopup = true

        val handled = PopupInterceptor.checkAndHandle(device.captureNodes(), device, "toggle wifi")

        assertTrue(handled)
        assertFalse(device.hasPopup)
    }

    @Test
    fun popupInterceptorStandsDownWhenTheGoalIsAboutTheDialog() {
        val device = DemoDevice()
        device.hasPopup = true

        val handled = PopupInterceptor.checkAndHandle(device.captureNodes(), device, "allow location permission")

        assertFalse(handled)
        assertTrue(device.hasPopup)
    }

    @Test
    fun essentialStateDetectsAToggleFlip() {
        val before = PrunedNode(id = 1, className = "Switch", resourceId = "sw", checked = false)
        val after = before.copy(checked = true)

        assertTrue(EssentialStateVerifier.verifyProgress(listOf(before), listOf(after), before))
        assertFalse(EssentialStateVerifier.verifyProgress(listOf(before), listOf(before), before))
    }

    @Test
    fun essentialStateDetectsNewTextAndHierarchyChanges() {
        val a = PrunedNode(id = 1, className = "TextView", resourceId = "t", text = "before")
        val b = a.copy(text = "after")
        assertTrue(EssentialStateVerifier.verifyProgress(listOf(a), listOf(b), a))

        val c = PrunedNode(id = 2, className = "Button", resourceId = "t")
        assertTrue(EssentialStateVerifier.verifyProgress(listOf(a), listOf(a, c), a))
    }
}
