package com.vcoins;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VDuplicateScreen extends VeloriaContainerScreen<VDuplicateScreenHandler> {
    private final VeloriaMerchantPreview merchantPreview = new VeloriaMerchantPreview();

    private VCoinsPurchaseConfirm purchaseConfirm = new VCoinsPurchaseConfirm();
    private Button backToShopButton;
    private Button duplicateActionButton;
    private Button verifyToggleButton;
    private Button shopTabButton;
    private Button blackMarketTabButton;
    private Button duplicateTabButton;

    private int selectedDuplicateSlot = -1;
    private ItemStack selectedDuplicateStack = ItemStack.EMPTY;

    public VDuplicateScreen(VDuplicateScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 236, 250);
        this.inventoryLabelX = 1000;
        this.titleLabelX = 1000;
    }

    @Override
    protected void init() {
        super.init();

        // Nút quay lại Cửa hàng chính
        this.backToShopButton = this.addRenderableWidget(VeloriaButton.create(
                Component.literal("◀ Mua"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenShopPayload());
                })
                .bounds(this.leftPos + 14, this.topPos + 6, 38, 14)
                .build());

        // Nút Nhân Bản Vật Phẩm
        this.duplicateActionButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.duplicate.action"),
                button -> executeDuplicate())
                .bounds(this.leftPos + 68, this.topPos + 102, 100, 18)
                .build());

        // Công tắc bật/tắt xác minh giao dịch
        this.verifyToggleButton = this.addRenderableWidget(VeloriaButton.create(
                VCoinsPurchaseConfirm.getCompactToggleLabel(),
                button -> {
                    boolean enabled = VCoinsPurchaseConfirm.toggleConfirmation();
                    updateVerifyToggleButton();
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), enabled ? 1.15f : 0.85f));
                        if (this.minecraft.player != null) {
                            this.minecraft.player.sendOverlayMessage(
                                    Component.translatable(enabled ? "vcoins.verify.msg_on" : "vcoins.verify.msg_off")
                                            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GOLD)
                            );
                        }
                    }
                })
                .bounds(this.leftPos + 206, this.topPos + 151, 16, 12)
                .tooltip(VCoinsPurchaseConfirm.getToggleTooltip())
                .build());

        // 3 nút chuyển đổi Chế độ ngay trên thanh Toolbar
        this.shopTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.title"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenShopPayload());
                })
                .bounds(this.leftPos + 14, this.topPos + 132, 64, 16)
                .build());

        this.blackMarketTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.tab.black_market"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenBlackMarketPayload());
                })
                .bounds(this.leftPos + 82, this.topPos + 132, 72, 16)
                .build());

        this.duplicateTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.duplicate.open"),
                button -> {})
                .bounds(this.leftPos + 158, this.topPos + 132, 64, 16)
                .build());

        updateDuplicateButtonState();
    }

    private void updateVerifyToggleButton() {
        if (this.verifyToggleButton != null) {
            this.verifyToggleButton.setMessage(VCoinsPurchaseConfirm.getCompactToggleLabel());
            this.verifyToggleButton.setTooltip(VCoinsPurchaseConfirm.getToggleTooltip());
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateDuplicateButtonState();
    }

    private void updateDuplicateButtonState() {
        if (this.duplicateActionButton == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (this.selectedDuplicateSlot < 0 || this.selectedDuplicateStack.isEmpty()) {
            this.duplicateActionButton.active = false;
            return;
        }

        ItemStack current = this.minecraft.player.getInventory().getItem(this.selectedDuplicateSlot);
        if (current.isEmpty() || !VCoinsPricing.isTradeable(current)) {
            this.duplicateActionButton.active = false;
            return;
        }

        long coinCost = VDuplicatePricing.getCoinCost(current);
        int levelCost = VDuplicatePricing.getExperienceLevelCost(current);
        long balance = VCoinsState.getCoins(this.minecraft.player.getUUID());
        this.duplicateActionButton.active = (balance >= coinCost && this.minecraft.player.experienceLevel >= levelCost);
    }

    private void executeDuplicate() {
        if (this.selectedDuplicateSlot >= 0 && !this.selectedDuplicateStack.isEmpty()) {
            if (this.minecraft == null || this.minecraft.player == null) return;
            ItemStack current = this.minecraft.player.getInventory().getItem(this.selectedDuplicateSlot);
            if (!VCoinsPricing.isTradeable(current)) return;
            if (!ItemStack.isSameItemSameComponents(current, this.selectedDuplicateStack)) {
                this.selectedDuplicateStack = current.copy();
                purchaseConfirm = new VCoinsPurchaseConfirm();
            }
            if (!purchaseConfirm.checkOrArm(this.selectedDuplicateSlot, false, current,
                    VDuplicatePricing.getCoinCost(current), this.minecraft)) return;
            ClientPlayNetworking.send(new DuplicateActionPayload(this.selectedDuplicateSlot));
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_USE, 1.0f));
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack item = this.hoveredSlot.getItem();
            if (!item.isEmpty() && VCoinsPricing.isTradeable(item)) {
                purchaseConfirm = new VCoinsPurchaseConfirm();
                this.selectedDuplicateSlot = this.hoveredSlot.getContainerSlot();
                this.selectedDuplicateStack = item.copy();
                updateDuplicateButtonState();
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractBackground(extractor, mouseX, mouseY, delta);
        merchantPreview.drawBehindMenu(extractor, this.leftPos, this.topPos, this.height, mouseX, mouseY);
        InventoryTextures.panel(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);

        // Title
        extractor.text(this.font, Component.translatable("vcoins.duplicate.title"), this.leftPos + 58, this.topPos + 9, 0xFF55FFFF, false);

        if (purchaseConfirm.isArmed()) {
            purchaseConfirm.renderBanner(extractor, this.font, this.leftPos + 16, this.topPos + 22, 204, 13);
        }

        // Forge Workspace Tray
        extractor.fill(this.leftPos + 18, this.topPos + 36, this.leftPos + 218, this.topPos + 124, 0x66080310);
        extractor.fill(this.leftPos + 18, this.topPos + 36, this.leftPos + 218, this.topPos + 37, 0x33D4AF37);
        extractor.fill(this.leftPos + 18, this.topPos + 123, this.leftPos + 218, this.topPos + 124, 0x33D4AF37);

        // Original Box
        InventoryTextures.recess(extractor, this.leftPos + 40, this.topPos + 44, 32, 32);
        extractor.centeredText(this.font, Component.literal("§eGốc"), this.leftPos + 56, this.topPos + 36, 0xFFD4AF37);
        if (!selectedDuplicateStack.isEmpty()) {
            extractor.item(selectedDuplicateStack, this.leftPos + 48, this.topPos + 52);
            extractor.itemDecorations(this.font, selectedDuplicateStack, this.leftPos + 48, this.topPos + 52);
        }

        // Glowing Forge Arrow
        extractor.centeredText(this.font, Component.literal("§6§l➜"), this.leftPos + 118, this.topPos + 54, 0xFFFFAA00);

        // Clone Box
        InventoryTextures.recess(extractor, this.leftPos + 164, this.topPos + 44, 32, 32);
        extractor.centeredText(this.font, Component.literal("§bBản sao"), this.leftPos + 180, this.topPos + 36, 0xFF55FFFF);
        if (!selectedDuplicateStack.isEmpty()) {
            ItemStack clone = selectedDuplicateStack.copyWithCount(1);
            extractor.item(clone, this.leftPos + 172, this.topPos + 52);
            extractor.itemDecorations(this.font, clone, this.leftPos + 172, this.topPos + 52);
        }

        // Cost and Instruction Info
        if (selectedDuplicateStack.isEmpty()) {
            extractor.centeredText(this.font, Component.literal("§7Nhấp một món đồ trong túi đồ để nhân bản"),
                    this.leftPos + 118, this.topPos + 84, 0xFFAAAAAA);
        } else {
            long coinCost = VDuplicatePricing.getCoinCost(selectedDuplicateStack);
            int levelCost = VDuplicatePricing.getExperienceLevelCost(selectedDuplicateStack);
            long balance = (this.minecraft != null && this.minecraft.player != null)
                    ? VCoinsState.getCoins(this.minecraft.player.getUUID()) : 0L;
            int playerLevels = (this.minecraft != null && this.minecraft.player != null)
                    ? this.minecraft.player.experienceLevel : 0;

            String coinStr = (balance >= coinCost ? "§a" : "§c") + formatCompactNumber(coinCost) + " Coins";
            String xpStr = (playerLevels >= levelCost ? "§a" : "§c") + levelCost + " Cấp EXP";
            extractor.centeredText(this.font, Component.literal("§eGiá: " + coinStr + " §7| " + xpStr),
                    this.leftPos + 118, this.topPos + 84, 0xFFFFFFFF);
        }

        // Shared player inventory grid
        InventoryTextures.slots(extractor, this.leftPos + VDuplicateScreenHandler.PLAYER_X, this.topPos + VDuplicateScreenHandler.PLAYER_INVENTORY_Y, 9, 3);
        InventoryTextures.slots(extractor, this.leftPos + VDuplicateScreenHandler.PLAYER_X, this.topPos + VDuplicateScreenHandler.PLAYER_HOTBAR_Y, 9, 1);

        // Toolbar decorative tray
        extractor.fill(this.leftPos + 12, this.topPos + 130, this.leftPos + 224, this.topPos + 150, 0x55080310);
        extractor.fill(this.leftPos + 12, this.topPos + 130, this.leftPos + 224, this.topPos + 131, 0x22D4AF37);
        extractor.fill(this.leftPos + 12, this.topPos + 149, this.leftPos + 224, this.topPos + 150, 0x22D4AF37);

        // Highlight line under active mode button (Duplicate - cyan)
        extractor.fill(this.leftPos + 158, this.topPos + 147, this.leftPos + 222, this.topPos + 148, 0xFF55FFFF);

        // Player Inventory label
        extractor.text(this.font, Component.translatable("vcoins.inventory"),
                this.leftPos + VDuplicateScreenHandler.PLAYER_X, this.topPos + 153, 0xFFC8A96E, false);

        VeloriaMenuEffects.draw(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        if (mouseX >= this.leftPos + 40 && mouseX < this.leftPos + 72 && mouseY >= this.topPos + 44 && mouseY < this.topPos + 76 && !selectedDuplicateStack.isEmpty()) {
            extractor.setTooltipForNextFrame(this.font, getCleanTooltip(selectedDuplicateStack), selectedDuplicateStack.getTooltipImage(), mouseX, mouseY,
                    selectedDuplicateStack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
            return;
        }
        if (mouseX >= this.leftPos + 164 && mouseX < this.leftPos + 196 && mouseY >= this.topPos + 44 && mouseY < this.topPos + 76 && !selectedDuplicateStack.isEmpty()) {
            extractor.setTooltipForNextFrame(this.font, getCleanTooltip(selectedDuplicateStack), selectedDuplicateStack.getTooltipImage(), mouseX, mouseY,
                    selectedDuplicateStack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
            return;
        }

        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack stack = this.hoveredSlot.getItem();
            extractor.setTooltipForNextFrame(this.font, getCleanTooltip(stack), stack.getTooltipImage(), mouseX, mouseY,
                    stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
        }
    }

    private List<Component> getCleanTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>(this.getTooltipFromContainerItem(stack));
        VeloriaTooltip.removeDebugLines(lines, stack);
        return lines;
    }

    private static String formatCompactNumber(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        } else if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        } else if (value >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }
}
