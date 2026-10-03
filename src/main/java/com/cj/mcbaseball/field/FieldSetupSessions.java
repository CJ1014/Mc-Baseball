package com.cj.mcbaseball.field;

import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.network.MarkingModePacket;
import com.cj.mcbaseball.network.ModNetwork;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class FieldSetupSessions {
    private static final Map<UUID, FieldSetupSessions.Session> SESSIONS = new HashMap<>();

    public static void begin(ServerPlayer player, BlockPos controller, FieldMarker marker) {
        long now = player.serverLevel().getGameTime();
        long timeout = (long)((Integer)BaseballConfig.MARKING_TIMEOUT_SECONDS.get()).intValue() * 20L;
        SESSIONS.put(player.getUUID(), new FieldSetupSessions.Session(controller.immutable(), player.level().dimension(), marker, now + timeout));
        ModNetwork.toPlayer(player, new MarkingModePacket(marker.ordinal()));
        String hintKey = marker.multiPoint() ? "mcbaseball.marking.hint_wall" : "mcbaseball.marking.hint";
        player.displayClientMessage(
            Component.translatable(hintKey, new Object[]{marker.displayName().copy().withStyle(ChatFormatting.YELLOW)}).withStyle(ChatFormatting.WHITE), true
        );
    }

    @Nullable
    public static FieldSetupSessions.Session get(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    public static void end(ServerPlayer player, @Nullable Component message) {
        if (SESSIONS.remove(player.getUUID()) != null) {
            ModNetwork.toPlayer(player, new MarkingModePacket(-1));
            if (message != null) {
                player.displayClientMessage(message, true);
            }
        }
    }

    public static void forget(UUID id) {
        SESSIONS.remove(id);
    }

    public static void handleClick(ServerPlayer player, BlockPos clicked) {
        FieldSetupSessions.Session s = SESSIONS.get(player.getUUID());
        if (s != null) {
            if (!player.level().dimension().equals(s.dimension())) {
                end(player, Component.translatable("mcbaseball.marking.cancelled").withStyle(ChatFormatting.GRAY));
            } else if (clicked.equals(s.controller())) {
                end(player, Component.translatable("mcbaseball.marking.done").withStyle(ChatFormatting.GREEN));
            } else if (s.marker().multiPoint() && player.isShiftKeyDown()) {
                end(player, Component.translatable("mcbaseball.marking.done").withStyle(ChatFormatting.GREEN));
            } else {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(s.controller()) && level.getBlockEntity(s.controller()) instanceof FieldControllerBlockEntity be) {
                    FieldControllerBlockEntity.MarkResult var7 = be.applyMarker(s.marker(), clicked, player);
                    switch (var7) {
                        case OK:
                            level.playSound(null, clicked, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.4F);
                            if (s.marker().multiPoint()) {
                                int n = be.layout().outfieldWall().size();
                                SESSIONS.put(
                                    player.getUUID(),
                                    new FieldSetupSessions.Session(
                                        s.controller(),
                                        s.dimension(),
                                        s.marker(),
                                        level.getGameTime() + (long)((Integer)BaseballConfig.MARKING_TIMEOUT_SECONDS.get()).intValue() * 20L
                                    )
                                );
                                player.displayClientMessage(Component.translatable("mcbaseball.marking.wall_added", new Object[]{n}).withStyle(ChatFormatting.GREEN), true);
                            } else {
                                end(
                                    player,
                                    Component.translatable(
                                            "mcbaseball.marking.set",
                                            new Object[]{s.marker().displayName(), clicked.getX(), clicked.getY(), clicked.getZ()}
                                        )
                                        .withStyle(ChatFormatting.GREEN)
                                );
                            }
                            break;
                        case TOO_FAR:
                            player.displayClientMessage(
                                Component.translatable("mcbaseball.marking.too_far", new Object[]{BaseballConfig.FIELD_MAX_RADIUS.get()})
                                    .withStyle(ChatFormatting.RED),
                                true
                            );
                            break;
                        case WALL_FULL:
                            end(player, Component.translatable("mcbaseball.marking.wall_full").withStyle(ChatFormatting.YELLOW));
                            break;
                        case NO_PERMISSION:
                            end(player, Component.translatable("mcbaseball.field.no_permission", new Object[]{be.ownerName()}).withStyle(ChatFormatting.RED));
                    }
                } else {
                    end(player, Component.translatable("mcbaseball.marking.controller_gone").withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    public static void tickExpiry(MinecraftServer server) {
        if (!SESSIONS.isEmpty()) {
            Iterator<Entry<UUID, FieldSetupSessions.Session>> it = SESSIONS.entrySet().iterator();

            while (it.hasNext()) {
                Entry<UUID, FieldSetupSessions.Session> e = it.next();
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null) {
                    it.remove();
                } else if (p.serverLevel().getGameTime() > e.getValue().expiresAt()) {
                    it.remove();
                    ModNetwork.toPlayer(p, new MarkingModePacket(-1));
                    p.displayClientMessage(Component.translatable("mcbaseball.marking.timeout").withStyle(ChatFormatting.GRAY), true);
                }
            }
        }
    }

    public static void clearAll() {
        SESSIONS.clear();
    }

    private FieldSetupSessions() {
    }

    public static record Session(BlockPos controller, ResourceKey<Level> dimension, FieldMarker marker, long expiresAt) {
    }
}
