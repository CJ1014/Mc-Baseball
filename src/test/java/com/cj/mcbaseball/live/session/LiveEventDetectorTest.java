package com.cj.mcbaseball.live.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.Fixtures;
import com.cj.mcbaseball.live.mlb.MlbLiveFeedParser;
import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LivePitch;
import com.cj.mcbaseball.live.model.LivePlay;
import com.cj.mcbaseball.live.model.LivePlayEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Uses the 20 real snapshots of ATL @ LAD (bottom 4th). Ground truth (computed independently from the
 * JSON): after the first snapshot there are 20 new pitches, 3 actions (mound visit, pitching change,
 * stolen base) and 6 at-bat results (flyout, walk, home run, single, two lineouts).
 */
class LiveEventDetectorTest {

    private static List<LiveEvent> replay(List<LiveFeed> feeds, int joinAt, boolean pollTwice) {
        LiveEventDetector d = new LiveEventDetector();
        d.sync(feeds.get(joinAt));
        List<LiveEvent> all = new ArrayList<>();
        for (int i = joinAt + 1; i < feeds.size(); i++) {
            all.addAll(d.detect(feeds.get(i)));
            if (pollTwice) {
                assertTrue(d.detect(feeds.get(i)).isEmpty(), "same data polled again must produce nothing (snapshot " + i + ")");
            }
        }
        return all;
    }

    @Test
    void everyRealEventExactlyOnce() {
        List<LiveFeed> feeds = Fixtures.recordedBottom4th();
        assertEquals(20, feeds.size());
        List<LiveEvent> events = replay(feeds, 0, true);

        Set<String> keys = new HashSet<>();
        for (LiveEvent e : events) {
            assertTrue(keys.add(e.key()), "duplicate " + e.key());
        }
        assertEquals(20, events.stream().filter(e -> e instanceof LiveEvent.Pitch).count());
        assertEquals(List.of("mound_visit", "pitching_substitution", "stolen_base_2b"),
            events.stream().filter(e -> e instanceof LiveEvent.Action).map(e -> ((LiveEvent.Action) e).event().eventType()).sorted().toList());
        assertEquals(List.of("field_out", "walk", "home_run", "single", "field_out", "field_out"),
            events.stream().filter(e -> e instanceof LiveEvent.AtBatResult).map(e -> ((LiveEvent.AtBatResult) e).play().eventType()).toList());
        assertTrue(events.stream().noneMatch(e -> e instanceof LiveEvent.CallChanged || e instanceof LiveEvent.ResultChanged));
        assertTrue(events.stream().noneMatch(e -> e instanceof LiveEvent.HalfInning), "whole sequence is the bottom of the 4th");

        LiveEvent.AtBatResult hr = events.stream().filter(e -> e instanceof LiveEvent.AtBatResult r && r.play().eventType().equals("home_run"))
            .map(e -> (LiveEvent.AtBatResult) e).findFirst().orElseThrow();
        assertEquals(2, hr.play().homeScore());
        assertEquals(2, hr.play().rbi());
    }

    /** Within the stream, every at-bat's events come in index order and its result comes after all of them. */
    @Test
    void gameOrder() {
        List<LiveEvent> events = replay(Fixtures.recordedBottom4th(), 0, false);
        int lastAb = -1;
        int lastIndex = -1;
        Set<Integer> finished = new HashSet<>();
        for (LiveEvent e : events) {
            if (e instanceof LiveEvent.Pitch || e instanceof LiveEvent.Action) {
                LivePlay play = e instanceof LiveEvent.Pitch p ? p.play() : ((LiveEvent.Action) e).play();
                LivePlayEvent pe = e instanceof LiveEvent.Pitch p ? p.event() : ((LiveEvent.Action) e).event();
                assertFalse(finished.contains(play.atBatIndex()), "event after its at-bat's result: " + e.label());
                assertTrue(play.atBatIndex() >= lastAb, "at-bats out of order");
                if (play.atBatIndex() == lastAb) {
                    assertTrue(pe.index() > lastIndex, "events out of order in at-bat " + lastAb);
                }
                lastAb = play.atBatIndex();
                lastIndex = pe.index();
            } else if (e instanceof LiveEvent.AtBatResult r) {
                assertTrue(r.play().isComplete());
                finished.add(r.play().atBatIndex());
            }
        }
    }

