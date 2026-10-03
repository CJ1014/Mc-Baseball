package com.cj.mcbaseball.live;

import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.net.LiveDataException;
import java.util.List;

/**
 * How often to ask the provider for data. Kept in one place so the rates are easy to tune and test.
 *
 * <ul>
 *   <li>A game is live: every {@code liveMillis} (default 15s; the MLB CDN caches schedules for 20s).</li>
 *   <li>Nothing live, games still to come: every {@code idleMillis} (default 60s).</li>
 *   <li>Everything final / postponed: every 5 minutes (only while someone is looking).</li>
 *   <li>After a failure: 5s, 10s, 20s... up to 2 minutes; at least 30s (or Retry-After) when rate limited.</li>
 * </ul>
 */
public record LivePollingPolicy(long liveMillis, long idleMillis) {

    public static final long ALL_DONE_MILLIS = 5 * 60_000L;
    public static final long FAILURE_BASE_MILLIS = 5_000L;
    public static final long FAILURE_MAX_MILLIS = 120_000L;
    public static final long RATE_LIMIT_MIN_MILLIS = 30_000L;
    /** A manual refresh never triggers requests more often than this. */
    public static final long MIN_FORCED_REFRESH_MILLIS = 5_000L;

    public static LivePollingPolicy defaults() {
        return new LivePollingPolicy(15_000L, 60_000L);
    }

    public long scheduleTtlMillis(List<LiveGameSummary> games) {
        boolean anyUpcoming = false;
        for (LiveGameSummary g : games) {
            LiveGameStatus.State s = g.status().state();
            if (s.isActive()) {
                return this.liveMillis;
            }
            if (!s.isOver()) {
                anyUpcoming = true;
            }
        }
        return anyUpcoming ? this.idleMillis : ALL_DONE_MILLIS;
    }

    public long failureBackoffMillis(int consecutiveFailures, LiveDataException cause) {
        int n = Math.max(1, consecutiveFailures);
        long d = Math.min(FAILURE_MAX_MILLIS, FAILURE_BASE_MILLIS << Math.min(n - 1, 10));
        if (cause != null && cause.kind() == LiveDataException.Kind.RATE_LIMITED) {
            d = Math.max(d, Math.max(RATE_LIMIT_MIN_MILLIS, cause.retryAfterMillis()));
        }
        return d;
    }
}
