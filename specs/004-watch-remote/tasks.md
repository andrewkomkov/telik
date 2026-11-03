# Tasks: Apple Watch remote connects on the first try

- [ ] T001 Add `sessionless_timeout(xid, model)` with the watch rule in `remote/crates/atv-core/src/session.rs`.
- [ ] T002 Add unit tests for `sessionless_timeout`. CI runs them in the step "Test Remote protocol".
- [ ] T003 Log the device model of `_systemInfo` in `app/src/main/kotlin/io/github/henry753951/airplayremote/remote/RemoteEngine.kt`.
- [ ] T004 Check on the TV: the watch connects on the first try, and the iPhone remote reconnects fast. Read the model in the log.
- [ ] T005 Add `AccessibilityRestore` and call it when the remote starts and when a client connects.
- [ ] T006 Add unit tests for the merge of the enabled list.
- [ ] T007 Check on the TV: turn the service off with adb, connect the remote, and check that the keys work.
