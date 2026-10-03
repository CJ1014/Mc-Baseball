package com.cj.mcbaseball.game;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class PlayTracker {
    public final boolean batted;
    public final long startTick;
    public final int outsAtStart;
    public final List<Runner> runners = new ArrayList<>();
    @Nullable
    public final LineupSlot batter;
    public FairState fair = FairState.UNDECIDED;
    public boolean inFlight;
    public boolean caughtInAir;
    public boolean homeRun;
    public boolean groundRuleDouble;
    public int outsOnPlay;
    public int errors;
    public boolean forceThirdOut;
    public long thirdOutTick = -1L;
    @Nullable
    public LineupSlot lastThrower;
    public long lastThrowTick;
    @Nullable
    public Vec3 contactPoint;
    public double exitMph;
    public double launchDeg;
    public int settledTicks;
    @Nullable
    public LineupSlot holderSince;
    public long holderSinceTick;
    public int throwTargetBase = -1;
    public long endAt = -1L;

    public PlayTracker(boolean batted, long startTick, int outsAtStart, @Nullable LineupSlot batter) {
        this.batted = batted;
        this.inFlight = batted;
        this.startTick = startTick;
        this.outsAtStart = outsAtStart;
        this.batter = batter;
    }

    @Nullable
    public Runner batterRunner() {
        for (Runner r : this.runners) {
            if (r.isBatter()) {
                return r;
            }
        }

        return null;
    }

    @Nullable
    public Runner byStartBase(int b) {
        for (Runner r : this.runners) {
            if (r.startBase == b) {
                return r;
            }
        }

        return null;
    }

    @Nullable
    public Runner of(LineupSlot s) {
        for (Runner r : this.runners) {
            if (r.slot == s) {
                return r;
            }
        }

        return null;
    }

    public boolean isForced(Runner r) {
        if (!this.batted || this.caughtInAir || this.fair == FairState.FOUL) {
            return false;
        } else if (r.isBatter()) {
            return true;
        } else {
            Runner behind = this.byStartBase(r.startBase - 1);
            return behind != null && !behind.out && this.isForced(behind);
        }
    }
}
