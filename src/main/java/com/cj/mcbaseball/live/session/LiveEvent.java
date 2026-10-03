package com.cj.mcbaseball.live.session;

import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LivePitch;
import com.cj.mcbaseball.live.model.LivePlay;
import com.cj.mcbaseball.live.model.LivePlayEvent;
import java.util.Locale;

/**
 * Something new that happened in the real game, in the order the recreation should show it.
 * {@link #key()} is unique per real-world occurrence and is what makes "never replay twice" hold.
 */
public sealed interface LiveEvent {

    String key();

    /** One line for the HUD ticker / debug panel. */
    String label();

    /** Real-world time it happened (epoch ms), 0 if unknown. */
    long timeMillis();

    /** A pitch was thrown. */
    record Pitch(LivePlay play, LivePlayEvent event) implements LiveEvent {
        @Override
        public String key() {
            return eventKey(this.play, this.event);
        }

        @Override
        public String label() {
            LivePitch p = this.event.pitch();
            StringBuilder b = new StringBuilder(p.call().isEmpty() ? "Pitch" : p.call());
            if (this.event.ballsAfter() >= 0 && this.event.strikesAfter() >= 0 && !p.isInPlay()) {
                b.append(" (").append(this.event.ballsAfter()).append('-').append(this.event.strikesAfter()).append(')');
            }
            if (p.mph() > 0) {
                b.append(String.format(Locale.ROOT, " - %.1f", p.mph()));
            }
            if (!p.typeName().isEmpty()) {
                b.append(p.mph() > 0 ? " " : " - ").append(p.typeName());
            }
            return b.toString();
        }

        @Override
        public long timeMillis() {
            return this.event.timeMillis();
        }
    }

    /** Non-pitch event inside an at-bat: steal, substitution, pickoff throw, mound visit... */
    record Action(LivePlay play, LivePlayEvent event) implements LiveEvent {
        @Override
        public String key() {
            return eventKey(this.play, this.event);
        }

        @Override
        public String label() {
            return !this.event.description().isEmpty() ? this.event.description() : this.event.eventType().replace('_', ' ');
        }

        @Override
        public long timeMillis() {
            return this.event.timeMillis();
        }
    }

    /** A plate appearance ended (single, strikeout, home run...). Emitted only once the feed marks it complete. */
    record AtBatResult(LivePlay play) implements LiveEvent {
        @Override
        public String key() {
            return "r:" + this.play.atBatIndex();
        }

        @Override
        public String label() {
            return !this.play.description().isEmpty() ? this.play.description() : this.play.event();
        }

        @Override
        public long timeMillis() {
            return this.play.endTimeMillis();
        }
    }

    /** A new half-inning began. */
    record HalfInning(int inning, boolean top) implements LiveEvent {
        @Override
        public String key() {
            return "h:" + this.inning + (this.top ? "t" : "b");
        }

        @Override
        public String label() {
            return (this.top ? "Top " : "Bottom ") + LiveGameSummary.ordinal(this.inning);
        }

        @Override
        public long timeMillis() {
            return 0L;
        }
    }

    /** The real game changed status (started, delayed, resumed, final...). */
    record Status(LiveGameStatus from, LiveGameStatus to, int seq) implements LiveEvent {
        @Override
        public String key() {
            return "s:" + this.seq + ":" + this.to.state();
        }

        @Override
        public String label() {
            return switch (this.to.state()) {
                case LIVE -> this.from.state().section() == LiveGameStatus.Section.UPCOMING || this.from.state() == LiveGameStatus.State.WARMUP
                    ? "PLAY BALL!"
                    : this.from.state() == LiveGameStatus.State.REVIEW ? "Play resumes" : "Game resumed";
                case FINAL -> "FINAL - GAME COMPLETE";
                default -> this.to.label();
            };
        }

        @Override
        public long timeMillis() {
            return 0L;
        }
    }

    /** A pitch we already showed had its call changed (replay review / ABS challenge). */
    record CallChanged(LivePlay play, LivePlayEvent event, String oldCall, int seq) implements LiveEvent {
        @Override
        public String key() {
            return "c:" + eventKey(this.play, this.event) + ":" + this.seq;
        }

        @Override
        public String label() {
            return "Call changed: " + this.oldCall + " -> " + this.event.pitch().call();
        }

        @Override
        public long timeMillis() {
            return this.event.timeMillis();
        }
    }

    /** The official result of an at-bat we already showed was changed (scoring change / review). */
    record ResultChanged(LivePlay play, String oldEventType, int seq) implements LiveEvent {
        @Override
        public String key() {
            return "rc:" + this.play.atBatIndex() + ":" + this.seq;
        }

        @Override
        public String label() {
            return "Ruling changed: " + this.play.description();
        }

        @Override
        public long timeMillis() {
            return this.play.endTimeMillis();
        }
    }

    /** Pitches use the provider's stable id; anything else (or a pitch without one) uses its position in the at-bat. */
    static String eventKey(LivePlay play, LivePlayEvent e) {
        if (e.kind() == LivePlayEvent.Kind.PITCH && !e.playId().isEmpty()) {
            return "p:" + e.playId();
        }
        return "e:" + play.atBatIndex() + ":" + e.index();
    }
}
