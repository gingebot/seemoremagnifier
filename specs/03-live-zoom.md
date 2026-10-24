<!-- SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Feature 03 — Live Preview Zoom

## Objective

Allow users to pinch-zoom the live rear-camera preview with bounded, stable zoom behavior.

## Dependencies

- Feature 02 — Camera Permission and Live Preview
- [`../docs/architecture.md`](../docs/architecture.md), Sections 5–7

## Scope

- Read the camera's minimum and maximum zoom ratios.
- Add coordinated Compose pinch recognition.
- Convert gesture scale to a zoom ratio.
- Apply zoom through `CameraControl.setZoomRatio`.
- Clamp zoom at camera-reported bounds.
- Display a clear zoom indicator.

## Requirements

1. Pinch gestures must not trigger freeze/tap behavior.
2. Zoom updates must not block the main thread.
3. Values outside camera bounds are clamped.
4. The displayed zoom value reflects the applied value, not only the gesture input.
5. Zoom survives ordinary recomposition and is preserved through supported lifecycle transitions.
6. The zoom indicator remains readable over the camera image.
7. A `setZoomRatio` request superseded by a newer pinch update is treated as normal cancellation; it must not make the preview unavailable or stop later zoom updates.

## Acceptance Criteria

- Pinching inward and outward changes live zoom.
- Zoom stops cleanly at minimum and maximum values.
- A pinch never toggles another screen action.
- Zoom indicator updates accurately.
- Camera errors during zoom are handled without a crash.

## Tests and Device Validation

- Unit-test zoom clamping and ratio conversion.
- Test single tap versus multi-pointer gesture interpretation.
- Verify zoom range and responsiveness on the connected device.
- Test with large system font/display scaling.
- If the phone is locked, do not attempt touch-driven device checks; rely on build/unit/UI tests and defer live pinch validation until the user can unlock the device.

## Risks and Edge Cases

- Camera reports unusual or invalid zoom bounds.
- Camera control receives updates faster than the device can apply them.
- Gesture detector conflicts with full-screen tap handling.
- Zoom state is reset unintentionally during rebinding.

## Completion Checklist

- [x] `ZoomPolicy` implemented and unit-tested.
- [x] Compose transform detector applies multiplicative pinch zoom; no tap/freeze action is attached in Feature 03.
- [x] Camera zoom is clamped to CameraX bounds and applied asynchronously.
- [x] Zoom indicator displays CameraX-reported zoom state.
- [x] Superseded CameraX zoom requests are ignored rather than reported as camera failures.
- [x] On 2026-10-24, verified live pinch zoom in/out and confirmed pinching does not trigger freeze on the interactive SM-A566B.