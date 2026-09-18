package io.agents.anima.engine

import android.content.Context

/**
 * The public engine facade the app talks to.
 *
 * Everything here runs on device: the skill library is local SQLite, grounding is a deterministic
 * heuristic planner, and there is no network code anywhere in this package.
 */
class AnimaEngine(context: Context) {

    val store: SkillStore = SkillStore(context.applicationContext)

    private val planner: Planner = HeuristicPlanner()

    /**
     * Runs [goal] against [device]: warm speculative replay when a skill (or a parameter template)
     * matches, otherwise a cold heuristic plan that is auto-compiled into the skill library.
     */
    fun run(
        goal: String,
        device: AgentDevice,
        params: Map<String, String> = emptyMap(),
    ): ExecutionResult = AnimaRuntime.execute(store, planner, goal, device, params)

    /**
     * The staged 3-act demo (cold compile -> warm 0-call replay -> drift + popup chaos), run
     * against the hermetic [DemoDevice] and an isolated in-memory skill library so it never
     * touches the real skill database and never needs a target app.
     *
     * [onAct] is invoked after each act with that act's name and result.
     */
    fun runThreeActDemo(onAct: (actName: String, result: ExecutionResult) -> Unit): List<ExecutionResult> =
        DemoScript.run(planner = planner, repo = MemorySkillStore(), onAct = onAct)

    /** Exports the on-device skill library in the Python runtime's `skills.json` format. */
    fun exportSkills(): String = store.exportJson()

    /** Imports a `skills.json` produced by the Python runtime. Returns the number of skills read. */
    fun importSkills(json: String?): Int = store.importJson(json)
}
