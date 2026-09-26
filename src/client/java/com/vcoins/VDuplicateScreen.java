package com.vcoins;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class VDuplicateScreen extends AbstractContainerScreen<VDuplicateScreenHandler> {
    private static final int VANILLA_PANEL_WIDTH = 176;
    private static final int COST_PANEL_X = 180;
    private static final int COST_PANEL_WIDTH = 112;
    private Button duplicateButton;

    public VDuplicateScreen(VDuplicateScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 296, 166);
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.duplicateButton = Button.builder(Component.translatable("vcoins.duplicate.action"), button -> {
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
                    }
                })
                .bounds(this.leftPos + 59, this.topPos + 22, 110, 20)
                .build();
        this.addRenderableWidget(this.duplicateButton);
        updateButtonState();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtonState();
    }

    private void updateButtonState() {
        if (this.duplicateButton == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }

        ItemStack sample = this.menu.getSampleStack();
        long balance = VCoinsState.getCoins(this.minecraft.player.getUUID());
        this.duplicateButton.active = !sample.isEmpty()
                && VCoinsPricing.isTradeable(sample.getItem())
                && balance >= VDuplicatePricing.getCoinCost(sample)
                && this.minecraft.player.experienceLevel >= VDuplicatePricing.getExperienceLevelCost(sample);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractBackground(extractor, mouseX, mouseY, delta);
        InventoryTextures.panel(extractor, this.leftPos, this.topPos, VANILLA_PANEL_WIDTH, this.imageHeight);
        InventoryTextures.slots(extractor, this.leftPos + 27, this.topPos + 47, 1, 1);
        InventoryTextures.slots(extractor, this.leftPos + 134, this.topPos + 47, 1, 1);
        InventoryTextures.slots(extractor, this.leftPos + 8, this.topPos + 84, 9, 3);
        InventoryTextures.slots(extractor, this.leftPos + 8, this.topPos + 142, 9, 1);
        InventoryTextures.panel(extractor, this.leftPos + COST_PANEL_X, this.topPos + 4, COST_PANEL_WIDTH, 92);
    }
    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        extractor.text(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        ItemStack sample = this.menu.getSampleStack();
        extractor.text(this.font, Component.translatable("vcoins.duplicate.sample_cost"),
                COST_PANEL_X + 5, 10, 0x404040, false);

        if (sample.isEmpty()) {
            extractor.text(this.font, Component.translatable("vcoins.duplicate.place_sample"),
                    8, 64, 0x777777, false);
            extractor.textWithWordWrap(this.font, Component.translatable("vcoins.duplicate.no_sample_cost"),
                    COST_PANEL_X + 5, 27, COST_PANEL_WIDTH - 10, 0x777777);
            return;
        }

        long coinCost = VDuplicatePricing.getCoinCost(sample);
        int levelCost = VDuplicatePricing.getExperienceLevelCost(sample);
        boolean hasCoins = this.minecraft != null && this.minecraft.player != null
                && VCoinsState.getCoins(this.minecraft.player.getUUID()) >= coinCost;
        boolean hasLevels = this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.experienceLevel >= levelCost;
        String sampleName = this.font.plainSubstrByWidth(sample.getHoverName().getString(), COST_PANEL_WIDTH - 10);

        extractor.text(this.font, sampleName, COST_PANEL_X + 5, 23, 0xFF555555, false);
        extractor.text(this.font, Component.translatable("vcoins.duplicate.coin_usage"),
                COST_PANEL_X + 5, 39, 0xFF777777, false);
        extractor.text(this.font, formatNumber(coinCost),
                COST_PANEL_X + 5, 50, hasCoins ? 0xFFE8B829 : 0xFFB02020, false);
        extractor.text(this.font, Component.translatable("vcoins.duplicate.xp_usage"),
                COST_PANEL_X + 5, 66, 0xFF777777, false);
        extractor.text(this.font, Integer.toString(levelCost),
                COST_PANEL_X + 5, 77, hasLevels ? 0xFF45B9C7 : 0xFFB02020, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack stack = this.hoveredSlot.getItem();
            List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(stack));
            long buyPrice = VCoinsPricing.getPrice(stack);

            if (buyPrice > 0) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                        .withStyle(ChatFormatting.YELLOW));
            }
            if (this.hoveredSlot.index == 0 || this.hoveredSlot.index == 1) {
                if (buyPrice <= 0) {
                    tooltip.add(Component.empty());
                }
                tooltip.add(Component.translatable("vcoins.duplicate.coin_cost",
                        formatNumber(VDuplicatePricing.getCoinCost(stack))).withStyle(ChatFormatting.YELLOW));
                tooltip.add(Component.translatable("vcoins.duplicate.xp_cost",
                        VDuplicatePricing.getExperienceLevelCost(stack)).withStyle(ChatFormatting.AQUA));
                if (this.hoveredSlot.index == 1) {
                    tooltip.add(Component.translatable("vcoins.duplicate.preview").withStyle(ChatFormatting.GRAY));
                }
            }
            extractor.setTooltipForNextFrame(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
            return;
        }
        super.extractTooltip(extractor, mouseX, mouseY);
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
