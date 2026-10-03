package com.cj.mcbaseball.live.session;

import com.cj.mcbaseball.live.model.LiveFeed;
import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LivePlay;
import com.cj.mcbaseball.live.model.LivePlayEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns successive live-feed snapshots into new {@link LiveEvent}s, each real occurrence exactly once.
 *
 * <ul>
 *   <li>{@link #sync} on the first snapshot marks everything already in the game as processed:
 *       watching from the 6th inning does not replay innings 1-5.</li>
 *   <li>Pitches are keyed by the provider's stable pitch id; actions by (at-bat, index); results by at-bat.
 *       Polling the same data again yields nothing.</li>
 *   <li>Results are only emitted once the at-bat is complete (mid-at-bat the feed's result can be a steal).</li>
 *   <li>If the feed later changes a pitch call or an at-bat result we already emitted, a correction
 *       event is emitted instead of a duplicate.</li>
 *   <li>Output is in game order: half-inning start, then each at-bat's events by index, then its result.</li>
 * </ul>
 */
public final class LiveEventDetector {

    private final Set<String> seen = new HashSet<>();
    private final Map<String, String> pitchCalls = new HashMap<>();
    private final Map<Integer, String> results = new HashMap<>();
    private int lastHalfKey = -1;
    private LiveGameStatus lastStatus;
    private boolean synced;
    private int seq;

    public boolean isSynced() {
        return this.synced;
    }

    /** Remembers the game as it is now without producing events. */
    public void sync(LiveFeed feed) {
        for (LivePlay play : feed.plays()) {
            this.lastHalfKey = Math.max(this.lastHalfKey, play.halfKey());
            for (LivePlayEvent e : play.events()) {
                String k = LiveEvent.eventKey(play, e);
                this.seen.add(k);
                if (e.kind() == LivePlayEvent.Kind.PITCH) {
                    this.pitchCalls.put(k, e.pitch().call());
                }
            }
            if (play.isComplete()) {
                this.results.put(play.atBatIndex(), play.eventType());
            }
        }
        this.lastStatus = feed.state().status();
        this.synced = true;
    }

    /** Events new since the previous call (or since {@link #sync}). */
    public List<LiveEvent> detect(LiveFeed feed) {
        if (!this.synced) {
            this.sync(feed);
            return List.of();
        }
        List<LiveEvent> out = new ArrayList<>();
        LiveGameStatus status = feed.state().status();
        boolean statusChanged = this.lastStatus != null && status.state() != this.lastStatus.state();
        // Starting / resuming goes before the pitches it allows; delays and the final go after.
        if (statusChanged && !status.state().isOver() && status.state() != LiveGameStatus.State.DELAYED) {
            out.add(new LiveEvent.Status(this.lastStatus, status, ++this.seq));
        }

        for (LivePlay play : feed.plays()) {
            List<LiveEvent> forPlay = new ArrayList<>();
            for (LivePlayEvent e : play.events()) {
                String k = LiveEvent.eventKey(play, e);
                if (this.seen.add(k)) {
                    if (e.kind() == LivePlayEvent.Kind.PITCH) {
                        this.pitchCalls.put(k, e.pitch().call());
                        forPlay.add(new LiveEvent.Pitch(play, e));
                    } else {
                        forPlay.add(new LiveEvent.Action(play, e));
                    }
                } else if (e.kind() == LivePlayEvent.Kind.PITCH) {
                    String before = this.pitchCalls.get(k);
                    String now = e.pitch().call();
                    if (before != null && !now.isEmpty() && !before.isEmpty() && !before.equals(now)) {
                        this.pitchCalls.put(k, now);
                        forPlay.add(new LiveEvent.CallChanged(play, e, before, ++this.seq));
                    }
                }
            }
            if (play.isComplete()) {
                String before = this.results.get(play.atBatIndex());
                if (before == null) {
                    this.results.put(play.atBatIndex(), play.eventType());
                    forPlay.add(new LiveEvent.AtBatResult(play));
                } else if (!play.eventType().isEmpty() && !before.isEmpty() && !before.equals(play.eventType())) {
                    this.results.put(play.atBatIndex(), play.eventType());
                    forPlay.add(new LiveEvent.ResultChanged(play, before, ++this.seq));
                }
            }
            if (!forPlay.isEmpty() && play.halfKey() > this.lastHalfKey) {
                this.lastHalfKey = play.halfKey();
                out.add(new LiveEvent.HalfInning(play.inning(), play.isTop()));
            }
            out.addAll(forPlay);
        }

        if (statusChanged && (status.state().isOver() || status.state() == LiveGameStatus.State.DELAYED)) {
            out.add(new LiveEvent.Status(this.lastStatus, status, ++this.seq));
        }
        this.lastStatus = status;
        return out;
    }

    /** Distinct pitches / actions / results remembered so far (debug panel). */
    public int processedCount() {
        return this.seen.size() + this.results.size();
    }
}
