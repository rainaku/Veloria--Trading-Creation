package com.vcoins;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import java.util.ArrayList;
import java.util.List;

/** All decorative timing is client-side; only confirmed server state changes the rewards. */
public final class VFortunaScreen extends VeloriaContainerScreen<VFortunaMenu> {
    private static final Identifier TABLE = Identifier.fromNamespaceAndPath("vcoins", "textures/gui/fortuna_table.png");
    private static final Identifier[] CARD_BACKS = {
            Identifier.fromNamespaceAndPath("vcoins", "textures/gui/card_back.png"),
            Identifier.fromNamespaceAndPath("vcoins", "textures/gui/card_legend_back.png"),
            Identifier.fromNamespaceAndPath("vcoins", "textures/gui/card_mythic_back.png")};
    private static final Identifier[] CARD_FRONTS = {
            Identifier.fromNamespaceAndPath("vcoins", "textures/gui/card_front.png"),
            Identifier.fromNamespaceAndPath("vcoins", "textures/gui/card_legend_front.png"),
            Identifier.fromNamespaceAndPath("vcoins", "textures/gui/card_mythic_front.png")};
    private static final int CARD_W = 48, CARD_H = 60;
    private final List<Button> starts = new ArrayList<>(), redeem = new ArrayList<>();
    private Button risk, cash, charm;
    private int revision = -1;
    private long effectStart, sentAt;
    private boolean pending;
    private boolean outcomeSound;
    private int selectedCharm;
    private Button start;

