# Anima's own UI — visual design

> **Scope: the way *our* app looks.** Not to be confused with the `design_system` section of a Knowledge Pack, which is the visual language Anima *extracts from an app it scans* — that is `:design`, owned by Jiya, and the two must not borrow each other's vocabulary.
>
> Owner: Neethu · module `:app`

## Visual thesis

Anima is an instrument panel cut from jet-black glass: quiet at rest, with pearlescent controls that look like light travelling across polished metal. It avoids the expected neon-cyber control room in favor of a restrained, physical iridescence drawn from the supplied reference.

## Color roles

- **Ground:** true jet black (`jet_black`) establishes the device-like field.
- **Surfaces:** graphite (`slate_900`) and near-black (`slate_800`) separate grouped tasks without gray page backgrounds.
- **Pearl:** blue-white (`pearl_white`) is the default interactive accent.
- **Prism:** the primary action moves through blue, lilac, peach, and soft green in one deliberate gradient.
- **Status:** green and red remain reserved only for operational state.

## Components

- Sections are 16dp rounded, subtly iridescent-rimmed dark surfaces; do not stack generic cards inside cards.
- The primary action uses the prism gradient; secondary actions use the pearl fill; permission controls are outlined pearl buttons.
- Inputs are dark, high-contrast outlined Material fields with a pearl focus indicator.
- Labels use Roboto/Material type roles; mono is restricted to live system values and modes.

## Motion and states

Use native Material press, focus, disabled, and permission states. Gradients are static material, never animated decoration. Keep text readable over every state.

The single authored motion moment is the launch burst: **ANIMA** assembles from the jet-black field, then opens into a pearlescent radial burst that hands off to onboarding. It is a one-time continuity cue—not a repeating loading effect—and respects Android's system animation scale.


## Where this applies next

The theme, colours and drawables here survive. The layouts they were first
applied to do not: `activity_main.xml` is the retired 3-act demo scoreboard and
`item_act_result.xml` goes with it (see `EXECUTION_PLAN.md` §N1). Carry the
palette onto the onboarding gate, app picker, scan control and viewer instead.

Accessibility is not optional for this palette. Pearl on jet black is high
contrast, but the muted `slate_400` on `slate_900` is the pairing to check
against WCAG AA at small sizes, and every state — pressed, focused, disabled —
has to stay readable. An app built on the accessibility API being hard to read
is the first thing a judge will notice.
