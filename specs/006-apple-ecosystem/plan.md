# Plan: Apple ecosystem

**Spec**: `spec.md` | **Branch**: `feat/apple-ecosystem`, stacked on `feat/launcher-v3`

## Technical context

- Kotlin 2, Jetpack Compose, minSdk 33, targetSdk 37. The test TV has Android 14 (API 34), armeabi-v7a, 1.8 GB RAM.
- The iPhone remote is the Rust crate `remote/crates/atv-core` (Companion protocol, OPACK). `atv-android` is the JNI
  bridge, and `RemoteNative.kt` is the Kotlin side. Build: `scripts/build-remote.sh` with Rust 1.98.0 and NDK 27.
- AirPlay is UxPlay in `app/src/main/cpp`. HomeKit is `dev/tvhomekit` in Kotlin.
- Local check: `/Volumes/SanDisk2Tb/builds/telik/devbuild.sh` installs `dev.telik.tv.dev` next to the real Telik.
  The remote and AirPlay ports belong to the real Telik, so remote and AirPlay tests need a signed CI build.

## Design

### US1, US2: data from Kotlin to the remote sessions

Kotlin pushes state into the native side, as `nativeSetTextFocus` does now:

- `nativeSetApps(ids: Array<String>, names: Array<String>)`: the launchable apps. Telik calls it at start and when
  `LauncherApps` reports a change.
- `nativeSetMedia(flags: Long)`: the media flags from the active `MediaController` (play, pause, next, previous, volume).
  Telik calls it on each change of the playback state.

`atv-core` keeps both in a shared state. `FetchLaunchableApplicationsEvent` answers with the app list. `_launchApp`
checks the id against the list (FR-002) and gives the event to Kotlin. A change of the media flags sends `_iMC` to
each connected session. `_mcc` play, pause, next and previous go to Kotlin as buttons. Kotlin sends them to the
media session with `MediaController.TransportControls`.

The flag values come from pyatv (`MediaControlFlags`). Pure logic gets Rust unit tests in `atv-core`, and the Kotlin
mapping from `PlaybackState` actions to flags gets a JVM unit test.

### US3: buffered audio

No code change. Run the test of `docs/AIRPLAY2.md`. Record the result. Turn the default on only if it passes.

### US4, US5: screensaver

- `AerialDream : DreamService` with a `TextureView` and Media3 ExoPlayer (current stable, pinned). Two players
  fade between videos.
- `AerialCatalog`: reads the public Apple list (`entries.json` in the resources tar of `sylvan.apple.com`), keeps
  the 1080p HEVC or H.264 links, and caches the list on disk for 7 days. Pure parsing gets unit tests.
- `SharedAlbum`: the `webstream` and `webasseturls` calls of a public iCloud album. Pure parsing gets unit tests
  with saved sample JSON.
- The clock and the place name are Compose on top.

### US6: HomeKit

`TvAccessory.kt` adds an `InputSource` service for each HDMI input (type HDMI). An input selection calls the same
code as the HDMI tiles of the Home screen. A `Switch` service "Telik message" shows an overlay through the
`SYSTEM_ALERT_WINDOW` permission that Telik has, and turns the switch off after 8 seconds.

### US7: Shortcuts API

`ShortcutServer`: a small HTTP server on the LAN, off by default, with a bearer token from `SecureRandom` (FR-010).
It allows only the actions of FR-011. Pure request parsing gets unit tests.

### US8: several HomePods

Extend `audiooutput/` from one RAOP receiver to a list, with one shared start time and a user delay. This is the
last task and the riskiest: if the group cannot hold sync, the story stops at one speaker with a delay setting.

## Constitution check

- I. Everything runs on the TV: yes. The Shortcuts app is part of iOS, the user installs nothing.
- II. No protection bypass: yes. No DRM content is touched.
- III. Privacy: the album link, the token and media titles stay out of logs (FR-013).
- IV. Current tools: Media3 at the current stable version, pinned.
- V. Test on the TV: each story has a TV check task.
- VI. Do not disturb playback: installs and tests happen only when nothing plays.
