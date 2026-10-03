package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.client.ClientStatsCache;
import com.cj.mcbaseball.network.GameActionPacket;
import com.cj.mcbaseball.stats.StatLine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

public class StatsScreen extends ControllerScreen {
    private StatsScreen.Tab tab = StatsScreen.Tab.BATTING;
    private int scroll;

    public StatsScreen(BlockPos pos, Screen parent) {
        super(Component.translatable("mcbaseball.gui.stats.title"), pos, parent);
    }

    protected void init() {
        this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.REQUEST_STATS));
        int cx = this.width / 2;
        int by = this.height - 28;
        int i = 0;

        for (StatsScreen.Tab t : StatsScreen.Tab.values()) {
            Component label = Component.translatable("mcbaseball.gui.stats." + t.name().toLowerCase());
            if (t == this.tab) {
                label = label.copy().withStyle(ChatFormatting.YELLOW);
            }

            this.addRenderableWidget(Button.builder(label, b -> {
                this.tab = t;
                this.scroll = 0;
                this.rebuildWidgets();
            }).bounds(cx - 154 + i++ * 80, by, 76, 20).build());
        }

        this.back(cx + 86, by, 68);
    }

    private List<StatLine> lines() {
        List<StatLine> out = new ArrayList<>();
        ListTag l = ClientStatsCache.lastGame.getList("Lines", 10);

        for (int i = 0; i < l.size(); i++) {
            StatLine s = StatLine.load(l.getCompound(i));
            if (this.tab != StatsScreen.Tab.PITCHING || s.pitched()) {
                out.add(s);
            }
        }

        out.sort((a, b) -> a.team != b.team ? Integer.compare(b.team, a.team) : 0);
        return out;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        this.scroll = Math.max(0, this.scroll - (int)Math.signum(delta));
        return true;
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        this.title(g, 8);
        CompoundTag last = ClientStatsCache.lastGame;
        int x = this.width / 2 - 154;
        int y = 24;
        if (!last.contains("Home")) {
            g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.stats.none").withStyle(ChatFormatting.GRAY), this.width / 2, 40, 16777215);
        } else {
            g.drawCenteredString(
                this.font,
                Component.literal(
                        last.getString("Away") + " " + last.getInt("AwayRuns") + "  -  " + last.getString("Home") + " " + last.getInt("HomeRuns")
                    )
                    .withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}),
                this.width / 2,
                y,
                16777215
            );
            y += 14;

            String header = switch (this.tab) {
                case BATTING -> "  AB   H   R  HR RBI  BB   K";
                case PITCHING -> "   P   K  BB  RA";
                case FIELDING -> "  PO   A   E";
            };
            g.drawString(this.font, Component.literal("PLAYER").withStyle(ChatFormatting.GOLD), x, y, 16777215);
            g.drawString(this.font, Component.literal(header).withStyle(ChatFormatting.GOLD), x + 150, y, 16777215);
            y += 11;
            List<StatLine> lines = this.lines();
            int rows = Math.max(1, (this.height - 60 - y) / 10);
            this.scroll = Math.min(this.scroll, Math.max(0, lines.size() - rows));

            for (int i = this.scroll; i < lines.size() && i < this.scroll + rows; i++) {
                StatLine s = lines.get(i);
                String name = (s.team == 0 ? last.getString("HomeAbbr") : last.getString("AwayAbbr")) + " " + s.name;
                if (this.font.width(name) > 146) {
                    name = this.font.plainSubstrByWidth(name, 140) + "..";
                }
                String nums = switch (this.tab) {
                    case BATTING -> String.format("%4d%4d%4d%4d%4d%4d%4d", s.ab, s.h, s.r, s.hr, s.rbi, s.bb, s.so);
                    case PITCHING -> String.format("%4d%4d%4d%4d", s.pitches, s.pk, s.pbb, s.ra);
                    case FIELDING -> String.format("%4d%4d%4d", s.po, s.a, s.e);
                };
                g.drawString(this.font, name, x, y, 16777215);
                g.drawString(this.font, nums, x + 150, y, 14540253);
                y += 10;
            }
        }

        if (ClientStatsCache.career.contains("S")) {
            StatLine c = StatLine.load(ClientStatsCache.career);
            String avg = c.ab == 0 ? ".000" : String.format("%.3f", (double)c.h / (double)c.ab).replace("0.", ".");
            Component career = Component.translatable("mcbaseball.gui.stats.career", new Object[]{c.games, avg, c.hr, c.rbi, c.pk});
            g.drawCenteredString(this.font, career, this.width / 2, this.height - 42, 16048205);
        }

        super.render(g, mx, my, pt);
    }

    private static enum Tab {
        BATTING,
        PITCHING,
        FIELDING;
    }
}
