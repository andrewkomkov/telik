# AirPlay 2 plan for Telik 2.0

Telik 1.x mirrors through the UxPlay legacy protocol. Telik 2.0 moves to the modern AirPlay 2 pairing
and channel. Legacy stays as a runtime fallback until the new path works.

Protected video stays out of scope. Telik does not decrypt DRM.

## Stages

Each stage has one test on a real iPhone. A failed test stops the next stage.

| Stage | Change | Pass condition |
| --- | --- | --- |
| 0 | Log the iOS `/info` and `SETUP` plists | Logs exist for the current iOS |
| 1 | Set feature bit 48, clear bit 27, raise `GLOBAL_VERSION` to 366 in a test build | iOS sends `X-Apple-HKP: 4` |
| 2 | Add transient pairing and the encrypted RTSP channel | The next request decrypts to a valid plist |
| 3 | Run mirroring (`SETUP` type 110) on the new channel | H.264 or H.265 video decodes |
| 4 | Add buffered audio (type 103, bit 40) without PTP | Audio plays and drift is measured |
| 5 | Add PTP only if iOS requires it | iOS accepts the timing peer |
| 6 | Study types 120 and 130 | Decide after stage 5 |

## Known limits

- Every open type 110 path found so far still uses `ekey` and `fp-setup`.
- Nothing public describes how iOS at sender version 980 selects a protocol.
- `combinedGetInfoWithControlSetup` has no public description.
- PTP uses ports 319 and 320. An Android app may not be able to bind them.

## References

- [AirPlay spec](https://openairplay.github.io/airplay-spec/)
- [AirPlay 2 notes](https://emanuelecozzi.net/docs/airplay2/)
- [rairplay](https://github.com/r4v3n6101/rairplay)
- [shairport-sync](https://github.com/mikebrady/shairport-sync/blob/master/AIRPLAY2.md)
- [pair_ap](https://github.com/ejurgensen/pair_ap)
- [UxPlay issue 535](https://github.com/FDH2/UxPlay/issues/535)
