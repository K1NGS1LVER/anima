package io.agents.anima.engine

/**
 * Core data model for the on-device Anima engine.
 *
 * This file is a 1:1 port of the dataclasses in `anima.py` and is deliberately free of any
 * `android.*` import so it can be exercised by plain JVM unit tests.
 *
 * Kotlin property names are camelCase; the *serialized* JSON keys stay snake_case (see
 * [SkillJson]) so a `skills.json` written by the Python runtime imports into the phone
 * unchanged, and vice versa.
 */

/** A structurally pruned accessibility node (UIFormer Agent-DOM element). */
data class PrunedNode(
    val id: Int,
    val className: String,
    val resourceId: String? = null,
    val text: String? = null,
    val contentDesc: String? = null,
    val clickable: Boolean = false,
    val checked: Boolean = false,
    /**
     * Whether this node scrolls. Read during capture since the very first
     * version and then thrown away, which meant the crawler could not tell a
     * screen it had finished reading from one that had four more pages below
     * the fold. Not serialized into the Agent-DOM: [UIFormer.toCompactJson] is
     * a cross-runtime contract with anima.py and adding a key there would break
     * the Python importer for a field the planner never reads.
     */
    val scrollable: Boolean = false,
    /** Whether text can be typed into this node. Drives form filling. */
    val editable: Boolean = false,
    val bounds: List<Int> = listOf(0, 0, 0, 0),
    val center: List<Int> = listOf(0, 0),
    val relBounds: List<Double> = listOf(0.0, 0.0, 0.0, 0.0),
    val relCenter: List<Double> = listOf(0.0, 0.0),
)

/** Weighted multi-attribute locator. Every field is optional; only non-null fields are scored. */
data class Locator(
    val resourceId: String? = null,
    val contentDesc: String? = null,
    val text: String? = null,
    val className: String? = null,
    val bounds: List<Int>? = null,
    val relBounds: List<Double>? = null,
    val relCenter: List<Double>? = null,
) {
    companion object {
        /** Copies the seven locator-relevant fields off a captured node, 1:1. */
        fun fromNode(node: PrunedNode): Locator = Locator(
            resourceId = node.resourceId,
            contentDesc = node.contentDesc,
            text = node.text,
            className = node.className,
            bounds = node.bounds,
            relBounds = node.relBounds,
            relCenter = node.relCenter,
        )
    }
}

/**
 * One compiled step of a skill.
 *
 * [locator] is a `var` on purpose: the warm replay loop heals a drifted locator in place and
 * persists the repaired skill mid-run.
 */
data class Step(
    val action: String, // "tap" | "input_text" | "key"
    var locator: Locator,
    val paramSlot: String? = null,
    val value: String? = null,
)

/** A compiled, replayable skill keyed by its (possibly templated) intent. */
data class Skill(
    val intent: String,
    val steps: List<Step>,
    var successCount: Int = 0,
    var failureCount: Int = 0,
)

/** Outcome of a single [AnimaRuntime.execute] invocation. */
data class ExecutionResult(
    val mode: String,        // "COLD_COMPILED" | "WARM_REPLAY" | "HITL_PAUSED" | "FAILED"
    val success: Boolean,
    val stepsExecuted: Int,
    val llmCalls: Int,
    val latencyMs: Long,
    val message: String,
)

/**
 * The execution plane abstraction. Implemented by [AccessibilityDevice] (real phone) and
 * [DemoDevice] (hermetic in-memory twin used by the 3-act demo and unit tests).
 */
interface AgentDevice {
    /** Raw text used by [BiometricGuard] (an XML dump, or a flattened node dump). */
    fun rawDump(): String

    /** The current screen as pruned, normalized nodes. */
    fun captureNodes(): List<PrunedNode>

    /** (widthPx, heightPx) */
    fun screenSize(): Pair<Int, Int>

    fun tap(x: Int, y: Int)

    fun inputText(text: String)

    fun key(keycode: Int)
}
