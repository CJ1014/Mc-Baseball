package com.cj.mcbaseball.client.render;

import com.cj.mcbaseball.block.AngledBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

public class AngledBlockRenderer implements BlockEntityRenderer<AngledBlockEntity> {
    public AngledBlockRenderer(Context ctx) {
    }

    public void render(AngledBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        renderRotated(be.getBlockState(), be.yaw(), pose, buffers, light, overlay);
    }

    public static void renderRotated(BlockState state, float yaw, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState base = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? (BlockState)state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) : state;
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(base);
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.translate(-0.5, 0.0, -0.5);
        dispatcher.getModelRenderer()
            .renderModel(
                pose.last(),
                buffers.getBuffer(RenderTypeHelper.getEntityRenderType(RenderType.cutout(), false)),
                base,
                model,
                1.0F,
                1.0F,
                1.0F,
                light,
                overlay,
                ModelData.EMPTY,
                RenderType.cutout()
            );
        pose.popPose();
    }
}
