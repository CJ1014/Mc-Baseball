package com.cj.mcbaseball.client.screen;

import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

public class HowToPlayScreen extends Screen {
    private static final int PAGES = 7;
    private static final int[] LINES = new int[]{5, 5, 6, 6, 6, 5, 6};
    @Nullable
    private final Screen parent;
    private int page;

    public HowToPlayScreen(@Nullable Screen parent) {
        super(Component.translatable("mcbaseball.guide.title"));
        this.parent = parent;
    }

    protected void init() {
        int y = this.height - 30;
        Button prev = (Button)this.addRenderableWidget(Button.builder(Component.literal("< ").append(Component.translatable("mcbaseball.guide.prev")), b -> {
            this.page--;
            this.rebuildWidgets();
        }).bounds(this.width / 2 - 154, y, 100, 20).build());
        prev.active = this.page > 0;
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.done"), b -> this.minecraft.setScreen(this.parent))
                .bounds(this.width / 2 - 50, y, 100, 20)
                .build()
        );
        Button next = (Button)this.addRenderableWidget(Button.builder(Component.translatable("mcbaseball.guide.next").append(" >"), b -> {
            this.page++;
            this.rebuildWidgets();
        }).bounds(this.width / 2 + 54, y, 100, 20).build());
        next.active = this.page < 6;
    }

    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int top = Math.max(8, this.height / 2 - 115);
        g.drawCenteredString(
            this.font, Component.translatable("mcbaseball.guide.page", new Object[]{this.page + 1, 7}).withStyle(ChatFormatting.DARK_GRAY), cx, top, 16777215
        );
        g.pose().pushPose();
        g.pose().translate((float)cx, (float)(top + 12), 0.0F);
        g.pose().scale(1.6F, 1.6F, 1.0F);
        g.drawCenteredString(
            this.font,
            Component.translatable("mcbaseball.guide." + this.page + ".title").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}),
            0,
            0,
            16777215
        );
        g.pose().popPose();
        int picX = cx - 150;
        int picY = top + 40;
        g.fill(picX - 4, picY - 4, picX + 104, picY + 104, Integer.MIN_VALUE);
        this.picture(g, this.page, picX, picY);
        int tx = cx - 34;
        int ty = top + 40;

        for (int i = 1; i <= LINES[this.page]; i++) {
            Component line = Component.translatable("mcbaseball.guide." + this.page + "." + i);

            for (FormattedCharSequence seq : this.font.split(line, 190)) {
                g.drawString(this.font, seq, tx, ty, 15132390, false);
                ty += 10;
            }

            ty += 4;
        }

        super.render(g, mx, my, pt);
    }

    private void picture(GuiGraphics g, int p, int x, int y) {
        switch (p) {
            case 0:
                this.diamond(g, x, y, true);
                break;
            case 1:
                g.fill(x + 8, y + 70, x + 26, y + 88, -9538432);
                g.fill(x + 11, y + 73, x + 23, y + 82, -15589354);
                g.fill(x + 12, y + 83, x + 15, y + 86, -12128166);
                arrow(g, x + 30, y + 74, x + 48, y + 56, -729011);
                this.mini(g, x + 46, y + 4, 0.5F);
                break;
            case 2:
                g.fill(x, y, x + 100, y + 100, -14992862);

                for (int i = 0; i < 3; i++) {
                    int cx = x + 20 + i * 30;
                    int cy = y + 50;
                    int r = 14 - i * 5;
                    circle(g, cx, cy, r, i == 2 ? -8465592 : -1);
                    g.fill(cx - 2, cy - 2, cx + 2, cy + 2, -855314);
                }

                g.drawCenteredString(this.font, Component.translatable("mcbaseball.guide.pic.swing"), x + 80, y + 76, 8311624);
                g.drawCenteredString(this.font, Component.translatable("mcbaseball.guide.pic.wait"), x + 22, y + 76, 16777215);
                break;
            case 3:
                g.fill(x, y, x + 100, y + 100, -14013904);
                outline(g, x + 30, y + 18, x + 70, y + 66, -1);
                circle(g, x + 44, y + 34, 6, -8465592);
                g.fill(x + 10, y + 82, x + 90, y + 88, -12961216);
                g.fill(x + 64, y + 82, x + 82, y + 88, -13722814);
                g.fill(x + 72, y + 78, x + 74, y + 92, -1);
                break;
            case 4:
                g.fill(x, y, x + 100, y + 100, -12612038);

                for (int i = 0; i < 6; i++) {
                    int bx = x + 10 + i * 12;
                    int by = y + 70 - (int)(Math.sin((double)i / 5.0 * Math.PI) * 50.0);
                    g.fill(bx, by, bx + 3, by + 3, -855314);
                }

                circle(g, x + 80, y + 78, 9, -729011);
                break;
            case 5:
                this.diamond(g, x, y, false);
                break;
            default:
                g.drawString(this.font, "K", x + 10, y + 8, 14697786, false);
                g.drawString(this.font, "↑", x + 10, y + 32, 14697786, false);
                g.drawString(this.font, "◆", x + 10, y + 56, 14697786, false);
                g.drawString(this.font, "✋", x + 10, y + 80, 14697786, false);
                g.drawString(this.font, Component.translatable("mcbaseball.guide.pic.k"), x + 24, y + 8, 16777215, false);
                g.drawString(this.font, Component.translatable("mcbaseball.guide.pic.fly"), x + 24, y + 32, 16777215, false);
                g.drawString(this.font, Component.translatable("mcbaseball.guide.pic.force"), x + 24, y + 56, 16777215, false);
                g.drawString(this.font, Component.translatable("mcbaseball.guide.pic.tag"), x + 24, y + 80, 16777215, false);
        }
    }

    private void diamond(GuiGraphics g, int x, int y, boolean arrows) {
        g.fill(x, y, x + 100, y + 100, -12612038);
        int cx = x + 50;
        int cy = y + 60;
        g.pose().pushPose();
        g.pose().translate((float)cx, (float)cy, 0.0F);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45.0F));
        g.fill(-26, -26, 26, 26, -5216193);
        g.fill(-18, -18, 18, 18, -10639286);
        g.pose().popPose();
        int[][] b = new int[][]{{cx, cy + 36}, {cx + 36, cy}, {cx, cy - 36}, {cx - 36, cy}};

        for (int[] p : b) {
            g.fill(p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3, -723728);
        }

        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, -723728);
        if (arrows) {
            for (int i = 0; i < 4; i++) {
                int[] a = b[i];
                int[] c = b[(i + 1) % 4];
                arrow(g, a[0] + (c[0] - a[0]) / 5, a[1] + (c[1] - a[1]) / 5, c[0] - (c[0] - a[0]) / 5, c[1] - (c[1] - a[1]) / 5, -729011);
            }
        } else {
            g.drawString(this.font, "1", b[1][0] + 5, b[1][1] - 4, 16777215, false);
            g.drawString(this.font, "2", b[2][0] + 5, b[2][1] - 4, 16777215, false);
            g.drawString(this.font, "3", b[3][0] - 10, b[3][1] - 4, 16777215, false);
        }
    }

    private void mini(GuiGraphics g, int x, int y, float s) {
        g.pose().pushPose();
        g.pose().translate((float)x, (float)y, 0.0F);
        g.pose().scale(s, s, 1.0F);
        this.diamond(g, 0, 0, false);
        g.pose().popPose();
    }

    private static void arrow(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));

        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            g.fill(x, y, x + 2, y + 2, c);
        }

        g.fill(x1 - 2, y1 - 2, x1 + 3, y1 + 3, c);
    }

    private static void circle(GuiGraphics g, int cx, int cy, int r, int c) {
        for (int a = 0; a < 360; a += 6) {
            int x = cx + (int)Math.round(Math.cos(Math.toRadians((double)a)) * (double)r);
            int y = cy + (int)Math.round(Math.sin(Math.toRadians((double)a)) * (double)r);
            g.fill(x, y, x + 1, y + 1, c);
        }
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(x0, y0, x1, y0 + 1, c);
        g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c);
        g.fill(x1 - 1, y0, x1, y1, c);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
