# Team Assignments

> Five people, five modules, five branches. Read your section, read [KNOWLEDGE_PACK.md](KNOWLEDGE_PACK.md), and you have everything you need. After Day 0, nobody blocks anybody.
>
> Plan: [PLAN.md](PLAN.md) · **Runbook: [EXECUTION_PLAN.md](EXECUTION_PLAN.md)** · Contract: [KNOWLEDGE_PACK.md](KNOWLEDGE_PACK.md) · Status: [CHECKLIST.md](CHECKLIST.md) · Shipping & demo: [RELEASE_READINESS.md](RELEASE_READINESS.md)
>
> **This file says what you own. [EXECUTION_PLAN.md](EXECUTION_PLAN.md) says what to do next, in what order, and what to wait for.** Point your agent at both.
>
> **Everyone**: the last three days (freeze → regression pass → rehearsal) are in [RELEASE_READINESS.md](RELEASE_READINESS.md), and your module's regression pass is your own job.

## The rule that keeps us out of each other's way

**You only edit files inside your own module.** Everything talks through `:core` interfaces and the pack schema. If you need something from someone else's module, you need an interface change, not a file edit — raise it, don't reach in.

| Module | Owner | Branch |
| :--- | :--- | :--- |
| `:core` (schema, IDs, canonicalization, UIFormer) | Jacob | `feat/knowledge-store` |
| `:capture` + `:explore` | Samuel | `feat/explorer` |
| `:understand` | Daniel | `feat/understanding` |
| `:design` | Jiya | `feat/design-extract` |
| `:store` | Jacob | `feat/knowledge-store` |
| `:app` | Neethu | `feat/app-ui` |

## Day 0 — done, except the fixtures

Samuel has landed it on `dev`. You do not need to repeat any of it:

1. ✅ **Seven Gradle modules**, one owner each. `./gradlew projects` lists them.
2. ✅ **Pack schema frozen in code** — `core/src/main/kotlin/io/agents/anima/core/Pack.kt`, matching [KNOWLEDGE_PACK.md](KNOWLEDGE_PACK.md).
3. ⬜ **Golden fixture packs** — **Jacob**. Jiya and Neethu are blocked on this and nothing else, so it is the highest-priority task on the project.
4. ✅ **Module interfaces agreed** — `core/src/main/kotlin/io/agents/anima/core/Contracts.kt`. Signatures only; implement yours in your own module.
5. ✅ **Branches created off `dev`** and pushed. Yours already exists — check it out, don't create it.

Also landed: **minSdk 26 → 30** for `AccessibilityService.takeScreenshot()`, which is far cleaner than MediaProjection and needs no per-session consent dialog. The SDK levels are declared once in `android/build.gradle.kts` and read by every module.

### Start here

```bash
git fetch origin
git checkout feat/<yours>        # it exists already
cd android && . ./env.sh && ./gradlew test
```

If `./gradlew test` is not green before you have written a line, something is wrong with your setup, not with your code. Fix that first.

Read `<your-module>/README.md` — each one names its owner, its files and its contract.

## Branches

```
main                  release-ready, protected. Samuel merges.
└── dev               integration. Rebase on it daily.
    ├── feat/explorer          Samuel
    ├── feat/understanding     Daniel
    ├── feat/knowledge-store   Jacob
    ├── feat/design-extract    Jiya
    └── feat/app-ui            Neethu
```

All five already exist on the remote:

```bash
git fetch origin
git checkout feat/<yours>
# daily:
git fetch origin && git rebase origin/dev
```

Every PR keeps CI green. A `:core` change needs Samuel **and** Jacob to approve.

---

## Samuel — Exploration engine

**Branch `feat/explorer` · modules `:capture`, `:explore`**

You own the part that makes it autonomous, and you integrate at the end.

### Capture gaps — closed

All four landed in `:capture`:

- **Screenshots** — `AnimaAccessibilityService.takeScreenshotSync()` (API 30+), with `capture/Screenshotter.kt` downscaling to the 720 px / 60 KB budget in the contract.
- **Scroll** — `ExplorationDevice.scroll()`. A successful gesture does not mean content moved; the caller decides that by comparing observations, because an exhausted list swipes perfectly well and changes nothing.
- **Multi-window** — `captureAllWindowNodes()` via `getWindows()`, ordered by layer then window id.
- **`isScrollable`** — now stored on `AccessibilityNode` and `PrunedNode`, and scrollable containers survive `UIFormer` pruning.

### The exploration loop

Frontier queue over unexplored elements. Each cycle: capture → prune → identify screen (via `:core` hash) → if new, enqueue its actionable elements → pick the highest-value unexplored action → act → observe → record the edge.

Action vocabulary: tap, scroll, back, open drawer, fill field (delegating field *type* to Daniel's module), rotate/toggle dark mode (for Jiya's light/dark diff).

**Stop conditions:** frontier exhausted, step budget, wall-clock budget, or novelty decay (N consecutive actions yielding no new screen).

### Safety envelope — not optional

