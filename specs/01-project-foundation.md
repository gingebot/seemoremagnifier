# Feature 01 — Android Project Foundation

## Objective

Create the minimal Android application foundation so the project builds, tests, lints, installs, and launches on the connected device.

## Dependencies

- Architecture: [`../docs/architecture.md`](../docs/architecture.md)
- Development workflow: [`../docs/development.md`](../docs/development.md)
- Testing workflow: [`../docs/testing.md`](../docs/testing.md)

## Scope

- Create the Android Gradle project and commit the Gradle wrapper.
- Configure Kotlin, Jetpack Compose, AndroidX Lifecycle/ViewModel, and CameraX dependencies.
- Define the application ID, SDK versions, namespace, and Java/Kotlin compatibility.
- Add a launcher activity and minimal Compose theme/screen.
- Add debug and release build types without committing signing credentials.
- Configure unit-test, Android instrumented-test, and lint support.

## Requirements

1. The project builds with `./gradlew`, not a globally installed Gradle.
2. The project contains a single application module unless implementation needs justify more.
3. The launcher starts without camera access and displays a minimal placeholder state.
4. Dependency versions are centralized and recorded in the development guide.
5. No camera permission is requested by the foundation-only implementation.
6. Build artifacts and local configuration files are excluded from source control.

## Acceptance Criteria

- `./gradlew assembleDebug` succeeds.
- `./gradlew test` succeeds.
- `./gradlew lint` succeeds.
- The debug APK installs with `./gradlew installDebug`.
- The app launches on the connected device.
- The application ID and SDK/tool versions are documented.

## Tests and Device Validation

- Add a smoke test for the launcher content.
- Confirm cold launch and process restart on the physical device.
- Confirm the app can be uninstalled and reinstalled cleanly.

## Risks and Edge Cases

- Incompatible AGP, Gradle, JDK, or SDK versions.
- Missing SDK components on a clean machine.
- Incorrect namespace/application ID.
- Release configuration accidentally requiring local signing secrets.

## Completion Checklist

- [ ] Android project and Gradle wrapper created.
- [ ] Versions and application ID recorded.
- [ ] Debug build, unit tests, and lint pass.
- [ ] Debug APK installed and launched on the connected device.
- [ ] No generated artifacts or secrets committed.