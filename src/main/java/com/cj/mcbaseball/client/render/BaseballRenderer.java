package com.cj.mcbaseball.client.render;

import com.cj.mcbaseball.entity.BaseballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class BaseballRenderer extends EntityRenderer<BaseballEntity> {
    private static final ResourceLocation TEX = new ResourceLocation("mcbaseball", "textures/entity/baseball.png");
    private final ModelPart ball;

    public BaseballRenderer(Context ctx) {
        super(ctx);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("ball", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        this.ball = LayerDefinition.create(mesh, 16, 8).bakeRoot().getChild("ball");
        this.shadowRadius = 0.12F;
        this.shadowStrength = 0.6F;
    }

    public void render(BaseballEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (e.tickCount >= 2 || Minecraft.getInstance().getCameraEntity() == null || !(Minecraft.getInstance().getCameraEntity().distanceToSqr(e) < 2.0)) {
            Vec3 v = e.getDeltaMovement();
            double speed = v.length();
            pose.pushPose();
            pose.translate(0.0, 0.125, 0.0);
            if (speed > 0.001) {
                float dirYaw = (float)(Mth.atan2(v.x, v.z) * (180.0 / Math.PI));
                pose.mulPose(Axis.YP.rotationDegrees(dirYaw));
                pose.mulPose(Axis.XP.rotationDegrees(((float)e.tickCount + partialTick) * (float)Math.min(60.0, speed * 70.0)));
            }

            this.ball.render(pose, buffers.getBuffer(RenderType.entityCutout(TEX)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(e, yaw, partialTick, pose, buffers, light);
        }
    }

    public ResourceLocation getTextureLocation(BaseballEntity e) {
        return TEX;
    }
}
