# SeeMoreMagnifier — Development and Deployment Guide

This document defines the local development workflow and build/deployment policy. Runtime behavior and component design are documented in [`architecture.md`](architecture.md). Test strategy and acceptance criteria are documented in [`testing.md`](testing.md).

## 1. Build Requirements

The project must use and commit the Gradle wrapper. Builds must not depend on a globally installed Gradle version.

Required local tools:

- JDK version required by the selected Android Gradle Plugin.
- Android SDK platforms and build-tools matching the project configuration.
- Android SDK Platform-Tools.
- ADB for physical-device validation.

The following values must be recorded when the Android project is created:

- Minimum SDK.
- Compile SDK.
- Target SDK.
- Android Gradle Plugin version.
- Kotlin version.
- Jetpack Compose BOM/version.
- CameraX version.
- Application ID.

### Selected project versions

The initial Android project uses this pinned version set:

| Component | Version / value |
|---|---|
| Gradle wrapper | 8.13 |
| Android Gradle Plugin | 8.13.2 |
| JDK | 17 |
| Kotlin | 2.3.21 |
| Compose compiler Gradle plugin | `org.jetbrains.kotlin.plugin.compose`, 2.3.21 |
| Compose BOM | 2026.06.01 |
| AndroidX Activity Compose | 1.13.0 |
| AndroidX Lifecycle | 2.10.0 |
| AndroidX Lifecycle Compose | 2.10.0 |
| CameraX | 1.6.2 |
| AndroidX Core KTX | 1.17.0 |
| Coroutines Android | 1.10.2 |
| JUnit | 4.13.2 |
| AndroidX Test Runner | 1.7.0 |
| AndroidX Test Ext JUnit | 1.3.0 |
| Espresso | 3.7.0 |
| Android SDK Build Tools | 35.0.0 (AGP-selected default) |
| Compile SDK | 36 |
| Target SDK | 36 |
| Minimum SDK | 26 |
| Application ID / namespace | `com.seemoremagnifier` |

Compose library versions are controlled by the stable Compose BOM, while the Compose compiler plugin version follows Kotlin. The BOM does not configure the compiler plugin. Compose BOM 2026.06.01 selects the Compose 1.11.x generation, which is compatible with this API 36 / AGP 8.13 project; Compose 1.12 requires API 37 and AGP 9. Dependency versions are centralized in `gradle/libs.versions.toml`.

AGP 8.13.2 is chosen over the newer AGP 9 line to match the installed API 36 SDK and the conventional Android/Kotlin plugin setup. AGP 8.13 requires Gradle 8.13 and JDK 17 and supports Kotlin 2.3 with version 8.13.2. Lifecycle 2.10.0 is pinned because Lifecycle 2.11.0 requires API 37 and AGP 9.1 or later. The selected versions should be upgraded together only after validating the compatibility matrix and installed SDK requirements.

## 2. Build Commands

Run commands from the Android project root:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Release signing credentials must remain outside the repository. Build artifacts must not be committed to source control.

## 3. Local Device Deployment

Verify the device connection with:

```bash
adb devices -l
```

Install the debug build using Gradle:

```bash
./gradlew installDebug
```

When a specific APK must be installed directly:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The final package name and launcher activity must be recorded when the Android project is created. Debug APKs may be installed directly with ADB. A production distribution process is out of scope until a distribution target is selected.

## 4. Development Workflow

For every change:

1. Make the smallest implementation change that satisfies the requirement.
2. Run the build and automated checks defined in [`testing.md`](testing.md).
3. For camera, UI, lifecycle, or accessibility changes, install the debug build on a physical device.
4. Record any device-specific findings or limitations in the relevant issue or test report.

The connected physical device is particularly important for rear-camera behavior, aspect ratio, gesture interaction, lifecycle transitions, and accessibility behavior.

## 5. Release Policy

- Release builds must be produced with `assembleRelease`.
- Release signing credentials must not be committed.
- Debug and release artifacts must remain outside source control.
- A release is not considered ready until the release checks in [`testing.md`](testing.md) pass.
- The distribution target and signing process must be documented before the first public release.