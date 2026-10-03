package com.cj.mcbaseball.live.model;

/** Runs / hits / errors / left on base for one side. Each value is -1 when unreported. */
public record LiveLineTotals(int runs, int hits, int errors, int leftOnBase) {
    public static final LiveLineTotals NONE = new LiveLineTotals(-1, -1, -1, -1);

    public boolean known() {
        return this.runs >= 0 || this.hits >= 0 || this.errors >= 0;
    }
}
