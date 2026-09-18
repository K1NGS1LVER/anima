package io.agents.anima.explore

import io.agents.anima.core.AppIdentity
import io.agents.anima.core.CanonicalJson
import io.agents.anima.core.DeviceInfo
import io.agents.anima.core.ElementRole
import io.agents.anima.core.ExplorationDevice
import io.agents.anima.core.KnowledgePack
import io.agents.anima.core.PackDiff
import io.agents.anima.core.PackRepository
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ScrollDirection
import io.agents.anima.core.UiMode
import io.agents.anima.core.UnderstanderInfo
import io.agents.anima.engine.PrunedNode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScanOrchestrator] is the piece that turns a raw [ScanOutcome] into a real
 * [KnowledgePack]. The headline requirement it inherits from [Explorer] is
 * stability: two scans of the same app must produce packs that are not just
 * "similar" but `equals()`-identical in every part that KNOWLEDGE_PACK.md
 * promises is stable.
 */
class ScanOrchestratorTest {

    private val appIdentity = AppIdentity(
        packageName = "com.example.fake",
        label = "Fake App",
        versionName = "1.0",
        versionCode = 1L,
    )

    private val deviceInfo = DeviceInfo(
        model = "Pixel Test",
        sdk = 33,
        resolution = listOf(1080, 2400),
        locale = "en-US",
    )

    /** Fixed so scan.id / scan.started_at / duration_ms are identical across two runs, not just close. */
    private val fixedClock: () -> Long = { 1_700_000_000_000L }

    private object NoOpPackRepository : PackRepository {
        override fun save(pack: KnowledgePack) {}
        override fun load(packageName: String, scanId: String): KnowledgePack? = null
        override fun latest(packageName: String): KnowledgePack? = null
        override fun toCanonicalJson(pack: KnowledgePack): String = CanonicalJson.toJson(pack)
        override fun diff(old: KnowledgePack, new: KnowledgePack): PackDiff =
            PackDiff(emptyList(), emptyList(), emptyList(), emptyList())
    }

    private fun orchestratorWithDevice(device: ExplorationDevice): ScanOrchestrator {
        val explorer = Explorer(
            device = device,
            identifier = StructuralIdentifier(),
            budget = ScanBudget(),
            listener = ScanListener.NONE,
            isAborted = { false },
            clock = fixedClock,
        )
        return ScanOrchestrator(
            explorer = explorer,
            identifier = StructuralIdentifier(),
            understander = FakeUnderstander(),
            repository = NoOpPackRepository,
            clock = fixedClock,
        )
    }

    private fun orchestrator(app: FakeApp): ScanOrchestrator = orchestratorWithDevice(FakeDevice(app))

    /** A small app with one navigating element and one dead end, for the shape tests below. */
    private fun demoApp() = FakeApp(
        screens = mapOf(
            "Home" to FakeScreen(
                "Home",
                listOf(
                    FakeElement("Go", top = 400, leadsTo = "Detail"),
                    FakeElement("Dead end", top = 600, leadsTo = null),
                ),
            ),
            "Detail" to FakeScreen("Detail", emptyList(), parent = "Home"),
        ),
        start = "Home",
    )

    // ---- 1. The headline test -------------------------------------------------

    @Test
    fun sameAppScannedTwiceProducesEqualPacks() {
        val result1 = orchestrator(demoApp()).scan(appIdentity, deviceInfo)
        val result2 = orchestrator(demoApp()).scan(appIdentity, deviceInfo)

        assertEquals(result1.pack.screens, result2.pack.screens)
        assertEquals(result1.pack.graph, result2.pack.graph)
        assertEquals(result1.pack.journeys, result2.pack.journeys)
        // Full pack equality holds too, since both scans share a fixed clock.
        assertEquals(result1.pack, result2.pack)
        assertEquals(CanonicalJson.toJson(result1.pack), CanonicalJson.toJson(result2.pack))
    }

    // ---- 2. Role heuristic, every branch ---------------------------------------

