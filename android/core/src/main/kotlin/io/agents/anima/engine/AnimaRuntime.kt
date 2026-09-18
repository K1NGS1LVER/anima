package io.agents.anima.engine

/**
 * The dual-mode orchestrator: Intent -> warm speculative replay OR cold heuristic planning +
 * auto-compilation. Ported from `AnimaRuntime.run` in `anima.py`.
 *
 * Free of `android.*` imports on purpose, so the whole control flow (including the 3-act demo)
 * is exercisable by plain JVM unit tests.
 *
 * `llmCalls` keeps the Python contract's meaning — "planner invocations" — even though the Kotlin
 * planner is a deterministic on-device heuristic and performs ZERO network calls. A warm replay
 * that needed no re-grounding therefore reports 0; a cold compile or a drift heal reports 1.
 */
object AnimaRuntime {

    const val MODE_COLD = "COLD_COMPILED"
    const val MODE_WARM = "WARM_REPLAY"
    const val MODE_HITL = "HITL_PAUSED"
    const val MODE_FAILED = "FAILED"

    const val MSG_HITL =
        "Biometric/session prompt detected; autonomous handling paused for user authentication"

    /** Settle delay after dismissing a popup, before re-capturing the screen. */
    private const val POPUP_SETTLE_MS = 50L

    fun execute(
        repo: SkillRepository,
        planner: Planner,
        goal: String,
        device: AgentDevice,
        params: Map<String, String> = emptyMap(),
    ): ExecutionResult {
        val start = System.nanoTime()
        val (skill, extractedParams) = repo.findMatch(goal)

        val merged = LinkedHashMap<String, String>()
        merged.putAll(extractedParams)
        merged.putAll(params)

        return if (skill != null) {
            warmReplay(repo, planner, goal, device, skill, merged, start)
        } else {
            coldCompile(repo, planner, goal, device, start)
        }
    }

    // ------------------------------------------------------------------ warm

    private fun warmReplay(
        repo: SkillRepository,
        planner: Planner,
        goal: String,
        device: AgentDevice,
        skill: Skill,
        params: Map<String, String>,
        start: Long,
    ): ExecutionResult {
        var executed = 0
        var driftDetected = false
        val unverifiedSteps = ArrayList<Int>()

        for (idx in skill.steps.indices) {
            val step = skill.steps[idx]

            // 1 + 2. Fresh capture every step, biometric guard BEFORE matching or popup handling.
            if (BiometricGuard.detect(device.rawDump())) {
                return ExecutionResult(
                    mode = MODE_HITL,
                    success = false,
                    stepsExecuted = executed,
                    llmCalls = 0,
                    latencyMs = elapsedMs(start),
                    message = MSG_HITL,
                )
            }
            var nodes = device.captureNodes()

            // 3. Popup interceptor: re-capture when something was dismissed.
            if (PopupInterceptor.checkAndHandle(nodes, device, goal)) {
                sleep(POPUP_SETTLE_MS)
                nodes = device.captureNodes()
            }

            // 4. Weighted locator match.
            var target = Matcher.findBest(step.locator, nodes)

            // 5. Drift: re-ground once and heal the skill in place.
            if (target == null) {
                driftDetected = true
                val recovered = planner.planStep(goal, UIFormer.toCompactJson(nodes), nodes)
                if (recovered == null) {
                    return ExecutionResult(
                        mode = MODE_WARM,
                        success = false,
                        stepsExecuted = executed,
                        llmCalls = 1,
                        latencyMs = elapsedMs(start),
                        message = "Locator drift at step $idx; recovery failed",
                    )
                }
                target = recovered.target
                step.locator = Locator.fromNode(target)
                repo.save(skill) // heal-in-place: persist the repaired locator immediately
            }

            // 6. Dispatch.
            dispatch(device, step.action, target, params[step.paramSlot ?: ""] ?: step.value ?: "", step.value)
            executed += 1

            // Improvement over the Python runtime: essential-state progress check. Never aborts.
            if (!EssentialStateVerifier.verifyProgress(nodes, device.captureNodes(), target)) {
                unverifiedSteps.add(idx)
            }
        }

        skill.successCount += 1
        repo.save(skill)

        val base = if (driftDetected) {
            "Self-healed drifted locator and succeeded"
        } else {
            "Speculative replay succeeded with 0 LLM calls"
        }
        return ExecutionResult(
            mode = MODE_WARM,
            success = true,
            stepsExecuted = executed,
            llmCalls = if (driftDetected) 1 else 0,
            latencyMs = elapsedMs(start),
            message = withProgressNote(base, unverifiedSteps),
        )
    }

