package com.cj.mcbaseball.live.recreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.mlb.MlbLiveFeedParser;
import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LivePitch;
import com.cj.mcbaseball.live.model.LivePlay;
import com.cj.mcbaseball.live.model.LivePlayEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class LiveSwingTest {

    private static LivePitch p(String code, String call, boolean inPlay) {
        return new LivePitch("id", "FF", "", 95, call, false, false, inPlay, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0, code);
    }

    @Test
    void callCodes() {
        assertEquals(LiveSwing.TAKE, LiveSwing.of(p("B", "Ball", false)));
        assertEquals(LiveSwing.TAKE, LiveSwing.of(p("*B", "Ball In Dirt", false)));
        assertEquals(LiveSwing.TAKE, LiveSwing.of(p("C", "Called Strike", false)));
        assertEquals(LiveSwing.TAKE, LiveSwing.of(p("P", "Pitchout", false)));
        assertEquals(LiveSwing.TAKE, LiveSwing.of(p("V", "Automatic Ball", false)));
        assertEquals(LiveSwing.SWING_MISS, LiveSwing.of(p("S", "Swinging Strike", false)));
        assertEquals(LiveSwing.SWING_MISS, LiveSwing.of(p("W", "Swinging Strike (Blocked)", false)));
        assertEquals(LiveSwing.FOUL, LiveSwing.of(p("F", "Foul", false)));
        assertEquals(LiveSwing.FOUL_TIP, LiveSwing.of(p("T", "Foul Tip", false)));
        assertEquals(LiveSwing.BUNT_FOUL, LiveSwing.of(p("L", "Foul Bunt", false)));
        assertEquals(LiveSwing.BUNT_MISS, LiveSwing.of(p("M", "Missed Bunt", false)));
        assertEquals(LiveSwing.IN_PLAY, LiveSwing.of(p("X", "In play, out(s)", true)));
        assertEquals(LiveSwing.IN_PLAY, LiveSwing.of(p("D", "In play, no out", true)));
        assertEquals(LiveSwing.IN_PLAY, LiveSwing.of(p("E", "In play, run(s)", true)));
        assertEquals(LiveSwing.HIT_BY_PITCH, LiveSwing.of(p("H", "Hit By Pitch", false)));
    }

    @Test
    void unknownCodeFallsBackToDescription() {
        assertEquals(LiveSwing.FOUL, LiveSwing.of(p("ZZ", "Foul (Runner Going)", false)));
        assertEquals(LiveSwing.SWING_MISS, LiveSwing.of(p("", "Swinging Strike (Runner Going)", false)));
        assertEquals(LiveSwing.FOUL_TIP, LiveSwing.of(p("?", "Foul Tip", false)));
        assertEquals(LiveSwing.BUNT_FOUL, LiveSwing.of(p("?", "Foul Bunt", false)));
        assertEquals(LiveSwing.IN_PLAY, LiveSwing.of(p("?", "something new", true)));
        assertEquals(LiveSwing.HIT_BY_PITCH, LiveSwing.of(p("?", "Hit By Pitch", false)));
        // Nothing recognisable: the batter just takes the pitch (never an invented swing).
        assertEquals(LiveSwing.TAKE, LiveSwing.of(p("?", "", false)));
    }

    @Test
    void swingsAndBunts() {
        assertFalse(LiveSwing.TAKE.swings());
        assertFalse(LiveSwing.HIT_BY_PITCH.swings());
        assertTrue(LiveSwing.FOUL.swings());
        assertTrue(LiveSwing.BUNT_MISS.isBunt());
        assertFalse(LiveSwing.FOUL.isBunt());
    }

    /** Every pitch of a real, complete game: the swing agrees with the feed's own flags and nothing is unmapped. */
    @Test
    void wholeRealGameAgreesWithFeedFlags() throws Exception {
        LiveFeed feed = MlbLiveFeedParser.parseFeed(Fixtures.mlbGz("feeds/feed_849829_final.json.gz"), 849829L);
        Map<LiveSwing, Integer> counts = new EnumMap<>(LiveSwing.class);
        Map<String, Integer> codes = new TreeMap<>();
        int pitches = 0;
        for (LivePlay play : feed.plays()) {
            int inPlayInThisAtBat = 0;
            for (LivePlayEvent e : play.events()) {
                if (e.kind() != LivePlayEvent.Kind.PITCH) {
                    continue;
                }
                pitches++;
                LivePitch pitch = e.pitch();
                LiveSwing s = LiveSwing.of(pitch);
                counts.merge(s, 1, Integer::sum);
                codes.merge(pitch.callCode(), 1, Integer::sum);
                assertFalse(pitch.callCode().isEmpty(), "pitch without call code: " + pitch);
                assertEquals(pitch.isInPlay(), s == LiveSwing.IN_PLAY, pitch.callCode() + " / " + pitch.call());
                if (pitch.isBall() && !"H".equals(pitch.callCode())) {
                    assertEquals(LiveSwing.TAKE, s, pitch.call());
                }
                if (s == LiveSwing.IN_PLAY) {
                    inPlayInThisAtBat++;
                }
            }
            assertTrue(inPlayInThisAtBat <= 1, "at most one ball in play per at-bat");
        }
        // Counted straight from the raw JSON: B 107, *B 2, C 47, S 37, W 5, F 47, T 5, X 28, D 7, E 2.
        assertEquals(287, pitches);
        assertEquals(107 + 2 + 47, counts.get(LiveSwing.TAKE));
        assertEquals(37 + 5, counts.get(LiveSwing.SWING_MISS));
        assertEquals(47, counts.get(LiveSwing.FOUL));
        assertEquals(5, counts.get(LiveSwing.FOUL_TIP));
        assertEquals(28 + 7 + 2, counts.get(LiveSwing.IN_PLAY));
        assertEquals(10, codes.size(), codes.toString());
    }
}
