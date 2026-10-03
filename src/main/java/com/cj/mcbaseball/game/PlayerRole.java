package com.cj.mcbaseball.game;

public enum PlayerRole {
    NONE,
    BATTER,
    PITCHER,
    CATCHER,
    FIELDER,
    RUNNER,
    ON_DECK;

    public static PlayerRole byId(int id) {
        PlayerRole[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }
}
