package com.vcoins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VDuplicateScreen extends HandledScreen<VDuplicateScreenHandler> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/anvil.png");
    private static final int VANILLA_PANEL_WIDTH = 176;
    private static final int COST_PANEL_X = 180;
    private static final int COST_PANEL_WIDTH = 112;
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_DARK = 0xFF373737;
    private ButtonWidget duplicateButton;

    public VDuplicateScreen(VDuplicateScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 296;
        this.backgroundHeight = 166;
        this.playerInventoryTitleY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.duplicateButton = ButtonWidget.builder(Text.translatable("vcoins.duplicate.action"), button -> {
                    if (this.client != null && this.client.interactionManager != null) {
                        this.client.interactionManager.clickButton(this.handler.syncId, 0);
                    }
                })
                .dimensions(this.x + 59, this.y + 22, 110, 20)
                .build();
        this.addDrawableChild(this.duplicateButton);
        updateButtonState();
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        updateButtonState();
    }

    private void updateButtonState() {
        if (this.duplicateButton == null || this.client == null || this.client.player == null) {
            return;
        }

        ItemStack sample = this.handler.getSampleStack();
        long balance = VCoinsState.getCoins(this.client.player.getUuid());
        this.duplicateButton.active = !sample.isEmpty()
                && VCoinsPricing.isTradeable(sample.getItem())
                && balance >= VDuplicatePricing.getCoinCost(sample)
                && this.client.player.experienceLevel >= VDuplicatePricing.getExperienceLevelCost(sample);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE, this.x, this.y,
                0.0f, 0.0f, VANILLA_PANEL_WIDTH, this.backgroundHeight, 256, 256);

        int panelX = this.x + COST_PANEL_X;
        int panelY = this.y + 4;
        int panelRight = panelX + COST_PANEL_WIDTH;
        int panelBottom = this.y + 96;
        context.fill(panelX, panelY, panelRight, panelBottom, PANEL_DARK);
        context.fill(panelX + 1, panelY + 1, panelRight - 1, panelBottom - 1, PANEL_COLOR);
        context.fill(panelX + 2, panelY + 2, panelRight - 2, panelY + 3, PANEL_LIGHT);
        context.fill(panelX + 2, panelY + 2, panelX + 3, panelBottom - 2, PANEL_LIGHT);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
        context.drawText(this.textRenderer, this.playerInventoryTitle,
                this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);

        ItemStack sample = this.handler.getSampleStack();
        context.drawText(this.textRenderer, Text.translatable("vcoins.duplicate.sample_cost"),
                COST_PANEL_X + 5, 10, 0x404040, false);

        if (sample.isEmpty()) {
            context.drawText(this.textRenderer, Text.translatable("vcoins.duplicate.place_sample"),
                    8, 64, 0x777777, false);
            context.drawWrappedText(this.textRenderer, Text.translatable("vcoins.duplicate.no_sample_cost"),
                    COST_PANEL_X + 5, 27, COST_PANEL_WIDTH - 10, 0x777777, false);
            return;
        }

        long coinCost = VDuplicatePricing.getCoinCost(sample);
        int levelCost = VDuplicatePricing.getExperienceLevelCost(sample);
        boolean hasCoins = this.client != null && this.client.player != null
                && VCoinsState.getCoins(this.client.player.getUuid()) >= coinCost;
        boolean hasLevels = this.client != null && this.client.player != null
                && this.client.player.experienceLevel >= levelCost;
        String sampleName = this.textRenderer.trimToWidth(sample.getName().getString(), COST_PANEL_WIDTH - 10);

        context.drawText(this.textRenderer, sampleName, COST_PANEL_X + 5, 23, 0xFF555555, false);
        context.drawText(this.textRenderer, Text.translatable("vcoins.duplicate.coin_usage"),
                COST_PANEL_X + 5, 39, 0xFF777777, false);
        context.drawText(this.textRenderer, formatNumber(coinCost),
                COST_PANEL_X + 5, 50, hasCoins ? 0xFFE8B829 : 0xFFB02020, false);
        context.drawText(this.textRenderer, Text.translatable("vcoins.duplicate.xp_usage"),
                COST_PANEL_X + 5, 66, 0xFF777777, false);
        context.drawText(this.textRenderer, Integer.toString(levelCost),
                COST_PANEL_X + 5, 77, hasLevels ? 0xFF45B9C7 : 0xFFB02020, false);
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext context, int mouseX, int mouseY) {
        if (this.focusedSlot != null && this.focusedSlot.hasStack()) {
            ItemStack stack = this.focusedSlot.getStack();
            List<Text> tooltip = new ArrayList<>(this.getTooltipFromItem(stack));
            long buyPrice = VCoinsPricing.getPrice(stack);

            if (buyPrice > 0) {
                tooltip.add(Text.empty());
                tooltip.add(Text.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                        .formatted(Formatting.YELLOW));
            }
            if (this.focusedSlot.id == 0 || this.focusedSlot.id == 1) {
                if (buyPrice <= 0) {
                    tooltip.add(Text.empty());
                }
                tooltip.add(Text.translatable("vcoins.duplicate.coin_cost",
                        formatNumber(VDuplicatePricing.getCoinCost(stack))).formatted(Formatting.YELLOW));
                tooltip.add(Text.translatable("vcoins.duplicate.xp_cost",
                        VDuplicatePricing.getExperienceLevelCost(stack)).formatted(Formatting.AQUA));
                if (this.focusedSlot.id == 1) {
                    tooltip.add(Text.translatable("vcoins.duplicate.preview").formatted(Formatting.GRAY));
                }
            }
            context.drawTooltip(this.textRenderer, tooltip, stack.getTooltipData(), mouseX, mouseY);
            return;
        }
        super.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
