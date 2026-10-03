package com.cj.mcbaseball.live.recreation;

import com.cj.mcbaseball.live.model.LivePitch;

/**
 * Converts a real pitch location into a point in the mod's strike zone (lateral and height above the
 * ground, in blocks, at the front of home plate). The mod's pitching physics then finds the trajectory.
 *
 * <p>MLB reports {@code pX} (feet from the middle of the plate, catcher's view, + = first-base side) and
 * {@code pZ} (feet above the ground), plus each batter's own zone top/bottom. A pitch on the edge of the
 * real plate lands on the edge of the Minecraft zone, and a pitch at the batter's knees lands at the
 * bottom of the Minecraft zone, whatever the batter's height.
 */
public final class PitchCoordinateMapper {

    /** Half of the 17" plate plus a ball radius, in feet: the horizontal strike-zone edge. */
    public static final double PLATE_EDGE_FEET = 8.5 / 12.0 + 0.12;
    /** Typical zone when the feed doesn't give one (feet). */
    public static final double DEFAULT_ZONE_BOTTOM = 1.6;
    public static final double DEFAULT_ZONE_TOP = 3.4;

    /** Mod strike zone (blocks): FieldGeometry.ZONE_HALF_WIDTH / ZONE_BOTTOM / ZONE_TOP. */
    static final double MC_HALF_WIDTH = 0.5;
    static final double MC_BOTTOM = 0.45;
    static final double MC_TOP = 1.35;

    /** Keep wild pitches on the field: no more than this far outside, never into the ground. */
    static final double MC_MAX_LATERAL = 1.6;
    static final double MC_MIN_HEIGHT = 0.05;
    static final double MC_MAX_HEIGHT = 2.6;

    /** Where the pitch should cross, in the mod's zone coordinates. */
    public record ZonePoint(double lateral, double height, boolean fromData) {
    }

    private PitchCoordinateMapper() {
    }

    /** True when the feed gave an actual location for this pitch. */
    public static boolean hasLocation(LivePitch p) {
        return !Double.isNaN(p.plateX()) && !Double.isNaN(p.plateZ());
    }

    public static ZonePoint map(double plateX, double plateZ, double zoneTop, double zoneBottom) {
        double zb = Double.isNaN(zoneBottom) || zoneBottom <= 0 ? DEFAULT_ZONE_BOTTOM : zoneBottom;
        double zt = Double.isNaN(zoneTop) || zoneTop <= zb + 0.5 ? Math.max(DEFAULT_ZONE_TOP, zb + 1.2) : zoneTop;
        double lateral = plateX / PLATE_EDGE_FEET * MC_HALF_WIDTH;
        double t = (plateZ - zb) / (zt - zb);
        double height = MC_BOTTOM + t * (MC_TOP - MC_BOTTOM);
        return new ZonePoint(clamp(lateral, -MC_MAX_LATERAL, MC_MAX_LATERAL), clamp(height, MC_MIN_HEIGHT, MC_MAX_HEIGHT), true);
    }

    /**
     * Location for a pitch. When the feed has coordinates they are used. When it doesn't, a visual-only spot
     * consistent with the call is chosen (inside the zone for called strikes, just outside for balls) and
     * marked {@code fromData = false}; it is never presented as real data.
     */
    public static ZonePoint forPitch(LivePitch p, LiveSwing swing) {
        if (hasLocation(p)) {
            return map(p.plateX(), p.plateZ(), p.zoneTop(), p.zoneBottom());
        }
        long h = p.id().hashCode() * 2654435761L;
        double u = ((h >>> 8) & 0xFFFF) / 65535.0 * 2 - 1;
        double v = ((h >>> 24) & 0xFFFF) / 65535.0 * 2 - 1;
        if (swing == LiveSwing.TAKE && (p.isBall() || p.callCode().contains("B"))) {
            // Just off the plate, alternating sides.
            double side = u >= 0 ? 1 : -1;
            return new ZonePoint(side * (MC_HALF_WIDTH + 0.25 + Math.abs(u) * 0.2), MC_BOTTOM + 0.45 + v * 0.35, false);
        }
        return new ZonePoint(u * MC_HALF_WIDTH * 0.7, MC_BOTTOM + 0.45 + v * 0.3, false);
    }

    static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
