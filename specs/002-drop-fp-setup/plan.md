# Plan: Drop fp-setup

Add `app/src/main/cpp/patches/UxPlay/0004-drop-fp-setup.patch`. It removes the `/fp-setup` route from `lib/raop.c`.
The build applies the patch with the others. The handler function and `fairplay_playfair.c` stay until the test passes,
so a revert is one file. After a pass, remove them in a second change.
