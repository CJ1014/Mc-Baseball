package com.cj.mcbaseball.client;

import com.cj.mcbaseball.client.screen.FieldControllerScreen;
import com.cj.mcbaseball.client.screen.GameOverScreen;
import com.cj.mcbaseball.client.screen.StadiumKitScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class ClientHooks {
    public static void openFieldController(BlockPos pos) {
        Minecraft.getInstance().setScreen(new FieldControllerScreen(pos));
    }

    public static void openStadiumKit(BlockPos pos) {
        Minecraft.getInstance().setScreen(new StadiumKitScreen(pos));
    }

    public static void openGameOver(CompoundTag summary, BlockPos controller) {
        Minecraft.getInstance().setScreen(new GameOverScreen(summary, controller));
    }

    public static void receiveStats(CompoundTag lastGame, CompoundTag career) {
        ClientStatsCache.lastGame = lastGame;
        ClientStatsCache.career = career;
        ClientStatsCache.version++;
    }

    private ClientHooks() {
    }
}
