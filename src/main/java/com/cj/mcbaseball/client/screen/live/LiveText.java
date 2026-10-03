package com.cj.mcbaseball.client.screen.live;

import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameSummary;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display strings for live games, shared by the browser and detail screens. */
final class LiveText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a z", Locale.US);

    static final int RED = 0xFF5555;
    static final int GOLD = 0xFFAA00;
    static final int YELLOW = 0xFFFF55;
    static final int AQUA = 0x55FFFF;
    static final int GREEN = 0x55FF55;
    static final int GRAY = 0xAAAAAA;
    static final int DARK_GRAY = 0x777777;
    static final int WHITE = 0xFFFFFF;

    private LiveText() {
    }

    /** Scheduled first pitch in the viewer's own time zone, e.g. "7:10 PM EDT". */
    static String startTime(LiveGameSummary g) {
        if (g.startTimeTbd()) {
            return "TIME TBD";
        }
        if (g.startEpochMillis() <= 0L) {
            return "TIME UNKNOWN";
        }
        return TIME.format(Instant.ofEpochMilli(g.startEpochMillis()).atZone(ZoneId.systemDefault()));
    }

    /** "NYY 2 - BOS 1" */
    static String score(LiveGameSummary g) {
        return g.away().displayAbbr() + " " + g.awayScore() + " - " + g.home().displayAbbr() + " " + g.homeScore();
    }

    /** Second line of a browser row (without the score). */
    static String statusLine(LiveGameSummary g) {
        LiveGameStatus.State s = g.status().state();
        String inning = g.inningLabel();
        return switch (s.section()) {
            case LIVE -> {
                StringBuilder b = new StringBuilder(g.status().label());
                if (!inning.isEmpty() && s != LiveGameStatus.State.WARMUP) {
                    b.append(" - ").append(inning);
                }
                if (s == LiveGameStatus.State.LIVE && g.outs() >= 0 && !"Middle".equals(g.inningState()) && !"End".equals(g.inningState())) {
                    b.append(" - ").append(g.outs()).append(g.outs() == 1 ? " out" : " outs");
                }
                yield b.toString();
            }
            case UPCOMING -> startTime(g) + " - " + g.status().label();
            case FINAL -> {
                if (s == LiveGameStatus.State.FINAL && g.inning() > 0 && g.inning() != 9) {
                    yield g.status().label() + "/" + g.inning();
                }
                yield g.status().label();
            }
        };
    }

    static int statusColor(LiveGameSummary g) {
        LiveGameStatus.State s = g.status().state();
        return switch (s) {
            case LIVE, REVIEW, WARMUP -> RED;
            case DELAYED, SUSPENDED, DELAYED_START -> YELLOW;
            case SCHEDULED, PREGAME, UNKNOWN -> AQUA;
            case FINAL, FORFEIT -> GRAY;
            case POSTPONED, CANCELLED -> DARK_GRAY;
        };
    }

    static int sectionColor(LiveGameStatus.Section section) {
        return switch (section) {
            case LIVE -> RED;
            case UPCOMING -> AQUA;
            case FINAL -> GRAY;
        };
    }
}
