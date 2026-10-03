package com.cj.mcbaseball.game;

import com.cj.mcbaseball.config.BaseballConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class Coach {
    public static Component line(BaseballGame g, ServerPlayer p, PlayerRole role) {
        LineupSlot s = g.slotOf(p);
        if (s == null) {
            return Component.empty();
        } else {
            return (Component)(switch (g.phase) {
                case PREGAME -> g.isDefense(s)
                ? t("mcbaseball.coach.pregame_field", s.position.displayName(), g.team(s.side).data.name)
                : t("mcbaseball.coach.pregame_bat", g.team(s.side).data.name);
                case PITCHING -> pitching(g, s, role);
                case BALL_IN_PLAY -> inPlay(g, s, p);
                default -> Component.empty();
            });
        }
    }

    private static Component pitching(BaseballGame g, LineupSlot s, PlayerRole role) {
        boolean thrown = g.pitch != null;

        return (Component)(switch (role) {
            case BATTER -> thrown
            ? t("mcbaseball.coach.bat_swing")
            : (
                g.settings.simpleBatting
                    ? t("mcbaseball.coach.bat_ready_simple")
                    : t(g.settings.battingAssist ? "mcbaseball.coach.bat_ready_assist" : "mcbaseball.coach.bat_ready")
            );
            case PITCHER -> thrown
            ? Component.empty()
            : (g.phaseTicks < BaseballConfig.PITCH_READY_TICKS.get() ? t("mcbaseball.coach.pitch_wait") : t("mcbaseball.coach.pitch_go"));
            case CATCHER -> t("mcbaseball.coach.catcher");
            case FIELDER -> t("mcbaseball.coach.fielder_ready");
            case RUNNER -> {
                int b = baseOf(g, s);
                yield b > 0 ? t("mcbaseball.coach.runner_wait", baseName(b)) : Component.empty();
            }
            case ON_DECK -> {
                int n = battersUntil(g, s);
                yield n <= 0 ? Component.empty() : t("mcbaseball.coach.on_deck", n);
            }
            default -> Component.empty();
        });
    }

    private static Component inPlay(BaseballGame g, LineupSlot s, ServerPlayer p) {
        PlayTracker play = g.play;
        if (play == null) {
            return Component.empty();
        } else if (!g.isDefense(s)) {
            Runner r = play.of(s);
            if (r == null || !r.active()) {
                return Component.empty();
            } else if (r.mustTagUp) {
                return t("mcbaseball.coach.tag_up", baseName(r.startBase));
            } else if (r.isBatter() && r.base == 0) {
                return t("mcbaseball.coach.run_first");
            } else if (play.batted && play.inFlight && !play.caughtInAir && g.outs < 2) {
                return t("mcbaseball.coach.fly_wait", baseName(Math.max(1, r.base)));
            } else if (g.isSafe(r, p.position())) {
                return r.base >= 3 ? t("mcbaseball.coach.safe_home", baseName(r.base)) : t("mcbaseball.coach.safe", baseName(r.base), baseName(r.base + 1));
            } else {
                return t("mcbaseball.coach.run_to", baseName(Math.min(4, r.base + 1)));
            }
        } else if (g.holder == s) {
            return throwAdvice(g, play, p);
        } else if (g.ai.chaser() != s) {
            for (int b = 1; b <= 4; b++) {
                if (g.ai.coverer(b) == s) {
                    return t("mcbaseball.coach.cover", baseName(b));
                }
            }

            return t("mcbaseball.coach.backup");
        } else {
            return play.batted && play.inFlight ? t("mcbaseball.coach.catch_fly") : t("mcbaseball.coach.get_ball");
        }
    }

    private static Component throwAdvice(BaseballGame g, PlayTracker play, ServerPlayer p) {
        Runner best = null;
        int bestBase = -1;
        boolean force = false;

        for (Runner r : play.runners) {
            if (r.active()) {
                LivingEntity ra = g.actor(r.slot);
                if (ra != null) {
                    int tb;
                    boolean f;
                    if (r.mustTagUp) {
                        tb = r.startBase;
                        f = true;
                    } else if (play.isForced(r) && r.base < r.startBase + 1) {
                        tb = r.startBase + 1;
                        f = true;
                    } else {
                        if (g.isSafe(r, ra.position())) {
                            continue;
                        }

                        tb = Math.min(4, r.base + 1);
                        f = false;
                    }

                    if (tb > bestBase) {
                        best = r;
                        bestBase = tb;
                        force = f;
                    }
                }
            }
        }

        if (best == null) {
            return t("mcbaseball.coach.throw_in");
        } else {
            LivingEntity ra = g.actor(best.slot);
            if (!force && ra != null && FieldGeometry.flatDist(ra.position(), p.position()) < 6.0) {
                return t("mcbaseball.coach.tag_runner");
            } else {
                return FieldGeometry.flatDist(p.position(), g.geo.base(bestBase)) < 5.0
                    ? t(force ? "mcbaseball.coach.step_on" : "mcbaseball.coach.tag_at", baseName(bestBase))
                    : t(force ? "mcbaseball.coach.throw_force" : "mcbaseball.coach.throw_to", baseName(bestBase));
            }
        }
    }

    private static int baseOf(BaseballGame g, LineupSlot s) {
        for (int b = 1; b <= 3; b++) {
            if (g.onBase[b] == s) {
                return b;
            }
        }

        return 0;
    }

    private static int battersUntil(BaseballGame g, LineupSlot s) {
        GameTeam t = g.team(s.side);
        int bi = t.order.indexOf(g.batter);
        int si = t.order.indexOf(s);
        return bi >= 0 && si >= 0 ? Math.floorMod(si - bi, t.order.size()) : 0;
    }

    public static Component baseName(int b) {
        return Component.translatable("mcbaseball.base." + Math.min(4, Math.max(1, b)));
    }

    private static Component t(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.YELLOW);
    }

    private Coach() {
    }
}
