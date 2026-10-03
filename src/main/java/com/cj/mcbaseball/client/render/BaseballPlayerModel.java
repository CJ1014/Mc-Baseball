package com.cj.mcbaseball.client.render;

import com.cj.mcbaseball.client.BatClientExtensions;
import com.cj.mcbaseball.client.anim.ThrowPoses;
import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.npc.NpcAnim;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

public class BaseballPlayerModel extends PlayerModel<BaseballPlayerEntity> {
    public BaseballPlayerModel(ModelPart root) {
        super(root, false);
    }

    public void setupAnim(BaseballPlayerEntity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        NpcAnim a = e.anim();
        float age = Math.max(0.0F, ageInTicks - (float)e.animStart());
        float p = a.duration() > 0 ? Mth.clamp(age / (float)a.duration(), 0.0F, 1.0F) : 0.0F;
        switch (a) {
            case BAT_STANCE:
                BatClientExtensions.applyStance(this, e, HumanoidArm.RIGHT);
                break;
            case SWING:
                float t = ease(Mth.clamp(p * 1.6F, 0.0F, 1.0F));
                this.rightArm.xRot = Mth.lerp(t, -1.95F, -1.0F);
                this.leftArm.xRot = Mth.lerp(t, -1.7F, -1.0F);
                this.rightArm.yRot = Mth.lerp(t, -0.55F, 1.35F);
                this.leftArm.yRot = Mth.lerp(t, 0.85F, 1.5F);
                this.body.yRot = Mth.lerp(t, -0.2F, 0.9F);
                break;
            case BUNT:
                this.rightArm.xRot = -1.45F;
                this.leftArm.xRot = -1.45F;
                this.rightArm.yRot = -0.25F;
                this.leftArm.yRot = 0.35F;
                break;
            case PITCH:
            case THROW:
            case T_FOUR_SEAM:
            case T_TWO_SEAM:
            case T_SINKER:
            case T_CURVE:
            case T_SLIDER:
            case T_CHANGEUP:
            case T_OVERHAND:
            case T_SIDEARM:
            case T_FLIP:
            case T_CROWHOP:
                ThrowPoses.apply(this, a.throwKind(), p, true);
                break;
            case CATCH_READY:
                this.rightArm.xRot = -0.9F;
                this.leftArm.xRot = -1.35F;
                this.leftArm.yRot = 0.2F;
                this.rightLeg.xRot = -1.25F;
                this.leftLeg.xRot = -1.25F;
                this.rightLeg.yRot = 0.3F;
                this.leftLeg.yRot = -0.3F;
                break;
            case SLIDE:
                this.rightArm.xRot = -2.6F;
                this.leftArm.xRot = -2.6F;
                this.rightLeg.xRot = -0.2F;
                this.leftLeg.xRot = -0.6F;
                break;
            case CHEER:
                float wave = Mth.sin(ageInTicks * 0.6F) * 0.35F;
                this.rightArm.xRot = -2.8F + wave;
                this.leftArm.xRot = -2.8F - wave;
                this.rightArm.zRot = 0.3F;
                this.leftArm.zRot = -0.3F;
        }

        this.leftSleeve.copyFrom(this.leftArm);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftPants.copyFrom(this.leftLeg);
        this.rightPants.copyFrom(this.rightLeg);
        this.jacket.copyFrom(this.body);
        this.hat.copyFrom(this.head);
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
