package com.cj.mcbaseball.game;

public enum TeamSide {
    HOME,
    AWAY;

    public TeamSide other() {
        return this == HOME ? AWAY : HOME;
    }

    public static TeamSide byId(int id) {
        return id == 1 ? AWAY : HOME;
    }
}
