package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.game.GameSettings;
import com.cj.mcbaseball.network.GameActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class GameSettingsScreen extends ControllerScreen {
    private String last = "";

    public GameSettingsScreen(BlockPos pos, Screen parent) {
        super(Component.translatable("mcbaseball.gui.settings.title"), pos, parent);
    }

    protected void init() {
        FieldControllerBlockEntity be = this.controller();
        if (be != null) {
            GameSettings s = be.settings();
            this.last = s.save().toString();
            int x = this.width / 2 - 100;
            int y = this.height / 2 - 60;
            this.toggle(x, y, "mcbaseball.gui.settings.extra", s.extraInnings, GameActionPacket.Action.TOGGLE_EXTRA);
            this.toggle(x, y + 24, "mcbaseball.gui.settings.zone", s.showStrikeZone, GameActionPacket.Action.TOGGLE_ZONE);
            this.toggle(x, y + 48, "mcbaseball.gui.settings.landing", s.showLandingMarker, GameActionPacket.Action.TOGGLE_LANDING);
            this.toggle(x, y + 72, "mcbaseball.gui.settings.assist", s.battingAssist, GameActionPacket.Action.TOGGLE_ASSIST);
            this.toggle(x, y + 96, "mcbaseball.gui.settings.simple_bat", s.simpleBatting, GameActionPacket.Action.TOGGLE_SIMPLE_BAT);
            this.toggle(x, y + 120, "mcbaseball.gui.start.autofill", s.npcAutoFill, GameActionPacket.Action.TOGGLE_AUTOFILL);
            this.addRenderableWidget(
                Button.builder(
                        Component.translatable("mcbaseball.gui.start.difficulty").append(": ").append(s.difficulty.displayName()),
                        b -> this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.CYCLE_DIFFICULTY))
                    )
                    .bounds(x, y + 144, 200, 20)
                    .build()
            );
            this.back(x, y + 174, 200);
        }
    }

    private void toggle(int x, int y, String key, boolean on, GameActionPacket.Action a) {
        Component label = Component.translatable(key)
            .append(": ")
            .append(Component.translatable(on ? "options.on" : "options.off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
        this.addRenderableWidget(Button.builder(label, b -> this.send(GameActionPacket.of(this.pos, a))).bounds(x, y, 200, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        FieldControllerBlockEntity be = this.controller();
        if (be != null && !be.settings().save().toString().equals(this.last)) {
            this.rebuildWidgets();
        }
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        this.title(g, this.height / 2 - 84);
        super.render(g, mx, my, pt);
    }
}
