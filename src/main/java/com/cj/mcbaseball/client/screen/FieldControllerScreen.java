package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.network.GameActionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class FieldControllerScreen extends ControllerScreen {
    private static final int BTN_W = 170;
    private static final int GAP = 24;
    private boolean lastActive;

    public FieldControllerScreen(BlockPos pos) {
        super(Component.translatable("mcbaseball.gui.field.title"), pos, null);
    }

    protected void init() {
        this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.REQUEST_TEAMS));
        FieldControllerBlockEntity be = this.controller();
        this.lastActive = be != null && be.isGameActive();
        int x = this.width / 2 - 85;
        int y = this.height / 2 - 62;
        this.addRenderableWidget(
            Button.builder(
                    Component.translatable("mcbaseball.gui.field.how").withStyle(ChatFormatting.AQUA), b -> this.minecraft.setScreen(new HowToPlayScreen(this))
                )
                .bounds(this.width - 108, 8, 100, 20)
                .build()
        );
        if (this.lastActive) {
            this.addRenderableWidget(
                Button.builder(
                        Component.translatable("mcbaseball.gui.field.end_game").withStyle(ChatFormatting.RED),
                        b -> this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.END_GAME))
                    )
                    .bounds(x, y, 170, 20)
                    .build()
            );
        } else {
            this.addRenderableWidget(
                Button.builder(
                        Component.translatable("mcbaseball.gui.field.start_game").withStyle(ChatFormatting.GREEN),
                        b -> this.minecraft.setScreen(new StartGameScreen(this.pos, this))
                    )
                    .bounds(x, y, 170, 20)
                    .build()
            );
        }

        this.addRenderableWidget(
            Button.builder(Component.translatable("mcbaseball.gui.field.teams"), b -> this.minecraft.setScreen(new TeamsScreen(this.pos, this)))
                .bounds(x, y + 24, 170, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("mcbaseball.gui.field.players"), b -> this.minecraft.setScreen(new PlayersScreen(this.pos, this)))
                .bounds(x, y + 48, 170, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("mcbaseball.gui.field.setup"), b -> this.minecraft.setScreen(new FieldSetupScreen(this.pos, this)))
                .bounds(x, y + 72, 170, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("mcbaseball.gui.field.settings"), b -> this.minecraft.setScreen(new GameSettingsScreen(this.pos, this)))
                .bounds(x, y + 96, 170, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("mcbaseball.gui.field.stats"), b -> this.minecraft.setScreen(new StatsScreen(this.pos, this)))
                .bounds(x, y + 120, 170, 20)
                .build()
        );
    }

    @Override
    public void tick() {
        super.tick();
        FieldControllerBlockEntity be = this.controller();
        if (be != null && be.isGameActive() != this.lastActive) {
            this.rebuildWidgets();
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int top = this.height / 2 - 62;
        this.title(g, top - 36);
        FieldControllerBlockEntity be = this.controller();
        if (be != null) {
            Component status = (Component)(be.isGameActive()
                ? Component.translatable("mcbaseball.gui.field.in_progress").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD})
                : be.statusLine());
            g.drawCenteredString(this.font, status, this.width / 2, top - 20, 16777215);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }
}
