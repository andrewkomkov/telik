# Tasks: Drop fp-setup

- [x] T001 Check type 103 and bit 40 in the code. Result: they are not there (see `spec.md`).
- [x] T002 Add `0004-drop-fp-setup.patch`. It applies to UxPlay with `git apply --unidiff-zero`.
- [ ] T003 Build the debug APK with the patch.
- [ ] T004 Install on the TV (state is not PLAYING) and start mirroring from an iPhone. The user does the iPhone step.
- [ ] T005 On a pass: remove `raop_handler_fpsetup` and `fairplay_playfair.c`, update `docs/AIRPLAY2.md`.
