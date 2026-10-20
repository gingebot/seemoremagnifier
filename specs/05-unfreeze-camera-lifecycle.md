# Feature 05 — Unfreeze and Camera Lifecycle

## Objective

Resume the live camera safely after a frozen frame, while releasing camera and image resources when they are not needed.

## Dependencies

- Feature 04 — Frame Analysis and Freeze
- [`../docs/architecture.md`](../docs/architecture.md), Sections 5, 6, and 9

## Scope

- Add the `Frozen` and rebind transitions to the ViewModel state machine.
- Unbind `Preview` and `ImageAnalysis` while frozen.
- Release the frozen frame on unfreeze, stop, error, and replacement.
- Rebind camera use cases on unfreeze.
- Preserve logical zoom through freeze/unfreeze.
- Handle rebind failure with an explicit retryable error.
- Handle backgrounding and process recreation according to the architecture.

## Requirements

1. The app does not claim to be live until camera binding succeeds.
2. Camera use cases are stopped while frozen.
3. The frozen frame is released exactly once when ownership ends.
4. Unfreeze never accesses the frozen image through the camera.
5. A rebind failure does not discard useful error information.
6. Process recreation does not attempt to restore an in-memory frozen bitmap.
7. Camera resources are released when the screen is no longer visible.

## Acceptance Criteria

- Freezing stops camera access.
- Unfreezing releases the frozen frame and restores live preview.
- Rebind failure presents a retry action and does not show a false live state.
- Repeated freeze/unfreeze cycles do not accumulate bitmaps or camera bindings.
- Backgrounding and foregrounding are safe in both live and frozen modes.

## Tests and Device Validation

- Unit-test all state transitions and cleanup paths.
- Test simulated bind and rebind failures.
- Exercise repeated cycles on the connected device.
- Verify camera privacy indicator and resource shutdown while frozen.
- Verify rotation and app backgrounding in both states.

## Risks and Edge Cases

- Rebinding while a lifecycle stop is in progress.
- Releasing a frame while the UI is still drawing it.
- Camera provider invalidation after another app uses the camera.
- Zoom bounds changing after camera rebind.
- Device process death while frozen.

## Completion Checklist

- [ ] Frozen-state camera unbinding implemented.
- [ ] Unfreeze rebind implemented.
- [ ] Frame and camera cleanup is centralized and idempotent.
- [ ] Retryable rebind error implemented.
- [ ] Lifecycle and repeated-cycle validation completed.