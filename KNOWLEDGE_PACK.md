# App Knowledge Pack — Schema v1

> **This is the team contract.** Every module produces or consumes this document. It is frozen on Day 0; changing it after that stalls four people at once, so changes need a PR approved by **Samuel and Jacob**.
>
> Owner: **Jacob** (`:core` + `:store`). Consumers: everyone.

## What it is

A complete, structured, machine-readable description of an Android app, produced by scanning it autonomously: every screen, what each one does, the elements and form fields on it, the journeys connecting them, and the app's brand and design language.

Three properties matter more than completeness:

1. **Stable** — two scans of the same app version produce byte-identical output.
2. **Compact** — raw UI trees exceed 1.5 MB; a whole pack for a 40-screen app must stay far below that.
3. **Readable by another AI** — flat, predictable, no prose blobs where structure will do.

## File layout

A pack exports as `.animapack`, a zip:

```
pack.json              the document below
screens/scr_*.webp     one screenshot per screen, WebP, downscaled
```

## Schema

```jsonc
{
  "pack_version": "1.0",

  "app": {
    "package": "com.example.bank",
    "label": "Example Bank",
    "version_name": "4.12.0",
    "version_code": 41200
  },

  // Everything in "scan" is EXCLUDED from the stability diff.
  "scan": {
    "id": "scan_2026_09_18T04_12Z",
    "started_at": "2026-09-18T04:12:00Z",
    "duration_ms": 184000,
    "device": { "model": "2201117TI", "sdk": 33, "resolution": [1080, 2400], "locale": "en-IN" },
    "coverage": { "screens_found": 42, "elements_found": 812, "frontier_remaining": 3, "stop_reason": "frontier_exhausted" },
    "understander": { "backend": "cloud_vlm", "model": "gemini-2.5-flash", "cache_hits": 38 }
  },

  "screens": [
    {
      "id": "scr_a91f3c8b0d24",              // stable structural hash, 12 hex
      "name": "Account Overview",             // LLM, cached by id
      "purpose": "Shows the user's balance and recent transactions. Entry point to transfers and card controls.",
      "kind": "list|form|detail|dialog|onboarding|auth|settings|other",

      "signature": {
        "structural_hash": "a91f3c8b0d24…",   // full SHA-256
        "anchors": ["com.example:id/balance_card", "com.example:id/tx_list"],
        "activity": "com.example.ui.HomeActivity"   // when observable
      },

      "elements": [
        {
          "id": "el_7c2d91ab",
          "role": "button|input|toggle|link|list|list_item|text|image|tab|nav",
          "label": "Transfer",                 // visible label, inherited if on a child
          "semantic": "Starts a money transfer to a saved payee",
          "bounds_rel": [0.08, 0.42, 0.92, 0.49],
          "actions": ["tap"],
          "leads_to": "scr_ff10a2bc7e31",     // null when it does not navigate
          "input": {                            // present only for role=input
            "type": "email|phone|otp|amount|date|password|text",
            "required": true,
            "hint": "Registered email",
            "max_length": 64
          }
        }
      ],

      "screenshot": "screens/scr_a91f3c8b0d24.webp",
      "modes_seen": ["light", "dark"]
    }
  ],

  "journeys": [
    {
      "id": "jr_3f21a0",
      "name": "Sign in with OTP",
      "goal": "Authenticate an existing user",
      "preconditions": ["app freshly installed", "valid test account"],
      "steps": [
        { "screen": "scr_...", "element": "el_...", "action": "tap" },
        { "screen": "scr_...", "element": "el_...", "action": "input", "input_slot": "{email}" },
        { "screen": "scr_...", "element": "el_...", "action": "input", "input_slot": "{otp}" }
      ],
      "outcome": "scr_a91f3c8b0d24",
      "replayable": true,                       // verified by replaying it
      "verified_at_scan": "scan_2026_09_18T04_12Z"
    }
  ],

  "design_system": {
    "colors": {
      "primary": "#1B5E20", "on_primary": "#FFFFFF",
      "surface": "#FFFFFF", "background": "#F7F9F7",
      "error": "#B3261E",
      "palette": ["#1B5E20", "#2E7D32", "#F7F9F7", "#212121"]
    },
    "typography": [
      { "role": "display", "family": "Inter", "size_sp": 28, "weight": 700 },
      { "role": "body",    "family": "Inter", "size_sp": 14, "weight": 400 }
    ],
    "spacing": { "base_dp": 8, "scale": [4, 8, 16, 24, 32] },
    "shape": { "radius_dp": [8, 16, 28] },
    "components": [
      { "name": "PrimaryButton", "fill": "#1B5E20", "text": "#FFFFFF", "radius_dp": 28, "height_dp": 48, "seen_on": 14 }
    ],
    "modes": { "light": { "surface": "#FFFFFF" }, "dark": { "surface": "#121212" } },
    "tone_of_voice": {
      "register": "formal|friendly|terse|playful",
      "summary": "Direct and reassuring; uses second person; avoids financial jargon.",
      "examples": ["Your money is on its way", "Check the details before you send"]
    }
  },

  "graph": {
    "edges": [
      { "from": "scr_a91f3c8b0d24", "to": "scr_ff10a2bc7e31", "via": "el_7c2d91ab", "action": "tap" }
    ]
  }
}
```

