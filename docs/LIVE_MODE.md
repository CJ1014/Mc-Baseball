# Live Real-World Baseball Mode

Watch a real MLB game recreated by the mod's NPCs in your Minecraft stadium.
The real game decides every outcome; the mod's existing pitching, batting, fielding and
running systems make it look right.

**Status: Phases 1-3 of 7 done.**

* **Phase 1:** Field Controller → **Watch Live Game** opens a graphical browser of today's real
  games (LIVE / UPCOMING / FINAL) with scores, inning, outs, count, first-pitch time, line score
  and connection status.
* **Phase 2:** **WATCH LIVE** (or **WAIT FOR GAME** for an upcoming game) makes that Field
  Controller follow the game. Everyone within the broadcast radius of the field gets a live
  scoreboard HUD: score, inning and half, balls/strikes/outs, runners on the diamond, current
  batter and pitcher (name, number, handedness), last pitch (call, mph, type), last play, and
  `● LIVE` / `● RECONNECTING` / `LIVE DATA TEMPORARILY UNAVAILABLE` with "Updated Ns ago".
  Joining mid-game syncs straight to the current state. The controller menu shows
  `LIVE: ATL @ LAD` and a **Stop** button.
* **Phase 3:** each field following a game runs a `LiveBaseballSession`: new pitches, actions
  (steals, substitutions, pickoffs...) and at-bat results are detected exactly once, queued in game
  order, and played at a watchable pace (catch-up up to 2x, never skipping events). The HUD shows a
  play-by-play ticker of what the recreation just played. Developer debug mode adds a **Live Debug**
  panel (real state vs. Minecraft state, queue, processed ids...) and **replay of recorded games**.
  No NPC recreation yet: Phase 4 plugs NPCs into the queue.

## How it fits together

```
 MLB Stats API ──HTTPS──▶ LiveApiClient (async, timeouts, retry/backoff, gzip, cache, coalescing)
                               │
                     MlbStatsApiProvider  implements LiveBaseballProvider   ◀─ swap the data source here
                               │  (MlbScheduleParser, MlbStatusMapper → provider-neutral models)
                               ▼
        ┌──────────────────────┴───────────────────────┐  (RoutingProvider: negative ids -> RecordedFeedProvider)
        ▼                                              ▼
 LiveScheduleService (browser)                  LiveWatchService (stadiums following a game)
   │ LiveScheduleSyncPacket on change              │ one feed per game, polled only while someone
   ▼                                               │ is near a stadium following it
 ClientLiveCache ─▶ browser / detail screens       │ LiveWatchSyncPacket to that stadium's audience
   ▲                                               ▼
   └ LiveBrowserRequestPacket every 5s       ClientLiveWatch ─▶ LiveGameHud
     while a live screen is open             LiveWatchActionPacket (start / stop) ◀─ detail screen, controller
```

* **Only the server talks to the internet.** In singleplayer that's the integrated server; in
  multiplayer only the dedicated server. Clients never make HTTP requests (verified with thread
  dumps in a real client + dedicated server session).
* **Never blocks a game thread.** All I/O runs on two daemon threads (`MCBaseball-LiveData-IO-*`);
  retries are scheduled, not slept. Results are handed to the server thread with
  `server.execute(...)`, guarded by a generation counter so late callbacks after shutdown are dropped.
* **Demand-driven polling.** Nothing is fetched unless someone is looking. Schedule: at most one
  request per date per window (15s with a live game, 60s otherwise, 5 min when all final; manual
  Refresh at most every 5s). Watched game feed: every 10s while live (never faster than the feed's
  own `wait` hint), 30s during delays, 20s-5min before first pitch depending on how close it is,
  never again once final. A feed is polled only while a player is within the broadcast radius of a
  field following it; several fields following one game share one request.
* **Failure handling.** Timeouts, refused connections, HTTP 5xx/429, invalid JSON: quick retries
  inside the client (1s, 2s, honouring `Retry-After`), then service-level backoff 5s → 10s → 20s → …
  → 2 min (≥30s when rate limited). The UI shows `● RECONNECTING` (old data kept) or
  `LIVE DATA TEMPORARILY UNAVAILABLE`, with a retry countdown. One log line per outage, not one
  per attempt.
* **Watches don't survive a server restart** (or leaving a singleplayer world); pick the game again.
* **Clean shutdown.** Leaving a singleplayer world or stopping a server cancels pending requests,
  stops the threads and clears callbacks (`ServerStoppingEvent` → `LiveBaseballManager.shutdown()`).

## Code map (`com.cj.mcbaseball`)

