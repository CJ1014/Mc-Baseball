package com.cj.mcbaseball.item;

import com.cj.mcbaseball.client.CapClientExtensions;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeableArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public class UniformArmorItem extends DyeableArmorItem {
    public static final int DEFAULT_COLOR = 15921906;

    public UniformArmorItem(Type type, Properties props) {
        super(UniformArmorMaterial.UNIFORM, type, props);
    }

    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        if (this.getType() == Type.HELMET) {
            consumer.accept(CapClientExtensions.INSTANCE);
        }
    }

    public int getColor(ItemStack stack) {
        CompoundTag display = stack.getTagElement("display");
        return display != null && display.contains("color", 99) ? display.getInt("color") : 15921906;
    }
}
