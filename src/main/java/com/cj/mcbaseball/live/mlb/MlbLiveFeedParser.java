package com.cj.mcbaseball.live.mlb;

import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveHit;
import com.cj.mcbaseball.live.model.LivePlay;
import com.cj.mcbaseball.live.model.LivePlayEvent;
import com.cj.mcbaseball.live.model.LiveRunner;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LiveLineTotals;
import com.cj.mcbaseball.live.model.LivePitch;
import com.cj.mcbaseball.live.model.LivePlayer;
import com.cj.mcbaseball.live.net.Json;
import com.cj.mcbaseball.live.net.LiveDataException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Parses {@code /api/v1.1/game/{gamePk}/feed/live} into a {@link LiveGameState}.
 *
 * <p>Notes from real responses (see the recorded fixtures):
 * <ul>
 *   <li>Batter / pitcher / runners come from {@code liveData.linescore.offense/defense}. At an inning
 *       break ("Middle"/"End") these have already flipped to the next half-inning, while
 *       {@code currentPlay} still points at the last at-bat; the linescore is what MLB Gameday shows.</li>
 *   <li>Player names/numbers: {@code gameData.players["ID<id>"]} plus the boxscore entry of whichever
 *       team has the player (don't guess the team from the half-inning; that's wrong at breaks).</li>
 *   <li>Boxscore {@code battingOrder} is "300" = slot 3 starter, "301" = first sub in slot 3.</li>
 *   <li>Missing objects, nulls and type changes produce defaults, never exceptions.</li>
 * </ul>
 */
public final class MlbLiveFeedParser {

    private static final int MAX_INNINGS = 30;
    private static final int MAX_LINEUP = 12;

    private final JsonObject gameData;
    private final JsonObject players;
    private final JsonObject boxAway;
    private final JsonObject boxHome;

    private MlbLiveFeedParser(JsonObject root) {
        this.gameData = Json.obj(root, "gameData");
        this.players = Json.obj(this.gameData, "players");
        JsonObject boxTeams = Json.obj(root, "liveData", "boxscore", "teams");
        this.boxAway = Json.obj(boxTeams, "away", "players");
        this.boxHome = Json.obj(boxTeams, "home", "players");
    }

    /**
     * @param expectedGameId the game that was requested; a response for a different game is rejected
     * @throws LiveDataException if the document is not a live feed at all
     */
    public static LiveGameState parse(String body, long expectedGameId) throws LiveDataException {
        return parseFeed(body, expectedGameId).state();
    }

    /** Game state plus the full play-by-play. */
    public static LiveFeed parseFeed(String body, long expectedGameId) throws LiveDataException {
        JsonObject root = Json.parseObject(body);
        if (Json.obj(root, "gameData") == null && Json.obj(root, "liveData") == null) {
            throw new LiveDataException(LiveDataException.Kind.INVALID_RESPONSE, "not a live feed document");
        }
        long pk = Json.lng(root, "gamePk", Json.lng(Json.obj(root, "gameData", "game"), "pk", expectedGameId));
        if (pk != expectedGameId) {
            throw new LiveDataException(LiveDataException.Kind.INVALID_RESPONSE, "feed is for game " + pk + ", expected " + expectedGameId);
        }
        MlbLiveFeedParser p = new MlbLiveFeedParser(root);
        return new LiveFeed(p.build(root, pk), p.plays(Json.arr(Json.obj(root, "liveData", "plays"), "allPlays")));
    }

    private static final int MAX_PLAYS = 400;
    private static final int MAX_EVENTS_PER_PLAY = 80;

    private List<LivePlay> plays(@Nullable JsonArray all) {
        List<LivePlay> out = new ArrayList<>();
        if (all == null) {
            return out;
        }
        for (int i = 0; i < all.size() && out.size() < MAX_PLAYS; i++) {
            JsonObject p = Json.objAt(all, i);
            JsonObject about = Json.obj(p, "about");
            int ab = Json.integer(about, "atBatIndex", Json.integer(p, "atBatIndex", -1));
            if (p == null || ab < 0) {
                continue;
            }
            JsonObject result = Json.obj(p, "result");
            JsonObject matchup = Json.obj(p, "matchup");
            List<LivePlayEvent> events = new ArrayList<>();
            JsonArray pe = Json.arr(p, "playEvents");
            if (pe != null) {
                for (int j = 0; j < pe.size() && events.size() < MAX_EVENTS_PER_PLAY; j++) {
                    LivePlayEvent e = this.playEvent(Json.objAt(pe, j), j);
                    if (e != null) {
                        events.add(e);
                    }
                }
            }
            List<LiveRunner> runners = new ArrayList<>();
            JsonArray rs = Json.arr(p, "runners");
            if (rs != null) {
                for (int j = 0; j < rs.size() && runners.size() < 20; j++) {
                    LiveRunner r = this.runner(Json.objAt(rs, j));
                    if (r != null) {
                        runners.add(r);
                    }
                }
            }
            out.add(new LivePlay(
                ab,
                Json.integer(about, "inning", -1),
                Json.bool(about, "isTopInning", "top".equalsIgnoreCase(Json.str(about, "halfInning"))),
                Json.bool(about, "isComplete", false),
                this.player(Json.obj(matchup, "batter")),
                this.player(Json.obj(matchup, "pitcher")),
                Json.str(result, "eventType"),
                Json.str(result, "event"),
                Json.str(result, "description"),
                Json.integer(result, "rbi", 0),
                Json.integer(result, "awayScore", -1),
                Json.integer(result, "homeScore", -1),
                Json.bool(result, "isOut", false),
                Json.integer(Json.obj(p, "count"), "outs", -1),
                events,
                runners,
                MlbScheduleParser.startMillis(Json.str(about, "endTime", Json.str(p, "playEndTime")))
            ));
        }
        out.sort(java.util.Comparator.comparingInt(LivePlay::atBatIndex));
        return out;
    }

