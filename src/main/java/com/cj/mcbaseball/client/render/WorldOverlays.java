package com.cj.mcbaseball.client.render;

import com.cj.mcbaseball.client.ClientGameState;
import com.cj.mcbaseball.entity.BaseballEntity;
import com.cj.mcbaseball.game.GameKit;
import com.cj.mcbaseball.game.GamePhase;
import com.cj.mcbaseball.game.PlayerRole;
import com.cj.mcbaseball.item.BaseballItem;
import com.cj.mcbaseball.item.BatItem;
import com.cj.mcbaseball.network.GameHudPacket;
import com.cj.mcbaseball.physics.AimSolver;
import com.cj.mcbaseball.physics.BallMotion;
import com.cj.mcbaseball.physics.BallPhysics;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class WorldOverlays {
    private static final DustParticleOptions MARKER = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.2F), 1.0F);

    public static void renderAll(PoseStack pose, Camera camera, float partialTick) {
        if (ClientGameState.active()) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p != null && mc.level != null) {
                GameHudPacket s = ClientGameState.hud();
                PlayerRole role = ClientGameState.role();
                boolean pitching = s.phase() == GamePhase.PITCHING.ordinal();
                Vec3 cam = camera.getPosition();
                Vec3 fwd = new Vec3(s.fwdX(), 0.0, s.fwdZ());
                Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
                Vec3 home = new Vec3(s.homeX(), s.homeY(), s.homeZ());
                BufferSource buffers = mc.renderBuffers().bufferSource();
                pose.pushPose();
                pose.translate(-cam.x, -cam.y, -cam.z);
                Matrix4f m = pose.last().pose();
                Matrix3f n = pose.last().normal();
                VertexConsumer lines = buffers.getBuffer(RenderType.lines());
                if (s.showZone() && pitching && (role == PlayerRole.BATTER || role == PlayerRole.PITCHER || role == PlayerRole.CATCHER)) {
                    double hw = 0.5;
                    Vec3 bl = home.add(right.scale(-hw)).add(0.0, 0.45, 0.0);
                    Vec3 br = home.add(right.scale(hw)).add(0.0, 0.45, 0.0);
                    Vec3 tl = home.add(right.scale(-hw)).add(0.0, 1.35, 0.0);
                    Vec3 tr = home.add(right.scale(hw)).add(0.0, 1.35, 0.0);
                    line(lines, m, n, bl, br, 1.0F, 1.0F, 1.0F, 0.6F);
                    line(lines, m, n, br, tr, 1.0F, 1.0F, 1.0F, 0.6F);
                    line(lines, m, n, tr, tl, 1.0F, 1.0F, 1.0F, 0.6F);
                    line(lines, m, n, tl, bl, 1.0F, 1.0F, 1.0F, 0.6F);
                }

                if (s.assist() && pitching && role == PlayerRole.BATTER) {
                    BaseballEntity ball = incomingPitch(mc, home, fwd);
                    if (ball != null) {
                        Vec3 planePt = home.add(fwd.scale(0.35));
                        Vec3 c0 = ball.getPosition(partialTick).add(0.0, 0.125, 0.0);
                        AimSolver.Crossing c = AimSolver.toPlane(
                            c0, ball.getDeltaMovement(), ball.getSpin(), BallPhysics.Params.fromConfig(), planePt, fwd.reverse(), 40
                        );
                        if (c != null) {
                            float ideal = 2.0F / (p.getMainHandItem().getItem() instanceof BatItem bat ? bat.stats().swingSpeed() : 1.0F);
                            double lead = c.ticks() - (double)ideal;
                            double radius = 0.16 + Math.max(0.0, lead) * 0.09;
                            boolean now = Math.abs(lead) < 0.8;
                            float r = now ? 0.3F : 1.0F;
                            float gg = 1.0F;
                            float b = now ? 0.3F : 0.4F;
                            ring(lines, m, n, c.point(), right, radius, r, gg, b, 0.95F);
                            if (now) {
                                ring(lines, m, n, c.point(), right, radius + 0.05, r, gg, b, 0.8F);
                            }
                        }
                    }
                }

                if (pitching && role == PlayerRole.PITCHER && holdsGameBall(p.getMainHandItem().getItem(), p)) {
                    Vec3 eye = p.getEyePosition(partialTick);
                    Vec3 look = p.getViewVector(partialTick);
                    double lf = look.dot(fwd);
                    if (lf < -0.4) {
                        double t = -eye.subtract(home).dot(fwd) / lf;
                        Vec3 aim = eye.add(look.scale(t));
                        boolean inZone = Math.abs(aim.subtract(home).dot(right)) <= 0.5
                            && aim.y - s.homeY() >= 0.45
                            && aim.y - s.homeY() <= 1.35;
                        float r = inZone ? 0.4F : 1.0F;
                        float gg = inZone ? 1.0F : 0.55F;
                        float b = 0.3F;
                        ring(lines, m, n, aim, right, 0.14, r, gg, b, 0.9F);
                        line(lines, m, n, aim.add(right.scale(-0.22)), aim.add(right.scale(0.22)), r, gg, b, 0.9F);
                        line(lines, m, n, aim.add(0.0, -0.22, 0.0), aim.add(0.0, 0.22, 0.0), r, gg, b, 0.9F);
                    }
                }

                pose.popPose();
                buffers.endBatch(RenderType.lines());
                if (role != PlayerRole.NONE && role != PlayerRole.ON_DECK) {
                    Font font = mc.font;
                    String[] names = new String[]{"", "1ST", "2ND", "3RD", "HOME"};

                    for (int bIdx = 1; bIdx <= 4; bIdx++) {
                        Vec3 at = s.base(bIdx).add(0.0, 2.4, 0.0);
                        double dist = at.distanceTo(cam);
                        if (!(dist > 90.0)) {
                            pose.pushPose();
                            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
                            pose.mulPose(camera.rotation());
                            float sc = (float)(0.025 * Math.max(1.0, dist / 14.0));
                            pose.scale(-sc, -sc, sc);
                            String label = names[bIdx];
                            int w = font.width(label);
                            font.drawInBatch(
                                label,
                                (float)(-w) / 2.0F,
                                0.0F,
                                -729011,
                                false,
                                pose.last().pose(),
                                buffers,
                                DisplayMode.SEE_THROUGH,
                                1610612736,
                                15728880
                            );
                            pose.popPose();
                        }
                    }

                    buffers.endBatch();
                }
            }
        }
    }

    private static boolean holdsGameBall(Item item, LocalPlayer p) {
        return item instanceof BaseballItem && GameKit.gameBallId(p.getMainHandItem()) != null;
    }

    private static BaseballEntity incomingPitch(Minecraft mc, Vec3 home, Vec3 fwd) {
        for (BaseballEntity b : mc.level.getEntitiesOfClass(BaseballEntity.class, mc.player.getBoundingBox().inflate(48.0))) {
            if (b.isGameBall() && b.getMotion() == BallMotion.FLYING) {
                Vec3 c = b.getCenter();
                double d = c.subtract(home).dot(fwd);
                if (d > 0.2 && b.getDeltaMovement().dot(fwd) < -0.2 && d < 40.0) {
                    return b;
                }
            }
        }

        return null;
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 c, Vec3 right, double r, float cr, float cg, float cb, float a) {
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        int seg = 28;
        Vec3 prev = c.add(right.scale(r));

        for (int i = 1; i <= seg; i++) {
            double t = (double)i * Math.PI * 2.0 / (double)seg;
            Vec3 next = c.add(right.scale(Math.cos(t) * r)).add(up.scale(Math.sin(t) * r));
            line(vc, m, n, prev, next, cr, cg, cb, a);
            prev = next;
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, float r, float g, float bl, float al) {
        Vec3 d = b.subtract(a);
        if (!(d.lengthSqr() < 1.0E-9)) {
            d = d.normalize();
            vc.vertex(m, (float)a.x, (float)a.y, (float)a.z)
                .color(r, g, bl, al)
                .normal(n, (float)d.x, (float)d.y, (float)d.z)
                .endVertex();
            vc.vertex(m, (float)b.x, (float)b.y, (float)b.z)
                .color(r, g, bl, al)
                .normal(n, (float)d.x, (float)d.y, (float)d.z)
                .endVertex();
        }
    }

    public static void tickLandingMarkers() {
        Minecraft mc = Minecraft.getInstance();
        if (ClientGameState.active() && mc.player != null && mc.level != null) {
            GameHudPacket s = ClientGameState.hud();
            PlayerRole role = ClientGameState.role();
            if (s.showLanding() && mc.player.tickCount % 2 == 0) {
                if (role == PlayerRole.FIELDER || role == PlayerRole.CATCHER || role == PlayerRole.PITCHER) {
                    BallPhysics.Params params = BallPhysics.Params.fromConfig();

                    for (BaseballEntity b : mc.level.getEntitiesOfClass(BaseballEntity.class, mc.player.getBoundingBox().inflate(160.0))) {
                        if (b.isGameBall()
                            && b.syncedBounces() == 0
                            && b.getMotion() == BallMotion.FLYING
                            && !(b.getDeltaMovement().length() < 0.2)
                            && (!(b.getDeltaMovement().y < -0.05) || !(b.getY() - s.homeY() < 2.5))) {
                            BallPhysics.Prediction pr = BallPhysics.predictLanding(b.getCenter(), b.getDeltaMovement(), b.getSpin(), params, s.homeY(), 200);
                            if (pr != null && pr.ticks() >= 6) {
                                Vec3 c = pr.point();

                                for (int i = 0; i < 10; i++) {
                                    double a = (double)i * Math.PI / 5.0 + (double)mc.player.tickCount * 0.12;
                                    mc.level
                                        .addParticle(MARKER, c.x + Math.cos(a) * 0.8, s.homeY() + 0.08, c.z + Math.sin(a) * 0.8, 0.0, 0.0, 0.0);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private WorldOverlays() {
    }
}
