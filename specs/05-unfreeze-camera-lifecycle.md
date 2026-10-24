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

1. The frozen frame remains visible during unfreeze and until the preview reports its stream is `STREAMING`.
2. The app does not claim to be live until camera binding succeeds.
3. Camera use cases are stopped while frozen.
4. The frozen frame is released exactly once after a successful unfreeze or when its owner is destroyed.
5. Unfreeze never accesses the frozen image through the camera.
6. A rebind failure retains the frozen frame and exposes a retry action.
7. Process recreation does not attempt to restore an in-memory frozen bitmap.
8. Camera resources are released when the screen is no longer visible.

## Acceptance Criteria

- Freezing stops camera access while preserving the frozen frame display.
- Unfreezing rebinds camera use cases and only releases the frozen frame after preview streaming starts, preventing a blank transition.
- Rebind failure retains the frozen frame, presents a retry action, and does not show a false live state.
- Frozen mode exposes an explicit accessible resume button and does not display the live zoom badge over the frozen status.
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

- [x] Frozen-state camera unbinding implemented.
- [x] Unfreeze rebind implemented; frozen image remains visible until binding succeeds.
- [x] Frame and camera cleanup is centralized and idempotent.
- [x] Retryable rebind error implemented without claiming live state.
- [x] On 2026-10-24, verified camera/preview shutdown while frozen, successful resume/rebind, background/foreground in live and frozen modes, and about 10 freeze/resume cycles on the SM-A566B. The user reported all checks passed.