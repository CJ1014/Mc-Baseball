package com.cj.mcbaseball.live;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Which calendar day counts as "today" for baseball, wherever the server is. */
public final class LiveDates {

    /** MLB's official dates follow US Eastern time. */
    public static final ZoneId BASEBALL_ZONE = ZoneId.of("America/New_York");
    /** Until 5 AM Eastern, "today" is still the previous day, so late West Coast / extra-inning games stay listed. */
    public static final int ROLLOVER_HOUR = 5;
    /** How far from today players may browse. */
    public static final int MAX_DAYS_FROM_TODAY = 30;

    private LiveDates() {
    }

    public static LocalDate baseballToday(Instant now) {
        ZonedDateTime et = now.atZone(BASEBALL_ZONE);
        LocalDate d = et.toLocalDate();
        return et.getHour() < ROLLOVER_HOUR ? d.minusDays(1) : d;
    }

    /** Clamps a requested day to the browsable window; {@code Long.MIN_VALUE} means today. */
    public static LocalDate resolve(long requestedEpochDay, Instant now) {
        LocalDate today = baseballToday(now);
        if (requestedEpochDay == Long.MIN_VALUE) {
            return today;
        }
        long t = today.toEpochDay();
        long clamped = Math.max(t - MAX_DAYS_FROM_TODAY, Math.min(t + MAX_DAYS_FROM_TODAY, requestedEpochDay));
        return LocalDate.ofEpochDay(clamped);
    }
}
