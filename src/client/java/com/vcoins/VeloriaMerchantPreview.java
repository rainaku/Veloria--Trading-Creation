package com.vcoins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Renders the 2D Veloria Merchant artwork beside the main trade menu.
 * Uses native 2D GUI blitting for full compatibility with Spatial GUI and VR mods
 * (no 3D entity world-space projection or depth conflicts).
 */
final class VeloriaMerchantPreview {
    private static final Identifier MERCHANT_IMAGE =
            Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/veloria_merchant.png");

    void drawBehindMenu(GuiGraphicsExtractor graphics, int left, int top, int screenHeight,
                        int mouseX, int mouseY) {
        if (left < 30) {
            return;
        }

        // Scale artwork according to available screen space on the left
        int width = Math.min(130, Math.max(50, left - 10));
        int height = (width * 3) / 2; // 2:3 aspect ratio matching the 1024x1536 artwork
        if (height > screenHeight - 16) {
            height = screenHeight - 16;
            width = (height * 2) / 3;
        }

        int x = left - width - 6;
        int y = top + Math.max(0, (250 - height) / 2);

        // Fixed position: no vertical bobbing/jitter during player operations
        graphics.blit(RenderPipelines.GUI_TEXTURED, MERCHANT_IMAGE,
                x, y, 0f, 0f, width, height, 1, 1, 1, 1);
    }
}
