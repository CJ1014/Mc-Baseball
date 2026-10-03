package com.cj.mcbaseball.network;

import com.cj.mcbaseball.client.ClientHooks;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public record GameOverPacket(CompoundTag summary, BlockPos controller) {
    public static void encode(GameOverPacket p, FriendlyByteBuf b) {
        b.writeNbt(p.summary);
        b.writeBlockPos(p.controller);
    }

    public static GameOverPacket decode(FriendlyByteBuf b) {
        CompoundTag t = b.readNbt();
        return new GameOverPacket(t == null ? new CompoundTag() : t, b.readBlockPos());
    }

    public static void handle(GameOverPacket p, Supplier<Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openGameOver(p.summary, p.controller));
    }
}
