package com.cj.mcbaseball.physics;

import com.cj.mcbaseball.config.BaseballConfig;

public final class BaseballUnits {
    public static final double MPH_PER_BLOCK_PER_TICK = 44.738726;

    public static double trueMph(double blocksPerTick) {
        return blocksPerTick * 44.738726;
    }

    public static double displayMph(double blocksPerTick) {
        return trueMph(blocksPerTick) * (Double)BaseballConfig.SPEED_DISPLAY_SCALE.get();
    }

    public static double blocksPerTickFromDisplayMph(double mph) {
        return mph / (44.738726 * (Double)BaseballConfig.SPEED_DISPLAY_SCALE.get());
    }

    private BaseballUnits() {
    }
}
