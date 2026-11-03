# Feature Specification: Launcher v3, favorites and app integration

**Feature Branch**: `feat/launcher-v3`
**Created**: 2026-11-03
**Status**: Draft. Planned, no code.
**Input**: The user asked for launcher version 3: favorites and a better integration with the installed apps, as far as the Android TV API allows.

<!-- ASD-STE100 deviation: the section headings and the Given/When/Then scenarios follow the
     mandatory spec-kit template (.specify/templates/spec-template.md), as in specs 001-004. -->
## User Scenarios & Testing *(mandatory)*

### User Story 1 - Favorites (Priority: P1)

The user keeps the apps, HDMI inputs and Telik cards that they use most in a Favorites row at the top of the Home screen.

**Why this priority**: It is the main request, and it needs no permission.

**Independent Test**: Add an app to the favorites from the edit menu. Restart the TV. The app is still first in the Favorites row.

**Acceptance Scenarios**:

1. **Given** an app in the app row, **When** the user holds OK and selects "Add to favorites", **Then** the app shows in the Favorites row.
2. **Given** an HDMI input or a Telik card, **When** the user adds it to the favorites, **Then** it shows in the Favorites row with its live state.
3. **Given** the Favorites row, **When** the user moves or removes an entry in edit mode, **Then** the order persists after a reboot.
4. **Given** no favorites yet, **When** the Home screen opens, **Then** the Favorites row offers the 5 apps that the user opened most from Telik.
5. **Given** a favorite app is uninstalled, **When** the Home screen opens, **Then** the entry is gone and no empty tile stays.

---

### User Story 2 - Now playing (Priority: P1)

When any app plays video or music, the Home screen shows a card with the app, the title, the picture and the progress. Play, pause and next work from the card. OK opens the app.

**Why this priority**: It connects the Home screen with all apps, not only Telik. It needs no user step.

**Independent Test**: Start a video in an app, press Home. The card shows the title. Press play or pause on the card. The video stops and starts.

**Acceptance Scenarios**:

1. **Given** an app with an active media session, **When** the Home screen opens, **Then** the card shows the app name, the title and the artwork that the session gives.
2. **Given** the card, **When** the user selects play or pause, **Then** the session gets the command.
3. **Given** no active session, **When** the Home screen opens, **Then** no card shows.
4. **Given** notification access is off, **When** the Home screen opens, **Then** a hint shows two buttons: `Allow` and `Not now`. `Allow` opens the system screen.

---

### User Story 3 - Recent and new apps (Priority: P2)

The Home screen shows a Recent row and marks new and updated apps.

**Why this priority**: It makes the next app one press away. The basic form needs no permission.

**Independent Test**: Open three apps from Telik. Press Home. The Recent row shows them, last used first.

**Acceptance Scenarios**:

1. **Given** apps opened from Telik, **When** the Home screen opens, **Then** the Recent row shows them, last used first.
2. **Given** the user granted usage access, **When** the Home screen opens, **Then** the Recent row also counts apps opened from other places.
3. **Given** an app installed or updated in the last 7 days, **When** the user sees its tile, **Then** the tile has a "New" or "Updated" badge until the first launch.
4. **Given** an app installs, **When** the Home screen is open, **Then** a tile shows the install progress.

---

### User Story 4 - Groups by category (Priority: P2)

The user filters the app row by category: all, video, music, games, tools. The groups come from the category that each app declares.

**Why this priority**: It helps with many apps and needs no permission.

**Independent Test**: Select "Games". The row shows only apps that declare the game category.

**Acceptance Scenarios**:

1. **Given** apps with declared categories, **When** the user selects a group, **Then** the row shows only that group.
2. **Given** an app with no category, **When** the user opens edit mode, **Then** the user can set its group by hand.

---

### User Story 5 - Content from apps (Priority: P3)

When the focused app publishes channels or Watch Next items to Android TV, the shelf above the app row shows them. OK opens the item in the app.

**Why this priority**: It depends on the apps and on a permission, and the probe (T001) can show that the apps publish nothing under Google TV.

**Independent Test**: Grant `READ_TV_LISTINGS`. Focus an app that publishes a channel. The shelf shows its items.

**Acceptance Scenarios**:

1. **Given** the permission and an app with preview programs, **When** the focus stays 300 ms, **Then** the shelf shows them.
2. **Given** Watch Next items, **When** the Home screen opens, **Then** a "Continue watching" row shows them.
3. **Given** no permission or no items, **When** the Home screen opens, **Then** no empty row shows.

---

### User Story 6 - App actions (Priority: P3)

The edit menu of an app has: add to favorites, move, hide, app info, uninstall, and the shortcuts of the app if it has any.

