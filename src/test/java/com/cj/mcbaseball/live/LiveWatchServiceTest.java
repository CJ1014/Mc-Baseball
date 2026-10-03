package com.cj.mcbaseball.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.live.model.LiveWatchSnapshot;
import com.cj.mcbaseball.live.net.LiveDataException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Watched-game syncing with a fake provider, fake clock and fake server-thread queue. */
class LiveWatchServiceTest {

    static final class FeedProvider implements LiveBaseballProvider {
        final Map<Long, List<CompletableFuture<LiveGameState>>> calls = new HashMap<>();

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public String displayName() {
            return "Fake";
        }

        @Override
        public CompletableFuture<List<LiveGameSummary>> getGamesForDate(LocalDate date) {
            return new CompletableFuture<>();
        }

        @Override
        public CompletableFuture<Optional<LiveGameSummary>> getGameInfo(long gameId) {
            return new CompletableFuture<>();
        }

        @Override
        public CompletableFuture<LiveFeed> getLiveFeed(long gameId) {
            CompletableFuture<LiveGameState> f = new CompletableFuture<>();
            this.calls.computeIfAbsent(gameId, k -> new ArrayList<>()).add(f);
            return f.thenApply(s -> new LiveFeed(s, List.of()));
        }

        @Override
        public void close() {
        }

        int count(long gameId) {
            return this.calls.getOrDefault(gameId, List.of()).size();
        }

        CompletableFuture<LiveGameState> last(long gameId) {
            List<CompletableFuture<LiveGameState>> l = this.calls.get(gameId);
            return l.get(l.size() - 1);
        }
    }

    private FeedProvider provider;
    private final Queue<Runnable> serverThread = new ArrayDeque<>();
    private final List<Map.Entry<String, LiveWatchSnapshot>> updates = new ArrayList<>();
    private final Set<String> audience = new HashSet<>();
    private long now;
    private LiveWatchService<String> svc;

    @BeforeEach
    void setUp() {
        this.provider = new FeedProvider();
        this.now = 1_800_000_000_000L;
        this.svc = new LiveWatchService<>(this.provider, LivePollingPolicy.defaults(), this.serverThread::add, () -> this.now,
            (k, s) -> this.updates.add(Map.entry(k, s)), m -> { }, false);
        this.audience.add("fieldA");
        this.audience.add("fieldB");
    }

    private void run() {
        Runnable r;
        while ((r = this.serverThread.poll()) != null) {
            r.run();
        }
    }

    private void tick() {
        this.svc.tick(this.audience::contains);
    }

    static LiveGameState state(long id, LiveGameStatus.State st, int waitSeconds, long start, int balls) {
        return new LiveGameState(id, LiveGameStatus.of(st), null, null, start, 1, 2, 5, "Top", balls, 1, 1, false, null, null, null, null, null, null,
            null, null, null, null, null, "", "", "ts", waitSeconds, null, null);
    }

    private LiveWatchSnapshot lastFor(String key) {
        for (int i = this.updates.size() - 1; i >= 0; i--) {
            if (this.updates.get(i).getKey().equals(key)) {
                return this.updates.get(i).getValue();
            }
        }
        return null;
    }

    @Test
    void joinMidGameSyncsImmediatelyThenPollsAtFeedRate() {
        this.svc.start("fieldA", 7L);
        assertEquals(LiveProviderStatus.LOADING, this.svc.snapshot("fieldA").status());
        this.tick();
        assertEquals(1, this.provider.count(7L), "first fetch right away");
        this.tick();
        assertEquals(1, this.provider.count(7L), "never two requests in flight");

        this.provider.last(7L).complete(state(7, LiveGameStatus.State.LIVE, 10, 0, 2));
        this.run();
        LiveWatchSnapshot s = this.lastFor("fieldA");
        assertEquals(LiveProviderStatus.OK, s.status());
        assertEquals(2, s.state().balls());
        assertEquals(this.now, s.fetchedAtMillis());

        this.now += 9_999;
        this.tick();
        assertEquals(1, this.provider.count(7L));
        this.now += 1;
        this.tick();
        assertEquals(2, this.provider.count(7L), "live: every 10s");
    }

    @Test
    void honoursFeedWaitHint() {
        this.svc.start("fieldA", 7L);
        this.tick();
        this.provider.last(7L).complete(state(7, LiveGameStatus.State.LIVE, 25, 0, 0));
        this.run();
        this.now += 24_000;
        this.tick();
        assertEquals(1, this.provider.count(7L));
        this.now += 1_000;
        this.tick();
        assertEquals(2, this.provider.count(7L));
    }

    @Test
    void stadiumsWatchingTheSameGameShareOneFeed() {
        this.svc.start("fieldA", 7L);
        this.svc.start("fieldB", 7L);
        this.tick();
        assertEquals(1, this.provider.count(7L));
        this.provider.last(7L).complete(state(7, LiveGameStatus.State.LIVE, 10, 0, 3));
        this.run();
        assertSame(this.lastFor("fieldA").state(), this.lastFor("fieldB").state());
    }

    @Test
    void noAudienceNoRequests() {
        this.audience.clear();
        this.svc.start("fieldA", 7L);
        for (int i = 0; i < 100; i++) {
            this.now += 1_000;
            this.tick();
        }
        assertEquals(0, this.provider.count(7L));
        this.audience.add("fieldA");
        this.tick();
        assertEquals(1, this.provider.count(7L), "someone arrived: fetch now");
    }

