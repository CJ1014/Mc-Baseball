package com.cj.mcbaseball.live.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.model.LiveFeed;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveEventQueueTest {

    private static List<LiveEvent> realEvents() {
        List<LiveFeed> feeds = Fixtures.recordedBottom4th();
        LiveEventDetector d = new LiveEventDetector();
        d.sync(feeds.get(0));
        List<LiveEvent> out = new ArrayList<>();
        for (int i = 1; i < feeds.size(); i++) {
            out.addAll(d.detect(feeds.get(i)));
        }
        return out;
    }

    @Test
    void playsOneAtATimeInOrderAtRealisticPace() {
        LiveEventQueue q = new LiveEventQueue(e -> 3_000L);
        List<LiveEvent> evs = realEvents().subList(0, 5);
        evs.forEach(q::add);
        long now = 0;
        List<LiveEvent> played = new ArrayList<>();
        played.addAll(q.tick(now));
        assertEquals(1, played.size(), "five events arriving together are not snapped through");
        played.addAll(q.tick(now + 2_999));
        assertEquals(1, played.size());
        played.addAll(q.tick(now + 3_000));
        assertEquals(2, played.size());
        for (int t = 0; t < 100; t++) {
            now += 1_000;
            played.addAll(q.tick(now + 3_000));
        }
        assertEquals(evs, played);
    }

    @Test
    void duplicatesIgnored() {
        LiveEventQueue q = new LiveEventQueue(e -> 0L);
        LiveEvent e = realEvents().get(0);
        assertTrue(q.add(e));
        assertFalse(q.add(e));
        q.tick(0);
        assertFalse(q.add(e), "even after it was played");
        assertEquals(2, q.duplicatesIgnored());
    }

    @Test
    void catchUpIsCappedAndNeverDrops() {
        LiveEventQueue q = new LiveEventQueue(e -> 5_000L);
        List<LiveEvent> evs = realEvents();
        evs.forEach(q::add);
        assertEquals(LiveEventQueue.MAX_SPEED, q.speed(), 1e-9, "big backlog plays faster, but at most 2x");
        long now = 0;
        int played = 0;
        long start = now;
        while (q.size() > 0) {
            played += q.tick(now).size();
            now += 50;
        }
        played += q.tick(now + 100_000).size();
        assertEquals(evs.size(), played);
        long realDuration = evs.size() * 5_000L;
        assertTrue(now - start < realDuration, "caught up");
        assertTrue(now - start >= realDuration / LiveEventQueue.MAX_SPEED - 5_000, "but no faster than 2x");
    }

    @Test
    void downtimeTakesNoTime() {
        LiveEventQueue q = new LiveEventQueue(LiveEventQueue::defaultDuration);
        List<LiveEvent> evs = realEvents();
        LiveEvent mound = evs.stream().filter(e -> e instanceof LiveEvent.Action a && a.event().eventType().equals("mound_visit")).findFirst().orElseThrow();
        assertEquals(0L, LiveEventQueue.defaultDuration(mound));
        LiveEvent sub = evs.stream().filter(e -> e instanceof LiveEvent.Action a && a.event().isSubstitution()).findFirst().orElseThrow();
        assertTrue(LiveEventQueue.defaultDuration(sub) > 0, "a pitching change is not skipped");
        q.add(mound);
        q.add(sub);
        assertEquals(2, q.tick(0).size(), "mound visit passes instantly, the substitution starts right after");
    }

    /** A ball put in play is held until the at-bat result is known, so the recreation knows where it goes. */
    @Test
    void inPlayPitchWaitsForTheResult() {
        List<LiveEvent> evs = realEvents();
        LiveEvent.Pitch inPlay = evs.stream().filter(e -> e instanceof LiveEvent.Pitch p && p.event().pitch().isInPlay())
            .map(e -> (LiveEvent.Pitch) e).findFirst().orElseThrow();
        LiveEvent result = evs.stream().filter(e -> e instanceof LiveEvent.AtBatResult r && r.play().atBatIndex() == inPlay.play().atBatIndex())
            .findFirst().orElseThrow();
        LiveEventQueue q = new LiveEventQueue(e -> 1_000L);
        q.add(inPlay);
        assertTrue(q.tick(0).isEmpty());
        assertTrue(q.isWaitingForResult());
        assertTrue(q.tick(10_000).isEmpty());
        q.add(result);
        assertEquals(List.of(inPlay), q.tick(11_000));
        assertEquals(List.of(result), q.tick(12_000));

        LiveEventQueue lonely = new LiveEventQueue(e -> 1_000L);
        lonely.add(inPlay);
        assertTrue(lonely.tick(0).isEmpty());
        assertEquals(List.of(inPlay), lonely.tick(LiveEventQueue.IN_PLAY_WAIT_MAX_MILLIS), "gives up waiting eventually");
    }
}