**Acceptance Scenarios**:

1. **Given** an app, **When** the user selects "Uninstall", **Then** the system uninstall dialog opens.
2. **Given** an app with shortcuts, **When** the user opens its menu, **Then** the shortcuts show, and OK opens one.

### User Story 7 - Layout by the tvOS guidelines (Priority: P1)

The user asked for a Home screen that looks like Apple TV and follows the Apple guidelines. The layout uses the numbers of the tvOS Human Interface Guidelines.

**Why this priority**: The user found the screen of 2.1.0 ugly: rows cut in half, large tiles, a label under each tile.

**Independent Test**: Open the Home screen and move through all rows. No row shows cut in half. Only the tile in focus has a label.

**Acceptance Scenarios**:

1. **Given** any focus, **When** the user moves down, **Then** the focused row moves up as a whole, and no row shows cut in half.
2. **Given** a tile in focus, **When** the focus stays, **Then** the backdrop shows its blurred art, and the title area shows its name and state.
3. **Given** the app grid, **When** the screen shows it, **Then** it has five columns of 16:9 tiles inside the safe area.

---

### Edge Cases

- A favorite is an HDMI input that the TV no longer reports. The entry stays hidden until the input comes back.
- Two media sessions are active. The card shows the session that played last.
- The user denies `READ_TV_LISTINGS`. Story 5 stays off, and Telik does not ask again on each start.
- The app list changes while edit mode is open. Edit mode keeps the focus on the same app.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Telik MUST keep a Favorites row with apps, HDMI inputs and Telik cards, in an order that the user sets and that persists.
- **FR-002**: Telik MUST record the launches that start from its Home screen and MUST use them for the Recent row and the default favorites. The record stays on the TV.
- **FR-003**: Telik MUST show the active media session of any app with title, artwork, progress and play, pause and next.
- **FR-004**: When notification access is off, Telik MUST show a hint that opens the system screen for it. After `Not now`, the hint MUST stay away.
- **FR-005**: Telik MUST use usage access only when the user grants it, and MUST work without it.
- **FR-006**: Telik MUST mark apps that were installed or updated in the last 7 days until their first launch.
- **FR-007**: Telik MUST show the progress of an app install.
- **FR-008**: Telik MUST group apps by their declared category, and the user MUST be able to set the group of an app.
- **FR-009**: Telik MUST read preview programs and Watch Next only with `READ_TV_LISTINGS`, and MUST hide the rows when the read gives nothing.
- **FR-010**: The edit menu MUST offer uninstall and app info through the system screens, and the shortcuts of the app when it has them.
- **FR-011**: All text MUST exist in English, Russian and Traditional Chinese.
- **FR-012**: Telik MUST NOT send the launch record, the usage data or the media titles off the TV, and MUST NOT write titles to the log.
- **FR-013**: The layout MUST use the tvOS guidelines at 0.5 dp per point: a safe area of 80 pt at the sides and 60 pt at the top and bottom.
  The app grid MUST have five columns of 320 pt with 40 pt gaps. The text MUST use the tvOS styles (Title 1 76 pt, Headline 38 pt, Body 29 pt, Caption 25 pt).
- **FR-014**: Only the tile in focus MUST show its label. A focused tile MUST grow only into the gap.
- **FR-015**: The row of the Telik entries MUST sit under the app grid. The user asked for this on 2026-11-03.
- **FR-016**: The clock MUST stay on one line. When the top bar is too wide, the top bar scrolls.

### Key Entities

- **Favorite**: kind (app, input, Telik card), id, position.
- **LaunchRecord**: package name, last launch time, launch count.
- **AppGroup**: package name, group (from the category or set by the user).
- **NowPlaying**: package name, title, artwork, state, position, duration.

## Success Criteria *(mandatory)*

- **SC-001**: A favorite opens with one press from the Home screen.
- **SC-002**: The now playing card shows within 1 second after Home is pressed.
- **SC-003**: The Home screen still opens in 1 second or less with all v3 rows (budget of 001).
- **SC-004**: With no permission granted, stories 1, 2, 3 (basic form), 4 and 6 work.

## Assumptions

- Telik is the default Home app, so the shortcut API is allowed.
- The TV has the system screen for notification access (`NotificationAccessActivity`).
- The apps on the TV publish media sessions while they play. Apple TV and Telik do so on the test TV.

## Open Questions

- **Q1**: Do the apps publish `TvContract` content under Google TV? T001 decides story 5.
- **Q2**: Should the Favorites row replace the Telik cards row, or sit above it? The plan puts it above, and the user decides at the review.
