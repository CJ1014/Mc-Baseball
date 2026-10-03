package com.cj.mcbaseball.network;

import com.cj.mcbaseball.client.ClientHooks;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public record StatsSyncPacket(CompoundTag lastGame, CompoundTag career) {
    public static void encode(StatsSyncPacket p, FriendlyByteBuf b) {
        b.writeNbt(p.lastGame);
        b.writeNbt(p.career);
    }

    public static StatsSyncPacket decode(FriendlyByteBuf b) {
        CompoundTag a = b.readNbt();
        CompoundTag c = b.readNbt();
        return new StatsSyncPacket(a == null ? new CompoundTag() : a, c == null ? new CompoundTag() : c);
    }

    public static void handle(StatsSyncPacket p, Supplier<Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.receiveStats(p.lastGame, p.career));
    }
}
