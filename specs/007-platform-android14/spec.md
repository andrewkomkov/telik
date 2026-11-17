# Feature Specification: Current platform features (Android 13 and 14)

**Feature Branch**: `feat/platform-android14` (stacked on `feat/apple-ecosystem`)

**Created**: 2026-11-17

**Status**: Done on the test TV on 2026-11-17: Back on all levels, English UI, font scale 2.0 with no overlap.

**Input**: The user asked on 2026-11-17 for a launcher with all current platform features ("state of the art"). slop: user quote.
A research of the official Android 13 and 14 pages on 2026-11-17 gave the list below. The test TV runs Android 14 (API 34).

<!-- STE note: the scenarios use the Given, When, Then form of all specs in this repository. -->

## User Scenarios & Testing

### User Story 1 - Predictive back (Priority: P1)

Back goes through the platform back callback, as Android 14 asks. The system animation shows where the TV supports it.

**Independent Test**: Open the Telik settings, a sub page and an app menu on the Home screen. Press Back in each.

**Acceptance Scenarios**:

1. **Given** a sub page or a menu, **When** the user presses Back, **Then** it closes, as before.
2. **Given** the Home screen with no menu, **When** the user presses Back, **Then** Telik stays on the Home screen.

---

### User Story 2 - The language of Telik (Priority: P2)

The user selects the language of Telik apart from the TV language: system, English, Russian or Traditional Chinese.

**Independent Test**: Select English in the Telik settings while the TV is in Russian.

**Acceptance Scenarios**:

1. **Given** the language setting, **When** the user selects a language, **Then** all Telik screens show that language at once.
2. **Given** a TV with a per-app language screen, **When** the user opens it, **Then** Telik is in the list with its three languages.

---

### User Story 3 - Large text (Priority: P2)

The user sets a large font size on the TV. The Home screen and the settings stay usable.

**Independent Test**: Set the font scale to 1.3 and to 2.0 with adb. Take screenshots.

**Acceptance Scenarios**:

1. **Given** a font scale up to 2.0, **When** the Home screen shows, **Then** no text overlaps, and labels end with an ellipsis instead of a cut.

---

### User Story 4 - Behavior of target SDK 34 (Priority: P1)

Telik follows the rules of Android 14 for its target SDK, so nothing stops without a message.

**Acceptance Scenarios**:

1. **Given** the receivers that Telik registers in code, **When** Telik registers them, **Then** each one states if it is exported.
2. **Given** a start of an activity from a service, **When** no Telik window shows, **Then** the start goes through the accessibility service (done in 006).

### Edge Cases

- The TV has no per-app language screen. The Telik setting still works.

## Requirements

- **FR-001**: The app MUST set `android:enableOnBackInvokedCallback="true"` and MUST handle Back only through `OnBackPressedDispatcher` or `BackHandler`.
- **FR-002**: The build MUST make the locale config from the resources (`generateLocaleConfig`). Telik MUST set its language with `LocaleManager`.
- **FR-003**: The temperature unit MUST come from the regional preferences of the TV (`LocalePreferences`).
- **FR-004**: Each receiver that Telik registers in code MUST state `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED`.
- **FR-005**: Text sizes MUST use `sp`, and one-line labels MUST end with an ellipsis.

## Success Criteria

- **SC-001**: No crash and no change of Back behavior in a check of all screens.
- **SC-002**: At font scale 2.0, the Home screen shows no overlap in screenshots.

## Assumptions

- Out of scope here: app widgets on the Home screen (a separate story), edge to edge (not on Android 14), the predictive back animation of the system (a developer option on Android 14).
