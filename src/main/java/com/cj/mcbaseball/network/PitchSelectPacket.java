package com.cj.mcbaseball.network;

import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.GameManager;
import com.cj.mcbaseball.pitching.PitchType;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public record PitchSelectPacket(int type) {
    public static void encode(PitchSelectPacket p, FriendlyByteBuf b) {
        b.writeByte(p.type);
    }

    public static PitchSelectPacket decode(FriendlyByteBuf b) {
        return new PitchSelectPacket(b.readByte());
    }

    public static void handle(PitchSelectPacket p, Supplier<Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) {
            BaseballGame g = GameManager.forPlayer(sp.getUUID());
            if (g != null) {
                g.humanPitchType.put(sp.getUUID(), PitchType.byId(p.type));
                g.net.dirty();
            }
        }
    }
}