    /** The feed briefly reports "stolen_base_2b" as the at-bat result while the at-bat is still going. */
    @Test
    void midAtBatResultIsNotAnOutcome() {
        List<LiveEvent> events = replay(Fixtures.recordedBottom4th(), 0, false);
        assertTrue(events.stream().noneMatch(e -> e instanceof LiveEvent.AtBatResult r && r.play().eventType().startsWith("stolen_base")));
        assertEquals(1, events.stream().filter(e -> e instanceof LiveEvent.Action a && a.event().eventType().equals("stolen_base_2b")).count());
    }

    /** Joining at any point never replays what was already in the game. */
    @Test
    void joinMidGameNeverReplaysThePast() {
        List<LiveFeed> feeds = Fixtures.recordedBottom4th();
        Set<String> all = replay(feeds, 0, false).stream().map(LiveEvent::key).collect(Collectors.toSet());
        for (int join = 1; join < feeds.size(); join++) {
            Set<String> before = replay(feeds.subList(0, join + 1), 0, false).stream().map(LiveEvent::key).collect(Collectors.toSet());
            Set<String> after = replay(feeds, join, false).stream().map(LiveEvent::key).collect(Collectors.toSet());
            for (String k : after) {
                assertFalse(before.contains(k), "joined at " + join + " but replayed " + k);
            }
            Set<String> union = new HashSet<>(before);
            union.addAll(after);
            assertEquals(all, union, "joining at " + join + " lost events");
        }
    }

    /** A completed game watched from the 3rd inning: one half-inning event per new half, in order. */
    @Test
    void halfInningsAppearInOrder() throws Exception {
        LiveFeed full = MlbLiveFeedParser.parseFeed(Fixtures.mlbGz("feeds/feed_849829_final.json.gz"), 849829L);
        int cut = 0;
        while (full.plays().get(cut).inning() < 3) {
            cut++;
        }
        LiveFeed early = new LiveFeed(withStatus(full.state(), LiveGameStatus.State.LIVE), full.plays().subList(0, cut));
        LiveEventDetector d = new LiveEventDetector();
        d.sync(early);
        List<LiveEvent> events = d.detect(full);
        List<String> halves = events.stream().filter(e -> e instanceof LiveEvent.HalfInning).map(LiveEvent::label).toList();
        assertEquals("Top 3rd", halves.get(0));
        assertEquals("Bottom 9th", halves.get(halves.size() - 1));
        assertEquals(14, halves.size());
        assertInstanceOf(LiveEvent.Status.class, events.get(events.size() - 1), "FINAL comes after the last play");
        assertEquals(LiveGameStatus.State.FINAL, ((LiveEvent.Status) events.get(events.size() - 1)).to().state());
        assertEquals(full.plays().size() - cut, events.stream().filter(e -> e instanceof LiveEvent.AtBatResult).count());
    }

    @Test
    void overturnedCallIsACorrectionNotADuplicate() {
        List<LiveFeed> feeds = Fixtures.recordedBottom4th();
        LiveEventDetector d = new LiveEventDetector();
        d.sync(feeds.get(0));
        d.detect(feeds.get(5));
        LiveFeed changed = mapLastPitch(feeds.get(5), p -> new LivePitch(p.id(), p.typeCode(), p.typeName(), p.mph(), "Called Strike (ABS challenge)",
            true, false, false, p.plateX(), p.plateZ(), p.zoneTop(), p.zoneBottom(), p.ballsAfter(), p.strikesAfter()));
        List<LiveEvent> ev = d.detect(changed);
        assertEquals(1, ev.size());
        LiveEvent.CallChanged c = assertInstanceOf(LiveEvent.CallChanged.class, ev.get(0));
        assertTrue(c.label().endsWith("-> Called Strike (ABS challenge)"));
        assertTrue(d.detect(changed).isEmpty(), "the correction itself is reported once");
    }

