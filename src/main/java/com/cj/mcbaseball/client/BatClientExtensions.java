package com.cj.mcbaseball.client;

import com.cj.mcbaseball.item.BatItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class BatClientExtensions implements IClientItemExtensions {
    public static final BatClientExtensions INSTANCE = new BatClientExtensions();
    private static ArmPose stancePose;

    private static ArmPose stance() {
        if (stancePose == null) {
            stancePose = ArmPose.create("MCBASEBALL_BAT_STANCE", true, (model, entity, arm) -> applyStance(model, entity, arm));
        }

        return stancePose;
    }

    public static void applyStance(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        float head = model.head.yRot;
        model.rightArm.xRot = -1.9477875F;
        model.rightArm.yRot = -0.55F + head * 0.3F;
        model.rightArm.zRot = 0.1F;
        model.leftArm.xRot = -1.7278761F;
        model.leftArm.yRot = 0.85F + head * 0.3F;
        model.leftArm.zRot = 0.0F;
        if (!right) {
            model.leftArm.yRot = 0.55F + head * 0.3F;
            model.rightArm.yRot = -0.85F + head * 0.3F;
        }
    }

    public ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return entity.isUsingItem() && entity.getUseItem() == stack && stack.getItem() instanceof BatItem ? stance() : null;
    }
}
