package com.cj.mcbaseball.client;

import com.cj.mcbaseball.client.anim.ClientThrowAnims;
import com.cj.mcbaseball.client.anim.ThrowPoses;
import com.cj.mcbaseball.item.BaseballItem;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class GloveClientExtensions implements IClientItemExtensions {
    public static final GloveClientExtensions INSTANCE = new GloveClientExtensions();
    private static ArmPose pose;

    private static ArmPose pose() {
        if (pose == null) {
            pose = ArmPose.create("MCBASEBALL_THROW", true, (model, e, arm) -> {
                boolean right = e.getMainArm() == HumanoidArm.RIGHT;
                ClientThrowAnims.Active a = ClientThrowAnims.current(e);
                if (a != null) {
                    ThrowPoses.apply(model, a.kind(), a.progress(), right);
                } else if (charging(e)) {
                    ThrowPoses.windup(model, (float)e.getTicksUsingItem() / 20.0F, right);
                }
            });
        }

        return pose;
    }

    private static boolean charging(LivingEntity e) {
        return e.isUsingItem() && e.getUseItem().getItem() instanceof BaseballItem;
    }

    public ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (!(entity instanceof Player) || hand != InteractionHand.OFF_HAND) {
            return null;
        } else {
            return !charging(entity) && ClientThrowAnims.current(entity) == null ? null : pose();
        }
    }
}
