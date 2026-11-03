# Feature Specification: Apple Watch remote connects on the first try

**Feature Branch**: `fix/watch-remote`
**Created**: 2026-11-03
**Status**: Partly done. The keys and the iPhone remote work. The watch still needs up to three tries.
**Input**: The user says that the Apple Watch remote again does not connect on the first try. The iPhone remote connects.

## Findings

- A connection that sends `_systemInfo` with the high bit set in `xid` gets 1 second to send `_sessionStart`.
  After that time, Telik closes it with `sessionless_timeout`.
- Background probes of the iPhone use the same high bit. The iPhone opens its real remote connection only after the TV closes the probe.
- Commit `7fa2aa8` set the time to 30 seconds for the watch. Then the iPhone remote reconnected slowly.
  Commit `c92a772` set 1 second again, and the watch failed again.
- 2026-11-03 14:14: the remote connected, but no key worked. `KeyDispatcher` had no channel. The accessibility service was off (`enabled_accessibility_services=null`).
  The hardware channel is blocked by SELinux, and the adb channel is a stub. The cause of the switch-off is not known.
- `_systemInfo` has the device model in its content. On 2026-11-03 the log showed `Watch7,12` for the watch and `iPhone18,4` for the iPhone.
- 2026-11-03 14:16:37: the watch opened two connections 150 ms apart. A new connection from the same IP aborted the first one (`superseded`).
  The watch closed the second one after 0.1 seconds, so the reconnect failed.
- 2026-11-03 14:37:32: the watch also sends background probes. A probe of the watch stayed open for 30 seconds, and the reconnect waited for it.
  The real watch sessions started 10 ms, 18 ms and 1.7 seconds after `_systemInfo`.

## Requirements

- **FR-001**: A background probe from an Apple Watch (model starts with `Watch`) gets 3 seconds to start its session.
- **FR-002**: A background probe from another device keeps the time of 1 second, so the iPhone remote stays fast.
- **FR-003**: The event log shows the device model of `_systemInfo`. A model is a name, so the log holds no private data.
- **FR-005**: A new connection aborts an unestablished connection from the same IP only when that connection is older than 2 seconds.
- **FR-004**: When the remote starts or a client connects, Telik adds its accessibility service to the enabled list. Other services stay in the list.

## Success Criteria

- **SC-001**: The watch remote connects on the first try, and a reconnect takes not more than about 3 seconds.
- **SC-002**: The iPhone remote reconnects as fast as before.
- **SC-003**: The remote controls the TV after an update of Telik.
