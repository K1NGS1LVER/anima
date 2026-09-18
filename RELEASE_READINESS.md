# Release & Display Readiness

> The rest of the plan covers *building* the product. This covers *shipping* it and *showing* it — the part teams discover too late.
>
> Plan: [PLAN.md](PLAN.md) · **Runbook: [EXECUTION_PLAN.md](EXECUTION_PLAN.md)** · Briefs: [ASSIGNMENTS.md](ASSIGNMENTS.md) · Status: [CHECKLIST.md](CHECKLIST.md)
>
> The T-3 → T schedule below is expanded into per-person tasks and exit gates in [EXECUTION_PLAN.md](EXECUTION_PLAN.md) §P2–P5.

## What "display-ready" means here

Not "it works on my machine with the right app open". It means:

**A judge takes the phone out of our hands, picks an app we did not choose, taps Scan, and the app behaves.** If it can't do something, it says so clearly instead of hanging or crashing.

That is a different bar from "the crawler works", and it is the bar we are actually judged against: *Execution & Working Prototype*.

## Owners

| Area | Owner |
| :--- | :--- |
| Release build, store assets, app polish | **Neethu** |
| Integration, code freeze, rehearsal, the live run | **Samuel** |
| Performance budgets for scanning | **Samuel** |
| Model failure behaviour (no network, model absent) | **Daniel** |
| Pack integrity, export, saved-pack loading | **Jacob** |
| Rebuild view, design page correctness | **Jiya** |
| Each module's regression pass before freeze | **Everyone, own module** |

## Performance budgets

Measured on the primary demo device, not a laptop emulator. A scan that takes eleven minutes is a failed demo regardless of output quality.