An unattended crawler inside a real banking app is the risk in this project.

- **Never tap destructive or irreversible controls:** Delete, Remove, Pay, Buy, Send, Transfer, Confirm, Log out, Close account. Deny-list first, model-assisted classification second.
- **Never leave the target package.** Detect and return.
- **Recover when lost:** Back, then Home, then relaunch.
- **Hard budgets** on steps, depth and wall-clock.
- **Kill switch always live** — `FloatingOverlayService` already has it; keep it working.

### Determinism

Seeded, ordered traversal: the same app scanned twice visits screens in the same order. Without this the byte-identical acceptance test cannot pass, no matter how good Jacob's hashing is.

### Reuse

`PopupInterceptor` (dismisses consent dialogs mid-crawl), `BiometricGuard` (a crawl boundary — stop, don't solve), `UIFormer` (compaction), and the guard-calling skeleton at `AnimaRuntime.kt:66-83`.

### Built

`:explore` — `Frontier`, `ExplorationPolicy`, `SafetyEnvelope`, `ScanBudget`, `Explorer`. 33 tests, including two scans of a fake app producing identical tap order, visit order, screen ids and edges.

### Still open — all of it needs the phone

The crawler scans a real app with no human input, ≥30 screens on the fintech target, zero destructive controls across ten consecutive scans, and two consecutive scans visiting screens in the same order **on hardware**, where timing, animations and OEM behaviour are real. Also outstanding: the drawer-opening edge swipe, and wiring coverage into `scan.coverage` once Jacob's assembler exists.

---

## Daniel — Understanding

**Branch `feat/understanding` · module `:understand`**

You make it work on *any* app instead of any *tested* app. Every keyword rule currently in the planner was hand-fitted to one MIUI phone; your module is what replaces that with something general.

### The interface

Now frozen in `core/Contracts.kt` — implement it, don't redesign it:

```kotlin
interface ScreenUnderstander {
    fun describe(observation: ScreenObservation, screenId: String, context: ScanContext): ScreenProfile
    fun describeJourney(path: List<JourneyStep>, screens: List<Screen>, context: ScanContext): Journey
    fun toneOfVoice(copy: List<String>, context: ScanContext): ToneOfVoice
}
```

`ScreenObservation` carries the pruned tree *and* the screenshot from the same capture, so you get both without asking the crawler for anything. `screenId` is passed in rather than computed by you: it is the cache key, and caching by it is what actually makes the pack stable.

`ScreenProfile` fills the LLM-authored fields of the pack: screen `name`, `purpose`, `kind`, per-element `semantic`, and `input.type` for form fields — **including when the app gives no roles or labels**, which is the explicit hard case in the brief. Its maps are keyed by `PrunedNode.id`, so the crawler attaches your output without re-matching anything.

### Three backends, one interface

- `CloudVlm` — Gemini. Fast, high quality, default while building and for the live demo.
- `OnDeviceLlm` — Gemma via MediaPipe/LiteRT. The privacy story and the offline fallback.
- `HeuristicUnderstander` — always available, no model, no network. The floor.

Compose with fallback, the way Python's `HybridPlanner` (`anima.py:711`) does: try the real model, fall back on any failure. `GeminiPlanner.plan_visual` (`anima.py:574`) is the only existing screenshot→VLM code in the repo — pure `urllib`, no SDK — and is your starting reference.

### Non-negotiables

- **Strict JSON out.** Schema + validation + repair + fallback. A malformed model response must never break a scan.
- **Cache by structural hash.** Stability is your problem as much as Jacob's: temperature 0 is necessary but not sufficient. Cache every result keyed by screen/element ID so a rescan reuses the prior text verbatim.
- **Token discipline.** You get the pruned Agent-DOM, not the raw tree. Don't undo `UIFormer`'s work by re-inflating the prompt.

### Also yours

Journey naming and summarization from graph paths, and the `tone_of_voice` classification from collected copy.

### Done when

A screen with no labels and no roles still gets a correct purpose and correctly typed form fields; malformed model output degrades to heuristics without failing; and two runs over the same screens produce identical text.

---

## Jacob — Knowledge store & the contract

**Branch `feat/knowledge-store` · modules `:core` (schema), `:store`**

You own the spine. The two requirements most likely to be judged — stability and compactness — are yours.

Your interfaces are frozen in `core/Contracts.kt`: `ScreenIdentifier` (screen and element ids, plus the signature block) and `PackRepository` (save, load, canonical JSON, diff). The pack types themselves are in `core/Pack.kt`. Both files are yours to change, with Samuel's approval, since four people build against them.

### Ship first, on Day 0

**Golden fixture packs** in `fixtures/packs/`. Jiya and Neethu are blocked on nothing else once these exist. Hand-build the first one if you must, then replace it with a real scan output as soon as one exists.

### Stable ID engine

Implement the hashing spec in [KNOWLEDGE_PACK.md](KNOWLEDGE_PACK.md). **Do not port `compute_screen_signature` from `anima.py:869`** — it uses Python's `hash()`, which is salted per process, so it produces different IDs on every run. It also folds in prune-order indices, reads only the first 10 nodes, and truncates to four digits. Write the real thing: SHA-256 over a canonical structural fingerprint, excluding all dynamic content.

### Canonical JSON writer

Fixed key order, fixed float precision, `Locale.US`. Do not rely on map iteration order — `SkillJson.kt:18` documents why this bites specifically on Android (`org.json` is `HashMap`-backed on the JVM, `LinkedHashMap`-backed on device).

### Storage

New SQLite schema: app → scans → screens → elements → journeys. The existing `skills` table is keyed by goal string and is the wrong shape — treat `SkillStore` as a reference for *how we do SQLite here*, not as an extension point.

### Diffing

Scan N vs N+1 → added / removed / changed screens. This is the direct answer to "knowledge breaks the moment the app updates", and it demos better than anything else in the project.

### Compaction

String interning, component dedupe, WebP screenshots, omit empty fields. **Enforce the size budget with a test that fails the build.**

### Done when

Two consecutive scans produce byte-identical output, a 40-screen pack fits the budget, diffing correctly reports a screen added between two app versions, and `.animapack` round-trips.

---

## Jiya — Design & brand extraction

**Branch `feat/design-extract` · module `:design`**

Self-contained and testable from static fixtures — you can build and verify all of it without the crawler ever running. Implement `DesignExtractor` from `core/Contracts.kt`: in, a list of `ScreenObservation` (each carrying a screenshot and its pruned node list); out, the `DesignSystem` section of the pack.

### What to extract

- **Colors** — quantize the screenshot for the real palette; cross-check against theme attributes where readable. Classify primary / surface / background / error rather than dumping a histogram.
- **Typography** — family, size, weight from the node tree where exposed; VLM fallback where not. Group into roles (display / title / body / caption).
- **Spacing & shape** — infer the base grid unit (usually 4 or 8 dp) and corner radii from element bounds.
- **Components** — detect recurring patterns (primary button, card, input, list row) into a catalog with a `seen_on` count.
- **Light/dark** — Samuel's crawler can toggle the mode; diff the tokens between the two passes.
- **Tone of voice** — register plus a short summary and real examples, from the copy the scan collected.

### The rebuild test — your headline

The brief explicitly asks for it: **recreate 2–3 screens from the pack alone** and show them beside the originals. It validates your own output, it is one of the judging criteria, and it is the most visually convincing thing we can put in front of a judge. Build it as a screen in the app (coordinate the entry point with Neethu; the rendering is yours).

### Working without the crawler

Use `fixtures/packs/` plus the screenshots in it. Everything in your module should unit-test from a static image and a static node list — no device required.

### Done when

Extracted tokens match the target app by eye, the same screenshot produces identical tokens on repeat runs, and two rebuilt screens are recognizably the originals.

---

## Neethu — Product app & viewer

**Branch `feat/app-ui` · module `:app`**

The only part judges actually touch. You can build all of it from Jacob's fixtures on day 1 — zero dependency on the crawler.

### Screens to build

1. **Onboarding & permission gate.** Productionize what exists: explain plainly *why* an accessibility service is needed, deep-link to the settings screens, show live status, re-check on resume.
2. **App picker.** Installed apps, icon, label, search, recents.
3. **Scan control.** Start / pause / stop, live progress (screens found, coverage, elapsed), and an always-visible abort. The user must never feel the phone has been taken away from them.
4. **The viewer** — the centrepiece the brief asks for:
   - App map: zoomable, pannable screen graph.
   - Screen list with thumbnails.
   - **Screen profile beside its screenshot** — name, purpose, elements, form fields.
   - Journeys browser.
   - Design-system page (renders Jiya's section).
5. **Export & share** the `.animapack`.

### Production polish — this is what "production grade" means here

Adaptive icon, store listing assets, privacy policy screen, empty / loading / error states for every screen, dark mode, and **accessibility of our own UI** — content descriptions, touch target sizes, font scaling. An app built on the accessibility API that is itself inaccessible would be indefensible.

### What exists today

One Activity, 253 lines, and two layouts. Nearly everything is net-new, so you will rarely collide with anyone. The 3-act demo scoreboard in `MainActivity` is being retired — don't build on it.

### Done when

A person who has never seen the project can install the APK, grant permissions, scan an app, and browse the result without being told how; `assembleRelease` produces an installable APK; every screen has empty/error states.

---

## Integration

Samuel merges to `dev` and ties the system together. Daily: rebase, run your module's tests, push. If you are blocked on another module, use the fixtures — that is what they are for.

---

## Everyone — the last three days

Feature freeze at **T-3**, regression pass at **T-2**, **two full rehearsals at T-1** (one with wifi off). Your module's regression pass against the *merged* build — not your branch — is yours. Details and the release checklist: [RELEASE_READINESS.md](RELEASE_READINESS.md).

The rehearsal is the highest-value hour in the schedule. Teams that skip it find their bugs in front of judges.
