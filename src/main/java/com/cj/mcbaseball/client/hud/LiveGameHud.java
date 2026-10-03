package com.cj.mcbaseball.client.hud;

import com.cj.mcbaseball.client.ClientGameState;
import com.cj.mcbaseball.client.ClientLiveWatch;
import com.cj.mcbaseball.live.model.LiveGameState;
import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import com.cj.mcbaseball.live.model.LivePitch;
import com.cj.mcbaseball.live.model.LivePlayer;
import com.cj.mcbaseball.live.model.LiveProviderStatus;
import com.cj.mcbaseball.live.model.LiveWatchSnapshot;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Scoreboard for the real game the nearby stadium is following. Same look as the mod's game HUD
 * (top-left), plus connection state, batter / pitcher, last pitch and last play.
 */
public final class LiveGameHud {

    private static final int W = 188;
    private static final int RED = 0xFF5555;
    private static final int YELLOW = 0xFFFF55;
    private static final int GOLD = 0xF4DE6D;
    private static final int AQUA = 0x55FFFF;
    private static final int GRAY = 0xAAAAAA;
    private static final int WHITE = 0xFFFFFF;
    private static final int AWAY_ROW = 0xFF2B3A5E;
    private static final int HOME_ROW = 0xFF5E2B2B;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a z", Locale.US);

    private LiveGameHud() {
    }

