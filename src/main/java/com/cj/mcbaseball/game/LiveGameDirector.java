package com.cj.mcbaseball.game;

import com.cj.mcbaseball.pitching.PitchRecord;

/**
 * Lets something other than the game's own AI and rules decide what happens: Live Mode attaches one to
 * recreate a real game. With no director attached ({@link BaseballGame#director} == null) the game plays
 * exactly as before.
 *
 * <p>While a director is attached the game still animates and simulates (positions, windups, pitch flight,
 * swings, the catcher, ball physics, sounds) but never decides a call, a count, a batter or an inning itself.
 */
public interface LiveGameDirector {

    /** PITCHING phase, every tick: decide when and what the NPC pitcher throws (replaces the pitch AI). */
    void tickPitching(BaseballGame g);

    /** A pitch was just released: decide what the batter does (replaces the NPC swing AI). */
    void onPitchReleased(BaseballGame g, PitchRecord pr);

    /** The pitch is over (caught, passed, or hit the batter): apply the real call (replaces the count rules). */
    void onPitchResolved(BaseballGame g);
}
