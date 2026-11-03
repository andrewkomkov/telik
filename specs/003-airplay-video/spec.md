# Feature Specification: AirPlay video from apps

**Feature Branch**: `fix/airplay-video`
**Created**: 2026-11-03
**Status**: Draft. The cause is not known yet.
**Input**: The user played a YouTube video on the iPhone during mirroring. Nothing plays on the TV. The user says that this worked before.

## Findings

- 2026-11-03 13:33:39, build 2.1.0: the iPhone stops the mirror stream and opens a second connection two times.
  Each connection closes after 20 to 40 ms. Telik gets no `/play` request. The first connection stays open.
- The release build logs only native errors, so the log does not show the failed step.
- Telik advertises AirPlay video (feature bits 0 and 4).

## Requirements

- **FR-001**: A video that an iPhone app sends over AirPlay (for example YouTube) plays on the TV.
- **FR-002**: The user can turn on the native debug log with `adb shell setprop debug.telik.loglevel 7` and a restart of Telik.
  The default level stays at errors only, so the log holds no URLs.

## Success Criteria

- **SC-001**: A YouTube video starts on the TV from the AirPlay button in the YouTube app on the iPhone.
- **SC-002**: The same video starts when the user opens YouTube during mirroring.
- **SC-003**: Mirroring and audio still work.