    private fun roleNode(
        id: Int,
        cls: String,
        top: Int,
        clickable: Boolean = false,
        editable: Boolean = false,
        scrollable: Boolean = false,
        checked: Boolean = false,
        text: String,
    ): PrunedNode {
        val l = 0
        val r = 1080
        val b = top + 100
        return PrunedNode(
            id = id,
            className = cls,
            resourceId = null,
            text = text,
            contentDesc = null,
            clickable = clickable,
            checked = checked,
            scrollable = scrollable,
            editable = editable,
            bounds = listOf(l, top, r, b),
            center = listOf((l + r) / 2, (top + b) / 2),
            relBounds = listOf(l / 1080.0, top / 2400.0, r / 1080.0, b / 2400.0),
            relCenter = listOf(0.5, (top + 50) / 2400.0),
        )
    }

    private fun roleTestObservation(): ScreenObservation = ScreenObservation(
        packageName = "com.example.roles",
        activity = "com.example.roles.RoleActivity",
        nodes = listOf(
            roleNode(1, "android.widget.EditText", 0, editable = true, text = "Email"),
            roleNode(2, "com.example.CustomToggleWidget", 100, checked = true, text = "Custom toggle"),
            roleNode(3, "android.widget.Switch", 200, clickable = true, text = "Switch"),
            roleNode(4, "android.widget.CheckBox", 300, clickable = true, text = "CheckBox"),
            roleNode(5, "android.widget.ToggleButton", 400, clickable = true, text = "ToggleButton"),
            roleNode(6, "androidx.recyclerview.widget.RecyclerView", 500, scrollable = true, text = "List"),
            roleNode(7, "android.widget.Button", 600, clickable = true, text = "Button"),
            roleNode(8, "android.widget.ImageView", 700, text = "Image"),
            roleNode(9, "com.google.android.material.tabs.TabLayout\$Tab", 800, clickable = true, text = "Tab"),
            roleNode(10, "com.google.android.material.bottomnavigation.BottomNavigationView", 900, text = "Nav"),
            roleNode(11, "android.widget.TextView", 1000, clickable = true, text = "Link"),
            roleNode(12, "android.widget.TextView", 1100, text = "PlainText"),
        ),
        screenshot = null,
        screenSize = 1080 to 2400,
        uiMode = UiMode.LIGHT,
    )

    /** A device with exactly one screen that never changes, for isolating the role heuristic. */
    private class SingleScreenDevice(private val observation: ScreenObservation) : ExplorationDevice {
        override fun observe(): ScreenObservation = observation
        override fun tap(x: Int, y: Int): Boolean = true
        override fun inputText(x: Int, y: Int, text: String): Boolean = true
        override fun scroll(direction: ScrollDirection, boundsPx: List<Int>?): Boolean = true
        override fun back(): Boolean = true
        override fun home(): Boolean = true
        override fun launch(packageName: String): Boolean = true
        override fun currentPackage(): String = observation.packageName
        override fun setUiMode(mode: UiMode): Boolean = false
        override fun settle(ms: Long) = Unit
    }

    @Test
    fun elementRolesMatchTheHeuristicForEveryBranch() {
        val observation = roleTestObservation()
        val device = SingleScreenDevice(observation)
        val result = orchestratorWithDevice(device).scan(
            app = AppIdentity("com.example.roles", "Roles", null, null),
            device = deviceInfo,
        )

        val elements = result.pack.screens.single().elements
        fun roleOf(label: String): ElementRole = elements.first { it.label == label }.role

        assertEquals(ElementRole.INPUT, roleOf("Email"))
        assertEquals("checked flag alone must trigger TOGGLE", ElementRole.TOGGLE, roleOf("Custom toggle"))
        assertEquals(ElementRole.TOGGLE, roleOf("Switch"))
        assertEquals(ElementRole.TOGGLE, roleOf("CheckBox"))
        assertEquals(ElementRole.TOGGLE, roleOf("ToggleButton"))
        assertEquals(ElementRole.LIST, roleOf("List"))
        assertEquals(ElementRole.BUTTON, roleOf("Button"))
        assertEquals(ElementRole.IMAGE, roleOf("Image"))
        assertEquals(ElementRole.TAB, roleOf("Tab"))
        assertEquals(ElementRole.NAV, roleOf("Nav"))
        assertEquals(ElementRole.LINK, roleOf("Link"))
        assertEquals(ElementRole.TEXT, roleOf("PlainText"))
    }

