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
    @Nullable
    private LiveFeed lastFeed;
    /** True while NPCs on a field play the events (they pull them); false = timed playback for the HUD only. */
    private boolean playbackAttached;
    private boolean changed;

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
        this.lastFeed = feed;
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

    /** Timed playback: plays due events; true if anything changed. With NPC playback attached, only reports changes. */
    public boolean tick(long now) {
        if (this.playbackAttached) {
            boolean c = this.changed;
            this.changed = false;
            return c;
        }
        List<LiveEvent> started = this.queue.tick(now);
        for (LiveEvent e : started) {
            this.markPlayed(e);
        }
        this.changed = false;
        return !started.isEmpty();
    }

    /** NPC playback: the field's recreation pulls events itself when its animations are done. */
    public void attachPlayback(boolean attached) {
        this.playbackAttached = attached;
    }

    public boolean isPlaybackAttached() {
        return this.playbackAttached;
    }

    /** Next event for the NPCs, or null if nothing is ready (see LiveEventQueue#pollNext). */
    @Nullable
    public LiveEvent nextForPlayback(long now) {
        return this.queue.pollNext(now);
    }

    /** Records that the recreation has shown this event (updates the Minecraft-side state and the ticker). */
    public void markPlayed(LiveEvent e) {
        this.recreation.apply(e);
        this.eventsPlayed++;
        this.changed = true;
        if (e.timeMillis() > 0L) {
            this.lastAppliedRealTime = e.timeMillis();
        }
        if (e instanceof LiveEvent.Pitch) {
            this.lastPitch = e.label();
        } else if (e instanceof LiveEvent.AtBatResult) {
            this.lastPlay = e.label();
        }
        if (e instanceof LiveEvent.Action a && a.event().isDowntime()) {
            return;
        }
        this.recent.addFirst(e.label());
        while (this.recent.size() > RECENT) {
            this.recent.removeLast();
        }
    }

    public boolean isSynced() {
        return this.detector.isSynced();
    }

    @Nullable
    public LiveFeed lastFeed() {
        return this.lastFeed;
    }

    /** What the HUD should show: the recreation's version of the game while NPCs play it, else the real state. */
    public LiveGameState view(LiveGameState real) {
        return this.playbackAttached && this.detector.isSynced() ? this.recreation.view(real) : real;
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
