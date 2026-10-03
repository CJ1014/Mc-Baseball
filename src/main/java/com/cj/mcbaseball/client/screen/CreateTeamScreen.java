package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.network.ModNetwork;
import com.cj.mcbaseball.network.TeamEditPacket;
import com.cj.mcbaseball.team.TeamColors;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class CreateTeamScreen extends ControllerScreen {
    private EditBox name;
    private EditBox abbr;
    private int primary = 12;
    private int secondary = 0;
    private Button create;
    private String keepPrev = "";

    public CreateTeamScreen(BlockPos pos, Screen parent) {
        super(Component.translatable("mcbaseball.gui.create.title"), pos, parent);
    }

    private int top() {
        return Math.max(4, this.height / 2 - 80);
    }

    protected void init() {
        int cx = this.width / 2;
        int y = this.top() + 24;
        String keepName = this.name == null ? "" : this.name.getValue();
        String keepAbbr = this.abbr == null ? "" : this.abbr.getValue();
        this.name = (EditBox)this.addRenderableWidget(new EditBox(this.font, cx - 10, y, 144, 20, Component.translatable("mcbaseball.gui.create.name")));
        this.name.setMaxLength(20);
        this.name.setValue(keepName);
        this.name.setResponder(v -> {
            if (this.abbr.getValue().isEmpty() || this.abbr.getValue().equalsIgnoreCase(autoAbbr(this.keepPrev))) {
                this.abbr.setValue(autoAbbr(v));
            }

            this.keepPrev = v;
            this.updateCreate();
        });
        this.abbr = (EditBox)this.addRenderableWidget(new EditBox(this.font, cx - 10, y + 26, 50, 20, Component.translatable("mcbaseball.gui.create.abbr")));
        this.abbr.setMaxLength(3);
        this.abbr.setValue(keepAbbr);
        this.abbr.setResponder(v -> this.updateCreate());
        this.colorRow(cx, y + 52, true);
        this.colorRow(cx, y + 76, false);
        this.create = (Button)this.addRenderableWidget(
            Button.builder(
                    Component.translatable("mcbaseball.gui.create.create").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}), b -> {
                        ModNetwork.toServer(new TeamEditPacket(0, this.name.getValue(), this.abbr.getValue(), this.primary, this.secondary, new UUID(0L, 0L)));
                        this.minecraft.setScreen(this.parent);
                    }
                )
                .bounds(cx - 154, y + 110, 150, 20)
                .build()
        );
        this.back(cx + 4, y + 110, 150);
        this.setInitialFocus(this.name);
        this.updateCreate();
    }

    private static String autoAbbr(String n) {
        String t = n.replaceAll("[^A-Za-z]", "").toUpperCase();
        return t.length() >= 3 ? t.substring(0, 3) : t;
    }

    private void updateCreate() {
        if (this.create != null) {
            this.create.active = !this.name.getValue().isBlank() && !this.abbr.getValue().isBlank();
        }
    }

    private void colorRow(int cx, int y, boolean isPrimary) {
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.shift(isPrimary, -1)).bounds(cx - 10, y, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.shift(isPrimary, 1)).bounds(cx + 114, y, 20, 20).build());
    }

    private void shift(boolean isPrimary, int d) {
        if (isPrimary) {
            this.primary = Math.floorMod(this.primary + d, TeamColors.RGB.length);
        } else {
            this.secondary = Math.floorMod(this.secondary + d, TeamColors.RGB.length);
        }
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        this.title(g, this.top());
        int cx = this.width / 2;
        int y = this.top() + 24;
        int lx = cx - 154;
        this.lbl(g, "mcbaseball.gui.create.name", lx, y);
        this.lbl(g, "mcbaseball.gui.create.abbr", lx, y + 26);
        this.lbl(g, "mcbaseball.gui.create.primary", lx, y + 52);
        this.lbl(g, "mcbaseball.gui.create.secondary", lx, y + 76);
        this.swatch(g, cx + 52, y + 52, this.primary);
        this.swatch(g, cx + 52, y + 76, this.secondary);
        super.render(g, mx, my, pt);
    }

    private void lbl(GuiGraphics g, String key, int x, int y) {
        g.drawString(this.font, Component.translatable(key).withStyle(ChatFormatting.GOLD), x, y + 6, 16777215);
    }

    private void swatch(GuiGraphics g, int cx, int y, int color) {
        String n = TeamColors.name(color);
        int w = this.font.width(n);
        g.fill(cx - w / 2 - 14, y + 4, cx - w / 2 - 4, y + 16, 0xFF000000 | TeamColors.rgb(color));
        g.drawString(this.font, n, cx - w / 2, y + 6, 16777215);
    }

    @Override
    public void tick() {
        this.name.tick();
        this.abbr.tick();
    }
}
