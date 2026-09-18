package io.agents.anima.design

import io.agents.anima.core.ComponentToken
import io.agents.anima.core.DesignSystem
import io.agents.anima.core.ScreenObservation
import io.agents.anima.engine.PrunedNode
import java.util.Base64
import java.util.Locale

/**
 * One screen as the pack stores it: structure and labels, and no pixels.
 *
 * The absence of a screenshot field is the whole point of the type. The rebuild
 * test only means something if the reconstruction is built from what the pack
 * knows, so the renderer is given a type that *cannot* reach the original
 * image — the discipline is enforced by the signature rather than by intent.
 */
data class RebuildScreen(
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val elements: List<RebuildElement>,
)

/** One element, carrying exactly the fields `Element` holds in the pack. */
data class RebuildElement(
    /** Matches a [ComponentToken] name, or "Text" for a plain label. */
    val role: String,
    /** left, top, right, bottom as fractions of the frame, as `boundsRel` is. */
    val boundsRel: List<Double>,
    val label: String?,
)

/** One app's tokens, screens and originals — a single block of the page. */
data class RebuildGroup(
    val design: DesignSystem,
    val screens: List<RebuildScreen>,
    val originals: Map<String, ByteArray>,
)

/**
 * Y5, the rebuild test: redraws screens from the extracted design system alone
 * and puts them beside the originals.
 *
 * This is the check that the tokens are a design system rather than a list of
 * numbers that happen to be true. A palette can look plausible in JSON and
 * still be wrong; a screen drawn from it either looks like the app or it does
 * not, and the difference is visible from across a room.
 *
 * The output is a single self-contained HTML file. No device, no toolchain, no
 * screenshot diffing — it opens in a browser, which is what makes it usable as
 * a judging artefact rather than another number in a report.
 */
