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

## Results

Stages 0 and 1 passed on 2026-10-31 with an iPhone on sender version 1005.13.1.

The probe advertises version 366.0, sets bit 48 and clears bit 27. The iPhone then sends these requests:

| Request | `X-Apple-HKP` | Body |
| --- | --- | --- |
| `GET /info` | none | Binary plist with the key `qualifier` |
| `POST /pair-pin-start` | 4 | Empty |
| `POST /pair-setup` | 4 | 9 bytes that start with `00 01 00` |

The first three bytes are the TLV8 method item for pair setup. The legacy handler accepts only a 32-byte body, so it rejects this request. The iPhone then asks for the code again.

The 9-byte body is `00 01 00 06 01 01 13 01 10`: method, state and the transient flag `0x10`.

Stage 2 passed on the TV on 2026-10-31 with a `pyatv` client, `check_transient.py`. The client finished transient pairing and read `/info` on the encrypted channel. An iPhone and an iMac then finished the same pairing without a code.

The code for stage 2 is on the branch `airplay2-probe`. It uses `pair_ap` and OpenSSL. It does not add libsodium.

Stage 3 passed on 2026-10-31 with an iPhone 18,4 on iOS 27.2. The screen showed video. The log shows 1801 decrypted frames in 34 seconds and no decryption errors. The sender was on the new channel for the full test.

After transient pairing, the iPhone uses this sequence. `POST /fp-setup` comes first, two times. The first `SETUP` has no `ekey` and no `eiv`. The `RECORD` request comes before the stream `SETUP`.

Video uses ChaCha20-Poly1305:

| Item | Value |
| --- | --- |
| Key | HKDF-SHA512 of the 64-byte pairing secret |
| Salt | `DataStream-Salt` and the decimal `streamConnectionID` |
| Info | `DataStream-Output-Encryption-Key` |
| Nonce | 4 zero bytes and a 64-bit little-endian frame count |
| Additional data | The 128-byte frame header |
| Tag | The last 16 bytes of the payload |

## Known limits

- The iPhone still calls `fp-setup`. The video key does not use its result.
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
