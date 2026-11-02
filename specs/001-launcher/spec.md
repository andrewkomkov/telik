# Feature Specification: Telik as the TV Launcher

<!-- STE exception: the Given, When, Then scenarios follow the spec-kit template and keep its order. -->

**Feature Branch**: `001-launcher`
**Created**: 2026-11-02
**Status**: Draft (plan only, no code)
**Input**: "Make our app a launcher. It must look and behave like Apple TV. It must be as light and fast as possible, use the newest technology, and have a liquid glass look."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Telik is the Home screen (Priority: P1)

The user presses the Home button on any remote. Telik opens as the Home screen, so the TV looks like an Apple TV from the first second.

**Why this priority**: Without the Home role, Telik is one app among others. This story is the base for all other stories.

**Independent Test**: Install the build, choose Telik as the default Home app, press Home in any app. Telik opens in less than 1 second.

**Acceptance Scenarios**:
1. **Given** Telik is the default Home app, **When** the user presses Home, **Then** the Telik Home screen opens.
2. **Given** the user runs another app, **When** the user presses Home, **Then** that app moves to the background and Telik shows its last row focus.
3. **Given** Telik is not the default Home app, **When** the user opens Telik, **Then** it offers to become the default Home app and explains how to undo this.

---

### User Story 2 - App rows like on Apple TV (Priority: P1)

The user sees a large row of app icons. The focused icon grows, tilts and shows a parallax layer effect, as on Apple TV. The user can reorder and hide apps.

**Why this priority**: The app row is the main part of the Apple TV look and the main way to start an app.

**Independent Test**: Open the Home screen with 20 installed apps. Move focus with the D-pad and check size, tilt and order. Reorder one app and restart the TV. The order stays.

**Acceptance Scenarios**:
1. **Given** the Home screen is open, **When** the focus moves to an icon, **Then** the icon grows with a smooth animation of 60 frames per second.
2. **Given** the user holds OK on an icon, **When** the edit mode opens, **Then** the user can move the icon, hide it or open its information.
3. **Given** a new app is installed, **When** the user returns Home, **Then** its icon is in the row.

---

### User Story 3 - Top shelf and continue watching (Priority: P2)

Above the app row, the user sees a large shelf for the focused app. It shows content that the app offers, such as the recommended channel of Android TV, and the last watched item.

**Why this priority**: The top shelf makes the screen useful and not only a list of icons.

**Independent Test**: Focus an app that publishes a channel. The shelf shows its items. Press OK on one item. The app opens that item.

**Acceptance Scenarios**:
1. **Given** the focused app publishes preview programs, **When** the focus stays 300 ms, **Then** the shelf shows them.
2. **Given** the focused app publishes nothing, **When** the focus stays 300 ms, **Then** the shelf shows the Telik status panel.

---

### User Story 4 - Telik features on the Home screen (Priority: P2)

The current Telik entries stay on the Home screen: screen mirroring, headphones, Apple Home and sound on a HomePod. Their state is visible without a settings screen.

**Why this priority**: These entries are the reason for Telik. The launcher must not hide them.

**Independent Test**: On the Home screen, connect the headphones with one press and see the state change in under 5 seconds.

**Acceptance Scenarios**:
1. **Given** the Home screen is open, **When** an iPhone starts screen mirroring, **Then** the Home screen shows the mirror state.
2. **Given** the headphones are paired, **When** the user presses OK on the Headphones entry, **Then** the state changes as it does today.

---

### User Story 5 - Wallpaper and screensaver (Priority: P3)

The Home screen has a moving background and a screensaver with slow landscape video. The user can switch them off.

**Why this priority**: This is the polish that makes the screen look modern, and it has the highest cost.

**Independent Test**: Leave the Home screen for the screensaver time. The screensaver starts. Any key stops it.

**Acceptance Scenarios**:
1. **Given** the Home screen is idle for the set time, **When** the time ends, **Then** the screensaver starts.
2. **Given** the screensaver runs, **When** the user presses any key, **Then** the Home screen returns at once.

### User Story 6 - Inputs, settings and remote keep working (Priority: P1)

The user reaches the HDMI inputs, the system settings and the search from the Home screen. The iPhone remote and the Apple Watch remote keep working, and the TV button of the phone opens the Home screen.

**Why this priority**: A Home screen without inputs and settings locks the user out of the TV. This story blocks the release of story 1.

**Independent Test**: From the Home screen, open an HDMI input, open the settings and go back. Then press the TV button in the iPhone remote.

