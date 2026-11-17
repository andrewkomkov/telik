# Research: Now playing in the iPhone remote

Research of 2026-11-17. Sources: a decompile of TVRemoteCore of iOS 26.1 (`EthanArbuckle/iPhone18-3_26.1_23B85_Restore`,
`TVRCRPCompanionLinkClientWrapper.mm`, `TVRCNowPlayingInfo.mm`, `TVRCNowPlayingMetadata.mm`), pyatv issue 2461 (the
archives of a real Apple TV), and `henrikwidlund/ratv` (`crates/ratv-companion/src/now_playing.rs`).

## The card

- iOS registers for the event `NowPlayingInfo` (`_interest`), then sends `FetchCurrentNowPlayingInfoEvent`. It ignores
  the reply. It takes the card only from the pushed event.
- The event is `{_i: "NowPlayingInfo", _t: 1, _c: {NowPlayingInfoKey: <bplist>}}`, with no `_x`.
- The bplist is an NSKeyedArchiver archive of `TVRCNowPlayingInfo`: `identifier`, `playerIdentifier`, `playbackRate`,
  `playbackState` (1 play, 2 pause, 3 stop), `metadata`, `imageData` (JPEG), and flags.
- `metadata` is a `TVRCNowPlayingMetadata`: `title`, `episodeTitle`, `duration`, `timeOffset`, `timestamp`
  (seconds since 2001-01-01), `bundleID`, `canonicalID` and others. It has no artist key.
- A missing key makes iOS ignore the event with no message.

## Up Next and Top Shelf

- `FetchUpNextInfoEvent {PaginationTokenKey}`: the reply key and the item format are not known. Items use content IDs of
  the Apple TV app.
- `FetchCurrentTopShelfItemsEvent` is new in iOS 27. Its payload is a `TVRCTopShelfInfo` archive. The full format is
  not known.
- The iPhone of the user sends `FetchCurrentTopShelfItemsEvent`, so it runs iOS 27.
