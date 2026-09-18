# Anima — Phase 10 Plan: Make It Actually Learn, On Any Phone

> **Working branch:** `dev-sam`
> **Status:** planned, not started. Everything below is intent — see [CHECKLIST.md](CHECKLIST.md) for what is actually proven.
> **Companion docs:** [CHECKLIST.md](CHECKLIST.md) · [CURRENT_PROGRESS.md](CURRENT_PROGRESS.md) · [dev_plan.md](dev_plan.md) §14 (full technical spec)

## Phase 9, for context: shipped

The APK builds, installs and was verified on a physical Redmi Note 11: it drove the real Settings UI, toggled Wi-Fi and Bluetooth, and replayed compiled skills at **0 LLM calls** in ~264ms. CI builds the APK on every push. That work is done; this plan is what comes next.

## Why this phase exists

Two gaps, both confirmed by reading the code rather than the docs.

**1. The cold path compiles exactly one step.** `anima.py:1271` and `AnimaRuntime.kt:179` each build a one-element list. There is no loop. Every multi-step skill in the repo is a hand-written test fixture, and "set an alarm for 7:30" is unreachable by construction. The replay side is already N-step-general, so the entire gap is on the compile side.

**2. There is no model in the APK, and the planner is fitted to one phone.** `AnimaEngine.kt:15` hardcodes the heuristic planner. Every planner rule added in Phase 9 — punctuation-stripping for "Wi-Fi", the actionable-ancestor redirect, exact-label preference, the toggle-row rule that exists *only* because MIUI renders a switch as a `CheckBox` — is hand-fitted to one skin on one device. Samsung, Pixel and ColorOS differ; a German phone shares no English keywords at all.

A model reading the pruned Agent-DOM generalises where keyword rules cannot. **Device-agnostic operation is the objective; the model is the means.** The heuristic stays as the offline floor.

## What "done" looks like

1. A single goal can teach Anima a **multi-step flow**, compiled into one N-step skill and replayable at 0 LLM calls.
2. A real model sits behind the `Planner` interface, selectable between heuristic / on-device / cloud.
3. **The same goal runs on ≥3 unrelated OEM skins with no per-OEM code.** This is the bar that matters; passing on the Redmi alone proves nothing about generality.
4. An autonomous run cannot wander into a payment or deletion control without a human.
5. Reported metrics say what they mean: `plannerCalls` vs `llmCalls`.

## Workstream A — Multi-step cold planning

A bounded `plan → act → observe` loop replacing the one-shot compile, in `AnimaRuntime.run` (Python) and `AnimaRuntime.coldCompile` (Kotlin).

Per iteration: capture → biometric guard → popup intercept → screen signature → `planStep(goal, dom, nodes, history)` → execute → re-capture → essential-state check → append to trajectory.

Guards, which are the whole safety story: **step budget** (~12), **no-progress abort** (two unverified steps), **cycle detection** (repeated screen signature), and a **new destructive-action guard** requiring HITL for Delete / Pay / Buy / Send / Confirm. A trajectory that aborts is reported and discarded — never compiled.

Known friction: Python has a second single-step compile site (visual fallback) that Kotlin lacks entirely; Kotlin has no screen-signature function (the whole PTG subsystem is Python-only) and no `stepsVerified` field; `DemoDevice` is a single static screen so the demo would not exercise the loop; and `test_e2e_multi_screen_journey` models a journey as three separate goals, so it needs redesigning rather than re-asserting.

## Workstream B — A real planner behind the seam

```
Planner (interface)
├── HeuristicPlanner   offline floor, default          [exists]
├── OnDevicePlanner    quantized Gemma, LiteRT/MediaPipe
└── CloudPlanner       Gemini REST, opt-in, cloud flavor only
```

A `PlannerFactory` gates each option on what is actually available — on-device only with a model present, cloud only after explicit consent — and always falls back to the heuristic. The prompt is `UIFormer.toCompactJson(nodes)` plus goal and history; the response is the strict JSON action contract `LocalLiteRTPlanner` already speaks.

**Build flavors (`offline` / `cloud`) keep the strongest claim provable.** Cloud and model download both need `INTERNET`, and a universal APK carrying that permission would turn "cannot phone home" from a fact checkable with `grep INTERNET` into a promise.

## Sequence

1. Workstream A first — it is useful with no model at all, and it defines the `history` and `done` interface changes that B depends on.
2. Extend `DemoDevice` to a multi-screen fixture so the loop is demonstrable.
3. Destructive-action guard, before any autonomous loop is pointed at a real app.
4. `PlannerFactory` + flavors, then the on-device planner, then cloud.
5. Cross-OEM validation on ≥3 skins. Not optional; it is the acceptance bar.

## Out of scope for Phase 10

- Production hardening (PII redaction, DB encryption, signing, Play policy) — tracked separately.
- Porting the Page Transition Graph dashboard to Kotlin; only the screen-signature function is needed.
- Visual grounding fallback in Kotlin, unless the loop work forces the decision.

## Verification

- `make test-all` and `./gradlew :app:testDebugUnitTest` green throughout.
- A multi-step skill compiled from a single goal, replayed at `plannerCalls=0`, asserted in both runtimes.
- The cross-runtime test extended to a 2+-step skill, so multi-step skills stay portable between laptop and phone.
- Loop guards tested directly: budget exhaustion, no-progress abort, cycle abort, destructive-action halt.
- **The cross-OEM run recorded in `CURRENT_PROGRESS.md` with device names and outcomes** — three skins, no per-OEM code.
