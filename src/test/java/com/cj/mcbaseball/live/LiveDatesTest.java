package com.cj.mcbaseball.live;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LiveDatesTest {

    @Test
    void lateGamesStayOnTheirDay() {
        // 00:30Z Oct 4 = 8:30 PM EDT Oct 3: tonight's game.
        assertEquals(LocalDate.of(2026, 10, 3), LiveDates.baseballToday(Instant.parse("2026-10-04T00:30:00Z")));
        // 4:59 AM EDT: still yesterday's slate (late West Coast / extra innings).
        assertEquals(LocalDate.of(2026, 10, 3), LiveDates.baseballToday(Instant.parse("2026-10-04T08:59:00Z")));
        // 5:00 AM EDT: new day.
        assertEquals(LocalDate.of(2026, 10, 4), LiveDates.baseballToday(Instant.parse("2026-10-04T09:00:00Z")));
        // Winter (EST, UTC-5).
        assertEquals(LocalDate.of(2026, 3, 1), LiveDates.baseballToday(Instant.parse("2026-03-02T09:59:00Z")));
        assertEquals(LocalDate.of(2026, 3, 2), LiveDates.baseballToday(Instant.parse("2026-03-02T10:00:00Z")));
    }

    @Test
    void resolveClampsToWindow() {
        Instant now = Instant.parse("2026-10-03T18:00:00Z");
        LocalDate today = LocalDate.of(2026, 10, 3);
        assertEquals(today, LiveDates.resolve(Long.MIN_VALUE, now));
        assertEquals(today.minusDays(1), LiveDates.resolve(today.minusDays(1).toEpochDay(), now));
        assertEquals(today.minusDays(LiveDates.MAX_DAYS_FROM_TODAY), LiveDates.resolve(0L, now));
        assertEquals(today.plusDays(LiveDates.MAX_DAYS_FROM_TODAY), LiveDates.resolve(Long.MAX_VALUE, now));
    }
}
