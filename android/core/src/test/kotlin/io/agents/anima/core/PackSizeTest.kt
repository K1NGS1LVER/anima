package io.agents.anima.core

import org.junit.Test
import kotlin.test.assertTrue

class PackSizeTest {
    @Test
    fun testPackSizeBudget() {
        val screens = mutableListOf<Screen>()
        for (i in 1..40) {
            val elements = mutableListOf<Element>()
            for (j in 1..20) {
                elements.add(Element(
                    id = "el_${i}_${j}",
                    role = ElementRole.BUTTON,
                    label = "Test Label",
                    semantic = "Test Semantic",
                    boundsRel = listOf(0.1, 0.2, 0.3, 0.4),
                    actions = listOf(ElementAction.TAP),
                    leadsTo = null,
                    input = null
                ))
            }
            screens.add(Screen(
                id = "scr_$i",
                name = "Screen $i",
                purpose = "Test screen",
                kind = ScreenKind.LIST,
                signature = ScreenSignature("hash_$i", listOf("anchor1", "anchor2"), "com.test.Activity"),
                elements = elements,
                screenshot = "screens/scr_$i.webp",
                modesSeen = listOf(UiMode.LIGHT)
            ))
        }
        
        val pack = KnowledgePack(
            packVersion = "1.0",
            app = AppIdentity("com.test", "Test", "1.0", 1),
            scan = ScanMetadata("scan1", "now", 1000L, DeviceInfo("mod", 33, listOf(1080, 2400), "en-US"), Coverage(40, 800, 0, "exhausted"), UnderstanderInfo("b", "m", 0)),
            screens = screens,
            journeys = emptyList(),
            designSystem = null,
            graph = ScreenGraph(emptyList())
        )
        
        val compacted = PackCompactor.compact(pack)
        val jsonString = CanonicalJson.toJson(compacted)
        val sizeBytes = jsonString.toByteArray(Charsets.UTF_8).size
        
        val maxSizeBytes = 512 * 1024
        assertTrue(sizeBytes <= maxSizeBytes, "Pack size $sizeBytes bytes exceeds budget of $maxSizeBytes bytes")
    }
}
