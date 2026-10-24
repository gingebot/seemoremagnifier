<!-- SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Contributing to SeeMoreMagnifier

Thank you for helping improve SeeMoreMagnifier. Please read the project
documentation and check existing issues before starting substantial work.

## Development environment

- JDK 17
- Android SDK platform 36
- Android Debug Bridge (`adb`) for device testing
- Android Studio is optional; the Gradle wrapper is sufficient for CLI builds.

The repository includes the Gradle wrapper. Do not commit `local.properties`,
build outputs, IDE metadata, signing files, or credentials.

## Before opening a pull request

Run the relevant checks locally:

```bash
./gradlew testDebugUnitTest lint assembleDebug --no-daemon
```

For camera, gesture, lifecycle, or accessibility changes, also test on an
emulator or physical Android device where practical. Before using a connected
physical phone, confirm that it is unlocked, awake, and interactive. Do not
include private camera content in screenshots, logs, or test reports.

## Pull requests

Pull requests should:

- Explain the problem and the proposed change.
- Include tests for behavior changes where practical.
- Include updated documentation or screenshots for user-visible changes.
- Avoid unrelated formatting or generated-file changes.
- Confirm that no secrets or signing material are included.

Release builds and publishing are performed by GitHub Actions; contributors
should not add local signing configuration to the repository.

## License

By submitting a contribution, you agree that it may be distributed under the
GNU General Public License, version 3 or (at your option) any later version,
as described in `LICENSE` and `NOTICE`. Please do not submit material you do
not have the right to license under those terms. If a contribution includes
third-party code, assets, or generated material, identify its source and
license in the pull request and obtain maintainer review before submission.
