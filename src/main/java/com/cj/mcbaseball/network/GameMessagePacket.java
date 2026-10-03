package com.cj.mcbaseball.network;

import com.cj.mcbaseball.client.ClientGameState;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent.Context;

public record GameMessagePacket(int kind, Component text) {
    public static void encode(GameMessagePacket p, FriendlyByteBuf b) {
        b.writeByte(p.kind);
        b.writeComponent(p.text);
    }

    public static GameMessagePacket decode(FriendlyByteBuf b) {
        return new GameMessagePacket(b.readByte(), b.readComponent());
    }

    public static void handle(GameMessagePacket p, Supplier<Context> ctx) {
        ClientGameState.message(p.kind, p.text);
    }
}
