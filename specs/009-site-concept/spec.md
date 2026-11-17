# Feature Specification: The site as a product page

**Feature Branch**: `feat/site-concept`

**Created**: 2026-11-17

**Status**: In progress.

**Input**: The user asked to update the landing page for 3.1.0 as a concept in the style of Apple in 2027.
The user left the concept to the developer. The site in `site/` shows the features of 2.x only.

## Concept

"Android TV. Now speaks iPhone." The page reads as an Apple product page: a dark canvas, large type, panels of
liquid glass, and chapters that show on scroll. The pictures are scenes made in HTML and CSS: the TV, the iPhone
remote, the Home screen and the screensaver. They show sample data, not the watch history or the city of the user.

<!-- STE note: the scenarios use the Given, When, Then form of all specs in this repository. -->

## User Scenarios and Testing

### User Story 1 - A visitor understands Telik in one screen (Priority: P1)

**Acceptance Scenarios**:

1. **Given** a visitor opens the page, **Then** the first screen shows the claim, the TV and iPhone scene and the
   download button.
2. **Given** a phone screen of 375 px, **Then** the page has no horizontal scroll.

### User Story 2 - A visitor sees what 3.x adds (Priority: P1)

The chapters show the iPhone remote with skip, the keyboard, AirPlay, Apple Home with HDMI inputs, the Home screen with
Continue watching and the app shelf, the weather with the forecast, the 4K aerial screensaver and AirPods.

### User Story 3 - The same page in Russian (Priority: P1)

## Requirements

- **FR-001**: The page MUST state only features that the user checked on the TV. These stay out: the title and art on
  the iPhone, the message switch of Apple Home, Siri Shortcuts and the iCloud album.
- **FR-002**: The page MUST load all its files from the site itself. Web fonts, CDN scripts and analytics stay out
  (constitution III).
- **FR-003**: The motion MUST stop when the visitor asks for reduced motion.
- **FR-004**: The privacy pages MUST keep working with the new `style.css`.
- **FR-005**: The page MUST keep the SEO data: the title, the description, `hreflang`, Open Graph and JSON-LD.
- **FR-006**: The scenes MUST use made-up titles. Names and art of real films and series stay out.
