package com.cj.mcbaseball.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "mcbaseball");
    public static final RegistryObject<SoundEvent> BAT_HIT = reg("bat_hit");
    public static final RegistryObject<SoundEvent> BAT_SWING = reg("bat_swing");
    public static final RegistryObject<SoundEvent> GLOVE_CATCH = reg("glove_catch");
    public static final RegistryObject<SoundEvent> BALL_BOUNCE = reg("ball_bounce");
    public static final RegistryObject<SoundEvent> SLIDE = reg("slide");
    public static final RegistryObject<SoundEvent> STRIKEOUT = reg("strikeout");
    public static final RegistryObject<SoundEvent> HOME_RUN = reg("home_run");
    public static final RegistryObject<SoundEvent> GAME_START = reg("game_start");
    public static final RegistryObject<SoundEvent> GAME_END = reg("game_end");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("mcbaseball", name)));
    }

    private ModSounds() {
    }
}
