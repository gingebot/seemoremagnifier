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

## Risks and Edge Cases

- Camera reports unusual or invalid zoom bounds.
- Camera control receives updates faster than the device can apply them.
- Gesture detector conflicts with full-screen tap handling.
- Zoom state is reset unintentionally during rebinding.

## Completion Checklist

- [ ] `ZoomPolicy` implemented and unit-tested.
- [ ] Pinch detector emits semantic zoom events.
- [ ] Camera zoom is clamped and applied.
- [ ] Zoom indicator implemented.
- [ ] Physical-device pinch validation completed.