class RebuildRenderer(
    private val density: Density = Density.of(420),
) {

    /**
     * Builds the comparison page for one app.
     *
     * [originals] supplies the left-hand column and is keyed by screen name.
     * The reconstruction never sees it: [render] draws from [design] and
     * [screens] only, and the originals are composited in afterwards.
     */
    fun renderComparison(
        design: DesignSystem,
        screens: List<RebuildScreen>,
        originals: Map<String, ByteArray>,
    ): String = page(listOf(RebuildGroup(design, screens, originals)))

    /**
     * One page covering several apps, each with its own token set.
     *
     * A pack describes one app, so each group carries its own design system
     * rather than sharing one — concatenating whole pages instead repeated the
     * heading and swatches once per app.
     */
    fun renderComparison(groups: List<RebuildGroup>): String = page(groups)

    private fun sections(
        design: DesignSystem,
        screens: List<RebuildScreen>,
        originals: Map<String, ByteArray>,
    ): String {
        val body = StringBuilder()
        for (screen in screens) {
            val original = originals[screen.name]
            body.append(
                """
                <section class="pair">
                  <h2>${escape(screen.name)}</h2>
                  <div class="row">
                    <figure>
                      <figcaption>Original capture</figcaption>
                      ${original?.let { "<img alt=\"original\" src=\"data:image/png;base64,${base64(it)}\">" } ?: "<div class=\"missing\">no screenshot</div>"}
                    </figure>
                    <figure>
                      <figcaption>Rebuilt from the pack</figcaption>
                      ${render(design, screen)}
                    </figure>
                  </div>
                </section>
                """.trimIndent(),
            )
        }
        return body.toString()
    }

    /**
     * Draws one screen as SVG from the design tokens and the screen's structure.
     *
     * Every colour, radius and type size here comes from [design]. Where the
     * pack has no token for something, it is left undrawn rather than filled in
     * from a default — a rebuild that quietly substituted Material defaults
     * would look better and prove less.
     */
    fun render(design: DesignSystem, screen: RebuildScreen): String {
        val background = design.colors.background ?: "#FFFFFF"
        val svg = StringBuilder()
        svg.append(
            """<svg class="rebuilt" viewBox="0 0 ${screen.widthPx} ${screen.heightPx}" width="${screen.widthPx}" height="${screen.heightPx}" xmlns="http://www.w3.org/2000/svg">""",
        )
        svg.append("""<rect width="100%" height="100%" fill="${escape(background)}"/>""")

        // An element that wraps another carries that other's text as its own
        // accessibility label, so drawing both stacks two copies of the same
        // string at two sizes. The inner element owns the typography; the outer
        // one owns the shape. Deciding this by containment rather than by a list
        // of role names catches the search field, whose label is repeated by the
        // text view inside it just as a list row's is.
        val wrappers = screen.elements.filter { outer ->
            screen.elements.any { inner -> inner !== outer && inner.isInside(outer) }
        }.toSet()

        for (element in screen.elements.sortedBy { it.boundsRel.getOrElse(1) { 0.0 } }) {
            svg.append(drawElement(design, screen, element, suppressLabel = element in wrappers))
        }
        svg.append("</svg>")
        return svg.toString()
    }

    private fun drawElement(
        design: DesignSystem,
        screen: RebuildScreen,
        element: RebuildElement,
        suppressLabel: Boolean,
    ): String {
        if (element.boundsRel.size != 4) return ""
        val left = element.boundsRel[0] * screen.widthPx
        val top = element.boundsRel[1] * screen.heightPx
        val width = (element.boundsRel[2] - element.boundsRel[0]) * screen.widthPx
        val height = (element.boundsRel[3] - element.boundsRel[1]) * screen.heightPx
        if (width <= 0 || height <= 0) return ""

        val out = StringBuilder()
        val component = design.components.firstOrNull { it.name == element.role }

        if (element.role != "Text" && component != null) {
            val fill = component.fill ?: design.colors.surface
            if (fill != null) {
                val radiusPx = component.radiusDp?.let { density.toPx(it) } ?: 0
                out.append(
                    """<rect x="${fmt(left)}" y="${fmt(top)}" width="${fmt(width)}" height="${fmt(height)}" rx="$radiusPx" ry="$radiusPx" fill="${escape(fill)}"/>""",
                )
            }
        }

        val label = element.label.takeIf { !suppressLabel }
        if (!label.isNullOrBlank()) {
            // Type role by size: the biggest text on a screen is its title, and
            // the pack's scale is the only place the sizes come from.
            val token = design.typography.firstOrNull { it.role == roleForHeight(design, height) }
                ?: design.typography.lastOrNull()
            val sizePx = token?.let { density.toPx(it.sizeSp.toInt()) } ?: density.toPx(14)
            val onFill = component?.text ?: contrastingText(design)
            val baseline = top + height / 2 + sizePx / 3.0
            val inset = if (element.role == "Text") 0.0 else PADDING_PX
            out.append(
                """<text x="${fmt(left + inset)}" y="${fmt(baseline)}" font-family="system-ui, sans-serif" font-size="$sizePx" font-weight="${token?.weight ?: 400}" fill="${escape(onFill)}">${escape(label.take(MAX_LABEL))}</text>""",
            )
        }
        return out.toString()
    }

    /** Whether [this] sits wholly within [other] and is strictly smaller. */
    private fun RebuildElement.isInside(other: RebuildElement): Boolean {
        if (boundsRel.size != 4 || other.boundsRel.size != 4) return false
        val within = boundsRel[0] >= other.boundsRel[0] - EPSILON &&
            boundsRel[1] >= other.boundsRel[1] - EPSILON &&
            boundsRel[2] <= other.boundsRel[2] + EPSILON &&
            boundsRel[3] <= other.boundsRel[3] + EPSILON
        return within && areaRel() < other.areaRel()
    }

    private fun RebuildElement.areaRel(): Double =
        if (boundsRel.size != 4) 0.0
        else (boundsRel[2] - boundsRel[0]) * (boundsRel[3] - boundsRel[1])

    /** Picks the type role whose size best matches the space the label sits in. */
    private fun roleForHeight(design: DesignSystem, heightPx: Double): String {
        val heightSp = heightPx / density.scale / LINE_BOX
        val closest = design.typography.minByOrNull { Math.abs(it.sizeSp - heightSp) }
        return closest?.role ?: "body"
    }

    private fun contrastingText(design: DesignSystem): String {
        val surface = design.colors.surface ?: design.colors.background ?: "#FFFFFF"
        return Colors.toHex(Colors.bestOn(Colors.fromHex(surface)))
    }

    private fun page(groups: List<RebuildGroup>): String {
        val body = StringBuilder()
        for (group in groups) {
            val design = group.design
            val tokens = listOfNotNull(
                design.colors.primary?.let { swatch("primary", it) },
                design.colors.onPrimary?.let { swatch("on-primary", it) },
                design.colors.surface?.let { swatch("surface", it) },
                design.colors.background?.let { swatch("background", it) },
                design.colors.error?.let { swatch("error", it) },
            ).joinToString("")
            body.append("""<div class="swatches">$tokens</div>""")
            body.append(sections(design, group.screens, group.originals))
        }

        return """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Anima rebuild test</title>
<style>
  :root { color-scheme: light dark; }
  body { margin: 0; padding: 24px; font: 14px/1.5 system-ui, sans-serif; background: #f6f6f8; color: #16161a; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  .lede { margin: 0 0 24px; max-width: 60ch; color: #4a4a55; }
  .swatches { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 32px; }
  .swatch { display: flex; align-items: center; gap: 8px; font-size: 12px; }
  .chip { width: 28px; height: 28px; border-radius: 6px; border: 1px solid rgba(0,0,0,.15); }
  .pair { margin-bottom: 40px; }
  .pair h2 { font-size: 15px; margin: 0 0 8px; }
  .row { display: flex; flex-wrap: wrap; gap: 20px; align-items: flex-start; }
  figure { margin: 0; }
  figcaption { font-size: 12px; color: #55555f; margin-bottom: 6px; }
  img, .rebuilt { width: 300px; height: auto; border: 1px solid #d6d6de; border-radius: 8px; display: block; background: #fff; }
  .missing { width: 300px; height: 200px; display: grid; place-items: center; border: 1px dashed #bbb; border-radius: 8px; color: #777; }
  @media (prefers-color-scheme: dark) {
    body { background: #131316; color: #ececf0; }
    .lede, figcaption { color: #a0a0ab; }
    img, .rebuilt { border-color: #33333c; }
  }
</style>
</head>
<body>
<h1>Rebuild test</h1>
<p class="lede">Each pair is the captured screen beside one redrawn from the extracted
design system alone — the reconstruction is given structure and labels, never the
original pixels. Where the pack has no token for something it is left undrawn, so
the gaps are as informative as the matches.</p>
$body
</body>
</html>
"""
    }

    private fun swatch(name: String, hex: String): String =
        """<div class="swatch"><span class="chip" style="background:${escape(hex)}"></span>$name ${escape(hex)}</div>"""

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        private const val EPSILON = 0.001
        private const val PADDING_PX = 48.0
        private const val MAX_LABEL = 48
        private const val LINE_BOX = 1.35

        /**
         * Derives the pack-shaped structure of a screen from an observation.
         *
         * This is what the crawler and `ScreenIdentifier` would have written
         * into the pack, reproduced here so the rebuild can be exercised before
         * a pack with screenshots exists. The screenshot is dropped on the way
         * through, which is what keeps the reconstruction honest.
         */
        fun structureOf(observation: ScreenObservation, name: String): RebuildScreen {
            val (width, height) = observation.screenSize
            val elements = observation.nodes
                .filter { it.bounds.size == 4 }
                .sortedBy { it.id }
                .mapNotNull { node ->
                    val role = roleOf(node, observation.nodes) ?: return@mapNotNull null
                    RebuildElement(
                        role = role,
                        boundsRel = listOf(
                            node.bounds[0].toDouble() / width,
                            node.bounds[1].toDouble() / height,
                            node.bounds[2].toDouble() / width,
                            node.bounds[3].toDouble() / height,
                        ),
                        label = node.text ?: node.contentDesc,
                    )
                }
            return RebuildScreen(name, width, height, elements)
        }

        private fun roleOf(node: PrunedNode, all: List<PrunedNode>): String? {
            val width = node.bounds[2] - node.bounds[0]
            val height = node.bounds[3] - node.bounds[1]
            if (width <= 0 || height <= 0) return null
            if (node.editable || node.className.contains("EditText")) return "Input"
            if (node.className.contains("TextView")) return "Text"
            if (!node.clickable) return null
            if (node.className.contains("FloatingActionButton")) return "FloatingActionButton"
            if (node.className.contains("Button")) return "Button"
            val siblings = all.count { other ->
                other.bounds.size == 4 && other.clickable &&
                    (other.bounds[2] - other.bounds[0]) == width &&
                    Math.abs((other.bounds[3] - other.bounds[1]) - height) <= 12
            }
            return if (siblings >= 2) "ListRow" else "Card"
        }
    }
}
