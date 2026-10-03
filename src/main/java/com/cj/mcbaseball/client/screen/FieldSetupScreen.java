package com.cj.mcbaseball.client.screen;

import com.cj.mcbaseball.field.FieldControllerBlockEntity;
import com.cj.mcbaseball.field.FieldLayout;
import com.cj.mcbaseball.field.FieldMarker;
import com.cj.mcbaseball.network.FieldSetupActionPacket;
import com.cj.mcbaseball.network.ModNetwork;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

public class FieldSetupScreen extends Screen {
    private static final int COL_W = 200;
    private static final int ROW_H = 22;
    private final BlockPos pos;
    private final Screen parent;
    private final Map<FieldMarker, Button> setButtons = new EnumMap<>(FieldMarker.class);
    private final Map<FieldMarker, Button> clearButtons = new EnumMap<>(FieldMarker.class);

    public FieldSetupScreen(BlockPos pos, @Nullable Screen parent) {
        super(Component.translatable("mcbaseball.gui.setup.title"));
        this.pos = pos;
        this.parent = parent;
    }

    @Nullable
    private FieldControllerBlockEntity controller() {
        return this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getBlockEntity(this.pos) instanceof FieldControllerBlockEntity be
            ? be
            : null;
    }

    private int top() {
        return Math.max(6, this.height / 2 - 112);
    }

    private int rowsTop() {
        return this.top() + 42;
    }

    private int leftX() {
        return this.width / 2 - 200 - 6;
    }

    private int rightX() {
        return this.width / 2 + 6;
    }

