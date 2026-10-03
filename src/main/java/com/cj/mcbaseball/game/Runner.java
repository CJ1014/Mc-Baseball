package com.cj.mcbaseball.game;

public final class Runner {
    public final LineupSlot slot;
    public final int startBase;
    public int base;
    public int target;
    public boolean out;
    public boolean scored;
    public long scoredTick = -1L;
    public boolean mustTagUp;
    public boolean holdHalfway;
    public boolean stealing;
    public long slideUntil;
    public long overrunImmuneUntil;

    public Runner(LineupSlot slot, int startBase) {
        this.slot = slot;
        this.startBase = startBase;
        this.base = startBase;
        this.target = startBase;
    }

    public boolean active() {
        return !this.out && !this.scored;
    }

    public boolean isBatter() {
        return this.startBase == 0;
    }
}
