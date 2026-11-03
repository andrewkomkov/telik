# Tasks: Launcher v3

## Phase 0: Probe

- [ ] T001 Add a hidden debug action that asks for `READ_TV_LISTINGS` and counts rows of `PreviewPrograms` and `WatchNextPrograms` for each package. Run it on the TV.
- [ ] T003 Record the result of T001 in `research.md`.
- [ ] T002 Check whether the TV shows the "Usage access" screen for Telik. Record the result in `research.md`.

## Phase 1: Favorites (US1, P1)

- [x] T010 [US1] Keep the favorites in `LayoutStore`: apps, inputs and Telik cards. Add unit tests for add, remove, move and the removal of uninstalled apps.
- [x] T011 [US1] Add `LaunchLog` and write a record on each launch from the Home screen. Add unit tests for the default favorites.
- [x] T012 [US1] Add the Favorites row and "Add to favorites" and "Remove from favorites" in the edit menu.
- [ ] T013 [US1] Check on the TV: add, move, remove, reboot.

## Phase 2: Now playing (US2, P1)

- [x] T020 [US2] Add the notification listener service and the hint for notification access.
- [x] T021 [US2] Add the now playing card with play, pause and next. OK opens the app.
- [ ] T022 [US2] Check on the TV with Apple TV, YouTube and Telik audio.

## Phase 2b: Layout by the tvOS guidelines (US7, P1)

- [x] T025 [US7] Rework `HomeScreen.kt` with the HIG safe area, the five-column grid, the tvOS text styles, labels on focus only, the focus backdrop and whole-row scroll.
- [ ] T026 [US7] Check on the TV with screenshots: no cut rows, five columns, labels on focus only.

## Phase 3: Recent, new and installs (US3, P2)

- [ ] T030 [US3] Add the Recent row from `LaunchLog`, and from `UsageStatsManager` when usage access is granted.
- [ ] T031 [US3] Add the "New" and "Updated" badges. Add unit tests.
- [ ] T032 [US3] Add the install progress tile.
- [ ] T033 [US3] Check on the TV: install an app from Google Play and watch the tile.

## Phase 4: Groups (US4, P2)

- [ ] T040 [US4] Add `Groups` with the category map and user overrides. Add unit tests.
- [ ] T041 [US4] Add the group filter above the app row and "Set group" in the edit menu.
- [ ] T042 [US4] Check on the TV.

## Phase 5: Content and app actions (US5, US6, P3)

- [ ] T050 [US5] Only if T001 finds rows: add the shelf and the "Continue watching" row from `TvContract`.
- [ ] T051 [US6] Add uninstall, the system screen of the app and the app shortcuts to the edit menu.
- [ ] T052 Check on the TV: the Home screen opens in 1 second or less with all rows (SC-003).

## Release

- [ ] T060 Add changelog files for English and Russian, bump the version to 3.0.0 and release.
