package com.cj.mcbaseball.live;

import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.live.model.LiveSchedule;
import com.cj.mcbaseball.live.net.LiveDataException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Server-side cache of daily schedules, shared by every viewer.
 *
 * <p>Demand-driven: clients with the browser open ping every few seconds with the version they have.
 * A ping returns the cached snapshot immediately and starts a provider request only when the cache is
 * older than the {@link LivePollingPolicy} allows and no backoff is pending. However many players are
 * looking, the provider sees at most one request per date per TTL. When nobody is looking, nothing is fetched.
 *
 * <p><b>Threading:</b> every public method must be called on the main (server) thread. Provider callbacks
 * are marshalled back onto it via {@code mainThread}. A generation counter discards callbacks that arrive
 * after {@link #close()}.
 *
 * @param <K> viewer key (player UUID in game, anything in tests)
 */
public final class LiveScheduleService<K> {

    static final int MAX_CACHED_DATES = 8;

    private final class Entry {
        final LocalDate date;
        List<LiveGameSummary> games;
        LiveProviderStatus status = LiveProviderStatus.LOADING;
        long fetchedAt;
        String message = "";
        int failures;
        long nextAttemptAt;
        boolean inFlight;
        int version = 1;
        long lastAccess;
        final Set<K> waiters = new LinkedHashSet<>();

        Entry(LocalDate date) {
            this.date = date;
        }
    }

    private final LiveBaseballProvider provider;
    private final LivePollingPolicy policy;
    private final Executor mainThread;
    private final LongSupplier clock;
    private final BiConsumer<K, LiveSchedule> sender;
    private final Consumer<String> log;
    private final Map<LocalDate, Entry> entries = new HashMap<>();
    private final boolean enabled;
    private int generation;
    private boolean closed;
    private long fetchesStarted;

    public LiveScheduleService(
        LiveBaseballProvider provider,
        LivePollingPolicy policy,
        boolean enabled,
        Executor mainThread,
        LongSupplier clock,
        BiConsumer<K, LiveSchedule> sender,
        Consumer<String> log
    ) {
        this.provider = provider;
        this.policy = policy;
        this.enabled = enabled;
        this.mainThread = mainThread;
        this.clock = clock;
        this.sender = sender;
        this.log = log;
    }

    /**
     * A viewer wants a date's games. Sends them the snapshot now if theirs is out of date, and again when
     * an in-flight fetch finishes.
     *
     * @param knownVersion version the viewer already has (-1 = none)
     * @param force        user pressed Refresh: bypass the TTL (still rate limited and still respects backoff)
     */
    public void request(K viewer, LocalDate date, int knownVersion, boolean force) {
        if (this.closed) {
            return;
        }
        long now = this.clock.getAsLong();
        if (!this.enabled) {
            if (knownVersion == 0) {
                return;
            }
            this.sender.accept(viewer, new LiveSchedule(date.toEpochDay(), today(now), List.of(), LiveProviderStatus.DISABLED, 0L, now, 0L, "", this.provider.displayName(), 0));
            return;
        }
        Entry e = this.entries.get(date);
        if (e == null) {
            this.evictIfFull();
            e = new Entry(date);
            this.entries.put(date, e);
        }
        e.lastAccess = now;

        long age = now - e.fetchedAt;
        boolean expired = e.games == null || age >= this.policy.scheduleTtlMillis(e.games);
        boolean forcedNow = force && (e.games == null || age >= LivePollingPolicy.MIN_FORCED_REFRESH_MILLIS);
        if ((expired || forcedNow) && !e.inFlight && now >= e.nextAttemptAt) {
            this.startFetch(e);
        }
        if (e.inFlight) {
            e.waiters.add(viewer);
        }
        if (e.version != knownVersion) {
            this.sender.accept(viewer, this.snapshot(e, now));
        }
    }

    private void startFetch(Entry e) {
        e.inFlight = true;
        this.fetchesStarted++;
        int gen = this.generation;
        try {
            this.provider.getGamesForDate(e.date).whenComplete((games, err) -> {
                try {
                    this.mainThread.execute(() -> this.onResult(gen, e, games, err));
                } catch (RejectedExecutionException ignored) {
                    // Server is shutting down; nothing to deliver.
                }
            });
        } catch (RuntimeException ex) {
            this.onResult(gen, e, null, ex);
        }
    }

    private void onResult(int gen, Entry e, List<LiveGameSummary> games, Throwable err) {
        if (gen != this.generation || this.closed) {
            return;
        }
        e.inFlight = false;
        long now = this.clock.getAsLong();
        if (err == null && games != null) {
            e.games = games;
            e.fetchedAt = now;
            e.status = LiveProviderStatus.OK;
            e.message = "";
            e.failures = 0;
            e.nextAttemptAt = 0L;
        } else {
            LiveDataException ex = LiveDataException.from(err);
            if (ex.kind() == LiveDataException.Kind.CANCELLED) {
                e.waiters.clear();
                return;
            }
            e.failures++;
            e.nextAttemptAt = now + this.policy.failureBackoffMillis(e.failures, ex);
            e.status = e.games == null ? LiveProviderStatus.UNAVAILABLE : LiveProviderStatus.STALE;
            e.message = ex.shortReason();
            if (e.failures == 1 || e.failures % 10 == 0) {
                this.log.accept("Schedule " + e.date + " fetch failed (" + e.failures + "x): " + ex.getMessage());
            }
        }
        e.version++;
        LiveSchedule snap = this.snapshot(e, now);
        List<K> waiting = new ArrayList<>(e.waiters);
        e.waiters.clear();
        for (K k : waiting) {
            this.sender.accept(k, snap);
        }
    }

    private LiveSchedule snapshot(Entry e, long now) {
        long retryIn = e.status == LiveProviderStatus.STALE || e.status == LiveProviderStatus.UNAVAILABLE ? Math.max(0L, e.nextAttemptAt - now) : 0L;
        return new LiveSchedule(
            e.date.toEpochDay(), today(now), e.games == null ? List.of() : e.games, e.status, e.fetchedAt, now, retryIn, e.message, this.provider.displayName(), e.version
        );
    }

    private static long today(long nowMillis) {
        return LiveDates.baseballToday(Instant.ofEpochMilli(nowMillis)).toEpochDay();
    }

    private void evictIfFull() {
        while (this.entries.size() >= MAX_CACHED_DATES) {
            Entry oldest = null;
            for (Entry x : this.entries.values()) {
                if (!x.inFlight && (oldest == null || x.lastAccess < oldest.lastAccess)) {
                    oldest = x;
                }
            }
            if (oldest == null) {
                return;
            }
            this.entries.remove(oldest.date);
        }
    }

    /** Provider requests started so far (for tests / the debug panel). */
    public long fetchesStarted() {
        return this.fetchesStarted;
    }

    /** Stops delivering results; in-flight callbacks are ignored when they arrive. */
    public void close() {
        this.closed = true;
        this.generation++;
        this.entries.clear();
    }
}