    // ------------------------------------------------------------------ cold

    private fun coldCompile(
        repo: SkillRepository,
        planner: Planner,
        goal: String,
        device: AgentDevice,
        start: Long,
    ): ExecutionResult {
        if (BiometricGuard.detect(device.rawDump())) {
            return ExecutionResult(MODE_HITL, false, 0, 0, elapsedMs(start), MSG_HITL)
        }

        var nodes = device.captureNodes()
        if (PopupInterceptor.checkAndHandle(nodes, device, goal)) {
            sleep(POPUP_SETTLE_MS)
            nodes = device.captureNodes()
        }

        if (nodes.isEmpty()) {
            return ExecutionResult(
                mode = MODE_FAILED,
                success = false,
                stepsExecuted = 0,
                llmCalls = 0,
                latencyMs = elapsedMs(start),
                message = "No actionable UI elements on screen for goal: $goal",
            )
        }

        val decision = planner.planStep(goal, UIFormer.toCompactJson(nodes), nodes)
            ?: return ExecutionResult(
                mode = MODE_FAILED,
                success = false,
                stepsExecuted = 0,
                llmCalls = 1,
                latencyMs = elapsedMs(start),
                message = "Could not plan an action for goal: $goal",
            )

        dispatch(device, decision.action, decision.target, decision.value ?: "", decision.value)

        // Auto-compile the trajectory, extracting a dynamic parameter slot where possible.
        val (templatedIntent, slotName) = ParameterExtractor.parameterizeGoal(goal, decision.value)
        val skill = Skill(
            intent = templatedIntent,
            steps = listOf(
                Step(
                    action = decision.action,
                    locator = Locator.fromNode(decision.target),
                    paramSlot = slotName,
                    value = decision.value,
                )
            ),
            successCount = 1,
        )
        repo.save(skill)

        val unverified = ArrayList<Int>()
        if (!EssentialStateVerifier.verifyProgress(nodes, device.captureNodes(), decision.target)) {
            unverified.add(0)
        }

        return ExecutionResult(
            mode = MODE_COLD,
            success = true,
            stepsExecuted = 1,
            llmCalls = 1,
            latencyMs = elapsedMs(start),
            message = withProgressNote("Cold-start planned and compiled into the on-device skill library", unverified),
        )
    }

    // --------------------------------------------------------------- helpers

    private fun dispatch(
        device: AgentDevice,
        action: String,
        target: PrunedNode,
        textValue: String,
        rawValue: String?,
    ) {
        when (action) {
            "tap" -> device.tap(target.center.getOrElse(0) { 0 }, target.center.getOrElse(1) { 0 })
            "input_text" -> {
                device.inputText(textValue)
                // Always dismiss the IME afterwards so it cannot occlude the next locator.
                device.key(KEYCODE_BACK)
            }
            "key" -> device.key(rawValue?.trim()?.toIntOrNull() ?: KEYCODE_BACK)
            else -> device.tap(target.center.getOrElse(0) { 0 }, target.center.getOrElse(1) { 0 })
        }
    }

    private fun withProgressNote(base: String, unverifiedSteps: List<Int>): String =
        if (unverifiedSteps.isEmpty()) {
            base
        } else {
            "$base; essential-state progress unverified at step(s) ${unverifiedSteps.joinToString(",")}"
        }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000L

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    const val KEYCODE_BACK = 4
}
