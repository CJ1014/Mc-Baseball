package com.cj.mcbaseball.live.model;

import java.util.List;

/**
 * Everything known about a real game right now, from one live-feed response. Provider-neutral.
 * Integers use {@link LiveGameSummary#UNKNOWN} (-1) when not reported; players use {@link LivePlayer#NONE}.
 *
 * <p>During a break ({@link #isBreak()}), batter / pitcher are the ones <em>due up</em> next half-inning
 * (that's how the MLB feed reports them). Between two at-bats ({@link #betweenBatters()}), {@code batter}
 * is the next batter due up and the count is the fresh 0-0.
 *
 * @param feedTimestamp provider's own version stamp for this data ("" if none)
 * @param suggestedPollSeconds provider's hint for how often to ask again (0 = no hint)
 */
public record LiveGameState(
    long gameId,
    LiveGameStatus status,
    LiveTeam away,
    LiveTeam home,
    long startEpochMillis,
    int awayScore,
    int homeScore,
    int inning,
    String inningState,
    int balls,
    int strikes,
    int outs,
    boolean betweenBatters,
    LivePlayer batter,
    LivePlayer pitcher,
    LivePlayer onDeck,
    LivePlayer runnerOnFirst,
    LivePlayer runnerOnSecond,
    LivePlayer runnerOnThird,
    LiveLineTotals awayTotals,
    LiveLineTotals homeTotals,
    List<Integer> awayInningRuns,
    List<Integer> homeInningRuns,
    LivePitch lastPitch,
    String lastPlay,
    String venue,
    String feedTimestamp,
    int suggestedPollSeconds,
    List<LivePlayer> awayLineup,
    List<LivePlayer> homeLineup
) {
    public LiveGameState {
        status = status == null ? LiveGameStatus.of(LiveGameStatus.State.UNKNOWN) : status;
        away = away == null ? LiveTeam.UNKNOWN : away;
        home = home == null ? LiveTeam.UNKNOWN : home;
        inningState = inningState == null ? "" : inningState;
        batter = orNone(batter);
        pitcher = orNone(pitcher);
        onDeck = orNone(onDeck);
        runnerOnFirst = orNone(runnerOnFirst);
        runnerOnSecond = orNone(runnerOnSecond);
        runnerOnThird = orNone(runnerOnThird);
        awayTotals = awayTotals == null ? LiveLineTotals.NONE : awayTotals;
        homeTotals = homeTotals == null ? LiveLineTotals.NONE : homeTotals;
        awayInningRuns = awayInningRuns == null ? List.of() : List.copyOf(awayInningRuns);
        homeInningRuns = homeInningRuns == null ? List.of() : List.copyOf(homeInningRuns);
        lastPitch = lastPitch == null ? LivePitch.NONE : lastPitch;
        lastPlay = lastPlay == null ? "" : lastPlay;
        venue = venue == null ? "" : venue;
        feedTimestamp = feedTimestamp == null ? "" : feedTimestamp;
        awayLineup = awayLineup == null ? List.of() : List.copyOf(awayLineup);
        homeLineup = homeLineup == null ? List.of() : List.copyOf(homeLineup);
    }

    private static LivePlayer orNone(LivePlayer p) {
        return p == null ? LivePlayer.NONE : p;
    }

    public boolean isTop() {
        return "Top".equals(this.inningState) || "Middle".equals(this.inningState);
    }

    /** Between half-innings ("Middle" / "End"): no count, players shown are due up. */
    public boolean isBreak() {
        return "Middle".equals(this.inningState) || "End".equals(this.inningState);
    }

    /** Bit 0 = first, bit 1 = second, bit 2 = third (same layout as the mod's own HUD). */
    public int basesMask() {
        return (this.runnerOnFirst.known() ? 1 : 0) | (this.runnerOnSecond.known() ? 2 : 0) | (this.runnerOnThird.known() ? 4 : 0);
    }

    public boolean showsScore() {
        LiveGameStatus.State s = this.status.state();
        return this.awayScore >= 0 && this.homeScore >= 0 && s.section() != LiveGameStatus.Section.UPCOMING && !s.hasNoGame();
    }

    /** Same labels as the schedule: "Top 4th", "Mid 4th"... */
    public String inningLabel() {
        return LiveGameSummary.inningLabel(this.inning, this.inningState);
    }
}
