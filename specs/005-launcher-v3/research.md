# Research: Launcher v3

Checks on the test TV on 2026-11-03, with adb as shell. Telik 2.1.0 is the default Home app (`HomeAlias`).

## What the TV gives a third-party Home app

| Source | API | Access on the test TV | Use in v3 |
|---|---|---|---|
| Installed apps | `PackageManager`, `LauncherApps` callbacks | Free | Already in 001 |
| App category | `ApplicationInfo.category` (game, video, audio and others) | Free | Filters and groups |
| Install and update time | `PackageInfo.firstInstallTime`, `lastUpdateTime` | Free | "New" and "Updated" badges |
| Install progress | `PackageInstaller.getAllSessions()` and `SessionCallback` | Free | Tile with progress while an app installs |
| Launches from Telik | Own record in DataStore | Free | Recent apps and default favorites |
| Use of all apps | `UsageStatsManager` | App op `GET_USAGE_STATS` is "default", not granted. The user grants it in the settings or with adb | Better recent apps, optional |
| Now playing in any app | `MediaSessionManager.getActiveSessions` with a notification listener | The system ignores writes to `enabled_notification_listeners` (checked on 2026-11-03). The user allows access on the TV screen, or with `adb shell cmd notification allow_listener` | Now playing card with controls |
| App shortcuts | `LauncherApps.getShortcuts` | Telik is the default Home app, so the call is allowed. Only `com.google.android.gms` has shortcuts on the TV | Low value. Show them when an app has them |
| Preview channels and programs | `TvContract` | `READ_TV_LISTINGS` is dangerous, so the user grants it on the TV. `ACCESS_ALL_EPG_DATA` is `signature|privileged`, and only the maker launcher holds it | Unknown. Shell sees no rows, so a probe in Telik must decide |
| Watch Next | `TvContract.WatchNextPrograms` | As above. A third-party launcher cannot change entries of other apps | Read only, if the probe finds rows |
| HDMI inputs | `TvInputManager` | Free | Already in 001 |
| Uninstall, app info | `ACTION_DELETE`, `ACTION_APPLICATION_DETAILS_SETTINGS` | Free, the system asks the user | Edit menu |

## Not possible for a third-party Home app

- The "For you" rows of Google TV come from Google servers and are private to the maker launcher.
- Telik cannot change or remove Watch Next entries of other apps.
- Telik cannot read the content of other apps beyond what they publish to `TvContract` or their media session.
- Force stop and clear data need system screens. Telik opens the system screen of the app for them.

## Open points

- Under Google TV, do the apps on the test TV publish to `TvContract`? Examples are YouTube and Netflix.
  Task T001 answers this with `READ_TV_LISTINGS`.
- Does the TV show the "Usage access" screen? If not, the user grants `GET_USAGE_STATS` with `adb shell appops set dev.telik.tv GET_USAGE_STATS allow`.

## tvOS Human Interface Guidelines

Read on 2026-11-03 from the JSON of `developer.apple.com/design/human-interface-guidelines` (layout, typography, focus and selection).

- Safe area: inset primary content 60 pt from the top and bottom, and 80 pt from the sides.
- Grid: five columns of 320 pt, 40 pt horizontal spacing, 100 pt minimum vertical spacing. Titled rows need more vertical space.
- Partially hidden content at the screen edges must be symmetrical.
- Text styles (Medium weight): Title 1 76 pt, Title 2 57 pt, Title 3 48 pt, Headline 38 pt, Callout 31 pt, Body 29 pt, Caption 1 25 pt, Caption 2 23 pt.
- Focus: a focused item stands out by elevation, light and animation. Unfocused items are less prominent.
- The test TV has a density of 320 at 1920x1080, so the screen is 960x540 dp, and 1 pt is 0.5 dp.

