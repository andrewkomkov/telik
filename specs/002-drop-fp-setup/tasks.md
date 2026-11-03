# Tasks: Drop fp-setup

- [x] T001 Check type 103 and bit 40 in the code. Result: they are not there (see `spec.md`).
- [x] T002 Add `0004-drop-fp-setup.patch`. It applies to UxPlay with `git apply --unidiff-zero`. The first version had no context lines, and `applyUxplayPatches` skipped it. Commit 213ba72 adds context.
- [x] T003 Build the APK with the patch. Signed CI run 41633051915 on `test/launcher-fp-setup`. The `/fp-setup` route is not in `libairplay_native.so`.
- [ ] T004 Install on the TV (state is not PLAYING) and start mirroring from an iPhone. The user does the iPhone step. The APK is on the TV since 2026-11-03 13:14. The iPhone step is open.
- [ ] T005 On a pass: remove `raop_handler_fpsetup` and `fairplay_playfair.c`, update `docs/AIRPLAY2.md`.
