package com.cj.mcbaseball.network;

import com.cj.mcbaseball.anim.ThrowKind;
import com.cj.mcbaseball.client.anim.ClientThrowAnims;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent.Context;

public record ThrowAnimPacket(int entityId, int kind) {
    public static void broadcast(LivingEntity thrower, ThrowKind kind) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> thrower), new ThrowAnimPacket(thrower.getId(), kind.ordinal()));
    }

    public static void encode(ThrowAnimPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entityId);
        b.writeByte(p.kind);
    }

    public static ThrowAnimPacket decode(FriendlyByteBuf b) {
        return new ThrowAnimPacket(b.readVarInt(), b.readByte());
    }

    public static void handle(ThrowAnimPacket p, Supplier<Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientThrowAnims.start(p.entityId, ThrowKind.byId(p.kind)));
    }
}
