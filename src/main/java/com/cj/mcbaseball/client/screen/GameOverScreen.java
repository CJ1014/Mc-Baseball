package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.network.GameActionPacket;
import com.cj.mcbaseball.network.ModNetwork;
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

public class GameOverScreen extends Screen {
    private final CompoundTag summary;
    private final BlockPos controller;
    private final List<StatLine> lines = new ArrayList<>();

    public GameOverScreen(CompoundTag summary, BlockPos controller) {
        super(Component.translatable("mcbaseball.gui.final.title"));
        this.summary = summary;
        this.controller = controller;
        ListTag l = summary.getList("Lines", 10);

        for (int i = 0; i < l.size(); i++) {
            this.lines.add(StatLine.load(l.getCompound(i)));
        }
    }

    protected void init() {
        int y = this.height / 2 + 70;
        this.addRenderableWidget(Button.builder(Component.translatable("mcbaseball.gui.final.return").withStyle(ChatFormatting.GREEN), b -> {
            ModNetwork.toServer(GameActionPacket.of(this.controller, GameActionPacket.Action.RETURN_TO_FIELD));
            this.onClose();
        }).bounds(this.width / 2 - 154, y, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose()).bounds(this.width / 2 + 4, y, 150, 20).build());
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int y = this.height / 2 - 92;
        g.pose().pushPose();
        g.pose().translate((float)cx, (float)y, 0.0F);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawCenteredString(this.font, this.title.copy().withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), 0, 0, 16777215);
        g.pose().popPose();
        y += 28;
        boolean homeWon = this.summary.getInt("HomeRuns") > this.summary.getInt("AwayRuns");
        boolean tie = this.summary.getInt("HomeRuns") == this.summary.getInt("AwayRuns");
        this.row(g, this.summary.getString("Away"), this.summary.getInt("AwayRuns"), cx, y, !homeWon && !tie);
        this.row(g, this.summary.getString("Home"), this.summary.getInt("HomeRuns"), cx, y + 16, homeWon);
        y += 40;
        String winner = this.summary.getString("Winner");
        g.drawCenteredString(
            this.font,
            winner.isEmpty() ? Component.translatable("mcbaseball.gui.final.tie") : Component.translatable("mcbaseball.gui.final.winner", new Object[]{winner}),
            cx,
            y,
            16048205
        );
        y += 14;
        StatLine top = null;
        StatLine ace = null;
        List<String> hrs = new ArrayList<>();

        for (StatLine l : this.lines) {
            if (top == null || l.h > top.h || l.h == top.h && l.rbi > top.rbi) {
                top = l;
            }

            if (l.pitched() && (ace == null || l.pk > ace.pk)) {
                ace = l;
            }

            if (l.hr > 0) {
                hrs.add(l.name + (l.hr > 1 ? " (" + l.hr + ")" : ""));
            }
        }

        if (top != null && top.h > 0) {
            g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.final.top_hitter", new Object[]{top.name, top.h, top.ab, top.rbi}), cx, y, 16777215);
            y += 12;
        }

        g.drawCenteredString(
            this.font, Component.translatable("mcbaseball.gui.final.home_runs", new Object[]{hrs.isEmpty() ? "-" : String.join(", ", hrs)}), cx, y, 16777215
        );
        y += 12;
        if (ace != null) {
            g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.final.strikeouts", new Object[]{ace.name, ace.pk}), cx, y, 16777215);
        }

        super.render(g, mx, my, pt);
    }

    private void row(GuiGraphics g, String team, int runs, int cx, int y, boolean bold) {
        Component t = Component.literal(team.toUpperCase()).withStyle(bold ? ChatFormatting.WHITE : ChatFormatting.GRAY);
        if (bold) {
            t = t.copy().withStyle(ChatFormatting.BOLD);
        }

        g.drawString(this.font, t, cx - 80, y, 16777215);
        g.drawString(this.font, Component.literal(String.valueOf(runs)).withStyle(bold ? ChatFormatting.BOLD : ChatFormatting.GRAY), cx + 70, y, 16777215);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
