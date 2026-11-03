# Feature Specification: AirPlay video from apps

**Feature Branch**: `fix/airplay-video`
**Created**: 2026-11-03
**Status**: The fix is written. The TV check is open.
**Input**: The user played a YouTube video on the iPhone during mirroring. Nothing plays on the TV. The user says that this worked before.

## Findings

- 2026-11-03 13:33:39, build 2.1.0: the iPhone stops the mirror stream and opens a second connection two times.
  Each connection closes after 20 to 40 ms. Telik gets no `/play` request. The first connection stays open.
- The release build logs only native errors, so the log does not show the failed step.
- Telik advertises AirPlay video (feature bits 0 and 4).
- The cause comes from the log at level 6 on 2026-11-03 13:55. The video connection sends `POST /pair-verify` and then `POST /pair-setup`.
  These requests have no `X-Apple-HKP` header, so they use the legacy pairing.
  Commit `fbc2262` removed their routes in Telik 2.0. Telik replies that it cannot handle them, and the iPhone closes the connection.
- At level 7, UxPlay prints the binary body of `/pair-setup` as a plist and crashes. Level 6 is the highest safe level.

## Requirements

- **FR-001**: A video that an iPhone app sends over AirPlay (for example YouTube) plays on the TV.
- **FR-002**: The user can turn on the native log with `adb shell setprop debug.telik.loglevel 6` and a restart of Telik.
  The default level stays at errors only, so the log holds no URLs.

## Success Criteria

- **SC-001**: A YouTube video starts on the TV from the AirPlay button in the YouTube app on the iPhone.
- **SC-002**: The same video starts when the user opens YouTube during mirroring.
- **SC-003**: Mirroring and audio still work.
- **SC-004**: The sender shows no code. Telik keeps the legacy routes without the PIN routes.
