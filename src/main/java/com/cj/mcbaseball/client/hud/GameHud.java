package com.cj.mcbaseball.client.hud;

import com.cj.mcbaseball.client.ClientGameState;
import com.cj.mcbaseball.client.ClientSetup;
import com.cj.mcbaseball.game.GamePhase;
import com.cj.mcbaseball.game.PlayerRole;
import com.cj.mcbaseball.item.BaseballItem;
import com.cj.mcbaseball.network.GameHudPacket;
import com.cj.mcbaseball.pitching.PitchType;
import com.cj.mcbaseball.pitching.PitchingSystem;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;

public final class GameHud {
    public static void render(GuiGraphics g, int w, int h) {
        if (ClientGameState.active()) {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.options.hideGui && mc.player != null) {
                Font font = mc.font;
                GameHudPacket s = ClientGameState.hud();
                long now = System.currentTimeMillis();
                PlayerRole role = ClientGameState.role();
                scoreboard(g, font, s);
                int sy = 66;
                List<String> runners = new ArrayList<>();
                if ((s.bases() & 1) != 0) {
                    runners.add("1st");
                }

                if ((s.bases() & 2) != 0) {
                    runners.add("2nd");
                }

                if ((s.bases() & 4) != 0) {
                    runners.add("3rd");
                }

                MutableComponent sit = Component.translatable("mcbaseball.hud.outs", new Object[]{s.outs()})
                    .append("  ")
                    .append(
                        runners.isEmpty()
                            ? Component.translatable("mcbaseball.hud.bases_empty")
                            : Component.translatable("mcbaseball.hud.runners", new Object[]{String.join(", ", runners)})
                    );
                g.drawString(font, sit, 8, sy, 14737632, true);
                if (!s.batter().isEmpty()) {
                    g.drawString(font, Component.translatable("mcbaseball.hud.at_bat", new Object[]{s.batter()}), 8, sy + 10, 12105912, true);
                }

                if (!s.pitcher().isEmpty()) {
                    g.drawString(font, Component.translatable("mcbaseball.hud.pitching", new Object[]{s.pitcher()}), 8, sy + 20, 12105912, true);
                }

                int fy = sy + 34;

                for (ClientGameState.Msg m : ClientGameState.feed()) {
                    long age = now - m.at();
                    if (age <= 6000L) {
                        g.drawString(font, m.text(), 8, fy, withAlpha(14540253, fade(age, 6000L)), true);
                        fy += 10;
                    }
                }

                long since = now - ClientGameState.roleChangedAt();
                boolean banner = since < 4000L && role != PlayerRole.NONE && role != PlayerRole.ON_DECK;
                if (banner) {
                    float a = fade(since, 4000L);
                    Component title = Component.translatable("mcbaseball.hud.role." + role.name().toLowerCase()).withStyle(ChatFormatting.BOLD);
                    int bw = Math.max(font.width(title) * 2 + 40, 220);
                    int by = h / 2 - 70;
                    g.fill(w / 2 - bw / 2, by - 6, w / 2 + bw / 2, by + 56, withAlpha(1052692, a * 0.8F));
                    g.fill(w / 2 - bw / 2, by - 6, w / 2 + bw / 2, by - 4, withAlpha(16048205, a));
                    g.pose().pushPose();
                    g.pose().translate((float)w / 2.0F, (float)by, 0.0F);
                    g.pose().scale(2.0F, 2.0F, 1.0F);
                    g.drawCenteredString(font, title, 0, 0, withAlpha(16777215, a));
                    g.pose().popPose();
                    int ly = by + 24;

                    for (Component c : controls(role, s)) {
                        g.drawCenteredString(font, c, w / 2, ly, withAlpha(14540253, a));
                        ly += 10;
                    }
                }

                ClientGameState.Msg call = ClientGameState.call();
                int callY = h / 5;
                if (call != null && now - call.at() < 2200L && !banner) {
                    float a = fade(now - call.at(), 2200L);
                    g.pose().pushPose();
                    g.pose().translate((float)w / 2.0F, (float)callY, 0.0F);
                    g.pose().scale(2.4F, 2.4F, 1.0F);
                    g.drawCenteredString(font, call.text(), 0, 0, withAlpha(16777215, a));
                    g.pose().popPose();
                }

                if (!banner && s.coach() != null && !s.coach().getString().isEmpty()) {
                    Component c = s.coach();
                    int cw = font.width(c) + 12;
                    int cy = callY + 28;
                    g.fill(w / 2 - cw / 2, cy - 3, w / 2 + cw / 2, cy + 11, -1877995500);
                    g.drawCenteredString(font, c, w / 2, cy, 16048205);
                }

                ClientGameState.Msg ps = ClientGameState.pitchSpeed();
                if (ps != null && now - ps.at() < 3500L) {
                    int pw = font.width(ps.text());
                    g.fill(w - pw - 12, 6, w - 4, 20, -1609560044);
                    g.drawString(font, ps.text(), w - pw - 8, 9, withAlpha(16777215, fade(now - ps.at(), 3500L)), false);
                }

                ClientGameState.Msg tm = ClientGameState.timing();
                if (tm != null && now - tm.at() < 1500L) {
                    g.drawCenteredString(font, tm.text(), w / 2, h / 2 + 12, withAlpha(15263976, fade(now - tm.at(), 1500L) * 0.9F));
                }

                ClientGameState.Msg ev = ClientGameState.exitVelo();
                if (ev != null && now - ev.at() < 2500L) {
                    g.drawCenteredString(font, ev.text(), w / 2, h / 2 + 23, withAlpha(16765562, fade(now - ev.at(), 2500L)));
                }

                LocalPlayer p = mc.player;
                if (role == PlayerRole.PITCHER && s.phase() == GamePhase.PITCHING.ordinal()) {
                    Component type = PitchType.byId(s.pitchType()).displayName();
                    if (p.isUsingItem() && p.getUseItem().getItem() instanceof BaseballItem) {
                        drawMeter(g, font, w / 2 - 70, h / 2 + 34, 140, PitchingSystem.meterValue(p.getTicksUsingItem()));
                    }

                    Component label = Component.translatable("mcbaseball.hud.pitch", new Object[]{type});
                    g.drawCenteredString(font, label, w / 2, h / 2 + 52, 13619151);
                }

                if (ClientGameState.showHelp && role != PlayerRole.NONE) {
                    List<Component> lines = controls(role, s);
                    int cw = 0;

                    for (Component c : lines) {
                        cw = Math.max(cw, font.width(c));
                    }

                    Component head = Component.translatable("mcbaseball.hud.role." + role.name().toLowerCase()).withStyle(ChatFormatting.GOLD);
                    cw = Math.max(cw, font.width(head)) + 10;
                    int cx = w - cw - 4;
                    int cy = h / 2 - 20;
                    g.fill(cx, cy, w - 4, cy + 14 + lines.size() * 10 + 12, -2146430956);
                    g.drawString(font, head, cx + 5, cy + 4, 16777215, false);
                    int ly = cy + 16;

                    for (Component c : lines) {
                        g.drawString(font, c, cx + 5, ly, 14211288, false);
                        ly += 10;
                    }

                    g.drawString(
                        font, Component.translatable("mcbaseball.hud.help_toggle", new Object[]{ClientSetup.HELP.getTranslatedKeyMessage()}), cx + 5, ly + 2, 8421504, false
                    );
                }

                ClientGameState.Msg hint = ClientGameState.hint();
                if (hint != null && now - hint.at() < 3000L) {
                    g.drawCenteredString(font, hint.text(), w / 2, h - 62, 16777215);
                }
            }
        }
    }

    public static List<Component> controls(PlayerRole role, GameHudPacket s) {
        List<Component> l = new ArrayList<>();
        String base = "mcbaseball.hud.ctl." + role.name().toLowerCase() + ".";

        int n = switch (role) {
            case BATTER -> s.assist() ? 4 : 3;
            case PITCHER -> 4;
            case CATCHER, FIELDER -> 4;
            case RUNNER -> 3;
            case ON_DECK -> 1;
            default -> 0;
        };

        for (int i = 1; i <= n; i++) {
            if (role == PlayerRole.PITCHER && i == 1) {
                l.add(Component.translatable(base + i, new Object[]{ClientSetup.PITCH_MENU.getTranslatedKeyMessage()}));
            } else {
                l.add(Component.translatable(base + i));
            }
        }

        return l;
    }

    private static void scoreboard(GuiGraphics g, Font font, GameHudPacket s) {
        int x = 6;
        int y = 6;
        int bw = 124;
        int bh = 54;
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, -536870912);
        g.fill(x, y, x + bw, y + bh, -803990504);
        g.fill(x, y, x + 60, y + 13, 0xFF000000 | darken(s.awayColor()));
        g.fill(x, y + 13, x + 60, y + 26, 0xFF000000 | darken(s.homeColor()));
        g.drawString(font, Component.literal(s.awayAbbr()).withStyle(ChatFormatting.BOLD), x + 5, y + 3, 16777215, true);
        g.drawString(font, Component.literal(s.homeAbbr()).withStyle(ChatFormatting.BOLD), x + 5, y + 16, 16777215, true);
        String ar = String.valueOf(s.awayRuns());
        String hr = String.valueOf(s.homeRuns());
        g.drawString(font, Component.literal(ar).withStyle(ChatFormatting.BOLD), x + 55 - font.width(ar), y + 3, 16777215, true);
        g.drawString(font, Component.literal(hr).withStyle(ChatFormatting.BOLD), x + 55 - font.width(hr), y + 16, 16777215, true);
        String inning = (s.top() ? "▲ " : "▼ ") + ordinal(s.inning());
        g.drawString(font, inning, x + 66, y + 3, 16048205, false);
        g.drawString(font, s.balls() + "-" + s.strikes(), x + 66, y + 16, 16777215, false);

        for (int i = 0; i < 3; i++) {
            g.fill(x + 66 + i * 7, y + 29, x + 71 + i * 7, y + 34, i < s.outs() ? -2079430 : -12237492);
        }

        g.drawString(font, "OUT", x + 88, y + 28, 9474192, false);
        dots(g, font, "B", x + 5, y + 31, s.balls(), 3, -8465592);
        dots(g, font, "S", x + 5, y + 42, s.strikes(), 2, -729011);
        int cx = x + 108;
        int cy = y + 16;
        base(g, cx + 8, cy, (s.bases() & 1) != 0);
        base(g, cx, cy - 8, (s.bases() & 2) != 0);
        base(g, cx - 8, cy, (s.bases() & 4) != 0);
        g.fill(cx - 2, cy + 7, cx + 2, cy + 10, -1593835521);
    }

    private static String ordinal(int n) {
        int m = n % 100;
        String var10000;
        if (m >= 11 && m <= 13) {
            var10000 = "th";
        } else {
            switch (n % 10) {
                case 1:
                    var10000 = "st";
                    break;
                case 2:
                    var10000 = "nd";
                    break;
                case 3:
                    var10000 = "rd";
                    break;
                default:
                    var10000 = "th";
            }
        }

        String suf = var10000;
        return n + suf;
    }

    private static int darken(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int gg = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        return (int)((double)r * 0.7) << 16 | (int)((double)gg * 0.7) << 8 | (int)((double)b * 0.7);
    }

    private static void drawMeter(GuiGraphics g, Font font, int x, int y, int width, double m) {
        g.fill(x - 2, y - 2, x + width + 2, y + 10, -16777216);
        g.fill(x, y, x + width, y + 8, -12961216);
        int s0 = x + (int)(0.76 * (double)width);
        int s1 = x + (int)(Math.min(1.0, 0.96) * (double)width);
        g.fill(s0, y, s1, y + 8, -13722814);
        int fillTo = x + (int)(m * (double)width);
        g.fill(x, y + 3, fillTo, y + 5, -729011);
        g.fill(fillTo - 1, y - 3, fillTo + 1, y + 11, -1);
        g.drawCenteredString(font, Component.translatable("mcbaseball.hud.release_green"), (s0 + s1) / 2, y - 12, 8311624);
    }

    private static void dots(GuiGraphics g, Font font, String label, int x, int y, int count, int max, int on) {
        g.drawString(font, label, x, y - 1, 9474192, false);

        for (int i = 0; i <= max; i++) {
            int dx = x + 9 + i * 7;
            g.fill(dx, y, dx + 5, y + 5, i < count ? on : -12237492);
        }
    }

    private static void base(GuiGraphics g, int cx, int cy, boolean occupied) {
        g.pose().pushPose();
        g.pose().translate((float)cx, (float)cy, 0.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45.0F));
        g.fill(-4, -4, 4, 4, occupied ? -729011 : -12237492);
        g.pose().popPose();
    }

    private static float fade(long age, long life) {
        float t = (float)age / (float)life;
        return t < 0.75F ? 1.0F : Mth.clamp((1.0F - t) / 0.25F, 0.0F, 1.0F);
    }

    private static int withAlpha(int rgb, float a) {
        int al = Mth.clamp((int)(a * 255.0F), 4, 255);
        return al << 24 | rgb & 16777215;
    }

    private GameHud() {
    }
}
