package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.network.GameActionPacket;
import com.cj.mcbaseball.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public abstract class ControllerScreen extends Screen {
    protected final BlockPos pos;
    @Nullable
    protected final Screen parent;

    protected ControllerScreen(Component title, BlockPos pos, @Nullable Screen parent) {
        super(title);
        this.pos = pos;
        this.parent = parent;
    }

    @Nullable
    protected FieldControllerBlockEntity controller() {
        return this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.pos) instanceof FieldControllerBlockEntity be
            ? be
            : null;
    }

    protected void send(GameActionPacket p) {
        ModNetwork.toServer(p);
    }

    protected Button back(int x, int y, int w) {
        return (Button)this.addRenderableWidget(
            Button.builder(Component.translatable("gui.back"), b -> this.minecraft.setScreen(this.parent)).bounds(x, y, w, 20).build()
        );
    }

    public void tick() {
        if (this.controller() == null) {
            this.onClose();
        }
    }

    protected void title(GuiGraphics g, int y) {
        g.drawCenteredString(this.font, this.title, this.width / 2, y, 16777215);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
