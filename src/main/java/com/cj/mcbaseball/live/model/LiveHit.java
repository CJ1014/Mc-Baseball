package com.cj.mcbaseball.live.model;

/**
 * Batted-ball data, only as reported. Numbers are NaN and strings "" when the feed doesn't have them:
 * never fill these in with guesses (the recreation decides what to do with a missing value).
 *
 * @param location fielder position number the ball went to ("1"-"9"), "" if unknown
 * @param coordX   MLB spray-chart x (home plate is about (125, 205) in this coordinate system), NaN if unknown
 * @param coordY   MLB spray-chart y, NaN if unknown
 */
public record LiveHit(double launchSpeed, double launchAngle, double totalDistance, String trajectory, String hardness, String location, double coordX, double coordY) {

    public LiveHit {
        trajectory = trajectory == null ? "" : trajectory;
        hardness = hardness == null ? "" : hardness;
        location = location == null ? "" : location;
    }

    public boolean hasLaunch() {
        return !Double.isNaN(this.launchSpeed) && !Double.isNaN(this.launchAngle);
    }

    public boolean hasSpray() {
        return !Double.isNaN(this.coordX) && !Double.isNaN(this.coordY);
    }
}
