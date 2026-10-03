package com.cj.mcbaseball.physics;

public enum BallMotion {
    FLYING,
    ROLLING,
    RESTING;

    public static BallMotion byId(int id) {
        BallMotion[] v = values();
        return id >= 0 && id < v.length ? v[id] : FLYING;
    }
}
