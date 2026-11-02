# Tasks: Telik as the TV Launcher

**Input**: `specs/001-launcher/spec.md` and `plan.md`
**Status**: Plan only. No task has code yet, so no task is ticked.

<!-- STE exception: task lines use the spec-kit format [ID] [P] [Story]. -->

## Phase 0: Research on the target TV

- [ ] T001 Test the Home role on the Dahua TV: declare `HOME` in a debug build and read the result of the system chooser. Record the result in `specs/001-launcher/research.md`.
- [ ] T002 [P] Check `PackageManager.queryIntentActivities` for `LEANBACK_LAUNCHER` and `LAUNCHER` on the TV and list the apps that it returns. Record the result in `research.md`.
- [ ] T003 [P] Check whether Telik can read `TvContract` preview programs of other apps without a special permission. Record the result in `research.md`.
- [ ] T004 [P] Measure the frame time of 40 focusable tiles with scale and tilt on the TV. Record the result in `research.md`.
- [ ] T005 Update the open questions Q1 and Q2 in `spec.md` with the results.

**Checkpoint**: The Home role works on the target TV, or the plan stops here.

## Phase 1: Home role and app row (stories 1 and 2, release 2.1.0)

- [ ] T010 [US1] Add the `HOME` and `DEFAULT` intent filter and a `LauncherActivity` to `app/src/main/AndroidManifest.xml`.
- [ ] T011 [US1] Create `launcher/LauncherActivity.kt` with an empty Compose screen that Home opens in 1 second or less.
- [ ] T012 [US1] Add the prompt that offers the Home role and the one-path removal in the settings.
- [ ] T013 [P] [US2] Create `launcher/AppRepository.kt`: list launchable apps and react to install and remove events.
- [ ] T014 [P] [US2] Create `launcher/LayoutStore.kt`: DataStore order and hidden flags.
- [ ] T015 [P] [US2] Add unit tests for ordering and hiding in `app/src/test/`.
- [ ] T016 [US2] Create the app row with D-pad focus in `launcher/ui/`, reusing `ui/TvFocus.kt`.
- [ ] T017 [US2] Add the focus effect: scale, tilt and light sweep, with a plain-frame fallback on slow TVs.
- [ ] T018 [US2] Add the edit mode: move, hide and open information.
- [ ] T019 [US1] Add English and Russian strings in `app/src/main/res/values/` and `values-ru/`.
- [ ] T020 [US1] Check on the TV: Home opens in 1 second or less, the order survives a reboot, the removal path works, and a crash test leaves a usable Home.
- [ ] T021 Add changelog files `fastlane/metadata/android/*/changelogs/20.txt`, bump the version to 2.1.0 and release.

**Checkpoint**: The user can use Telik as the Home screen with an app row.

## Phase 2: Telik entries on the Home screen (story 4, release 2.2.0)

- [ ] T030 [US4] Move the four Telik entries into the first row of the Home screen with their state subtitles.
- [ ] T031 [US4] Check on the TV: mirroring state, headphones press and HomePod entry work from the Home screen.

## Phase 3: Top shelf (story 3, release 2.3.0)

- [ ] T040 [US3] Create `launcher/Shelf.kt`: read preview programs of the focused app.
- [ ] T041 [US3] Show the shelf after 300 ms of focus, or the Telik status panel when the app publishes nothing.
- [ ] T042 [US3] Check on the TV with two apps that publish channels.

## Phase 4: Wallpaper and screensaver (story 5, release 2.4.0)

- [ ] T050 [US5] Add a moving background with a switch in the settings.
- [ ] T051 [US5] Add a `DreamService` screensaver and the idle time setting.
- [ ] T052 [US5] Choose free landscape video with a license that allows redistribution. Record the license in `docs/`.
- [ ] T053 [US5] Check on the TV: the screensaver starts on time and any key stops it.

## Dependencies

- Phase 0 blocks all other phases.
- Phase 1 blocks phases 2, 3 and 4. Phases 2, 3 and 4 are independent of each other.
