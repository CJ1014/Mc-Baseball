package com.cj.mcbaseball.live.recorded;

import com.cj.mcbaseball.live.LiveBaseballProvider;
import com.cj.mcbaseball.live.mlb.MlbLiveFeedParser;
import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.net.LiveDataException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Replays recorded feed snapshots as if the game were live: each request returns the next snapshot,
 * then the last one forever. Recorded games use the <b>negative</b> gamePk as their id so they can
 * never be confused with the live game of the same id.
 */
public final class RecordedFeedProvider implements LiveBaseballProvider {

    private final Map<Long, List<Path>> recordings;
    private final Executor io;
    private final Map<Long, Integer> cursor = new HashMap<>();

    public RecordedFeedProvider(Map<Long, List<Path>> recordings, Executor io) {
        this.recordings = recordings;
        this.io = io;
    }

    public static long idFor(long gamePk) {
        return -Math.abs(gamePk);
    }

    @Override
    public String id() {
        return "recorded";
    }

    @Override
    public String displayName() {
        return "Recorded game";
    }

    /** Start this recording from its first snapshot again. */
    public synchronized void rewind(long gameId) {
        this.cursor.remove(-gameId);
    }

    @Override
    public CompletableFuture<LiveFeed> getLiveFeed(long gameId) {
        long pk = -gameId;
        List<Path> files = this.recordings.get(pk);
        if (gameId >= 0 || files == null || files.isEmpty()) {
            return CompletableFuture.failedFuture(new LiveDataException(LiveDataException.Kind.NOT_FOUND, "no recording for " + gameId));
        }
        Path file;
        synchronized (this) {
            int i = this.cursor.getOrDefault(pk, 0);
            file = files.get(Math.min(i, files.size() - 1));
            this.cursor.put(pk, Math.min(i + 1, files.size() - 1));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return MlbLiveFeedParser.parseFeed(RecordedGames.read(file), pk);
            } catch (IOException e) {
                throw new CompletionException(new LiveDataException(LiveDataException.Kind.INVALID_RESPONSE, "cannot read " + file.getFileName(), e));
            } catch (LiveDataException e) {
                throw new CompletionException(e);
            }
        }, this.io);
    }

    /** Browser entries for every recording (reads each first snapshot; call off the server thread). */
    public List<LiveGameSummary> summaries() {
        List<LiveGameSummary> out = new ArrayList<>();
        for (Map.Entry<Long, List<Path>> e : this.recordings.entrySet()) {
            try {
                LiveGameState s = MlbLiveFeedParser.parse(RecordedGames.read(e.getValue().get(0)), e.getKey());
                out.add(new LiveGameSummary(idFor(e.getKey()), "", s.away(), s.home(), s.status(), s.startEpochMillis(), false, s.awayScore(),
                    s.homeScore(), s.inning(), s.inningState(), s.balls(), s.strikes(), s.outs(), s.awayTotals(), s.homeTotals(), s.awayInningRuns(),
                    s.homeInningRuns(), s.venue(), "RECORDED - " + e.getValue().size() + " snapshots from " + s.inningLabel(), 1, false));
            } catch (IOException | LiveDataException | RuntimeException ignored) {
                // Unreadable recording: leave it out of the list.
            }
        }
        return out;
    }

    @Override
    public CompletableFuture<List<LiveGameSummary>> getGamesForDate(LocalDate date) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Optional<LiveGameSummary>> getGameInfo(long gameId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public void close() {
    }
}
