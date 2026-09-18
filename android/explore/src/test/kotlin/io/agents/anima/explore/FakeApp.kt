package io.agents.anima.explore

import io.agents.anima.core.ScreenIdentifier
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ScreenSignature
import io.agents.anima.core.ScrollDirection
import io.agents.anima.core.UiMode
import io.agents.anima.core.ExplorationDevice
import io.agents.anima.engine.PrunedNode
import java.security.MessageDigest

/**
 * A hermetic app to crawl.
 *
 * A crawler's policy cannot be tested on a real phone: the thing under test and
 * the thing measuring it are the same flaky process, and a failure tells you
 * nothing about whether the policy or the device was at fault. So the app is
 * fake, deterministic and instant, and the real device is exercised separately
 * by hand on hardware.
 */
class FakeApp(
    val packageName: String = "com.example.fake",
    private val screens: Map<String, FakeScreen>,
    private val start: String,
) {
    /** Every tap the crawler performed, in order, as `screen:label`. */
    val taps = mutableListOf<String>()
    val visitOrder = mutableListOf<String>()

    var currentScreen: String = start
        private set
    var currentPackage: String = packageName
        private set

    fun tapAt(x: Int, y: Int): Boolean {
        val screen = screens.getValue(currentScreen)
        val hit = screen.elements.firstOrNull { it.contains(x, y) } ?: return false
        taps.add("$currentScreen:${hit.label}")
        when {
            hit.leavesApp -> currentPackage = "com.android.launcher"
            hit.leadsTo != null -> go(hit.leadsTo)
        }
        return true
    }

    fun scroll(): Boolean {
        val screen = screens.getValue(currentScreen)
        if (!screen.scrollable) return false
        if (screen.scrolledInto != null) go(screen.scrolledInto)
        return true
    }

    fun back(): Boolean {
        if (currentPackage != packageName) {
            currentPackage = packageName
            return true
        }
        val parent = screens.getValue(currentScreen).parent ?: return false
        go(parent)
        return true
    }

    fun home() {
        currentPackage = "com.android.launcher"
    }

    fun launch(): Boolean {
        currentPackage = packageName
        go(start)
        return true
    }

    fun observe(): ScreenObservation? {
        if (currentPackage != packageName) {
            return ScreenObservation(
                packageName = currentPackage,
                activity = "Launcher",
                nodes = listOf(node(1, "Home screen", 0, 0, 100, 100)),
                screenshot = null,
                screenSize = SCREEN,
                uiMode = UiMode.LIGHT,
            )
        }
        val screen = screens.getValue(currentScreen)
        return ScreenObservation(
            packageName = packageName,
            activity = "com.example.fake.${screen.name}Activity",
            nodes = screen.toNodes(),
            screenshot = null,
            screenSize = SCREEN,
            uiMode = UiMode.LIGHT,
        )
    }

    private fun go(name: String) {
        currentScreen = name
        visitOrder.add(name)
    }

    companion object {
        val SCREEN = 1080 to 2400
    }
}

data class FakeElement(
    val label: String,
    val top: Int,
    val leadsTo: String? = null,
    val clickable: Boolean = true,
    val editable: Boolean = false,
    val leavesApp: Boolean = false,
    val className: String = "android.widget.Button",
) {
    val bounds: List<Int> get() = listOf(40, top, 1040, top + 120)
    fun contains(x: Int, y: Int): Boolean =
        x in bounds[0]..bounds[2] && y in bounds[1]..bounds[3]
}

data class FakeScreen(
    val name: String,
    val elements: List<FakeElement>,
    val parent: String? = null,
    val scrollable: Boolean = false,
    val scrolledInto: String? = null,
    /** Text that makes BiometricGuard treat this as an auth boundary. */
    val marker: String? = null,
) {
    fun toNodes(): List<PrunedNode> {
        val out = ArrayList<PrunedNode>()
        var id = 1
        if (scrollable) {
            out.add(node(id++, null, 0, 0, 1080, 2400, scrollable = true, cls = "androidx.recyclerview.widget.RecyclerView"))
        }
        marker?.let { out.add(node(id++, it, 0, 200, 1080, 320, clickable = false, cls = "android.widget.TextView")) }
        for (element in elements) {
            out.add(
                node(
                    id++, element.label,
                    element.bounds[0], element.bounds[1], element.bounds[2], element.bounds[3],
                    clickable = element.clickable, editable = element.editable, cls = element.className,
                )
            )
        }
        return out
    }
}

internal fun node(
    id: Int,
    label: String?,
    l: Int, t: Int, r: Int, b: Int,
    clickable: Boolean = true,
    editable: Boolean = false,
    scrollable: Boolean = false,
    cls: String = "android.widget.Button",
): PrunedNode {
    val bounds = listOf(l, t, r, b)
    val center = listOf((l + r) / 2, (t + b) / 2)
    return PrunedNode(
        id = id,
        className = cls.substringAfterLast('.'),
        resourceId = label?.let { "com.example.fake:id/${it.lowercase().replace(" ", "_")}" },
        text = label,
        contentDesc = null,
        clickable = clickable,
        checked = false,
        scrollable = scrollable,
        editable = editable,
        bounds = bounds,
        center = center,
        relBounds = listOf(l / 1080.0, t / 2400.0, r / 1080.0, b / 2400.0),
        relCenter = listOf(center[0] / 1080.0, center[1] / 2400.0),
    )
}

/** Drives a [FakeApp] through the same interface the real phone implements. */
class FakeDevice(private val app: FakeApp) : ExplorationDevice {
    var scrolls = 0
        private set

    override fun observe(): ScreenObservation? = app.observe()
    override fun tap(x: Int, y: Int): Boolean = app.tapAt(x, y)
    override fun inputText(x: Int, y: Int, text: String): Boolean = app.tapAt(x, y)
    override fun scroll(direction: ScrollDirection, boundsPx: List<Int>?): Boolean {
        scrolls += 1
        return app.scroll()
    }
    override fun back(): Boolean = app.back()
    override fun home(): Boolean { app.home(); return true }
    override fun launch(packageName: String): Boolean = app.launch()
    override fun currentPackage(): String? = app.currentPackage
    override fun setUiMode(mode: UiMode): Boolean = false
    override fun settle(ms: Long) = Unit
}

/**
 * A stand-in for Jacob's [ScreenIdentifier], hashing the same structural inputs
 * the spec names: resource-ids and the class skeleton, never text or bounds.
 * Tests here are about the crawler's ordering, not about his hashing, but they
 * would be worthless against an identifier that was not itself stable.
 */
class StructuralIdentifier : ScreenIdentifier {
    override fun screenId(observation: ScreenObservation): String =
        "scr_" + sha256(fingerprint(observation)).take(12)

    override fun elementId(screenId: String, node: PrunedNode, structuralPath: String): String =
        "el_" + sha256("$screenId|${node.resourceId ?: node.className}|$structuralPath").take(8)

    override fun signature(observation: ScreenObservation) = ScreenSignature(
        structuralHash = sha256(fingerprint(observation)),
        anchors = observation.nodes.mapNotNull { it.resourceId }.sorted(),
        activity = observation.activity,
    )

    private fun fingerprint(observation: ScreenObservation): String {
        val ids = observation.nodes.mapNotNull { it.resourceId }.sorted().distinct()
        val classes = observation.nodes.map { it.className }.sorted().distinct()
        return (listOf(observation.activity ?: "") + ids + classes).joinToString("|")
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
