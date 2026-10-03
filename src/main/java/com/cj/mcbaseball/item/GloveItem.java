package com.cj.mcbaseball.item;

import com.cj.mcbaseball.client.GloveClientExtensions;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

public class GloveItem extends Item {
    private final GloveItem.GloveStats stats;

    public GloveItem(GloveItem.GloveStats stats, Properties props) {
        super(props);
        this.stats = stats;
    }

    public GloveItem.GloveStats stats() {
        return this.stats;
    }

    @Nullable
    public static GloveItem.GloveStats heldGlove(Player p) {
        if (p.getOffhandItem().getItem() instanceof GloveItem g) {
            return g.stats;
        } else {
            return p.getMainHandItem().getItem() instanceof GloveItem g ? g.stats : null;
        }
    }

    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(BatItem.statLine("Reach", Math.round(this.stats.catchRadiusBonus() * 100.0F)));
        tooltip.add(BatItem.statLine("Sure Hands", Math.round(this.stats.maxCatchSpeedBonus() * 40.0F)));
        tooltip.add(Component.translatable("item.mcbaseball.glove.tip").withStyle(ChatFormatting.DARK_GRAY));
    }

    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(GloveClientExtensions.INSTANCE);
    }

    public static record GloveStats(float catchRadiusBonus, float maxCatchSpeedBonus, boolean firstBaseScoop) {
    }
}
