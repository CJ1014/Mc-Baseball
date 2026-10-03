package com.cj.mcbaseball.live.recreation;

import com.cj.mcbaseball.pitching.PitchType;

/**
 * Real pitch types (MLB codes) to the mod's pitch types. The mod has six types, so close relatives share one
 * and differ by how much they break. The real name (e.g. "Cutter") is still what players see on the HUD.
 * Unknown codes fly as a straight generic pitch rather than failing.
 */
public final class LivePitchTypes {

    /** @param breakScale multiplier on the mod type's movement (0 = straight) */
    public record Visual(PitchType type, double breakScale, boolean generic) {
    }

    public static final Visual GENERIC = new Visual(PitchType.FOUR_SEAM, 0.0, true);

    private LivePitchTypes() {
    }

    public static Visual of(String code) {
        return switch (code == null ? "" : code.toUpperCase(java.util.Locale.ROOT)) {
            case "FF", "FA" -> new Visual(PitchType.FOUR_SEAM, 1.0, false);
            case "FT" -> new Visual(PitchType.TWO_SEAM, 1.0, false);
            case "SI" -> new Visual(PitchType.SINKER, 1.0, false);
            // Cutter: fastball speed with a short glove-side break.
            case "FC" -> new Visual(PitchType.SLIDER, 0.35, false);
            case "SL" -> new Visual(PitchType.SLIDER, 1.0, false);
            // Sweeper: a slider with much more sideways break.
            case "ST" -> new Visual(PitchType.SLIDER, 1.4, false);
            case "SV" -> new Visual(PitchType.CURVEBALL, 0.9, false);
            case "CU", "KC", "CS" -> new Visual(PitchType.CURVEBALL, 1.0, false);
            case "CH" -> new Visual(PitchType.CHANGEUP, 1.0, false);
            // Splitter / forkball: a changeup that dives harder.
            case "FS", "FO" -> new Visual(PitchType.CHANGEUP, 1.3, false);
            case "SC" -> new Visual(PitchType.CHANGEUP, 1.1, false);
            case "KN" -> new Visual(PitchType.CHANGEUP, 0.4, false);
            case "EP" -> new Visual(PitchType.CURVEBALL, 0.5, false);
            case "PO", "IN", "AB", "AS" -> new Visual(PitchType.FOUR_SEAM, 0.3, false);
            default -> GENERIC;
        };
    }
}
