package com.cj.mcbaseball.live.recreation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cj.mcbaseball.live.model.LivePitch;
import org.junit.jupiter.api.Test;

class PitchCoordinateMapperTest {

    private static final double EPS = 1e-9;

    private static LivePitch pitch(String id, String code, boolean ball, double x, double z) {
        return new LivePitch(id, "FF", "Four-Seam Fastball", 95.0, "", !ball, ball, false, x, z, 3.5, 1.6, 0, 0, code);
    }

    @Test
    void middleOfPlateIsMiddleOfZone() {
        PitchCoordinateMapper.ZonePoint p = PitchCoordinateMapper.map(0.0, 2.55, 3.5, 1.6);
        assertEquals(0.0, p.lateral(), EPS);
        assertEquals((PitchCoordinateMapper.MC_BOTTOM + PitchCoordinateMapper.MC_TOP) / 2, p.height(), EPS);
        assertTrue(p.fromData());
    }

    @Test
    void plateEdgesMapToZoneEdges() {
        double edge = PitchCoordinateMapper.PLATE_EDGE_FEET;
        assertEquals(PitchCoordinateMapper.MC_HALF_WIDTH, PitchCoordinateMapper.map(edge, 2.5, 3.5, 1.6).lateral(), EPS);
        assertEquals(-PitchCoordinateMapper.MC_HALF_WIDTH, PitchCoordinateMapper.map(-edge, 2.5, 3.5, 1.6).lateral(), EPS);
        // Knees and letters of *this* batter land on the bottom and top of the Minecraft zone.
        assertEquals(PitchCoordinateMapper.MC_BOTTOM, PitchCoordinateMapper.map(0, 1.6, 3.5, 1.6).height(), EPS);
        assertEquals(PitchCoordinateMapper.MC_TOP, PitchCoordinateMapper.map(0, 3.5, 3.5, 1.6).height(), EPS);
        // A taller batter's zone: the same 3.5 ft pitch is now inside, below the top.
        assertTrue(PitchCoordinateMapper.map(0, 3.5, 3.9, 1.8).height() < PitchCoordinateMapper.MC_TOP);
    }

    @Test
    void wildPitchesAreClampedOntoTheField() {
        PitchCoordinateMapper.ZonePoint dirt = PitchCoordinateMapper.map(4.0, -1.0, 3.5, 1.6);
        assertEquals(PitchCoordinateMapper.MC_MAX_LATERAL, dirt.lateral(), EPS);
        assertEquals(PitchCoordinateMapper.MC_MIN_HEIGHT, dirt.height(), EPS);
        PitchCoordinateMapper.ZonePoint high = PitchCoordinateMapper.map(-4.0, 9.0, 3.5, 1.6);
        assertEquals(-PitchCoordinateMapper.MC_MAX_LATERAL, high.lateral(), EPS);
        assertEquals(PitchCoordinateMapper.MC_MAX_HEIGHT, high.height(), EPS);
    }

    @Test
    void missingOrBrokenZoneUsesDefaults() {
        PitchCoordinateMapper.ZonePoint a = PitchCoordinateMapper.map(0, 2.5, Double.NaN, Double.NaN);
        PitchCoordinateMapper.ZonePoint b = PitchCoordinateMapper.map(0, 2.5, PitchCoordinateMapper.DEFAULT_ZONE_TOP, PitchCoordinateMapper.DEFAULT_ZONE_BOTTOM);
        assertEquals(b.height(), a.height(), EPS);
        // Top below bottom must not divide by zero / flip the zone.
        PitchCoordinateMapper.ZonePoint c = PitchCoordinateMapper.map(0, 2.5, 1.0, 1.6);
        assertTrue(Double.isFinite(c.height()));
    }

    @Test
    void noLocationGivesVisualOnlyPointConsistentWithCall() {
        for (int i = 0; i < 200; i++) {
            LivePitch ball = pitch("ball-" + i, "B", true, Double.NaN, Double.NaN);
            PitchCoordinateMapper.ZonePoint b = PitchCoordinateMapper.forPitch(ball, LiveSwing.TAKE);
            assertFalse(b.fromData());
            assertTrue(Math.abs(b.lateral()) > PitchCoordinateMapper.MC_HALF_WIDTH, "ball without location should be off the plate");

            LivePitch strike = pitch("k-" + i, "C", false, Double.NaN, Double.NaN);
            PitchCoordinateMapper.ZonePoint k = PitchCoordinateMapper.forPitch(strike, LiveSwing.TAKE);
            assertFalse(k.fromData());
            assertTrue(Math.abs(k.lateral()) <= PitchCoordinateMapper.MC_HALF_WIDTH);
            assertTrue(k.height() >= PitchCoordinateMapper.MC_BOTTOM && k.height() <= PitchCoordinateMapper.MC_TOP);
        }
        // Deterministic: replays and spectators see the same pitch.
        LivePitch p = pitch("same", "B", true, Double.NaN, Double.NaN);
        assertEquals(PitchCoordinateMapper.forPitch(p, LiveSwing.TAKE), PitchCoordinateMapper.forPitch(p, LiveSwing.TAKE));
    }

    @Test
    void locationFromFeedIsMarkedAsData() {
        PitchCoordinateMapper.ZonePoint p = PitchCoordinateMapper.forPitch(pitch("x", "C", false, 0.3, 2.2), LiveSwing.TAKE);
        assertTrue(p.fromData());
    }
}
