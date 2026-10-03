package com.cj.mcbaseball.live;

import com.cj.mcbaseball.MCBaseball;
import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.live.mlb.MlbStatsApiProvider;
import com.cj.mcbaseball.live.model.LiveSchedule;
import com.cj.mcbaseball.live.net.LiveApiClient;
import com.cj.mcbaseball.live.net.ResponseRecorder;
import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.live.model.LiveWatchSnapshot;
import com.cj.mcbaseball.network.LiveScheduleSyncPacket;
import com.cj.mcbaseball.network.LiveWatchSyncPacket;
import com.cj.mcbaseball.network.ModNetwork;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/**
 * Owns everything Live Mode runs on the server: the background threads, the HTTP client, the data
 * provider, the shared schedule cache and the games stadiums are following. One instance per running server (integrated or dedicated).
 *
 * <p>Created lazily on the server thread the first time a player opens the live browser, and torn down
 * in {@link #shutdown()} on server stop (which in singleplayer is "leave world"): pending requests are
 * cancelled, late callbacks are ignored, and the threads exit.
 */
public final class LiveBaseballManager {

    @Nullable
    private static LiveBaseballManager instance;

    private final MinecraftServer server;
    private final ThreadPoolExecutor ioExecutor;
    private final ScheduledThreadPoolExecutor scheduler;
    private final LiveApiClient client;
    private final LiveBaseballProvider provider;
    private final LiveScheduleService<UUID> schedules;
    private final LiveWatchService<Stadium> watches;
    /** Which stadium's live HUD each player is currently being sent. */
    private final Map<UUID, Stadium> shown = new HashMap<>();
    private int ticks;

    /** A Field Controller somewhere in the world. */
    public record Stadium(ResourceKey<Level> dimension, BlockPos pos) {
        public static Stadium of(Level level, BlockPos pos) {
            return new Stadium(level.dimension(), pos.immutable());
        }
    }

    private LiveBaseballManager(MinecraftServer server) {
        this.server = server;
        this.ioExecutor = new ThreadPoolExecutor(2, 2, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), daemonThreads("MCBaseball-LiveData-IO"));
        this.ioExecutor.allowCoreThreadTimeOut(true);
        this.scheduler = new ScheduledThreadPoolExecutor(1, daemonThreads("MCBaseball-LiveData-Retry"));
        this.scheduler.setRemoveOnCancelPolicy(true);

