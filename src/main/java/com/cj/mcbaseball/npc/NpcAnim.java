package com.cj.mcbaseball.npc;

import com.cj.mcbaseball.anim.ThrowKind;
import org.jetbrains.annotations.Nullable;

public enum NpcAnim {
    NONE,
    BAT_STANCE,
    SWING,
    BUNT,
    PITCH,
    THROW,
    CATCH_READY,
    SLIDE,
    CHEER,
    T_FOUR_SEAM,
    T_TWO_SEAM,
    T_SINKER,
    T_CURVE,
    T_SLIDER,
    T_CHANGEUP,
    T_OVERHAND,
    T_SIDEARM,
    T_FLIP,
    T_CROWHOP;

    @Nullable
    public ThrowKind throwKind() {
        if (this == PITCH) {
            return ThrowKind.FOUR_SEAM;
        } else if (this == THROW) {
            return ThrowKind.OVERHAND;
        } else {
            return this.name().startsWith("T_") ? ThrowKind.valueOf(this.name().substring(2)) : null;
        }
    }

    public static NpcAnim forThrow(ThrowKind k) {
        return valueOf("T_" + k.name());
    }

    public int duration() {
        ThrowKind k = this.throwKind();
        if (k != null) {
            return k.duration;
        } else {
            return switch (this) {
                case SWING -> 8;
                case PITCH -> 16;
                case THROW -> 10;
                case SLIDE -> 16;
                case CHEER -> 0;
                default -> 0;
            };
        }
    }

    public static NpcAnim byId(int id) {
        NpcAnim[] v = values();
        return id >= 0 && id < v.length ? v[id] : NONE;
    }
}
