# Tasks: Apple Watch remote connects on the first try

- [x] T001 Add `sessionless_timeout(xid, model)` with the watch rule in `remote/crates/atv-core/src/session.rs`.
- [x] T002 Add unit tests for `sessionless_timeout`. CI runs them in the step "Test Remote protocol".
- [x] T003 Log the device model of `_systemInfo` in `app/src/main/kotlin/io/github/henry753951/airplayremote/remote/RemoteEngine.kt`.
- [ ] T004 Check on the TV: the watch connects on the first try, and the iPhone remote reconnects fast. Read the model in the log.
- [x] T005 Add `AccessibilityRestore` and call it when the remote starts and when a client connects.
- [x] T006 Add unit tests for the merge of the enabled list.
- [x] T007 Check on the TV: turn the service off with adb, connect the remote, and check that the keys work. The service came back after a restart on 2026-11-03 14:28. The user confirmed the keys at 14:38.
- [ ] T009 Change the watch time from 30 to 3 seconds, because the watch also sends probes. Check the reconnect of the watch on the TV.
- [x] T008 Abort only stale connections (older than 2 seconds) in `ActiveConnectionTracker::register`. Add a unit test for two parallel connections.
