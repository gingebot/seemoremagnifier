<!-- SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Feature 02 — Camera Permission and Live Preview

## Objective

Request camera permission and display a lifecycle-aware full-screen rear-camera preview.

## Dependencies

- Feature 01 — Android Project Foundation
- [`../docs/architecture.md`](../docs/architecture.md), Sections 3–6 and 9

## Scope

- Implement camera permission state and request flow.
- Add `CameraController` around `ProcessCameraProvider`.
- Bind CameraX `Preview` using `DEFAULT_BACK_CAMERA`.
- Host the preview through `PreviewView` in Compose.
- Start and stop camera use cases with screen/activity lifecycle.
- Add initialization, unavailable-camera, permission-denied, and retry UI states.

## Requirements

1. CameraX is not initialized before permission is granted.
2. Permission denial shows an explanation and retry action.
3. Permanent denial provides an application-settings shortcut.
4. Preview binds only while the screen is started and visible.
5. The preview uses an explicitly documented scale type and target rotation.
6. A device without a rear camera produces a user-presentable error.
7. Camera failures do not crash the application.

## Acceptance Criteria

- First launch requests camera permission.
- Granting permission shows the rear-camera preview.
- Denying permission leaves the app in a useful permission state.
- Returning from settings re-evaluates permission.
- Backgrounding stops camera use; foregrounding restores it.
- Camera initialization failures expose retry behavior.

## Tests and Device Validation

- Unit-test permission and camera state transitions.
- Add instrumented permission-flow coverage where practical.
- Verify preview startup, backgrounding, foregrounding, and rotation on the connected device.
- Confirm the rear-camera preview and frozen image are not mirrored.

## Risks and Edge Cases

- Permission changes while the app is stopped.
- Camera in use by another application.
- Camera provider failure or missing rear camera.
- Preview crop differing across device aspect ratios.
- Repeated lifecycle start/stop causing duplicate bindings.

## Completion Checklist

- [x] Permission flow implemented, including denial rationale and settings shortcut.
- [x] `CameraPreviewController` binds and unbinds `Preview` safely.
- [x] Rear-camera preview observed on the connected device; CameraService confirms `Camera ID: 0` is open for `com.seemoremagnifier`.
- [x] Error and retry states implemented.
- [x] On 2026-10-24, verified live preview recovery after background/foreground transitions and portrait/landscape rotation on the SM-A566B.