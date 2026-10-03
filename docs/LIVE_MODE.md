# Live Real-World Baseball Mode

Watch a real MLB game recreated by the mod's NPCs in your Minecraft stadium.
The real game decides every outcome; the mod's existing pitching, batting, fielding and
running systems make it look right.

**Status: Phase 1 of 7 done.** Field Controller → **Watch Live Game** opens a graphical
browser of today's real games (LIVE / UPCOMING / FINAL) with scores, inning, outs, count,
first-pitch time, line score and connection status. The stadium recreation behind
**WATCH LIVE** / **WAIT FOR GAME** comes in Phase 2+ (those buttons are visible but disabled).

## How it fits together

```
 MLB Stats API ──HTTPS──▶ LiveApiClient (async, timeouts, retry/backoff, gzip, cache, coalescing)
                               │
                     MlbStatsApiProvider  implements LiveBaseballProvider   ◀─ swap the data source here
                               │  (MlbScheduleParser, MlbStatusMapper → provider-neutral models)
                               ▼
                     LiveScheduleService (server thread; shared cache + polling policy)
                               │  LiveScheduleSyncPacket (only when the snapshot changed)
                               ▼
 client: ClientLiveCache ─▶ LiveGameBrowserScreen / LiveGameDetailScreen
              ▲
              └── LiveBrowserRequestPacket every 5s while a live screen is open ("I have version N")
```

* **Only the server talks to the internet.** In singleplayer that's the integrated server; in
  multiplayer only the dedicated server. Clients never make HTTP requests (verified with thread
  dumps in a real client + dedicated server session).
* **Never blocks a game thread.** All I/O runs on two daemon threads (`MCBaseball-LiveData-IO-*`);
  retries are scheduled, not slept. Results are handed to the server thread with
  `server.execute(...)`, guarded by a generation counter so late callbacks after shutdown are dropped.
* **Demand-driven polling.** Nothing is fetched unless someone has a live screen open. However many
  players are looking, the API sees at most one request per date per refresh window:
  15s while any game is live, 60s when games are still to come, 5 min when all are final.
  Manual Refresh is limited to once per 5s.
* **Failure handling.** Timeouts, refused connections, HTTP 5xx/429, invalid JSON: quick retries
  inside the client (1s, 2s, honouring `Retry-After`), then service-level backoff 5s → 10s → 20s → …
  → 2 min (≥30s when rate limited). The UI shows `● RECONNECTING` (old data kept) or
  `LIVE DATA TEMPORARILY UNAVAILABLE`, with a retry countdown. One log line per outage, not one
  per attempt.
* **Clean shutdown.** Leaving a singleplayer world or stopping a server cancels pending requests,
  stops the threads and clears callbacks (`ServerStoppingEvent` → `LiveBaseballManager.shutdown()`).

## Code map (`com.cj.mcbaseball`)

| Package / class | Role |
|---|---|
| `live.LiveBaseballProvider` | Data-source interface. Phase 1: `getGamesForDate`, `getGameInfo`. Phase 2 adds live feed methods. |
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
| `network.LiveBrowserRequestPacket` / `LiveScheduleSyncPacket` | Client ↔ server (protocol bumped to 2). |
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
| `debugRecording` | `false` | DEV: save raw responses to `<server>/mcbaseball-live-recordings/`. |
| `debugRecordingMaxFiles` | `2000` | DEV: cap per server run. |

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

## Testing

* `./gradlew build`: compiles, runs 48 unit tests (parser and status mapper on real recorded
  responses, malformed JSON, HTTP client against a misbehaving local server, schedule service
  timing/backoff/shutdown, packet round-trips, dates), builds the reobfuscated jar.
* `./gradlew test -Dmcbaseball.liveTests=true --tests '*RealMlbApiSmokeTest'`: hits the real API.
* `./gradlew runGameTestServer`: the mod's original 21 GameTests (including a full 9-inning NPC game).
* Fixtures in `src/test/resources/live/mlb/` are real MLB responses (test-only, not in the jar).

## Roadmap

1. ✅ Live data networking, today's games, Watch Live Game browser.
2. Select a game → live feed → score / inning / count / outs / batter / pitcher / runners in a HUD.
3. `LiveBaseballSession`: detect new pitches and plays without duplicates; event queue; join mid-game.
4. Real pitches → Minecraft pitcher/batter NPCs (`PitchCoordinateMapper`, pitch-type mapping).
5. Basic outcomes (balls, strikes, walks, strikeouts, hits, outs, home runs).
6. Runners and fielding detail, double plays, steals, errors, sacrifices, substitutions.
7. Broadcast cameras, presentation, crowd.
