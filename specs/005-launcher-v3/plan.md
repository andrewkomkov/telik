# Plan: Launcher v3

## Order

1. Probe first (T001). It decides story 5 before any work on it.
2. Stories 1 and 2 (P1). They need no user step.
3. Stories 3 and 4 (P2).
4. Stories 5 and 6 (P3). Story 5 only if the probe finds content.

## Structure

- `launcher/FavoritesStore.kt`: DataStore with the favorites list. It extends `LayoutStore` and keeps the format of 001.
- `launcher/LaunchLog.kt`: DataStore with the launch record. `LauncherActivity.launchApp` writes to it.
- `launcher/NowPlaying.kt`: a `NotificationListenerService` that has no notification UI. It gives the active sessions through `MediaSessionManager`.
  `ListenerRestore` adds it to `enabled_notification_listeners`, as `AccessibilityRestore` does for the remote.
- `launcher/Installs.kt`: `PackageInstaller.SessionCallback` for the progress tile.
- `launcher/Groups.kt`: category to group, with user overrides in DataStore.
- `launcher/TvContent.kt`: `TvContract` reads behind `READ_TV_LISTINGS`.
- `launcher/ui/HomeScreen.kt`: rows in this order: shortcuts, now playing, favorites, Telik cards, recent, apps with the group filter.

## Pure logic with unit tests

- Order of favorites and recent apps, default favorites from the launch record.
- "New" and "Updated" badges from install times and the launch record.
- Merge of the listener list (the same rule as `AccessibilityRestore.merged`).
- Category to group.

## Performance

- Read media sessions, install sessions and `TvContract` off the main thread.
- Load the rows after the first frame, so the 1 second budget of 001 holds.
- Stop the listeners when the Home screen is not visible (FR-013 of 001).

## Risks

- Google TV apps may publish nothing to `TvContract`. Then story 5 is dropped, and the spec records the result.
- Some apps do not publish artwork in their media session. The card shows the app banner then.
- A notification listener can read all notifications. Telik uses only media sessions and writes no titles to the log (FR-012).
