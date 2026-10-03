package com.cj.mcbaseball.network;

import com.cj.mcbaseball.batting.BattingSystem;
import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.GameManager;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record SwingPacket(boolean bunt) {
    public static void encode(SwingPacket p, FriendlyByteBuf b) {
        b.writeBoolean(p.bunt);
    }

    public static SwingPacket decode(FriendlyByteBuf b) {
        return new SwingPacket(b.readBoolean());
    }

    public static void handle(SwingPacket p, Supplier<Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) {
            BaseballGame g = GameManager.forPlayer(sp.getUUID());
            if (g != null) {
                BattingSystem.humanSwing(g, sp, p.bunt);
            }
        }
    }
}
