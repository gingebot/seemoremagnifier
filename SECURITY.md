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

## Sensitive input and release credentials

SeeMoreMagnifier is an input method and may receive sensitive text through
Android's input-method APIs. Do not include real personal data in issues,
tests, screenshots, logs, or pull requests.

Never commit upload keystores, passwords, tokens, `.env` files, or other
signing material. Release signing is performed by the protected GitHub
Actions `release` environment.