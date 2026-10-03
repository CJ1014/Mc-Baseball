package com.cj.mcbaseball.game;

import com.cj.mcbaseball.MCBaseball;
import com.cj.mcbaseball.config.BaseballConfig;
import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.field.FieldLayout;
import com.cj.mcbaseball.live.LiveBaseballManager;
import com.cj.mcbaseball.team.TeamData;
import com.cj.mcbaseball.team.TeamRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class GameManager {
    private static final Map<UUID, BaseballGame> GAMES = new LinkedHashMap<>();

    @Nullable
    public static BaseballGame get(@Nullable UUID id) {
        return id == null ? null : GAMES.get(id);
    }

    public static Collection<BaseballGame> all() {
        return Collections.unmodifiableCollection(GAMES.values());
    }

    @Nullable
    public static BaseballGame at(ServerLevel level, BlockPos controller) {
        for (BaseballGame g : GAMES.values()) {
            if (g.level == level && g.controllerPos.equals(controller)) {
                return g;
            }
        }

        return null;
    }

    @Nullable
    public static BaseballGame forPlayer(UUID player) {
        for (BaseballGame g : GAMES.values()) {
            if (g.isParticipant(player)) {
                return g;
            }
        }

        return null;
    }

    @Nullable
    public static BlockPos infieldFluid(ServerLevel level, FieldGeometry geo) {
        AABB box = new AABB(geo.home, geo.home);

        for (int b = 1; b <= 3; b++) {
            box = box.minmax(new AABB(geo.base(b), geo.base(b)));
        }

        box = box.inflate(3.0, 0.0, 3.0);
        int y0 = Mth.floor(geo.groundY);

        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                for (int y = y0; y <= y0 + 3; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getFluidState(pos).isEmpty()) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    public static GameManager.StartResult tryStart(ServerLevel level, FieldControllerBlockEntity be, @Nullable ServerPlayer starter) {
        if (at(level, be.getBlockPos()) != null) {
            return fail("mcbaseball.start.already_running");
        } else if (LiveBaseballManager.isWatching(level, be.getBlockPos())) {
            return fail("mcbaseball.start.live_watching");
        } else {
            FieldLayout layout = be.layout();
            if (!layout.isReady()) {
                return fail("mcbaseball.start.field_not_ready");
            } else {
                TeamRegistry reg = TeamRegistry.get(level.getServer());
                GameSettings st = be.settings();
                List<TeamData> teams = reg.list();
                TeamData home = reg.byId(st.homeTeam);
                if (home == null) {
                    home = teams.get(0);
                }

                TeamData away = reg.byId(st.awayTeam);
                if (away != null && away.id.equals(home.id)) {
                    return fail("mcbaseball.start.same_team");
                } else {
                    if (away == null) {
                        UUID hid = home.id;
                        away = teams.stream().filter(tx -> !tx.id.equals(hid)).findFirst().orElse(null);
                    }

                    if (away == null) {
                        return fail("mcbaseball.start.same_team");
                    } else {
                        FieldGeometry geo = FieldGeometry.of(level, layout);
                        BlockPos wet = infieldFluid(level, geo);
                        if (wet != null) {
                            return new GameManager.StartResult(
                                false,
                                Component.translatable("mcbaseball.start.water", new Object[]{wet.getX(), wet.getY(), wet.getZ()})
                                    .withStyle(ChatFormatting.RED)
                            );
                        } else {
                            BaseballGame g = new BaseballGame(level, be.getBlockPos(), geo, st, home, away);
                            Map<UUID, FieldControllerBlockEntity.Signup> signups = new LinkedHashMap<>(be.signups());
                            if (starter != null
                                && !signups.containsKey(starter.getUUID())
                                && forPlayer(starter.getUUID()) == null
                                && !be.isSpectator(starter.getUUID())) {
                                signups.put(starter.getUUID(), new FieldControllerBlockEntity.Signup(TeamSide.HOME, -1, starter.getGameProfile().getName()));
                            }

                            double r = (double)((Integer)BaseballConfig.BROADCAST_RADIUS.get()).intValue();

                            for (Entry<UUID, FieldControllerBlockEntity.Signup> e : signups.entrySet()) {
                                ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
                                if (p != null && p.level() == level && !(p.position().distanceToSqr(geo.home) > r * r) && forPlayer(p.getUUID()) == null) {
                                    assignHuman(g.team(e.getValue().side()), p, e.getValue().position());
                                }
                            }

                            for (GameTeam t : new GameTeam[]{g.home, g.away}) {
                                for (LineupSlot s : t.order) {
                                    s.enabled = s.humanId != null || st.npcAutoFill;
                                }

                                if (!t.hasAnyPlayer()) {
                                    return fail("mcbaseball.start.empty_team");
                                }
                            }

                            GAMES.put(g.id, g);
                            be.setGameActive(true);
                            g.begin();

                            for (UUID sid : be.spectators()) {
                                ServerPlayer sp = level.getServer().getPlayerList().getPlayer(sid);
                                if (sp != null) {
                                    sp.displayClientMessage(Component.translatable("mcbaseball.start.spectating").withStyle(ChatFormatting.AQUA), false);
                                }
                            }

                            return new GameManager.StartResult(true, Component.translatable("mcbaseball.start.ok").withStyle(ChatFormatting.GREEN));
                        }
                    }
                }
            }
        }
    }

    private static void assignHuman(GameTeam t, ServerPlayer p, int pref) {
        LineupSlot slot = null;
        if (pref >= 0) {
            LineupSlot s = t.byPosition.get(Position.byId(pref));
            if (s != null && s.humanId == null) {
                slot = s;
            }
        }

        if (slot == null) {
            for (Position pos : Position.HUMAN_PRIORITY) {
                LineupSlot s = t.byPosition.get(pos);
                if (s != null && s.humanId == null) {
                    slot = s;
                    break;
                }
            }
        }

        if (slot != null) {
            slot.humanId = p.getUUID();
            slot.humanName = p.getGameProfile().getName();
        }
    }

    private static GameManager.StartResult fail(String key) {
        return new GameManager.StartResult(false, Component.translatable(key).withStyle(ChatFormatting.RED));
    }

    public static void tick(MinecraftServer server) {
        if (!GAMES.isEmpty()) {
            for (BaseballGame g : new ArrayList<>(GAMES.values())) {
                if (g.level.isLoaded(g.controllerPos) && g.level.getBlockEntity(g.controllerPos) instanceof FieldControllerBlockEntity) {
                    try {
                        g.tick();
                    } catch (Exception var4) {
                        MCBaseball.LOGGER.error("Baseball game {} crashed and was ended", g.id, var4);
                        remove(g);
                    }
                } else {
                    remove(g);
                }
            }
        }
    }

    public static void remove(BaseballGame g) {
        if (GAMES.remove(g.id) != null) {
            g.cleanup();
            if (g.level.isLoaded(g.controllerPos) && g.level.getBlockEntity(g.controllerPos) instanceof FieldControllerBlockEntity be) {
                be.setGameActive(false);
            }
        }
    }

    public static void endAll() {
        for (BaseballGame g : new ArrayList<>(GAMES.values())) {
            remove(g);
        }
    }

    public static void onLogout(UUID player) {
        for (BaseballGame g : GAMES.values()) {
            g.onHumanLogout(player);
        }
    }

    public static void onLogin(UUID player) {
        for (BaseballGame g : GAMES.values()) {
            g.onHumanLogin(player);
        }
    }

    private GameManager() {
    }

    public static record StartResult(boolean ok, Component message) {
    }
}
