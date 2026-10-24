<!-- SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Code quality and security scanning

The repository uses GitHub Actions for Android Lint and build verification,
CodeQL analysis of Kotlin/Java, pull-request dependency review, and Gitleaks
secret scanning. Dependabot proposes Gradle and GitHub Actions updates. These
checks complement one another; passing a scan is not a guarantee that code is
free of security issues.

## Repository setup

Complete these steps in GitHub after the workflows have been pushed to the
default branch:

1. **Confirm Actions are enabled.** In the repository, open **Settings →
   Actions → General**. Allow the repository's workflows and the listed
   GitHub-maintained and Gitleaks actions to run. Keep the default
   `GITHUB_TOKEN` permissions read-only; the workflows declare their own
   minimum permissions.
2. **Enable dependency visibility and alerts.** Open **Settings → Advanced
   Security** (called **Code security and analysis** in some GitHub UI
   versions). Enable the dependency graph and Dependabot alerts. Enable
   Dependabot security updates as well, if offered. The dependency graph is
   needed for the pull-request Dependency Review check.
3. **Use the committed CodeQL workflow.** It analyzes `java-kotlin` with the
   autobuild mode, on `main` pushes, pull requests targeting `main`, and a
   weekly schedule. In **Settings → Advanced Security → Code scanning**, use
   the workflow/advanced setup rather than also enabling a conflicting default
   setup. Code scanning is free for public repositories; private-repository
   availability depends on the GitHub plan.
4. **Configure Gitleaks only if required by the account type.** Gitleaks Action
   v3 requires the `GITLEAKS_LICENSE` secret for organization-owned
   repositories; the action's documentation says it is not required for
   personal-account repositories. For an organization, obtain the free
   organization license from [Gitleaks](https://gitleaks.io/), then add it at
   **Settings → Secrets and variables → Actions → New repository secret** with
   the exact name `GITLEAKS_LICENSE`. Never put the license value in a workflow
   file or commit it. The workflow disables PR comments and skips fork and
   Dependabot pull requests because those runs cannot reliably receive
   repository secrets; those changes are scanned on `main` after merge.
5. **Review the first results.** Open **Actions** and review runs named
   **CodeQL**, **Dependency Review**, and **Secret Scan**. Existing dependency
   advisories are handled by Dependabot alerts; Dependency Review blocks new or
   updated dependencies with high or critical severity. Review the alert,
   upgrade, or mitigation rather than dismissing it solely to make CI green.
6. **Optionally require checks before merging.** After each workflow has
   completed at least once, go to **Settings → Rules → Rulesets** (or branch
   protection for `main`), require pull requests and passing status checks,
   then select the exact check names shown by GitHub Actions. Include the
   existing **CI / Verify** check and the CodeQL, Dependency Review, and Secret
   Scan checks as appropriate. Gitleaks is not run on fork-originated or
   Dependabot pull requests, so do not make its check a merge requirement for
   those PRs unless you change that trust/secret model.

## Responding to a possible secret leak

Treat a detected credential as exposed even if it was removed in a later
commit. Revoke or rotate the credential first, inspect its use, then remove it
from the working tree and—when appropriate—coordinate history cleanup. Do not
paste the secret into an issue or pull request. See [`../SECURITY.md`](../SECURITY.md)
for private vulnerability reporting.

## Local checks

Run the project's existing device-independent quality checks from the root:

```bash
./gradlew testDebugUnitTest lint assembleDebug --no-daemon
```

Connected instrumented tests are separate and require an emulator or a
confirmed unlocked, awake, interactive physical device; see
[`testing.md`](testing.md).