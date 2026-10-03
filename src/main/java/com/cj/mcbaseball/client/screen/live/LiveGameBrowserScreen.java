package com.cj.mcbaseball.client.screen.live;

import com.cj.mcbaseball.client.ClientLiveCache;
import com.cj.mcbaseball.live.LiveDates;
import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.network.LiveBrowserRequestPacket;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * "WATCH LIVE GAME": today's real games, grouped LIVE / UPCOMING / FINAL. All data comes from the
 * server; this screen never touches the network itself.
 */
public class LiveGameBrowserScreen extends LiveScreen {

    private static final int LIST_W = 340;
    private static final int ROW_H = 32;
    private static final int HEADER_H = 16;
    private static final int BTN_W = 100;
    private static final int LIST_TOP = 46;

    /** A visual line in the list: either a section header or a game card. */
    private record Row(@Nullable LiveGameStatus.Section header, int count, @Nullable LiveGameSummary game) {
        int height() {
            return this.header != null ? HEADER_H : ROW_H;
        }
    }

    private int scroll;

    public LiveGameBrowserScreen(BlockPos pos, @Nullable Screen parent) {
        this(pos, parent, LiveBrowserRequestPacket.TODAY);
    }

    public LiveGameBrowserScreen(BlockPos pos, @Nullable Screen parent, long day) {
        super(Component.translatable("mcbaseball.gui.live.title"), pos, parent, day);
    }

    private int listBottom() {
        return this.height - 34;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        ClientLiveCache.Received r = this.data();
        if (r == null || r.schedule().status() == LiveProviderStatus.DISABLED) {
            return rows;
        }
        for (LiveGameStatus.Section section : LiveGameStatus.Section.values()) {
            List<LiveGameSummary> games = r.schedule().inSection(section);
            if (games.isEmpty()) {
                continue;
            }
            rows.add(new Row(section, games.size(), null));
            for (LiveGameSummary g : games) {
                rows.add(new Row(null, 0, g));
            }
        }
        return rows;
    }

    private int contentHeight(List<Row> rows) {
        int h = 0;
        for (Row row : rows) {
            h += row.height();
        }
        return h;
    }

    private void clampScroll(List<Row> rows) {
        int max = Math.max(0, this.contentHeight(rows) - (this.listBottom() - LIST_TOP));
        this.scroll = Math.max(0, Math.min(this.scroll, max));
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int x0 = cx - LIST_W / 2;
        List<Row> rows = this.rows();
        this.clampScroll(rows);

        int y = LIST_TOP - this.scroll;
        for (Row row : rows) {
            int h = row.height();
            if (row.game != null && y >= LIST_TOP && y + h <= this.listBottom()) {
                this.addGameButton(row.game, x0 + LIST_W - BTN_W - 6, y + (ROW_H - 20) / 2);
            }
            y += h;
        }

        int by = this.height - 28;
        long resolved = this.resolvedDay();
        Button prev = this.addRenderableWidget(Button.builder(Component.translatable("mcbaseball.gui.live.prev_day"), b -> this.changeDay(-1))
            .bounds(cx - 170, by, 64, 20).build());
        Button today = this.addRenderableWidget(Button.builder(Component.translatable("mcbaseball.gui.live.today_btn"), b -> this.goToday())
            .bounds(cx - 102, by, 56, 20).build());
        Button next = this.addRenderableWidget(Button.builder(Component.translatable("mcbaseball.gui.live.next_day"), b -> this.changeDay(1))
            .bounds(cx - 42, by, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("mcbaseball.gui.live.refresh"), b -> this.ping(true))
            .bounds(cx + 26, by, 70, 20).build());
        this.back(cx + 100, by, 70);
        // The server only serves +/- MAX_DAYS_FROM_TODAY; asking beyond that would never get an answer for that day.
        long serverToday = ClientLiveCache.serverToday();
        boolean known = resolved != Long.MIN_VALUE && serverToday != Long.MIN_VALUE;
        prev.active = known && resolved > serverToday - LiveDates.MAX_DAYS_FROM_TODAY;
        next.active = known && resolved < serverToday + LiveDates.MAX_DAYS_FROM_TODAY;
        today.active = known && resolved != serverToday;
    }

    private void addGameButton(LiveGameSummary g, int x, int y) {
        LiveGameStatus.State s = g.status().state();
        if (s.hasNoGame()) {
            return;
        }
        Component label = switch (s.section()) {
            case LIVE -> Component.translatable("mcbaseball.gui.live.watch").withStyle(ChatFormatting.GREEN);
            case UPCOMING -> Component.translatable("mcbaseball.gui.live.watch_when_live");
            case FINAL -> Component.translatable("mcbaseball.gui.live.result");
        };
        long gameId = g.gameId();
        this.addRenderableWidget(Button.builder(label, b -> this.minecraft.setScreen(new LiveGameDetailScreen(this.pos, this, this.day, gameId)))
            .bounds(x, y, BTN_W, 20).build());
    }

