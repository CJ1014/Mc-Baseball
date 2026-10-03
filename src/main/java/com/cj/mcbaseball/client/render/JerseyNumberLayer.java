package com.cj.mcbaseball.client.render;

import com.cj.mcbaseball.npc.BaseballPlayerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class JerseyNumberLayer extends RenderLayer<BaseballPlayerEntity, BaseballPlayerModel> {
    private final Font font;

    public JerseyNumberLayer(RenderLayerParent<BaseballPlayerEntity, BaseballPlayerModel> parent, Font font) {
        super(parent);
        this.font = font;
    }

    public void render(
        PoseStack pose,
        MultiBufferSource buffers,
        int light,
        BaseballPlayerEntity e,
        float limbSwing,
        float limbSwingAmount,
        float partialTick,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        int num = e.jerseyNumber();
        if (num > 0 && !e.isInvisible()) {
            String s = Integer.toString(num);
            pose.pushPose();
            ((BaseballPlayerModel)this.getParentModel()).body.translateAndRotate(pose);
            pose.translate(0.0, 0.21875, 0.2);
            float scale = 0.0375F;
            pose.scale(-scale, scale, scale);
            int w = this.font.width(s);
            this.font
                .drawInBatch(
                    s,
                    (float)(-w) / 2.0F,
                    0.0F,
                    0xFF000000 | e.numberColor(),
                    false,
                    pose.last().pose(),
                    buffers,
                    DisplayMode.POLYGON_OFFSET,
                    0,
                    light
                );
            pose.popPose();
        }
    }
}
