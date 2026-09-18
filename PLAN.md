# Anima — Autonomous App Cartographer

> **The project pivoted.** Anima was a task-execution agent (goal → taps → compiled skill). It is now a system that explores an unfamiliar Android app hands-off and emits a structured, stable, compact **App Knowledge Pack**.
>
> Contract: [KNOWLEDGE_PACK.md](KNOWLEDGE_PACK.md) · Who does what: [ASSIGNMENTS.md](ASSIGNMENTS.md) · Status: [CHECKLIST.md](CHECKLIST.md) · Shipping & demo: [RELEASE_READINESS.md](RELEASE_READINESS.md) · Architecture SSOT: [dev_plan.md](dev_plan.md) §15

> **This file is the why and the what. The phase-by-phase runbook — who does what, in what order, and which gates block whom — is [EXECUTION_PLAN.md](EXECUTION_PLAN.md).**

## The problem

An in-app agent can only help inside a host app if it knows that app as well as someone who built it — every screen, what each does, how they connect, how it looks and speaks. Today that knowledge is recorded by hand: slow, incomplete, and stale the moment the app updates.

## What we're building

A normal user installs Anima, picks an installed app, and taps **Scan**. Anima explores it unattended and produces a browsable App Knowledge Pack: an app map, a profile per screen beside its screenshot, the journeys it found, and the app's design system. The pack exports as a file another AI can read.

No demo mode. No staged fixtures in the product. It scans whatever you point it at.

## Why we're well-positioned

Phase 9 shipped a working on-device agent verified on real hardware. Several pieces transfer directly:

| Asset | Why it matters now |
| :--- | :--- |
| **UIFormer pruning** | Already solves the 1.5 MB problem — drops non-interactive containers, >60% reduction asserted in tests. The most valuable reuse in the repo. |
| **Accessibility capture + gestures** | The execution plane an explorer needs: node capture, label inheritance, tap/swipe/text, foreground package detection. |
| **Screen-graph shape** | `PageTransitionGraph` is nodes-and-edges over screens — exactly an app map. The shape is right; the hash function is not and gets replaced. |
| **Popup interceptor / biometric guard** | Dialog dismissal is needed constantly while crawling; the biometric halt becomes a crawl boundary. |
| **Kill switch + overlay** | Safety-critical when an agent crawls an unfamiliar app unattended. |
| **Skill replay engine** | **Repurposed, not retired** — see below. |

Built from nothing: exploration policy, screenshots, scroll, LLM/VLM understanding, design extraction, the pack schema and store, gate passing, and the viewer.

## The differentiator: the pack is executable

Everyone else's output will be documentation. Ours contains journeys that can be **replayed to verify they are real** — which is exactly the tested replay engine Anima already has. A knowledge pack that can prove itself is a far better answer to *"the knowledge breaks the moment the app updates"* than a prettier JSON file.

## Architecture

Gradle modules, one owner each. Everything depends on `:core` and nothing else horizontal; `:app` depends on all.

```
:core        pure Kotlin — pack schema, stable IDs, canonicalization, UIFormer
:capture     Android — accessibility, screenshots, scroll, window enumeration
:explore     exploration policy, frontier, coverage, safety envelope
:understand  LLM/VLM screen + journey + form-field semantics
:design      brand and design token extraction
:store       SQLite, pack assembly, diffing, export
:app         onboarding, scan control, viewer, Play readiness
```

**Landed.** `:core` is a plain Kotlin/JVM library rather than an Android one, so the whole engine still unit-tests on a laptop with no emulator — a `NoAndroidImportsTest` enforces that rather than leaving it to good intentions. `:capture` owns the accessibility service and overlay declarations and the consent string, so `:app`'s manifest declares product surfaces only. The SDK levels are declared once in the root build file and read by all seven modules.

## The three properties that actually get judged

