# SeeMoreMagnifier — Testing and Validation Guide

This document defines automated tests, physical-device checks, and release validation. Runtime requirements are documented in [`architecture.md`](architecture.md), and build/deployment workflow is documented in [`development.md`](development.md).

For the engineer runbook to prepare, tag, approve, and verify a GHA release, see [`releasing.md`](releasing.md).

## 1. Automated Checks

Run the following from the Android project root:

```bash
./gradlew test
./gradlew lint
```

Unit tests and lint should run for every change.

### 1.1 Unit tests

Cover at least:

- Zoom clamping and zoom-state preservation.
- Valid and invalid state transitions.
- Tap-versus-pinch gesture interpretation.
- Error mapping and retry behavior.
- Frozen-frame ownership and cleanup.

### 1.2 Instrumented/UI tests

Run on an available emulator or physical Android device:

```bash
./gradlew connectedDebugAndroidTest
```

Before running this command against a connected physical phone, ask the user to confirm that the phone is unlocked, awake, and interactive. ADB connectivity alone does not confirm readiness: instrumented Compose tests can fail with missing Compose hierarchies when the phone is locked or dozing. If the user cannot confirm, defer physical-device tests and continue with device-independent checks; do not attempt to wake or unlock the phone. This confirmation is not required when tests run on an emulator.

Cover at least:

- Permission granted, denied, and permanently denied flows.
- Live preview appears after successful binding.
- Tap freezes and tap resumes.
- Pinch zoom works in live and frozen modes.
- Pinch does not trigger freeze.
- Camera rebind failure is presented correctly.
- TalkBack semantics expose the correct state, freeze/resume action, and camera-error retry action.
- Large font and display scaling do not hide essential controls.

## 2. Physical-Device Acceptance Checks

On a representative physical device, verify:

- Camera permission grant, denial, and permanent denial behavior.
- Rear-camera preview startup and confirmation that neither live nor frozen content is mirrored.
- Pinch zoom in live and frozen modes.
- Tap-to-freeze and tap-to-unfreeze.
- Camera shutdown while frozen and camera rebind after unfreezing.
- Background/foreground transitions.
- Portrait and landscape rotation.
- Freeze latency, memory use, and behavior under low-memory conditions.
- TalkBack semantics and custom freeze/unfreeze action.
- Large font and display scaling.
- Behavior when camera access is unavailable.

Camera, gesture, lifecycle, and accessibility behavior must be checked on the connected physical device because these behaviors cannot be fully validated with unit tests or a generic emulator.

Before running connected tests or touch-driven device checks, get explicit user confirmation that the physical device is unlocked, awake, and interactive. If confirmation is unavailable or the device is locked/dozing, defer those checks; do not attempt to wake it, unlock it, or inject gestures. Continue with build, unit, and lint checks that do not require the device. Emulator tests remain available without phone confirmation.

## 3. Device Coverage

Before release, test on at least:

- One low-end phone.
- One current phone.
- One tablet or large display, if tablet support remains in scope.

The coverage should include:

- Portrait and landscape rotation.
- Different rear-camera aspect ratios.
- App background/foreground transitions.
- Low-memory conditions.
- A device or emulator with camera access unavailable.

## 4. Validation Gates

### Every change

- `assembleDebug` succeeds.
- Unit tests pass.
- Lint passes.

### Camera, UI, lifecycle, or accessibility changes

- Instrumented tests pass.
- Debug build is installed on a physical device.
- Relevant physical-device acceptance checks pass.

### Before release

- Before creating a `v<versionName>` tag, instrumented tests and manual physical-device/device-coverage checks are complete.
- The tag triggers GitHub Actions to validate the version and run unit tests, lint, and a debug build before the protected release approval gate.
- Required reviewers approve the protected GitHub `release` environment before signing credentials are accessed and the release APK/AAB are built, signature-verified, and published.
- Full physical-device acceptance checklist passes.
- Required device coverage is complete.
- No release artifact or signing credential is committed.