package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.client.ClientGameState;
import com.cj.mcbaseball.network.ModNetwork;
import com.cj.mcbaseball.network.PitchSelectPacket;
import com.cj.mcbaseball.pitching.PitchType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PitchSelectScreen extends Screen {
    public PitchSelectScreen() {
        super(Component.translatable("mcbaseball.gui.pitch.title"));
    }

    protected void init() {
        PitchType[] types = PitchType.values();
        int current = ClientGameState.hud().pitchType();

        for (int i = 0; i < types.length; i++) {
            PitchType t = types[i];
            int col = i % 2;
            int row = i / 2;
            Component label = (Component)(i == current ? Component.literal("> ").append(t.displayName()).append(" <") : t.displayName());
            this.addRenderableWidget(Button.builder(label, b -> {
                ModNetwork.toServer(new PitchSelectPacket(t.ordinal()));
                this.onClose();
            }).bounds(this.width / 2 - 154 + col * 158, this.height / 2 - 40 + row * 24, 150, 20).build());
        }
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 62, 16777215);
        super.render(g, mx, my, pt);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
