package io.agents.anima.core

import org.junit.Test
import kotlin.test.assertTrue

class PackSizeTest {
    @Test
    fun testPackSizeBudget() {
        val screens = mutableListOf<ScreenInfo>()
        for (i in 1..40) {
            val elements = mutableListOf<ElementInfo>()
            for (j in 1..20) {
                elements.add(ElementInfo(
                    id = "el_${i}_${j}",
                    role = "button",
                    label = "Test Label",
                    semantic = "Test Semantic",
                    bounds_rel = listOf(0.1, 0.2, 0.3, 0.4),
                    actions = listOf("tap"),
                    leads_to = null,
                    input = null
                ))
            }
            screens.add(ScreenInfo(
                id = "scr_$i",
                name = "Screen $i",
                purpose = "Test screen",
                kind = "list",
                signature = ScreenSignature("hash_$i", listOf("anchor1", "anchor2"), "com.test.Activity"),
                elements = elements,
                screenshot = "screens/scr_$i.webp",
                modes_seen = listOf("light")
            ))
        }
        
        val pack = KnowledgePack(
            pack_version = "1.0",
            app = AppInfo("com.test", "Test", "1.0", 1),
            scan = ScanInfo("scan1", "now", 1000L, DeviceInfo("mod", 33, listOf(1080, 2400), "en-US"), CoverageInfo(40, 800, 0, "exhausted"), UnderstanderInfo("b", "m", 0)),
            screens = screens,
            journeys = emptyList(),
            design_system = null,
            graph = null
        )
        
        val compacted = PackCompactor.compact(pack)
        val jsonString = CanonicalJson.toJson(compacted)
        val sizeBytes = jsonString.toByteArray(Charsets.UTF_8).size
        
        // Budget: pack.json for a 40-screen app <= 512 KB
        val maxSizeBytes = 512 * 1024
        assertTrue(sizeBytes <= maxSizeBytes, "Pack size $sizeBytes bytes exceeds budget of $maxSizeBytes bytes")
    }
}
