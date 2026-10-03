package com.cj.mcbaseball.live.mlb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.LivePollingPolicy;
import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveGameStatus.State;
import com.cj.mcbaseball.live.model.LivePlayer;
import com.cj.mcbaseball.live.net.LiveDataException;
import org.junit.jupiter.api.Test;

/** Real live-feed responses for ATL @ LAD (849828), CWS @ CLE (849829) and NYY @ TB (849835), 2026-10-03. */
class MlbLiveFeedParserTest {

    private static final String REC = "recorded/849828_atl-lad_bot4/";

    private static LiveGameState feed(String gz, long pk) throws LiveDataException {
        return MlbLiveFeedParser.parse(Fixtures.mlbGz(gz), pk);
    }

    @Test
    void liveGameMidInning() throws Exception {
        LiveGameState s = feed("feeds/feed_849828_live_bot2.json.gz", 849828L);
        assertEquals(State.LIVE, s.status().state());
        assertEquals("ATL", s.away().displayAbbr());
        assertEquals("LAD", s.home().displayAbbr());
        assertEquals(2, s.inning());
        assertEquals("Bottom", s.inningState());
        assertFalse(s.isTop());
        assertFalse(s.isBreak());
        assertEquals(0, s.awayScore());
        assertEquals(0, s.homeScore());
        assertEquals(0, s.balls());
        assertEquals(0, s.strikes());
        assertEquals(2, s.outs());
        assertFalse(s.betweenBatters());
        assertEquals("Andy Pages", s.batter().fullName());
        assertEquals("A. Pages", s.batter().display());
        assertEquals("44", s.batter().jersey());
        assertEquals("Dylan Dodd", s.pitcher().fullName());
        assertEquals(0, s.basesMask());
        assertEquals("Kyle Tucker strikes out swinging.", s.lastPlay());
        assertEquals(10, s.suggestedPollSeconds());
        assertEquals("20261003_203817", s.feedTimestamp());
        assertEquals("UNIQLO Field at Dodger Stadium", s.venue());
        // Real batting orders, with game positions and jersey numbers.
        assertEquals(9, s.awayLineup().size());
        assertEquals(9, s.homeLineup().size());
        LivePlayer leadoff = s.awayLineup().get(0);
        assertEquals("Drake Baldwin", leadoff.fullName());
        assertEquals("DH", leadoff.position());
        assertEquals(1, leadoff.battingOrder());
        assertEquals("30", leadoff.jersey());
    }

    @Test
    void singleThenStolenBaseMovesTheRunner() throws Exception {
        LiveGameState afterSingle = feed(REC + "20261003_211159.json.gz", 849828L);
        assertEquals(2, afterSingle.homeScore(), "home run earlier in the inning");
        assertEquals("Kyle Tucker", afterSingle.runnerOnFirst().fullName());
        // Between at-bats: the feed still names Tucker as batter with his final 1-0 count. Next up is Pages.
        assertTrue(afterSingle.betweenBatters());
        assertEquals("Andy Pages", afterSingle.batter().fullName());
        assertEquals("Max Muncy", afterSingle.onDeck().fullName());
        assertEquals(0, afterSingle.balls());
        assertEquals(0, afterSingle.strikes());
        assertEquals(1, afterSingle.outs());

        LiveGameState nextAtBat = feed(REC + "20261003_211213.json.gz", 849828L);
        assertFalse(nextAtBat.betweenBatters());
        assertEquals("Andy Pages", nextAtBat.batter().fullName(), "the next snapshot confirms who was due up");
        assertEquals(1, afterSingle.basesMask());
        assertEquals("FF", afterSingle.lastPitch().typeCode());
        assertEquals("Four-Seam Fastball", afterSingle.lastPitch().typeName());
        assertEquals(97.3, afterSingle.lastPitch().mph(), 1e-9);
        assertEquals("In play, no out", afterSingle.lastPitch().call());
        assertTrue(afterSingle.lastPitch().isInPlay());
        assertFalse(Double.isNaN(afterSingle.lastPitch().plateX()));
        assertEquals("Kyle Tucker singles on a fly ball to left fielder Mauricio Dubón.", afterSingle.lastPlay());

        LiveGameState afterSteal = feed(REC + "20261003_211803.json.gz", 849828L);
        assertFalse(afterSteal.runnerOnFirst().known());
        assertEquals("Kyle Tucker", afterSteal.runnerOnSecond().fullName());
        assertEquals(2, afterSteal.basesMask());
        assertEquals(1, afterSteal.balls());
        assertEquals(2, afterSteal.strikes());
        assertEquals(2, afterSteal.outs());
        assertEquals("Max Muncy", afterSteal.batter().fullName());
        assertEquals("AJ Smith-Shawver", afterSteal.pitcher().fullName(), "second pitching change of the inning");
        assertEquals("Splitter", afterSteal.lastPitch().typeName());
        assertEquals(87.3, afterSteal.lastPitch().mph(), 1e-9);
    }

