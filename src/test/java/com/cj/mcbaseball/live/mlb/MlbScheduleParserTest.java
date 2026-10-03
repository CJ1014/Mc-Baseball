package com.cj.mcbaseball.live.mlb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.model.LiveGameStatus.Section;
import com.cj.mcbaseball.live.model.LiveGameStatus.State;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.net.LiveDataException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MlbScheduleParserTest {

    /** Real schedule captured 2026-10-03 ~20:38Z: one final, one live (Bot 2nd), two pre-game. */
    @Test
    void realMixedDay() throws Exception {
        List<LiveGameSummary> games = MlbScheduleParser.parse(Fixtures.mlb("schedule_2026-10-03_mixed.json"));
        assertEquals(4, games.size());

        // Display order: live, upcoming by start time, final.
        assertEquals(List.of(849828L, 849835L, 849830L, 849829L), games.stream().map(LiveGameSummary::gameId).toList());
        assertEquals(List.of(Section.LIVE, Section.UPCOMING, Section.UPCOMING, Section.FINAL), games.stream().map(LiveGameSummary::section).toList());

        LiveGameSummary live = games.get(0);
        assertEquals(State.LIVE, live.status().state());
        assertEquals("ATL", live.away().displayAbbr());
        assertEquals("LAD", live.home().displayAbbr());
        assertEquals("Braves", live.away().displayShort());
        assertEquals("Los Angeles Dodgers", live.home().displayFull());
        assertEquals(0, live.awayScore());
        assertEquals(0, live.homeScore());
        assertEquals(2, live.inning());
        assertEquals("Bottom", live.inningState());
        assertEquals("Bot 2nd", live.inningLabel());
        assertEquals(1, live.outs());
        assertEquals(2, live.balls());
        assertEquals(2, live.strikes());
        assertTrue(live.showsScore());
        assertEquals("UNIQLO Field at Dodger Stadium", live.venue());
        assertEquals("NLDS 'B' Game 1", live.description());
        assertEquals(Instant.parse("2026-10-03T20:00:00Z").toEpochMilli(), live.startEpochMillis());
        // Home hasn't finished batting in the 2nd: the feed omits "runs" for that half.
        assertEquals(List.of(0, 0), live.awayInningRuns());
        assertEquals(List.of(0, LiveGameSummary.UNKNOWN), live.homeInningRuns());
        assertEquals(1, live.awayTotals().hits());

        LiveGameSummary pre = games.get(1);
        assertEquals("NYY @ TB", pre.matchupAbbr());
        assertEquals(State.PREGAME, pre.status().state());
        // Pre-game linescore claims "Top 1st, 0-0"; we must not present that as a score.
        assertFalse(pre.showsScore());

        LiveGameSummary fin = games.get(3);
        assertEquals(State.FINAL, fin.status().state());
        assertEquals("CWS @ CLE", fin.matchupAbbr());
        assertEquals(3, fin.awayScore());
        assertEquals(0, fin.homeScore());
        assertTrue(fin.showsScore());
        assertEquals(9, fin.awayInningRuns().size());
    }

    @Test
    void realRainout() throws Exception {
        List<LiveGameSummary> games = MlbScheduleParser.parse(Fixtures.mlb("schedule_2025-04-05_postponed.json"));
        LiveGameSummary g = games.stream().filter(x -> x.gameId() == 778443L).findFirst().orElseThrow();
        assertEquals(State.POSTPONED, g.status().state());
        assertEquals(Section.FINAL, g.section());
        assertEquals("POSTPONED: RAIN", g.status().label());
        assertTrue(g.status().state().hasNoGame());
        assertFalse(g.showsScore());
        assertTrue(games.size() > 10);
    }

    @Test
    void realCompletedEarly() throws Exception {
        List<LiveGameSummary> games = MlbScheduleParser.parse(Fixtures.mlb("schedule_2025-07-08_completed_early.json"));
        LiveGameSummary g = games.stream().filter(x -> x.gameId() == 777188L).findFirst().orElseThrow();
        assertEquals(State.FINAL, g.status().state());
        assertEquals("COMPLETED EARLY", g.status().label());
        assertEquals(7, g.inning());
    }

    @Test
    void realEmptyDay() throws Exception {
        assertTrue(MlbScheduleParser.parse(Fixtures.mlb("schedule_2025-12-25_empty.json")).isEmpty());
    }

    @Test
    void garbageIsRejectedCleanly() {
        for (String bad : new String[]{null, "", "   ", "null", "[]", "42", "\"str\"", "{", "{\"dates\": [", "<html>503 Service Unavailable</html>", "{\"foo\": 1}"}) {
            LiveDataException e = assertThrows(LiveDataException.class, () -> MlbScheduleParser.parse(bad), String.valueOf(bad));
            assertEquals(LiveDataException.Kind.INVALID_RESPONSE, e.kind());
        }
    }

    @Test
    void oddShapesDegradeGracefully() throws Exception {
        assertTrue(MlbScheduleParser.parse("{\"dates\": null}").isEmpty());
        assertTrue(MlbScheduleParser.parse("{\"totalGames\": 0}").isEmpty());
        assertTrue(MlbScheduleParser.parse("{\"dates\": {\"not\": \"an array\"}}").isEmpty());
        assertTrue(MlbScheduleParser.parse("{\"dates\": [null, 5, \"x\", {\"games\": null}, {\"games\": [null, 1, []]}]}").isEmpty());

        List<String> skipped = new ArrayList<>();
        List<LiveGameSummary> games = MlbScheduleParser.parse(
            "{\"dates\": [{\"date\": \"2026-10-03\", \"games\": ["
                + "{},"                                       // no gamePk: skipped
                + "{\"gamePk\": \"abc\"},"                    // unusable gamePk: skipped
                + "{\"gamePk\": 5},"                          // bare minimum
                + "{\"gamePk\": \"6\", \"teams\": [], \"status\": \"Live\", \"gameDate\": \"yesterday-ish\","
                + "  \"linescore\": {\"currentInning\": \"seven\", \"innings\": [null, {\"away\": {\"runs\": \"2\"}}]}},"
                + "{\"gamePk\": 7, \"teams\": {\"away\": {\"score\": \"abc\", \"team\": {\"name\": \"Mudville Nine\"}}, \"home\": {\"score\": 4.0, \"team\": null}}}"
                + "]}]}",
            skipped::add
        );
        assertEquals(2, skipped.size());
        assertEquals(List.of(5L, 6L, 7L), games.stream().map(LiveGameSummary::gameId).toList());

        LiveGameSummary bare = games.get(0);
        assertEquals(State.UNKNOWN, bare.status().state());
        assertEquals("???", bare.away().displayAbbr());
        assertEquals(0L, bare.startEpochMillis());
        assertEquals("", bare.inningLabel());
        assertEquals("2026-10-03", bare.officialDate());

        LiveGameSummary wrongTypes = games.get(1);
        assertEquals(LiveGameSummary.UNKNOWN, wrongTypes.inning());
        assertEquals(List.of(LiveGameSummary.UNKNOWN, 2), wrongTypes.awayInningRuns());

        LiveGameSummary partial = games.get(2);
        assertEquals("MUD", partial.away().displayAbbr());
        assertEquals("Mudville Nine", partial.away().displayShort());
        assertEquals(LiveGameSummary.UNKNOWN, partial.awayScore());
        assertEquals(4, partial.homeScore());
        assertFalse(partial.showsScore());
    }

    @Test
    void duplicatesAndFloodsAreBounded() throws Exception {
        StringBuilder b = new StringBuilder("{\"dates\": [{\"games\": [");
        for (int i = 0; i < 500; i++) {
            b.append(i == 0 ? "" : ",").append("{\"gamePk\": ").append(1000 + (i % 200)).append('}');
        }
        b.append("]}, {\"games\": [{\"gamePk\": 1000}]}]}");
        List<LiveGameSummary> games = MlbScheduleParser.parse(b.toString());
        assertEquals(MlbScheduleParser.MAX_GAMES, games.size());
        assertEquals(games.size(), games.stream().map(LiveGameSummary::gameId).distinct().count());
    }
}
