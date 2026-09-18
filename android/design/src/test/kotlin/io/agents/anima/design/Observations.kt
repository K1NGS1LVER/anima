package io.agents.anima.design

import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.UiMode
import io.agents.anima.engine.UIFormer

/**
 * Builds `ScreenObservation`s for tests from files on disk — the same shape the
 * crawler produces, so a test fixture and a real scan are interchangeable.
 *
 * WHY THIS EXISTS: the shared `fixtures/packs/golden.animapack` is a placeholder
 * with no screenshots in it (gate G1), and a screenshot plus bounds is this
 * module's entire input. Rather than wait, `:design` makes its own.
 *
 * TO CAPTURE A REAL ONE — any app, any phone or emulator, thirty seconds:
 *
 *     adb shell uiautomator dump /sdcard/d.xml && adb pull /sdcard/d.xml
 *     adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png
 *
 * Drop the pair into `src/test/resources/fixtures/` under a matching name and
 * load it with [load]. Keep doing this even after real packs exist: a fixture
 * from an app whose brand you can see with your own eyes is what lets a test
 * assert "the background is this lavender" instead of "whatever it was last
 * time".
 *
 * Two gotchas, both of which cost real time the first time:
 *  - `uiautomator dump` captures whatever window is on top, including an ANR
 *    dialog or a permission prompt. Check the `package=` in the XML is the app
 *    you meant before trusting the fixture.
 *  - on Git Bash, `adb shell ... /sdcard/x` gets rewritten to a Windows path.
 *    Export `MSYS_NO_PATHCONV=1` first.
 */
object Observations {

    private const val DIR = "/fixtures/"

    /**
     * The device the committed fixtures were captured on: a 1080x2400 emulator
     * at 420dpi, confirmed with `wm size` and `wm density`.
     *
     * Recorded here because `ScreenObservation` has nowhere to put it (Q6), and
     * an assertion in dp is only meaningful next to the density it was measured
     * at. [REFERENCE_DENSITY] is what the extractors should be given for these
     * fixtures; the inference in [Density] is what they fall back to in
     * production, and it agrees with this value for this frame size.
     */
    val REFERENCE_DENSITY = Density.of(420)

    /** The synthetic fixture predates the real captures and is drawn at 3.0. */
    val SYNTHETIC_DENSITY = Density.of(480)

    /**
     * Loads `<name>.xml` and, when present, `<name>.png` from test resources.
     *
     * [screenSize] must match the frame the dump came from — `uiautomator dump`
     * records absolute pixel bounds, so a wrong size silently skews every
     * derived measurement.
     */
    fun load(
        name: String,
        screenSize: Pair<Int, Int> = 1080 to 2400,
        uiMode: UiMode = UiMode.LIGHT,
        packageName: String = "com.example.bank",
        activity: String? = "com.example.bank.ui.HomeActivity",
    ): ScreenObservation {
        val xml = readText("$DIR$name.xml")
            ?: error("Missing fixture $DIR$name.xml — see Observations for how to capture one")
        return ScreenObservation(
            packageName = packageName,
            activity = activity,
            nodes = UIFormer.prune(xml, screenSize),
            screenshot = readBytes("$DIR$name.png"),
            screenSize = screenSize,
            uiMode = uiMode,
        )
    }

    /** The Settings home screen: light, Material 3, cards on a tinted window. */
    fun settings(): ScreenObservation = load(
        name = "settings_home",
        packageName = "com.android.settings",
        activity = "com.android.settings.homepage.SettingsHomepageActivity",
    )

    /** The Clock app: a dark-surfaced app while the system itself is in light mode. */
    fun clock(): ScreenObservation = load(
        name = "clock_home",
        packageName = "com.google.android.deskclock",
        activity = "com.android.deskclock.DeskClock",
    )

    /** Contacts: an empty-state list with a floating action button. */
    fun contacts(): ScreenObservation = load(
        name = "contacts_home",
        packageName = "com.google.android.contacts",
        activity = "com.android.contacts.activities.PeopleActivity",
    )

    /** Every real capture, which is what a whole-pack extraction sees. */
    fun realCaptures(): List<ScreenObservation> = listOf(settings(), clock(), contacts())

    private fun readText(path: String): String? =
        Observations::class.java.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun readBytes(path: String): ByteArray? =
        Observations::class.java.getResourceAsStream(path)?.use { it.readBytes() }
}
