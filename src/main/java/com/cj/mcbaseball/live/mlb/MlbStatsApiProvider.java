package com.cj.mcbaseball.live.mlb;

import com.cj.mcbaseball.live.LiveBaseballProvider;
import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.net.LiveApiClient;
import com.cj.mcbaseball.live.net.LiveDataException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * {@link LiveBaseballProvider} backed by the public MLB Stats API (statsapi.mlb.com).
 *
 * <p>Endpoints (verified against live responses on 2026-10-03):
 * <ul>
 *   <li>Schedule: {@code /api/v1/schedule?sportId=1&date=YYYY-MM-DD&hydrate=linescore,team}
 *       (served from a CDN with {@code max-age=20}).</li>
 *   <li>Live feed: {@code /api/v1.1/game/{gamePk}/feed/live}. Note the <b>v1.1</b>:
 *       {@code /api/v1/game/{gamePk}/feed/live} returns 404. The feed's {@code metaData.wait} (10s)
 *       is the server's suggested poll interval.</li>
 * </ul>
 */
public final class MlbStatsApiProvider implements LiveBaseballProvider {

    public static final String DEFAULT_BASE_URL = "https://statsapi.mlb.com";
    /** The CDN caches the schedule for 20s; asking more often just returns the same bytes. */
    static final long SCHEDULE_MIN_AGE_MILLIS = 5_000L;
    /** Several stadiums watching the same game share one request. */
    static final long FEED_MIN_AGE_MILLIS = 2_000L;

    private final LiveApiClient client;
    private final String baseUrl;
    private final Consumer<String> warn;

    public MlbStatsApiProvider(LiveApiClient client, String baseUrl, Consumer<String> warn) {
        this.client = client;
        String b = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        this.baseUrl = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        this.warn = warn;
    }

    @Override
    public String id() {
        return "mlb_stats_api";
    }

    @Override
    public String displayName() {
        return "MLB Stats API";
    }

    String scheduleUrl(LocalDate date) {
        return this.baseUrl + "/api/v1/schedule?sportId=1&date=" + date + "&hydrate=linescore,team";
    }

    String gameScheduleUrl(long gamePk) {
        return this.baseUrl + "/api/v1/schedule?sportId=1&gamePk=" + gamePk + "&hydrate=linescore,team";
    }

    String feedUrl(long gamePk) {
        return this.baseUrl + "/api/v1.1/game/" + gamePk + "/feed/live";
    }

    @Override
    public CompletableFuture<LiveGameState> getLiveGameState(long gameId) {
        return this.client.get(this.feedUrl(gameId), "feed_" + gameId, FEED_MIN_AGE_MILLIS).thenApply(body -> {
            try {
                return MlbLiveFeedParser.parse(body, gameId);
            } catch (LiveDataException e) {
                throw new CompletionException(e);
            }
        });
    }

    @Override
    public CompletableFuture<List<LiveGameSummary>> getGamesForDate(LocalDate date) {
        return this.client.get(this.scheduleUrl(date), "schedule_" + date, SCHEDULE_MIN_AGE_MILLIS)
            .thenApply(body -> this.parseSchedule(body, "schedule " + date));
    }

    @Override
    public CompletableFuture<Optional<LiveGameSummary>> getGameInfo(long gameId) {
        return this.client.get(this.gameScheduleUrl(gameId), "game_" + gameId, SCHEDULE_MIN_AGE_MILLIS)
            .thenApply(body -> this.parseSchedule(body, "game " + gameId).stream().filter(g -> g.gameId() == gameId).findFirst());
    }

    private List<LiveGameSummary> parseSchedule(String body, String what) {
        try {
            return MlbScheduleParser.parse(body, reason -> this.warn.accept(what + ": skipped " + reason));
        } catch (LiveDataException e) {
            throw new CompletionException(e);
        }
    }

    @Override
    public void close() {
        this.client.close();
    }
}
