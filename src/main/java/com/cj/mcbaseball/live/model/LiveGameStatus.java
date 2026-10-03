package com.cj.mcbaseball.live.model;

import java.util.Locale;

/**
 * Provider-neutral game status. Providers translate their own status codes into a {@link State};
 * everything else in the mod (browser sections, polling rates, session behaviour) keys off the state.
 *
 * @param state    normalized state
 * @param detailed provider's human text, e.g. "Delayed: Rain" (never null, may be empty)
 * @param reason   provider's reason text, e.g. "Rain" (never null, may be empty)
 * @param code     provider's raw status code, kept for the debug panel (never null, may be empty)
 */
public record LiveGameStatus(State state, String detailed, String reason, String code) {

    public LiveGameStatus {
        state = state == null ? State.UNKNOWN : state;
        detailed = detailed == null ? "" : detailed;
        reason = reason == null ? "" : reason;
        code = code == null ? "" : code;
    }

    public static LiveGameStatus of(State state) {
        return new LiveGameStatus(state, "", "", "");
    }

    /** Which block of the live game browser a game is listed under. */
    public enum Section {
        LIVE,
        UPCOMING,
        FINAL
    }

    public enum State {
        /** On the schedule, not yet in pre-game. */
        SCHEDULED(Section.UPCOMING),
        /** Lineups posted, waiting for first pitch. */
        PREGAME(Section.UPCOMING),
        /** First pitch pushed back (rain, etc.). */
        DELAYED_START(Section.UPCOMING),
        /** Teams warming up, first pitch imminent. */
        WARMUP(Section.LIVE),
        /** Ball in play. */
        LIVE(Section.LIVE),
        /** Live but stopped for a replay review / challenge. */
        REVIEW(Section.LIVE),
        /** Live game stopped mid-game (rain delay, etc.). */
        DELAYED(Section.LIVE),
        /** Stopped and to be resumed later (possibly another day). */
        SUSPENDED(Section.LIVE),
        FINAL(Section.FINAL),
        POSTPONED(Section.FINAL),
        CANCELLED(Section.FINAL),
        FORFEIT(Section.FINAL),
        UNKNOWN(Section.UPCOMING);

        private final Section section;

        State(Section section) {
            this.section = section;
        }

        public Section section() {
            return this.section;
        }

        /** True while real baseball may still happen in this game today (worth polling). */
        public boolean isActive() {
            return this.section == Section.LIVE;
        }

        /** Games that will never produce more plays. */
        public boolean isOver() {
            return this == FINAL || this == POSTPONED || this == CANCELLED || this == FORFEIT;
        }

        /** Postponed / cancelled games have nothing to watch or show a result for. */
        public boolean hasNoGame() {
            return this == POSTPONED || this == CANCELLED;
        }
    }

    /** Short, upper-case label for list rows and the HUD: "LIVE", "RAIN DELAY", "FINAL", "POSTPONED: RAIN"... */
    public String label() {
        return switch (this.state) {
            case SCHEDULED -> "NOT STARTED";
            case PREGAME -> "PRE-GAME";
            case DELAYED_START -> this.reason.isEmpty() ? "DELAYED START" : "DELAYED START: " + upper(this.reason);
            case WARMUP -> "WARMUP";
            case LIVE -> "LIVE";
            case REVIEW -> "LIVE - REVIEW";
            case DELAYED -> this.reason.isEmpty() ? "GAME DELAYED" : upper(this.reason) + " DELAY";
            case SUSPENDED -> this.reason.isEmpty() ? "SUSPENDED" : "SUSPENDED: " + upper(this.reason);
            case FINAL -> this.detailed.isEmpty() || this.detailed.equalsIgnoreCase("Final") || this.detailed.equalsIgnoreCase("Game Over")
                ? "FINAL"
                : upper(this.detailed);
            case POSTPONED -> this.reason.isEmpty() ? "POSTPONED" : "POSTPONED: " + upper(this.reason);
            case CANCELLED -> this.reason.isEmpty() ? "CANCELLED" : "CANCELLED: " + upper(this.reason);
            case FORFEIT -> "FORFEIT";
            case UNKNOWN -> this.detailed.isEmpty() ? "UNKNOWN" : upper(this.detailed);
        };
    }

    private static String upper(String s) {
        return s.toUpperCase(Locale.ROOT);
    }
}
