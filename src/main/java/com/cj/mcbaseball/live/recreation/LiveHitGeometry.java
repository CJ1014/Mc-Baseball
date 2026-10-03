package com.cj.mcbaseball.live.recreation;

import com.cj.mcbaseball.live.model.LiveHit;
import javax.annotation.Nullable;

/**
 * Direction and speed for a ball put in play. Real Statcast values (exit velocity, launch angle, spray
 * coordinates) are used whenever the feed has them. Without them the ball still has to go somewhere, so
 * it flies a typical arc for the reported trajectory. Those fallbacks only shape the animation; they are
 * never shown to players as data.
 */
public final class LiveHitGeometry {

    /** MLB spray chart: home plate sits near (125.42, 198.27), with y decreasing toward the outfield. */
    static final double PLATE_X = 125.42;
    static final double PLATE_Y = 198.27;

    private LiveHitGeometry() {
    }

    /** Degrees from straight-away center field, + toward right field. */
    public static double sprayAngle(@Nullable LiveHit hit) {
        if (hit == null) {
            return 0.0;
        }
        if (hit.hasSpray()) {
            return Math.toDegrees(Math.atan2(hit.coordX() - PLATE_X, PLATE_Y - hit.coordY()));
        }
        return switch (hit.location()) {
            case "3" -> 38.0;
            case "4" -> 18.0;
            case "5" -> -38.0;
            case "6" -> -18.0;
            case "7" -> -30.0;
            case "9" -> 30.0;
            default -> 0.0;
        };
    }

    public static double exitMph(@Nullable LiveHit hit) {
        if (hit != null && !Double.isNaN(hit.launchSpeed())) {
            return hit.launchSpeed();
        }
        return switch (hit == null ? "" : hit.trajectory()) {
            case "ground_ball", "bunt_grounder" -> 85.0;
            case "line_drive" -> 95.0;
            case "fly_ball" -> 92.0;
            case "popup", "bunt_popup" -> 72.0;
            default -> 88.0;
        };
    }

    public static double launchDegrees(@Nullable LiveHit hit) {
        if (hit != null && !Double.isNaN(hit.launchAngle())) {
            return hit.launchAngle();
        }
        return switch (hit == null ? "" : hit.trajectory()) {
            case "ground_ball", "bunt_grounder" -> -8.0;
            case "line_drive" -> 14.0;
            case "fly_ball" -> 32.0;
            case "popup", "bunt_popup" -> 60.0;
            default -> 10.0;
        };
    }
}
