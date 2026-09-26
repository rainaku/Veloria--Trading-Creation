package com.vcoins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draws from the custom Veloria Royal Obsidian & Gold panel texture. */
final class InventoryTextures {
    public static final Identifier VELORIA_PANEL =
            Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/veloria_panel.png");

    private InventoryTextures() {}

    private record Bounds(int x, int y, int width, int height) {}
    private record UvArea(int u, int v, int width, int height) {}

    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        // Stretch blank interior and straight edges; keep 8x8 corners intact.
        region(graphics, new Bounds(x + 8, y + 8, width - 16, height - 16), new UvArea(8, 8, 48, 48));
        region(graphics, new Bounds(x + 8, y, width - 16, 8), new UvArea(8, 0, 48, 8));
        region(graphics, new Bounds(x + 8, y + height - 8, width - 16, 8), new UvArea(8, 56, 48, 8));
        region(graphics, new Bounds(x, y + 8, 8, height - 16), new UvArea(0, 8, 8, 48));
        region(graphics, new Bounds(x + width - 8, y + 8, 8, height - 16), new UvArea(56, 8, 8, 48));
        region(graphics, new Bounds(x, y, 8, 8), new UvArea(0, 0, 8, 8));
        region(graphics, new Bounds(x + width - 8, y, 8, 8), new UvArea(56, 0, 8, 8));
        region(graphics, new Bounds(x, y + height - 8, 8, 8), new UvArea(0, 56, 8, 8));
        region(graphics, new Bounds(x + width - 8, y + height - 8, 8, 8), new UvArea(56, 56, 8, 8));
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

    private static void region(GuiGraphicsExtractor graphics, Bounds bounds, UvArea uv) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, VELORIA_PANEL, bounds.x(), bounds.y(), (float) uv.u(), (float) uv.v(),
                bounds.width(), bounds.height(), uv.width(), uv.height(), 256, 256);
    }
}
