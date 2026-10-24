<!-- SPDX-FileCopyrightText: 2026 SeeMoreMagnifier contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Security Policy

## Supported versions

Security fixes are applied to the latest version on the `main` branch and
to the latest published release when practical.

## Reporting a vulnerability

Please do not report security vulnerabilities in a public issue, pull
request, or discussion. Use GitHub's private vulnerability reporting for
this repository when it is enabled. Otherwise, contact the repository
maintainer privately through the contact method listed on the GitHub
profile.

Include, when safe to share:

- A description of the issue and its potential impact
- Steps to reproduce or a minimal proof of concept
- Affected version, commit, or release
- Any suggested mitigation

Please allow time for investigation and coordinated disclosure before
publishing details.

## Camera and user privacy

SeeMoreMagnifier requests camera access to provide its live magnifier and
freeze capture. The app is intended to keep captured frames in memory and does
not save or export images. Do not include private camera content, identifiable
people, location details, or other personal data in public issues, tests,
screenshots, logs, or pull requests. Report any suspected unauthorized camera
access, image retention, or disclosure privately using the reporting process
above.

## Release credentials

Never commit upload keystores, passwords, tokens, `.env` files, or other
signing material. Release signing is performed by the protected GitHub
Actions `release` environment.
