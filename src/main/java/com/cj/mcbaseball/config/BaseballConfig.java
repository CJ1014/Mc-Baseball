package com.cj.mcbaseball.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

public final class BaseballConfig {
    public static final ForgeConfigSpec SPEC;
    public static final DoubleValue GRAVITY;
    public static final DoubleValue DRAG_COEFFICIENT;
    public static final DoubleValue MAGNUS_COEFFICIENT;
    public static final DoubleValue SPIN_DECAY;
    public static final DoubleValue BOUNCE_SCALE;
    public static final DoubleValue FRICTION_SCALE;
    public static final DoubleValue ROLLING_SCALE;
    public static final BooleanValue ALTITUDE_AFFECTS_CARRY;
    public static final DoubleValue SETTLE_VERTICAL_SPEED;
    public static final DoubleValue REST_SPEED;
    public static final DoubleValue WATER_DRAG;
    public static final IntValue IDLE_DESPAWN_TICKS;
    public static final DoubleValue SPEED_DISPLAY_SCALE;
    public static final DoubleValue THROW_MIN_SPEED;
    public static final DoubleValue THROW_MAX_SPEED;
    public static final IntValue THROW_FULL_CHARGE_TICKS;
    public static final DoubleValue BAREHAND_CATCH_RADIUS;
    public static final DoubleValue BAREHAND_MAX_CATCH_SPEED;
    public static final DoubleValue PICKUP_MAX_SPEED;
    public static final IntValue THROWER_IMMUNITY_TICKS;
    public static final IntValue FIELD_MAX_RADIUS;
    public static final IntValue AUTO_DETECT_RADIUS;
    public static final IntValue AUTO_DETECT_VERTICAL;
    public static final IntValue MARKING_TIMEOUT_SECONDS;
    public static final IntValue PREGAME_TICKS;
    public static final IntValue PITCH_READY_TICKS;
    public static final IntValue PLAY_OVER_TICKS;
    public static final IntValue SIDE_CHANGE_TICKS;
    public static final IntValue PLAY_TIMEOUT_TICKS;
    public static final IntValue GAME_OVER_CLEANUP_TICKS;
    public static final IntValue BROADCAST_RADIUS;
    public static final DoubleValue PITCH_SPEED_SCALE;
    public static final DoubleValue PITCH_BREAK_SCALE;
    public static final IntValue PITCH_METER_TICKS;
    public static final DoubleValue PITCH_MAX_MISS;
    public static final DoubleValue EXIT_VELOCITY_SCALE;
    public static final DoubleValue SWING_TIMING_WINDOW;
    public static final DoubleValue SWING_HEIGHT_WINDOW;
    public static final IntValue MAX_LAG_COMPENSATION_TICKS;
    public static final DoubleValue NPC_THROW_MIN_MPH;
    public static final DoubleValue NPC_THROW_MAX_MPH;
    public static final DoubleValue TAG_REACH;
    public static final DoubleValue SLIDE_TAG_REDUCTION;
    public static final BooleanValue LIVE_ENABLED;
    public static final ConfigValue<String> LIVE_API_BASE_URL;
    public static final IntValue LIVE_HTTP_TIMEOUT_SECONDS;
    public static final IntValue LIVE_HTTP_RETRIES;
    public static final IntValue LIVE_SCHEDULE_REFRESH_LIVE_SECONDS;
    public static final IntValue LIVE_SCHEDULE_REFRESH_IDLE_SECONDS;
    public static final BooleanValue LIVE_DEBUG_RECORDING;
    public static final IntValue LIVE_DEBUG_RECORDING_MAX_FILES;

    private BaseballConfig() {
    }

