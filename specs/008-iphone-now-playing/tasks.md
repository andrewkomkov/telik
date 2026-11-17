# Tasks: Now playing in the iPhone remote

- [x] T001 Research the formats of the three requests (pyatv and other sources). Record them in `research.md`.
- [x] T002 Log the full request bodies of the three requests on the TV (keys only, no titles) and record them.
- [x] T010 [US1] Answer `FetchCurrentNowPlayingInfoEvent` and push the change of media. Check on the iPhone with a screenshot.
  - 2026-11-17: the iPhone log shows that iOS accepts the archive. `rpMRtID` ties the remote to the AirPlay route. The SkipForward and SkipBackward flags make iOS show its media panel.
- [x] T011 [US1] Make the skip buttons seek. The iOS 27 remote sends the seconds as `MessageKeySkipSeconds`. The user checked -10 and +10 on 2026-11-17.
- [ ] T012 [US1] Media buttons for an app with no media session (kino.watch): skip sends the arrows, play or pause sends OK. The code exists. The user did not check it on the TV yet.
- [x] T013 [US1] Report a text field to the iPhone only while the TV keyboard shows or the focused view is editable. The hidden editor of YouTube opened the iPhone keyboard each time the remote opened. The user checked on 2026-11-17 that the keyboard stays closed.
- [ ] T014 [US1] Check that the iPhone keyboard still opens in the YouTube search.
- [ ] T020 [US2] Answer `FetchUpNextInfoEvent` and `FetchCurrentTopShelfItemsEvent`, if T001 or T002 gives a format.
