# Implementation Plan: Telik as the TV Launcher

**Branch**: `001-launcher` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)

<!-- STE exception: this plan lists technical terms and product names as they are. -->

## What the plan covers

Telik gets a Home screen role. A new Home activity shows an Apple TV style app row, a top shelf, and the current Telik entries. The look is liquid glass, and the code is light and fast. The first release has the Home role and the app row (stories 1 and 2). Stories 3, 4 and 5 follow in later releases. No code is written in this step.

## Technical Context

**Language/Version**: Kotlin 2.x, Jetpack Compose (BOM 2026.05.00), minSdk 33
**Primary Dependencies**: Compose (BOM 2026.05.00), `androidx.tv:tv-material` 1.1.0, `androidx.tv:tv-foundation` 1.0.0, DataStore. No image library: the icons come from `PackageManager` and a small own cache.
**Storage**: DataStore preferences for order and hidden apps
**Testing**: JUnit unit tests for ordering and filtering. Checks on the real TV for focus and speed.
**Target Platform**: Android TV 13 or later, 32-bit ARM, tested on a Dahua 4K TV with Android 14
**Project Type**: Android app, one module `app`
**Performance Goals**: First frame in 800 ms or less. Focus at 30 frames per second or more with 40 apps and the glass look on. Memory 100 MB or less when idle. APK growth 2 MB or less.
**Constraints**: No root. No new adb permission. APK growth is 4 MB or less. Low-end chip.
**Scale/Scope**: About 40 apps per TV, one screen with 3 layers.

## Constitution Check

- Everything runs on the TV: yes, no extra device.
- No protection bypass: yes. Telik reads only the public preview programs that apps publish.
- Privacy: yes. No analytics, and the app list stays on the TV.
- Current tools: use the newest stable Compose, `androidx.tv` and platform APIs, with exact pins.
- Verify on the TV: each story has a TV check in `tasks.md`.
- Do not disturb playback: the Home role changes the Home button only, and no test stops a video.

## Research Questions (resolve in phase 0)

1. **Home role**: What does an Android 14 TV with a locked launcher do when an app declares `HOME` and `DEFAULT`? Test on the target TV with `cmd role` and the system chooser. The answer decides Q1 of the spec.
2. **App list**: Use `PackageManager.queryIntentActivities` for `LEANBACK_LAUNCHER` and `LAUNCHER`. Check the package visibility rule for Android 11 and later. Telik already declares queries for both categories.
3. **Top shelf**: Read `TvContract` preview channels and programs. The AOSP manifest says `READ_EPG_DATA` is no longer enforced, but community reports say a launcher needs `READ_TV_LISTINGS`. Declare it, ask for it on the TV, and check that a query returns the programs of other apps. If it returns nothing, use the Telik content and the media session of the running app. See `research.md`.
4. **Liquid glass**: Compare three ways on the test TV. The first is `RenderEffect` blur with `Modifier.graphicsLayer`. The second is an AGSL `RuntimeShader` (API 33 and later) for blur, refraction and highlights. The third is a blurred copy of the wallpaper, made once and reused. Choose the cheapest way that holds 30 frames per second. A library such as Haze is allowed only if it is small and faster than own code.
4b. **Parallax icons**: Apple uses layered images. Android apps give one banner. Plan a depth effect from one image: shadow, scale, tilt and a light sweep. No layered art is copied.
5. **Slow chip**: Measure frame time on the test TV with 40 icons before choosing the animation level.
6. **Speed and size**: Add a Baseline Profile and a Macrobenchmark for cold start. Keep the Home activity in its own small process path, with no Hilt and no dependency injection graph. Use R8 full mode. Draw with `drawWithCache` and keep recompositions low. Check with the layout inspector and the frame time on the TV.
7. **Screensaver**: Use `DreamService` for the screensaver. Choose free landscape video that has a license that allows redistribution.

## Project Structure

### Documentation

```text
specs/001-launcher/
├── spec.md
├── plan.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/
├── AndroidManifest.xml            # HOME activity, queries
└── kotlin/io/github/henry753951/airplayremote/
    ├── launcher/                  # new package
    │   ├── LauncherActivity.kt    # Home activity
    │   ├── AppRepository.kt       # installed apps and package events
    │   ├── LayoutStore.kt         # order and hidden flags in DataStore
    │   ├── Shelf.kt               # preview programs (story 3)
    │   └── ui/                    # rows, tiles, focus effects
    └── ui/                        # existing screens, reuse Tv.kt and TvFocus.kt
app/src/test/                      # unit tests for ordering and filtering
```

**Structure Decision**: One new package `launcher` inside the existing module. The current `TvScreens.kt` entries move into the Home screen as a first row in story 4. The existing `MainActivity` stays as the settings and status screen.

## Phases

- **Phase 0**: Answer the research questions on the target TV. Write `research.md`.
- **Phase 1**: Home role and app row (stories 1 and 2). Release as 2.1.0.
- **Phase 2**: Telik entries on the Home screen (story 4). Release as 2.2.0.
- **Phase 3**: Top shelf (story 3). Release as 2.3.0.
- **Phase 4**: Wallpaper and screensaver (story 5). Release as 2.4.0.

## Risks

- A maker locks the Home role. Then Telik stays a normal app, and the app says so.
- A Home app that crashes makes the TV unusable. Keep the Home activity small, and test a crash on the TV before a release.
- The user cannot find the old Home screen. Give one visible path to remove the role.
- Layout and animation cost on a slow chip. Measure first and keep the plain-frame fallback.
- Blur is the most costly effect on a low-end chip. The glass look has an automatic fallback and a switch in the settings.
