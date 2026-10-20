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
- Apply rotation, front-camera mirroring, and preview crop rules.
- Store only the latest candidate frame.
- Freeze the candidate frame on tap.
- Handle missing frames and conversion/allocation errors.

## Requirements

1. Every `ImageProxy` is closed on success and failure paths.
2. Frame conversion never runs on the main thread.
3. Only one candidate frame is retained.
4. Frames are downsampled to a display-appropriate maximum size.
5. The frozen frame matches the preview orientation and mirror behavior.
6. Tapping before a frame is ready leaves the app live and reports a transient error.
7. Freeze does not persist an image to shared storage.

## Acceptance Criteria

- A tap freezes a recent visible frame.
- Frozen content is displayed without active camera streaming being required by this feature.
- Repeated freeze attempts do not leak frame memory.
- Conversion and allocation failures are user-presentable and nonfatal.
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

- [ ] `ImageAnalysis` is bound with latest-frame backpressure.
- [ ] Analyzer closes every proxy.
- [ ] Frame conversion is backgrounded and bounded.
- [ ] Frozen frame displays with correct orientation and crop.
- [ ] Failure paths and cleanup are tested.