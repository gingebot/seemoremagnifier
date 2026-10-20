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

The connected physical device is particularly important for camera behavior, front-camera mirroring, aspect ratio, gesture interaction, lifecycle transitions, and accessibility behavior.

## 5. Release Policy

- Release builds must be produced with `assembleRelease`.
- Release signing credentials must not be committed.
- Debug and release artifacts must remain outside source control.
- A release is not considered ready until the release checks in [`testing.md`](testing.md) pass.
- The distribution target and signing process must be documented before the first public release.