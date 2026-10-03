# Recorded live feed: ATL @ LAD, 2026-10-03 (gamePk 849828), bottom of the 4th

Consecutive `/api/v1.1/game/849828/feed/live` responses captured while the game was
live, polled every ~30s and saved only when `metaData.timeStamp` changed. File name =
that timestamp (UTC). Gzipped JSON.

Test data for Phase 3+ (new-play detection without duplicates, event queue, recreation).
Events in this window, in order:

| first snapshot      | event                                   |
|---------------------|-----------------------------------------|
| 20261003_210650     | Pitching substitution (new LAD inning)  |
| 20261003_210812     | Flyout (1 out)                          |
| 20261003_211045     | **Home run, 2 runs** (LAD 2, ATL 0)     |
| 20261003_211159     | Single                                  |
| 20261003_211213     | Mound visit                             |
| 20261003_211402     | Pitching substitution (mid at-bat)      |
| 20261003_211803     | **Stolen base 2B**                      |
| 20261003_211836     | Lineout, 3 outs                         |

Things these show about the live feed:
- `currentPlay.playEvents` grows pitch by pitch; each pitch has a stable `playId` UUID.
- Non-pitch actions (mound visit, substitution, stolen base) appear as `playEvents` with
  `type: "action"` inside the at-bat that is in progress.
- `allPlays` gains a new entry when an at-bat starts, before it has a result.
- Only pitches carry a `playId`. Actions (stolen base, mound visit, substitution) have none,
  so dedupe them by (`atBatIndex`, event `index`).
- **Gotcha:** while an at-bat is still in progress, `currentPlay.result.eventType` can hold a
  mid-at-bat action (e.g. `stolen_base_2b` in 20261003_211803 with `about.isComplete: false`).
  Never treat `result` as the at-bat outcome until `about.isComplete` is true.

Source: MLB Stats API (statsapi.mlb.com). Test fixture only; not shipped in the mod jar.
