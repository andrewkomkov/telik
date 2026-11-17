# Feature Specification: Apple ecosystem

**Feature Branch**: `feat/apple-ecosystem` (stacked on `feat/launcher-v3`)

**Created**: 2026-11-17

**Status**: In progress. US1 and US2 have code and pass a protocol check. US4 has code.

**Input**: The user said on 2026-11-17: "the main feature is that Telik is part of the Apple ecosystem". The user asked for all
the Apple features of the research below ("делай все").

The research of 2026-11-17 compared Telik with UxPlay, shairport-sync, pyatv and OwnTone. It found these gaps:

- The iPhone remote shows a fixed app list.
- The iPhone remote shows the volume buttons only.
- Buffered AirPlay audio has code but no test.
- Telik has no screensaver.
- HomeKit shows the apps as inputs, but not the HDMI inputs.
- The TV sound goes to one HomePod only.

<!-- STE note: the scenarios use the Given, When, Then form of all specs in this repository. -->


## User Scenarios & Testing

### User Story 1 - The real apps in the iPhone remote (Priority: P1)

The user opens the Apple TV remote in Control Center on the iPhone and taps the app list. The list shows the apps of the TV.
A tap opens the app on the TV.

**Why this priority**: The protocol path is in the code. Only the data is fake: the list now shows Apple apps that the TV does not have.

**Independent Test**: Open the app list in the iPhone remote. Compare it with the Home screen of Telik. Tap YouTube.

**Acceptance Scenarios**:

1. **Given** the iPhone remote is connected, **When** the user opens the app list, **Then** it shows the launchable TV apps with their names.
2. **Given** the list, **When** the user taps an app, **Then** the app opens on the TV within 2 seconds.
3. **Given** the user installs or removes an app, **When** the user opens the list again, **Then** the list shows the change.

---

### User Story 2 - Media buttons in the iPhone remote (Priority: P1)

Media plays on the TV. The iPhone remote shows play, pause and skip, and the buttons control the media on the TV.

**Why this priority**: The iPhone remote of an Apple TV has these buttons. Telik sends only the volume flag, so they are hidden.

**Independent Test**: Play a YouTube video on the TV. Open the iPhone remote. Tap pause, then play.

**Acceptance Scenarios**:

1. **Given** a media session on the TV, **When** the iPhone remote connects, **Then** it shows play and pause.
2. **Given** the session can skip, **When** the remote shows, **Then** it shows next and previous.
3. **Given** the user taps pause, **When** the TV gets the command, **Then** the media pauses within 1 second.
4. **Given** no media session, **When** the remote shows, **Then** it shows the volume only, as now.

---

### User Story 3 - Buffered AirPlay 2 audio (Priority: P2)

Music from the iPhone plays on the TV over the buffered AirPlay 2 path, as on a HomePod.

**Why this priority**: The code exists and is off. It needs a test on an iPhone.

**Independent Test**: The test of `docs/AIRPLAY2.md`, "Stage 4 code".

**Acceptance Scenarios**:

1. **Given** the setting is on, **When** the user plays music from Apple Music on the iPhone, **Then** the TV plays it with no gaps for 10 minutes.
2. **Given** the test fails, **When** the result is known, **Then** the setting stays off and `docs/AIRPLAY2.md` records the failure.

---

### User Story 4 - Aerial screensaver (Priority: P2)

The TV is idle. Telik shows the slow aerial videos of Apple TV, with the clock and the place name.

**Why this priority**: It is the most visible Apple TV detail. Spec 001 planned a screensaver and has no code for it.

**Independent Test**: Select the Telik screensaver in the TV settings. Wait for the idle time.

**Acceptance Scenarios**:

1. **Given** the Telik screensaver is selected, **When** the TV is idle for the system time, **Then** an aerial video plays at 1080p.
2. **Given** a video ends, **When** the next one starts, **Then** it fades in, with no black frame longer than 1 second.
3. **Given** no network, **When** the screensaver starts, **Then** it shows the clock on a dark screen.
4. **Given** any key, **When** the user presses it, **Then** the screensaver ends.

---

### User Story 5 - iCloud shared album in the screensaver (Priority: P3)

The user pastes the link of an iCloud shared album with a public website in the Telik settings. The screensaver shows its photos.

**Why this priority**: Family photos on the TV. There is no official API, so it can break.

**Independent Test**: Make a shared album public in Photos on the iPhone. Type its link on the TV. Start the screensaver.

**Acceptance Scenarios**:

1. **Given** a public album link, **When** the screensaver starts, **Then** it shows the photos, one every 10 seconds, between the videos or in place of them.
2. **Given** the album is no longer public, **When** the screensaver starts, **Then** it shows the aerial videos and no error.

---

### User Story 6 - More HomeKit (Priority: P2)

In the Home app, the TV lists its HDMI inputs. A Home automation can show a short message on the TV.

**Why this priority**: The HomeKit accessory exists. Siri and the Home app can then switch to HDMI 2 and announce the doorbell.

**Independent Test**: Open the TV in the Home app and select HDMI 1. Make an automation that turns on the "Telik message" switch.

**Acceptance Scenarios**:

1. **Given** the TV in the Home app, **When** the user opens the input list, **Then** it shows the HDMI inputs with their names.
2. **Given** the user selects an HDMI input, **When** the TV gets it, **Then** the TV switches to that input.
3. **Given** the message switch turns on, **When** the TV gets it, **Then** the TV shows a banner for 8 seconds over any app, and the switch turns off.

---

