package com.vcoins;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Shared screen-space compatibility for Spatial GUI's independent GUI scale. */
abstract class VeloriaContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private static final boolean SPATIAL_GUI = FabricLoader.getInstance().isModLoaded("spatial-gui");

    protected VeloriaContainerScreen(T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title, width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (SPATIAL_GUI) {
            // Spatial GUI changes Window's scaled dimensions during isolated extraction.
            // A screen opened while switching menus can have been initialized at a different scale.
            // Repair before vanilla calculates hovered slots or draws widgets/tooltips.
            int viewportWidth = graphics.guiWidth();
            int viewportHeight = graphics.guiHeight();
            if (viewportWidth > 0 && viewportHeight > 0
                    && (this.width != viewportWidth || this.height != viewportHeight)) {
                int newLeft = (viewportWidth - this.imageWidth) / 2;
                int newTop = (viewportHeight - this.imageHeight) / 2;
                int dx = newLeft - this.leftPos;
                int dy = newTop - this.topPos;
                this.width = viewportWidth;
                this.height = viewportHeight;
                this.leftPos = newLeft;
                this.topPos = newTop;
                // Shift existing widgets instead of rebuilding: preserve search, focus and confirmations.
                for (var child : this.children()) {
                    if (child instanceof AbstractWidget widget) {
                        widget.setX(widget.getX() + dx);
                        widget.setY(widget.getY() + dy);
                    }
                }
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}
