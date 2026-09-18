package io.agents.anima.design

import io.agents.anima.core.ScreenObservation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Y5 — the rebuild test.
 *
 * Running these writes `build/reports/rebuild/index.html`, which is the artefact
 * worth looking at: each captured screen beside one redrawn from the extracted
 * tokens alone. The assertions below are the part a CI run can check; the page
 * is the part a person can judge.
 */
class RebuildTest {

    private val extractor = AnimaDesignExtractor(density = Observations.REFERENCE_DENSITY)
    private val renderer = RebuildRenderer(density = Observations.REFERENCE_DENSITY)

    @Test
    fun `writes a side-by-side page for every captured screen`() {
        val observations = Observations.realCaptures()

        // One design system per app, not one across all three. A pack describes
        // a single app, and averaging three unrelated brands produced a light
        // window colour that made the dark Clock rebuild look nothing like the
        // Clock. Grouping here is what the production path does by construction.
        val pages = observations.groupBy { it.packageName }.entries.sortedBy { it.key }

        val html = renderer.renderComparison(
            pages.map { (packageName, screens) ->
                RebuildGroup(
                    design = extractor.extract(screens),
                    screens = screens.map { RebuildRenderer.structureOf(it, packageName) },
                    originals = screens.mapNotNull { observation ->
                        observation.screenshot?.let { packageName to it }
                    }.toMap(),
                )
            },
        )

        val output = File("build/reports/rebuild/index.html")
        output.parentFile.mkdirs()
        output.writeText(html)
        println("rebuild page: ${output.absolutePath}")

        assertTrue("every app should appear", pages.all { html.contains(it.key) })
        assertTrue("originals should be embedded", html.contains("data:image/png;base64,"))
        assertEquals(
            "one rebuilt svg per screen",
            observations.size,
            Regex("class=\"rebuilt\"").findAll(html).count(),
        )
    }

    @Test
    fun `the reconstruction is drawn only from tokens the pack carries`() {
        val observation = Observations.settings()
        val design = extractor.extract(listOf(observation))
        val screen = RebuildRenderer.structureOf(observation, "settings")

        val svg = renderer.render(design, screen)

        // The structure the renderer is handed has nowhere to put a screenshot,
        // so this can only be satisfied by drawing from the tokens.
        assertFalse("no pixels may leak into the rebuild", svg.contains("base64"))
        assertTrue("the window colour comes from the tokens", svg.contains(design.colors.background!!))
        assertTrue("elements are drawn", svg.contains("<rect"))
        assertTrue("labels are drawn", svg.contains("<text"))
    }

    @Test
    fun `a screen with no tokens renders rather than throwing`() {
        // The empty-pack case has to degrade, not crash: a scan that failed to
        // extract anything still has to produce a page a person can look at to
        // see that it failed.
        val empty = AnimaDesignExtractor().extract(emptyList())
        val screen = RebuildRenderer.structureOf(Observations.settings(), "settings")

        val svg = renderer.render(empty, screen)

        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.endsWith("</svg>"))
    }

    @Test
    fun `the same pack renders byte-identical html`() {
        val observations = Observations.realCaptures()
        val design = extractor.extract(observations)
        val screens = observations.map { RebuildRenderer.structureOf(it, it.packageName) }

        val first = renderer.renderComparison(design, screens, emptyMap())
        val second = RebuildRenderer(density = Observations.REFERENCE_DENSITY)
            .renderComparison(design, screens, emptyMap())

        assertEquals(first, second)
    }

    @Test
    fun `structure carries relative bounds and no pixels`() {
        val observation: ScreenObservation = Observations.settings()

        val screen = RebuildRenderer.structureOf(observation, "settings")

        assertTrue(screen.elements.isNotEmpty())
        assertTrue(
            "bounds are fractions of the frame",
            screen.elements.all { element -> element.boundsRel.all { it in -0.01..1.01 } },
        )
    }
}
