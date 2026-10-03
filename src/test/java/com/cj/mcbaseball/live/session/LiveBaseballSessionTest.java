package com.cj.mcbaseball.live.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LiveGameState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The strongest check of Phase 3: replaying the real bottom of the 4th, the state the Minecraft
 * recreation has shown must catch up to the real game's state after every poll.
 */
class LiveBaseballSessionTest {

    private static void drain(LiveBaseballSession s, long[] now) {
        for (int i = 0; i < 1000 && (s.queue().size() > 0 || i < 2); i++) {
            now[0] += 500;
            s.tick(now[0]);
        }
    }

    @Test
    void recreationConvergesToRealStateAfterEveryPoll() {
        List<LiveFeed> feeds = Fixtures.recordedBottom4th();
        LiveBaseballSession s = new LiveBaseballSession(849828L, LiveEventQueue::defaultDuration);
        long[] now = {0L};
        List<String> mismatches = new ArrayList<>();
        for (int i = 0; i < feeds.size(); i++) {
            s.onFeed(feeds.get(i));
            drain(s, now);
            LiveGameState real = feeds.get(i).state();
            RecreationState mc = s.recreation();
            String where = "snapshot " + i + " (" + real.feedTimestamp() + ")";
            if (mc.awayScore != real.awayScore() || mc.homeScore != real.homeScore()) {
                mismatches.add(where + " score " + mc.awayScore + "-" + mc.homeScore + " vs " + real.awayScore() + "-" + real.homeScore());
            }
            if (mc.outs != real.outs()) {
                mismatches.add(where + " outs " + mc.outs + " vs " + real.outs());
            }
            if (!real.isBreak() && mc.basesMask() != real.basesMask()) {
                mismatches.add(where + " bases " + mc.basesMask() + " vs " + real.basesMask());
            }
            if (!real.isBreak() && (mc.balls != real.balls() || mc.strikes != real.strikes())) {
                mismatches.add(where + " count " + mc.balls + "-" + mc.strikes + " vs " + real.balls() + "-" + real.strikes());
            }
        }
        assertEquals(List.of(), mismatches);
        assertEquals(29, s.eventsDetected());
        RecreationState end = s.recreation();
        assertEquals(2, end.homeScore);
        assertEquals(3, end.outs);
        assertEquals(0, end.basesMask(), "inning over: bases cleared");
    }

    @Test
    void stolenBaseMovesTheRunnerInTheRecreation() {
        List<LiveFeed> feeds = Fixtures.recordedBottom4th();
        LiveBaseballSession s = new LiveBaseballSession(849828L, LiveEventQueue::defaultDuration);
        long[] now = {0L};
        int steal = -1;
        for (int i = 0; i < feeds.size(); i++) {
            if (feeds.get(i).state().feedTimestamp().equals("20261003_211803")) {
                steal = i;
            }
        }
        for (int i = 0; i < steal; i++) {
            s.onFeed(feeds.get(i));
            drain(s, now);
        }
        assertEquals(1, s.recreation().basesMask(), "Tucker on first");
        s.onFeed(feeds.get(steal));
        drain(s, now);
        assertEquals(2, s.recreation().basesMask(), "Tucker stole second");
        assertTrue(s.recentEvents().stream().anyMatch(l -> l.contains("steals")), s.recentEvents().toString());
    }

    @Test
    void joiningSyncsWithoutReplaying() {
        List<LiveFeed> feeds = Fixtures.recordedBottom4th();
        LiveBaseballSession s = new LiveBaseballSession(849828L, LiveEventQueue::defaultDuration);
        s.onFeed(feeds.get(10));
        assertEquals(0, s.queue().size());
        assertEquals(feeds.get(10).state().homeScore(), s.recreation().homeScore);
        assertTrue(s.recentEvents().isEmpty());
        assertTrue(s.debugLines(0).stream().anyMatch(l -> l.startsWith("MINECRAFT:")));
    }
}
