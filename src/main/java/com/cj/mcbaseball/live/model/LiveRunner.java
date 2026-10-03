package com.cj.mcbaseball.live.model;

import java.util.List;

/**
 * One runner's movement in a play, as reported. Bases are "1B", "2B", "3B", "score" or "" (none / at bat).
 *
 * @param eventIndex index of the play event this movement belongs to (a steal belongs to its action,
 *                   the batter's own movement to the in-play pitch), -1 if unknown
 * @param credits    fielders involved, in order ("f_fielded_ball", "f_assist", "f_putout", "f_throwing_error"...)
 */
public record LiveRunner(
    LivePlayer runner,
    String startBase,
    String endBase,
    String outBase,
    boolean isOut,
    int outNumber,
    String eventType,
    String movementReason,
    boolean isScoringEvent,
    int eventIndex,
    List<Credit> credits
) {
    public record Credit(int playerId, String position, String credit) {
        public Credit {
            position = position == null ? "" : position;
            credit = credit == null ? "" : credit;
        }
    }

    public LiveRunner {
        runner = runner == null ? LivePlayer.NONE : runner;
        startBase = startBase == null ? "" : startBase;
        endBase = endBase == null ? "" : endBase;
        outBase = outBase == null ? "" : outBase;
        eventType = eventType == null ? "" : eventType;
        movementReason = movementReason == null ? "" : movementReason;
        credits = credits == null ? List.of() : List.copyOf(credits);
    }
}