**Acceptance Scenarios**:
1. **Given** the Home screen is open, **When** the user selects an HDMI input, **Then** the input opens.
2. **Given** the Home screen is open, **When** the user selects settings, **Then** the system settings open.
3. **Given** the iPhone remote is connected, **When** the user presses the TV button, **Then** the Home screen opens.

### Edge Cases

- The TV has an old low-end chip. Animations must degrade to a plain focus frame at under 30 frames per second.
- The TV reboots. Telik starts as Home in less than 10 seconds and shows the last order.
- An app has no banner icon. Telik shows the app icon on a colored tile.
- The user wants the old Home screen back. One path in settings removes the Telik Home role.
- A second Home app is installed. The system chooser stays available.
- Telik crashes as the Home app. The system must still show a Home screen.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Telik MUST declare the Home role and offer to become the default Home app.
- **FR-002**: Telik MUST list every launchable app, including apps that only declare the leanback launcher.
- **FR-003**: Users MUST be able to reorder and hide apps, and the order MUST persist.
- **FR-004**: The focused icon MUST show size, tilt and layer effects, and MUST fall back to a plain frame on slow TVs.
- **FR-005**: Telik MUST show the content that the focused app publishes as Android TV preview programs, when the app publishes them.
- **FR-006**: Telik MUST keep the existing entries: mirroring, headphones, Apple Home and HomePod sound.
- **FR-007**: Telik MUST let the user remove the Home role from the settings with one path.
- **FR-008**: Telik MUST NOT require a permission that the user must grant through adb, except the existing headphones feature.
- **FR-009**: Telik MUST NOT bypass any protection of other apps and MUST NOT read their private data.
- **FR-010**: All text MUST exist in English and Russian.
- **FR-015**: Telik MUST show tiles for the HDMI inputs and the system settings, and MUST show a search entry.
- **FR-016**: The Home screen MUST accept the D-pad keys from the Telik remote service, and the remote Home action MUST open it.
- **FR-017**: If Telik fails to start as Home three times in a row, the system MUST get a plain Home screen from the maker. Telik drops the Home role by itself and shows a notice.
- **FR-011**: The Home screen MUST use a liquid glass look: translucent blurred panels, soft highlights and depth, over a moving background.
- **FR-012**: The glass look MUST switch to a plain translucent panel with no blur when a frame takes more than 33 ms, and the user MUST be able to switch the glass off.
- **FR-013**: The launcher MUST stay light: no heavy library, no work in the background while the Home screen is hidden, and cached icons.
- **FR-014**: The launcher MUST use the newest stable platform APIs and tools, with exact version pins.

### Key Entities

- **App tile**: one launchable app with its icon, label, order and hidden flag.
- **Shelf item**: one content item of an app, with title, image and the intent that opens it.
- **Home layout**: the ordered list of tiles and the Telik entries, stored on the TV.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After Home is pressed, the first frame is visible in 800 ms or less on the test TV (Dahua 4K, Android 14, 32-bit).
- **SC-002**: Focus moves at 30 frames per second or more with 40 apps installed.
- **SC-003**: The app starts as the Home app after a reboot in less than 10 seconds.
- **SC-004**: The user restores the old Home screen in 3 steps or fewer.
- **SC-005**: The APK grows by 2 MB or less.
- **SC-006**: The Home screen uses 100 MB of memory or less when it is idle.
- **SC-007**: With the glass look on, focus moves at 30 frames per second or more. When it cannot, the plain panel starts by itself.
- **SC-008**: The Home screen uses no CPU in the background when it is hidden.

## Assumptions

- The target is the same as for Telik today: Android TV 13 or later, 32-bit ARM.
- Android TV preview channels are the only content source in the first version. Other sources are out of scope.
- Liquid glass means blur, translucency, highlights and depth. Telik draws it with its own code on current Compose APIs. It is not a copy of Apple artwork.
- Apple trademarks and artwork are not copied. Telik copies the behavior and the layout idea, with its own icons, fonts and wallpapers.
- The first release has stories 1 and 2. Stories 3, 4 and 5 follow in separate releases.
- Some TV makers lock the Home role. On such a TV, Telik stays a normal app and says so.

## Open Questions

- **Q1**: Should Telik replace the maker Home screen, or open only from a button? Desk research says an app can be a selectable Home app without root. A test on the target TV decides (see `research.md` and T001). [NEEDS CLARIFICATION]
- **Q2**: Which wallpaper and screensaver sources are free to use? [NEEDS CLARIFICATION]
- **Q3**: Can a third-party launcher read the preview programs of other apps? Reports say it needs the runtime permission `READ_TV_LISTINGS`, which the user grants on the TV with no adb. Test T003 decides story 3. [NEEDS CLARIFICATION]
