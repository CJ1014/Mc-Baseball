package com.cj.mcbaseball.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.crafting.Ingredient;

public enum UniformArmorMaterial implements ArmorMaterial {
    UNIFORM;

    public int getDurabilityForType(Type type) {
        return 120;
    }

    public int getDefenseForType(Type type) {
        return type == Type.CHESTPLATE ? 1 : 0;
    }

    public int getEnchantmentValue() {
        return 12;
    }

    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    public Ingredient getRepairIngredient() {
        return Ingredient.of(ItemTags.WOOL);
    }

    public String getName() {
        return "mcbaseball:uniform";
    }

    public float getToughness() {
        return 0.0F;
    }

    public float getKnockbackResistance() {
        return 0.0F;
    }
}
