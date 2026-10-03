package com.cj.mcbaseball.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.live.model.LiveSchedule;
import com.cj.mcbaseball.live.model.LiveTeam;
import com.cj.mcbaseball.live.net.LiveDataException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The schedule cache with a fake provider, a fake clock and a fake "server thread" queue,
 * so every timing rule is checked deterministically.
 */
class LiveScheduleServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 3);

    /** Provider whose requests are completed by the test. */
    static class FakeProvider implements LiveBaseballProvider {
        final List<CompletableFuture<List<LiveGameSummary>>> calls = new ArrayList<>();

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public String displayName() {
            return "Fake Provider";
        }

        @Override
        public CompletableFuture<List<LiveGameSummary>> getGamesForDate(LocalDate date) {
            CompletableFuture<List<LiveGameSummary>> f = new CompletableFuture<>();
            this.calls.add(f);
            return f;
        }

        @Override
        public CompletableFuture<Optional<LiveGameSummary>> getGameInfo(long gameId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<com.cj.mcbaseball.live.model.LiveFeed> getLiveFeed(long gameId) {
            return new CompletableFuture<>();
        }

        @Override
        public void close() {
        }

        CompletableFuture<List<LiveGameSummary>> last() {
            return this.calls.get(this.calls.size() - 1);
        }
    }

    private record Sent(String viewer, LiveSchedule schedule) {
    }

    private FakeProvider provider;
    private final Queue<Runnable> serverThread = new ArrayDeque<>();
    private final List<Sent> sent = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private long now;
    private LiveScheduleService<String> service;

    @BeforeEach
    void setUp() {
        this.provider = new FakeProvider();
        this.now = 1_800_000_000_000L;
        this.service = this.make(true);
    }

    private LiveScheduleService<String> make(boolean enabled) {
        return new LiveScheduleService<>(this.provider, LivePollingPolicy.defaults(), enabled, this.serverThread::add, () -> this.now,
            (v, s) -> this.sent.add(new Sent(v, s)), this.logs::add);
    }

    private void runServerTasks() {
        Runnable r;
        while ((r = this.serverThread.poll()) != null) {
            r.run();
        }
    }

    private static LiveGameSummary game(long id, LiveGameStatus.State state) {
        return new LiveGameSummary(id, "2026-10-03", new LiveTeam(1, "Away", "AWY", "Away", ""), new LiveTeam(2, "Home", "HOM", "Home", ""),
            LiveGameStatus.of(state), 0L, false, 1, 2, 5, "Top", 0, 0, 0, null, null, List.of(), List.of(), "", "", 1, false);
    }

    private LiveSchedule lastSentTo(String viewer) {
        for (int i = this.sent.size() - 1; i >= 0; i--) {
            if (this.sent.get(i).viewer.equals(viewer)) {
                return this.sent.get(i).schedule;
            }
        }
        return null;
    }

    @Test
    void firstRequestShowsLoadingThenData() {
        this.service.request("steve", DAY, -1, false);
        assertEquals(1, this.provider.calls.size());
        assertEquals(LiveProviderStatus.LOADING, this.lastSentTo("steve").status());

        this.provider.last().complete(List.of(game(1, LiveGameStatus.State.LIVE)));
        assertEquals(1, this.sent.size(), "results must wait for the server thread");
        this.runServerTasks();

        LiveSchedule s = this.lastSentTo("steve");
        assertEquals(LiveProviderStatus.OK, s.status());
        assertEquals(1, s.games().size());
        assertEquals(DAY.toEpochDay(), s.epochDay());
        assertEquals(this.now, s.fetchedAtMillis());
    }

    @Test
    void manyViewersOneRequest() {
        for (int i = 0; i < 20; i++) {
            this.service.request("p" + i, DAY, -1, false);
        }
        assertEquals(1, this.provider.calls.size());
        this.provider.last().complete(List.of(game(1, LiveGameStatus.State.LIVE)));
        this.runServerTasks();
        for (int i = 0; i < 20; i++) {
            assertEquals(LiveProviderStatus.OK, this.lastSentTo("p" + i).status());
        }
        // Within the TTL, more pings never reach the provider, and up-to-date viewers get nothing.
        int before = this.sent.size();
        int version = this.lastSentTo("p0").version();
        this.now += 10_000;
        for (int i = 0; i < 20; i++) {
            this.service.request("p" + i, DAY, version, false);
        }
        assertEquals(1, this.provider.calls.size());
        assertEquals(before, this.sent.size());
    }

    private void loadWith(LiveGameSummary... games) {
        this.service.request("steve", DAY, -1, false);
        this.provider.last().complete(List.of(games));
        this.runServerTasks();
    }

    @Test
    void pollingRateFollowsGameState() {
        this.loadWith(game(1, LiveGameStatus.State.LIVE));
        this.now += 14_999;
        this.service.request("steve", DAY, 2, false);
        assertEquals(1, this.provider.calls.size());
        this.now += 1;
        this.service.request("steve", DAY, 2, false);
        assertEquals(2, this.provider.calls.size(), "live game: refresh after 15s");

        this.provider.last().complete(List.of(game(1, LiveGameStatus.State.PREGAME)));
        this.runServerTasks();
        this.now += 59_999;
        this.service.request("steve", DAY, 3, false);
        assertEquals(2, this.provider.calls.size());
        this.now += 1;
        this.service.request("steve", DAY, 3, false);
        assertEquals(3, this.provider.calls.size(), "upcoming only: refresh after 60s");

        this.provider.last().complete(List.of(game(1, LiveGameStatus.State.FINAL), game(2, LiveGameStatus.State.POSTPONED)));
        this.runServerTasks();
        this.now += LivePollingPolicy.ALL_DONE_MILLIS - 1;
        this.service.request("steve", DAY, 4, false);
        assertEquals(3, this.provider.calls.size(), "all final: barely poll");
    }

    @Test
    void failuresBackOffAndKeepOldData() {
        this.service.request("steve", DAY, -1, false);
        this.provider.last().completeExceptionally(new LiveDataException(LiveDataException.Kind.TIMEOUT, "timed out"));
        this.runServerTasks();
        LiveSchedule s = this.lastSentTo("steve");
        assertEquals(LiveProviderStatus.UNAVAILABLE, s.status());
        assertEquals(5_000, s.retryInMillis());
        assertEquals("timed out", s.message());
        assertEquals(1, this.logs.size());

        this.now += 4_000;
        this.service.request("steve", DAY, s.version(), true);
        assertEquals(1, this.provider.calls.size(), "even Refresh must respect backoff");
        this.now += 1_000;
        this.service.request("steve", DAY, s.version(), false);
        assertEquals(2, this.provider.calls.size());
        this.provider.last().completeExceptionally(new RuntimeException("connection reset"));
        this.runServerTasks();
        assertEquals(10_000, this.lastSentTo("steve").retryInMillis(), "second failure doubles the wait");

        this.now += 10_000;
        this.service.request("steve", DAY, -1, false);
        this.provider.last().complete(List.of(game(7, LiveGameStatus.State.LIVE)));
        this.runServerTasks();
        assertEquals(LiveProviderStatus.OK, this.lastSentTo("steve").status());

        // Failure after good data: STALE, games kept.
        this.now += 15_000;
        this.service.request("steve", DAY, -1, false);
        this.provider.last().completeExceptionally(new LiveDataException(LiveDataException.Kind.INVALID_RESPONSE, "invalid JSON"));
        this.runServerTasks();
        LiveSchedule stale = this.lastSentTo("steve");
        assertEquals(LiveProviderStatus.STALE, stale.status());
        assertEquals(1, stale.games().size());
        assertEquals(5_000, stale.retryInMillis(), "backoff resets after a success");
    }

    @Test
    void rateLimitWaitsAtLeastThirtySeconds() {
        this.service.request("steve", DAY, -1, false);
        this.provider.last().completeExceptionally(new LiveDataException(LiveDataException.Kind.RATE_LIMITED, "HTTP 429", null, 429, 0));
        this.runServerTasks();
        assertEquals(30_000, this.lastSentTo("steve").retryInMillis());
    }

    @Test
    void manualRefreshIsRateLimited() {
        this.loadWith(game(1, LiveGameStatus.State.LIVE));
        this.now += 2_000;
        this.service.request("steve", DAY, 2, true);
        assertEquals(1, this.provider.calls.size(), "refresh spam within 5s is ignored");
        this.now += 3_000;
        this.service.request("steve", DAY, 2, true);
        assertEquals(2, this.provider.calls.size());
    }

    @Test
    void lateCallbacksAfterShutdownAreIgnored() {
        this.service.request("steve", DAY, -1, false);
        int sentBefore = this.sent.size();
        this.service.close();
        this.provider.last().complete(List.of(game(1, LiveGameStatus.State.LIVE)));
        this.runServerTasks();
        assertEquals(sentBefore, this.sent.size());
        this.service.request("steve", DAY, -1, false);
        assertEquals(1, this.provider.calls.size(), "closed service must not start requests");
    }

    @Test
    void cancelledRequestsAreSilent() {
        this.service.request("steve", DAY, -1, false);
        int sentBefore = this.sent.size();
        this.provider.last().completeExceptionally(new LiveDataException(LiveDataException.Kind.CANCELLED, "client closed"));
        this.runServerTasks();
        assertEquals(sentBefore, this.sent.size());
        assertTrue(this.logs.isEmpty());
    }

    @Test
    void providerThrowingSynchronouslyIsHandled() {
        LiveBaseballProvider exploding = new FakeProvider() {
            @Override
            public CompletableFuture<List<LiveGameSummary>> getGamesForDate(LocalDate date) {
                throw new IllegalStateException("boom");
            }
        };
        LiveScheduleService<String> svc = new LiveScheduleService<>(exploding, LivePollingPolicy.defaults(), true, this.serverThread::add,
            () -> this.now, (v, s) -> this.sent.add(new Sent(v, s)), this.logs::add);
        svc.request("steve", DAY, -1, false);
        assertEquals(LiveProviderStatus.UNAVAILABLE, this.lastSentTo("steve").status());
    }

    @Test
    void disabledInConfig() {
        LiveScheduleService<String> off = this.make(false);
        off.request("steve", DAY, -1, false);
        assertEquals(LiveProviderStatus.DISABLED, this.lastSentTo("steve").status());
        assertEquals(0, this.provider.calls.size());
        int n = this.sent.size();
        off.request("steve", DAY, 0, false);
        assertEquals(n, this.sent.size(), "no resend once the client knows");
    }

    @Test
    void datesAreCachedSeparatelyAndEvicted() {
        for (int d = 0; d < LiveScheduleService.MAX_CACHED_DATES + 3; d++) {
            this.service.request("steve", DAY.plusDays(d), -1, false);
            this.provider.last().complete(List.of());
            this.runServerTasks();
            this.now += 1;
        }
        int calls = this.provider.calls.size();
        this.service.request("steve", DAY.plusDays(LiveScheduleService.MAX_CACHED_DATES + 2), -1, false);
        assertEquals(calls, this.provider.calls.size(), "recent date still cached");
        this.service.request("steve", DAY, -1, false);
        assertEquals(calls + 1, this.provider.calls.size(), "oldest date was evicted");
    }
}
