package io.agents.anima.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crawler's two promises, tested: it never touches anything irreversible,
 * and it walks the same app the same way twice.
 */
class ExplorerTest {

    /** A small app with a settings branch, a list, and one control we must never touch. */
    private fun demoApp() = FakeApp(
        screens = mapOf(
            "Home" to FakeScreen(
                "Home",
                listOf(
                    FakeElement("Accounts", top = 400, leadsTo = "Accounts"),
                    FakeElement("Settings", top = 600, leadsTo = "Settings"),
                    FakeElement("Help", top = 800, leadsTo = "Help"),
                ),
            ),
            "Accounts" to FakeScreen(
                "Accounts",
                listOf(FakeElement("Statement", top = 400, leadsTo = "Statement")),
                parent = "Home",
                scrollable = true,
            ),
            "Statement" to FakeScreen("Statement", emptyList(), parent = "Accounts"),
            "Settings" to FakeScreen(
                "Settings",
                listOf(
                    FakeElement("Notifications", top = 400, leadsTo = "Notifications"),
                    FakeElement("Log out", top = 600, leadsTo = "Home"),
                    FakeElement("Delete account", top = 800, leadsTo = "Home"),
                ),
                parent = "Home",
            ),
            "Notifications" to FakeScreen("Notifications", emptyList(), parent = "Settings"),
            "Help" to FakeScreen("Help", emptyList(), parent = "Home"),
        ),
        start = "Home",
    )

    private fun scan(app: FakeApp, budget: ScanBudget = ScanBudget()): ScanOutcome =
        Explorer(FakeDevice(app), StructuralIdentifier(), budget).scan(app.packageName)

    @Test
    fun itFindsTheAppWithoutBeingTold() {
        val app = demoApp()
        val outcome = scan(app)

        // Six screens exist and every one of them is reachable by tapping.
        assertEquals(6, outcome.screenCount)
        assertEquals(StopReason.FRONTIER_EXHAUSTED, outcome.stopReason)
        assertTrue("expected observed transitions", outcome.edges.isNotEmpty())
    }

    @Test
    fun itNeverTapsSomethingIrreversible() {
        val app = demoApp()
        scan(app)

        val forbidden = app.taps.filter {
            it.contains("Log out", ignoreCase = true) || it.contains("Delete", ignoreCase = true)
        }
        assertTrue(
            "the crawler tapped a destructive control: $forbidden",
            forbidden.isEmpty(),
        )
        // And it still explored the screen those controls live on, rather than
        // avoiding the whole screen -- refusing one button must not cost a page.
        assertTrue("Settings was never explored", app.taps.any { it.endsWith(":Notifications") })
    }

    @Test
    fun twoScansOfTheSameAppWalkItIdentically() {
        // The headline requirement. If the visit order differs, screen ids are
        // assigned in a different sequence and no amount of careful hashing
        // downstream can make two packs byte-identical.
        val first = demoApp()
        val second = demoApp()

        val a = scan(first)
        val b = scan(second)

        assertEquals(first.taps, second.taps)
        assertEquals(first.visitOrder, second.visitOrder)
        assertEquals(a.screens.keys.toList(), b.screens.keys.toList())
        assertEquals(
            a.edges.map { "${it.from}->${it.to}" },
            b.edges.map { "${it.from}->${it.to}" },
        )
    }

    @Test
    fun theSameScreenIsRecordedOnceHoweverOftenItIsRevisited() {
        val app = demoApp()
        val outcome = scan(app)

        assertEquals(
            "screens must be keyed by structural identity, not by visit",
            outcome.screens.keys.size,
            outcome.screens.keys.distinct().size,
        )
        assertTrue("Home is revisited on the way back", app.visitOrder.count { it == "Home" } > 1)
        assertEquals(6, outcome.screenCount)
    }

    @Test
    fun itComesBackWhenAnActionThrowsItOutOfTheApp() {
        val app = FakeApp(
            screens = mapOf(
                "Home" to FakeScreen(
                    "Home",
                    listOf(
                        FakeElement("Open browser", top = 400, leavesApp = true),
                        FakeElement("About", top = 600, leadsTo = "About"),
                    ),
                ),
                "About" to FakeScreen("About", emptyList(), parent = "Home"),
            ),
            start = "Home",
        )
        val outcome = scan(app)

        assertEquals("should have recovered and kept exploring", app.packageName, app.currentPackage)
        assertTrue("About should still have been found", outcome.screenCount >= 2)
    }

