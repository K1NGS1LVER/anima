package io.agents.anima.core

/**
 * The verbs an autonomous crawler needs from a device.
 *
 * Deliberately separate from `AgentDevice`, which serves the goal-execution
 * runtime. That interface knows tap, type and key; an explorer additionally has
 * to scroll, go back, relaunch an app it fell out of, take a screenshot and ask
 * where it currently is. Widening `AgentDevice` would have forced a scroll
 * implementation onto the hermetic `DemoDevice` that unit-tests the replay
 * engine, for no benefit to it.
 *
 * Every method returns whether it actually happened. An explorer that assumes
 * its gestures land walks off the app and keeps crawling somebody else's.
 */
interface ExplorationDevice {

    /** The current screen: pruned tree, pixels and where we are. Null when no window is readable. */
    fun observe(): ScreenObservation?

    fun tap(x: Int, y: Int): Boolean

    /** Focuses [node]'s coordinates and types [text] into it. */
    fun inputText(x: Int, y: Int, text: String): Boolean

    /**
     * Scrolls within the rectangle [boundsPx] (`[l,t,r,b]`), or the whole screen
     * when null.
     *
     * @return false when the gesture was refused; it does **not** mean the
     *   content moved. Whether anything actually scrolled is decided by
     *   comparing observations, because a swipe on an already-exhausted list
     *   dispatches perfectly well and changes nothing.
     */
    fun scroll(direction: ScrollDirection, boundsPx: List<Int>? = null): Boolean

    fun back(): Boolean

    fun home(): Boolean

    /** Cold-starts [packageName], for recovery after falling out of the target app. */
    fun launch(packageName: String): Boolean

    /** The package of the window currently in front, or null when unreadable. */
    fun currentPackage(): String?

    /**
     * Best effort, and honestly so: switching the system theme needs
     * `WRITE_SECURE_SETTINGS`, which a normal app cannot hold. Returns false
     * when unavailable, and the scan then records only the mode it did see
     * rather than pretending to a light/dark diff it never made.
     */
    fun setUiMode(mode: UiMode): Boolean

    /** Blocks until the UI has plausibly settled after an action. */
    fun settle(ms: Long)
}

enum class ScrollDirection {
    /** Reveals content further down the page (a swipe upward). */
    DOWN,
    UP,
    LEFT,
    RIGHT,
}