    protected void init() {
        this.setButtons.clear();
        this.clearButtons.clear();
        int rowY = this.rowsTop();
        int li = 0;
        int ri = 0;

        for (FieldMarker m : FieldMarker.values()) {
            int x = m.required() ? this.leftX() : this.rightX();
            int y = rowY + (m.required() ? li++ : ri++) * 22;
            Component setLabel = Component.translatable(m.multiPoint() ? "mcbaseball.gui.setup.add" : "mcbaseball.gui.setup.set");
            Button set = Button.builder(setLabel, b -> this.beginMark(m)).bounds(x + 200 - 72, y, 48, 20).build();
            Button clear = Button.builder(Component.literal("✕"), b -> this.send(FieldSetupActionPacket.Action.CLEAR, m))
                .bounds(x + 200 - 22, y, 20, 20)
                .build();
            clear.setTooltip(Tooltip.create(Component.translatable("mcbaseball.gui.setup.clear")));
            this.setButtons.put(m, (Button)this.addRenderableWidget(set));
            this.clearButtons.put(m, (Button)this.addRenderableWidget(clear));
        }

        int bottom = rowY + 110 + 42;
        Button build = Button.builder(
                Component.translatable("mcbaseball.gui.setup.build").withStyle(ChatFormatting.GREEN),
                b -> this.minecraft.setScreen(new BuildFieldScreen(this.pos, this))
            )
            .bounds(this.width / 2 - 154, bottom, 102, 20)
            .build();
        build.setTooltip(Tooltip.create(Component.translatable("mcbaseball.gui.setup.build.tip")));
        this.addRenderableWidget(build);
        Button auto = Button.builder(
                Component.translatable("mcbaseball.gui.setup.autodetect"), b -> this.send(FieldSetupActionPacket.Action.AUTO_DETECT, FieldMarker.HOME_PLATE)
            )
            .bounds(this.width / 2 - 50, bottom, 102, 20)
            .build();
        auto.setTooltip(Tooltip.create(Component.translatable("mcbaseball.gui.setup.autodetect.tip")));
        this.addRenderableWidget(auto);
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.back"), b -> this.minecraft.setScreen(this.parent))
                .bounds(this.width / 2 + 54, bottom, 100, 20)
                .build()
        );
        this.refreshButtons();
    }

    private void beginMark(FieldMarker m) {
        this.send(FieldSetupActionPacket.Action.BEGIN_MARK, m);
        this.onClose();
    }

    private void send(FieldSetupActionPacket.Action action, FieldMarker m) {
        ModNetwork.toServer(new FieldSetupActionPacket(this.pos, action, m.ordinal()));
    }

    private void refreshButtons() {
        FieldControllerBlockEntity be = this.controller();
        if (be != null) {
            FieldLayout layout = be.layout();

            for (FieldMarker m : FieldMarker.values()) {
                boolean has = layout.has(m);
                this.clearButtons.get(m).active = has;
                Component tip;
                if (m.multiPoint()) {
                    tip = Component.translatable("mcbaseball.gui.setup.wall_tip", new Object[]{layout.outfieldWall().size()});
                } else if (has) {
                    BlockPos p = layout.get(m);
                    tip = Component.translatable("mcbaseball.gui.setup.at", new Object[]{p.getX(), p.getY(), p.getZ()});
                } else {
                    tip = Component.translatable("mcbaseball.gui.setup.not_set");
                }

                this.setButtons.get(m).setTooltip(Tooltip.create(tip));
            }
        }
    }

    public void tick() {
        if (this.controller() == null) {
            this.onClose();
        } else {
            this.refreshButtons();
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        FieldControllerBlockEntity be = this.controller();
        int top = this.top();
        g.drawCenteredString(this.font, this.title, this.width / 2, top, 16777215);
        if (be == null) {
            super.render(g, mouseX, mouseY, partialTick);
        } else {
            g.drawCenteredString(this.font, be.statusLine(), this.width / 2, top + 14, 16777215);
            int headerY = top + 30;
            g.drawString(this.font, Component.translatable("mcbaseball.gui.setup.required").withStyle(ChatFormatting.GOLD), this.leftX(), headerY, 16777215);
            g.drawString(this.font, Component.translatable("mcbaseball.gui.setup.optional").withStyle(ChatFormatting.GRAY), this.rightX(), headerY, 16777215);
            FieldLayout layout = be.layout();
            int rowY = this.rowsTop();
            int li = 0;
            int ri = 0;

            for (FieldMarker m : FieldMarker.values()) {
                int x = m.required() ? this.leftX() : this.rightX();
                int y = rowY + (m.required() ? li++ : ri++) * 22;
                boolean has = layout.has(m);
                String mark = has ? "✔ " : (m.required() ? "✖ " : "• ");
                ChatFormatting color = has ? ChatFormatting.GREEN : (m.required() ? ChatFormatting.RED : ChatFormatting.DARK_GRAY);
                MutableComponent label = Component.literal(mark)
                    .withStyle(color)
                    .append(m.displayName().copy().withStyle(has ? ChatFormatting.WHITE : (m.required() ? ChatFormatting.WHITE : ChatFormatting.GRAY)));
                if (m.multiPoint() && has) {
                    label.append(Component.literal(" (" + layout.outfieldWall().size() + ")").withStyle(ChatFormatting.GRAY));
                }

                g.drawString(this.font, label, x + 2, y + 6, 16777215);
            }

            List<Component> lines = new ArrayList<>();
            Component missing = be.missingLine();
            if (missing != null) {
                lines.add(missing);
            }

            for (Component p : layout.problems()) {
                lines.add(p.copy().withStyle(ChatFormatting.YELLOW));
            }

            if (lines.isEmpty() && !layout.has(FieldMarker.OUTFIELD_WALL)) {
                lines.add(Component.translatable("mcbaseball.gui.setup.no_wall_hint").withStyle(ChatFormatting.DARK_GRAY));
            }

            int ly = rowY + 110 + 4;

            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                g.drawCenteredString(this.font, lines.get(i), this.width / 2, ly + i * 11, 16777215);
            }

            super.render(g, mouseX, mouseY, partialTick);
        }
    }

    public boolean isPauseScreen() {
        return false;
    }
}
