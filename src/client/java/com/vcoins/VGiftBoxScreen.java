package com.vcoins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

public final class VGiftBoxScreen extends VeloriaContainerScreen<VGiftBoxMenu> {
    private final List<Button> actions = new ArrayList<>();
    private static final int[] COLORS = {0x69B3C0, 0x70C06F, 0x648BF2, 0xC069F3, 0xF8BF48};
    private final long clockOrigin = System.nanoTime();
    private long animationMillis() { return (System.nanoTime() - clockOrigin) / 1_000_000L; }
    private int revision;
    private long sent;
    private boolean pending;

    public VGiftBoxScreen(VGiftBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 354, 260);
        titleLabelX = inventoryLabelX = 1000;
    }

    @Override
    protected void init() {
        super.init();
        actions.clear();
        revision = menu.value(5);
        addRenderableWidget(VeloriaButton.create(Component.translatable("fortuna.back"), b ->
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new OpenShopPayload()))
                .bounds(leftPos + 12, topPos + 10, 48, 18).build());

        for (int i = 0; i < 5; i++) {
            final int tier = i;
            actions.add(addRenderableWidget(VeloriaButton.create(Component.translatable("vcoins.gift.buy"), b -> send(tier))
                    .bounds(leftPos + 12 + i * 67, topPos + 127, 62, 18)
                    .tooltip(Tooltip.create(Component.translatable("vcoins.gift.tier_odds", VGiftBox.odds(tier))
                            .append("\n").append(Component.translatable("vcoins.gift.pool_hint"))
                            .append("\n\n").append(Component.translatable("vcoins.gift.history." + tier + ".1"))
                            .append("\n").append(Component.translatable("vcoins.gift.history." + tier + ".2"))
                            .append("\n").append(Component.translatable("vcoins.gift.jackpot_item",
                                    new net.minecraft.world.item.ItemStack(VGiftBox.jackpot(tier)).getHoverName())))).build()));
            actions.add(addRenderableWidget(VeloriaButton.create(Component.translatable("vcoins.gift.open"), b -> send(tier + 5))
                    .bounds(leftPos + 12 + i * 67, topPos + 148, 62, 18).build()));
        }
    }

    private void send(int action) {
        if (pending || minecraft == null || minecraft.gameMode == null) return;
        pending = true;
        sent = animationMillis();
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        long now = animationMillis();
        if (menu.value(5) != revision) {
            revision = menu.value(5);
            pending = false;
        }
        if (pending && now - sent > 3000) pending = false;
        long balance = minecraft.player == null ? 0 : VCoinsState.getClientCoins(minecraft.player.getUUID());
        for (int i = 0; i < 5; i++) {
            actions.get(i * 2).visible = actions.get(i * 2 + 1).visible = true;
            actions.get(i * 2).active = !pending && balance >= VGiftBox.PRICES[i];
            actions.get(i * 2 + 1).active = !pending && menu.value(i) > 0;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractBackground(g, mx, my, delta);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF5111020);
        InventoryTextures.frame(g, leftPos, topPos, imageWidth, imageHeight, 0xFFC9A75E);
        g.centeredText(font, Component.literal("GIFT ATELIER"), leftPos + 177, topPos + 12, 0xFFF0DDAA);
        long balance = minecraft.player == null ? 0 : VCoinsState.getClientCoins(minecraft.player.getUUID());
        g.centeredText(font, Component.literal(String.format(Locale.ROOT, "%,d coins", balance)), leftPos + 177, topPos + 27, 0xFFB9B2CB);

        for (int i = 0; i < 5; i++) {
            int cx = leftPos + 43 + i * 67;
            int cy = topPos + 79;
            boolean hovered = (mx >= cx - 28 && mx <= cx + 28 && my >= cy - 35 && my <= cy + 35);

            g.centeredText(font, Component.literal("TIER " + (i + 1)), cx, topPos + 46, 0xFF000000 | COLORS[i]);
            box(g, i, cx, cy, hovered ? 1.08f : 1.0f, 0, 0);
            g.centeredText(font, Component.literal(String.format(Locale.ROOT, "%,d", VGiftBox.PRICES[i])), cx, topPos + 106, 0xFFF0DDAA);
            g.centeredText(font, Component.translatable("vcoins.gift.owned", menu.value(i)), cx, topPos + 117, 0xFFB9B2CB);
        }

        g.centeredText(font, Component.translatable("vcoins.gift.value_bands"), leftPos + 177, topPos + 179, 0xFFE0D2A2);
        g.centeredText(font, Component.translatable("vcoins.gift.tradeable"), leftPos + 177, topPos + 194, 0xFFB9B2CB);
        g.centeredText(font, Component.translatable("vcoins.gift.menu_hint"), leftPos + 177, topPos + 222, 0xFFB9B2CB);
    }

    private void box(GuiGraphicsExtractor g, int tier, float cx, float cy, float scale, float lift, float shake) {
        float pulse = VCoinsPurchaseConfirm.isReducedMotion() ? 1f : 0.85f + 0.15f * (float) Math.sin(animationMillis() * 0.003 + tier);
        VeloriaCardVfx.glow(g, cx, cy + 4, 44 * scale, 0.35f * pulse, COLORS[tier]);
        g.pose().pushMatrix();
        g.pose().translate(cx + shake, cy - lift);
        g.pose().scale(2.4f * scale, 2.4f * scale);
        var icon = new net.minecraft.world.item.ItemStack(VGiftBox.BOX_ITEMS[tier]);
        icon.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        g.item(icon, -8, -8);
        g.pose().popMatrix();
    }

}
