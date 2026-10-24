# Feature 08 — Testing, Hardening, and Release Validation

## Objective

Complete automated coverage, physical-device validation, performance checks, and release gates for the v1 magnifier.

## Dependencies

- Features 01–07
- [`../docs/architecture.md`](../docs/architecture.md)
- [`../docs/development.md`](../docs/development.md)
- [`../docs/testing.md`](../docs/testing.md)
- [`../docs/releasing.md`](../docs/releasing.md)

## Scope

- Complete unit and state-machine coverage.
- Complete Compose/UI and instrumented coverage.
- Run automated checks on each change and signed release builds in GitHub Actions.
- Run physical-device acceptance checks manually before creating a release tag.
- Validate required device and lifecycle scenarios.
- Measure startup, freeze latency, memory, and battery behavior.
- Record known limitations and release readiness.

## Requirements

- Debug build, unit tests, lint, and instrumented tests must pass.
- Releases are built and published only by GitHub Actions after a `v<versionName>` tag is pushed.
- The release workflow signs artifacts with credentials stored in the GitHub `release` environment; signing material must never be committed.
- Required instrumented and physical-device validation must be completed before tagging. GitHub Actions runs the automated release checks before the `release` environment approval gate; configure required reviewers there so signing and publication happen only after approval.
- Camera, lifecycle, gesture, accessibility, and cleanup behavior must be validated on a physical device.
- Device-specific limitations must be documented before release.

## Tests and Device Validation

Run from the Android project root:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
./gradlew installDebug
```

Before running connected tests against a physical phone, obtain explicit user confirmation that it is unlocked, awake, and interactive. ADB visibility alone is not sufficient; a locked/dozing phone can cause Compose instrumentation tests to fail because no Compose hierarchy is available. If confirmation is not available, defer physical-device tests and continue with checks that do not use the phone. Do not attempt to wake or unlock it. Emulator testing does not need this confirmation.

Do not create or sign local release artifacts. Pushing a `v<versionName>` tag starts [`.github/workflows/release.yml`](../.github/workflows/release.yml), which validates the tag/version and runs unit tests, lint, and a debug build before waiting for the protected `release` environment approval. After approval, it signs the release APK/AAB using GitHub environment secrets, verifies both signatures, and publishes the artifacts with checksums to a GitHub Release. The regular CI workflow runs unit tests, lint, and a debug build; neither hosted workflow has a physical camera device. Run connected instrumented tests and complete the device checks below before creating a tag.

After the user confirms the physical device is interactive, verify its connection with:

```bash
adb devices -l
```

## Acceptance Criteria

- Debug build, unit tests, lint, and instrumented tests pass.
- GitHub Actions produces and verifies signed release artifacts using GitHub `release` environment secrets.
- Instrumented and physical-device acceptance checks are complete before tag creation; protected `release` environment approval is required before signing and publication.
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