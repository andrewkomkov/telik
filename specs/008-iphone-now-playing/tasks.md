# Tasks: Now playing in the iPhone remote

- [x] T001 Research the formats of the three requests (pyatv and other sources). Record them in `research.md`.
- [x] T002 Log the full request bodies of the three requests on the TV (keys only, no titles) and record them.
- [x] T010 [US1] Answer `FetchCurrentNowPlayingInfoEvent` and push the change of media. Check on the iPhone with a screenshot.
  - 2026-11-17: the iPhone log shows that iOS accepts the archive. `rpMRtID` ties the remote to the AirPlay route. The SkipForward and SkipBackward flags make iOS show its media panel.
- [ ] T011 [US1] Make the skip buttons seek. The commands arrive, but no seek happens. The next build logs their bodies.
- [ ] T020 [US2] Answer `FetchUpNextInfoEvent` and `FetchCurrentTopShelfItemsEvent`, if T001 or T002 gives a format.
