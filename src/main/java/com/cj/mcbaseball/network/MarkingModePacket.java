package com.cj.mcbaseball.network;

import com.cj.mcbaseball.client.ClientFieldSetupState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public record MarkingModePacket(int marker) {
    public static void encode(MarkingModePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.marker);
    }

    public static MarkingModePacket decode(FriendlyByteBuf buf) {
        return new MarkingModePacket(buf.readVarInt());
    }

    public static void handle(MarkingModePacket p, Supplier<Context> ctx) {
        ClientFieldSetupState.setMarker(p.marker);
    }
}