    @Test
    void finalStopsPollingDelayedSlowsDown() {
        this.svc.start("fieldA", 7L);
        this.tick();
        this.provider.last(7L).complete(state(7, LiveGameStatus.State.DELAYED, 10, 0, 0));
        this.run();
        this.now += 29_999;
        this.tick();
        assertEquals(1, this.provider.count(7L));
        this.now += 1;
        this.tick();
        assertEquals(2, this.provider.count(7L), "delayed: every 30s");
        this.provider.last(7L).complete(state(7, LiveGameStatus.State.FINAL, 10, 0, 0));
        this.run();
        this.now += 24 * 3600_000L;
        this.tick();
        assertEquals(2, this.provider.count(7L), "final: never again");
        assertEquals(LiveGameStatus.State.FINAL, this.svc.snapshot("fieldA").state().status().state());
    }

    @Test
    void waitForGamePollsFasterAsFirstPitchNears() {
        long start = this.now + 3 * 3600_000L;
        this.svc.start("fieldA", 7L);
        this.tick();
        this.provider.last(7L).complete(state(7, LiveGameStatus.State.SCHEDULED, 10, start, 0));
        this.run();
        this.now += LivePollingPolicy.FEED_FAR_MILLIS - 1;
        this.tick();
        assertEquals(1, this.provider.count(7L), "3h out: every 5 min");
        LivePollingPolicy p = LivePollingPolicy.defaults();
        LiveGameState soon = state(7, LiveGameStatus.State.PREGAME, 10, this.now + 5 * 60_000L, 0);
        assertEquals(LivePollingPolicy.FEED_SOON_MILLIS, p.feedIntervalMillis(soon, this.now));
        LiveGameState laterToday = state(7, LiveGameStatus.State.PREGAME, 10, this.now + 30 * 60_000L, 0);
        assertEquals(LivePollingPolicy.FEED_LATER_TODAY_MILLIS, p.feedIntervalMillis(laterToday, this.now));
        LiveGameState late = state(7, LiveGameStatus.State.PREGAME, 10, this.now - 60_000L, 0);
        assertEquals(LivePollingPolicy.FEED_SOON_MILLIS, p.feedIntervalMillis(late, this.now), "past start but not live yet");
    }

    @Test
    void outageKeepsLastStateAndBacksOff() {
        this.svc.start("fieldA", 7L);
        this.tick();
        this.provider.last(7L).complete(state(7, LiveGameStatus.State.LIVE, 10, 0, 1));
        this.run();
        this.now += 10_000;
        this.tick();
        this.provider.last(7L).completeExceptionally(new LiveDataException(LiveDataException.Kind.TIMEOUT, "timed out"));
        this.run();
        LiveWatchSnapshot s = this.lastFor("fieldA");
        assertEquals(LiveProviderStatus.STALE, s.status());
        assertEquals(1, s.state().balls(), "Minecraft keeps the last real state");
        assertEquals(5_000, s.retryInMillis());
        this.now += 4_999;
        this.tick();
        assertEquals(2, this.provider.count(7L));
        this.now += 1;
        this.tick();
        assertEquals(3, this.provider.count(7L));
        this.provider.last(7L).completeExceptionally(new RuntimeException("reset"));
        this.run();
        assertEquals(10_000, this.lastFor("fieldA").retryInMillis());
        this.now += 10_000;
        this.tick();
        this.provider.last(7L).complete(state(7, LiveGameStatus.State.LIVE, 10, 0, 3));
        this.run();
        assertEquals(LiveProviderStatus.OK, this.lastFor("fieldA").status());
        assertEquals(3, this.lastFor("fieldA").state().balls());
    }

    @Test
    void neverAnyDataIsUnavailable() {
        this.svc.start("fieldA", 7L);
        this.tick();
        this.provider.last(7L).completeExceptionally(new LiveDataException(LiveDataException.Kind.CONNECTION, "refused"));
        this.run();
        LiveWatchSnapshot s = this.lastFor("fieldA");
        assertEquals(LiveProviderStatus.UNAVAILABLE, s.status());
        assertNull(s.state());
    }

    @Test
    void stopAndSwitchDiscardOldResults() {
        this.svc.start("fieldA", 7L);
        this.tick();
        CompletableFuture<LiveGameState> oldFetch = this.provider.last(7L);
        this.svc.start("fieldA", 8L);
        assertEquals(8L, this.svc.watchedGame("fieldA"));
        oldFetch.complete(state(7, LiveGameStatus.State.LIVE, 10, 0, 0));
        this.run();
        assertTrue(this.updates.isEmpty(), "result for the old game must not reach the field");
        this.tick();
        assertEquals(1, this.provider.count(8L));
        this.svc.stop("fieldA");
        this.provider.last(8L).complete(state(8, LiveGameStatus.State.LIVE, 10, 0, 0));
        this.run();
        assertTrue(this.updates.isEmpty());
        assertNull(this.svc.snapshot("fieldA"));
    }

    @Test
    void closeDropsLateCallbacks() {
        this.svc.start("fieldA", 7L);
        this.tick();
        this.svc.close();
        this.provider.last(7L).complete(state(7, LiveGameStatus.State.LIVE, 10, 0, 0));
        this.run();
        assertTrue(this.updates.isEmpty());
        this.svc.start("fieldA", 7L);
        this.tick();
        assertEquals(1, this.provider.count(7L), "closed service starts nothing");
    }

    @Test
    void everySuccessfulPollBumpsVersion() {
        this.svc.start("fieldA", 7L);
        this.tick();
        this.provider.last(7L).complete(state(7, LiveGameStatus.State.LIVE, 10, 0, 0));
        this.run();
        int v1 = this.lastFor("fieldA").version();
        this.now += 10_000;
        this.tick();
        this.provider.last(7L).complete(state(7, LiveGameStatus.State.LIVE, 10, 0, 0));
        this.run();
        assertTrue(this.lastFor("fieldA").version() > v1, "same data, but 'Updated Ns ago' must reset");
    }
}
