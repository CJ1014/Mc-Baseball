package com.cj.mcbaseball.client;

import com.cj.mcbaseball.game.GameMessages;
import com.cj.mcbaseball.game.GamePhase;
import com.cj.mcbaseball.game.PlayerRole;
import com.cj.mcbaseball.network.GameHudPacket;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.network.chat.Component;

public final class ClientGameState {
    private static GameHudPacket hud = GameHudPacket.inactive();
    private static ClientGameState.Msg call;
    private static ClientGameState.Msg timing;
    private static ClientGameState.Msg exitVelo;
    private static ClientGameState.Msg pitchSpeed;
    private static ClientGameState.Msg hint;
    private static final Deque<ClientGameState.Msg> feed = new ArrayDeque<>();
    private static PlayerRole lastRole = PlayerRole.NONE;
    private static long roleChangedAt;
    public static boolean showHelp = true;

    public static void update(GameHudPacket p) {
        hud = p;
        PlayerRole r = role();
        if (r != lastRole) {
            if (r != PlayerRole.NONE && r != PlayerRole.ON_DECK) {
                roleChangedAt = System.currentTimeMillis();
            }

            lastRole = r;
        }
    }

    public static long roleChangedAt() {
        return roleChangedAt;
    }

    public static GameHudPacket hud() {
        return hud;
    }

    public static boolean active() {
        return hud.active();
    }

    public static PlayerRole role() {
        return hud.active() ? PlayerRole.byId(hud.role()) : PlayerRole.NONE;
    }

    public static GamePhase phase() {
        return GamePhase.byId(hud.phase());
    }

    public static void message(int kind, Component text) {
        ClientGameState.Msg m = new ClientGameState.Msg(text, System.currentTimeMillis());
        switch (GameMessages.Kind.byId(kind)) {
            case CALL:
                call = m;
                break;
            case TIMING:
                timing = m;
                break;
            case EXIT_VELO:
                exitVelo = m;
                break;
            case PITCH_SPEED:
                pitchSpeed = m;
                break;
            case HINT:
                hint = m;
                break;
            case INFO:
                feed.addLast(m);

                while (feed.size() > 4) {
                    feed.removeFirst();
                }
        }
    }

    public static ClientGameState.Msg call() {
        return call;
    }

    public static ClientGameState.Msg timing() {
        return timing;
    }

    public static ClientGameState.Msg exitVelo() {
        return exitVelo;
    }

    public static ClientGameState.Msg pitchSpeed() {
        return pitchSpeed;
    }

    public static ClientGameState.Msg hint() {
        return hint;
    }

    public static Deque<ClientGameState.Msg> feed() {
        return feed;
    }

    public static void reset() {
        lastRole = PlayerRole.NONE;
        hud = GameHudPacket.inactive();
        hint = null;
        pitchSpeed = null;
        exitVelo = null;
        timing = null;
        call = null;
        feed.clear();
    }

    private ClientGameState() {
    }

    public static record Msg(Component text, long at) {
    }
}
