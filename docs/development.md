<!-- SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
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

The current Android project uses this pinned version set:

| Component | Version / value |
|---|---|
| Gradle wrapper | 9.8.2 |
| Android Gradle Plugin | 9.5.0 |
| JDK | 17 |
| Kotlin | 2.3.21 |
| Compose compiler Gradle plugin | `org.jetbrains.kotlin.plugin.compose`, 2.3.21 |
| Compose BOM | 2026.06.01 |
| AndroidX Activity Compose | 1.13.0 |
| AndroidX Lifecycle / Lifecycle Compose | 2.10.0 |
| CameraX | 1.6.2 |
| AndroidX Core KTX | 1.19.1 |
| Coroutines Android | 1.11.0 |
| JUnit | 4.13.2 |
| AndroidX Test Runner | 1.7.0 |
| AndroidX Test Ext JUnit | 1.3.0 |
| Espresso | 3.7.0 |
| Android SDK Build Tools | 36.0.0 (AGP-selected default) |
| Compile SDK | 37 |
| Target SDK | 37 |
| Minimum SDK | 26 |
| Application ID / namespace | `com.seemoremagnifier` |

Compose library versions are controlled by the stable Compose BOM, while the Compose compiler plugin version follows Kotlin. The BOM does not configure the compiler plugin. Compose BOM 2026.06.01 selects the Compose 1.11.x generation. Dependency versions are centralized in `gradle/libs.versions.toml`.

The project currently uses AGP 9.5.0 with Gradle 9.8.2 and JDK 17. API 37 is the compile and target SDK; minimum SDK remains 26. Targeting API 37 opts the app into Android 17 target-SDK behavior changes, including the removal of opt-outs from large-screen orientation and resizability handling. Test layouts on large-screen devices when changing target SDK. Keep the selected versions aligned and validate the toolchain compatibility matrix when upgrading dependencies.

## 2. Build Commands

Run commands from the Android project root:

```bash
./gradlew assembleDebug
```

Do not create or sign local release artifacts. Releases are built, signed, and published by GitHub Actions when a `v<versionName>` tag is pushed. The workflow uses signing credentials from the GitHub `release` environment; credentials and build artifacts must not be committed to source control.

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

The final package name and launcher activity must be recorded when the Android project is created. Debug APKs may be installed directly with ADB. The production distribution target is GitHub Releases; signed APK and AAB artifacts plus checksums are published by the release workflow.

## 4. Development Workflow

For every change:

1. Make the smallest implementation change that satisfies the requirement.
2. Run the build and automated checks defined in [`testing.md`](testing.md).
3. For camera, UI, lifecycle, or accessibility changes, install the debug build on a physical device.
4. Record any device-specific findings or limitations in the relevant issue or test report.

The connected physical device is particularly important for rear-camera behavior, aspect ratio, gesture interaction, lifecycle transitions, and accessibility behavior.

Do not unlock a connected device or send touch input as part of automated validation. If the device is locked, keep validation non-interactive and defer manual camera/gesture checks.

## 5. Release Policy

- Releases are initiated only by pushing a `v<versionName>` tag and are built/published by [`.github/workflows/release.yml`](../.github/workflows/release.yml).
- The workflow validates the tag against `app.versionName` and `app.versionCode` in `gradle.properties`.
- The workflow signs release APK/AAB files using the GitHub `release` environment secrets, verifies signatures, generates checksums, and publishes a GitHub Release.
- Instrumented and physical-device acceptance checks must be completed before creating the tag. The release workflow runs its automated checks before entering the protected GitHub `release` environment; configure required reviewers so approval is required before it accesses signing secrets, signs artifacts, or publishes. The hosted workflow has no connected camera device.
- Release signing credentials must not be committed or used for local release builds.
- Debug and release artifacts must remain outside source control.
- A release is not considered ready until the release checks in [`testing.md`](testing.md) pass.

For the engineer release procedure, see [`releasing.md`](releasing.md).