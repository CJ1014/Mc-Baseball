package com.cj.mcbaseball.item;

import com.cj.mcbaseball.client.BatClientExtensions;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

public class BatItem extends Item {
    private final BatItem.BatStats stats;

    public BatItem(BatItem.BatStats stats, Properties props) {
        super(props);
        this.stats = stats;
    }

    public BatItem.BatStats stats() {
        return this.stats;
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        } else {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
    }

    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(BatClientExtensions.INSTANCE);
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(statLine("Sweet Spot", Math.round(this.stats.sweetSpot() * 100.0F)));
        tooltip.add(statLine("Power", Math.round(this.stats.power() * 100.0F)));
        tooltip.add(statLine("Bat Speed", Math.round(this.stats.swingSpeed() * 100.0F)));
        tooltip.add(Component.translatable("item.mcbaseball.bat.tip").withStyle(ChatFormatting.DARK_GRAY));
    }

    static Component statLine(String name, int value) {
        return Component.literal(name + ": ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE));
    }

    public static record BatStats(float sweetSpot, float power, float swingSpeed) {
    }
}
