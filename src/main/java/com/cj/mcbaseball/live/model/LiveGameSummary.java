package com.cj.mcbaseball.live.model;

import java.util.List;

/**
 * One game on a day's schedule: enough to list it in the browser and to start watching it.
 * Integer fields use {@link #UNKNOWN} (-1) when the provider did not report them.
 *
 * @param gameId          provider's internal game id (MLB "gamePk"). Players never see or type it.
 * @param startEpochMillis scheduled first pitch, or 0 if unknown
 * @param inningState     "Top", "Middle", "Bottom", "End" or "" when unknown
 * @param awayInningRuns  runs per inning for the away side (index 0 = 1st), UNKNOWN for innings not played
 */
public record LiveGameSummary(
    long gameId,
    String officialDate,
    LiveTeam away,
    LiveTeam home,
    LiveGameStatus status,
    long startEpochMillis,
    boolean startTimeTbd,
    int awayScore,
    int homeScore,
    int inning,
    String inningState,
    int balls,
    int strikes,
    int outs,
    LiveLineTotals awayTotals,
    LiveLineTotals homeTotals,
    List<Integer> awayInningRuns,
    List<Integer> homeInningRuns,
    String venue,
    String description,
    int gameNumber,
    boolean doubleHeader
) {
    public static final int UNKNOWN = -1;

    public LiveGameSummary {
        officialDate = officialDate == null ? "" : officialDate;
        away = away == null ? LiveTeam.UNKNOWN : away;
        home = home == null ? LiveTeam.UNKNOWN : home;
        status = status == null ? LiveGameStatus.of(LiveGameStatus.State.UNKNOWN) : status;
        inningState = inningState == null ? "" : inningState;
        awayTotals = awayTotals == null ? LiveLineTotals.NONE : awayTotals;
        homeTotals = homeTotals == null ? LiveLineTotals.NONE : homeTotals;
        awayInningRuns = awayInningRuns == null ? List.of() : List.copyOf(awayInningRuns);
        homeInningRuns = homeInningRuns == null ? List.of() : List.copyOf(homeInningRuns);
        venue = venue == null ? "" : venue;
        description = description == null ? "" : description;
    }

    public LiveGameStatus.Section section() {
        return this.status.state().section();
    }

    /** Scores are only meaningful once a game has started (pre-game feeds report 0-0). */
    public boolean showsScore() {
        LiveGameStatus.State s = this.status.state();
        return this.awayScore >= 0 && this.homeScore >= 0 && (s.section() != LiveGameStatus.Section.UPCOMING) && !s.hasNoGame();
    }

    /** "Top 4th", "Mid 4th", "Bot 7th", "End 9th", or "" if unknown. */
    public String inningLabel() {
        if (this.inning <= 0) {
            return "";
        }
        String half = switch (this.inningState) {
            case "Top" -> "Top";
            case "Middle" -> "Mid";
            case "Bottom" -> "Bot";
            case "End" -> "End";
            default -> "";
        };
        String ord = ordinal(this.inning);
        return half.isEmpty() ? ord : half + " " + ord;
    }

    public static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix;
        if (mod100 >= 11 && mod100 <= 13) {
            suffix = "th";
        } else {
            suffix = switch (n % 10) {
                case 1 -> "st";
                case 2 -> "nd";
                case 3 -> "rd";
                default -> "th";
            };
        }
        return n + suffix;
    }

    /** "NYY @ BOS" */
    public String matchupAbbr() {
        return this.away.displayAbbr() + " @ " + this.home.displayAbbr();
    }

    /** "Yankees @ Red Sox" */
    public String matchupShort() {
        return this.away.displayShort() + " @ " + this.home.displayShort();
    }
}