    @Nullable
    private LivePlayEvent playEvent(@Nullable JsonObject e, int fallbackIndex) {
        if (e == null) {
            return null;
        }
        JsonObject details = Json.obj(e, "details");
        boolean isPitch = Json.bool(e, "isPitch", false);
        String type = Json.str(e, "type");
        LivePlayEvent.Kind kind = isPitch ? LivePlayEvent.Kind.PITCH : switch (type) {
            case "action" -> LivePlayEvent.Kind.ACTION;
            case "pickoff" -> LivePlayEvent.Kind.PICKOFF;
            case "stepoff" -> LivePlayEvent.Kind.STEPOFF;
            case "no_pitch" -> LivePlayEvent.Kind.NO_PITCH;
            default -> LivePlayEvent.Kind.OTHER;
        };
        JsonObject count = Json.obj(e, "count");
        JsonObject hd = Json.obj(e, "hitData");
        LiveHit hit = null;
        if (hd != null) {
            JsonObject c = Json.obj(hd, "coordinates");
            hit = new LiveHit(
                Json.dbl(hd, "launchSpeed", Double.NaN), Json.dbl(hd, "launchAngle", Double.NaN), Json.dbl(hd, "totalDistance", Double.NaN),
                Json.str(hd, "trajectory"), Json.str(hd, "hardness"), Json.str(hd, "location"), Json.dbl(c, "coordX", Double.NaN), Json.dbl(c, "coordY", Double.NaN)
            );
        }
        String eventType = Json.str(details, "eventType");
        if (eventType.isEmpty() && kind == LivePlayEvent.Kind.PICKOFF) {
            eventType = "pickoff_attempt";
        }
        return new LivePlayEvent(
            Json.integer(e, "index", fallbackIndex),
            kind,
            Json.str(e, "playId"),
            isPitch ? pitch(e) : LivePitch.NONE,
            hit,
            isPitch ? "" : eventType,
            Json.str(details, "description"),
            Json.bool(e, "isSubstitution", false),
            this.player(Json.obj(e, "player")),
            Json.str(Json.obj(e, "position"), "abbreviation"),
            Json.integer(count, "balls", -1),
            Json.integer(count, "strikes", -1),
            Json.integer(count, "outs", -1),
            MlbScheduleParser.startMillis(Json.str(e, "startTime"))
        );
    }

    @Nullable
    private LiveRunner runner(@Nullable JsonObject r) {
        if (r == null) {
            return null;
        }
        JsonObject mv = Json.obj(r, "movement");
        JsonObject d = Json.obj(r, "details");
        List<LiveRunner.Credit> credits = new ArrayList<>();
        JsonArray cs = Json.arr(r, "credits");
        if (cs != null) {
            for (int i = 0; i < cs.size() && credits.size() < 12; i++) {
                JsonObject c = Json.objAt(cs, i);
                if (c != null) {
                    credits.add(new LiveRunner.Credit(
                        Json.integer(Json.obj(c, "player"), "id", 0), Json.str(Json.obj(c, "position"), "abbreviation"), Json.str(c, "credit")));
                }
            }
        }
        return new LiveRunner(
            this.player(Json.obj(d, "runner")),
            Json.str(mv, "start"),
            Json.str(mv, "end"),
            Json.str(mv, "outBase"),
            Json.bool(mv, "isOut", false),
            Json.integer(mv, "outNumber", -1),
            Json.str(d, "eventType"),
            Json.str(d, "movementReason"),
            Json.bool(d, "isScoringEvent", false),
            Json.integer(d, "playIndex", -1),
            credits
        );
    }

