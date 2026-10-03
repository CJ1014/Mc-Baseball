package com.cj.mcbaseball.live.session;

import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LiveGameState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Connects one real game to one Minecraft field: real data in, ordered events out.
 *
 * <pre>
 *  feed snapshots ──▶ LiveEventDetector (new pitches/actions/results, no duplicates, corrections)
 *                          │
 *                          ▼
 *                   LiveEventQueue (in order, paced, catch-up, waits for in-play results)
 *                          │  tick()
 *                          ▼
 *                   RecreationState (what Minecraft has shown) + recent event list
 * </pre>
 *
 * The first snapshot only synchronises (joining in the 6th doesn't replay innings 1-5).
 * Server thread only.
 */
public final class LiveBaseballSession {

    private static final int RECENT = 5;

    private final long gameId;
    private final LiveEventDetector detector = new LiveEventDetector();
    private final LiveEventQueue queue;
    private final RecreationState recreation = new RecreationState();
    private final Deque<String> recent = new ArrayDeque<>();
    @Nullable
    private LiveGameState real;
    private String lastPitch = "";
    private String lastPlay = "";
    private long lastAppliedRealTime;
    private int eventsDetected;
    private int eventsPlayed;

    public LiveBaseballSession(long gameId, LiveEventQueue.Pacer pacer) {
        this.gameId = gameId;
        this.queue = new LiveEventQueue(pacer);
    }

    public long gameId() {
        return this.gameId;
    }

    /** New feed data arrived. */
    public void onFeed(LiveFeed feed) {
        this.real = feed.state();
        if (!this.detector.isSynced()) {
            this.detector.sync(feed);
            this.recreation.reset(feed.state());
            return;
        }
        for (LiveEvent e : this.detector.detect(feed)) {
            if (this.queue.add(e)) {
                this.eventsDetected++;
            }
        }
    }

    /** Plays due events; true if anything changed. */
    public boolean tick(long now) {
        List<LiveEvent> started = this.queue.tick(now);
        for (LiveEvent e : started) {
            this.recreation.apply(e);
            this.eventsPlayed++;
            if (e.timeMillis() > 0L) {
                this.lastAppliedRealTime = e.timeMillis();
            }
            if (e instanceof LiveEvent.Pitch) {
                this.lastPitch = e.label();
            } else if (e instanceof LiveEvent.AtBatResult) {
                this.lastPlay = e.label();
            }
            if (e instanceof LiveEvent.Action a && a.event().isDowntime()) {
                continue;
            }
            this.recent.addFirst(e.label());
            while (this.recent.size() > RECENT) {
                this.recent.removeLast();
            }
        }
        return !started.isEmpty();
    }

    public List<String> recentEvents() {
        return new ArrayList<>(this.recent);
    }

    public RecreationState recreation() {
        return this.recreation;
    }

    public LiveEventQueue queue() {
        return this.queue;
    }

    public int eventsDetected() {
        return this.eventsDetected;
    }

    /** Lines for the developer debug panel. */
    public List<String> debugLines(long now) {
        List<String> l = new ArrayList<>();
        String away = this.real == null ? "AWAY" : this.real.away().displayAbbr();
        String home = this.real == null ? "HOME" : this.real.home().displayAbbr();
        l.add("Game ID: " + this.gameId + (this.gameId < 0 ? " (recorded)" : ""));
        l.add("Feed timestamp: " + (this.real == null ? "-" : this.real.feedTimestamp()));
        l.add("Synced: " + this.detector.isSynced() + "   processed ids: " + this.detector.processedCount());
        l.add("Events detected: " + this.eventsDetected + "   played: " + this.eventsPlayed + "   duplicates ignored: " + this.queue.duplicatesIgnored());
        l.add(String.format(Locale.ROOT, "Queued: %d   backlog: %.1fs   speed: %.2fx%s", this.queue.size(), this.queue.backlogMillis() / 1000.0,
            this.queue.speed(), this.queue.isWaitingForResult() ? "   (waiting for in-play result)" : ""));
        l.add("Behind real game: " + (this.gameId < 0 ? "- (recorded replay)"
            : this.lastAppliedRealTime > 0 ? Math.max(0L, (now - this.lastAppliedRealTime) / 1000L) + "s" : "-"));
        l.add("Last processed pitch: " + (this.lastPitch.isEmpty() ? "-" : this.lastPitch));
        l.add("Last processed play: " + (this.lastPlay.isEmpty() ? "-" : this.lastPlay));
        if (this.real != null) {
            LiveGameState s = this.real;
            l.add("REAL: " + s.status().label() + ", " + s.inningLabel() + ", " + s.outs() + " out, " + s.balls() + "-" + s.strikes() + ", "
                + away + " " + s.awayScore() + " " + home + " " + s.homeScore());
        }
        l.add("MINECRAFT: " + this.recreation.summary(away, home));
        return l;
    }
}
