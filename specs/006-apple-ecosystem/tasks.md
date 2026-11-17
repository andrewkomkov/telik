# Tasks: Apple ecosystem

## Phase 1: The iPhone remote (US1, US2, P1)

- [x] T001 [US1] Add a shared app list to `atv-core`. Answer `FetchLaunchableApplicationsEvent` from it. Refuse `_launchApp` for an id that is not in it. Add Rust tests.
- [x] T002 [US1] Add `nativeSetApps` to `atv-android` and `RemoteNative.kt`. Push the launchable apps at start and on each app change. Open the app on `LaunchApp`.
- [x] T003 [US2] Add shared media flags to `atv-core`. Send `_iMC` to the sessions when they change. Add Rust tests.
- [x] T004 [US2] Add `nativeSetMedia`. Map the `PlaybackState` actions to the flags, with a JVM test. Send play, pause, next and previous from `_mcc` to the media session.
- [ ] T005 [US1, US2] Check on the TV with the iPhone remote: the app list, an app launch, pause and play.
  - 2026-11-17: pyatv 0.18 was the Companion client (`/Volumes/SanDisk2Tb/builds/telik/atv_probe.py`). The list showed 16 TV apps. YouTube opened. The media buttons showed only while media played. Pause and play worked. The check with a real iPhone is open.

## Phase 2: Buffered audio (US3, P2)

- [ ] T010 [US3] Run the test of `docs/AIRPLAY2.md` with an iPhone. Record the result there. Turn the default on only if it passes.

## Phase 3: Screensaver (US4, US5, P2)

- [x] T020 [US4] Add `AerialCatalog` with a parser of the Apple list and a disk cache. Add unit tests.
- [x] T021 [US4] Add `AerialDream` with two players that fade, the clock and the place name.
- [ ] T022 [US5] Add `SharedAlbum` with a parser of the iCloud answers. Add unit tests. Add the album link to the Telik settings.
- [ ] T023 [US4, US5] Check on the TV: 1 hour of play, the memory, the end on a key press, no network.

## Phase 4: HomeKit (US6, P2)

- [ ] T030 [US6] Add the HDMI inputs as input sources.
- [ ] T031 [US6] Add the "Telik message" switch with the overlay.
- [ ] T032 [US6] Check with the Home app on the iPhone.

## Phase 5: Shortcuts (US7, P3)

- [x] T040 [US7] Add `ShortcutServer` with the token and the actions of FR-011. Add unit tests for the parser.
- [ ] T041 [US7] Add the settings: on and off, the token, a link to copy.
- [ ] T042 [US7] Check with a shortcut on the iPhone.

## Phase 6: Several HomePods (US8, P3)

- [ ] T050 [US8] Send to a list of AirPlay speakers with one start time and a delay setting.
- [ ] T051 [US8] Check with two speakers.

## Phase 7: Release

- [ ] T060 Add changelog files in English and Russian in `fastlane/metadata/android/`.
