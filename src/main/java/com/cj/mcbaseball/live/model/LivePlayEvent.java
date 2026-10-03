package com.cj.mcbaseball.live.model;

import javax.annotation.Nullable;

/**
 * Something that happened inside an at-bat: a pitch, or an action (steal, substitution, mound visit,
 * pickoff throw...).
 *
 * @param index         position inside its at-bat; stable as the at-bat grows
 * @param playId        provider's id for pitches (stable across polls), "" for actions
 * @param pitch         pitch details for PITCH events, {@link LivePitch#NONE} otherwise
 * @param hit           batted-ball data when the pitch was put in play and the feed has it, else null
 * @param eventType     provider event type for actions ("stolen_base_2b", "pitching_substitution"...), "" for pitches
 * @param player        player an action is about (e.g. the new pitcher), {@link LivePlayer#NONE} if none
 * @param position      position abbreviation that goes with {@code player} (substitutions), "" if none
 * @param ballsAfter    count after this event, -1 if unknown (same for strikes / outs)
 * @param timeMillis    when it happened in the real game (epoch ms), 0 if unknown
 */
public record LivePlayEvent(
    int index,
    Kind kind,
    String playId,
    LivePitch pitch,
    @Nullable LiveHit hit,
    String eventType,
    String description,
    boolean isSubstitution,
    LivePlayer player,
    String position,
    int ballsAfter,
    int strikesAfter,
    int outsAfter,
    long timeMillis
) {
    public enum Kind {
        PITCH,
        ACTION,
        PICKOFF,
        STEPOFF,
        NO_PITCH,
        OTHER
    }

    public LivePlayEvent {
        kind = kind == null ? Kind.OTHER : kind;
        playId = playId == null ? "" : playId;
        pitch = pitch == null ? LivePitch.NONE : pitch;
        eventType = eventType == null ? "" : eventType;
        description = description == null ? "" : description;
        player = player == null ? LivePlayer.NONE : player;
        position = position == null ? "" : position;
    }

    /** Pure downtime that the recreation can skip through instantly (it is still recorded as processed). */
    public boolean isDowntime() {
        return switch (this.eventType) {
            case "batter_timeout", "mound_visit", "game_advisory", "pitcher_step_off" -> true;
            default -> this.kind == Kind.STEPOFF;
        };
    }
}
