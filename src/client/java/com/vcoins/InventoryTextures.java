package com.vcoins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draws from the custom Veloria Royal Obsidian & Gold panel texture. */
final class InventoryTextures {
    public static final Identifier VELORIA_PANEL =
            Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/veloria_panel.png");

    private static final Identifier OBSIDIAN = Identifier.fromNamespaceAndPath("vcoins", "textures/gui/veloria_obsidian.png");
    private InventoryTextures() {}

    private record Bounds(int x, int y, int width, int height) {}
    private record UvArea(int u, int v, int width, int height) {}

    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x + 2, y + 3, x + width + 2, y + height + 3, 0x66000000);
        graphics.blit(RenderPipelines.GUI_TEXTURED, OBSIDIAN, x, y, 0f, 0f, width, height, 1, 1, 1, 1);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0x66121021);
        frame(graphics, x, y, width, height, 0xFF8A7045);
        frame(graphics, x + 2, y + 2, width - 4, height - 4, 0xFF34323E);
        for (int cornerX : new int[]{x + 1, x + width - 7}) {
            graphics.fill(cornerX, y + 1, cornerX + 6, y + 3, 0xFFE5C278);
            graphics.fill(cornerX, y + height - 3, cornerX + 6, y + height - 1, 0xFFE5C278);
        }
    }

    static void slots(GuiGraphicsExtractor graphics, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                region(graphics, new Bounds(x + column * 18 - 1, y + row * 18 - 1, 18, 18),
                        new UvArea(70, 0, 18, 18));
            }
        }
    }

    static void recess(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        region(graphics, new Bounds(x, y, width, height), new UvArea(70, 20, 12, 18));
    }

    static void scrollThumb(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean enabled) {
        int u = enabled ? 84 : 98;
        region(graphics, new Bounds(x, y, width, height), new UvArea(u, 20, 12, 15));
    }

    static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean enabled, boolean focused, float hover) {
        g.fill(x, y, x + w, y + h, enabled ? 0xFF211D30 : 0xFF181721);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, ((int)(hover * 80) << 24) | 0x39BDA6);
        frame(g, x, y, w, h, !enabled ? 0xFF44404B : focused ? 0xFF7AE5D1 : hover > 0.1f ? 0xFFD5BA79 : 0xFF826B48);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x446CD4C3);
    }

    private static void region(GuiGraphicsExtractor graphics, Bounds bounds, UvArea uv) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, VELORIA_PANEL, bounds.x(), bounds.y(), (float) uv.u(), (float) uv.v(),
                bounds.width(), bounds.height(), uv.width(), uv.height(), 256, 256);
    }
}