    @Test
    void scoringChangeIsACorrection() {
        List<LiveFeed> feeds = Fixtures.recordedBottom4th();
        LiveEventDetector d = new LiveEventDetector();
        d.sync(feeds.get(0));
        LiveFeed last = feeds.get(feeds.size() - 1);
        d.detect(last);
        List<LivePlay> plays = new ArrayList<>(last.plays());
        LivePlay single = plays.stream().filter(p -> p.eventType().equals("single")).findFirst().orElseThrow();
        plays.set(plays.indexOf(single), new LivePlay(single.atBatIndex(), single.inning(), single.isTop(), true, single.batter(), single.pitcher(),
            "field_error", "Field Error", "Kyle Tucker reaches on a fielding error.", 0, single.awayScore(), single.homeScore(), false,
            single.outsAfter(), single.events(), single.runners(), single.endTimeMillis()));
        List<LiveEvent> ev = d.detect(new LiveFeed(last.state(), plays));
        assertEquals(1, ev.size());
        LiveEvent.ResultChanged rc = assertInstanceOf(LiveEvent.ResultChanged.class, ev.get(0));
        assertEquals("single", rc.oldEventType());
    }

    @Test
    void gameStartingComesBeforeFirstPitch() throws Exception {
        LiveFeed pre = MlbLiveFeedParser.parseFeed(Fixtures.mlbGz("feeds/feed_849835_pregame.json.gz"), 849835L);
        List<LiveFeed> rec = Fixtures.recordedBottom4th();
        LiveEventDetector d = new LiveEventDetector();
        d.sync(new LiveFeed(pre.state(), List.of()));
        LiveFeed firstPitches = new LiveFeed(rec.get(0).state(), rec.get(0).plays().subList(0, 1));
        List<LiveEvent> ev = d.detect(firstPitches);
        LiveEvent.Status s = assertInstanceOf(LiveEvent.Status.class, ev.get(0));
        assertEquals("PLAY BALL!", s.label());
        assertEquals("PLAY BALL!", new LiveEvent.Status(LiveGameStatus.of(LiveGameStatus.State.WARMUP), LiveGameStatus.of(LiveGameStatus.State.LIVE), 1).label());
        assertEquals("Game resumed", new LiveEvent.Status(LiveGameStatus.of(LiveGameStatus.State.DELAYED), LiveGameStatus.of(LiveGameStatus.State.LIVE), 2).label());
    }

    private static LiveGameState withStatus(LiveGameState s, LiveGameStatus.State st) {
        return new LiveGameState(s.gameId(), LiveGameStatus.of(st), s.away(), s.home(), s.startEpochMillis(), s.awayScore(), s.homeScore(), s.inning(),
            s.inningState(), s.balls(), s.strikes(), s.outs(), s.betweenBatters(), s.batter(), s.pitcher(), s.onDeck(), s.runnerOnFirst(),
            s.runnerOnSecond(), s.runnerOnThird(), s.awayTotals(), s.homeTotals(), s.awayInningRuns(), s.homeInningRuns(), s.lastPitch(), s.lastPlay(),
            s.venue(), s.feedTimestamp(), s.suggestedPollSeconds(), s.awayLineup(), s.homeLineup());
    }

    private static LiveFeed mapLastPitch(LiveFeed f, java.util.function.UnaryOperator<LivePitch> fn) {
        List<LivePlay> plays = new ArrayList<>(f.plays());
        for (int i = plays.size() - 1; i >= 0; i--) {
            LivePlay p = plays.get(i);
            List<LivePlayEvent> evs = new ArrayList<>(p.events());
            for (int j = evs.size() - 1; j >= 0; j--) {
                LivePlayEvent e = evs.get(j);
                if (e.kind() == LivePlayEvent.Kind.PITCH) {
                    evs.set(j, new LivePlayEvent(e.index(), e.kind(), e.playId(), fn.apply(e.pitch()), e.hit(), e.eventType(), e.description(),
                        e.isSubstitution(), e.player(), e.position(), e.ballsAfter(), e.strikesAfter(), e.outsAfter(), e.timeMillis()));
                    plays.set(i, new LivePlay(p.atBatIndex(), p.inning(), p.isTop(), p.isComplete(), p.batter(), p.pitcher(), p.eventType(), p.event(),
                        p.description(), p.rbi(), p.awayScore(), p.homeScore(), p.isOut(), p.outsAfter(), evs, p.runners(), p.endTimeMillis()));
                    return new LiveFeed(f.state(), plays);
                }
            }
        }
        throw new IllegalStateException("no pitch");
    }
}
