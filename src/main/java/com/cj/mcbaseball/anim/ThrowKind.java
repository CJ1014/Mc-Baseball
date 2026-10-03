package com.cj.mcbaseball.anim;

import com.cj.mcbaseball.pitching.PitchType;

public enum ThrowKind {
    FOUR_SEAM(20, 0.7F),
    TWO_SEAM(20, 0.7F),
    SINKER(20, 0.7F),
    CURVE(20, 0.7F),
    SLIDER(20, 0.7F),
    CHANGEUP(20, 0.7F),
    OVERHAND(12, 0.45F),
    SIDEARM(9, 0.4F),
    FLIP(8, 0.45F),
    CROWHOP(16, 0.55F);

    public final int duration;
    public final float release;

    private ThrowKind(int duration, float release) {
        this.duration = duration;
        this.release = release;
    }

    public boolean isPitch() {
        return this.ordinal() <= CHANGEUP.ordinal();
    }

    public static ThrowKind of(PitchType t) {
        return switch (t) {
            case FOUR_SEAM -> FOUR_SEAM;
            case TWO_SEAM -> TWO_SEAM;
            case SINKER -> SINKER;
            case CURVEBALL -> CURVE;
            case SLIDER -> SLIDER;
            case CHANGEUP -> CHANGEUP;
        };
    }

    public static ThrowKind fielder(String positionAbbr, double distance) {
        boolean outfield = positionAbbr.equals("LF") || positionAbbr.equals("CF") || positionAbbr.equals("RF");
        boolean infield = positionAbbr.equals("1B") || positionAbbr.equals("2B") || positionAbbr.equals("3B") || positionAbbr.equals("SS");
        if (distance < 7.0) {
            return FLIP;
        } else if (outfield && distance > 40.0) {
            return CROWHOP;
        } else {
            return infield && distance < 34.0 ? SIDEARM : OVERHAND;
        }
    }

    public static ThrowKind byId(int id) {
        ThrowKind[] v = values();
        return id >= 0 && id < v.length ? v[id] : OVERHAND;
    }
}