    @Test
    fun anAuthenticationScreenIsRecordedButNotDriven() {
        val app = FakeApp(
            screens = mapOf(
                "Home" to FakeScreen("Home", listOf(FakeElement("Unlock", top = 400, leadsTo = "Lock"))),
                "Lock" to FakeScreen(
                    "Lock",
                    listOf(FakeElement("Use fingerprint", top = 600, leadsTo = "Vault")),
                    parent = "Home",
                    marker = "Confirm your pattern",
                ),
                "Vault" to FakeScreen("Vault", emptyList(), parent = "Lock"),
            ),
            start = "Home",
        )
        val outcome = scan(app)

        assertTrue("the lock screen itself belongs in the pack", outcome.screenCount >= 2)
        assertTrue(
            "an auth prompt must be recorded as a boundary",
            outcome.skipped.any { it.reason == "authentication prompt" },
        )
        assertFalse(
            "the agent must never drive an authentication screen",
            app.taps.any { it.endsWith(":Use fingerprint") },
        )
    }

    @Test
    fun itScrollsAScreenThatHasMoreBelowTheFold() {
        val app = FakeApp(
            screens = mapOf(
                "Feed" to FakeScreen(
                    "Feed",
                    listOf(FakeElement("First post", top = 400, leadsTo = "Post")),
                    scrollable = true,
                    scrolledInto = "FeedPage2",
                ),
                "FeedPage2" to FakeScreen(
                    "FeedPage2",
                    listOf(FakeElement("Tenth post", top = 400, leadsTo = "Post")),
                    parent = "Feed",
                ),
                "Post" to FakeScreen("Post", emptyList(), parent = "Feed"),
            ),
            start = "Feed",
        )
        val device = FakeDevice(app)
        val outcome = Explorer(device, StructuralIdentifier()).scan(app.packageName)

        assertTrue("a scrollable screen must get a scroll pass", device.scrolls > 0)
        assertTrue("content below the fold should be discovered", outcome.screenCount >= 3)
    }

    @Test
    fun aMissingAppEndsTheScanCleanlyRatherThanHanging() {
        val outcome = Explorer(
            object : io.agents.anima.core.ExplorationDevice by FakeDevice(demoApp()) {
                override fun observe() = null
                override fun currentPackage() = null
                override fun launch(packageName: String) = false
            },
            StructuralIdentifier(),
        ).scan("com.example.absent")

        assertEquals(StopReason.FAILED_TO_START, outcome.stopReason)
        assertEquals(0, outcome.screenCount)
    }

    @Test
    fun theKillSwitchStopsItWithinOneStep() {
        val app = demoApp()
        var steps = 0
        val outcome = Explorer(
            FakeDevice(app),
            StructuralIdentifier(),
            isAborted = { steps++ >= 2 },
        ).scan(app.packageName)

        assertEquals(StopReason.ABORTED, outcome.stopReason)
        assertTrue("abort must not discard what was already found", outcome.screenCount >= 1)
    }

    @Test
    fun everyBudgetHasAStopReasonThatSaysSo() {
        val app = demoApp()
        assertEquals(StopReason.STEP_BUDGET, scan(app, ScanBudget(maxSteps = 2)).stopReason)
        assertEquals(StopReason.SCREEN_BUDGET, scan(demoApp(), ScanBudget(maxScreens = 2)).stopReason)
        assertEquals(
            StopReason.NOVELTY_DECAY,
            scan(demoApp(), ScanBudget(noveltyDecayLimit = 1)).stopReason,
        )
    }

    @Test
    fun aDepthCeilingStopsTheWalkWithoutStoppingTheScan() {
        val app = demoApp()
        val outcome = scan(app, ScanBudget(maxDepth = 1))

        assertTrue(
            "screens past the depth ceiling should be recorded as skipped",
            outcome.skipped.any { it.reason == "depth budget" },
        )
        assertNotNull(outcome.stopReason)
    }
}