1. **Stable** — two scans of the same app version produce byte-identical output. Achieved by SHA-256 structural hashing that excludes all dynamic content, canonical ordering, and caching every LLM result by screen hash. The current `compute_screen_signature` fails this outright (Python's `hash()` is salted per process) and is being replaced, not ported.
2. **Compact** — `pack.json` ≤ 512 KB for a 40-screen app, enforced by a test that fails the build.
3. **Machine-readable** — flat, predictable, no prose blobs where structure will do.

## Team

| Person | Owns | Branch |
| :--- | :--- | :--- |
| **Samuel** | Exploration engine (`:capture`, `:explore`) + integration | `feat/explorer` |
| **Daniel** | Understanding — LLM/VLM (`:understand`) | `feat/understanding` |
| **Jacob** | Schema, stable IDs, store, diffing (`:core`, `:store`) | `feat/knowledge-store` |
| **Jiya** | Design & brand extraction, rebuild test (`:design`) | `feat/design-extract` |
| **Neethu** | Product app & viewer (`:app`) | `feat/app-ui` |

Full briefs in [ASSIGNMENTS.md](ASSIGNMENTS.md).

## Day 0 — done, except the fixtures

Modules split, **pack schema frozen in code** (`core/Pack.kt`), module interfaces agreed (`core/Contracts.kt`), minSdk raised to 30 for `AccessibilityService.takeScreenshot()`, and all five feature branches pushed off `dev`.

One thing outstanding and it blocks two people: **Jacob's golden fixture packs in `fixtures/packs/`**. Jiya and Neethu are waiting on nothing else.

Branch protection on `main` is a GitHub repo setting rather than a commit, so it is Samuel's to click before the first PR.

## Decisions

| Decision | Rationale |
| :--- | :--- |
| **Hybrid, swappable LLM** | Cloud (Gemini) is the fast default for building and the live demo; on-device Gemma is the privacy story and offline fallback; heuristics are the floor. One interface, three backends. |
| **In-app viewer** | Matches "an app facing a normal user" and keeps the deliverable a single Play-shaped artifact. |
| **Gradle modules** | Five people in one module is a merge-conflict machine. Module boundaries are enforced by the compiler. |
| **No demo mode** | The product scans real apps. Fixtures exist for development and tests, never as a staged product path. |
| **Journeys are replayable** | Turns the retired replay engine into verification, and into the differentiator. |

## Risks

- **Play Store policy.** An app that automates *other* apps via the accessibility API is a genuine rejection risk. The goal is a release-ready unsigned APK, so this is a flag, not a blocker — but decide the framing before writing a store listing.
- **Scanning third-party apps** raises per-app ToS questions. Fine for owned and test apps; worth a sentence in the pitch rather than a surprise from a judge.
- **LLM non-determinism vs. stability.** Mitigated by caching every model result by structural hash. If that cache is wrong, the headline requirement fails.
- **Crawler safety.** An unattended agent inside a banking app is the real risk in this project. Deny-list, package boundary, budgets and kill switch are all mandatory, not polish.

## The bar: display-ready

Not "it works on my machine with the right app open". **A judge takes the phone, picks an app we did not choose, taps Scan, and it behaves** — and when it cannot do something it says so instead of hanging.

That is a different bar from "the crawler works", and it is what *Execution & Working Prototype* actually measures. Performance budgets, the device matrix, failure paths, the freeze/rehearsal schedule and the release checklist live in [RELEASE_READINESS.md](RELEASE_READINESS.md).

## Verification

- **Stability:** two consecutive scans → byte-identical `screens[]`, `journeys[]`, `design_system`. The headline test.
- **Compactness:** pack under budget for a 40-screen app.
- **Coverage:** ≥30 screens on the fintech target with no human input.
- **Rebuild:** 2–3 screens recreated from the pack alone, shown beside the originals.
- **Journey replay:** a recorded journey replays successfully on a real device.
- **Gates:** a login/OTP flow traversed autonomously with test credentials.
- **Cross-device:** the same app scanned on two devices produces the same screen IDs.
- CI green on every branch; `assembleRelease` produces an installable unsigned APK.