## Stability rules — the hard requirement

Two scans of the same app version **must** produce byte-identical `screens[]`, `journeys[]`, `design_system` and `graph`. Only `scan` may differ.

**The existing code fails this and must not be copied.** `PageTransitionGraph.compute_screen_signature` (`anima.py:869`) uses Python's built-in `hash()`, which is salted per process — two runs of the same scan produce different screen IDs. It also folds in prune-order node indices, reads only the first 10 nodes, and truncates to four digits. It is a sketch, not a content hash.

### Screen ID

```
screen_id = "scr_" + sha256(canonical_fingerprint)[0:12]
```

`canonical_fingerprint` is built from **structure only**:
- sorted list of non-null `resource_id`s on the screen (the strongest stable signal — compiled into the APK)
- the class-path skeleton (e.g. `FrameLayout>RecyclerView>LinearLayout`), sorted and deduped
- the activity name when observable

**Explicitly excluded:** all visible text, element counts, bounds, timestamps, screenshot bytes, list contents, badge numbers, dates, user data. Anything that changes between two runs on the same screen must not feed the hash.

### Element ID

```
element_id = "el_" + sha256(screen_id + "|" + (resource_id ?: role) + "|" + structural_path)[0:8]
```

Never include bounds or dynamic labels. A list row whose text changes between scans must keep its ID.

### Canonicalization

- Every array sorted by `id`. Never emit in discovery order.
- Object keys emitted in a fixed declared order — **do not** rely on map iteration. `SkillJson.kt:18` documents why: `org.json` is `HashMap`-backed on the JVM and `LinkedHashMap`-backed on Android, so the same code produces different key order on the two platforms.
- Floats formatted to a fixed precision (4 dp) with a fixed locale. `String.format` without an explicit `Locale.US` will emit `0,5` in some locales.
- No timestamps, durations or counters anywhere outside `scan`.

### LLM output must be cached by structural hash

`name`, `purpose`, `semantic` and `tone_of_voice` come from a model, and models are not deterministic. Cache every model result **keyed by the screen/element ID** and reuse it on rescan. Temperature 0 is necessary but not sufficient — the cache is what actually makes the pack stable.

## Size budget

| Item | Budget |
| :--- | :--- |
| `pack.json` for a 40-screen app | **≤ 512 KB** |
| One screenshot | ≤ 60 KB (WebP, longest edge 720 px) |
| Whole `.animapack` | ≤ 6 MB |

Enforced by a test that fails the build when exceeded. Techniques: string interning for repeated labels, component dedupe, dropping `bounds_rel` precision beyond 4 dp, and omitting any null/empty field rather than emitting `null`.

## Fixtures

`fixtures/packs/` holds golden packs checked into the repo. **Jacob ships these on Day 0** so Jiya and Neethu can build against real data before the crawler exists. Every fixture is a real scan output, never hand-written, and is regenerated only deliberately.

## Versioning

`pack_version` is bumped on any breaking schema change. `:store` keeps a reader for the previous version so existing packs stay loadable.
