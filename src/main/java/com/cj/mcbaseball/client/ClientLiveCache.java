package com.cj.mcbaseball.client;

import com.cj.mcbaseball.live.model.LiveSchedule;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/** Schedules the server has sent us, by baseball day. Client thread only. */
public final class ClientLiveCache {

    public record Received(LiveSchedule schedule, long receivedAtMillis) {
        /** Seconds since the server last got good data, corrected for client/server clock differences. */
        public long dataAgeMillis(long clientNow) {
            if (this.schedule.fetchedAtMillis() <= 0L) {
                return -1L;
            }
            return Math.max(0L, this.schedule.serverNowMillis() - this.schedule.fetchedAtMillis() + (clientNow - this.receivedAtMillis));
        }

        public long retryInMillis(long clientNow) {
            return Math.max(0L, this.schedule.retryInMillis() - (clientNow - this.receivedAtMillis));
        }
    }

    private static final Map<Long, Received> BY_DAY = new HashMap<>();
    /** Server's notion of today, learned from the latest snapshot (Long.MIN_VALUE until known). */
    private static long serverToday = Long.MIN_VALUE;
    private static int changes;

    private ClientLiveCache() {
    }

    public static void accept(LiveSchedule s) {
        BY_DAY.put(s.epochDay(), new Received(s, System.currentTimeMillis()));
        serverToday = s.todayEpochDay();
        changes++;
    }

    /** @param epochDay a day, or Long.MIN_VALUE for "today" */
    @Nullable
    public static Received get(long epochDay) {
        long day = epochDay == Long.MIN_VALUE ? serverToday : epochDay;
        return day == Long.MIN_VALUE ? null : BY_DAY.get(day);
    }

    public static long serverToday() {
        return serverToday;
    }

    /** Bumps on every received snapshot so screens know to rebuild. */
    public static int changes() {
        return changes;
    }

    public static void clear() {
        BY_DAY.clear();
        serverToday = Long.MIN_VALUE;
        changes++;
    }
}
