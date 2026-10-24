# Contributing to SeeMoreMagnifier

Thank you for helping improve SeeMoreMagnifier. Please read the project
documentation and check existing issues before starting substantial work.

## Development environment

- JDK 17
- Android SDK platform 36
- Android Debug Bridge (`adb`) for device testing
- Task 3.x is recommended

The repository includes the Gradle wrapper. Do not commit `local.properties`,
build outputs, IDE metadata, signing files, or credentials.

## Before opening a pull request

Run the relevant checks locally:

```bash
./gradlew testDebugUnitTest lint assembleDebug --no-daemon
```

For IME or UI changes, also test on a device or emulator and verify keyboard
activation, text entry, rotation, accessibility, and settings persistence.
Do not use real personal data in screenshots or logs.

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

By contributing, you agree that your contribution may be distributed under
the GNU General Public License, version 3 or later, as described in
`LICENSE` and `NOTICE`.