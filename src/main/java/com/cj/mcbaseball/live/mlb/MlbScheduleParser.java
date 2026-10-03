package com.cj.mcbaseball.live.mlb;

import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LiveLineTotals;
import com.cj.mcbaseball.live.model.LiveTeam;
import com.cj.mcbaseball.live.net.Json;
import com.cj.mcbaseball.live.net.LiveDataException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Parses {@code /api/v1/schedule?sportId=1&date=...&hydrate=linescore,team}.
 *
 * <p>Shape notes from real responses: a day with no games has an empty {@code dates} array; innings
 * in progress omit {@code runs} for the side that hasn't batted; pre-game games carry a linescore
 * claiming "Top 1st" with 0-0 (we hide it via {@link LiveGameSummary#showsScore()}); the
 * {@code team} object only has abbreviations when {@code hydrate=team} is requested.
 */
public final class MlbScheduleParser {

    /** Hard cap so a broken/hostile response can't flood clients. */
    public static final int MAX_GAMES = 64;
    private static final int MAX_INNINGS = 30;

    private MlbScheduleParser() {
    }

    public static List<LiveGameSummary> parse(String body) throws LiveDataException {
        return parse(body, null);
    }

    /**
     * @param onSkip receives a reason for each game entry that couldn't be used (for logging); may be null
     * @throws LiveDataException only if the document as a whole is not a schedule
     */
    public static List<LiveGameSummary> parse(String body, @Nullable Consumer<String> onSkip) throws LiveDataException {
        JsonObject root = Json.parseObject(body);
        if (!root.has("dates") && !root.has("totalGames")) {
            throw new LiveDataException(LiveDataException.Kind.INVALID_RESPONSE, "not a schedule document");
        }
        List<LiveGameSummary> out = new ArrayList<>();
        JsonArray dates = Json.arr(root, "dates");
        if (dates == null) {
            return out;
        }
        for (int d = 0; d < dates.size(); d++) {
            JsonObject date = Json.objAt(dates, d);
            JsonArray games = Json.arr(date, "games");
            if (games == null) {
                continue;
            }
            for (int i = 0; i < games.size() && out.size() < MAX_GAMES; i++) {
                JsonObject g = Json.objAt(games, i);
                LiveGameSummary s = g == null ? null : parseGame(g, Json.str(date, "date"));
                if (s == null) {
                    if (onSkip != null) {
                        onSkip.accept("game entry " + i + " has no usable gamePk");
                    }
                    continue;
                }
                // The same game can be listed twice (e.g. a suspended game on its original and resumption date).
                if (out.stream().noneMatch(o -> o.gameId() == s.gameId())) {
                    out.add(s);
                }
            }
        }
        out.sort(DISPLAY_ORDER);
        return out;
    }

    /** Live games first, then upcoming by start time, then finished; ties by start time then id. */
    public static final Comparator<LiveGameSummary> DISPLAY_ORDER = Comparator
        .<LiveGameSummary>comparingInt(g -> g.section().ordinal())
        .thenComparingLong(g -> g.startEpochMillis() == 0 ? Long.MAX_VALUE : g.startEpochMillis())
        .thenComparingLong(LiveGameSummary::gameId);

    @Nullable
    static LiveGameSummary parseGame(JsonObject g, String fallbackDate) {
        long pk = Json.lng(g, "gamePk", -1L);
        if (pk <= 0) {
            return null;
        }
        LiveGameStatus status = MlbStatusMapper.map(Json.obj(g, "status"));
        JsonObject teams = Json.obj(g, "teams");
        JsonObject awaySide = Json.obj(teams, "away");
        JsonObject homeSide = Json.obj(teams, "home");
        JsonObject ls = Json.obj(g, "linescore");
        JsonObject lsTeams = Json.obj(ls, "teams");

        LiveLineTotals awayTotals = totals(Json.obj(lsTeams, "away"));
        LiveLineTotals homeTotals = totals(Json.obj(lsTeams, "home"));
        int awayScore = Json.integer(awaySide, "score", awayTotals.runs());
        int homeScore = Json.integer(homeSide, "score", homeTotals.runs());

        List<Integer> awayInnings = new ArrayList<>();
        List<Integer> homeInnings = new ArrayList<>();
        JsonArray innings = Json.arr(ls, "innings");
        if (innings != null) {
            for (int i = 0; i < innings.size() && i < MAX_INNINGS; i++) {
                JsonObject inn = Json.objAt(innings, i);
                awayInnings.add(Json.integer(Json.obj(inn, "away"), "runs", LiveGameSummary.UNKNOWN));
                homeInnings.add(Json.integer(Json.obj(inn, "home"), "runs", LiveGameSummary.UNKNOWN));
            }
        }

        String inningState = Json.str(ls, "inningState");
        if (inningState.isEmpty()) {
            inningState = Json.str(ls, "inningHalf");
        }

        return new LiveGameSummary(
            pk,
            Json.str(g, "officialDate", fallbackDate),
            team(Json.obj(awaySide, "team")),
            team(Json.obj(homeSide, "team")),
            status,
            startMillis(Json.str(g, "gameDate")),
            Json.bool(Json.obj(g, "status"), "startTimeTBD", false),
            awayScore,
            homeScore,
            Json.integer(ls, "currentInning", LiveGameSummary.UNKNOWN),
            inningState,
            Json.integer(ls, "balls", LiveGameSummary.UNKNOWN),
            Json.integer(ls, "strikes", LiveGameSummary.UNKNOWN),
            Json.integer(ls, "outs", LiveGameSummary.UNKNOWN),
            awayTotals,
            homeTotals,
            awayInnings,
            homeInnings,
            Json.str(Json.obj(g, "venue"), "name"),
            firstNonEmpty(Json.str(g, "description"), Json.str(g, "seriesDescription")),
            Json.integer(g, "gameNumber", 1),
            "Y".equals(Json.str(g, "doubleHeader")) || "S".equals(Json.str(g, "doubleHeader"))
        );
    }

    static LiveTeam team(@Nullable JsonObject t) {
        if (t == null) {
            return LiveTeam.UNKNOWN;
        }
        return new LiveTeam(
            Json.integer(t, "id", 0),
            Json.str(t, "name"),
            Json.str(t, "abbreviation"),
            firstNonEmpty(Json.str(t, "clubName"), Json.str(t, "teamName")),
            firstNonEmpty(Json.str(t, "locationName"), Json.str(t, "shortName"))
        );
    }

    private static LiveLineTotals totals(@Nullable JsonObject t) {
        if (t == null) {
            return LiveLineTotals.NONE;
        }
        return new LiveLineTotals(
            Json.integer(t, "runs", -1), Json.integer(t, "hits", -1), Json.integer(t, "errors", -1), Json.integer(t, "leftOnBase", -1)
        );
    }

    static long startMillis(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0L;
        }
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0L;
        }
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }
}
