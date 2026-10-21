# Feature 06 — Frozen-Mode Zoom and Gesture Coordination

## Objective

Make zoom available in frozen mode and ensure tap and pinch gestures behave predictably across both live and frozen content.

## Dependencies

- Feature 03 — Live Preview Zoom
- Feature 05 — Unfreeze and Camera Lifecycle
- [`../docs/architecture.md`](../docs/architecture.md), Sections 5–7

## Scope

- Apply the logical zoom ratio to the frozen image using crop/scale transforms.
- Preserve zoom across live-to-frozen and frozen-to-live transitions.
- Coordinate tap and pinch recognition in one semantic gesture pipeline.
- Allow an intentional upward swipe in live mode to toggle the camera torch.
- Track velocity for completed single-finger frozen pans and apply bounded inertial decay.
- Define tap slop, pointer-count, and cancellation behavior.
- Make the full content area the primary interaction target.

## Requirements

1. A multi-pointer gesture is never treated as a tap.
2. Movement beyond the tap slop cancels a pending tap.
3. Pinch is available in live and frozen modes.
4. Frozen zoom does not re-access the camera.
5. Zoom remains clamped and consistent across both display modes.
6. Rapid valid taps do not leave state transitions concurrent or contradictory.
7. Frozen pan inertia starts only after a single-finger pan release, is cancelled by a new gesture or zoom, and never reveals empty image regions.
8. A single-finger live swipe-up of at least 72 dp with predominantly vertical movement toggles the torch; if it was on at freeze request, it stays on through exposure, turns off while frozen, and is restored after preview resume.

## Acceptance Criteria

- Frozen image zooms smoothly with pinch.
- A quick one-finger frozen pan continues briefly after release and stops at the image bounds.
- An upward swipe toggles the torch in live mode only and gives brief on/off or unavailable feedback.
- A pinch never freezes or unfreezes the image accidentally.
- A valid single tap toggles state in either mode.
- Zoom and crop remain visually consistent when freezing and unfreezing.
- Repeated and interrupted gestures recover cleanly.

## Tests and Device Validation

- Unit-test gesture event interpretation and cancellation.
- Add UI tests for tap, pinch, interrupted pinch, and rapid input.
- Verify live/frozen zoom behavior on the connected device.
- Test one-handed interaction and full-screen tap targeting.

## Risks and Edge Cases

- Compose pointer handlers competing for the same events.
- Fling animation continuing after a new gesture, zoom, or transition back to live mode.
- Frozen image crop exposing invalid or empty regions.
- Scale transform causing excessive recomposition.
- Touch-up after a cancelled pinch being misclassified as a tap.

## Completion Checklist

- [x] Frozen image transform implemented.
- [x] Shared zoom policy used by live and frozen modes.
- [x] Tap/pinch cancellation rules implemented.
- [x] Gesture tests pass.
- [ ] Physical-device gesture validation completed.