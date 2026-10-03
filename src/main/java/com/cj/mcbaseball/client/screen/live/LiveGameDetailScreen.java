package com.cj.mcbaseball.client.screen.live;

import com.cj.mcbaseball.client.ClientLiveCache;
import com.cj.mcbaseball.client.ClientLiveWatch;
import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LiveLineTotals;
import com.cj.mcbaseball.network.LiveWatchActionPacket;
import com.cj.mcbaseball.network.ModNetwork;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * One real game: score, inning, count, line score, first-pitch time. Keeps refreshing while open.
 * WATCH LIVE / WAIT FOR GAME makes this Field Controller follow the game: everyone near the field
 * gets the live scoreboard HUD (Phase 2). NPC recreation of the plays comes in later phases.
 */
public class LiveGameDetailScreen extends LiveScreen {

    private final long gameId;

    public LiveGameDetailScreen(BlockPos pos, @Nullable Screen parent, long day, long gameId) {
        super(Component.translatable("mcbaseball.gui.live.title"), pos, parent, day);
        this.gameId = gameId;
    }

    @Nullable
    private LiveGameSummary game() {
        ClientLiveCache.Received r = this.data();
        return r == null ? null : r.schedule().find(this.gameId);
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int by = this.height - 28;
        LiveGameSummary g = this.game();
        boolean thisGameOnField = ClientLiveWatch.watchingAt(this.pos) && ClientLiveWatch.snapshot().gameId() == this.gameId;
        if (thisGameOnField) {
            this.addRenderableWidget(Button.builder(Component.translatable("mcbaseball.gui.live.stop_watching").withStyle(ChatFormatting.RED), b -> {
                ModNetwork.toServer(LiveWatchActionPacket.stop(this.pos));
                this.minecraft.setScreen(null);
            }).bounds(cx - 160, by, 150, 20).build());
            this.back(cx + 10, by, 150);
        } else if (g != null && !g.status().state().hasNoGame() && (g.section() != LiveGameStatus.Section.FINAL || g.gameId() < 0)) {
            Component label = g.section() == LiveGameStatus.Section.LIVE
                ? Component.translatable("mcbaseball.gui.live.watch_live").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                : Component.translatable("mcbaseball.gui.live.wait_for_game").withStyle(ChatFormatting.AQUA);
            Button action = this.addRenderableWidget(Button.builder(label, b -> {
                // The field follows this game; close the menu so the live scoreboard is visible.
                ModNetwork.toServer(LiveWatchActionPacket.start(this.pos, this.gameId));
                this.minecraft.setScreen(null);
            }).bounds(cx - 160, by, 150, 20).build());
            action.setTooltip(Tooltip.create(Component.translatable(
                g.section() == LiveGameStatus.Section.LIVE ? "mcbaseball.gui.live.watch_tip" : "mcbaseball.gui.live.wait_tip")));
            this.back(cx + 10, by, 150);
        } else {
            this.back(cx - 75, by, 150);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        LiveGameSummary game = this.game();
        g.drawCenteredString(this.font, this.connectionLine(), cx, this.height - 42, LiveText.GRAY);
        if (ClientLiveWatch.watchingAt(this.pos) && ClientLiveWatch.snapshot().gameId() == this.gameId) {
            g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.live.on_field").withStyle(ChatFormatting.GREEN), cx, this.height - 54, LiveText.GREEN);
        }
        if (game == null) {
            g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), cx, 8, LiveText.WHITE);
            Component msg = this.data() == null
                ? Component.translatable("mcbaseball.gui.live.loading_games")
                : Component.translatable("mcbaseball.gui.live.game_missing");
            g.drawCenteredString(this.font, msg, cx, 60, LiveText.GRAY);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int y = 8;
        g.drawCenteredString(this.font, Component.literal(game.away().displayFull() + "  @  " + game.home().displayFull()).withStyle(ChatFormatting.BOLD), cx, y, LiveText.WHITE);
        y += 12;
        String sub = game.description();
        if (!game.venue().isEmpty()) {
            sub = sub.isEmpty() ? game.venue() : sub + "  -  " + game.venue();
        }
        if (!sub.isEmpty()) {
            g.drawCenteredString(this.font, this.font.plainSubstrByWidth(sub, this.width - 20), cx, y, LiveText.GRAY);
        }
        y += 18;

        LiveGameStatus.State s = game.status().state();
        switch (s.section()) {
            case LIVE -> y = this.renderLive(g, game, cx, y);
            case UPCOMING -> y = this.renderUpcoming(g, game, cx, y);
            case FINAL -> y = this.renderFinal(g, game, cx, y);
        }
        if (game.showsScore() && !game.awayInningRuns().isEmpty()) {
            this.renderLineScore(g, game, cx, y + 6);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private int renderBigScore(GuiGraphics g, LiveGameSummary game, int cx, int y) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        String away = game.away().displayAbbr() + "  " + game.awayScore();
        String home = game.homeScore() + "  " + game.home().displayAbbr();
        g.drawString(this.font, away, -this.font.width(away) - 8, 0, LiveText.WHITE);
        g.drawCenteredString(this.font, "-", 0, 0, LiveText.GRAY);
        g.drawString(this.font, home, 8, 0, LiveText.WHITE);
        g.pose().popPose();
        return y + 24;
    }

    private int renderLive(GuiGraphics g, LiveGameSummary game, int cx, int y) {
        String head = (game.status().state() == LiveGameStatus.State.LIVE ? "● " : "") + game.status().label();
        g.drawCenteredString(this.font, Component.literal(head).withStyle(ChatFormatting.BOLD), cx, y, LiveText.statusColor(game));
        y += 14;
        y = this.renderBigScore(g, game, cx, y);
        StringBuilder line = new StringBuilder();
        String inning = game.inningLabel();
        if (!inning.isEmpty()) {
            line.append(inning.replace("Bot ", "Bottom ").replace("Mid ", "Middle ").toUpperCase());
        }
        boolean midInning = "Middle".equals(game.inningState()) || "End".equals(game.inningState());
        if (!midInning && game.outs() >= 0) {
            line.append("   ").append(game.outs()).append(game.outs() == 1 ? " OUT" : " OUTS");
        }
        if (!midInning && game.balls() >= 0 && game.strikes() >= 0) {
            line.append("   COUNT ").append(game.balls()).append("-").append(game.strikes());
        }
        g.drawCenteredString(this.font, line.toString(), cx, y, LiveText.YELLOW);
        return y + 14;
    }

    private int renderUpcoming(GuiGraphics g, LiveGameSummary game, int cx, int y) {
        LiveGameStatus.State s = game.status().state();
        Component head = s == LiveGameStatus.State.DELAYED_START
            ? Component.literal(game.status().label())
            : Component.translatable("mcbaseball.gui.live.not_started");
        g.drawCenteredString(this.font, head.copy().withStyle(ChatFormatting.BOLD), cx, y, LiveText.statusColor(game));
        y += 18;
        g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.live.first_pitch"), cx, y, LiveText.GRAY);
        y += 12;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawCenteredString(this.font, LiveText.startTime(game), 0, 0, LiveText.WHITE);
        g.pose().popPose();
        y += 26;
        g.drawCenteredString(this.font, Component.literal(game.away().displayShort() + " at " + game.home().displayShort()), cx, y, LiveText.GRAY);
        return y + 14;
    }

    private int renderFinal(GuiGraphics g, LiveGameSummary game, int cx, int y) {
        LiveGameStatus.State s = game.status().state();
        g.drawCenteredString(this.font, Component.literal(LiveText.statusLine(game)).withStyle(ChatFormatting.BOLD), cx, y, LiveText.statusColor(game));
        y += 14;
        if (s.hasNoGame()) {
            g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.live.no_game_today"), cx, y + 6, LiveText.GRAY);
            return y + 20;
        }
        y = this.renderBigScore(g, game, cx, y);
        g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.live.game_complete"), cx, y, LiveText.GRAY);
        return y + 14;
    }

    private void renderLineScore(GuiGraphics g, LiveGameSummary game, int cx, int y) {
        List<Integer> away = game.awayInningRuns();
        List<Integer> home = game.homeInningRuns();
        int innings = Math.max(9, Math.max(away.size(), home.size()));
        int maxCols = Math.max(9, (this.width - 140) / 16);
        int first = Math.max(0, innings - maxCols);
        int cols = innings - first;
        int nameW = 34;
        int colW = 16;
        int totalW = nameW + cols * colW + 3 * colW + 6;
        int x0 = cx - totalW / 2;
        g.fill(x0 - 4, y - 3, x0 + totalW + 4, y + 36, 0x80000000);
        for (int c = 0; c < cols; c++) {
            String n = String.valueOf(first + c + 1);
            g.drawCenteredString(this.font, n, x0 + nameW + c * colW + colW / 2, y, LiveText.GRAY);
        }
        int tx = x0 + nameW + cols * colW + 6;
        String[] heads = {"R", "H", "E"};
        for (int i = 0; i < 3; i++) {
            g.drawCenteredString(this.font, heads[i], tx + i * colW + colW / 2, y, LiveText.GOLD);
        }
        this.lineRow(g, game.away().displayAbbr(), away, game.awayTotals(), game.awayScore(), x0, y + 12, first, cols, nameW, colW, tx);
        this.lineRow(g, game.home().displayAbbr(), home, game.homeTotals(), game.homeScore(), x0, y + 23, first, cols, nameW, colW, tx);
    }

    private void lineRow(GuiGraphics g, String abbr, List<Integer> runs, LiveLineTotals t, int score, int x0, int y, int first, int cols, int nameW, int colW, int tx) {
        g.drawString(this.font, abbr, x0, y, LiveText.WHITE);
        for (int c = 0; c < cols; c++) {
            int idx = first + c;
            int r = idx < runs.size() && runs.get(idx) != null ? runs.get(idx) : -1;
            g.drawCenteredString(this.font, r < 0 ? "-" : String.valueOf(r), x0 + nameW + c * colW + colW / 2, y, r > 0 ? LiveText.WHITE : LiveText.GRAY);
        }
        int[] vals = {t.runs() >= 0 ? t.runs() : score, t.hits(), t.errors()};
        for (int i = 0; i < 3; i++) {
            g.drawCenteredString(this.font, vals[i] < 0 ? "-" : String.valueOf(vals[i]), tx + i * colW + colW / 2, y, LiveText.WHITE);
        }
    }
}
