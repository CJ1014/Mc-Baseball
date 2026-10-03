package com.cj.mcbaseball.game;

import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.network.GameHudPacket;
import com.cj.mcbaseball.network.GameMessagePacket;
import com.cj.mcbaseball.network.GameOverPacket;
import com.cj.mcbaseball.network.ModNetwork;
import com.cj.mcbaseball.scoreboard.ScoreboardBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GameBroadcaster {
    private final BaseballGame g;
    private boolean dirty = true;
    private Set<UUID> viewers = new HashSet<>();

    public GameBroadcaster(BaseballGame g) {
        this.g = g;
    }

    public void dirty() {
        this.dirty = true;
    }

    public void tick() {
        int every = this.g.phase == GamePhase.BALL_IN_PLAY ? 4 : (this.g.phase == GamePhase.PITCHING ? 10 : 20);
        if (this.dirty || this.g.tick % (long)every == 0L) {
            this.sendHud();
            this.dirty = false;
        }

        if (this.g.tick % 20L == 0L) {
            this.updateScoreboards();
        }
    }

    public List<ServerPlayer> audience() {
        double r = (double)((Integer)BaseballConfig.BROADCAST_RADIUS.get()).intValue();
        List<ServerPlayer> out = new ArrayList<>();

        for (ServerPlayer p : this.g.level.players()) {
            if (p.position().distanceToSqr(this.g.geo.home) < r * r || this.g.isParticipant(p.getUUID())) {
                out.add(p);
            }
        }

        return out;
    }

    public PlayerRole role(ServerPlayer p) {
        LineupSlot s = this.g.slotOf(p);
        if (s == null) {
            return PlayerRole.NONE;
        } else if (this.g.isDefense(s)) {
            if (s == this.g.pitcherSlot()) {
                return PlayerRole.PITCHER;
            } else {
                return s.position == Position.CATCHER ? PlayerRole.CATCHER : PlayerRole.FIELDER;
            }
        } else if (s != this.g.batter || this.g.phase != GamePhase.PITCHING && this.g.phase != GamePhase.PLAY_OVER) {
            for (int b = 1; b <= 3; b++) {
                if (this.g.onBase[b] == s) {
                    return PlayerRole.RUNNER;
                }
            }

            return this.g.play != null && this.g.play.of(s) != null ? PlayerRole.RUNNER : PlayerRole.ON_DECK;
        } else {
            return PlayerRole.BATTER;
        }
    }

    private void sendHud() {
        Set<UUID> now = new HashSet<>();

        for (ServerPlayer p : this.audience()) {
            PlayerRole r = this.role(p);
            ModNetwork.toPlayer(p, GameHudPacket.of(this.g, r, p.getUUID(), Coach.line(this.g, p, r)));
            now.add(p.getUUID());
        }

        for (UUID old : this.viewers) {
            if (!now.contains(old)) {
                ServerPlayer p = this.g.level.getServer().getPlayerList().getPlayer(old);
                if (p != null) {
                    ModNetwork.toPlayer(p, GameHudPacket.inactive());
                }
            }
        }

        this.viewers = now;
    }

    public void sendInactive() {
        for (UUID id : this.viewers) {
            ServerPlayer p = this.g.level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                ModNetwork.toPlayer(p, GameHudPacket.inactive());
            }
        }

        this.viewers.clear();
    }

    public void call(Component text) {
        this.send(GameMessages.Kind.CALL, text);
    }

    public void info(Component text) {
        this.send(GameMessages.Kind.INFO, text);
    }

    public void toRoles(GameMessages.Kind kind, Component text) {
        this.send(kind, text);
    }

    private void send(GameMessages.Kind kind, Component text) {
        GameMessagePacket pkt = new GameMessagePacket(kind.ordinal(), text);

        for (ServerPlayer p : this.audience()) {
            ModNetwork.toPlayer(p, pkt);
        }
    }

    public void personal(ServerPlayer p, GameMessages.Kind kind, Component text) {
        ModNetwork.toPlayer(p, new GameMessagePacket(kind.ordinal(), text));
    }

    public void sendGameOver(CompoundTag summary) {
        GameOverPacket pkt = new GameOverPacket(summary, this.g.controllerPos);

        for (ServerPlayer p : this.audience()) {
            ModNetwork.toPlayer(p, pkt);
        }

        this.updateScoreboards();
    }

    private void updateScoreboards() {
        if (this.g.level.isLoaded(this.g.controllerPos)) {
            if (this.g.level.getBlockEntity(this.g.controllerPos) instanceof FieldControllerBlockEntity be) {
                ScoreboardBlockEntity.Snapshot snap = new ScoreboardBlockEntity.Snapshot(
                    true,
                    this.g.away.abbr(),
                    this.g.home.abbr(),
                    this.g.away.runs,
                    this.g.home.runs,
                    this.g.inning,
                    this.g.top,
                    this.g.balls,
                    this.g.strikes,
                    this.g.outs,
                    this.g.phase == GamePhase.GAME_OVER,
                    this.g.away.data.primaryRgb(),
                    this.g.home.data.primaryRgb()
                );

                for (BlockPos pos : new ArrayList<>(be.scoreboards())) {
                    if (this.g.level.isLoaded(pos)) {
                        if (this.g.level.getBlockEntity(pos) instanceof ScoreboardBlockEntity sb) {
                            sb.update(snap);
                        } else {
                            be.unlinkScoreboard(pos);
                        }
                    }
                }
            }
        }
    }
}
