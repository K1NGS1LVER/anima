package io.agents.anima.engine

import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Swallows recoverable XML warnings/errors; a fatal error still aborts the parse. */
private object SilentErrorHandler : ErrorHandler {
    override fun warning(exception: SAXParseException) = Unit
    override fun error(exception: SAXParseException) = Unit
    override fun fatalError(exception: SAXParseException) {
        throw exception
    }
}

/**
 * UIFormer structural pruning DSL, ported from `anima.py`.
 *
 * Drops non-interactive structural containers and emits a compact Agent-DOM. Uses only
 * `javax.xml` / `org.w3c.dom`, which exist both on the JVM and on Android, so this file stays
 * free of `android.*` imports and unit-tests on a plain JVM.
 */
object UIFormer {

    val CONTAINERS: Set<String> = setOf(
        "android.widget.FrameLayout",
        "android.widget.LinearLayout",
        "android.view.ViewGroup",
        "android.widget.RelativeLayout",
        "androidx.recyclerview.widget.RecyclerView",
    )

    private val BOUNDS_REGEX = Regex("""\[(\d+),(\d+)]\[(\d+),(\d+)]""")

    private const val DEFAULT_WIDTH = 1080
    private const val DEFAULT_HEIGHT = 2400

    /** Parses an Android `[l,t][r,b]` bounds string into (bounds, center). */
    fun parseBounds(boundsStr: String?): Pair<List<Int>, List<Int>> {
        val m = if (boundsStr.isNullOrEmpty()) null else BOUNDS_REGEX.find(boundsStr)
        if (m != null) {
            val l = m.groupValues[1].toIntOrNull() ?: 0
            val t = m.groupValues[2].toIntOrNull() ?: 0
            val r = m.groupValues[3].toIntOrNull() ?: 0
            val b = m.groupValues[4].toIntOrNull() ?: 0
            return Pair(listOf(l, t, r, b), listOf((l + r) / 2, (t + b) / 2))
        }
        return Pair(listOf(0, 0, 0, 0), listOf(0, 0))
    }

    /** Rounds to 4 decimal places, matching the Python runtime's normalized coordinates. */
    fun round4(value: Double): Double = Math.round(value * 10000.0) / 10000.0

    /** Normalizes absolute pixel geometry into resolution-invariant [0,1] coordinates. */
    fun normalize(bounds: List<Int>, center: List<Int>, screenW: Int, screenH: Int): Pair<List<Double>, List<Double>> {
        val sw = if (screenW > 0) screenW.toDouble() else DEFAULT_WIDTH.toDouble()
        val sh = if (screenH > 0) screenH.toDouble() else DEFAULT_HEIGHT.toDouble()
        val relBounds = listOf(
            round4(bounds[0] / sw),
            round4(bounds[1] / sh),
            round4(bounds[2] / sw),
            round4(bounds[3] / sh),
        )
        val relCenter = listOf(round4(center[0] / sw), round4(center[1] / sh))
        return Pair(relBounds, relCenter)
    }

    /**
     * Serializes pruned nodes into the compact Agent-DOM JSON handed to the planner.
     * Key order and shape mirror `UIFormer.to_compact_json` in `anima.py`.
     */
    fun toCompactJson(nodes: List<PrunedNode>): String {
        val sb = StringBuilder()
        sb.append('[')
        nodes.forEachIndexed { index, n ->
            if (index > 0) sb.append(',')
            sb.append('{')
            sb.append("\"id\":").append(n.id).append(',')
            sb.append("\"cls\":").append(jsonQuote(n.className))
            val resId = n.resourceId
            if (!resId.isNullOrEmpty()) {
                val short = if (resId.contains("/")) resId.substringAfterLast("/") else resId
                sb.append(",\"res_id\":").append(jsonQuote(short))
            }
            val text = n.text
            if (!text.isNullOrEmpty()) sb.append(",\"text\":").append(jsonQuote(text))
            val desc = n.contentDesc
            if (!desc.isNullOrEmpty()) sb.append(",\"desc\":").append(jsonQuote(desc))
            if (n.clickable) sb.append(",\"click\":true")
            if (n.checked) sb.append(",\"checked\":true")
            sb.append(",\"center\":[").append(n.center.getOrElse(0) { 0 })
                .append(',').append(n.center.getOrElse(1) { 0 }).append(']')
            sb.append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    /**
     * Prunes a raw uiautomator-style XML dump into semantic nodes.
     * Returns an empty list for blank or malformed input (never throws).
     */
    fun prune(rawXml: String?, screenSize: Pair<Int, Int>? = null): List<PrunedNode> {
        if (rawXml == null || rawXml.isBlank()) return emptyList()

        val root: Element = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            // Keep malformed dumps quiet: the default handler prints to stderr / logcat.
            builder.setErrorHandler(SilentErrorHandler)
            val doc = builder.parse(InputSource(StringReader(rawXml)))
            doc.documentElement ?: return emptyList()
        } catch (e: Exception) {
            return emptyList()
        }

        var sw = screenSize?.first ?: DEFAULT_WIDTH
        var sh = screenSize?.second ?: DEFAULT_HEIGHT
        if (screenSize == null) {
            val rootBounds = root.getAttribute("bounds")
            if (rootBounds != null && rootBounds.isNotEmpty()) {
                val (rb, _) = parseBounds(rootBounds)
                if (rb[2] > 0 && rb[3] > 0) {
                    sw = rb[2]
                    sh = rb[3]
                }
            }
        }
        if (sw <= 0) sw = DEFAULT_WIDTH
        if (sh <= 0) sh = DEFAULT_HEIGHT

        val nodes = ArrayList<PrunedNode>()
        var counter = 1

        fun walk(el: Element) {
            val cls = el.getAttribute("class") ?: ""
            val clickable = el.getAttribute("clickable") == "true"
            val checked = el.getAttribute("checked") == "true"
            val text = el.getAttribute("text")?.trim()?.takeIf { it.isNotEmpty() }
            val desc = el.getAttribute("content-desc")?.trim()?.takeIf { it.isNotEmpty() }
            val resId = el.getAttribute("resource-id")?.trim()?.takeIf { it.isNotEmpty() }

            val hasSemantics = text != null || desc != null || clickable ||
                el.getAttribute("checkable") == "true"
            val isContainer = CONTAINERS.contains(cls)

            if (hasSemantics && (!isContainer || clickable)) {
                val (bounds, center) = parseBounds(el.getAttribute("bounds"))
                if (bounds[2] > bounds[0] && bounds[3] > bounds[1]) {
                    val (relBounds, relCenter) = normalize(bounds, center, sw, sh)
                    nodes.add(
                        PrunedNode(
                            id = counter,
                            className = cls.substringAfterLast('.'),
                            resourceId = resId,
                            text = text,
                            contentDesc = desc,
                            clickable = clickable,
                            checked = checked,
                            bounds = bounds,
                            center = center,
                            relBounds = relBounds,
                            relCenter = relCenter,
                        )
                    )
                    counter += 1
                }
            }

            val children = el.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element) walk(child)
            }
        }

        walk(root)
        return nodes
    }
}