    public VFortunaScreen(VFortunaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 354, 288);
        titleLabelX = inventoryLabelX = 1000;
    }
    private Button button(String key, int x, int y, int w, Runnable action) {
        return addRenderableWidget(VeloriaButton.create(Component.translatable(key), b -> action.run())
                .bounds(leftPos + x, topPos + y, w, 18).build());
    }
    @Override protected void init() {
        super.init(); starts.clear(); redeem.clear();
        button("fortuna.back", 16, 12, 48, () -> ClientPlayNetworking.send(new OpenShopPayload()));
        for (int i = 0; i < 3; i++) {
            final int index = i;
            Button b = button("fortuna.charm." + i, 18 + i * 107, 193, 103, () -> selectedCharm = index);
            b.setTooltip(Tooltip.create(Component.translatable("fortuna.charm.tip." + i)));
            starts.add(b);
        }
        start = button("fortuna.start", 73, 217, 208, () -> send(selectedCharm));
        risk = button("fortuna.risk", 183, 217, 153, () -> send(3));
        cash = button("fortuna.cash", 18, 217, 153, () -> send(4));
        charm = button("fortuna.use", 93, 193, 168, () -> send(5));
        for (int i = 0; i < 3; i++) {
            final int action = 6 + i;
            redeem.add(button("fortuna.redeem." + i, 18 + i * 107, 255, 103, () -> send(action)));
        }
        button("fortuna.skip", 287, 12, 49, () -> { effectStart = 0; updateButtons(); });
        start.setTooltip(Tooltip.create(Component.translatable("fortuna.rules")));
        for (Button b : redeem) b.setTooltip(Tooltip.create(Component.translatable("fortuna.redeem.tip")));
        updateButtons();
    }
    private void send(int action) {
        if (pending || minecraft == null || minecraft.gameMode == null) return;
        pending = true; sentAt = System.currentTimeMillis();
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
        updateButtons();
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (menu.value(7) != revision) {
            if (revision >= 0) {
                effectStart = System.currentTimeMillis();
                outcomeSound = true;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f + menu.value(1) * 0.12f));
            }
            revision = menu.value(7); pending = false;
        }
        if (pending && System.currentTimeMillis() - sentAt > 1500) pending = false;
        if (outcomeSound && (VCoinsPurchaseConfirm.isReducedMotion() || System.currentTimeMillis() - effectStart >= 550)) {
            outcomeSound = false;
            var sound = switch (menu.value(6)) {
                case 2 -> SoundEvents.PLAYER_LEVELUP;
                case 3 -> SoundEvents.GLASS_BREAK;
                case 4, 6 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
                default -> SoundEvents.AMETHYST_BLOCK_CHIME;
            };
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 0.9f + menu.value(1) * 0.12f));
        }
        updateButtons();
    }
    private void updateButtons() {
        boolean active = menu.value(0) == 1;
        boolean ready = !pending && System.currentTimeMillis() - sentAt >= 550
                && (VCoinsPurchaseConfirm.isReducedMotion() || System.currentTimeMillis() - effectStart >= 700);
        for (int i = 0; i < starts.size(); i++) {
            Button b = starts.get(i); b.visible = !active; b.active = ready;
            b.setMessage(Component.literal(i == selectedCharm ? "◆ " : "").append(Component.translatable("fortuna.charm." + i)));
        }
        start.visible = !active;
        start.setMessage(Component.translatable("fortuna.start", String.format(java.util.Locale.ROOT, "%,d", menu.entryPrice())));
        start.active = ready && menu.entryPrice() > 0 && minecraft.player != null && VCoinsState.getClientCoins(minecraft.player.getUUID()) >= menu.entryPrice();
        risk.visible = cash.visible = charm.visible = active;
        risk.active = ready && menu.value(1) < 5;
        cash.active = ready;
        charm.active = ready && menu.value(3) == 0 && menu.value(1) < 5;
        charm.setMessage(Component.translatable(menu.value(3) == 1 ? "fortuna.used" : "fortuna.charm." + menu.value(2)));
        charm.setTooltip(Tooltip.create(Component.translatable("fortuna.charm.tip." + menu.value(2))));
        for (Button b : redeem) b.active = ready && !active && menu.value(5) >= VFortuna.REDEEM;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        int x = leftPos, y = topPos, tier = menu.value(1);
        boolean active = menu.value(0) == 1;
        g.blit(RenderPipelines.GUI_TEXTURED, TABLE, x, y, 0f, 0f, imageWidth, imageHeight, 1536, 1024, 1536, 1024);
        g.centeredText(font, Component.translatable("fortuna.title"), x + 177, y + 17, 0xFFFFDA8A);
        long balance = minecraft.player == null ? 0 : VCoinsState.getClientCoins(minecraft.player.getUUID());
        g.centeredText(font, Component.translatable("fortuna.balance", String.format(java.util.Locale.ROOT, "%,d", balance)), x + 177, y + 34, 0xFFCDC2D9);

        // 5 Phase Gates at the top
        int bw = 60, bh = 17, gap = 6;
        int totalW = 5 * bw + 4 * gap;
        int startX = x + (imageWidth - totalW) / 2;
        for (int i = 0; i < 5; i++) {
            int bx = startX + i * (bw + gap);
            boolean isGate5 = (i == 4);
            int color;
            if (tier > i && active) {
                color = isGate5 ? 0xFFFF4500 : 0xFFF4D582;
            } else if (tier == i && active) {
                color = isGate5 ? 0xFFFFD700 : 0xFFE0C475;
            } else {
                color = isGate5 ? 0xFF9C4A4A : 0xFF847393;
            }
            g.fill(bx, y + 51, bx + bw, y + 51 + bh, isGate5 && tier >= 4 && active ? 0xDD2A0808 : 0xBB1D142B);
            InventoryTextures.frame(g, bx, y + 51, bw, bh, color);
            String gateText = isGate5 ? ("✦ 5·" + FortunaRun.CHANCES[i] + "%") : ((i + 1) + " · " + FortunaRun.CHANCES[i] + "%");
            g.centeredText(font, Component.literal(gateText), bx + bw / 2, y + 56, color);
        }

        // Left Card (Your Stake) and Right Card (Next Reward)
        card(g, x + 30, y + 76, tier, "fortuna.current", active);
        card(g, x + 236, y + 76, Math.min(5, tier + 1), "fortuna.next", active && tier < 5);

        // Center Altar & Astrolabe VFX
        double seconds = System.currentTimeMillis() / 1000.0;
        boolean motion = !VCoinsPurchaseConfirm.isReducedMotion();
        long elapsed = System.currentTimeMillis() - effectStart;
        renderCenterAltar(g, x + 177, y + 118, tier, active, elapsed, motion, seconds);

        if (active && menu.value(4) > 0) g.centeredText(font, Component.translatable("fortuna.anchored", menu.value(4) - 1), x + 177, y + 164, 0xFF89E0CE);
        else if (active && menu.value(2) == 1 && menu.value(3) == 1 && tier < 4) {
            var future = menu.preview(tier + 2);
            g.centeredText(font, Component.literal(font.plainSubstrByWidth(Component.translatable("fortuna.future", future.getHoverName()).getString(), 310)), x + 177, y + 164, 0xFF89E0CE);
        }
        Component status = Component.translatable(active ? (tier == 5 ? "fortuna.final" : "fortuna.odds") : "fortuna.result." + menu.value(6), active && tier < 5 ? FortunaRun.CHANCES[tier] : 0);
        g.centeredText(font, status, x + 177, y + 181, menu.value(6) == 3 ? 0xFFFF9B9B : (tier == 5 ? 0xFFFFD700 : 0xFFE5D2A7));
        g.centeredText(font, Component.translatable("fortuna.fragments", menu.value(5), VFortuna.REDEEM), x + 177, y + 243, 0xFFAFA0C8);
    }

    private void renderCenterAltar(GuiGraphicsExtractor g, int cx, int cy, int tier, boolean active, long elapsed, boolean motion, double time) {
        boolean mythic = tier >= 4;
        int tierIdx = mythic ? 1 : 0;
        int primaryColor = mythic ? VeloriaCardVfx.MYTHIC_FLAME : VeloriaCardVfx.GOLD_PRIMARY;
        int lightColor = mythic ? VeloriaCardVfx.MYTHIC_SEARING : VeloriaCardVfx.GOLD_LIGHT;

        // 1. Celestial Astrolabe & Orbit Rings behind the deck
        if (motion) {
            float pulse = 0.85f + 0.15f * (float) Math.sin(time * 2.2);
            VeloriaCardVfx.glow(g, cx, cy, 78f * pulse, (active ? 0.35f : 0.20f), primaryColor);

            // Subtle pulsing sunburst rays
            VeloriaCardVfx.layer(g, VeloriaCardVfx.SUN, cx, cy, 96f, 96f, (float) (time * 0.12), (active ? 0.28f : 0.15f), lightColor, 256);

            // Counter-rotating outer and inner celestial orbital rings
            VeloriaCardVfx.layer(g, VeloriaCardVfx.ORBIT[tierIdx], cx, cy, 76f, 76f, (float) (time * 0.40), (active ? 0.65f : 0.40f), lightColor, 256);
            VeloriaCardVfx.layer(g, VeloriaCardVfx.ORBIT[tierIdx], cx, cy, 54f, 54f, (float) (-time * 0.65), (active ? 0.50f : 0.30f), primaryColor, 256);

            // 4-pointed sacred star rotating in center
            VeloriaCardVfx.coreStar(g, cx, cy, 32f, (float) (time * 0.50), active ? 0.85f : 0.45f, lightColor, mythic);
        }

        // 2. 3D Stacked Gilded Tarot Deck on the Altar
        int deckW = 32, deckH = 42;
        int deckX = cx - deckW / 2;
        int deckY = cy - deckH / 2;
        int deckStyle = tier >= 5 ? 2 : (tier >= 3 ? 1 : 0);

        // 3D Deck Shadow & Card Edge Thickness
        g.fill(deckX + 3, deckY + 3, deckX + deckW + 3, deckY + deckH + 3, 0x66000000);
        g.fill(deckX + 2, deckY + 2, deckX + deckW + 2, deckY + deckH + 2, 0x881A0E2B);
        g.fill(deckX + 1, deckY + 1, deckX + deckW + 1, deckY + deckH + 1, 0xCC2B1B40);

        // Top Tarot Card of the Deck
        g.blit(RenderPipelines.GUI_TEXTURED, CARD_BACKS[deckStyle], deckX, deckY, 0f, 0f, deckW, deckH, deckW, deckH);
        drawCardOutline(g, deckX, deckY, deckW, deckH, tier >= 5 ? 0xFFFF3A14 : (tier >= 3 ? 0xFFFFD700 : 0xFFC8A565));

        // Animated Golden / Cosmic Runic Seal on deck
        if (motion) {
            float sealPulse = 0.70f + 0.30f * (float) Math.sin(time * 3.0);
            VeloriaCardVfx.spark(g, cx, cy, 14f, (float) (time * 0.8), sealPulse * (active ? 0.90f : 0.60f), 0xFFFFFF, mythic);

            // Ambient rising stardust motes off the altar
            for (int i = 0; i < 5; i++) {
                double phase = time * 0.28 + i * 0.61803398875;
                float p = (float) (phase - Math.floor(phase));
                float px = cx + (float) Math.sin(time * 1.5 + i * 2) * 22f;
                float py = cy + 18f - p * 42f;
                float alpha = (float) Math.sin(p * Math.PI) * (active ? 0.75f : 0.45f);
                VeloriaCardVfx.spark(g, px, py, 2.5f, (float) (time * 2.0 + i), alpha, primaryColor, mythic);
            }
        }

        // 3. Dynamic Roll / Action Burst VFX
        if (motion && elapsed >= 0 && elapsed < 900) {
            float t = Math.clamp(elapsed / 900f, 0f, 1f);
            float expand = t * t * (3 - 2 * t);
            float alpha = (1f - t) * (1f - t);

            int result = menu.value(6);
            if (result == 2) { // WON / GATE ADVANCED
                float waveSize = 24f + expand * 160f;
                VeloriaCardVfx.layer(g, VeloriaCardVfx.SHOCKWAVE[tierIdx], cx, cy, waveSize, waveSize * 0.75f, 0f, alpha * 0.90f, lightColor, 256);
                VeloriaCardVfx.layer(g, VeloriaCardVfx.SUN, cx, cy, 120f * (1f + t * 0.5f), 120f * (1f + t * 0.5f), (float) (elapsed * 0.003), alpha * 0.75f, lightColor, 256);
                for (int i = 0; i < 24; i++) {
                    double angle = i * Math.PI / 12.0;
                    float speed = 18f + expand * (50f + (i % 3) * 30f);
                    float sx = cx + (float) Math.cos(angle) * speed;
                    float sy = cy + (float) Math.sin(angle) * (speed * 0.65f);
                    VeloriaCardVfx.spark(g, sx, sy, 5f, (float) (angle + time * 3), alpha, lightColor, mythic);
                }
            } else if (result == 3) { // LOST / PACT SHATTERED
                float waveSize = 24f + expand * 130f;
                VeloriaCardVfx.layer(g, VeloriaCardVfx.SHOCKWAVE[1], cx, cy, waveSize, waveSize * 0.75f, 0f, alpha * 0.85f, 0xFF2200, 256);
                for (int i = 0; i < 20; i++) {
                    double angle = i * Math.PI / 10.0 + 0.15;
                    float speed = 12f + expand * (45f + (i % 4) * 20f);
                    float sx = cx + (float) Math.cos(angle) * speed;
                    float sy = cy + (float) Math.sin(angle) * (speed * 0.65f) + expand * expand * 16f;
                    VeloriaCardVfx.spark(g, sx, sy, 4f, (float) (angle + time * 4), alpha * 0.90f, (i % 2 == 0 ? 0xFFFF2200 : 0xFFAA0033), true);
                }
            } else if (result == 4 || result == 6) { // CLAIMED / CASHED OUT
                for (int i = 0; i < 16; i++) {
                    double p = (t + i * 0.0625) % 1.0;
                    float sx = cx + (float) Math.sin(p * Math.PI * 4 + i) * 28f;
                    float sy = cy + 20f - (float) p * 60f;
                    float a = (float) Math.sin(p * Math.PI) * (1f - t);
                    VeloriaCardVfx.spark(g, sx, sy, 3.5f, (float) (time * 2.0), a, VeloriaCardVfx.GOLD_LIGHT, false);
                }
            }
        }
    }

    private static void drawCardOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void card(GuiGraphicsExtractor g, int x, int y, int tier, String label, boolean show) {
        long elapsed = System.currentTimeMillis() - effectStart;
        boolean flip = !VCoinsPurchaseConfirm.isReducedMotion() && elapsed >= 0 && elapsed < 700;
        int cardX = x + (88 - CARD_W) / 2, cardY = y + 12;
        int style = tier >= 5 ? 2 : (tier >= 3 ? 1 : 0);
        g.centeredText(font, Component.translatable(label), x + 44, y, 0xFFCCC0D8);
        g.pose().pushMatrix();
        if (flip) {
            float scale = Math.max(0.05f, Math.abs((float)Math.cos(elapsed / 700.0 * Math.PI)));
            g.pose().scaleAround(scale, 1f, cardX + CARD_W / 2f, cardY + CARD_H / 2f);
            if (elapsed < 350) show = false;
        }
        // Same portrait artwork and 4:5 proportions as the official Black Market deck.
        g.blit(RenderPipelines.GUI_TEXTURED, show ? CARD_FRONTS[style] : CARD_BACKS[style],
                cardX, cardY, 0f, 0f, CARD_W, CARD_H, CARD_W, CARD_H);
        int borderColor = style == 2 ? 0xD0FF2E14 : (style == 1 ? 0xD0FFD700 : 0xC0C8A870);
        drawCardOutline(g, cardX, cardY, CARD_W, CARD_H, borderColor);
        if (show) {
            var stack = menu.preview(tier);
            g.item(stack, cardX + 16, cardY + 22);
            g.itemDecorations(font, stack, cardX + 16, cardY + 22);
        }
        g.pose().popMatrix();
        if (show && style >= 1) {
            double time = System.currentTimeMillis() / 1000.0;
            VeloriaCardVfx.ambient(g, cardX, cardY, time, 0.90f, 0f, false, style == 2);
        }
        if (show) g.centeredText(font,
                Component.literal(font.plainSubstrByWidth(menu.preview(tier).getHoverName().getString(), 98)),
                x + 44, cardY + CARD_H + 3, 0xFFE4D6B7);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {}
    @Override protected void extractTooltip(GuiGraphicsExtractor g, int mx, int my) {
        if (menu.value(0) != 1 || my < topPos + 88 || my >= topPos + 148) return;
        int tier = menu.value(1);
        if (mx >= leftPos + 256 && mx < leftPos + 304 && tier < 5) tier++;
        else if (!(mx >= leftPos + 50 && mx < leftPos + 98)) return;
        var stack = menu.preview(tier);
        g.setTooltipForNextFrame(font, getTooltipFromContainerItem(stack), stack.getTooltipImage(), mx, my,
                stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
    }
}