    private void changeDay(int delta) {
        long resolved = this.resolvedDay();
        long serverToday = ClientLiveCache.serverToday();
        if (resolved == Long.MIN_VALUE || serverToday == Long.MIN_VALUE) {
            return;
        }
        long target = resolved + delta;
        if (Math.abs(target - serverToday) > LiveDates.MAX_DAYS_FROM_TODAY) {
            return;
        }
        this.day = target == serverToday ? LiveBrowserRequestPacket.TODAY : target;
        this.scroll = 0;
        this.ping(false);
        this.rebuildWidgets();
    }

    private void goToday() {
        this.day = LiveBrowserRequestPacket.TODAY;
        this.scroll = 0;
        this.ping(false);
        this.rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int before = this.scroll;
        this.scroll -= (int) Math.signum(delta) * 24;
        this.clampScroll(this.rows());
        if (this.scroll != before) {
            this.rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int x0 = cx - LIST_W / 2;
        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), cx, 8, LiveText.WHITE);
        g.drawCenteredString(this.font, this.dayTitle(), cx, 20, LiveText.GOLD);
        g.drawCenteredString(this.font, this.connectionLine(), cx, 31, LiveText.GRAY);

        List<Row> rows = this.rows();
        int bottom = this.listBottom();
        ClientLiveCache.Received r = this.data();
        if (rows.isEmpty()) {
            Component msg;
            if (r == null || r.schedule().status() == LiveProviderStatus.LOADING) {
                msg = Component.translatable("mcbaseball.gui.live.loading_games");
            } else if (r.schedule().status() == LiveProviderStatus.DISABLED) {
                msg = Component.translatable("mcbaseball.gui.live.disabled_long");
            } else if (r.schedule().status() == LiveProviderStatus.UNAVAILABLE) {
                msg = Component.translatable("mcbaseball.gui.live.unavailable_long");
            } else {
                msg = Component.translatable("mcbaseball.gui.live.no_games");
            }
            g.drawCenteredString(this.font, msg.copy().withStyle(ChatFormatting.GRAY), cx, LIST_TOP + 30, LiveText.GRAY);
        } else {
            g.enableScissor(0, LIST_TOP, this.width, bottom);
            int y = LIST_TOP - this.scroll;
            for (Row row : rows) {
                int h = row.height();
                if (y + h > LIST_TOP && y < bottom) {
                    if (row.header != null) {
                        this.renderHeader(g, row, x0, y);
                    } else {
                        this.renderCard(g, row.game, x0, y);
                    }
                }
                y += h;
            }
            g.disableScissor();
            if (this.contentHeight(rows) > bottom - LIST_TOP) {
                this.renderScrollbar(g, rows, x0 + LIST_W + 4, bottom);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics g, Row row, int x0, int y) {
        int color = LiveText.sectionColor(row.header);
        String name = switch (row.header) {
            case LIVE -> "● LIVE";
            case UPCOMING -> "UPCOMING";
            case FINAL -> "FINAL";
        };
        g.drawString(this.font, Component.literal(name + "  (" + row.count + ")").withStyle(ChatFormatting.BOLD), x0 + 2, y + 5, color);
        g.fill(x0, y + 14, x0 + LIST_W, y + 15, 0x40FFFFFF);
    }

    private void renderCard(GuiGraphics g, LiveGameSummary game, int x0, int y) {
        int top = y + 2;
        int bot = y + ROW_H - 2;
        g.fill(x0, top, x0 + LIST_W, bot, 0x70000000);
        g.fill(x0, top, x0 + 2, bot, 0xFF000000 | LiveText.sectionColor(game.section()));

        int textW = LIST_W - BTN_W - 20;
        String matchup = game.matchupShort();
        if (game.doubleHeader()) {
            matchup = matchup + " (G" + game.gameNumber() + ")";
        }
        g.drawString(this.font, this.font.plainSubstrByWidth(matchup, textW), x0 + 8, top + 4, LiveText.WHITE);

        String status = LiveText.statusLine(game);
        int sw = this.font.width(status);
        g.drawString(this.font, status, x0 + 8, top + 16, LiveText.statusColor(game));
        if (game.showsScore()) {
            g.drawString(this.font, this.font.plainSubstrByWidth(LiveText.score(game), Math.max(0, textW - sw - 12)), x0 + 8 + sw + 10, top + 16, LiveText.WHITE);
        }
    }

    private void renderScrollbar(GuiGraphics g, List<Row> rows, int x, int bottom) {
        int view = bottom - LIST_TOP;
        int content = this.contentHeight(rows);
        int barH = Math.max(12, view * view / content);
        int barY = LIST_TOP + (view - barH) * this.scroll / Math.max(1, content - view);
        g.fill(x, LIST_TOP, x + 3, bottom, 0x40FFFFFF);
        g.fill(x, barY, x + 3, barY + barH, 0xC0FFFFFF);
    }
}
