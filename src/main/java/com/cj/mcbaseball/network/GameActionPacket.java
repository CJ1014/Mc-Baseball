package com.cj.mcbaseball.network;

import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.GameManager;
import com.cj.mcbaseball.game.GameSettings;
import com.cj.mcbaseball.game.TeamSide;
import com.cj.mcbaseball.stats.CareerStats;
import com.cj.mcbaseball.stats.StatLine;
import com.cj.mcbaseball.team.TeamRegistry;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record GameActionPacket(BlockPos controller, GameActionPacket.Action action, int value, UUID uuid) {
    public static final UUID NONE = new UUID(0L, 0L);
    private static final double MAX_DIST_SQR = 144.0;

    public static GameActionPacket of(BlockPos pos, GameActionPacket.Action a) {
        return new GameActionPacket(pos, a, 0, NONE);
    }

    public static GameActionPacket of(BlockPos pos, GameActionPacket.Action a, int v) {
        return new GameActionPacket(pos, a, v, NONE);
    }

    public static GameActionPacket of(BlockPos pos, GameActionPacket.Action a, UUID id) {
        return new GameActionPacket(pos, a, 0, id);
    }

    public static void encode(GameActionPacket p, FriendlyByteBuf b) {
        b.writeBlockPos(p.controller);
        b.writeEnum(p.action);
        b.writeVarInt(p.value);
        b.writeUUID(p.uuid);
    }

    public static GameActionPacket decode(FriendlyByteBuf b) {
        return new GameActionPacket(b.readBlockPos(), (GameActionPacket.Action)b.readEnum(GameActionPacket.Action.class), b.readVarInt(), b.readUUID());
    }

    public static void handle(GameActionPacket p, Supplier<Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) {
            ServerLevel level = sp.serverLevel();
            if (p.action == GameActionPacket.Action.REQUEST_TEAMS) {
                ModNetwork.toPlayer(sp, new TeamsSyncPacket(TeamRegistry.get(sp.server).list()));
            } else if (p.action == GameActionPacket.Action.RETURN_TO_FIELD) {
                if (level.isLoaded(p.controller)
                    && level.getBlockEntity(p.controller) instanceof FieldControllerBlockEntity
                    && sp.position().distanceToSqr(p.controller.getCenter()) < 160000.0) {
                    BlockPos c = p.controller;
                    sp.teleportTo(level, (double)c.getX() + 0.5, (double)c.getY() + 1.0, (double)c.getZ() + 1.5, sp.getYRot(), 0.0F);
                }
            } else if (level.isLoaded(p.controller) && !(sp.distanceToSqr(p.controller.getCenter()) > 144.0)) {
                if (level.getBlockEntity(p.controller) instanceof FieldControllerBlockEntity be) {
                    if (p.action == GameActionPacket.Action.REQUEST_STATS) {
                        CareerStats cs = CareerStats.get(sp.server);
                        CompoundTag last = cs.lastGame(BaseballGame.fieldKey(level, p.controller));
                        StatLine mine = cs.get("p:" + sp.getUUID());
                        CompoundTag career = mine == null ? new CompoundTag() : mine.save();
                        ModNetwork.toPlayer(sp, new StatsSyncPacket(last == null ? new CompoundTag() : last, career));
                    } else {
                        switch (p.action) {
                            case JOIN:
                                be.signup(sp, TeamSide.byId(p.value));
                                return;
                            case LEAVE:
                                be.leave(sp.getUUID());
                                return;
                            case WATCH:
                                be.watch(sp);
                                return;
                            case SET_POSITION:
                                be.setPositionPref(sp.getUUID(), p.value);
                                return;
                            default:
                                if (!be.canEdit(sp)) {
                                    sp.displayClientMessage(
                                        Component.translatable("mcbaseball.field.no_permission", new Object[]{be.ownerName()}).withStyle(ChatFormatting.RED), true
                                    );
                                } else {
                                    GameSettings s = be.settings();
                                    switch (p.action) {
                                        case START:
                                            GameManager.StartResult r = GameManager.tryStart(level, be, sp);
                                            sp.displayClientMessage(r.message(), true);
                                            if (r.ok()) {
                                                be.clearSignups();
                                            }
                                            break;
                                        case END_GAME:
                                            BaseballGame g = GameManager.at(level, p.controller);
                                            if (g != null) {
                                                GameManager.remove(g);
                                            }
                                            break;
                                        case SET_HOME_TEAM:
                                            s.homeTeam = p.uuid;
                                            break;
                                        case SET_AWAY_TEAM:
                                            s.awayTeam = p.uuid;
                                            break;
                                        case SET_INNINGS:
                                            s.innings = GameSettings.clampInnings(p.value);
                                            break;
                                        case TOGGLE_AUTOFILL:
                                            s.npcAutoFill = !s.npcAutoFill;
                                            break;
                                        case CYCLE_DIFFICULTY:
                                            s.difficulty = s.difficulty.next();
                                            break;
                                        case TOGGLE_EXTRA:
                                            s.extraInnings = !s.extraInnings;
                                            break;
                                        case TOGGLE_ZONE:
                                            s.showStrikeZone = !s.showStrikeZone;
                                            break;
                                        case TOGGLE_LANDING:
                                            s.showLandingMarker = !s.showLandingMarker;
                                            break;
                                        case TOGGLE_ASSIST:
                                            s.battingAssist = !s.battingAssist;
                                            break;
                                        case TOGGLE_SIMPLE_BAT:
                                            s.simpleBatting = !s.simpleBatting;
                                    }

                                    be.markSettingsChanged();
                                }
                        }
                    }
                }
            }
        }
    }

    public static enum Action {
        START,
        END_GAME,
        SET_HOME_TEAM,
        SET_AWAY_TEAM,
        SET_INNINGS,
        TOGGLE_AUTOFILL,
        CYCLE_DIFFICULTY,
        TOGGLE_EXTRA,
        TOGGLE_ZONE,
        TOGGLE_LANDING,
        TOGGLE_ASSIST,
        JOIN,
        LEAVE,
        SET_POSITION,
        REQUEST_TEAMS,
        REQUEST_STATS,
        RETURN_TO_FIELD,
        WATCH,
        TOGGLE_SIMPLE_BAT;
    }
}
