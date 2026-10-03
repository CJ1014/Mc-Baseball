package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.client.ClientTeamCache;
import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.game.Position;
import com.cj.mcbaseball.game.TeamSide;
import com.cj.mcbaseball.network.GameActionPacket;
import com.cj.mcbaseball.team.NpcProfile;
import com.cj.mcbaseball.team.TeamData;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public class PlayersScreen extends ControllerScreen {
    private boolean showAway;
    private String lastState = "";

    public PlayersScreen(BlockPos pos, Screen parent) {
        super(Component.translatable("mcbaseball.gui.players.title"), pos, parent);
    }

    private int top() {
        return Math.max(4, this.height / 2 - 115);
    }

    protected void init() {
        FieldControllerBlockEntity be = this.controller();
        if (be != null) {
            this.lastState = be.signups().toString() + ClientTeamCache.version();
            int cx = this.width / 2;
            int by = this.top() + 204;
            FieldControllerBlockEntity.Signup mine = this.minecraft.player == null ? null : be.signups().get(this.minecraft.player.getUUID());
            if (mine != null) {
                Component pos = (Component)(mine.position() < 0
                    ? Component.translatable("mcbaseball.gui.players.any")
                    : Position.byId(mine.position()).displayName());
                this.addRenderableWidget(Button.builder(Component.translatable("mcbaseball.gui.players.my_position", new Object[]{pos}), b -> {
                    int next = mine.position() + 1;
                    this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.SET_POSITION, next > 8 ? -1 : next));
                }).bounds(cx - 154, by, 150, 20).build());
            } else {
                this.addRenderableWidget(
                    Button.builder(
                            Component.translatable("mcbaseball.gui.start.join_home"),
                            b -> this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.JOIN, TeamSide.HOME.ordinal()))
                        )
                        .bounds(cx - 154, by, 74, 20)
                        .build()
                );
                this.addRenderableWidget(
                    Button.builder(
                            Component.translatable("mcbaseball.gui.start.join_away"),
                            b -> this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.JOIN, TeamSide.AWAY.ordinal()))
                        )
                        .bounds(cx - 78, by, 74, 20)
                        .build()
                );
            }

            this.addRenderableWidget(
                Button.builder(Component.translatable(this.showAway ? "mcbaseball.gui.players.show_home" : "mcbaseball.gui.players.show_away"), b -> {
                    this.showAway = !this.showAway;
                    this.rebuildWidgets();
                }).bounds(cx + 4, by, 74, 20).build()
            );
            this.back(cx + 82, by, 72);
        }
    }

    @Nullable
    private TeamData rosterTeam(FieldControllerBlockEntity be) {
        UUID id = this.showAway ? be.settings().awayTeam : be.settings().homeTeam;
        TeamData t = ClientTeamCache.byId(id);
        if (t == null && !ClientTeamCache.teams().isEmpty()) {
            t = ClientTeamCache.teams().get(this.showAway && ClientTeamCache.teams().size() > 1 ? 1 : 0);
        }

        return t;
    }

    @Override
    public void tick() {
        super.tick();
        FieldControllerBlockEntity be = this.controller();
        if (be != null && !(be.signups().toString() + ClientTeamCache.version()).equals(this.lastState)) {
            this.rebuildWidgets();
        }
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        FieldControllerBlockEntity be = this.controller();
        int top = this.top();
        this.title(g, top);
        if (be == null) {
            super.render(g, mx, my, pt);
        } else {
            int x = this.width / 2 - 154;
            int y = top + 16;
            g.drawString(this.font, Component.translatable("mcbaseball.gui.players.humans").withStyle(ChatFormatting.GOLD), x, y, 16777215);
            y += 11;
            if (be.signups().isEmpty()) {
                g.drawString(this.font, Component.translatable("mcbaseball.gui.players.none").withStyle(ChatFormatting.GRAY), x + 4, y, 16777215);
                y += 10;
            }

            for (Entry<UUID, FieldControllerBlockEntity.Signup> e : be.signups().entrySet()) {
                FieldControllerBlockEntity.Signup s = e.getValue();
                String pos = s.position() < 0 ? "Any" : Position.byId(s.position()).abbr;
                g.drawString(this.font, s.name() + "  -  " + s.side().name() + "  (" + pos + ")", x + 4, y, 16777215);
                y += 10;
                if (y > top + 60) {
                    break;
                }
            }

            TeamData t = this.rosterTeam(be);
            y = Math.max(y + 6, top + 68);
            if (t == null) {
                super.render(g, mx, my, pt);
            } else {
                g.drawString(
                    this.font, Component.translatable("mcbaseball.gui.players.roster", new Object[]{t.name}).withStyle(ChatFormatting.GOLD), x, y, 16777215
                );
                g.drawString(this.font, Component.literal("CON POW SPD FLD").withStyle(ChatFormatting.DARK_GRAY), x + 222, y, 16777215);
                y += 12;

                for (Position p : Position.BATTING_ORDER) {
                    NpcProfile np = t.roster.get(p);
                    if (np != null) {
                        g.drawString(this.font, p.abbr, x, y, 16048205);
                        g.drawString(this.font, np.displayName() + (np.batsRight ? "" : " (L)"), x + 22, y, 16777215);
                        String r = String.format("%3d %3d %3d %3d", np.contact, np.power, np.speed, np.fielding);
                        if (p == Position.PITCHER) {
                            r = String.format("VEL %d CTL %d", np.velocity, np.control);
                        }

                        g.drawString(this.font, r, x + 222, y, 12303291);
                        y += 12;
                    }
                }

                super.render(g, mx, my, pt);
            }
        }
    }
}
