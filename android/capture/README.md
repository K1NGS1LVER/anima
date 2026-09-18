# `:capture` — the perception and execution plane

**Owner: Samuel** · branch `feat/explorer`

Everything that touches the device: the accessibility service, screenshots, gestures, scrolling and window enumeration. This module owns the accessibility service declaration and its consent string, so the machinery is self-contained and `:app` declares only product surfaces.

| File | What |
| :--- | :--- |
| `AnimaAccessibilityService.kt` | Node capture, gesture dispatch, screenshots, window enumeration, kill switch. |
| `FloatingOverlayService.kt` | Live HUD and the always-available **Take Control** kill switch. |
| `capture/Screenshotter.kt` | `AccessibilityService.takeScreenshot()` (API 30+), downscaled to WebP. |
| `capture/ScreenCapture.kt` | Assembles a `ScreenObservation` from tree + pixels. |
| `engine/AccessibilityDevice.kt` | `AgentDevice` over the real phone. |

Nothing above `:capture` is allowed to talk to `android.accessibilityservice` directly.
