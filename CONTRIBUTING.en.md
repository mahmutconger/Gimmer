# Contributing to Gimmer

[Türkçe](CONTRIBUTING.md) · **English**

See the [README](README.en.md) for setup and development prerequisites.

## Before you start

1. When reporting a bug, include steps to reproduce it, the expected and actual behavior, your Android version, and the device model. Discuss the scope of larger changes in a GitHub issue.
2. Branch from the latest `main`. Use a descriptive branch name such as `fix/set-logging`, `feat/routine-import`, or `docs/setup`.
3. Keep each PR focused on one topic. Use short commit messages that describe the change.

## Code and verification

- Write user-facing text in Turkish. Use English for Kotlin class, function, and variable names.
- Reuse the existing Material 3 components and theme colors for UI changes. Try the change on a device and include a screenshot in the PR.
- If the Room schema changes, provide a migration that preserves existing records and test the upgrade from an older database. If a backup or JSON format changes, verify that the app can still read older files.
- Use sample records for screenshots and tests. Keep account details, keys, access tokens, and local settings out of the PR.

For code changes, build the app and run the unit tests.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Run instrumented tests on a connected device or emulator when changing Room migrations, backups, or device behavior.

```bash
./gradlew :app:connectedDebugAndroidTest
```

For documentation changes, review the wording, formatting, and links.

## Opening and reviewing a PR

- Target `main`. Explain what changed and why. If the PR closes a GitHub issue, link it using `Fixes #123`.
- List the tests you ran and their results. Explain any relevant checks you could not run.
- Address review feedback before merging and make sure the relevant checks are complete. [Pull request template](.github/pull_request_template.md)
