package com.cj.mcbaseball.client.render;

import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.cj.mcbaseball.npc.NpcAnim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class BaseballPlayerRenderer extends HumanoidMobRenderer<BaseballPlayerEntity, BaseballPlayerModel> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[10];

    public BaseballPlayerRenderer(Context ctx) {
        super(ctx, new BaseballPlayerModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(
            new HumanoidArmorLayer(
                this, new HumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()
            )
        );
        this.addLayer(new JerseyNumberLayer(this, ctx.getFont()));
    }

    public ResourceLocation getTextureLocation(BaseballPlayerEntity e) {
        return SKINS[Math.floorMod(e.skin(), SKINS.length)];
    }

    protected void setupRotations(BaseballPlayerEntity e, PoseStack pose, float ageInTicks, float yaw, float partialTicks) {
        super.setupRotations(e, pose, ageInTicks, yaw, partialTicks);
        if (e.anim() == NpcAnim.SLIDE) {
            float t = Math.min(1.0F, (ageInTicks - (float)e.animStart()) / 4.0F);
            pose.translate(0.0, 0.15 * (double)t, 0.0);
            pose.mulPose(Axis.XP.rotationDegrees(-72.0F * t));
        }
    }

    static {
        for (int i = 0; i < SKINS.length; i++) {
            SKINS[i] = new ResourceLocation("mcbaseball", "textures/entity/npc/skin_" + i + ".png");
        }
    }
}
