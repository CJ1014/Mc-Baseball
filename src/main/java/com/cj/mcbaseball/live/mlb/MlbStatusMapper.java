package com.cj.mcbaseball.live.mlb;

import com.cj.mcbaseball.live.model.LiveGameStatus;
import com.cj.mcbaseball.live.model.LiveGameStatus.State;
import com.google.gson.JsonObject;
import com.cj.mcbaseball.live.net.Json;
import javax.annotation.Nullable;

/**
 * Maps MLB Stats API status objects to {@link LiveGameStatus}.
 *
 * <p>Built from the full catalogue at {@code /api/v1/gameStatus}. Things that look wrong but are real:
 * postponed and cancelled games report {@code abstractGameState: "Final"}; "Warmup" (PW) is abstract
 * "Live"; "Scheduled: COVID-19" uses coded state T, which elsewhere means Suspended. So the abstract
 * state is checked first and the coded state / status code refine it.
 */
public final class MlbStatusMapper {

    private MlbStatusMapper() {
    }

    public static LiveGameStatus map(@Nullable JsonObject status) {
        String abs = Json.str(status, "abstractGameState");
        String coded = Json.str(status, "codedGameState");
        String code = Json.str(status, "statusCode");
        String detailed = Json.str(status, "detailedState");
        String reason = Json.str(status, "reason");
        return new LiveGameStatus(state(abs, coded, code), detailed, reason, code.isEmpty() ? coded : code);
    }

    static State state(String abs, String coded, String code) {
        char c = coded.isEmpty() ? (code.isEmpty() ? '?' : code.charAt(0)) : coded.charAt(0);
        if (code.isEmpty()) {
            code = coded;
        }
        switch (abs) {
            case "Preview":
                return preview(c, code);
            case "Live":
                return live(c, code);
            case "Final":
                return fin(c);
            default:
                // "Other" (Writing / Unknown) or a missing/new abstract state: infer from the coded letter.
                return switch (c) {
                    case 'S', 'P' -> c == 'P' && code.equals("PW") ? State.WARMUP : preview(c, code);
                    case 'I', 'M', 'N', 'T', 'U' -> live(c, code);
                    case 'F', 'O', 'D', 'C', 'Q', 'R' -> fin(c);
                    default -> State.UNKNOWN;
                };
        }
    }

    private static State preview(char c, String code) {
        if (c == 'P') {
            if (code.equals("PW")) {
                return State.WARMUP;
            }
            return code.length() > 1 ? State.DELAYED_START : State.PREGAME;
        }
        return State.SCHEDULED;
    }

    private static State live(char c, String code) {
        return switch (c) {
            case 'P' -> State.WARMUP;
            case 'I' -> {
                if (code.length() <= 1) {
                    yield State.LIVE;
                }
                yield code.equals("IH") ? State.REVIEW : State.DELAYED;
            }
            case 'M', 'N' -> State.REVIEW;
            case 'T', 'U' -> State.SUSPENDED;
            default -> State.LIVE;
        };
    }

    private static State fin(char c) {
        return switch (c) {
            case 'D' -> State.POSTPONED;
            case 'C' -> State.CANCELLED;
            case 'Q', 'R' -> State.FORFEIT;
            default -> State.FINAL;
        };
    }
}
