package com.cj.mcbaseball.client.screen.live;

import com.cj.mcbaseball.client.ClientLiveCache;
import com.cj.mcbaseball.client.screen.ControllerScreen;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.live.model.LiveSchedule;
import com.cj.mcbaseball.network.LiveBrowserRequestPacket;
import com.cj.mcbaseball.network.ModNetwork;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Base for live-data screens: keeps asking the server for the day's schedule while open (the server
 * decides whether that costs an API call) and rebuilds widgets when new data arrives.
 */
public abstract class LiveScreen extends ControllerScreen {

    /** Ping interval while open. Cheap: the server replies only when its snapshot changed. */
    private static final int PING_TICKS = 100;
    private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US);

    /** Requested day: LiveBrowserRequestPacket.TODAY or an epoch day. */
    protected long day;
    private int ticks;
    private int seenChanges = -1;

    protected LiveScreen(Component title, BlockPos pos, @Nullable Screen parent, long day) {
        super(title, pos, parent);
        this.day = day;
    }

    @Nullable
    protected ClientLiveCache.Received data() {
        return ClientLiveCache.get(this.day);
    }

    protected void ping(boolean force) {
        ClientLiveCache.Received r = this.data();
        int known = r == null ? -1 : r.schedule().version();
        ModNetwork.toServer(new LiveBrowserRequestPacket(this.pos, this.day, known, force));
    }

    @Override
    protected void init() {
        if (this.seenChanges == -1) {
            this.ping(false);
        }
        this.seenChanges = ClientLiveCache.changes();
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.ticks % PING_TICKS == 0) {
            this.ping(false);
        }
        if (ClientLiveCache.changes() != this.seenChanges) {
            this.seenChanges = ClientLiveCache.changes();
            this.rebuildWidgets();
        }
    }

    /** Resolved epoch day being shown, or Long.MIN_VALUE if "today" isn't known yet. */
    protected long resolvedDay() {
        return this.day == LiveBrowserRequestPacket.TODAY ? ClientLiveCache.serverToday() : this.day;
    }

    protected Component dayTitle() {
        long d = this.resolvedDay();
        if (d == Long.MIN_VALUE) {
            return Component.translatable("mcbaseball.gui.live.today");
        }
        String s = DAY_TITLE.format(LocalDate.ofEpochDay(d)).toUpperCase(Locale.ROOT);
        if (d == ClientLiveCache.serverToday()) {
            return Component.translatable("mcbaseball.gui.live.today_is", s);
        }
        return Component.literal(s);
    }

    /** "Updated 4s ago", "RECONNECTING...", "LIVE DATA TEMPORARILY UNAVAILABLE"... */
    protected Component connectionLine() {
        ClientLiveCache.Received r = this.data();
        if (r == null) {
            return Component.translatable("mcbaseball.gui.live.connecting").withStyle(ChatFormatting.GRAY);
        }
        LiveSchedule s = r.schedule();
        long now = System.currentTimeMillis();
        return switch (s.status()) {
            case LOADING -> Component.translatable("mcbaseball.gui.live.loading", s.provider()).withStyle(ChatFormatting.GRAY);
            case OK -> Component.translatable("mcbaseball.gui.live.updated", ago(r.dataAgeMillis(now)), s.provider()).withStyle(ChatFormatting.GRAY);
            case STALE -> Component.translatable("mcbaseball.gui.live.reconnecting", ago(r.dataAgeMillis(now)), secs(r.retryInMillis(now)))
                .withStyle(ChatFormatting.YELLOW);
            case UNAVAILABLE -> Component.translatable("mcbaseball.gui.live.unavailable", secs(r.retryInMillis(now))).withStyle(ChatFormatting.RED);
            case DISABLED -> Component.translatable("mcbaseball.gui.live.disabled").withStyle(ChatFormatting.RED);
        };
    }

    protected boolean hasGames() {
        ClientLiveCache.Received r = this.data();
        return r != null && r.schedule().status() != LiveProviderStatus.DISABLED && !r.schedule().games().isEmpty();
    }

    static String ago(long millis) {
        if (millis < 0L) {
            return "-";
        }
        long s = millis / 1000L;
        if (s < 60L) {
            return s + "s";
        }
        if (s < 3600L) {
            return s / 60L + "m";
        }
        return s / 3600L + "h";
    }

    static String secs(long millis) {
        return Math.max(1L, (millis + 999L) / 1000L) + "s";
    }
}
