# Tasks: Launcher v3

## Phase 0: Probe

- [x] T001 Add `TvListings`: with `READ_TV_LISTINGS`, it counts the rows of the channels, `PreviewPrograms` and `WatchNextPrograms` for each package. It logs counts only. Run it on the TV.
- [x] T003 Record the result of T001 in `research.md`.
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
- [ ] T026 [US7, US8] Check on the TV with screenshots: no cut rows, five columns, labels on focus only, the backdrop color, the Top Shelf art of the media and the focus without a frame.

## Phase 2c: A richer Home screen (US8, P1)

- [x] T027 [US8] Add `Ambient`: the main color of tile art from a small pixel sample, off the main thread, kept in `BannerCache`. Add unit tests.
- [x] T028 [US8] Add the colored glow to the backdrop and the large art of the Top Shelf in the top right.
- [x] T035 [US8] Speed: the focus state is read only in `Backdrop` and `FocusTitle`, the Top Shelf has no offscreen layer and shows only media art, and the frame watch counts long frames (FR-022).
- [x] T037 [US8] Replace the round glow spots with one tinted vertical gradient (FR-023). Draw the scrim only over media art. Show the tile label only when the large title is off screen.
- [ ] T036 [US8] Check on the TV with `dumpsys gfxinfo`: fewer janky frames than build v3f (66% janky, 89 ms median on 2026-11-17).
- [x] T029 [US8] Rework the focus of `FocusTile`: no frame with effects on, a shadow in the tile color, a light sweep that passes once. Apps without a banner get a tile in their icon color.

## Phase 3: Recent, new and installs (US3, P2)

- [x] T030 [US3] Add the Recent row from `LaunchLog`. Add unit tests.
- [ ] T034 [US3] With usage access, add the apps from `UsageStatsManager` to the Recent row. Do T002 first.
- [x] T031 [US3] Add the "New" and "Updated" badges. Add unit tests.
- [ ] T032 [US3] Add the install progress tile.
- [ ] T033 [US3] Check on the TV: install an app from Google Play and watch the tile.

## Phase 4: Groups (US4, P2)

Out of scope: the user removed US4 on 2026-11-17 ("не делай группы"). On the test TV, 12 of 15 apps declare no category (see `research.md`). T040, T041 and T042 are not done.

## Phase 5: Content and app actions (US5, US6, P3)

- [x] T054 [US5] Add `ContinueWatching`: read `WatchNextPrograms`, keep the items of installed apps, sort them by the last use and remove duplicates. Add unit tests.
- [x] T055 [US5] Add `PosterCache`: load the art of a program from a `content:` or `https:` link off the main thread, at the card size, with a memory limit and its main color. The Top Shelf loads a 1280x720 version. For YouTube, it asks for `maxresdefault.jpg` first.
- [x] T056 [US5] Add the "Continue watching" row with art, a progress bar, the title in the title area and the art in the Top Shelf. OK opens the `INTENT_URI` of the item.
- [x] T057 [US5] Ask for `READ_TV_LISTINGS` on the Home screen, as for notification access, with "Not now".
- [x] T058 [US5] Show the preview programs of the focused app in the Top Shelf (scenario 1).
- [x] T061 [US5] Fixes of 3.1.0: the shelf of an app with no preview channels shows all its Watch Next items, and each Continue watching card keeps its own art. The user checked both on the TV on 2026-11-17.
- [ ] T059 [US5] Check on the TV with screenshots and `gfxinfo`.
- [x] T051 [US6] Add uninstall, the system screen of the app and the app shortcuts to the edit menu.
- [x] T053 [US6] Add the app menu for a long OK. It has these items: open, stop, favorites, move, hide, the system screen, uninstall. Stop runs `am force-stop` through the adb port of the TV. Without the adb port, the system screen opens.
- [ ] T052 Check on the TV: the Home screen opens in 1 second or less with all rows (SC-003).

## Release

- [x] T060 Add changelog files for English and Russian, bump the version to 3.0.0 and release.