| Package / class | Role |
|---|---|
| `live.LiveBaseballProvider` | Data-source interface: `getGamesForDate`, `getGameInfo`, `getLiveGameState`. One feed request returns score, line score, count, at-bat, runners, players and lineups together, so they arrive as one `LiveGameState` rather than separate network calls. |
| `live.mlb.MlbLiveFeedParser` | Live feed JSON → `LiveGameState` (players, numbers, positions, batting order, last pitch, last play). |
| `live.LiveWatchService` | Which game each field follows; feed polling, sharing, audience gating, outage handling. Minecraft-agnostic, unit tested. |
| `client.hud.LiveGameHud` | The live scoreboard overlay + play-by-play ticker. |
| `live.mlb.MlbLiveFeedParser.parseFeed` | Also builds the play-by-play: `LivePlay` (at-bat) → `LivePlayEvent` (pitch/action, with `LivePitch` and Statcast `LiveHit` only when reported) and `LiveRunner` movements with fielder credits. |
| `live.session.LiveEventDetector` | Snapshot → new `LiveEvent`s exactly once (pitch `playId`, action at-bat+index, result at-bat). Join mid-game = sync without events. Corrections (`CallChanged`, `ResultChanged`) instead of duplicates. |
| `live.session.LiveEventQueue` | Ordered playback: one event at a time, catch-up ≤2x, downtime instant, in-play pitch waits for its result. |
| `live.session.RecreationState` | What Minecraft has shown so far (count, outs, score, runners by base). |
| `live.session.LiveBaseballSession` | Detector + queue + recreation state for one field. |
| `live.recorded.*` | Developer test mode: `RecordedGames` finds saved feed sequences, `RecordedFeedProvider` replays them, `RoutingProvider` sends negative ids there. |
| `client.screen.live.LiveDebugScreen` | Developer panel. |
| `live.mlb.MlbStatsApiProvider` | MLB Stats API implementation. |
| `live.mlb.MlbScheduleParser` | Schedule JSON → `LiveGameSummary`; never throws on missing/odd fields. |
| `live.mlb.MlbStatusMapper` | All ~230 MLB status codes → `LiveGameStatus.State`. |
| `live.model.*` | Provider-neutral records: `LiveGameSummary`, `LiveTeam`, `LiveGameStatus`, `LiveSchedule`, … No Minecraft imports. |
| `live.net.LiveApiClient` | Async HTTP: timeouts, retries, backoff, gzip, cache, in-flight coalescing. |
| `live.net.ResponseRecorder` | Dev: save raw responses for bug reproduction (off by default, capped). |
| `live.LiveScheduleService` | Shared schedule cache + polling/backoff rules. Minecraft-agnostic, fully unit tested. |
| `live.LivePollingPolicy` | All refresh/backoff numbers in one place. |
| `live.LiveDates` | "Today" for baseball = US Eastern date, rolling over at 5 AM ET. |
| `live.LiveBaseballManager` | Per-server owner of threads, client, provider, service. |
| `network.LiveBrowserRequestPacket` / `LiveScheduleSyncPacket` | Browser data (client ↔ server). |
| `network.LiveWatchActionPacket` / `LiveWatchSyncPacket` | Start/stop following a game; HUD state to nearby players. Protocol version 4. |
| `client.screen.live.*` | Browser and detail screens. |

## Server config (`serverconfig/mcbaseball-server.toml`, section `[live]`)

| Key | Default | |
|---|---|---|
| `enabled` | `true` | Allow browsing/watching real games. |
| `apiBaseUrl` | `https://statsapi.mlb.com` | Change only for a mirror/proxy. |
| `httpTimeoutSeconds` | `10` | Per request. |
| `httpRetries` | `2` | Quick retries before reporting a failure. |
| `scheduleRefreshLiveSeconds` | `15` | While any game is live. |
| `scheduleRefreshIdleSeconds` | `60` | When no game is live yet. |
| `feedRefreshSeconds` | `10` | Watched game's feed while live (never faster than the feed's own hint). |
| `debugRecording` | `false` | DEV: save raw responses to `<server>/mcbaseball-live-recordings/`. |
| `debugRecordingMaxFiles` | `2000` | DEV: cap per server run. |
| `debugMode` | `false` | DEV: Live Debug panel on following fields; recorded games listed under RECORDED (DEV) in today's browser. |

## MLB Stats API facts (verified against real responses, 2026-10-03)

* Schedule: `/api/v1/schedule?sportId=1&date=YYYY-MM-DD&hydrate=linescore,team`. Without
  `hydrate=team` there are no team abbreviations. CDN cache `max-age=20`.
* **Live feed is `/api/v1.1/game/{gamePk}/feed/live`. `/api/v1/...` returns 404.**
  `metaData.wait` (10s) is the server's suggested poll interval.
* **Postponed and cancelled games report `abstractGameState: "Final"`** (coded `D`/`C`). Warmup
  (`PW`) is abstract `"Live"`. "Scheduled: COVID-19" reuses coded state `T`, which otherwise means
  Suspended. `MlbStatusMapper` handles every code in `/api/v1/gameStatus`.