        ResponseRecorder recorder = null;
        if (BaseballConfig.LIVE_DEBUG_RECORDING.get()) {
            Path dir = server.getServerDirectory().toPath().resolve("mcbaseball-live-recordings");
            recorder = new ResponseRecorder(dir, BaseballConfig.LIVE_DEBUG_RECORDING_MAX_FILES.get());
            MCBaseball.LOGGER.info("[MCBaseball Live] Debug recording ON -> {}", dir.toAbsolutePath());
        }
        Duration timeout = Duration.ofSeconds(BaseballConfig.LIVE_HTTP_TIMEOUT_SECONDS.get());
        LiveApiClient.Settings settings = new LiveApiClient.Settings(
            Duration.ofSeconds(Math.min(5, timeout.toSeconds())), timeout, BaseballConfig.LIVE_HTTP_RETRIES.get(), 1_000L, 15_000L, userAgent()
        );
        this.client = new LiveApiClient(this.ioExecutor, this.scheduler, settings, System::currentTimeMillis, recorder);
        this.provider = new MlbStatsApiProvider(this.client, BaseballConfig.LIVE_API_BASE_URL.get(), msg -> MCBaseball.LOGGER.warn("[MCBaseball Live] {}", msg));
        LivePollingPolicy policy = new LivePollingPolicy(
            BaseballConfig.LIVE_SCHEDULE_REFRESH_LIVE_SECONDS.get() * 1000L,
            BaseballConfig.LIVE_SCHEDULE_REFRESH_IDLE_SECONDS.get() * 1000L,
            BaseballConfig.LIVE_FEED_REFRESH_SECONDS.get() * 1000L
        );
        this.schedules = new LiveScheduleService<>(
            this.provider,
            policy,
            BaseballConfig.LIVE_ENABLED.get(),
            server,
            System::currentTimeMillis,
            this::sendSchedule,
            msg -> MCBaseball.LOGGER.warn("[MCBaseball Live] {}", msg)
        );
        this.watches = new LiveWatchService<>(
            this.provider, policy, server, System::currentTimeMillis, this::sendToAudience, msg -> MCBaseball.LOGGER.warn("[MCBaseball Live] {}", msg)
        );
    }

    /** Server thread only. */
    public static LiveBaseballManager get(MinecraftServer server) {
        if (instance != null && instance.server != server) {
            shutdown();
        }
        if (instance == null) {
            instance = new LiveBaseballManager(server);
            MCBaseball.LOGGER.info("[MCBaseball Live] Live data started (provider: {})", instance.provider.displayName());
        }
        return instance;
    }

    /** Cancels everything. Safe to call when nothing is running. Server thread only. */
    public static void shutdown() {
        LiveBaseballManager m = instance;
        instance = null;
        if (m == null) {
            return;
        }
        m.schedules.close();
        m.watches.close();
        m.provider.close();
        m.scheduler.shutdownNow();
        m.ioExecutor.shutdownNow();
        MCBaseball.LOGGER.info("[MCBaseball Live] Live data stopped");
    }

    /** A player's live browser wants a day's games (server thread). */
    public void requestSchedule(ServerPlayer player, long epochDay, int knownVersion, boolean force) {
        LocalDate date = LiveDates.resolve(epochDay, Instant.now());
        this.schedules.request(player.getUUID(), date, knownVersion, force);
    }

    /** Called every server tick; does nothing unless Live Mode has been used this session. */
    public static void tickIfRunning(MinecraftServer server) {
        LiveBaseballManager m = instance;
        if (m != null && m.server == server) {
            m.tick();
        }
    }

    /** True if the Field Controller at this position is following a real game. */
    public static boolean isWatching(Level level, BlockPos pos) {
        LiveBaseballManager m = instance;
        return m != null && m.watches.watchedGame(Stadium.of(level, pos)) != null;
    }

    private void tick() {
        this.watches.tick(this.shown::containsValue);
        if (++this.ticks % 20 == 0) {
            this.refreshAudience();
        }
    }

    /** Starts (or switches) the live game this stadium follows. */
    public void startWatching(ServerLevel level, BlockPos controller, long gameId) {
        Stadium s = Stadium.of(level, controller);
        Long before = this.watches.watchedGame(s);
        this.watches.start(s, gameId);
        MCBaseball.LOGGER.info("[MCBaseball Live] Field at {} now following game {}", controller.toShortString(), gameId);
        if (before != null && before != gameId) {
            // Switching games: viewers drop the old game's HUD until the new one's data arrives.
            for (Map.Entry<UUID, Stadium> e : this.shown.entrySet()) {
                if (e.getValue().equals(s)) {
                    this.sendTo(e.getKey(), s, this.watches.snapshot(s));
                }
            }
        }
        this.refreshAudience();
    }

    public void stopWatching(ServerLevel level, BlockPos controller) {
        this.stop(Stadium.of(level, controller));
    }

    private void stop(Stadium s) {
        BlockPos controller = s.pos();
        if (this.watches.watchedGame(s) == null) {
            return;
        }
        this.watches.stop(s);
        MCBaseball.LOGGER.info("[MCBaseball Live] Field at {} stopped following its live game", controller.toShortString());
        this.shown.entrySet().removeIf(e -> {
            if (e.getValue().equals(s)) {
                this.sendTo(e.getKey(), s, null);
                return true;
            }
            return false;
        });
    }

    /** Recomputes who sees which stadium's live HUD: the nearest watching stadium within broadcast range. */
    private void refreshAudience() {
        double radius = BaseballConfig.BROADCAST_RADIUS.get();
        List<Stadium> stadiums = this.watches.watchers();
        // Controllers that were broken (or replaced) stop watching.
        for (Stadium s : stadiums) {
            ServerLevel level = this.server.getLevel(s.dimension());
            if (level == null || level.isLoaded(s.pos()) && !(level.getBlockEntity(s.pos()) instanceof FieldControllerBlockEntity)) {
                this.stop(s);
            }
        }
        stadiums = this.watches.watchers();
        for (ServerPlayer p : this.server.getPlayerList().getPlayers()) {
            Stadium best = null;
            double bestDist = radius * radius;
            for (Stadium s : stadiums) {
                if (s.dimension().equals(p.level().dimension())) {
                    double d = p.distanceToSqr(s.pos().getCenter());
                    if (d <= bestDist) {
                        bestDist = d;
                        best = s;
                    }
                }
            }
            Stadium current = this.shown.get(p.getUUID());
            if (Objects.equals(best, current)) {
                continue;
            }
            if (best == null) {
                this.shown.remove(p.getUUID());
                this.sendTo(p.getUUID(), current, null);
            } else {
                this.shown.put(p.getUUID(), best);
                this.sendTo(p.getUUID(), best, this.watches.snapshot(best));
            }
        }
        this.shown.keySet().removeIf(id -> this.server.getPlayerList().getPlayer(id) == null);
    }

    private void sendToAudience(Stadium s, LiveWatchSnapshot snap) {
        for (Map.Entry<UUID, Stadium> e : this.shown.entrySet()) {
            if (e.getValue().equals(s)) {
                this.sendTo(e.getKey(), s, snap);
            }
        }
    }

    private void sendTo(UUID playerId, Stadium s, @Nullable LiveWatchSnapshot snap) {
        ServerPlayer p = this.server.getPlayerList().getPlayer(playerId);
        if (p != null) {
            ModNetwork.toPlayer(p, new LiveWatchSyncPacket(s.pos(), snap));
        }
    }

    private void sendSchedule(UUID playerId, LiveSchedule schedule) {
        ServerPlayer p = this.server.getPlayerList().getPlayer(playerId);
        if (p != null) {
            ModNetwork.toPlayer(p, new LiveScheduleSyncPacket(schedule));
        }
    }

    public LiveBaseballProvider provider() {
        return this.provider;
    }

    private static String userAgent() {
        String v = ModList.get() == null ? "dev" : ModList.get().getModContainerById(MCBaseball.MODID)
            .map(c -> c.getModInfo().getVersion().toString()).orElse("dev");
        return "MCBaseball/" + v + " (Minecraft mod; live game viewer)";
    }

    private static ThreadFactory daemonThreads(String name) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + n.incrementAndGet());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((th, e) -> MCBaseball.LOGGER.error("[MCBaseball Live] Uncaught error on {}", th.getName(), e));
            return t;
        };
    }
}