    /** At "End" the linescore has already flipped to the next half: due-up batter is an Atlanta player. */
    @Test
    void inningBreakShowsWhoIsDueUp() throws Exception {
        LiveGameState s = feed(REC + "20261003_211836.json.gz", 849828L);
        assertEquals("End", s.inningState());
        assertTrue(s.isBreak());
        assertEquals("End 4th", s.inningLabel());
        assertEquals(3, s.outs());
        assertFalse(s.betweenBatters(), "breaks keep the linescore's due-up batter");
        assertEquals("Michael Harris II", s.batter().fullName());
        assertEquals("M. Harris II", s.batter().display());
        assertEquals("23", s.batter().jersey(), "jersey must come from the away boxscore, not a guessed team");
        assertEquals("Tarik Skubal", s.pitcher().fullName());
        assertEquals(0, s.basesMask());
        assertEquals("Max Muncy lines out to first baseman Matt Olson.", s.lastPlay());
    }

    @Test
    void finalGameStopsPolling() throws Exception {
        LiveGameState s = feed("feeds/feed_849829_final.json.gz", 849829L);
        assertEquals(State.FINAL, s.status().state());
        assertEquals(3, s.awayScore());
        assertEquals(0, s.homeScore());
        assertEquals(9, s.inning());
        assertEquals("Curveball", s.lastPitch().typeName());
        assertEquals(85.6, s.lastPitch().mph(), 1e-9);
        assertEquals("Travis Bazzana strikes out swinging.", s.lastPlay());
        assertEquals(Long.MAX_VALUE, LivePollingPolicy.defaults().feedIntervalMillis(s, System.currentTimeMillis()));
    }

    @Test
    void pregameHasLineupsButNoScore() throws Exception {
        LiveGameState s = feed("feeds/feed_849835_pregame.json.gz", 849835L);
        assertEquals(State.PREGAME, s.status().state());
        assertFalse(s.showsScore());
        assertEquals("NYY", s.away().displayAbbr());
        assertEquals("B. Rice", s.batter().display());
        assertEquals("22", s.batter().jersey());
        assertEquals("Drew Rasmussen", s.pitcher().fullName());
        assertEquals(9, s.awayLineup().size());
        assertTrue(s.startEpochMillis() > 0);
    }

    @Test
    void rejectsWrongGameAndGarbage() {
        String body = Fixtures.mlbGz("feeds/feed_849829_final.json.gz");
        assertEquals(LiveDataException.Kind.INVALID_RESPONSE, assertThrows(LiveDataException.class, () -> MlbLiveFeedParser.parse(body, 1L)).kind());
        for (String bad : new String[]{"", "null", "[]", "{\"copyright\":\"x\"}", "{\"gameData\": ", "<html>"}) {
            assertThrows(LiveDataException.class, () -> MlbLiveFeedParser.parse(bad, 5L), bad);
        }
    }

    @Test
    void sparseFeedDegradesGracefully() throws Exception {
        LiveGameState s = MlbLiveFeedParser.parse("{\"gamePk\": 5, \"gameData\": {\"status\": null, \"players\": []},"
            + " \"liveData\": {\"linescore\": {\"offense\": {\"batter\": {\"id\": 99, \"fullName\": \"Mystery Man\"}, \"first\": \"nope\"},"
            + " \"balls\": \"two\"}, \"plays\": {\"allPlays\": [null, {\"about\": {\"isComplete\": true}}]}}}", 5L);
        assertEquals(State.UNKNOWN, s.status().state());
        assertEquals("Mystery Man", s.batter().fullName());
        assertEquals("Mystery Man", s.batter().display());
        assertEquals(-1, s.balls());
        assertEquals(0, s.basesMask());
        assertFalse(s.pitcher().known());
        assertFalse(s.lastPitch().known());
        assertEquals("", s.lastPlay());
        assertTrue(s.awayLineup().isEmpty());
    }

    @Test
    void battingSlots() {
        assertEquals(3, MlbLiveFeedParser.battingSlot("300"));
        assertEquals(3, MlbLiveFeedParser.battingSlot("301"));
        assertEquals(9, MlbLiveFeedParser.battingSlot("900"));
        assertEquals(-1, MlbLiveFeedParser.battingSlot(""));
        assertEquals(-1, MlbLiveFeedParser.battingSlot("0"));
        assertEquals(-1, MlbLiveFeedParser.battingSlot("1000"));
        assertEquals(-1, MlbLiveFeedParser.battingSlot("abc"));
    }
}
