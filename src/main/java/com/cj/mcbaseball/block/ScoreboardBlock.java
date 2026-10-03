package com.cj.mcbaseball.block;

import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.item.FieldSetupToolItem;
import com.cj.mcbaseball.scoreboard.ScoreboardBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ScoreboardBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public ScoreboardBlock(Properties props) {
        super(props);
        this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
        b.add(new Property[]{FACING});
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return (BlockState)this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof ScoreboardBlockEntity sb) {
            sb.setYaw(AngledBlockEntity.snap(placer.getYRot() + 180.0F));
        }

        if (!level.isClientSide) {
            BlockPos ctrl = FieldControllerBlockEntity.nearestLoaded(level, pos, 128.0);
            if (ctrl != null) {
                link(level, pos, ctrl, placer instanceof Player p ? p : null);
            }
        }
    }

    public static void link(Level level, BlockPos scoreboard, BlockPos controller, @Nullable Player who) {
        if (level.getBlockEntity(scoreboard) instanceof ScoreboardBlockEntity sb && level.getBlockEntity(controller) instanceof FieldControllerBlockEntity be) {
            sb.link(controller);
            be.linkScoreboard(scoreboard);
            if (who != null) {
                who.displayClientMessage(
                    Component.translatable("mcbaseball.scoreboard.linked", new Object[]{controller.getX(), controller.getY(), controller.getZ()})
                        .withStyle(ChatFormatting.GREEN),
                    true
                );
            }
        }

        if (level.getBlockEntity(controller) instanceof FieldControllerBlockEntity c) {
            c.aimFieldBlocks();
        }
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof ScoreboardBlockEntity sb) {
                sb.setYaw(AngledBlockEntity.snap(sb.yaw() + 22.5F));
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof FieldSetupToolItem) {
                if (!level.isClientSide) {
                    BlockPos ctrl = FieldSetupToolItem.linkedController(held);
                    if (ctrl != null) {
                        link(level, pos, ctrl, player);
                    }
                }

                return InteractionResult.sidedSuccess(level.isClientSide);
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScoreboardBlockEntity(pos, state);
    }
}
