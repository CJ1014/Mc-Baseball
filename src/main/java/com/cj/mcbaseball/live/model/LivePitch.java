package com.cj.mcbaseball.live.model;

/**
 * One real pitch, as reported by the provider. Only what the feed actually contains:
 * {@code mph} is -1 and the location {@code NaN} when not reported. Nothing is invented.
 *
 * @param id        provider's stable pitch id (MLB playId), "" if none
 * @param typeCode  provider pitch type code, e.g. "FF", "" if unknown
 * @param typeName  e.g. "Four-Seam Fastball"
 * @param call      e.g. "Called Strike", "Ball", "In play, no out"
 * @param plateX    horizontal location at the plate, feet from the middle (catcher's view), NaN if unknown
 * @param plateZ    height at the plate, feet above the ground, NaN if unknown
 * @param zoneTop   batter's strike zone top (feet), NaN if unknown
 * @param zoneBottom batter's strike zone bottom (feet), NaN if unknown
 * @param callCode  provider's call code (MLB: B, *B, C, S, W, F, T, L, M, X, D, E, H...), "" if unknown
 */
public record LivePitch(
    String id,
    String typeCode,
    String typeName,
    double mph,
    String call,
    boolean isStrike,
    boolean isBall,
    boolean isInPlay,
    double plateX,
    double plateZ,
    double zoneTop,
    double zoneBottom,
    int ballsAfter,
    int strikesAfter,
    String callCode
) {
    public static final LivePitch NONE = new LivePitch("", "", "", -1, "", false, false, false, Double.NaN, Double.NaN, Double.NaN, Double.NaN, -1, -1, "");

    public LivePitch {
        id = id == null ? "" : id;
        typeCode = typeCode == null ? "" : typeCode;
        typeName = typeName == null ? "" : typeName;
        call = call == null ? "" : call;
        callCode = callCode == null ? "" : callCode;
    }

    public boolean known() {
        return !this.call.isEmpty() || !this.typeName.isEmpty();
    }

    /** "97.3 mph Four-Seam Fastball - Called Strike" with missing parts left out. */
    public String summary() {
        StringBuilder b = new StringBuilder();
        if (this.mph > 0) {
            b.append(String.format(java.util.Locale.ROOT, "%.1f mph", this.mph));
        }
        if (!this.typeName.isEmpty()) {
            b.append(b.length() > 0 ? " " : "").append(this.typeName);
        }
        if (!this.call.isEmpty()) {
            b.append(b.length() > 0 ? " - " : "").append(this.call);
        }
        return b.toString();
    }
}
