# Plan: AirPlay video from apps

1. Read the native log level from the system property `debug.telik.loglevel` in `native_bridge.cpp`.
   Use `LOGGER_ERR` when the property is empty or not a number from 0 to 8.
2. Build a signed APK on top of the launcher branch and install it. Set the level to 7.
3. Repeat the YouTube test.
4. Find the failed step in the log.
5. Fix the cause.
6. Find the change that broke it with the git history. Candidates: `fbc2262` (legacy pairing removed) and the UxPlay patch `0002-airplay2.patch`.
