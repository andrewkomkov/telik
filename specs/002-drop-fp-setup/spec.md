# Feature Specification: Drop fp-setup

**Feature Branch**: `002-drop-fp-setup`
**Created**: 2026-11-03
**Status**: Closed. The iPhone test failed on 2026-11-03, and the patch is removed.
**Input**: The user said to remove `fp-setup` and buffered audio (type 103) from the open items of Telik 2.0.

## Findings

- Type 103 and bit 40 came later in PR #17, behind a setting. Before PR #17, `android_dnssd_shim.c` set only bit 48 and cleared bit 27.
  There is nothing to remove for type 103. `docs/AIRPLAY2.md` stage 4 stays as a note.
- `POST /fp-setup` is still routed to `raop_handler_fpsetup` in `lib/raop.c`. The video key does not use its result.

## Requirements

- **FR-001**: Telik does not route `POST /fp-setup`. The request gets the default response for an unknown URL.
- **FR-002**: Mirroring from an iPhone and audio still start without `fp-setup`.

## Success Criteria

- **SC-001**: An iPhone on iOS 27 starts mirroring to the TV with the new build, with no code.
- **SC-002**: If SC-001 fails, the patch is reverted and `docs/AIRPLAY2.md` records the failure.
