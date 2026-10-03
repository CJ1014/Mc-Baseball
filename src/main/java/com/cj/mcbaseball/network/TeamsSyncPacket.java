package com.cj.mcbaseball.network;

import com.cj.mcbaseball.client.ClientTeamCache;
import com.cj.mcbaseball.team.TeamData;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

public record TeamsSyncPacket(List<TeamData> teams) {
    public static void encode(TeamsSyncPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.teams.size());

        for (TeamData t : p.teams) {
            t.write(b);
        }
    }

    public static TeamsSyncPacket decode(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 256);
        List<TeamData> list = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            list.add(TeamData.read(b));
        }

        return new TeamsSyncPacket(list);
    }

    public static void handle(TeamsSyncPacket p, Supplier<Context> ctx) {
        ClientTeamCache.set(p.teams);
    }
}
