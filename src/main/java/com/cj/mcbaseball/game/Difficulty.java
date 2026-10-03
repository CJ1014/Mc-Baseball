package com.cj.mcbaseball.game;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public enum Difficulty {
    ROOKIE(11, 2.1, 0.38, 0.42, 0.4, 0.035, 0.55, 0.945),
    NORMAL(7, 1.6, 0.3, 0.27, 0.28, 0.02, 0.75, 0.975),
    ALL_STAR(4, 1.25, 0.22, 0.17, 0.18, 0.013, 0.88, 0.986),
    LEGEND(2, 0.95, 0.17, 0.1, 0.1, 0.007, 0.97, 0.995);

    public final int reactionTicks;
    public final double swingTimingSigma;
    public final double swingHeightSigma;
    public final double pitchError;
    public final double chaseRate;
    public final double throwError;
    public final double judgment;
    public final double catchReliability;

    private Difficulty(
        int reactionTicks,
        double swingTimingSigma,
        double swingHeightSigma,
        double pitchError,
        double chaseRate,
        double throwError,
        double judgment,
        double catchReliability
    ) {
        this.reactionTicks = reactionTicks;
        this.swingTimingSigma = swingTimingSigma;
        this.swingHeightSigma = swingHeightSigma;
        this.pitchError = pitchError;
        this.chaseRate = chaseRate;
        this.throwError = throwError;
        this.judgment = judgment;
        this.catchReliability = catchReliability;
    }

    public Component displayName() {
        return Component.translatable("mcbaseball.difficulty." + this.name().toLowerCase(Locale.ROOT));
    }

    public Difficulty next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public static Difficulty byId(int id) {
        Difficulty[] v = values();
        return id >= 0 && id < v.length ? v[id] : NORMAL;
    }
}
