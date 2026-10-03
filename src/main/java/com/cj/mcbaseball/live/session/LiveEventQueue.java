package com.cj.mcbaseball.live.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Real events waiting to be shown in Minecraft, played strictly in order, one at a time.
 *
 * <ul>
 *   <li>Each event occupies the recreation for {@link Pacer#durationMillis} (Phase 4+ replaces the timing
 *       stand-in with "animation finished").</li>
 *   <li>Several events arriving in one poll are not snapped through: they play back-to-back.</li>
 *   <li>If the backlog gets long, playback speeds up (up to {@link #MAX_SPEED}x). Events are never dropped.
 *       Pure downtime (timeouts, mound visits) takes no time.</li>
 *   <li>A pitch put in play waits until that at-bat's result has arrived, so the recreation knows where the
 *       ball must go (gives up waiting after {@link #IN_PLAY_WAIT_MAX_MILLIS}).</li>
 *   <li>Duplicate keys are ignored forever, as a second line of defence behind the detector.</li>
 * </ul>
 */
public final class LiveEventQueue {

    public interface Pacer {
        long durationMillis(LiveEvent e);
    }

    public static final long CATCH_UP_THRESHOLD_MILLIS = 20_000L;
    public static final double MAX_SPEED = 2.0;
    public static final long IN_PLAY_WAIT_MAX_MILLIS = 45_000L;

    private final Pacer pacer;
    private final Deque<LiveEvent> pending = new ArrayDeque<>();
    private final Set<String> everQueued = new HashSet<>();
    private LiveEvent current;
    private long currentEndsAt;
    private long waitingSince = -1L;
    private int duplicatesIgnored;

    public LiveEventQueue(Pacer pacer) {
        this.pacer = pacer;
    }

    /** @return false if this event was already queued once (ignored) */
    public boolean add(LiveEvent e) {
        if (!this.everQueued.add(e.key())) {
            this.duplicatesIgnored++;
            return false;
        }
        this.pending.addLast(e);
        return true;
    }

    /** Starts every event that is due; returns them in order (usually zero or one per call). */
    public List<LiveEvent> tick(long now) {
        List<LiveEvent> started = new ArrayList<>();
        while (true) {
            if (this.current != null && now < this.currentEndsAt) {
                break;
            }
            this.current = null;
            LiveEvent next = this.pending.peekFirst();
            if (next == null) {
                break;
            }
            if (this.waitsForResult(next)) {
                if (this.waitingSince < 0L) {
                    this.waitingSince = now;
                }
                if (now - this.waitingSince < IN_PLAY_WAIT_MAX_MILLIS) {
                    break;
                }
            }
            this.waitingSince = -1L;
            this.pending.pollFirst();
            long d = (long) (this.pacer.durationMillis(next) / this.speed());
            this.current = next;
            this.currentEndsAt = now + d;
            started.add(next);
            if (d > 0L) {
                break;
            }
        }
        return started;
    }

    /**
     * Playback driven by the consumer (NPC animations set the pace): the next event if one is ready, ignoring
     * the stand-in timings. Still holds an in-play pitch until its result arrives (with the same timeout).
     */
    @javax.annotation.Nullable
    public LiveEvent pollNext(long now) {
        LiveEvent next = this.pending.peekFirst();
        if (next == null) {
            return null;
        }
        if (this.waitsForResult(next)) {
            if (this.waitingSince < 0L) {
                this.waitingSince = now;
            }
            if (now - this.waitingSince < IN_PLAY_WAIT_MAX_MILLIS) {
                return null;
            }
        }
        this.waitingSince = -1L;
        this.current = null;
        return this.pending.pollFirst();
    }

    private boolean waitsForResult(LiveEvent e) {
        if (!(e instanceof LiveEvent.Pitch p) || !p.event().pitch().isInPlay()) {
            return false;
        }
        int ab = p.play().atBatIndex();
        for (LiveEvent q : this.pending) {
            if (q instanceof LiveEvent.AtBatResult r && r.play().atBatIndex() == ab) {
                return false;
            }
        }
        return true;
    }

    /** Current playback speed: 1.0, rising toward MAX_SPEED as the backlog grows. */
    public double speed() {
        long backlog = this.backlogMillis();
        if (backlog <= CATCH_UP_THRESHOLD_MILLIS) {
            return 1.0;
        }
        return Math.min(MAX_SPEED, (double) backlog / CATCH_UP_THRESHOLD_MILLIS);
    }

    /** Unscaled time the queued events would take. */
    public long backlogMillis() {
        long sum = 0L;
        for (LiveEvent e : this.pending) {
            sum += this.pacer.durationMillis(e);
        }
        return sum;
    }

    public int size() {
        return this.pending.size();
    }

    public boolean isWaitingForResult() {
        return this.waitingSince >= 0L;
    }

    public int duplicatesIgnored() {
        return this.duplicatesIgnored;
    }

    /** Stand-in timing until NPC animations drive the pace (Phase 4+). */
    public static long defaultDuration(LiveEvent e) {
        if (e instanceof LiveEvent.Pitch p) {
            return p.event().pitch().isInPlay() ? 1_500L : 3_000L;
        }
        if (e instanceof LiveEvent.AtBatResult r) {
            return switch (r.play().eventType()) {
                case "home_run" -> 8_000L;
                case "triple" -> 7_000L;
                case "double" -> 6_000L;
                case "strikeout", "strikeout_double_play" -> 2_500L;
                case "walk", "intent_walk", "hit_by_pitch" -> 3_000L;
                default -> 4_500L;
            };
        }
        if (e instanceof LiveEvent.Action a) {
            if (a.event().isDowntime()) {
                return 0L;
            }
            return a.event().isSubstitution() ? 3_000L : 2_500L;
        }
        if (e instanceof LiveEvent.HalfInning) {
            return 4_000L;
        }
        return 1_500L;
    }
}
