package com.cj.mcbaseball.game;

public final class GameMessages {
    private GameMessages() {
    }

    public static enum Kind {
        CALL,
        INFO,
        TIMING,
        EXIT_VELO,
        PITCH_SPEED,
        HINT;

        public static GameMessages.Kind byId(int id) {
            GameMessages.Kind[] v = values();
            return id >= 0 && id < v.length ? v[id] : INFO;
        }
    }
}
