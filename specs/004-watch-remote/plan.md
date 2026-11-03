# Plan: Apple Watch remote connects on the first try

1. Move the choice of the time into the function `sessionless_timeout(xid, model)` in `remote/crates/atv-core/src/session.rs`.
2. Give 30 seconds to a background probe when the model starts with `Watch`. Keep 1 second for other probes and 2.5 seconds for other connections.
3. Add unit tests for the function.
4. Log the device model of `_systemInfo` in `RemoteEngine.kt`.
5. Check on the TV with the watch and the iPhone. If the model in the log does not start with `Watch`, change the rule to the real model.
