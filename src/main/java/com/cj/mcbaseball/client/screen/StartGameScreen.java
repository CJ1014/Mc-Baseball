package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.client.ClientTeamCache;
import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.game.GameSettings;
import com.cj.mcbaseball.game.Position;
import com.cj.mcbaseball.game.TeamSide;
import com.cj.mcbaseball.network.GameActionPacket;
import com.cj.mcbaseball.team.TeamData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

public class StartGameScreen extends ControllerScreen {
    private int cacheVersion = -1;
    private String lastState = "";
    private static final Map<String, String> POS_NAMES = Map.of(
        "P",
        "Pitcher",
        "C",
        "Catcher",
        "1B",
        "First Base",
        "2B",
        "Second Base",
        "3B",
        "Third Base",
        "SS",
        "Shortstop",
        "LF",
        "Left Field",
        "CF",
        "Center Field",
        "RF",
        "Right Field"
    );

    public StartGameScreen(BlockPos pos, Screen parent) {
        super(Component.translatable("mcbaseball.gui.start.title"), pos, parent);
    }

    private int top() {
        return Math.max(4, this.height / 2 - 110);
    }

    protected void init() {
        FieldControllerBlockEntity be = this.controller();
        if (be != null) {
            GameSettings s = be.settings();
            this.cacheVersion = ClientTeamCache.version();
            this.lastState = this.stateKey(be);
            int cx = this.width / 2;
            int y = this.top() + 24;
            this.teamRow(cx, y, GameActionPacket.Action.SET_HOME_TEAM, this.effectiveHome(s));
            this.teamRow(cx, y + 24, GameActionPacket.Action.SET_AWAY_TEAM, this.effectiveAway(s));
            int ix = cx - 10;

            for (int i = 0; i < GameSettings.INNING_CHOICES.length; i++) {
                int n = GameSettings.INNING_CHOICES[i];
                Component label = n == s.innings ? Component.literal("[" + n + "]").withStyle(ChatFormatting.YELLOW) : Component.literal(String.valueOf(n));
                this.addRenderableWidget(
                    Button.builder(label, b -> this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.SET_INNINGS, n)))
                        .bounds(ix + i * 34, y + 52, 30, 20)
                        .build()
                );
            }

