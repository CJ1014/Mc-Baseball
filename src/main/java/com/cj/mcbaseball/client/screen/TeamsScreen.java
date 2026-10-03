package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.client.ClientTeamCache;
import com.cj.mcbaseball.network.ModNetwork;
import com.cj.mcbaseball.network.TeamEditPacket;
import com.cj.mcbaseball.team.TeamColors;
import com.cj.mcbaseball.team.TeamData;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class TeamsScreen extends ControllerScreen {
    private static final int PER_PAGE = 7;
    private int page;
    private int cacheVersion = -1;

    public TeamsScreen(BlockPos pos, Screen parent) {
        super(Component.translatable("mcbaseball.gui.teams.title"), pos, parent);
    }

    private int top() {
        return Math.max(4, this.height / 2 - 105);
    }

    protected void init() {
        this.cacheVersion = ClientTeamCache.version();
        List<TeamData> teams = ClientTeamCache.teams();
        int pages = Math.max(1, (teams.size() + 7 - 1) / 7);
        this.page = Math.min(this.page, pages - 1);
        int y = this.top() + 22;

        for (int i = 0; i < 7; i++) {
            int idx = this.page * 7 + i;
            if (idx >= teams.size()) {
                break;
            }

            TeamData t = teams.get(idx);
            int ry = y + i * 22;
            Button uni = Button.builder(Component.translatable("mcbaseball.gui.teams.uniform"), b -> this.edit(2, t.id))
                .bounds(this.width / 2 + 40, ry, 70, 20)
                .build();
            uni.setTooltip(Tooltip.create(Component.translatable("mcbaseball.gui.teams.uniform.tip")));
            this.addRenderableWidget(uni);
            Button del = Button.builder(Component.literal("✕").withStyle(ChatFormatting.RED), b -> this.edit(1, t.id))
                .bounds(this.width / 2 + 114, ry, 20, 20)
                .build();
            del.setTooltip(Tooltip.create(Component.translatable("mcbaseball.gui.teams.delete")));
            del.active = teams.size() > 2;
            this.addRenderableWidget(del);
        }

        int by = y + 154 + 6;
        Button prev = (Button)this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            this.page--;
            this.rebuildWidgets();
        }).bounds(this.width / 2 - 154, by, 20, 20).build());
        prev.active = this.page > 0;
        Button next = (Button)this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            this.page++;
            this.rebuildWidgets();
        }).bounds(this.width / 2 - 130, by, 20, 20).build());
        next.active = this.page < pages - 1;
        this.addRenderableWidget(
            Button.builder(
                    Component.translatable("mcbaseball.gui.teams.create").withStyle(ChatFormatting.GREEN),
                    b -> this.minecraft.setScreen(new CreateTeamScreen(this.pos, this))
                )
                .bounds(this.width / 2 - 100, by, 120, 20)
                .build()
        );
        this.back(this.width / 2 + 24, by, 110);
    }

    private void edit(int action, UUID id) {
        ModNetwork.toServer(new TeamEditPacket(action, "", "", 0, 0, id));
    }

    @Override
    public void tick() {
        super.tick();
        if (ClientTeamCache.version() != this.cacheVersion) {
            this.rebuildWidgets();
        }
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        this.title(g, this.top());
        List<TeamData> teams = ClientTeamCache.teams();
        int y = this.top() + 22;

        for (int i = 0; i < 7; i++) {
            int idx = this.page * 7 + i;
            if (idx >= teams.size()) {
                break;
            }

            TeamData t = teams.get(idx);
            int ry = y + i * 22;
            int x = this.width / 2 - 150;
            g.fill(x, ry + 3, x + 7, ry + 17, 0xFF000000 | t.primaryRgb());
            g.fill(x + 7, ry + 3, x + 12, ry + 17, 0xFF000000 | t.secondaryRgb());
            g.drawString(this.font, t.name, x + 18, ry + 2, 16777215);
            g.drawString(
                this.font, t.abbreviation + "  " + TeamColors.name(t.primaryColor) + " / " + TeamColors.name(t.secondaryColor), x + 18, ry + 11, 10132122
            );
        }

        if (teams.isEmpty()) {
            g.drawCenteredString(this.font, "...", this.width / 2, y + 10, 11184810);
        }

        super.render(g, mx, my, pt);
    }
}
