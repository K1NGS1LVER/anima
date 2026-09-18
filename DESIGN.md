# Anima Design System

## Visual thesis

Anima is an instrument panel cut from jet-black glass: quiet at rest, with pearlescent controls that look like light travelling across polished metal. It avoids the expected neon-cyber control room in favor of a restrained, physical iridescence drawn from the supplied reference.

## Color roles

- **Ground:** true jet black (`jet_black`) establishes the device-like field.
- **Surfaces:** graphite (`slate_900`) and near-black (`slate_800`) separate grouped tasks without gray page backgrounds.
- **Pearl:** blue-white (`cyan_400`) is the default interactive accent.
- **Prism:** the primary action moves through blue, lilac, peach, and soft green in one deliberate gradient.
- **Status:** green and red remain reserved only for operational state.

## Components

- Sections are 16dp rounded, subtly iridescent-rimmed dark surfaces; do not stack generic cards inside cards.
- The primary demo button uses the prism gradient. The standard Run button uses the pearl fill. Permission controls are outlined pearl buttons.
- Inputs are dark, high-contrast outlined Material fields with a pearl focus indicator.
- Labels use Roboto/Material type roles; mono is restricted to live system values and modes.

## Motion and states

Use native Material press, focus, disabled, and permission states. Gradients are static material, never animated decoration. Keep text readable over every state.