    static {
        Builder b = new Builder();
        b.comment("Baseball flight and ground physics").push("physics");
        GRAVITY = b.comment("Downward acceleration (blocks/tick^2). Real Earth gravity is ~0.0245.").defineInRange("gravity", 0.03, 0.0, 0.2);
        DRAG_COEFFICIENT = b.comment("Quadratic air drag k: each tick v -= k*|v|*v. A real baseball is ~0.006.")
            .defineInRange("dragCoefficient", 0.006, 0.0, 0.1);
        MAGNUS_COEFFICIENT = b.comment("Magnus force strength: acceleration = k * (spin x velocity). Drives pitch movement, fly-ball carry, hooks and slices.")
            .defineInRange("magnusCoefficient", 5.4E-4, 0.0, 0.01);
        SPIN_DECAY = b.comment("Fraction of spin lost per tick in the air.").defineInRange("spinDecay", 0.0015, 0.0, 0.1);
        BOUNCE_SCALE = b.comment("Multiplies every surface's bounciness (grass, clay, stone, ice, padding...).").defineInRange("bounceScale", 1.0, 0.0, 2.0);
        FRICTION_SCALE = b.comment("Multiplies every surface's grip (how much bounces change speed and spin).").defineInRange("frictionScale", 1.0, 0.0, 3.0);
        ROLLING_SCALE = b.comment("Multiplies rolling resistance (lower = grounders roll farther).").defineInRange("rollingScale", 1.0, 0.0, 5.0);
        ALTITUDE_AFFECTS_CARRY = b.comment("Thinner air at high Y: balls carry farther in mountain stadiums.").define("altitudeAffectsCarry", true);
        SETTLE_VERTICAL_SPEED = b.comment("Bounces weaker than this (blocks/tick) turn into rolling.").defineInRange("settleVerticalSpeed", 0.06, 0.0, 1.0);
        REST_SPEED = b.comment("A rolling ball slower than this comes to rest.").defineInRange("restSpeed", 0.008, 0.0, 0.5);
        WATER_DRAG = b.comment("Extra speed fraction lost per tick in water.").defineInRange("waterDrag", 0.25, 0.0, 1.0);
        IDLE_DESPAWN_TICKS = b.comment("A loose resting ball that is not part of a game drops as an item after this many ticks. 0 = never.")
            .defineInRange("idleDespawnTicks", 6000, 0, Integer.MAX_VALUE);
        b.pop();
        b.comment("How speeds are shown to players").push("display");
        SPEED_DISPLAY_SCALE = b.comment(
                "Displayed MPH = true block speed in MPH * this. Keeps the game playable on Minecraft-sized fields while the numbers still read like baseball."
            )
            .defineInRange("speedDisplayScale", 1.5, 0.1, 10.0);
        b.pop();
        b.comment("Fielder throwing (hold right-click with a baseball, release to throw)").push("throwing");
        THROW_MIN_SPEED = b.comment("Speed of a barely-charged throw (blocks/tick).").defineInRange("minSpeed", 0.45, 0.05, 5.0);
        THROW_MAX_SPEED = b.comment("Speed of a fully-charged throw (blocks/tick). 1.3 displays as ~87 MPH at default scale.")
            .defineInRange("maxSpeed", 1.3, 0.05, 5.0);
        THROW_FULL_CHARGE_TICKS = b.comment("Ticks of holding needed for a full-strength throw.").defineInRange("fullChargeTicks", 20, 1, 200);
        b.pop();
        b.comment("Catching and picking up").push("catching");
        BAREHAND_CATCH_RADIUS = b.comment("How far outside a player's hitbox a ball can still be caught with no glove.")
            .defineInRange("barehandCatchRadius", 0.35, 0.0, 3.0);
        BAREHAND_MAX_CATCH_SPEED = b.comment("Balls faster than this (blocks/tick) can't be caught barehanded; they deflect off you.")
            .defineInRange("barehandMaxCatchSpeed", 0.55, 0.0, 10.0);
        PICKUP_MAX_SPEED = b.comment("Balls slower than this can be scooped up just by touching them.").defineInRange("pickupMaxSpeed", 0.12, 0.0, 10.0);
        THROWER_IMMUNITY_TICKS = b.comment("Ticks after a throw during which the thrower can't touch their own ball.")
            .defineInRange("throwerImmunityTicks", 8, 0, 100);
        b.pop();
        b.comment("Field setup").push("field");
        FIELD_MAX_RADIUS = b.comment("Field markers must be within this many blocks of the Field Controller.").defineInRange("maxRadius", 192, 16, 1024);
        AUTO_DETECT_RADIUS = b.comment("Horizontal radius scanned by the Auto-Detect button.").defineInRange("autoDetectRadius", 64, 8, 160);
        AUTO_DETECT_VERTICAL = b.comment("Vertical range (up and down) scanned by Auto-Detect.").defineInRange("autoDetectVertical", 12, 1, 64);
        MARKING_TIMEOUT_SECONDS = b.comment("Marking mode cancels itself after this many seconds without a click.")
            .defineInRange("markingTimeoutSeconds", 90, 10, 3600);
        b.pop();
        b.comment("Game pacing (ticks; 20 ticks = 1 second)").push("game");
        PREGAME_TICKS = b.defineInRange("pregameTicks", 100, 0, 2400);
        PITCH_READY_TICKS = b.comment("Pause before each pitch").defineInRange("pitchReadyTicks", 30, 5, 400);
        PLAY_OVER_TICKS = b.comment("Pause after each play").defineInRange("playOverTicks", 40, 5, 400);
        SIDE_CHANGE_TICKS = b.defineInRange("sideChangeTicks", 80, 10, 1200);
        PLAY_TIMEOUT_TICKS = b.comment("A play that runs this long is ended and runners return to their bases")
            .defineInRange("playTimeoutTicks", 1200, 200, 12000);
        GAME_OVER_CLEANUP_TICKS = b.comment("NPCs leave this long after the final out").defineInRange("gameOverCleanupTicks", 200, 20, 6000);
        BROADCAST_RADIUS = b.comment("Players within this many blocks of home plate see the game HUD").defineInRange("broadcastRadius", 160, 32, 512);
        b.pop();
        b.comment("Pitching").push("pitching");
        PITCH_SPEED_SCALE = b.comment("Multiplies every pitch's speed. Lower = easier to hit.").defineInRange("speedScale", 1.0, 0.3, 2.0);
        PITCH_BREAK_SCALE = b.comment("Multiplies every pitch's movement.").defineInRange("breakScale", 1.0, 0.0, 3.0);
        PITCH_METER_TICKS = b.comment("Ticks for the pitch meter to fill once (it then drains).").defineInRange("meterTicks", 22, 6, 100);
        PITCH_MAX_MISS = b.comment("How far (blocks) a pitch with the worst meter timing misses its target.").defineInRange("maxMiss", 1.1, 0.0, 4.0);
        b.pop();
        b.comment("Batting").push("batting");
        EXIT_VELOCITY_SCALE = b.comment("Multiplies batted ball speed. Raise for bigger fields.").defineInRange("exitVelocityScale", 1.05, 0.3, 3.0);
        SWING_TIMING_WINDOW = b.comment("Ticks early/late a swing can be and still make contact.").defineInRange("timingWindow", 2.2, 0.5, 6.0);
        SWING_HEIGHT_WINDOW = b.comment("Blocks above/below the ball a swing can be and still make contact.").defineInRange("heightWindow", 0.5, 0.1, 2.0);
        MAX_LAG_COMPENSATION_TICKS = b.comment("Swings are judged up to this many ticks in the past to cancel network lag.")
            .defineInRange("maxLagCompensationTicks", 5, 0, 20);
        b.pop();
        b.comment("Fielding and base running").push("fielding");
        NPC_THROW_MIN_MPH = b.defineInRange("npcThrowMinMph", 66.0, 20.0, 120.0);
        NPC_THROW_MAX_MPH = b.defineInRange("npcThrowMaxMph", 90.0, 20.0, 120.0);
        TAG_REACH = b.comment("How close a fielder holding the ball must be to tag a runner.").defineInRange("tagReach", 1.1, 0.3, 4.0);
        SLIDE_TAG_REDUCTION = b.comment("Sliding shrinks tag reach by this much.").defineInRange("slideTagReduction", 0.3, 0.0, 2.0);
        b.pop();
        b.comment("Live real-world baseball (Watch Live Game). Only the server talks to the data provider; clients never do.").push("live");
        LIVE_ENABLED = b.comment("Allow players to browse and watch real games.").define("enabled", true);
        LIVE_API_BASE_URL = b.comment("Base URL of the MLB Stats API (change only for a mirror/proxy).")
            .define("apiBaseUrl", "https://statsapi.mlb.com");
        LIVE_HTTP_TIMEOUT_SECONDS = b.comment("Give up on a single HTTP request after this long.").defineInRange("httpTimeoutSeconds", 10, 2, 60);
        LIVE_HTTP_RETRIES = b.comment("Quick retries per request (timeouts, connection errors, HTTP 5xx/429) before reporting a failure.")
            .defineInRange("httpRetries", 2, 0, 6);
        LIVE_SCHEDULE_REFRESH_LIVE_SECONDS = b.comment("Refresh today's games this often while any game is live (only while someone has the browser open).")
            .defineInRange("scheduleRefreshLiveSeconds", 15, 5, 300);
        LIVE_SCHEDULE_REFRESH_IDLE_SECONDS = b.comment("Refresh this often when no game is live yet.")
            .defineInRange("scheduleRefreshIdleSeconds", 60, 15, 3600);
        LIVE_DEBUG_RECORDING = b.comment("DEVELOPER: save every raw API response to <server folder>/mcbaseball-live-recordings for reproducing bugs. Leave off normally.")
            .define("debugRecording", false);
        LIVE_DEBUG_RECORDING_MAX_FILES = b.comment("DEVELOPER: stop recording after this many files per server run.")
            .defineInRange("debugRecordingMaxFiles", 2000, 10, 100000);
        b.pop();
        SPEC = b.build();
    }
}
