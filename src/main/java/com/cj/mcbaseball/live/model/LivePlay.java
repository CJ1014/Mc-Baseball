package com.cj.mcbaseball.live.model;

import java.util.List;

/**
 * One plate appearance (MLB "play"). While {@link #isComplete()} is false, its result fields are not
 * trustworthy: the feed can show a mid-at-bat action there (e.g. a stolen base). Only read
 * {@code eventType} / {@code description} once complete.
 *
 * @param atBatIndex 0-based, increases through the game; the stable identity of the play
 * @param eventType  result type when complete ("single", "home_run", "strikeout", "field_out",
 *                   "grounded_into_double_play"...)
 */
public record LivePlay(
    int atBatIndex,
    int inning,
    boolean isTop,
    boolean isComplete,
    LivePlayer batter,
    LivePlayer pitcher,
    String eventType,
    String event,
    String description,
    int rbi,
    int awayScore,
    int homeScore,
    boolean isOut,
    int outsAfter,
    List<LivePlayEvent> events,
    List<LiveRunner> runners,
    long endTimeMillis
) {
    public LivePlay {
        batter = batter == null ? LivePlayer.NONE : batter;
        pitcher = pitcher == null ? LivePlayer.NONE : pitcher;
        eventType = eventType == null ? "" : eventType;
        event = event == null ? "" : event;
        description = description == null ? "" : description;
        events = events == null ? List.of() : List.copyOf(events);
        runners = runners == null ? List.of() : List.copyOf(runners);
    }

    /** Sort key for "half-inning order": 2 * inning + (bottom ? 1 : 0). */
    public int halfKey() {
        return this.inning * 2 + (this.isTop ? 0 : 1);
    }
}
