package com.vcoins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Decoration only: never delays inventory input or predicts a successful purchase. */
final class VeloriaMenuEffects {
    private static long balanceAt;
    private static long categoryAt;

    private VeloriaMenuEffects() {}

    static void balanceChanged(long before, long after) {
        if (before == after) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var menu = player.containerMenu;
        if (!(menu instanceof VTradeScreenHandler || menu instanceof VBlackMarketScreenHandler || menu instanceof VDuplicateScreenHandler)) return;
        balanceAt = System.nanoTime();
    }

    static void categoryChanged() { categoryAt = System.nanoTime(); }

    // cubic-bezier(0.23, 1, 0.32, 1): shared 160–250ms feedback curve.
    static float easeOut(float progress) {
        if (progress <= 0) return 0;
        if (progress >= 1) return 1;
        double lo = 0, hi = 1;
        for (int i = 0; i < 16; i++) {
            double t = (lo + hi) / 2, u = 1 - t;
            double x = 3*u*u*t*0.23 + 3*u*t*t*0.32 + t*t*t;
            if (x < progress) lo = t; else hi = t;
        }
        double t = (lo + hi) / 2;
        return (float) (1 - Math.pow(1 - t, 3));
    }

    static void draw(GuiGraphicsExtractor g, int left, int top, int width, int height) {
        long now = System.nanoTime();
        float feedback = Math.max(0, 1f - (now - balanceAt) / 250_000_000f);
        float category = Math.max(0, 1f - (now - categoryAt) / 160_000_000f);
        if (feedback > 0 || category > 0) {
            int alpha = (int) (Math.max(feedback, category) * 180);
            g.fill(left + 8, top + 2, left + width - 8, top + 3, (alpha << 24) | 0x55E6CA);
        }
    }
}