* Pre-game games carry a linescore that says "Top 1st, 0-0". Don't show it as a score.
* An inning in progress omits `runs` for the side that hasn't batted.
* Batting order arrives as `"300"` (slot 3, starter), `"301"` (first sub in slot 3).
* **Between at-bats** (`currentPlay.about.isComplete` mid-inning) the linescore still names the
  batter who just finished, often already standing on base, with his final count. The next batter
  is `offense.onDeck`. **At inning breaks** ("Middle"/"End") the linescore has already flipped to
  the next half's batter and pitcher. `MlbLiveFeedParser` handles both; they're covered by recorded tests.
* Each pitch has a stable `playId` UUID; actions (steals, mound visits, subs) have none.
  `currentPlay.result` can hold a mid-at-bat action before the at-bat is complete.
  `gameData.absChallenges` exists: **pitch calls can be overturned after the fact**.
  (See `src/test/resources/live/mlb/recorded/849828_atl-lad_bot4/README.md`.)

## Existing systems Live Mode will reuse (Phase 4+)

| Need | Existing system |
|---|---|
| Pitch of a given type/speed to a given spot | `PitchingSystem.releasePitch(game, slot, pitcher, PitchType, Vec3 target, quality, velocityRating, stuffRating)` + `AimSolver.solve(...)` |
| Pitch types | `PitchType` = FOUR_SEAM, TWO_SEAM, CHANGEUP, CURVEBALL, SLIDER, SINKER. No cutter/splitter yet: map FC/FS/KC/ST/SV etc. to the nearest type. |
| Forced batted-ball result | `BattingSystem.PendingContact(applyTick, point, velocity, spin, exitMph, launchDeg, …)` + `applyPending` |
| Ball flight, bounces, home-run carry | `BallPhysics` (drag, Magnus, surfaces), `BaseballEntity` |
| Fielders chasing / catching / throwing | `NpcDirector` (`chaser()`, `coverer(base)`), `InterceptPlanner`, `FieldingSystem.npcTryCatch/npcThrow` |
| NPC bodies, animations, uniforms, jersey numbers | `BaseballPlayerEntity` (`setup`, `dress`, `moveTo`, `setAnim`, `holdBat/Glove/Ball`, `startSlide`), `NpcAnim`, `ThrowAnimPacket`, `JerseyNumberLayer` |
| Real names / numbers / handedness | `NpcProfile(name, number, batsRight, throwsRight, …)` |
| Positions | `Position` P C 1B 2B 3B SS LF CF RF (DH bats only) |
| Field geometry, spots, home-run fence | `FieldGeometry`, `FieldLayout` |
| Scoreboard + HUD + messages | `ScoreboardBlockEntity`, `GameHudPacket`/`GameHud`, `GameBroadcaster` |

## Developer test mode (recorded games)

1. Set `debugMode = true` (and optionally `debugRecording = true` to capture games yourself) in
   `serverconfig/mcbaseball-server.toml`.
2. Put recordings in `<server folder>/mcbaseball-live-recordings/`, either the debug recorder's own
   files (`<date>/<time>_feed_<gamePk>.json`) or a folder named `<gamePk>_<anything>/` with
   time-stamped `.json` / `.json.gz` snapshots (like `src/test/resources/live/mlb/recorded/`).
3. Today's browser lists them under **RECORDED (DEV)** → **Replay**. Each poll serves the next snapshot,
   through exactly the same detector / queue / HUD path as a live game. The HUD says `● RECORDED`.

## Testing

* `./gradlew build`: compiles, runs 86 unit tests (schedule and live-feed parsers on real recorded
  responses, malformed JSON, HTTP client against a misbehaving local server, schedule and watch
  service timing/backoff/sharing/audience/shutdown, packet round-trips, dates; Phase 3: every real
  event of a recorded inning exactly once and in order, joining at every snapshot never replays,
  corrections, queue pacing/catch-up, and the recreation state matching the real game after every
  one of 20 real polls), builds the jar.
* `./gradlew test -Dmcbaseball.liveTests=true --tests '*RealMlbApiSmokeTest'`: hits the real API.
* `./gradlew runGameTestServer`: the mod's original 21 GameTests (including a full 9-inning NPC game).
* Fixtures in `src/test/resources/live/mlb/` are real MLB responses (test-only, not in the jar).

## Roadmap

1. ✅ Live data networking, today's games, Watch Live Game browser.
2. ✅ Select a game → live feed → score / inning / count / outs / batter / pitcher / runners in a HUD.
3. ✅ `LiveBaseballSession`: detect new pitches and plays without duplicates; event queue; join mid-game.
4. Real pitches → Minecraft pitcher/batter NPCs (`PitchCoordinateMapper`, pitch-type mapping).
5. Basic outcomes (balls, strikes, walks, strikeouts, hits, outs, home runs).
6. Runners and fielding detail, double plays, steals, errors, sacrifices, substitutions.
7. Broadcast cameras, presentation, crowd.