    // ---- 3. leadsTo -------------------------------------------------------------

    @Test
    fun leadsToIsPopulatedForANavigatingElementAndNullForADeadEnd() {
        val result = orchestrator(demoApp()).scan(appIdentity, deviceInfo)
        val allElements = result.pack.screens.flatMap { it.elements }

        val go = allElements.first { it.label == "Go" }
        val deadEnd = allElements.first { it.label == "Dead end" }

        assertTrue("a navigating element must resolve leadsTo", go.leadsTo != null)
        assertNull("an element that goes nowhere must have a null leadsTo", deadEnd.leadsTo)
    }

    // ---- 4. Graph edges reference real element ids -------------------------------

    @Test
    fun graphEdgesReferenceRealElementIds() {
        val result = orchestrator(demoApp()).scan(appIdentity, deviceInfo)
        val idsByScreen = result.pack.screens.associate { screen -> screen.id to screen.elements.map { it.id }.toSet() }

        assertTrue("expected at least one observed transition", result.pack.graph.edges.isNotEmpty())
        result.pack.graph.edges.forEach { edge ->
            assertTrue(
                "edge.via '${edge.via}' is not among the elements of screen '${edge.from}'",
                idsByScreen[edge.from]?.contains(edge.via) == true,
            )
        }
    }

    // ---- 5. Sorting ---------------------------------------------------------------

    @Test
    fun everyArrayIsSortedById() {
        val result = orchestrator(demoApp()).scan(appIdentity, deviceInfo)
        val pack = result.pack

        assertEquals(pack.screens.map { it.id }, pack.screens.map { it.id }.sorted())
        pack.screens.forEach { screen ->
            assertEquals(
                "elements of screen ${screen.id} must be sorted",
                screen.elements.map { it.id },
                screen.elements.map { it.id }.sorted(),
            )
        }
        assertEquals(pack.journeys.map { it.id }, pack.journeys.map { it.id }.sorted())

        val edgeKeys = pack.graph.edges.map { Triple(it.from, it.to, it.via) }
        assertEquals(
            edgeKeys,
            edgeKeys.sortedWith(compareBy({ it.first }, { it.second }, { it.third })),
        )
    }

    // ---- 6. Screenshots -------------------------------------------------------------

    /** Attaches [bytes] to any observation whose activity contains [activityMarker]; passes everything else through. */
    private class ScreenshotInjectingDevice(
        private val delegate: ExplorationDevice,
        private val activityMarker: String,
        private val bytes: ByteArray,
    ) : ExplorationDevice by delegate {
        override fun observe(): ScreenObservation? {
            val obs = delegate.observe() ?: return null
            return if (obs.activity?.contains(activityMarker) == true) obs.copy(screenshot = bytes) else obs
        }
    }

    @Test
    fun observationsWithAScreenshotProduceAPathAndAnEntryInTheScreenshotMap() {
        val bytes = byteArrayOf(1, 2, 3)
        val device = ScreenshotInjectingDevice(FakeDevice(demoApp()), "HomeActivity", bytes)
        val result = orchestratorWithDevice(device).scan(appIdentity, deviceInfo)

        val withShot = result.pack.screens.first { it.screenshot != null }
        val withoutShot = result.pack.screens.first { it.screenshot == null }

        assertEquals("screens/${withShot.id}.webp", withShot.screenshot)
        assertTrue(result.screenshots.containsKey(withShot.id))
        assertArrayEquals(bytes, result.screenshots[withShot.id])

        assertFalse(
            "a screen with no screenshot must not appear in the screenshots map",
            result.screenshots.containsKey(withoutShot.id),
        )
    }

    // ---- 7. understanderInfo passthrough --------------------------------------------

    @Test
    fun understanderInfoPassesThroughVerbatimOrStaysNull() {
        val info = UnderstanderInfo(backend = "heuristic", model = null, cacheHits = 0)

        val withInfo = orchestrator(demoApp()).scan(appIdentity, deviceInfo, understanderInfo = info)
        assertEquals(info, withInfo.pack.scan.understander)

        val withoutInfo = orchestrator(demoApp()).scan(appIdentity, deviceInfo)
        assertNull(withoutInfo.pack.scan.understander)
    }
}