            this.addRenderableWidget(
                Button.builder(onOff(s.npcAutoFill), b -> this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.TOGGLE_AUTOFILL)))
                    .bounds(cx - 10, y + 76, 134, 20)
                    .build()
            );
            this.addRenderableWidget(
                Button.builder(s.difficulty.displayName(), b -> this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.CYCLE_DIFFICULTY)))
                    .bounds(cx - 10, y + 100, 134, 20)
                    .build()
            );
            int jy = y + 130;
            this.addRenderableWidget(
                Button.builder(
                        Component.translatable("mcbaseball.gui.start.join_home"),
                        b -> this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.JOIN, TeamSide.HOME.ordinal()))
                    )
                    .bounds(cx - 154, jy, 100, 20)
                    .build()
            );
            this.addRenderableWidget(
                Button.builder(
                        Component.translatable("mcbaseball.gui.start.join_away"),
                        b -> this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.JOIN, TeamSide.AWAY.ordinal()))
                    )
                    .bounds(cx - 50, jy, 100, 20)
                    .build()
            );
            this.addRenderableWidget(
                Button.builder(
                        Component.translatable("mcbaseball.gui.start.watch"), b -> this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.WATCH))
                    )
                    .bounds(cx + 54, jy, 100, 20)
                    .build()
            );
            FieldControllerBlockEntity pbe = this.controller();
            if (pbe != null && this.minecraft.player != null) {
                FieldControllerBlockEntity.Signup mine = pbe.signups().get(this.minecraft.player.getUUID());
                Button posBtn = Button.builder(positionLabel(mine), b -> {
                    if (mine != null) {
                        int next = mine.position() + 1;
                        this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.SET_POSITION, next > 8 ? -1 : next));
                    }
                }).bounds(cx - 154, jy + 22, 308, 20).build();
                posBtn.active = mine != null;
                this.addRenderableWidget(posBtn);
            }

            Button start = Button.builder(
                    Component.translatable("mcbaseball.gui.start.start").withStyle(new ChatFormatting[]{ChatFormatting.GREEN, ChatFormatting.BOLD}), b -> {
                        this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.START));
                        this.onClose();
                    }
                )
                .bounds(cx - 154, jy + 62, 200, 20)
                .build();
            start.active = be.layout().isReady() && !be.isGameActive();
            this.addRenderableWidget(start);
            this.back(cx + 50, jy + 40, 104);
        }
    }

    private void teamRow(int cx, int y, GameActionPacket.Action action, @Nullable TeamData current) {
        List<TeamData> teams = ClientTeamCache.teams();
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.cycle(action, current, -1)).bounds(cx - 10, y, 20, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.cycle(action, current, 1)).bounds(cx + 104, y, 20, 20).build());
        if (teams.isEmpty()) {
            this.send(GameActionPacket.of(this.pos, GameActionPacket.Action.REQUEST_TEAMS));
        }
    }

    private void cycle(GameActionPacket.Action action, @Nullable TeamData current, int dir) {
        List<TeamData> teams = ClientTeamCache.teams();
        if (!teams.isEmpty()) {
            int i = current == null ? 0 : ClientTeamCache.indexOf(current.id);
            TeamData next = teams.get(Math.floorMod(i + dir, teams.size()));
            this.send(GameActionPacket.of(this.pos, action, next.id));
        }
    }

    @Nullable
    private TeamData effectiveHome(GameSettings s) {
        TeamData t = ClientTeamCache.byId(s.homeTeam);
        return t != null ? t : (ClientTeamCache.teams().isEmpty() ? null : ClientTeamCache.teams().get(0));
    }

    @Nullable
    private TeamData effectiveAway(GameSettings s) {
        TeamData t = ClientTeamCache.byId(s.awayTeam);
        if (t != null) {
            return t;
        } else {
            TeamData h = this.effectiveHome(s);

            for (TeamData d : ClientTeamCache.teams()) {
                if (h == null || !d.id.equals(h.id)) {
                    return d;
                }
            }

            return null;
        }
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private String stateKey(FieldControllerBlockEntity be) {
        return be.settings().save().toString() + be.signups().toString() + be.spectators().toString() + be.isGameActive() + be.layout().isReady();
    }

    @Override
    public void tick() {
        super.tick();
        FieldControllerBlockEntity be = this.controller();
        if (be != null && (ClientTeamCache.version() != this.cacheVersion || !this.stateKey(be).equals(this.lastState))) {
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
            GameSettings s = be.settings();
            int cx = this.width / 2;
            int y = top + 24;
            int lx = cx - 154;
            this.label(g, "mcbaseball.gui.start.home", lx, y);
            this.label(g, "mcbaseball.gui.start.away", lx, y + 24);
            this.label(g, "mcbaseball.gui.start.innings", lx, y + 52);
            this.label(g, "mcbaseball.gui.start.autofill", lx, y + 76);
            this.label(g, "mcbaseball.gui.start.difficulty", lx, y + 100);
            this.teamName(g, this.effectiveHome(s), cx + 47, y);
            this.teamName(g, this.effectiveAway(s), cx + 47, y + 24);
            int home = 0;
            int away = 0;
            String mine = null;

            for (Entry<UUID, FieldControllerBlockEntity.Signup> e : be.signups().entrySet()) {
                if (e.getValue().side() == TeamSide.HOME) {
                    home++;
                } else {
                    away++;
                }

                if (this.minecraft.player != null && e.getKey().equals(this.minecraft.player.getUUID())) {
                    mine = e.getValue().side().name();
                }
            }

            MutableComponent who = Component.translatable("mcbaseball.gui.start.signed", new Object[]{home, away});
            if (mine != null) {
                who.append(Component.translatable("mcbaseball.gui.start.you_" + mine.toLowerCase()).withStyle(ChatFormatting.YELLOW));
            } else if (this.minecraft.player != null && be.isSpectator(this.minecraft.player.getUUID())) {
                who.append(Component.translatable("mcbaseball.gui.start.you_spectate").withStyle(ChatFormatting.AQUA));
            } else {
                who.append(Component.translatable("mcbaseball.gui.start.you_default").withStyle(ChatFormatting.GRAY));
            }

            g.drawCenteredString(this.font, who, cx, y + 178, 13421772);
            Component status = (Component)(!be.layout().isReady()
                ? be.statusLine()
                : (
                    be.isGameActive()
                        ? Component.translatable("mcbaseball.gui.field.in_progress").withStyle(ChatFormatting.GOLD)
                        : Component.translatable("mcbaseball.gui.start.ready_hint").withStyle(ChatFormatting.GRAY)
                ));
            g.drawCenteredString(this.font, status, cx, y + 216, 16777215);
            super.render(g, mx, my, pt);
        }
    }

    private static Component positionLabel(@Nullable FieldControllerBlockEntity.Signup mine) {
        if (mine == null) {
            return Component.translatable("mcbaseball.gui.start.position_join").withStyle(ChatFormatting.GRAY);
        } else {
            String name;
            if (mine.position() < 0) {
                name = Component.translatable("mcbaseball.gui.start.position_any").getString();
            } else {
                String abbr = Position.byId(mine.position()).abbr;
                name = POS_NAMES.getOrDefault(abbr, abbr) + " (" + abbr + ")";
            }

            return Component.translatable("mcbaseball.gui.start.position", new Object[]{name});
        }
    }

    private void label(GuiGraphics g, String key, int x, int y) {
        g.drawString(this.font, Component.translatable(key).withStyle(ChatFormatting.GOLD), x, y + 6, 16777215);
    }

    private void teamName(GuiGraphics g, @Nullable TeamData t, int cx, int y) {
        if (t == null) {
            g.drawCenteredString(this.font, "...", cx, y + 6, 11184810);
        } else {
            String text = t.name + " (" + t.abbreviation + ")";
            int w = this.font.width(text);
            g.fill(cx - w / 2 - 6, y + 5, cx - w / 2 - 2, y + 15, 0xFF000000 | t.primaryRgb());
            g.drawString(this.font, text, cx - w / 2, y + 6, 16777215);
        }
    }
}
