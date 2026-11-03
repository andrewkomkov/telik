# Tasks: AirPlay video from apps

- [x] T001 Read `debug.telik.loglevel` in `app/src/main/cpp/native_bridge.cpp`. A level above 6 gives errors only, because level 7 crashes UxPlay.
- [x] T002 Build the signed APK and install it on the TV (state is not PLAYING). Set the level. CI run 41637171171 and level 6 give the log.
- [x] T003 Repeat the YouTube test with the user. Find the failed step in the log. Telik cannot handle the legacy `/pair-verify` and `/pair-setup`.
- [x] T004 Fix the cause. `0004-legacy-pairing-for-video.patch` sends `/pair-setup` and `/pair-verify` without `X-Apple-HKP` to the UxPlay handlers.
  The patch has context lines. Its reverse check passes only after the patch applies.
- [x] T005 Check SC-001, SC-002, SC-003 and SC-004 on the TV with the user. Result: YouTube, mirroring and audio worked on 2026-11-03, with no code.
