package com.cj.mcbaseball.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "mcbaseball");
    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mcbaseball"))
                .icon(() -> new ItemStack((ItemLike)ModItems.BASEBALL.get()))
                .displayItems((params, out) -> {
                    for (RegistryObject<Item> item : ModItems.ITEMS.getEntries()) {
                        out.accept((ItemLike)item.get());
                    }
                })
                .build()
    );

    private ModCreativeTabs() {
    }
}
