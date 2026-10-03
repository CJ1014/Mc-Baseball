package com.cj.mcbaseball.client.screen.live;

import com.cj.mcbaseball.client.ClientLiveWatch;
import com.cj.mcbaseball.client.screen.ControllerScreen;
import com.cj.mcbaseball.live.model.LiveWatchSnapshot;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Developer panel (server config live.debugMode): what the live session knows and is doing. Updates live.
 * Shows provider status, last update, processed ids, queue, real state vs. Minecraft recreation state.
 */
public class LiveDebugScreen extends ControllerScreen {

    public LiveDebugScreen(BlockPos pos, @Nullable Screen parent) {
        super(Component.translatable("mcbaseball.gui.live.debug_title"), pos, parent);
    }

    @Override
    protected void init() {
        this.back(this.width / 2 - 50, this.height - 28, 100);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW), this.width / 2, 8, 0xFFFFFF);
        LiveWatchSnapshot snap = ClientLiveWatch.watchingAt(this.pos) ? ClientLiveWatch.snapshot() : null;
        List<String> lines = snap == null ? List.of() : snap.debugLines();
        int x = 12;
        int y = 26;
        if (lines.isEmpty()) {
            g.drawString(this.font, Component.translatable("mcbaseball.gui.live.debug_none"), x, y, 0xAAAAAA);
        }
        for (String line : lines) {
            if (y > this.height - 40) {
                break;
            }
            int color = line.startsWith("REAL:") ? 0x55FFFF : line.startsWith("MINECRAFT:") ? 0x55FF55 : 0xDDDDDD;
            g.drawString(this.font, this.font.plainSubstrByWidth(line, this.width - 24), x, y, color);
            y += 11;
        }
        if (snap != null && !snap.recentEvents().isEmpty()) {
            y += 6;
            g.drawString(this.font, Component.literal("Recently played:").withStyle(ChatFormatting.GOLD), x, y, 0xFFAA00);
            y += 11;
            for (String e : snap.recentEvents()) {
                if (y > this.height - 40) {
                    break;
                }
                g.drawString(this.font, this.font.plainSubstrByWidth("  " + e, this.width - 24), x, y, 0xAAAAAA);
                y += 10;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
