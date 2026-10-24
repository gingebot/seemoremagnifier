# SeeMoreMagnifier — Release Instructions

This runbook is for engineers preparing and publishing a production release. Releases are built, signed, and published only by GitHub Actions. Do not build, sign, or publish a local release APK or AAB.

## Release overview

Pushing a `v<versionName>` tag starts [the release workflow](../.github/workflows/release.yml). The workflow validates the tag against the version in `gradle.properties`, then runs unit tests, lint, and a debug build. If those checks pass, the workflow waits for approval of the protected GitHub `release` environment. After approval, it uses the environment's signing secrets to build and verify the signed release APK and AAB, generates checksums, and publishes a GitHub Release.

The hosted workflow has no camera hardware. Complete the required connected instrumented tests and physical-device checks before creating a release tag; the environment approval is a final publication gate, not a substitute for those checks.

## 1. Complete pre-release validation

From the project root, run the device-independent checks:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Run the connected instrumented suite:

```bash
./gradlew connectedDebugAndroidTest --no-daemon
```

Before running this command against a physical phone, ask the user/operator to confirm that it is unlocked, awake, and interactive. ADB connectivity alone is not sufficient. If the phone is not confirmed interactive, defer the device run; do not try to wake or unlock it. Emulator runs do not require phone confirmation. Follow [`testing.md`](testing.md) and complete the relevant physical-device and accessibility checks. Record results, device details, performance observations, and any documented exceptions with the release review.

Do not tag a release while required unit, lint, instrumented, physical-device, accessibility, or device-coverage checks are failing or incomplete.

## 2. Set the release version

On the release commit, update both properties in [`gradle.properties`](../gradle.properties):

```properties
app.versionName=1.1
app.versionCode=2
```

Use the intended user-facing version for `app.versionName` and increment `app.versionCode` for every release. The workflow requires a positive integer version code and a tag exactly matching `v<versionName>` (for example, `v1.1`). Confirm the version change is committed and present on the intended release branch before tagging.

## 3. Create and push the release tag

After the pre-release checks pass and the version change is committed, create and push the matching tag:

```bash
git switch main
git pull --ff-only
git tag -a v1.1 -m "SeeMoreMagnifier 1.1"
git push origin v1.1
```

Replace `1.1` in both commands with the committed `app.versionName`. The tag must point to the commit containing the version change and validated source. Do not reuse or move a tag or version code that has already been published.

## 4. Monitor checks and approve publication

Open the repository's **Actions** tab and select the workflow run for the pushed tag.

1. The `checks` job validates the tag/version and runs `testDebugUnitTest`, `lint`, and `assembleDebug`.
2. If `checks` passes, the `release` job waits at the GitHub `release` environment. Review the CI result and pre-release device-validation evidence before approving the environment deployment.
3. The protected job accesses signing credentials, builds the release APK/AAB, verifies their signatures, creates checksums, and publishes the GitHub Release.

The `release` environment must have the signing secrets configured with these exact names:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Configure required reviewers on that environment so signing and publication cannot proceed without review. Never put secret values in source, command-line arguments, logs, release notes, or this runbook. The workflow writes the decoded keystore to the runner's temporary directory and removes it in its cleanup step.

## 5. Verify and announce the release

When the workflow succeeds, open the GitHub Release for the tag and confirm it contains:

- `SeeMoreMagnifier-<version>-release.apk`
- `SeeMoreMagnifier-<version>-release.aab`
- `SHA256SUMS`

The workflow verifies the APK with Android's `apksigner` and the AAB with `jarsigner` before publishing. After downloading the artifacts and `SHA256SUMS`, verify the downloaded files from their containing directory:

```bash
sha256sum -c SHA256SUMS
```

Only announce distribution once the release job completed successfully and the downloaded checksums match.

## Troubleshooting

- **Tag/version validation fails:** compare the pushed tag with `app.versionName` on the tagged commit. The tag must be exactly `v` followed by that value.
- **Version-code validation fails:** set `app.versionCode` to a positive integer, commit the change, and use a new version/tag.
- **Automated checks fail:** inspect the failed step's Actions log. Fix the source, rerun relevant checks, and release from a new validated commit/tag. A transient workflow failure may be rerun from the Actions run page.
- **The release job cannot access signing material:** check that all four named secrets are configured in the GitHub `release` environment and that the approver approved the correct environment deployment. Do not print or copy secret values into logs when diagnosing this.
- **Artifact signature verification or publication fails:** do not distribute any partial or unverified artifacts. Inspect the failing Actions step, resolve the issue, and use a new release version/tag if the source must change. Never replace artifacts under an already published version.

For each release, retain the Actions run URL, tag, validation results, device-check evidence, and any known limitations with the project release record.