package com.cj.mcbaseball.client;

import net.minecraft.nbt.CompoundTag;

public final class ClientStatsCache {
    public static CompoundTag lastGame = new CompoundTag();
    public static CompoundTag career = new CompoundTag();
    public static int version;

    private ClientStatsCache() {
    }
}
