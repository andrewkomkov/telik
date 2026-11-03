# Tasks: Apple Watch remote connects on the first try

- [ ] T001 Add `sessionless_timeout(xid, model)` with the watch rule in `remote/crates/atv-core/src/session.rs`.
- [ ] T002 Add unit tests for `sessionless_timeout`. CI runs them in the step "Test Remote protocol".
- [ ] T003 Log the device model of `_systemInfo` in `app/src/main/kotlin/io/github/henry753951/airplayremote/remote/RemoteEngine.kt`.
- [ ] T004 Check on the TV: the watch connects on the first try, and the iPhone remote reconnects fast. Read the model in the log.
