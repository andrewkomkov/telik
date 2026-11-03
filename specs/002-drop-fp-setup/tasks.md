# Tasks: Drop fp-setup

- [x] T001 Check type 103 and bit 40 in the code. Result: they are not there (see `spec.md`).
- [x] T002 Add `0004-drop-fp-setup.patch`. It applies to UxPlay with `git apply --unidiff-zero`. The first version had no context lines, and `applyUxplayPatches` skipped it. Commit 213ba72 adds context.
- [x] T003 Build the APK with the patch. Signed CI run 41633051915 on `test/launcher-fp-setup`. The `/fp-setup` route is not in `libairplay_native.so`.
- [x] T004 Install on the TV (state is not PLAYING) and start mirroring from an iPhone. The user does the iPhone step. Result on 2026-11-03: fail. The iPhone connects and disconnects after 0.2 seconds, two times. The TV is back on the 2.1.0 build without the patch.
- [ ] T005 On a pass: remove `raop_handler_fpsetup` and `fairplay_playfair.c`, update `docs/AIRPLAY2.md`. Not done, because the test failed.
- [x] T006 On a fail (SC-002): remove `0004-drop-fp-setup.patch` and record the failure in `docs/AIRPLAY2.md`.
