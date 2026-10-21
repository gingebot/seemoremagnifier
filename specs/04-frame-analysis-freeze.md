# Feature 04 — Frame Analysis and Freeze

## Objective

Capture and display the most recently available camera frame when the user taps the live preview.

## Dependencies

- Feature 02 — Camera Permission and Live Preview
- Feature 03 — Live Preview Zoom
- [`../docs/architecture.md`](../docs/architecture.md), Sections 5–6 and 9

## Scope

- Bind CameraX `ImageAnalysis` alongside `Preview`.
- Use `STRATEGY_KEEP_ONLY_LATEST`.
- Implement `CameraFrameAnalyzer` on a background executor.
- Convert YUV frames to a bounded displayable frame.
- Apply rotation and preview crop rules without mirroring the rear-camera image.
- Store only the latest candidate frame in `LatestFrameStore`.
- Freeze the candidate frame on tap.
- Capture an in-memory still using CameraX's latency-oriented capture mode; retain the analysis frame as a fallback.
- By default, capture using the camera's current preview focus without requesting tap-point autofocus/metering.
- Retain the optional tap-point focus/metering path behind `FreezeCaptureOptions.ENABLE_TAP_POINT_FOCUS`; focus brackets are shown only when this option is enabled. This is an internal development switch, not a user setting; a future options menu may expose it as a persisted preference.
- Show a screen flash during capture; the current animation uses 80% peak opacity and a 450 ms fade.
- Emit debug-only freeze timing traces, filterable with `adb logcat -s FreezeTiming:D`.
- Handle missing frames and conversion/allocation errors.

## Requirements

1. Every analysis and still-capture `ImageProxy` is closed on success and failure paths.
2. Frame conversion never runs on the main thread.
3. Only one candidate frame is retained.
4. Frames are downsampled to a display-appropriate maximum size.
5. The frozen frame matches the preview orientation and mirror behavior.
6. Tapping before a frame is ready leaves the app live and reports a transient error.
7. Freeze does not persist an image to shared storage.
8. With tap-point focus disabled, still capture starts immediately using the current camera focus. When tap-point focus is enabled, capture proceeds after focus completion or timeout; focus failure does not block freezing.
9. The live preview remains visible until still capture completes; still-capture failure freezes the latest available analysis fallback.

## Acceptance Criteria

- A tap freezes a recent visible frame.
- Frozen content is displayed without active camera streaming being required by this feature.
- Repeated freeze attempts do not leak frame memory.
- Conversion and allocation failures are user-presentable and nonfatal.
- Successful still capture provides more detail than the capped analysis fallback on supported devices.
- Frozen images can be panned with one finger when magnified, without exposing blank areas or resuming the camera.
- Freeze latency is acceptable on the connected device.

## Tests and Device Validation

- Unit-test candidate-frame replacement and ownership transfer.
- Test conversion failure and no-frame behavior.
- Add instrumentation for repeated freeze/unfreeze preparation.
- Verify orientation, crop, mirroring, latency, and memory on the connected device.

## Risks and Edge Cases

- Incorrect YUV plane conversion.
- Image rotation metadata ignored or applied twice.
- Preview crop not matching analysis crop.
- Bitmap allocation pressure on low-memory devices.
- Candidate frame replaced while freeze is being requested.

## Completion Checklist

- [x] `ImageAnalysis` is bound with latest-frame backpressure.
- [x] Analyzer closes every proxy.
- [x] Frame conversion is backgrounded and bounded.
- [x] Frozen frame displays with rear-camera orientation and crop.
- [x] Candidate ownership and cleanup paths are unit-tested.
- [x] In-memory latency-oriented still capture with analysis fallback implemented.
- [x] Optional tap-point focus path retained behind an internal switch; disabled by default.
- [x] Physical-device freeze latency and repeated freeze/unfreeze cycles validated on the connected SM-A566B, including latency-mode capture with tap-point focus disabled.
- [ ] Validate focus quality and latency in low light and on additional device classes before treating the current capture settings as broadly representative.