    public static void render(GuiGraphics g, int screenW, int screenH) {
        LiveWatchSnapshot snap = ClientLiveWatch.snapshot();
        Minecraft mc = Minecraft.getInstance();
        if (snap == null || mc.player == null || mc.options.hideGui || ClientGameState.active()) {
            return;
        }
        Font font = mc.font;
        LiveGameState s = snap.state();
        int x = 6;
        int y = 6;

        if (s == null) {
            // Nothing received yet: connecting, or the provider is down.
            boolean down = snap.status() == LiveProviderStatus.UNAVAILABLE;
            String head = down ? "LIVE DATA TEMPORARILY UNAVAILABLE" : "CONNECTING TO LIVE GAME...";
            int w = Math.max(W, font.width(head) + 10);
            panel(g, x, y, w, down ? 26 : 14);
            g.drawString(font, head, x + 5, y + 3, down ? RED : GRAY, false);
            if (down) {
                g.drawString(font, "Retrying in " + secs(ClientLiveWatch.retryInMillis()), x + 5, y + 14, GRAY, false);
            }
            return;
        }

        // Header: status + age.
        panel(g, x, y, W, 12);
        Header h = header(snap, s);
        g.drawString(font, Component.literal(h.text).withStyle(ChatFormatting.BOLD), x + 4, y + 2, h.color, false);
        // Long headers (REVIEW, RECONNECTING, RAIN DELAY...) get the short form of the age so nothing overlaps.
        int headW = font.width(Component.literal(h.text).withStyle(ChatFormatting.BOLD));
        String age = "Updated " + ago(ClientLiveWatch.dataAgeMillis()) + " ago";
        if (headW + font.width(age) + 12 > W) {
            age = ago(ClientLiveWatch.dataAgeMillis()) + " ago";
        }
        g.drawString(font, age, x + W - 4 - font.width(age), y + 2, GRAY, false);
        y += 14;

        y = scoreboard(g, font, s, x, y);

        // Body lines.
        LiveGameStatus.State st = s.status().state();
        int lineY = y + 2;
        if (st.section() == LiveGameStatus.Section.UPCOMING) {
            lineY = text(g, font, "First pitch " + startTime(s), x, lineY, AQUA);
        } else if (st.isOver()) {
            lineY = text(g, font, st == LiveGameStatus.State.FINAL ? "GAME COMPLETE" : s.status().label(), x, lineY, GRAY);
        }
        if (!st.isOver()) {
            String due = s.isBreak() || s.betweenBatters() || st.section() == LiveGameStatus.Section.UPCOMING ? "Due up: " : "AB ";
            if (s.batter().known()) {
                lineY = text(g, font, due + s.batter().displayWithNumber() + hand(s.batter().batSide()), x, lineY, WHITE);
            }
            if (s.pitcher().known()) {
                lineY = text(g, font, "P  " + s.pitcher().displayWithNumber() + hand(s.pitcher().pitchHand()), x, lineY, WHITE);
            }
            String runners = runners(s);
            if (!runners.isEmpty()) {
                lineY = text(g, font, runners, x, lineY, GOLD);
            }
            if (st.section() == LiveGameStatus.Section.LIVE && s.lastPitch().known() && !s.isBreak()) {
                LivePitch lp = s.lastPitch();
                String what = (lp.mph() > 0 ? String.format(Locale.ROOT, "%.1f mph ", lp.mph()) : "") + lp.typeName();
                // Call first (short), then speed + type on their own line so long pitch names fit.
                lineY = text(g, font, "Last pitch: " + (lp.call().isEmpty() ? "-" : lp.call()), x, lineY, GRAY);
                if (!what.isBlank()) {
                    lineY = text(g, font, "  " + what.trim(), x, lineY, GRAY);
                }
            }
        }
        if (!s.lastPlay().isEmpty() && st.section() != LiveGameStatus.Section.UPCOMING) {
            List<FormattedCharSequence> lines = font.split(Component.literal(s.lastPlay()), W - 8);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                lineY = textSeq(g, font, lines.get(i), x, lineY, GRAY);
            }
        }
        if (snap.status() == LiveProviderStatus.STALE) {
            lineY = text(g, font, "RECONNECTING TO LIVE GAME...", x, lineY, YELLOW);
            text(g, font, "  next try in " + secs(ClientLiveWatch.retryInMillis()), x, lineY, YELLOW);
        }
    }

    private record Header(String text, int color) {
    }

    private static Header header(LiveWatchSnapshot snap, LiveGameState s) {
        if (snap.status() == LiveProviderStatus.STALE) {
            return new Header("● RECONNECTING", YELLOW);
        }
        LiveGameStatus.State st = s.status().state();
        return switch (st) {
            case LIVE -> new Header("● LIVE", RED);
            case REVIEW -> new Header("● REVIEW", RED);
            case WARMUP -> new Header("WARMUP", AQUA);
            case DELAYED, SUSPENDED, DELAYED_START -> new Header(st == LiveGameStatus.State.DELAYED ? "GAME DELAYED" : s.status().label(), YELLOW);
            case SCHEDULED, PREGAME, UNKNOWN -> new Header("NOT STARTED", AQUA);
            case FINAL, FORFEIT -> new Header(s.status().label(), GRAY);
            case POSTPONED, CANCELLED -> new Header(s.status().label(), GRAY);
        };
    }

    private static int scoreboard(GuiGraphics g, Font font, LiveGameState s, int x, int y) {
        int h = 40;
        panel(g, x, y, W, h);
        g.fill(x, y, x + 64, y + 13, AWAY_ROW);
        g.fill(x, y + 13, x + 64, y + 26, HOME_ROW);
        g.drawString(font, Component.literal(s.away().displayAbbr()).withStyle(ChatFormatting.BOLD), x + 5, y + 3, WHITE, true);
        g.drawString(font, Component.literal(s.home().displayAbbr()).withStyle(ChatFormatting.BOLD), x + 5, y + 16, WHITE, true);
        if (s.showsScore()) {
            String ar = String.valueOf(s.awayScore());
            String hr = String.valueOf(s.homeScore());
            g.drawString(font, Component.literal(ar).withStyle(ChatFormatting.BOLD), x + 59 - font.width(ar), y + 3, WHITE, true);
            g.drawString(font, Component.literal(hr).withStyle(ChatFormatting.BOLD), x + 59 - font.width(hr), y + 16, WHITE, true);
        }
        LiveGameStatus.State st = s.status().state();
        boolean inGame = st.section() == LiveGameStatus.Section.LIVE && s.inning() > 0;
        if (inGame || (st.isOver() && s.inning() > 0)) {
            String inning = s.isBreak()
                ? s.inningLabel().toUpperCase(Locale.ROOT)
                : (s.isTop() ? "▲ " : "▼ ") + LiveGameSummary.ordinal(s.inning());
            g.drawString(font, inning, x + 70, y + 3, GOLD, false);
        }
        if (inGame && !s.isBreak()) {
            if (s.balls() >= 0 && s.strikes() >= 0) {
                g.drawString(font, s.balls() + "-" + s.strikes(), x + 70, y + 16, WHITE, false);
            }
            int outs = Math.max(0, Math.min(3, s.outs()));
            for (int i = 0; i < 3; i++) {
                g.fill(x + 70 + i * 7, y + 29, x + 75 + i * 7, y + 34, i < outs ? 0xFFE04A3A : 0xFF454545);
            }
            g.drawString(font, "OUT", x + 92, y + 28, 0x909090, false);
            int bases = s.basesMask();
            int cx = x + 164;
            int cy = y + 16;
            base(g, cx + 8, cy, (bases & 1) != 0);
            base(g, cx, cy - 8, (bases & 2) != 0);
            base(g, cx - 8, cy, (bases & 4) != 0);
            g.fill(cx - 2, cy + 7, cx + 2, cy + 10, 0xA0FFFFFF);
        }
        return y + h;
    }

    private static void base(GuiGraphics g, int cx, int cy, boolean on) {
        int color = on ? 0xFFF4DE6D : 0xFF505050;
        for (int i = 0; i < 4; i++) {
            g.fill(cx - i, cy - 3 + i, cx + i + 1, cy - 2 + i, color);
            g.fill(cx - i, cy + 3 - i, cx + i + 1, cy + 4 - i, color);
        }
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xE0000000);
        g.fill(x, y, x + w, y + h, 0xD0141418);
    }

    private static int text(GuiGraphics g, Font font, String s, int x, int y, int color) {
        String clipped = font.width(s) > W - 8 ? font.plainSubstrByWidth(s, W - 14) + ".." : s;
        g.fill(x - 1, y - 1, x + W + 1, y + 10, 0xB0141418);
        g.drawString(font, clipped, x + 4, y + 1, color, false);
        return y + 10;
    }

    private static int textSeq(GuiGraphics g, Font font, FormattedCharSequence s, int x, int y, int color) {
        g.fill(x - 1, y - 1, x + W + 1, y + 10, 0xB0141418);
        g.drawString(font, s, x + 4, y + 1, color, false);
        return y + 10;
    }

    private static String runners(LiveGameState s) {
        StringBuilder b = new StringBuilder();
        add(b, "1B", s.runnerOnFirst());
        add(b, "2B", s.runnerOnSecond());
        add(b, "3B", s.runnerOnThird());
        return b.length() == 0 ? "" : "On: " + b;
    }

    private static void add(StringBuilder b, String base, LivePlayer p) {
        if (p.known()) {
            b.append(b.length() == 0 ? "" : ", ").append(base).append(' ').append(p.display());
        }
    }

    /** " (L)" for left/right/switch, or nothing if unknown. */
    private static String hand(String code) {
        return code.isEmpty() ? "" : " (" + code + ")";
    }

    private static String startTime(LiveGameState s) {
        return s.startEpochMillis() <= 0 ? "TBD" : TIME.format(Instant.ofEpochMilli(s.startEpochMillis()).atZone(ZoneId.systemDefault()));
    }

    private static String ago(long millis) {
        if (millis < 0) {
            return "-";
        }
        long sec = millis / 1000L;
        return sec < 60 ? sec + "s" : sec < 3600 ? sec / 60 + "m" : sec / 3600 + "h";
    }

    private static String secs(long millis) {
        return Math.max(1L, (millis + 999L) / 1000L) + "s";
    }
}
