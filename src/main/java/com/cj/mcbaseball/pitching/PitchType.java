package com.cj.mcbaseball.pitching;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum PitchType {
    FOUR_SEAM(89, 97, 0.0, 0.0035, 0),
    TWO_SEAM(86, 93, 0.0055, -0.002, 0),
    CHANGEUP(77, 85, 0.003, -0.0065, 0),
    CURVEBALL(71, 80, -0.0045, -0.016, 2),
    SLIDER(81, 88, -0.012, -0.006, 2),
    SINKER(87, 93, 0.004, -0.015, 6);

    public final int minMph;
    public final int maxMph;
    public final double armSide;
    public final double vertical;
    public final int breakDelay;

    private PitchType(int minMph, int maxMph, double armSide, double vertical, int breakDelay) {
        this.minMph = minMph;
        this.maxMph = maxMph;
        this.armSide = armSide;
        this.vertical = vertical;
        this.breakDelay = breakDelay;
    }

    public Component displayName() {
        return Component.translatable("mcbaseball.pitch." + this.name().toLowerCase(Locale.ROOT));
    }

    public boolean isFastball() {
        return this == FOUR_SEAM || this == TWO_SEAM || this == SINKER;
    }

    public static PitchType byId(int id) {
        PitchType[] v = values();
        return id >= 0 && id < v.length ? v[id] : FOUR_SEAM;
    }
}
