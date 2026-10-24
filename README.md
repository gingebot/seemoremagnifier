<!-- SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# SeeMoreMagnifier

SeeMoreMagnifier is an Android magnifier that uses the rear-facing camera to
show a live, zoomable view and lets you freeze a magnified image for closer
inspection. It is built with Kotlin, Jetpack Compose, and CameraX.

> **Project status:** The repository is under active development. Installable
> builds, when published, are distributed through the
> [GitHub Releases](https://github.com/gingebot/seemoremagnifier/releases)
> page.

## Features

- Full-screen rear-camera preview with pinch-to-zoom.
- Tap to freeze an in-memory still image; if still capture fails, the app
  attempts to use its latest analysis frame.
- Pinch-to-zoom and bounded pan of a frozen image.
- Press and hold to request focus at a point before capture.
- Swipe up in live mode to toggle the camera torch when supported.
- Screen-reader state descriptions and semantic freeze/resume and camera-error
  retry actions.
- Portrait/landscape handling and camera lifecycle integration.

The frozen image is held in memory only. The app does not save or share images.
Camera access is needed for live preview and capture. See
[`docs/architecture.md`](docs/architecture.md) for implementation details and
known limitations, including accessibility announcements and operational
error feedback.

## Requirements

- Android 8.0 (API 26) or later.
- A device with a rear-facing camera for magnifier functionality.
- To build: JDK 17 and Android SDK Platform 36. The repository includes the
  Gradle wrapper; a system Gradle installation is not required.

## Build, test, and install

From the repository root:

```bash
# Build a debug APK
./gradlew assembleDebug

# Run local unit tests
./gradlew testDebugUnitTest

# Run Android lint
./gradlew lint

# Install the debug app on a connected device
./gradlew installDebug
```

The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`. For camera and Compose UI
instrumentation tests, use an emulator or an unlocked, awake, interactive
physical Android device:

```bash
./gradlew connectedDebugAndroidTest
```

Do not run connected-device tests against a locked or dozing phone. For full
setup and validation instructions, see [`docs/development.md`](docs/development.md)
and [`docs/testing.md`](docs/testing.md).

## Controls

| Gesture | Action |
|---|---|
| Pinch on live view | Zoom the camera preview |
| Tap live view | Capture and freeze the magnified view |
| Press and hold live view | Focus at the pressed point, then capture |
| Pinch frozen image | Zoom the frozen image |
| Drag zoomed frozen image | Pan within the image bounds |
| Tap frozen image | Resume live camera preview |
| Swipe up on live view | Toggle the torch, if available |

Semantic actions are provided for screen-reader users. Normal freeze/resume
changes are not currently configured as live-region announcements. The torch
control is implemented, but explicit unavailable/failure feedback is not.

## Privacy

- Android camera permission is required to start the magnifier.
- Images are processed in memory and are not saved or exported by the app.
- Camera use cases are unbound while the image is frozen. CameraX also manages
  use according to the Activity lifecycle.
- Do not share screenshots, logs, or test material containing private camera
  content.

See [`SECURITY.md`](SECURITY.md) for vulnerability reporting and credential
handling.

## Project documentation

- [Architecture and implementation notes](docs/architecture.md)
- [Development and deployment](docs/development.md)
- [Testing and device validation](docs/testing.md)
- [Release procedure](docs/releasing.md)
- [Contribution guidelines](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Third-party notices and artwork status](NOTICE)

## License

SeeMoreMagnifier first-party source code and documentation are licensed under
the GNU General Public License, version 3 or (at your option) any later
version. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE). Third-party
dependencies remain under their respective licenses.

The repository also contains AI-generated application artwork. Its
provenance is disclosed in `NOTICE`, but no copyright ownership or reuse
license is asserted for that artwork while its applicable terms are being
reviewed.
