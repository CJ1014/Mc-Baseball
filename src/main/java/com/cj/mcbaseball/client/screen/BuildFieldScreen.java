package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.field.FieldBuilder;
import com.cj.mcbaseball.network.FieldSetupActionPacket;
import com.cj.mcbaseball.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class BuildFieldScreen extends ControllerScreen {
    private FieldBuilder.Size size = FieldBuilder.Size.STANDARD;

    public BuildFieldScreen(BlockPos pos, Screen parent) {
        super(Component.translatable("mcbaseball.gui.build.title"), pos, parent);
    }

    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 20;
        int i = 0;

        for (FieldBuilder.Size s : FieldBuilder.Size.values()) {
            Component label = Component.translatable("mcbaseball.gui.build.size." + s.name().toLowerCase());
            if (s == this.size) {
                label = Component.literal("> ").append(label).append(" <").withStyle(ChatFormatting.YELLOW);
            }

            this.addRenderableWidget(Button.builder(label, b -> {
                this.size = s;
                this.rebuildWidgets();
            }).bounds(cx - 154 + i++ * 104, y, 100, 20).build());
        }

        this.addRenderableWidget(
            Button.builder(Component.translatable("mcbaseball.gui.build.go").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}), b -> {
                ModNetwork.toServer(new FieldSetupActionPacket(this.pos, FieldSetupActionPacket.Action.BUILD_FIELD, this.size.ordinal()));
                this.onClose();
            }).bounds(cx - 154, y + 50, 150, 20).build()
        );
        this.back(cx + 4, y + 50, 150);
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int y = this.height / 2 - 20;
        this.title(g, y - 64);
        g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.build.what"), cx, y - 46, 14540253);
        g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.build.where"), cx, y - 34, 14540253);
        int across = this.size.fence + 13;
        g.drawCenteredString(
            this.font,
            Component.translatable("mcbaseball.gui.build.dims", new Object[]{this.size.bases, this.size.fence, across, across}).withStyle(ChatFormatting.GRAY),
            cx,
            y + 26,
            16777215
        );
        g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.build.warning").withStyle(ChatFormatting.RED), cx, y + 78, 16777215);
        super.render(g, mx, my, pt);
    }
}