### User Story 7 - Siri Shortcuts (Priority: P3)

The user makes a shortcut in the Shortcuts app on the iPhone with "Get Contents of URL". The shortcut opens an app on the TV,
selects an input or shows a message.

**Why this priority**: "Hey Siri, open YouTube on the TV" with no extra app. The Shortcuts app is part of iOS, so it agrees with principle I.

**Independent Test**: Copy the shortcut link from the Telik settings. Run the shortcut on the iPhone.

**Acceptance Scenarios**:

1. **Given** the token from the Telik settings, **When** a request with the token asks to open an app, **Then** the app opens.
2. **Given** a request without the correct token, **When** Telik gets it, **Then** Telik refuses it with status 401 and does nothing.
3. **Given** the API is off in the settings, **When** a request comes, **Then** the port is closed.

---

### User Story 8 - TV sound on several HomePods (Out of scope)

The user removed this story on 2026-11-17 ("можно без нее"). The sender stays at one speaker.

The user selects two or more AirPlay speakers in Telik. The sound of the TV plays on all of them, with a delay setting for lip sync.

**Why this priority**: High value, but the sync of a group without PTP is not proven.

**Independent Test**: Select two HomePods. Play a video. Set the delay until the lips match.

**Acceptance Scenarios**:

1. **Given** two speakers, **When** media plays, **Then** both speakers play it, and the difference between them is less than 50 ms by ear.
2. **Given** the delay setting, **When** the user changes it, **Then** the sound moves against the picture by that time.
3. **Given** one speaker goes away, **When** the others still play, **Then** the sound does not stop on them.

### User Story 9 - Weather next to the clock (Priority: P2)

The screensaver shows the weather under the clock, as on Apple TV. The Home screen shows it next to the clock.
The user asked for the weather "in a natural place" and then in the screensaver overlay on 2026-11-17.

**Why this priority**: The user asked for it twice.

**Independent Test**: Start the screensaver. The weather symbol and the temperature show under the clock.

**Acceptance Scenarios**:

1. **Given** the network, **When** the screensaver or the Home screen shows, **Then** the temperature and a weather symbol show next to the clock.
2. **Given** no city in the settings, **When** Telik gets the weather, **Then** it uses the city of the time zone of the TV.
3. **Given** a city in the settings, **When** Telik gets the weather, **Then** it uses that city.
4. **Given** no network, **When** the screens show, **Then** the clock shows alone.
5. **Given** the weather next to the clock on the Home screen, **When** the user moves the focus to it, **Then** the title area shows the forecast for 5 days: the day, a symbol, the high and the low. The user asked for this on 2026-11-17.

### Edge Cases

- The iPhone remote asks for the app list before Telik has read the apps. Telik reads them at that time.
- A media session ends while the iPhone remote is open. The remote hides the media buttons.
- The aerial video list of Apple changes or is not available. The screensaver uses the clock.
- Two Shortcut requests come at the same time. Telik does them in order.

## Requirements

### Functional Requirements

- **FR-001**: The app list of the iPhone remote MUST come from the launchable TV apps (`LEANBACK_LAUNCHER`), with the package name as the id.
- **FR-002**: `_launchApp` MUST open only a package from that list.
- **FR-003**: The media flags of the iPhone remote MUST follow the active media session of the TV.
- **FR-004**: Play, pause, next and previous from the iPhone MUST go to the active media session of the TV.
- **FR-005**: Buffered audio MUST stay off by default until the iPhone test passes.
- **FR-006**: The screensaver MUST be an Android `DreamService`, selectable in the TV settings. It MUST stream the videos and MUST NOT ship them in the app.
- **FR-007**: The screensaver MUST NOT keep the screen on after the system time for Energy Saver.
- **FR-008**: The album link MUST stay on the TV. Telik MUST send only the album id to the iCloud host.
- **FR-009**: HomeKit MUST list the HDMI inputs as input sources after the app inputs.
- **FR-010**: The Shortcuts API MUST be off by default. It MUST need a random token of 128 bits or more, made on the TV.
- **FR-011**: The Shortcuts API MUST allow only these actions: open an app, select an input, show a message, the media keys.
- **FR-012**: All text MUST exist in English, Russian and Traditional Chinese.
- **FR-014**: The weather MUST come from Open-Meteo, with no key and no account. Telik MUST send only the coordinates of the city, not the IP location or other data. It MUST ask at most once in 30 minutes.
- **FR-013**: Logs MUST NOT hold the album link, the token or media titles (principle III).

### Key Entities

- **App entry**: package name, label. The iPhone remote sees it.
- **Aerial video**: link, place name, time of day.
- **Shortcut action**: kind, argument.

## Success Criteria

- **SC-001**: The iPhone remote opens a TV app in 2 seconds or less from the tap.
- **SC-002**: Pause from the iPhone stops the media in 1 second or less.
- **SC-003**: The screensaver plays for 1 hour with no crash, and the memory of the dream process stays under 250 MB.
- **SC-004**: A Shortcut request opens an app in 2 seconds or less.

## Assumptions

- The iPhone remote of iOS 26 still uses the Companion messages that pyatv documents.
- The aerial video list at `sylvan.apple.com` stays public. Its license is not clear, so Telik streams and does not copy.
- The user can test with an iPhone, a Watch and HomePods at home.
- Out of scope: a full AirPlay 2 receiver with PTP (it needs ports under 1024), AirDrop, Handoff, Universal Clipboard,
  SharePlay, Find My. None of them is possible for an app without root.
