package com.cj.mcbaseball.client.render;

import com.cj.mcbaseball.scoreboard.ScoreboardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class ScoreboardRenderer implements BlockEntityRenderer<ScoreboardBlockEntity> {
    private final Font font;
    private static final ResourceLocation PANEL = new ResourceLocation("mcbaseball", "textures/misc/board_panel.png");
    private static final ResourceLocation FRAME = new ResourceLocation("mcbaseball", "textures/misc/board_frame.png");

    public ScoreboardRenderer(Context ctx) {
        this.font = ctx.getFont();
    }

    public void render(ScoreboardBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ScoreboardBlockEntity.Snapshot s = be.snapshot();
        float yaw = be.yaw();
        boolean big = be.scale() > 1;
        if (!big) {
            AngledBlockRenderer.renderRotated(be.getBlockState(), yaw, pose, buffers, light, overlay);
        }

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0.0, 0.0, big ? 0.0 : 0.502);
        float sc = (float)be.scale() / 110.0F;
        pose.scale(sc, -sc, sc);
        if (big) {
            drawPanel(pose, buffers);
        }

        Matrix4f m = pose.last().pose();
        int full = 15728880;
        if (!s.active()) {
            this.draw(m, buffers, "PLAY", -42.0F, -30.0F, 16048205, full);
            this.draw(m, buffers, "BALL!", -42.0F, -10.0F, 16048205, full);
        } else {
            this.draw(m, buffers, pad(s.away()), -44.0F, -40.0F, s.awayColor() | 2105376, full);
            this.draw(m, buffers, String.valueOf(s.awayRuns()), 22.0F, -40.0F, 16777215, full);
            this.draw(m, buffers, pad(s.home()), -44.0F, -24.0F, s.homeColor() | 2105376, full);
            this.draw(m, buffers, String.valueOf(s.homeRuns()), 22.0F, -24.0F, 16777215, full);
            String inn = s.fin() ? "FINAL" : (s.top() ? "TOP " : "BOT ") + s.inning();
            this.draw(m, buffers, inn, -44.0F, -4.0F, 16048205, full);
            if (!s.fin()) {
                this.draw(m, buffers, "B" + s.balls() + " S" + s.strikes(), -44.0F, 12.0F, 8311624, full);
                this.draw(m, buffers, "OUT " + s.outs(), -44.0F, 28.0F, 15759898, full);
            }
        }

        pose.popPose();
    }

    public boolean shouldRenderOffScreen(ScoreboardBlockEntity be) {
        return be.scale() > 1;
    }

    public int getViewDistance() {
        return 256;
    }

    private static void drawPanel(PoseStack pose, MultiBufferSource buffers) {
        quad(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(FRAME)), -75.0F, -61.0F, 75.0F, 57.0F, -2.0F, 6.0F);
        quad(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(PANEL)), -69.0F, -55.0F, 69.0F, 51.0F, -1.0F, 9.0F);
    }

    private static void quad(PoseStack pose, VertexConsumer vc, float x0, float y0, float x1, float y1, float z, float tiles) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int light = 15728880;
        int ov = OverlayTexture.NO_OVERLAY;
        float u = (x1 - x0) / (y1 - y0) * tiles;
        vc.vertex(m, x0, y0, z).color(255, 255, 255, 255).uv(0.0F, 0.0F).overlayCoords(ov).uv2(light).normal(n, 0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x0, y1, z).color(255, 255, 255, 255).uv(0.0F, tiles).overlayCoords(ov).uv2(light).normal(n, 0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x1, y1, z).color(255, 255, 255, 255).uv(u, tiles).overlayCoords(ov).uv2(light).normal(n, 0.0F, 0.0F, 1.0F).endVertex();
        vc.vertex(m, x1, y0, z).color(255, 255, 255, 255).uv(u, 0.0F).overlayCoords(ov).uv2(light).normal(n, 0.0F, 0.0F, 1.0F).endVertex();
    }

    private static String pad(String abbr) {
        return abbr.length() > 3 ? abbr.substring(0, 3) : abbr;
    }

    private void draw(Matrix4f m, MultiBufferSource buffers, String text, float x, float y, int color, int light) {
        this.font.drawInBatch(text, x, y, 0xFF000000 | color, false, m, buffers, DisplayMode.POLYGON_OFFSET, 0, light);
    }
}
