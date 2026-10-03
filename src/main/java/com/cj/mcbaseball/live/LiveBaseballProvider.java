package com.cj.mcbaseball.live;

import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The only thing the rest of the mod knows about where real baseball data comes from.
 * Swap the implementation (another API, recorded files for testing...) without touching the
 * browser, the session, or the recreation code.
 *
 * <p>Contract for implementations:
 * <ul>
 *   <li>Every method is non-blocking and returns immediately; work happens on the provider's own threads.</li>
 *   <li>Futures complete exceptionally with {@link com.cj.mcbaseball.live.net.LiveDataException} on failure;
 *       they never throw from the calling thread.</li>
 *   <li>Futures may complete on any thread. Callers hop back to the server thread before touching the world.</li>
 *   <li>Missing or odd fields in the source data produce defaults, never exceptions.</li>
 * </ul>
 *
 * <p>One live-feed request returns everything about a game at once (score, line score, count,
 * current at-bat, runners, players, lineups), so {@link #getLiveGameState} delivers all of it in a
 * single {@link LiveGameState} rather than separate calls that would each hit the network.
 * Phase 3 adds play-by-play events.
 */
public interface LiveBaseballProvider {

    /** Stable id used in configs and recordings, e.g. "mlb_stats_api". */
    String id();

    /** Shown to players, e.g. "MLB Stats API". */
    String displayName();

    /** All games for a baseball date, in display order (live, upcoming by start time, final). */
    CompletableFuture<List<LiveGameSummary>> getGamesForDate(LocalDate date);

    /** Fresh schedule-level info for one game; empty if the provider doesn't know the game. */
    CompletableFuture<Optional<LiveGameSummary>> getGameInfo(long gameId);

    /** Current state of one game from its live feed (score, inning, count, batter, pitcher, runners, lineups). */
    CompletableFuture<LiveGameState> getLiveGameState(long gameId);

    /** Releases threads/connections. Pending futures complete exceptionally with CANCELLED. */
    void close();
}
