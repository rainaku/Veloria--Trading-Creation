package com.vcoins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Draws directly from the active resource pack's vanilla inventory texture. */
final class InventoryTextures {
    private static final Identifier INVENTORY =
            Identifier.withDefaultNamespace("textures/gui/container/inventory.png");

    private InventoryTextures() {}

    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        // Stretch only blank interior and straight edges; keep the four corners intact.
        region(graphics, x + 4, y + 4, width - 8, height - 8, 160, 5, 8, 8);
        region(graphics, x + 4, y, width - 8, 4, 4, 0, 168, 4);
        region(graphics, x + 4, y + height - 4, width - 8, 4, 4, 162, 168, 4);
        region(graphics, x, y + 4, 4, height - 8, 0, 4, 4, 158);
        region(graphics, x + width - 4, y + 4, 4, height - 8, 172, 4, 4, 158);
        region(graphics, x, y, 4, 4, 0, 0, 4, 4);
        region(graphics, x + width - 4, y, 4, 4, 172, 0, 4, 4);
        region(graphics, x, y + height - 4, 4, 4, 0, 162, 4, 4);
        region(graphics, x + width - 4, y + height - 4, 4, 4, 172, 162, 4, 4);
    }

    static void slots(GuiGraphicsExtractor graphics, int x, int y, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                region(graphics, x + column * 18 - 1, y + row * 18 - 1,
                        18, 18, 7 + column * 18, 83 + (row % 3) * 18, 18, 18);
            }
        }
    }

    static void recess(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        region(graphics, x, y, width, height, 7, 83, 18, 18);
    }

    private static void region(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY, x, y, (float) u, (float) v,
                width, height, sourceWidth, sourceHeight, 256, 256);
    }
}
