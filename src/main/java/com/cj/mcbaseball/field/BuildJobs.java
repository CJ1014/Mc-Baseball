package com.cj.mcbaseball.field;

import com.cj.mcbaseball.MCBaseball;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class BuildJobs {
    private static final List<BuildJob> JOBS = new ArrayList<>();
    public static final int BUDGET_PER_TICK = 4000;

    public static void start(BuildJob job) {
        JOBS.add(job);
    }

    public static boolean busyNear(ServerLevel level, BlockPos pos) {
        for (BuildJob j : JOBS) {
            if (j.level == level && j.anchor.distSqr(pos) < 40000.0) {
                return true;
            }
        }

        return false;
    }

    public static void tick(MinecraftServer server) {
        if (!JOBS.isEmpty()) {
            BuildJob j = JOBS.get(0);

            try {
                if (j.tick(4000)) {
                    JOBS.remove(0);
                }
            } catch (Exception var3) {
                MCBaseball.LOGGER.error("[Baseball] build job failed", var3);
                JOBS.remove(0);
            }
        }
    }

    public static void clear() {
        JOBS.clear();
    }

    private BuildJobs() {
    }
}