    private LiveGameState build(JsonObject root, long pk) {
        JsonObject teams = Json.obj(this.gameData, "teams");
        JsonObject ls = Json.obj(root, "liveData", "linescore");
        JsonObject lsTeams = Json.obj(ls, "teams");
        JsonObject offense = Json.obj(ls, "offense");
        JsonObject defense = Json.obj(ls, "defense");
        JsonObject plays = Json.obj(root, "liveData", "plays");
        JsonObject currentPlay = Json.obj(plays, "currentPlay");

        LiveLineTotals awayTotals = totals(Json.obj(lsTeams, "away"));
        LiveLineTotals homeTotals = totals(Json.obj(lsTeams, "home"));

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

        // Batter/pitcher: linescore first (correct at breaks), current play's matchup as fallback.
        JsonObject matchup = Json.obj(currentPlay, "matchup");
        LivePlayer batter = this.player(firstObj(Json.obj(offense, "batter"), Json.obj(matchup, "batter")));
        LivePlayer pitcher = this.player(firstObj(Json.obj(defense, "pitcher"), Json.obj(matchup, "pitcher")));

        int balls = Json.integer(ls, "balls", Json.integer(Json.obj(currentPlay, "count"), "balls", LiveGameSummary.UNKNOWN));
        int strikes = Json.integer(ls, "strikes", Json.integer(Json.obj(currentPlay, "count"), "strikes", LiveGameSummary.UNKNOWN));
        int outs = Json.integer(ls, "outs", Json.integer(Json.obj(currentPlay, "count"), "outs", LiveGameSummary.UNKNOWN));
        LivePlayer onDeck = this.player(Json.obj(offense, "onDeck"));

        // Between at-bats mid-inning the feed still names the batter who just finished (often now a
        // runner) with his final count; the next man up is "onDeck". Verified on recorded snapshots:
        // 211159 (Tucker done, on 1B, onDeck Pages) -> 211213 (Pages batting, 0-0).
        boolean breakNow = "Middle".equals(inningState) || "End".equals(inningState);
        boolean betweenBatters = false;
        if (!breakNow && Json.bool(Json.obj(currentPlay, "about"), "isComplete", false) && onDeck.known()) {
            betweenBatters = true;
            batter = onDeck;
            onDeck = this.player(Json.obj(offense, "inHole"));
            balls = 0;
            strikes = 0;
        }

        JsonObject meta = Json.obj(root, "metaData");
        return new LiveGameState(
            pk,
            MlbStatusMapper.map(Json.obj(this.gameData, "status")),
            MlbScheduleParser.team(Json.obj(teams, "away")),
            MlbScheduleParser.team(Json.obj(teams, "home")),
            MlbScheduleParser.startMillis(Json.str(Json.obj(this.gameData, "datetime"), "dateTime")),
            awayTotals.runs(),
            homeTotals.runs(),
            Json.integer(ls, "currentInning", LiveGameSummary.UNKNOWN),
            inningState,
            balls,
            strikes,
            outs,
            betweenBatters,
            batter,
            pitcher,
            onDeck,
            this.player(Json.obj(offense, "first")),
            this.player(Json.obj(offense, "second")),
            this.player(Json.obj(offense, "third")),
            awayTotals,
            homeTotals,
            awayInnings,
            homeInnings,
            lastPitch(Json.arr(plays, "allPlays"), currentPlay),
            lastCompletePlay(Json.arr(plays, "allPlays")),
            Json.str(Json.obj(this.gameData, "venue"), "name"),
            Json.str(meta, "timeStamp"),
            Math.max(0, Json.integer(meta, "wait", 0)),
            this.lineup(Json.obj(root, "liveData", "boxscore", "teams", "away")),
            this.lineup(Json.obj(root, "liveData", "boxscore", "teams", "home"))
        );
    }

    @Nullable
    private static JsonObject firstObj(@Nullable JsonObject a, @Nullable JsonObject b) {
        return a != null ? a : b;
    }

    private static LiveLineTotals totals(@Nullable JsonObject t) {
        if (t == null) {
            return LiveLineTotals.NONE;
        }
        return new LiveLineTotals(
            Json.integer(t, "runs", -1), Json.integer(t, "hits", -1), Json.integer(t, "errors", -1), Json.integer(t, "leftOnBase", -1)
        );
    }

    /** Resolves a {"id":..,"fullName":..} reference into a full player. */
    LivePlayer player(@Nullable JsonObject ref) {
        if (ref == null) {
            return LivePlayer.NONE;
        }
        int id = Json.integer(ref, "id", 0);
        return this.playerById(id, Json.str(ref, "fullName"));
    }

