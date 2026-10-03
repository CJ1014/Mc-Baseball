package com.cj.mcbaseball.pitching;

import com.cj.mcbaseball.game.LineupSlot;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class PitchRecord {
    public final PitchType type;
    public final double mph;
    public final long releaseTick;
    public final LineupSlot pitcher;
    public final LineupSlot batter;
    public boolean crossed;
    @Nullable
    public Vec3 crossPoint;
    public double crossTick;
    public boolean inZone;
    public boolean contactCrossed;
    @Nullable
    public Vec3 contactPoint;
    public double contactTick;
    double lastHomeDist = Double.NaN;
    double lastContactDist = Double.NaN;
    @Nullable
    Vec3 lastCenter;
    public boolean swung;
    public boolean contact;
    public boolean resolved;
    public boolean hbp;
    public boolean catcherCaught;
    public long earliestResolve = -1L;

    public PitchRecord(PitchType type, double mph, long releaseTick, LineupSlot pitcher, LineupSlot batter) {
        this.type = type;
        this.mph = mph;
        this.releaseTick = releaseTick;
        this.pitcher = pitcher;
        this.batter = batter;
    }
}
