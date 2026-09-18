# `:app` — the product surface

**Owner: Neethu** · branch `feat/app-ui`

The only part judges actually touch. Onboarding, permission gate, app picker, scan control, the viewer, export, and Play readiness.

Anima's own visual design is [`DESIGN.md`](DESIGN.md) in this directory. That is distinct from the `design_system` section of a Knowledge Pack, which is the visual language Anima *extracts from an app it scans* and belongs to `:design`.

## Your next tasks

Full detail in [`EXECUTION_PLAN.md`](../../EXECUTION_PLAN.md) §Neethu.

1. **N1 — retire the 3-act demo.** `MainActivity`, `activity_main.xml`, `item_act_result.xml` and `TaskerReceiver` all belong to the retired task-execution product. Replace, don't extend. The pearlescent theme survives; the layouts it was first applied to do not.
2. **N2 — onboarding and permission gate.** The screen that decides whether a judge trusts the app.
3. **N3 — app picker.**
4. **N4 — scan control.** Build against a fake `ScanController` emitting scripted `ScanListener` events and swap in Samuel's when gate **G6** opens. Do not wait to start.
5. **N5 — the viewer.** App map, screen list, **screen profile beside its screenshot**, journeys, design-system page.
6. **N6 — export and share** the `.animapack`. `PackArchive.exportPack` already exists.
7. **N7 — production polish**, including accessibility of our own UI. An app built on the accessibility API that is itself inaccessible would be indefensible.

Note: `fixtures/packs/golden.animapack` is a placeholder (issue #5). Don't build viewer test expectations against it yet.
