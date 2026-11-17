# Feature Specification: Now playing in the iPhone remote

**Feature Branch**: `feat/iphone-now-playing`

**Created**: 2026-11-17

**Status**: Research. No code yet.

**Input**: The user tested the iPhone remote on 2026-11-17 with YouTube playing on the TV. The remote showed no media
card. The TV log shows that the remote of iOS 26 asks for `FetchCurrentNowPlayingInfoEvent`, `FetchUpNextInfoEvent` and
`FetchCurrentTopShelfItemsEvent`. Telik answers them with nothing. The remote never asks for the app list.

<!-- STE note: the scenarios use the Given, When, Then form of all specs in this repository. -->

## User Scenarios & Testing

### User Story 1 - The media card (Priority: P1)

Media plays on the TV. The remote on the iPhone shows the title, the artwork and the progress, as with an Apple TV.

**Acceptance Scenarios**:

1. **Given** media plays on the TV, **When** the user opens the remote, **Then** it shows the title and the artwork.
2. **Given** the media changes, **When** the remote is open, **Then** the card shows the new media within 2 seconds.

### User Story 2 - Up Next and Top Shelf (Priority: P2)

The remote shows the Continue watching items of the TV, if iOS shows them for a TV of this kind.

## Requirements

- **FR-001**: Telik MUST send only the title, the subtitle, the artwork and the times. It MUST NOT log them (006 FR-013).
- **FR-002**: The formats MUST come from a source (pyatv, a write-up) or from a check on the iPhone of the user.

## Assumptions

- The iPhone of the user is on the cable of the Mac. `xcrun devicectl device capture screenshot` gives its screen.
- The protocol has no public documentation. A format that the iPhone does not accept stays out.
