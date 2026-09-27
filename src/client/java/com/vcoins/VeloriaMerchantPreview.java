package com.vcoins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A menu-only model, never added to the world or sent to the server. */
final class VeloriaMerchantPreview {
    // Preview entities never enter a level, so normal spawn/network ID assignment
    // does not run. Give each preview a nonzero, client-local negative ID instead.
    private static final java.util.concurrent.atomic.AtomicInteger PREVIEW_IDS =
            new java.util.concurrent.atomic.AtomicInteger(-1);
    private WanderingTrader merchant;
    private long lastFrame;
    private float yaw, pitch;

    void drawBehindMenu(GuiGraphicsExtractor graphics, int left, int top, int screenHeight,
                        int mouseX, int mouseY) {
        var client = Minecraft.getInstance();
        if (client.level == null || left < 4) return;
        if (merchant == null || merchant.level() != client.level) {
            merchant = EntityTypes.WANDERING_TRADER.create(client.level, EntitySpawnReason.COMMAND);
            if (merchant == null) return;
            merchant.setId(PREVIEW_IDS.getAndDecrement());
            merchant.setNoAi(true);
            merchant.setSilent(true);
            merchant.addTag("veloria_menu_preview");
            lastFrame = 0;
            yaw = pitch = 0;
        }

        // The feet and most of the torso sit behind the menu; the tilted head peeks left.
        int scale = Math.min(54, Math.max(28, left));
        int centerX = left - Math.min(10, left / 3);
        int centerY = top + 28 + scale;
        int halfWidth = scale + 12;
        int halfHeight = scale + 14;
        float targetYaw = (float) Math.toDegrees(Math.atan2(mouseX - (left - 16), 180.0));
        float targetPitch = (float) Math.toDegrees(Math.atan2(mouseY - (top + 44), 180.0));
        targetYaw = Math.clamp(targetYaw, -35f, 35f);
        targetPitch = Math.clamp(targetPitch, -25f, 25f);
        updateLook(targetYaw, targetPitch);

        // Extract a fresh state, following vanilla inventory rendering without mutating a world entity.
        merchant.tickCount = (int) ((System.nanoTime() / 50_000_000L) % 1_000_000L);
        merchant.setYHeadRot(180f - yaw);
        merchant.setXRot(pitch);
        var renderer = VeloriaPreviewRenderers.get();
        if (renderer == null) return;
        var state = renderer.createRenderState(merchant, 1f);
        VeloriaPreviewRenderers.states.add(state);
        state.ageInTicks = (System.nanoTime() % 1_000_000_000_000L) / 50_000_000f;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.nameTag = null;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180f;
            living.yRot = -yaw;
            living.xRot = pitch;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1f;
        }
        // Rotate the model itself, not the GUI rectangle: a real sideways lean.
        Quaternionf lean = new Quaternionf().rotateZ((float) Math.PI - 0.35f);
        Vector3f center = new Vector3f(0, state.boundingBoxHeight / 2f, 0);
        // Explicit clipping keeps the model behind the menu even when GUI batching changes.
        graphics.enableScissor(0, 0, left, screenHeight);
        try {
            graphics.entity(state, scale, center, lean, new Quaternionf(),
                    centerX - halfWidth, centerY - halfHeight,
                    centerX + halfWidth, centerY + halfHeight);
        } finally {
            graphics.disableScissor();
        }
    }

    private void updateLook(float targetYaw, float targetPitch) {
        long now = System.nanoTime();
        if (VCoinsPurchaseConfirm.isReducedMotion()) {
            yaw = pitch = 0;
            lastFrame = now;
            return;
        }
        float elapsed = lastFrame == 0 ? 1f : Math.min(0.25f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        // Frame-rate-independent 70ms follow; no slow spring wind-up or substep loop.
        float blend = (float) (1 - Math.exp(-elapsed / 0.07f));
        yaw += (targetYaw - yaw) * blend;
        pitch += (targetPitch - pitch) * blend;
    }
}
