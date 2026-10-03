package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.field.FieldBuilder;
import com.cj.mcbaseball.field.StadiumKitBlockEntity;
import com.cj.mcbaseball.network.ModNetwork;
import com.cj.mcbaseball.network.StadiumKitPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class StadiumKitScreen extends Screen {
    private final BlockPos pos;
    private int lastSize = -1;

    public StadiumKitScreen(BlockPos pos) {
        super(Component.translatable("mcbaseball.gui.stadium.title"));
        this.pos = pos;
    }

    private StadiumKitBlockEntity kit() {
        return this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.pos) instanceof StadiumKitBlockEntity k
            ? k
            : null;
    }

    protected void init() {
        StadiumKitBlockEntity k = this.kit();
        if (k != null) {
            this.lastSize = k.size().ordinal();
            int cx = this.width / 2;
            int y = this.height / 2 + 4;
            int i = 0;

            for (FieldBuilder.Size s : FieldBuilder.Size.values()) {
                Component label = Component.translatable("mcbaseball.gui.build.size." + s.name().toLowerCase());
                if (s == k.size()) {
                    label = Component.literal("> ").append(label).append(" <").withStyle(ChatFormatting.YELLOW);
                }

                this.addRenderableWidget(
                    Button.builder(label, b -> ModNetwork.toServer(new StadiumKitPacket(this.pos, 0, s.ordinal())))
                        .bounds(cx - 154 + i++ * 104, y, 100, 20)
                        .build()
                );
            }

            this.addRenderableWidget(
                Button.builder(
                        Component.translatable("mcbaseball.gui.stadium.build").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}), b -> {
                            ModNetwork.toServer(new StadiumKitPacket(this.pos, 1, 0));
                            this.onClose();
                        }
                    )
                    .bounds(cx - 154, y + 56, 150, 20)
                    .build()
            );
            this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose()).bounds(cx + 4, y + 56, 150, 20).build());
        }
    }

    public void tick() {
        StadiumKitBlockEntity k = this.kit();
        if (k == null) {
            this.onClose();
        } else {
            if (k.size().ordinal() != this.lastSize) {
                this.rebuildWidgets();
            }
        }
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int y = this.height / 2 + 4;
        g.pose().pushPose();
        g.pose().translate((float)cx, (float)(y - 92), 0.0F);
        g.pose().scale(1.8F, 1.8F, 1.0F);
        g.drawCenteredString(this.font, this.title.copy().withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), 0, 0, 16777215);
        g.pose().popPose();

        for (int i = 1; i <= 4; i++) {
            g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.stadium.line" + i), cx, y - 70 + i * 11, 14540253);
        }

        StadiumKitBlockEntity k = this.kit();
        if (k != null) {
            FieldBuilder.Size s = k.size();
            int across = s.fence + 26 + 8 + 10 + 3;
            g.drawCenteredString(
                this.font,
                Component.translatable("mcbaseball.gui.stadium.dims", new Object[]{s.bases, s.fence, across, across}).withStyle(ChatFormatting.GRAY),
                cx,
                y + 28,
                16777215
            );
        }

        g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.stadium.outline").withStyle(ChatFormatting.AQUA), cx, y + 40, 16777215);
        g.drawCenteredString(this.font, Component.translatable("mcbaseball.gui.build.warning").withStyle(ChatFormatting.RED), cx, y + 84, 16777215);
        super.render(g, mx, my, pt);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
