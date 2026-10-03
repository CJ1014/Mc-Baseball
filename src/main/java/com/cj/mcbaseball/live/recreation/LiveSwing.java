package com.cj.mcbaseball.live.recreation;

import com.cj.mcbaseball.live.model.LivePitch;

/** What the Minecraft batter does with a pitch, decided by what the real batter did (never by the AI). */
public enum LiveSwing {
    /** No swing: ball, called strike, pitchout, automatic ball/strike. */
    TAKE,
    /** Swings and misses. */
    SWING_MISS,
    /** Swings, the ball is fouled off. */
    FOUL,
    /** Swings, nicks it straight back into the catcher's mitt. */
    FOUL_TIP,
    /** Bunt attempt fouled off. */
    BUNT_FOUL,
    /** Bunt attempt missed. */
    BUNT_MISS,
    /** Ball put in play; the at-bat result decides what happens next. */
    IN_PLAY,
    /** Pitch hits the batter. */
    HIT_BY_PITCH;

    /**
     * From MLB call codes (B, *B, C, S, W, F, T, L, M, X, D, E, H, P, Q, R, V, A, I...). Unknown codes fall back to
     * the pitch's flags and description so a new code never makes the batter do something wrong-headed.
     */
    public static LiveSwing of(LivePitch p) {
        String code = p.callCode();
        switch (code) {
            case "B", "*B", "P", "V", "I", "C", "A", "K" -> {
                return TAKE;
            }
            case "S", "W", "Q" -> {
                return SWING_MISS;
            }
            case "F", "R" -> {
                return FOUL;
            }
            case "T" -> {
                return FOUL_TIP;
            }
            case "L" -> {
                return BUNT_FOUL;
            }
            case "M", "O" -> {
                return BUNT_MISS;
            }
            case "X", "D", "E" -> {
                return IN_PLAY;
            }
            case "H" -> {
                return HIT_BY_PITCH;
            }
            default -> {
            }
        }
        String d = p.call().toLowerCase(java.util.Locale.ROOT);
        if (p.isInPlay() || d.startsWith("in play")) {
            return IN_PLAY;
        }
        if (d.contains("hit by pitch")) {
            return HIT_BY_PITCH;
        }
        if (d.contains("foul tip")) {
            return FOUL_TIP;
        }
        if (d.contains("bunt")) {
            return d.contains("foul") ? BUNT_FOUL : BUNT_MISS;
        }
        if (d.contains("foul")) {
            return FOUL;
        }
        if (d.contains("swinging")) {
            return SWING_MISS;
        }
        return TAKE;
    }

    public boolean swings() {
        return this != TAKE && this != HIT_BY_PITCH;
    }

    public boolean isBunt() {
        return this == BUNT_FOUL || this == BUNT_MISS;
    }
}
