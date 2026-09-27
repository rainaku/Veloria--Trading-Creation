package com.vcoins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Shared themed button; retains vanilla focus, narration and activation behavior. */
final class VeloriaButton extends Button {
    private long pressedAt;
    private long hoverAt;
    private boolean lastHovered;
    private float hoverFrom;
    private float hoverValue;

    private VeloriaButton(int x, int y, int w, int h, Component label, OnPress press) {
        super(x, y, w, h, label, press, DEFAULT_NARRATION);
    }

    static Factory create(Component label, OnPress press) { return new Factory(label, press); }

    @Override
    public void onPress(InputWithModifiers input) {
        if (input instanceof MouseButtonEvent) pressedAt = System.nanoTime();
        super.onPress(input);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        boolean hovered = active && isHovered();
        if (hovered != lastHovered) {
            hoverFrom = hoverValue;
            hoverAt = now;
            lastHovered = hovered;
        }
        float t = Math.min(1f, (now - hoverAt) / 160_000_000f);
        hoverValue = hoverFrom + ((hovered ? 1f : 0f) - hoverFrom) * VeloriaMenuEffects.easeOut(t);
        float press = VCoinsPurchaseConfirm.isReducedMotion() ? 0f
                : 1f - VeloriaMenuEffects.easeOut(Math.min(1f, (now - pressedAt) / 160_000_000f));
        g.pose().pushMatrix();
        g.pose().scaleAround(1f - 0.03f * press, 1f - 0.03f * press, getX() + width / 2f, getY() + height / 2f);
        InventoryTextures.button(g, getX(), getY(), width, height, active, isFocused(), hoverValue);
        var font = Minecraft.getInstance().font;
        String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, width - 6));
        g.centeredText(font, Component.literal(label), getX() + width / 2, getY() + (height - 8) / 2,
                active ? 0xFFF1DFC0 : 0xFF777481);
        g.pose().popMatrix();
    }

    static final class Factory {
        private final Component label;
        private final OnPress press;
        private int x, y, w = 150, h = 20;
        private Tooltip tooltip;
        Factory(Component label, OnPress press) { this.label = label; this.press = press; }
        Factory bounds(int x, int y, int w, int h) { this.x=x; this.y=y; this.w=w; this.h=h; return this; }
        Factory tooltip(Tooltip tooltip) { this.tooltip=tooltip; return this; }
        Button build() {
            Button button = new VeloriaButton(x, y, w, h, label, press);
            button.setTooltip(tooltip);
            return button;
        }
    }
}