| Metric | Budget |
| :--- | :--- |
| Scan of a ~30-screen app | **≤ 4 minutes** |
| Time to first screen appearing in the viewer | ≤ 15 s (stream results, don't batch) |
| App cold start | ≤ 2 s |
| Viewer scroll and map pan | 60 fps, no dropped frames on the demo device |
| Understanding call per screen | ≤ 3 s cloud, ≤ 8 s on-device |
| Memory during a scan | no OOM with 40 screens + screenshots held |
| APK size | ≤ 30 MB excluding any downloaded model |

If a budget can't be met, degrade visibly and deliberately: stream partial results, cap screenshot resolution, or lower the step budget — never silently hang.

## Device matrix

| Role | Device | Why |
| :--- | :--- | :--- |
| **Primary demo** | Redmi Note 11 (MIUI, Android 13) | Hardware-verified in Phase 9; every quirk we knew about was on this device. **Unavailable during the S4 pivot bring-up — confirm before T-1, don't assume.** |
| **Second verified device** | Samsung Galaxy M35 (One UI, Android 16 / API 36) | S4 ran here instead. Found two real bugs the Redmi never surfaced: a missing `canTakeScreenshot` capability, and `rootInActiveWindow` unreliably reporting the notification shade as active. Both fixed in `:capture`. Good evidence the crawler survives a second OEM and a much newer Android version, not just MIUI. |
| **Backup demo** | A second phone, different OEM, fully set up and rehearsed | If the primary dies or an update breaks it mid-event. Non-negotiable. |
| **Generality proof** | A third, ideally stock Android | Shows the output is not tuned to one skin. |

Every demo device: developer options on, accessibility granted, overlay granted, battery optimisation disabled for our app, screen timeout raised, Do Not Disturb on, and the target apps installed and logged in ahead of time.

## Failure plan — assume something breaks

The live demo will be on unfamiliar wifi, possibly with no wifi, in front of people.

| Failure | Plan |
| :--- | :--- |
| **No network at the venue** | On-device understanding backend is the fallback. Daniel's fallback chain must be exercised in rehearsal with wifi *actually off*, not simulated. |
| **A live scan stalls or yields a thin pack** | Open a **previously saved pack** in the viewer and keep talking. This is not demo mode — it is the product's own export/import path, which we ship anyway. Have packs for 2–3 apps saved on the device. |
| **The target app updated overnight and broke a journey** | That is the diffing story. Show the diff instead and make it the point. |
| **Phone crashes or the service dies** | Backup phone, already set up. Switch and continue. |
| **A judge picks an unexpected app** | This is the real test and we should want it. The safety envelope must hold on an app we have never seen — that is what the deny-list and package boundary are for. |

Rule: never let the crawler run into a blank screen in silence. Every stall path gets a visible state in the UI.

## Observability on stage

- Crash handler that writes a readable log to app storage rather than dying silently.
- **In-app log export** — when something goes wrong in front of judges, we can show *why* in five seconds. This has more credibility than pretending it didn't happen.
- Scan telemetry visible in the UI: screens found, current action, elapsed, stop reason.
- No third-party analytics. Nothing leaves the device.

## Schedule — the last three days

Dates are relative to the build deadline (T).

| When | What | Owner |
| :--- | :--- | :--- |
| **T-3** | Feature freeze on all five modules. Only integration and bug fixes after this. | Everyone |
| **T-3** | Full merge to `dev`; CI green; end-to-end scan on the primary device. | Samuel |
| **T-2** | **Regression pass** — every module owner runs their own checklist against the merged build, not their branch. | Everyone |
| **T-2** | Backup device set up identically and verified. | Neethu |
| **T-1** | **Full rehearsal, twice.** Once with network, once with wifi off. Timed. | Samuel drives, all present |
| **T-1** | Saved packs for 2–3 apps loaded onto both devices. | Jacob |
| **T-1** | `main` tagged; release APK built and installed from the release build, not debug. | Neethu |
| **T** | No code changes. If it isn't in by T-1, it isn't in. | — |

The rehearsal is the single highest-value hour in the schedule. Teams that skip it discover their bugs in front of judges.

## Release checklist

- [ ] `versionCode` / `versionName` set deliberately, not left at `1` / `1.0.0`
- [ ] `./gradlew :app:assembleRelease` produces an installable APK (unsigned is accepted for this)
- [ ] **Release build actually installed and exercised** — R8 strips things debug builds keep; a release-only crash is the classic last-day disaster
- [ ] ProGuard/R8 rules verified against every reflective entry point (services, receivers, any JSON model classes)
- [ ] App icon, name and launch screen final
- [ ] Store listing assets: screenshots, short and long description, feature graphic
- [ ] Privacy policy screen, and a URL if the listing needs one
- [ ] Permissions: every one declared is actually used and explained in-app
- [ ] No debug logging of screen contents in release
- [ ] Empty / loading / error states on every screen
- [ ] Dark mode on every screen
- [ ] Our own UI passes an accessibility sweep (TalkBack, 48dp targets, font scaling at 200%)
- [ ] First-run experience tested on a **freshly wiped install**, not an upgrade

## Presentation flow

Five people; not everyone should talk. Suggested split — one narrator, one driver, three on standby for questions in their area.

1. **The problem, in one sentence.** Knowledge about an app is recorded by hand and breaks the moment the app updates.
2. **Pick an app live.** Let a judge choose if they will.
3. **Scan it.** Narrate what the agent is deciding while it runs — this is where the autonomy is visible.
4. **Open the pack.** App map, then one screen profile beside its screenshot.
5. **The design system**, extracted not authored.
6. **The rebuild** — two screens recreated from the pack alone, side by side with the originals.
7. **The differentiator:** replay a journey on the device to prove the pack is still true. Then show a diff against an earlier scan.
8. **Stability**, in one command: scan twice, `diff` is empty.

Items 6–8 are what separate us from a prettier JSON file. Do not run out of time before reaching them.

## Honest constraints to state, not hide

Judges ask. Having the answer ready reads as competence; being caught reads as spin.

- **Play Store policy.** An app automating other apps via the accessibility API faces a real policy review. We ship a release-ready unsigned APK and have a distribution answer (enterprise/sideload/developer tool framing), not a pretend one.
- **Scanning third-party apps** raises per-app ToS questions. We scan apps we own or have test access to.
- **What the pack does not yet cover** — say it plainly rather than let it be discovered.
