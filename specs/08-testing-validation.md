# Feature 08 — Testing, Hardening, and Release Validation

## Objective

Complete automated coverage, physical-device validation, performance checks, and release gates for the v1 magnifier.

## Dependencies

- Features 01–07
- [`../docs/architecture.md`](../docs/architecture.md)
- [`../docs/development.md`](../docs/development.md)
- [`../docs/testing.md`](../docs/testing.md)

## Scope

- Complete unit and state-machine coverage.
- Complete Compose/UI and instrumented coverage.
- Run lint and debug/release builds.
- Validate on the connected physical device.
- Validate required device and lifecycle scenarios.
- Measure startup, freeze latency, memory, and battery behavior.
- Record known limitations and release readiness.

## Requirements

- Debug build, unit tests, lint, and instrumented tests must pass.
- Release builds must succeed without repository-stored signing secrets.
- Camera, lifecycle, gesture, accessibility, and cleanup behavior must be validated on a physical device.
- Device-specific limitations must be documented before release.

## Tests and Device Validation

Run from the Android project root:

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
./gradlew installDebug
```

Verify the connected device with:

```bash
adb devices -l
```

The connected device must be verified with:

```bash
adb devices -l
```

## Acceptance Criteria

- Debug build, unit tests, lint, and instrumented tests pass.
- Release build succeeds without repository-stored signing secrets.
- Permission, preview, zoom, freeze, unfreeze, lifecycle, and error flows pass.
- Pinch never triggers freeze/resume.
- No `ImageProxy` remains unclosed on tested analyzer paths.
- Repeated freeze/unfreeze does not show progressive memory growth.
- TalkBack and large-display acceptance checks pass.
- Device-specific limitations are documented before release.

## Device Coverage

At minimum, validate on the connected physical device and, before release where available:

- One low-end phone.
- One current phone.
- One tablet or large display.
- A device or emulator with camera access unavailable.

Include portrait/landscape, background/foreground, low-memory, different aspect ratio, and process-recreation scenarios.

## Performance Checks

- Measure cold startup to visible preview.
- Measure tap-to-frozen-frame latency.
- Observe memory during sustained preview and repeated freeze/unfreeze.
- Confirm camera and analysis stop while frozen.
- Confirm no excessive main-thread work during analysis or zoom.
- Check battery behavior during a representative live session.

## Risks and Edge Cases

- Tests passing on an emulator but failing on physical camera hardware.
- Camera behavior varying by device vendor or aspect ratio.
- Flaky permission or lifecycle instrumentation tests.
- Release-only configuration or shrinking failures.
- Accessibility regressions introduced by final UI changes.

## Completion Checklist

- [ ] Automated checks pass.
- [ ] Connected-device checks pass.
- [ ] Required device coverage completed or exceptions documented.
- [ ] Performance observations recorded.
- [ ] Release build validated.
- [ ] Known limitations and follow-up work documented.