package com.cj.mcbaseball.live;

import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.live.model.LiveWatchSnapshot;
import com.cj.mcbaseball.live.net.LiveDataException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Keeps watched real games in sync. A "watch" is a stadium (key {@code K}) following one game.
 *
 * <ul>
 *   <li>One feed per game, shared by every stadium watching it.</li>
 *   <li>A feed is polled only while at least one of its stadiums has an audience
 *       ({@code wanted} in {@link #tick}); nobody nearby = no requests. Coming back triggers an
 *       immediate refresh because the poll time has passed.</li>
 *   <li>Poll rate from {@link LivePollingPolicy#feedIntervalMillis}; stops entirely once final.</li>
 *   <li>Failures: keep the last good state (STALE), back off, keep trying. Never drops the watch.</li>
 *   <li>Every successful poll bumps the version so viewers' "Updated Ns ago" stays truthful.</li>
 * </ul>
 *
 * <p><b>Threading:</b> all public methods on the server thread. Provider callbacks hop back through
 * {@code mainThread}; a generation counter drops callbacks that arrive after {@link #close()}.
 */
public final class LiveWatchService<K> {

    private final class Feed {
        final long gameId;
        @Nullable
        LiveGameState state;
        LiveProviderStatus status = LiveProviderStatus.LOADING;
        long fetchedAt;
        long nextPollAt;
        boolean inFlight;
        int failures;
        String message = "";
        int version = 1;

        Feed(long gameId) {
            this.gameId = gameId;
        }
    }

    private final LiveBaseballProvider provider;
    private final LivePollingPolicy policy;
    private final Executor mainThread;
    private final LongSupplier clock;
    private final BiConsumer<K, LiveWatchSnapshot> onUpdate;
    private final Consumer<String> log;
    private final Map<Long, Feed> feeds = new HashMap<>();
    private final Map<K, Long> watches = new HashMap<>();
    private int generation;
    private boolean closed;
    private long fetchesStarted;

    public LiveWatchService(
        LiveBaseballProvider provider, LivePollingPolicy policy, Executor mainThread, LongSupplier clock, BiConsumer<K, LiveWatchSnapshot> onUpdate, Consumer<String> log
    ) {
        this.provider = provider;
        this.policy = policy;
        this.mainThread = mainThread;
        this.clock = clock;
        this.onUpdate = onUpdate;
        this.log = log;
    }

    /** Stadium {@code key} starts following {@code gameId} (replacing whatever it followed). */
    public void start(K key, long gameId) {
        if (this.closed) {
            return;
        }
        Long previous = this.watches.put(key, gameId);
        if (previous != null && previous != gameId) {
            this.dropFeedIfUnused(previous);
        }
        this.feeds.computeIfAbsent(gameId, Feed::new);
    }

    public void stop(K key) {
        Long gameId = this.watches.remove(key);
        if (gameId != null) {
            this.dropFeedIfUnused(gameId);
        }
    }

    private void dropFeedIfUnused(long gameId) {
        if (!this.watches.containsValue(gameId)) {
            this.feeds.remove(gameId);
        }
    }

    @Nullable
    public Long watchedGame(K key) {
        return this.watches.get(key);
    }

    public List<K> watchers() {
        return new ArrayList<>(this.watches.keySet());
    }

    /** Current snapshot for a stadium, or null if it isn't watching anything. */
    @Nullable
    public LiveWatchSnapshot snapshot(K key) {
        Long gameId = this.watches.get(key);
        Feed f = gameId == null ? null : this.feeds.get(gameId);
        return f == null ? null : this.snapshot(f, this.clock.getAsLong());
    }

    /**
     * Call every server tick (cheap: a few comparisons when nothing is due).
     *
     * @param wanted whether a stadium currently has anyone near it
     */
    public void tick(Predicate<K> wanted) {
        if (this.closed || this.feeds.isEmpty()) {
            return;
        }
        long now = this.clock.getAsLong();
        for (Feed f : this.feeds.values()) {
            if (f.inFlight || now < f.nextPollAt) {
                continue;
            }
            boolean anyone = false;
            for (Map.Entry<K, Long> w : this.watches.entrySet()) {
                if (w.getValue() == f.gameId && wanted.test(w.getKey())) {
                    anyone = true;
                    break;
                }
            }
            if (anyone) {
                this.fetch(f);
            }
        }
    }

    private void fetch(Feed f) {
        f.inFlight = true;
        this.fetchesStarted++;
        int gen = this.generation;
        try {
            this.provider.getLiveGameState(f.gameId).whenComplete((state, err) -> {
                try {
                    this.mainThread.execute(() -> this.onResult(gen, f, state, err));
                } catch (RejectedExecutionException ignored) {
                    // Server shutting down.
                }
            });
        } catch (RuntimeException ex) {
            this.onResult(gen, f, null, ex);
        }
    }

    private void onResult(int gen, Feed f, @Nullable LiveGameState state, @Nullable Throwable err) {
        if (gen != this.generation || this.closed || this.feeds.get(f.gameId) != f) {
            return;
        }
        f.inFlight = false;
        long now = this.clock.getAsLong();
        if (err == null && state != null) {
            f.state = state;
            f.status = LiveProviderStatus.OK;
            f.fetchedAt = now;
            f.failures = 0;
            f.message = "";
            long interval = this.policy.feedIntervalMillis(state, now);
            f.nextPollAt = interval == Long.MAX_VALUE ? Long.MAX_VALUE : now + interval;
        } else {
            LiveDataException ex = LiveDataException.from(err);
            if (ex.kind() == LiveDataException.Kind.CANCELLED) {
                return;
            }
            f.failures++;
            f.nextPollAt = now + this.policy.failureBackoffMillis(f.failures, ex);
            f.status = f.state == null ? LiveProviderStatus.UNAVAILABLE : LiveProviderStatus.STALE;
            f.message = ex.shortReason();
            if (f.failures == 1 || f.failures % 10 == 0) {
                this.log.accept("Game " + f.gameId + " feed failed (" + f.failures + "x): " + ex.getMessage());
            }
        }
        f.version++;
        LiveWatchSnapshot snap = this.snapshot(f, now);
        for (Map.Entry<K, Long> w : new ArrayList<>(this.watches.entrySet())) {
            if (w.getValue() == f.gameId) {
                this.onUpdate.accept(w.getKey(), snap);
            }
        }
    }

    private LiveWatchSnapshot snapshot(Feed f, long now) {
        boolean failing = f.status == LiveProviderStatus.STALE || f.status == LiveProviderStatus.UNAVAILABLE;
        long retryIn = failing ? Math.max(0L, f.nextPollAt - now) : 0L;
        return new LiveWatchSnapshot(f.gameId, f.state, f.status, f.fetchedAt, now, retryIn, f.message, this.provider.displayName(), f.version);
    }

    public long fetchesStarted() {
        return this.fetchesStarted;
    }

    public void close() {
        this.closed = true;
        this.generation++;
        this.feeds.clear();
        this.watches.clear();
    }
}
