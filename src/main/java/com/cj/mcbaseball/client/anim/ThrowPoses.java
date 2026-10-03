package com.cj.mcbaseball.client.anim;

import com.cj.mcbaseball.anim.ThrowKind;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class ThrowPoses {
    private static ThrowPoses.Keys keys(ThrowKind k) {
        return switch (k) {
            case FOUR_SEAM -> new ThrowPoses.Keys(-2.9F, 0.35F, 0.0F, 0.45F, 1.1F, 0.6F, -0.2F, 0.3F, -0.5F, 0.35F);
            case TWO_SEAM -> new ThrowPoses.Keys(-2.7F, 0.6F, 0.0F, 0.45F, 1.0F, 0.5F, -0.5F, 0.5F, -0.55F, 0.3F);
            case SINKER -> new ThrowPoses.Keys(-2.4F, 0.95F, 0.0F, 0.5F, 1.0F, 0.3F, -0.7F, 0.7F, -0.6F, 0.35F);
            case CURVE -> new ThrowPoses.Keys(-3.1F, 0.15F, 0.0F, 0.4F, 1.15F, 1.1F, 0.0F, 0.2F, -0.45F, 0.55F);
            case SLIDER -> new ThrowPoses.Keys(-2.6F, 0.7F, -0.3F, 0.5F, 1.0F, 0.4F, -0.4F, 1.1F, -0.7F, 0.3F);
            case CHANGEUP -> new ThrowPoses.Keys(-2.9F, 0.35F, 0.0F, 0.45F, 1.1F, -0.1F, -0.2F, 0.15F, -0.3F, 0.15F);
            case OVERHAND -> new ThrowPoses.Keys(-2.6F, 0.3F, 0.0F, 0.35F, 0.0F, 0.4F, -0.2F, 0.3F, -0.4F, 0.25F);
            case SIDEARM -> new ThrowPoses.Keys(-1.5F, 1.35F, -0.6F, 0.5F, 0.0F, -1.2F, 1.2F, 1.2F, -0.6F, 0.1F);
            case FLIP -> new ThrowPoses.Keys(0.7F, 0.1F, 0.0F, -0.1F, 0.0F, -1.4F, 0.0F, 0.1F, 0.0F, 0.2F);
            case CROWHOP -> new ThrowPoses.Keys(-3.0F, 0.4F, 0.0F, 0.5F, 0.7F, 0.8F, -0.2F, 0.3F, -0.6F, 0.45F);
        };
    }

    public static void apply(HumanoidModel<?> m, ThrowKind kind, float p, boolean rightHanded) {
        ThrowPoses.Keys k = keys(kind);
        float bodyX = 0.0F;
        float kick = 0.0F;
        float armX;
        float armZ;
        float armY;
        float bodyY;
        float glove;
        if (p < kind.release) {
            float t = ease(p / kind.release);
            armX = Mth.lerp(t, 0.0F, k.cX);
            armZ = Mth.lerp(t, 0.0F, k.cZ);
            armY = Mth.lerp(t, 0.0F, k.cY);
            bodyY = Mth.lerp(t, 0.0F, k.cBody);
            kick = k.kick * t;
            glove = Mth.lerp(t, 0.0F, -1.2F);
        } else {
            float t = ease((p - kind.release) / (1.0F - kind.release));
            if (kind == ThrowKind.CHANGEUP) {
                t *= t;
            }

            armX = Mth.lerp(t, k.cX, k.fX);
            armZ = Mth.lerp(t, k.cZ, k.fZ);
            armY = Mth.lerp(t, k.cY, k.fY);
            bodyY = Mth.lerp(t, k.cBody, k.fBody);
            bodyX = Mth.lerp(t, 0.0F, k.fBend);
            kick = k.kick * (1.0F - t);
            glove = Mth.lerp(t, -1.2F, -0.2F);
        }

        float s = rightHanded ? 1.0F : -1.0F;
        ModelPart throwArm = rightHanded ? m.rightArm : m.leftArm;
        ModelPart gloveArm = rightHanded ? m.leftArm : m.rightArm;
        ModelPart frontLeg = rightHanded ? m.leftLeg : m.rightLeg;
        throwArm.xRot = armX;
        throwArm.zRot = armZ * s;
        throwArm.yRot = armY * s;
        gloveArm.xRot = glove;
        gloveArm.yRot = 0.2F * s;
        gloveArm.zRot = 0.0F;
        m.body.yRot = bodyY * s;
        m.body.xRot = bodyX;
        frontLeg.xRot = -kick;
        if (kind == ThrowKind.CROWHOP) {
            (rightHanded ? m.rightLeg : m.leftLeg).xRot = -kick * 0.6F;
        }
    }

    public static void windup(HumanoidModel<?> m, float charge, boolean rightHanded) {
        apply(m, ThrowKind.OVERHAND, ThrowKind.OVERHAND.release * Mth.clamp(charge, 0.0F, 1.0F), rightHanded);
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private ThrowPoses() {
    }

    private static record Keys(float cX, float cZ, float cY, float cBody, float kick, float fX, float fZ, float fY, float fBody, float fBend) {
    }
}