    private LivePlayer playerById(int id, String fallbackName) {
        if (id <= 0 && fallbackName.isEmpty()) {
            return LivePlayer.NONE;
        }
        String key = "ID" + id;
        JsonObject person = Json.obj(this.players, key);
        JsonObject box = Json.obj(this.boxAway, key);
        if (box == null) {
            box = Json.obj(this.boxHome, key);
        }
        String full = Json.str(person, "fullName", fallbackName);
        if (full.isEmpty()) {
            full = Json.str(Json.obj(box, "person"), "fullName", fallbackName);
        }
        String first = Json.str(person, "useName", Json.str(person, "firstName"));
        String last = Json.str(person, "useLastName", Json.str(person, "lastName"));
        String jersey = Json.str(box, "jerseyNumber", Json.str(person, "primaryNumber"));
        String pos = Json.str(Json.obj(box, "position"), "abbreviation", Json.str(Json.obj(person, "primaryPosition"), "abbreviation"));
        return new LivePlayer(
            id,
            full,
            LivePlayer.shortName(first, last, full),
            jersey,
            pos,
            Json.str(Json.obj(person, "batSide"), "code"),
            Json.str(Json.obj(person, "pitchHand"), "code"),
            battingSlot(Json.str(box, "battingOrder"))
        );
    }

    /** "300" -> 3, "301" -> 3, anything else -> -1. */
    static int battingSlot(String order) {
        try {
            int n = Integer.parseInt(order.trim());
            int slot = n / 100;
            return slot >= 1 && slot <= 9 ? slot : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private List<LivePlayer> lineup(@Nullable JsonObject boxTeam) {
        List<LivePlayer> out = new ArrayList<>();
        JsonArray order = Json.arr(boxTeam, "battingOrder");
        if (order == null) {
            return out;
        }
        for (int i = 0; i < order.size() && out.size() < MAX_LINEUP; i++) {
            JsonElement e = order.get(i);
            int id;
            try {
                id = e != null && e.isJsonPrimitive() ? e.getAsInt() : 0;
            } catch (RuntimeException ex) {
                id = 0;
            }
            if (id > 0) {
                out.add(this.playerById(id, ""));
            }
        }
        return out;
    }

    /** Most recent pitch in the game: current at-bat first, then earlier at-bats. */
    static LivePitch lastPitch(@Nullable JsonArray allPlays, @Nullable JsonObject currentPlay) {
        LivePitch p = lastPitchIn(currentPlay);
        if (p != null || allPlays == null) {
            return p == null ? LivePitch.NONE : p;
        }
        for (int i = allPlays.size() - 1; i >= 0; i--) {
            p = lastPitchIn(Json.objAt(allPlays, i));
            if (p != null) {
                return p;
            }
        }
        return LivePitch.NONE;
    }

    @Nullable
    private static LivePitch lastPitchIn(@Nullable JsonObject play) {
        JsonArray events = Json.arr(play, "playEvents");
        if (events == null) {
            return null;
        }
        for (int i = events.size() - 1; i >= 0; i--) {
            JsonObject e = Json.objAt(events, i);
            if (e != null && Json.bool(e, "isPitch", false)) {
                return pitch(e);
            }
        }
        return null;
    }

    static LivePitch pitch(JsonObject e) {
        JsonObject details = Json.obj(e, "details");
        JsonObject type = Json.obj(details, "type");
        JsonObject pd = Json.obj(e, "pitchData");
        JsonObject coords = Json.obj(pd, "coordinates");
        JsonObject count = Json.obj(e, "count");
        String call = Json.str(Json.obj(details, "call"), "description", Json.str(details, "description"));
        return new LivePitch(
            Json.str(e, "playId"),
            Json.str(type, "code"),
            Json.str(type, "description"),
            Json.dbl(pd, "startSpeed", -1),
            call,
            Json.bool(details, "isStrike", false),
            Json.bool(details, "isBall", false),
            Json.bool(details, "isInPlay", false),
            Json.dbl(coords, "pX", Double.NaN),
            Json.dbl(coords, "pZ", Double.NaN),
            Json.dbl(pd, "strikeZoneTop", Double.NaN),
            Json.dbl(pd, "strikeZoneBottom", Double.NaN),
            Json.integer(count, "balls", -1),
            Json.integer(count, "strikes", -1)
        );
    }

    /** Description of the latest finished at-bat ("Kyle Tucker singles on a fly ball to left fielder..."). */
    static String lastCompletePlay(@Nullable JsonArray allPlays) {
        if (allPlays == null) {
            return "";
        }
        for (int i = allPlays.size() - 1; i >= 0; i--) {
            JsonObject p = Json.objAt(allPlays, i);
            if (Json.bool(Json.obj(p, "about"), "isComplete", false)) {
                return Json.str(Json.obj(p, "result"), "description");
            }
        }
        return "";
    }
}
