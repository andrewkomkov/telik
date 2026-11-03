# Feature Specification: Apple Watch remote connects on the first try

**Feature Branch**: `fix/watch-remote`
**Created**: 2026-11-03
**Status**: The fix is written. The TV check is open.
**Input**: The user says that the Apple Watch remote again does not connect on the first try. The iPhone remote connects.

## Findings

- A connection that sends `_systemInfo` with the high bit set in `xid` gets 1 second to send `_sessionStart`.
  After that time, Telik closes it with `sessionless_timeout`.
- Background probes of the iPhone use the same high bit. The iPhone opens its real remote connection only after the TV closes the probe.
- Commit `7fa2aa8` set the time to 30 seconds for the watch. Then the iPhone remote reconnected slowly.
  Commit `c92a772` set 1 second again, and the watch failed again.
- `_systemInfo` has the device model in its content. The event log shows only message names, so the model of the watch is not known yet.

## Requirements

- **FR-001**: A connection from an Apple Watch (model starts with `Watch`) gets 30 seconds to start its session.
- **FR-002**: A background probe from another device keeps the time of 1 second, so the iPhone remote stays fast.
- **FR-003**: The event log shows the device model of `_systemInfo`. A model is a name, so the log holds no private data.

## Success Criteria

- **SC-001**: The watch remote connects on the first try.
- **SC-002**: The iPhone remote reconnects as fast as before.
