package io.agents.anima.store

import androidx.test.core.app.ApplicationProvider
import io.agents.anima.core.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class KnowledgeStoreTest {

    @Test
    fun testPersistenceRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = KnowledgeStore(context, "test_knowledge_pack.db")

        val app = AppIdentity("com.test", "Test App", "1.0", 1)
        val scan = ScanMetadata("scan1", "2024-01-01T00:00:00Z", 1000L, DeviceInfo("Phone", 33, listOf(1080, 2400), "en-US"), Coverage(5, 50, 0, "DONE"), UnderstanderInfo("b", "m", 0))
        val el1 = Element("el_1", ElementRole.BUTTON, "Submit", "Submit btn", listOf(0.1, 0.2, 0.3, 0.4), listOf(ElementAction.TAP), null, null)
        val scr1 = Screen("scr_1", "Home", "Home screen", ScreenKind.OTHER, ScreenSignature("hash1", listOf("a"), "Activity"), listOf(el1), "path1", listOf(UiMode.LIGHT))
        val pack = KnowledgePack(KnowledgePack.PACK_VERSION, app, scan, listOf(scr1), emptyList(), null, ScreenGraph(emptyList()))

        store.save(pack)
        
        val loaded = store.load(app.packageName, scan.id)!!
        
        val canonicalOriginal = store.toCanonicalJson(pack)
        val canonicalLoaded = store.toCanonicalJson(loaded)
        
        assertEquals(canonicalOriginal, canonicalLoaded, "Loaded pack must canonicalize identically to saved pack")
    }
}
