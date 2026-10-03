package com.cj.mcbaseball.client;

import com.cj.mcbaseball.client.render.CapModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class CapClientExtensions implements IClientItemExtensions {
    public static final CapClientExtensions INSTANCE = new CapClientExtensions();
    private HumanoidModel<LivingEntity> model;

    public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
        if (slot != EquipmentSlot.HEAD) {
            return original;
        } else {
            if (this.model == null) {
                this.model = new HumanoidModel(Minecraft.getInstance().getEntityModels().bakeLayer(CapModel.LAYER));
            }

            return this.model;
        }
    }
}
