package com.cj.mcbaseball.game;

public enum GamePhase {
    WAITING,
    PREGAME,
    PITCHING,
    BALL_IN_PLAY,
    PLAY_OVER,
    SIDE_CHANGE,
    GAME_OVER;

    public static GamePhase byId(int id) {
        GamePhase[] v = values();
        return id >= 0 && id < v.length ? v[id] : WAITING;
    }
}
