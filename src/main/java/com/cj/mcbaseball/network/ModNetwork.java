package com.cj.mcbaseball.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "5";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("mcbaseball", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
    );
    private static boolean registered;

    public static void register() {
        if (!registered) {
            registered = true;
            int id = 0;
            CHANNEL.messageBuilder(FieldSetupActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FieldSetupActionPacket::encode)
                .decoder(FieldSetupActionPacket::decode)
                .consumerMainThread(FieldSetupActionPacket::handle)
                .add();
            CHANNEL.messageBuilder(MarkingModePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MarkingModePacket::encode)
                .decoder(MarkingModePacket::decode)
                .consumerMainThread(MarkingModePacket::handle)
                .add();
            CHANNEL.messageBuilder(GameActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GameActionPacket::encode)
                .decoder(GameActionPacket::decode)
                .consumerMainThread(GameActionPacket::handle)
                .add();
            CHANNEL.messageBuilder(TeamEditPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TeamEditPacket::encode)
                .decoder(TeamEditPacket::decode)
                .consumerMainThread(TeamEditPacket::handle)
                .add();
            CHANNEL.messageBuilder(SwingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SwingPacket::encode)
                .decoder(SwingPacket::decode)
                .consumerMainThread(SwingPacket::handle)
                .add();
            CHANNEL.messageBuilder(PitchSelectPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PitchSelectPacket::encode)
                .decoder(PitchSelectPacket::decode)
                .consumerMainThread(PitchSelectPacket::handle)
                .add();
            CHANNEL.messageBuilder(StadiumKitPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StadiumKitPacket::encode)
                .decoder(StadiumKitPacket::decode)
                .consumerMainThread(StadiumKitPacket::handle)
                .add();
            CHANNEL.messageBuilder(ThrowAnimPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ThrowAnimPacket::encode)
                .decoder(ThrowAnimPacket::decode)
                .consumerMainThread(ThrowAnimPacket::handle)
                .add();
            CHANNEL.messageBuilder(GameHudPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GameHudPacket::encode)
                .decoder(GameHudPacket::decode)
                .consumerMainThread(GameHudPacket::handle)
                .add();
            CHANNEL.messageBuilder(GameMessagePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GameMessagePacket::encode)
                .decoder(GameMessagePacket::decode)
                .consumerMainThread(GameMessagePacket::handle)
                .add();
            CHANNEL.messageBuilder(GameOverPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GameOverPacket::encode)
                .decoder(GameOverPacket::decode)
                .consumerMainThread(GameOverPacket::handle)
                .add();
            CHANNEL.messageBuilder(StatsSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StatsSyncPacket::encode)
                .decoder(StatsSyncPacket::decode)
                .consumerMainThread(StatsSyncPacket::handle)
                .add();
            CHANNEL.messageBuilder(TeamsSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TeamsSyncPacket::encode)
                .decoder(TeamsSyncPacket::decode)
                .consumerMainThread(TeamsSyncPacket::handle)
                .add();
            CHANNEL.messageBuilder(LiveBrowserRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LiveBrowserRequestPacket::encode)
                .decoder(LiveBrowserRequestPacket::decode)
                .consumerMainThread(LiveBrowserRequestPacket::handle)
                .add();
            CHANNEL.messageBuilder(LiveScheduleSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LiveScheduleSyncPacket::encode)
                .decoder(LiveScheduleSyncPacket::decode)
                .consumerMainThread(LiveScheduleSyncPacket::handle)
                .add();
            CHANNEL.messageBuilder(LiveWatchActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LiveWatchActionPacket::encode)
                .decoder(LiveWatchActionPacket::decode)
                .consumerMainThread(LiveWatchActionPacket::handle)
                .add();
            CHANNEL.messageBuilder(LiveWatchSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LiveWatchSyncPacket::encode)
                .decoder(LiveWatchSyncPacket::decode)
                .consumerMainThread(LiveWatchSyncPacket::handle)
                .add();
        }
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }

    private ModNetwork() {
    }
}
