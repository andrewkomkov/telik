# Telik Constitution

## Core Principles

### I. Everything Runs on the TV
Telik runs on the Android TV only. The user installs nothing else: not on the iPhone, not on a server.
A feature that needs an extra device is out of scope.

### II. No Protection Bypass
Telik respects every content protection. A feature that decrypts protected video or bypasses DRM is rejected.

### III. Privacy
Telik sends no analytics and asks for no account. Logs hold names and numbers only.

### IV. Current Tools
Use current stable libraries and tool versions. Replace an old library with a current one.
Pin every dependency and every CI action to an exact version.

### V. Test and Verify on the TV
Logic that does not need a TV has a unit test. UI and device behavior is checked on a real TV.
A task is done only when its code exists and was checked.

### VI. Do Not Disturb Playback
A test or an install leaves the video that the user watches running.

## Release Rules
- Commits follow Conventional Commits. Pull requests are small and stacked, and they merge bottom-up.
- A release is a tag `vX.Y.Z`. CI builds and signs it with the release key.
- Each user-visible change has a changelog file in `fastlane/metadata/android/` for English and Russian.

## Governance
This constitution wins over other practice in this repository.
A change to it needs a pull request that states the reason.

**Version**: 1.0.0 | **Ratified**: 2026-11-02 | **Last Amended**: 2026-11-02
