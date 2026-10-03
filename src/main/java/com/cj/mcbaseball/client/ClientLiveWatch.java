package com.cj.mcbaseball.client;

import com.cj.mcbaseball.live.model.LiveWatchSnapshot;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/** The live game the stadium we're standing near is following (client thread only). */
public final class ClientLiveWatch {

    @Nullable
    private static BlockPos controller;
    @Nullable
    private static LiveWatchSnapshot snapshot;
    private static long receivedAt;

    private ClientLiveWatch() {
    }

    public static void accept(BlockPos pos, @Nullable LiveWatchSnapshot snap) {
        if (snap == null) {
            if (pos.equals(controller)) {
                clear();
            }
            return;
        }
        controller = pos.immutable();
        snapshot = snap;
        receivedAt = System.currentTimeMillis();
    }

    public static void clear() {
        controller = null;
        snapshot = null;
    }

    @Nullable
    public static LiveWatchSnapshot snapshot() {
        return snapshot;
    }

    @Nullable
    public static BlockPos controller() {
        return controller;
    }

    public static boolean watchingAt(BlockPos pos) {
        return snapshot != null && pos.equals(controller);
    }

    /** Time since the server last got good data, corrected for client/server clock differences. -1 if never. */
    public static long dataAgeMillis() {
        LiveWatchSnapshot s = snapshot;
        if (s == null || s.fetchedAtMillis() <= 0L) {
            return -1L;
        }
        return Math.max(0L, s.serverNowMillis() - s.fetchedAtMillis() + (System.currentTimeMillis() - receivedAt));
    }

    public static long retryInMillis() {
        LiveWatchSnapshot s = snapshot;
        return s == null ? 0L : Math.max(0L, s.retryInMillis() - (System.currentTimeMillis() - receivedAt));
    }
}
