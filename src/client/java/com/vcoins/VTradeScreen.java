package com.vcoins;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

public class VTradeScreen extends VeloriaContainerScreen<VTradeScreenHandler> {
    private final VeloriaMerchantPreview merchantPreview = new VeloriaMerchantPreview();
    private final VCoinsPurchaseConfirm purchaseConfirm = new VCoinsPurchaseConfirm();
    private static final int TAB_WIDTH = 27;
    private static final int TAB_HEIGHT = 28;
    private static final int TAB_GAP = 1;
    private static final int TOP_TAB_COUNT = 7;
    private static final int SCROLLBAR_X = 194;
    private static final int SCROLLBAR_Y = 38;
    private static final int SCROLLBAR_HEIGHT = 90;
    private static final int SCROLL_THUMB_HEIGHT = 15;
    private static final int SHOP_X = VTradeScreenHandler.SHOP_X;
    private static final int SHOP_Y = VTradeScreenHandler.SHOP_Y;
    private static final int PLAYER_X = VTradeScreenHandler.PLAYER_X;
    private static final int PLAYER_INVENTORY_Y = VTradeScreenHandler.PLAYER_INVENTORY_Y;
    private static final int PLAYER_HOTBAR_Y = VTradeScreenHandler.PLAYER_HOTBAR_Y;
    private static final int SLOT_SPACING = 18;
    private static final int SLOT_SIZE = 16;

    private static final ShopCategory[] TABS = {
            ShopCategory.ALL,
            ShopCategory.BUILDING,
            ShopCategory.COLORED,
            ShopCategory.NATURAL,
            ShopCategory.FUNCTIONAL,
            ShopCategory.REDSTONE,
            ShopCategory.TOOLS,
            ShopCategory.COMBAT,
            ShopCategory.FOOD,
            ShopCategory.INGREDIENTS,
            ShopCategory.MISC,
            ShopCategory.BUYBACK
    };

    private EditBox searchBox;
    private ShopCategory selectedCategory = ShopCategory.ALL;
    private float scrollPosition;
    private boolean scrolling;
    private int lastScrollOffset = -1;

    // View Navigation Buttons
    private Button verifyToggleButton;
    private Button shopTabButton;
    private Button blackMarketTabButton;
    private Button duplicateTabButton;

    public VTradeScreen(VTradeScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 236, 250);
        this.inventoryLabelX = 1000;
        this.titleLabelX = 1000;
    }

    @Override
    protected void init() {
        super.init();

        this.searchBox = new EditBox(this.font, this.leftPos + 112, this.topPos + 6, 110, 15,
                Component.translatable("vcoins.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setHint(Component.translatable("vcoins.search"));
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        // Công tắc bật/tắt xác minh giao dịch >100k
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
                button -> {})
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
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenDuplicatePayload());
                })
                .bounds(this.leftPos + 158, this.topPos + 132, 64, 16)
                .build());

        this.setInitialFocus(this.searchBox);
    }

    private void updateVerifyToggleButton() {
        if (this.verifyToggleButton != null) {
            this.verifyToggleButton.setMessage(VCoinsPurchaseConfirm.getCompactToggleLabel());
            this.verifyToggleButton.setTooltip(VCoinsPurchaseConfirm.getToggleTooltip());
        }
    }

    private void onSearchChanged(String query) {
        ClientPlayNetworking.send(new ShopActionPayload("SEARCH", query));
    }

    private void selectCategory(ShopCategory category) {
        if (this.selectedCategory == category) return;
        this.selectedCategory = category;
        this.scrollPosition = 0.0f;
        this.lastScrollOffset = -1;
        this.menu.setScrollOffset(0);
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
        ClientPlayNetworking.send(new ShopActionPayload("TAB", category.name()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractBackground(extractor, mouseX, mouseY, delta);
        merchantPreview.drawBehindMenu(extractor, this.leftPos, this.topPos, this.height, mouseX, mouseY);
        drawPanel(extractor);

        drawTabs(extractor);
        drawSlotGrid(extractor, SHOP_X, SHOP_Y, VTradeScreenHandler.SHOP_COLUMNS, VTradeScreenHandler.SHOP_ROWS);
        drawScrollbar(extractor);

        if (purchaseConfirm.isArmed()) {
            purchaseConfirm.renderBanner(extractor, this.font, this.leftPos + 16, this.topPos + 22, 204, 13);
        } else {
            renderShopBalance(extractor);
        }

        // Shared player inventory grid
        drawSlotGrid(extractor, PLAYER_X, PLAYER_INVENTORY_Y, 9, 3);
        drawSlotGrid(extractor, PLAYER_X, PLAYER_HOTBAR_Y, 9, 1);

        // Toolbar decorative tray
        extractor.fill(this.leftPos + 12, this.topPos + 130, this.leftPos + 224, this.topPos + 150, 0x55080310);
        extractor.fill(this.leftPos + 12, this.topPos + 130, this.leftPos + 224, this.topPos + 131, 0x22D4AF37);
        extractor.fill(this.leftPos + 12, this.topPos + 149, this.leftPos + 224, this.topPos + 150, 0x22D4AF37);

        // Highlight line under active mode button (Shop - gold)
        extractor.fill(this.leftPos + 14, this.topPos + 147, this.leftPos + 78, this.topPos + 148, 0xFFD4AF37);

        // Player Inventory label
        extractor.text(this.font, Component.translatable("vcoins.inventory"),
                this.leftPos + PLAYER_X, this.topPos + 153, 0xFFC8A96E, false);
        VeloriaMenuEffects.draw(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    private void renderShopBalance(GuiGraphicsExtractor extractor) {
        long balance = this.minecraft != null && this.minecraft.player != null
                ? VCoinsState.getCoins(this.minecraft.player.getUUID())
                : 0L;
        extractor.fill(this.leftPos + 16, this.topPos + 22, this.leftPos + 220, this.topPos + 35, 0x88080310);
        extractor.fill(this.leftPos + 16, this.topPos + 22, this.leftPos + 220, this.topPos + 23, 0x33D4AF37);
        extractor.fill(this.leftPos + 16, this.topPos + 34, this.leftPos + 220, this.topPos + 35, 0x33D4AF37);
        extractor.text(this.font, Component.translatable("vcoins.balance", formatNumber(balance)),
                this.leftPos + 20, this.topPos + 24, 0xFFE8B829, true);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY) {
        super.extractSlot(extractor, slot, mouseX, mouseY);

        if (slot.index < VTradeScreenHandler.SHOP_SLOT_COUNT && slot.hasItem()
                && selectedCategory != ShopCategory.BUYBACK) {
            var trend = VMarketEngine.getTrend(slot.getItem());
            extractor.text(this.font, Component.literal(trend.getArrow()).withStyle(trend.direction().getColor()),
                    slot.x + 11, slot.y, 0xFFFFFFFF, true);
        }
        if (slot.index < VTradeScreenHandler.SHOP_SLOT_COUNT && purchaseConfirm.isSlotPending(slot.index)) {
            purchaseConfirm.renderSlotWarningPulse(extractor, slot.x - 1, slot.y - 1, 18);
        }
    }

    private void drawPanel(GuiGraphicsExtractor extractor) {
        InventoryTextures.panel(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        extractor.text(this.font, Component.translatable("vcoins.title"), this.leftPos + 18, this.topPos + 9, 0xFFD4AF37, false);
    }

    private void drawSlotGrid(GuiGraphicsExtractor extractor, int relativeX, int relativeY, int columns, int rows) {
        InventoryTextures.slots(extractor, this.leftPos + relativeX, this.topPos + relativeY, columns, rows);
    }

    private void drawTabs(GuiGraphicsExtractor extractor) {
        for (int index = 0; index < TABS.length; index++) {
            TabBounds bounds = getTabBounds(index);
            boolean selected = TABS[index] == this.selectedCategory;
            InventoryTextures.button(extractor, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    true, selected, selected ? 0.65f : 0f);
            if (selected) {
                if (index < TOP_TAB_COUNT) {
                    extractor.fill(bounds.x() + 2, bounds.y() + bounds.height() - 2, bounds.x() + bounds.width() - 2, bounds.y() + bounds.height(), 0xFFD4AF37);
                } else {
                    extractor.fill(bounds.x() + 2, bounds.y(), bounds.x() + bounds.width() - 2, bounds.y() + 2, 0xFFD4AF37);
                }
            }
            Item icon = getTabIcon(TABS[index]);
            extractor.item(new ItemStack(icon), bounds.x() + 5, bounds.y() + 6);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor extractor) {
        int trackX = this.leftPos + SCROLLBAR_X;
        int trackY = this.topPos + SCROLLBAR_Y;
        int maxRows = this.menu.getMaxRows();
        boolean enabled = maxRows > 0;

        InventoryTextures.recess(extractor, trackX, trackY, 12, SCROLLBAR_HEIGHT);
        int travel = SCROLLBAR_HEIGHT - SCROLL_THUMB_HEIGHT;
        int thumbY = trackY + Math.round(this.scrollPosition * travel);
        InventoryTextures.scrollThumb(extractor, trackX, thumbY, 12, SCROLL_THUMB_HEIGHT, enabled);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            extractSlotTooltip(extractor, mouseX, mouseY);
            return;
        }

        for (int index = 0; index < TABS.length; index++) {
            TabBounds bounds = getTabBounds(index);
            if (bounds.contains(mouseX, mouseY)) {
                extractor.setTooltipForNextFrame(this.font, getTabName(TABS[index]), mouseX, mouseY);
                return;
            }
        }
    }

    private void extractSlotTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        Slot slot = this.hoveredSlot;
        ItemStack stack = slot.getItem();
        var tooltip = this.getTooltipFromContainerItem(stack);

        if (slot.index < VTradeScreenHandler.SHOP_SLOT_COUNT) {
            long buyPrice = (selectedCategory == ShopCategory.BUYBACK)
                    ? VCoinsPricing.getBuybackPrice(stack)
                    : VCoinsPricing.getPrice(stack);
            long sellPrice = VCoinsPricing.getSellPrice(stack);

            if (selectedCategory != ShopCategory.BUYBACK) {
                var trend = VMarketEngine.getTrend(stack);
                if (trend.percentChange() != 0) {
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§7Xu hướng: ").append(trend.getBadge()));
                    if (!trend.reasonKey().isEmpty()) {
                        tooltip.add(Component.translatable(trend.reasonKey()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                    }
                }
            }

            tooltip.add(Component.empty());
            if (buyPrice > 0) {
                tooltip.add(Component.translatable("vcoins.tooltip.buy_unit", formatNumber(buyPrice)).withStyle(ChatFormatting.YELLOW));
            }
            if (sellPrice > 0 && selectedCategory != ShopCategory.BUYBACK) {
                tooltip.add(Component.translatable("vcoins.tooltip.sell_unit", formatNumber(sellPrice)).withStyle(ChatFormatting.AQUA));
            }

            long balance = (this.minecraft != null && this.minecraft.player != null)
                    ? VCoinsState.getCoins(this.minecraft.player.getUUID()) : 0L;
            if (buyPrice > 0 && balance < buyPrice) {
                tooltip.add(Component.translatable("vcoins.tooltip.cannot_afford").withStyle(ChatFormatting.RED));
            } else if (buyPrice > 0) {
                tooltip.add(Component.translatable("vcoins.tooltip.buy_left").withStyle(ChatFormatting.GRAY));
                if (stack.getMaxStackSize() > 1) {
                    tooltip.add(Component.translatable("vcoins.tooltip.buy_right_stack").withStyle(ChatFormatting.DARK_GRAY));
                }
            }

            if (purchaseConfirm.isSlotPending(slot.index)) {
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("vcoins.verify.click_confirm").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }
        }

        extractor.setTooltipForNextFrame(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY,
                stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();

        for (int index = 0; index < TABS.length; index++) {
            TabBounds bounds = getTabBounds(index);
            if (bounds.contains(mouseX, mouseY)) {
                selectCategory(TABS[index]);
                return true;
            }
        }

        int trackX = this.leftPos + SCROLLBAR_X;
        int trackY = this.topPos + SCROLLBAR_Y;
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT
                && mouseX >= trackX && mouseX < trackX + 12
                && mouseY >= trackY && mouseY < trackY + SCROLLBAR_HEIGHT
                && this.menu.getMaxRows() > 0) {
            this.scrolling = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        int shopSlot = getShopSlotAt(mouseX, mouseY);
        if (shopSlot >= 0) {
            if (click.button() == InputConstants.MOUSE_BUTTON_LEFT
                    || click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                boolean buyStack = click.button() == InputConstants.MOUSE_BUTTON_RIGHT || click.hasShiftDown();
                Slot slot = this.menu.slots.get(shopSlot);
                if (slot != null && slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    long unitPrice = (this.selectedCategory == ShopCategory.BUYBACK)
                            ? VCoinsPricing.getBuybackPrice(stack)
                            : VCoinsPricing.getPrice(stack);
                    int amount = buyStack ? stack.getMaxStackSize() : 1;
                    long totalCost = safeMultiply(unitPrice, amount);

                    if (purchaseConfirm.checkOrArm(shopSlot, buyStack, stack, totalCost, this.minecraft)) {
                        ClientPlayNetworking.send(new ShopTransactionPayload(shopSlot, buyStack));
                    }
                }
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.scrolling) {
            updateScrollFromMouse(click.y());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && this.scrolling) {
            this.scrolling = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxRows = this.menu.getMaxRows();
        if (maxRows > 0 && verticalAmount != 0.0) {
            int currentOffset = Math.round(this.scrollPosition * maxRows);
            int nextOffset = Math.max(0, Math.min(maxRows, currentOffset - (int) Math.signum(verticalAmount)));
            setScrollOffset(nextOffset, maxRows);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxRows = this.menu.getMaxRows();
        if (maxRows <= 0) return;
        int trackY = this.topPos + SCROLLBAR_Y;
        float travel = SCROLLBAR_HEIGHT - SCROLL_THUMB_HEIGHT;
        float value = (float) ((mouseY - trackY - SCROLL_THUMB_HEIGHT / 2.0) / travel);
        this.scrollPosition = Math.max(0.0f, Math.min(1.0f, value));
        setScrollOffset(Math.round(this.scrollPosition * maxRows), maxRows);
    }

    private void setScrollOffset(int offset, int maxRows) {
        this.scrollPosition = maxRows == 0 ? 0.0f : (float) offset / maxRows;
        if (offset == this.lastScrollOffset) return;
        this.lastScrollOffset = offset;
        this.menu.setScrollOffset(offset);
        ClientPlayNetworking.send(new ShopActionPayload("SCROLL", Integer.toString(offset)));
    }

    private int getShopSlotAt(double mouseX, double mouseY) {
        double localX = mouseX - (this.leftPos + SHOP_X);
        double localY = mouseY - (this.topPos + SHOP_Y);
        if (localX < 0 || localY < 0) return -1;
        int column = (int) (localX / SLOT_SPACING);
        int row = (int) (localY / SLOT_SPACING);
        if (column >= VTradeScreenHandler.SHOP_COLUMNS || row >= VTradeScreenHandler.SHOP_ROWS) return -1;
        if (localX - column * SLOT_SPACING >= SLOT_SIZE || localY - row * SLOT_SPACING >= SLOT_SIZE) return -1;
        return column + row * VTradeScreenHandler.SHOP_COLUMNS;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyInput) {
        if (!this.searchBox.isFocused() && (keyInput.key() == InputConstants.KEY_SPACE || keyInput.key() == InputConstants.KEY_RETURN || keyInput.key() == InputConstants.KEY_NUMPADENTER)) {
            if (purchaseConfirm.handleKeyPress((slot, buyStack) -> {
                ClientPlayNetworking.send(new ShopTransactionPayload(slot, buyStack));
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                }
            })) {
                return true;
            }
        }
        if (this.searchBox.keyPressed(keyInput)) return true;
        if (this.searchBox.isFocused() && !keyInput.isEscape()) return true;
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent charInput) {
        if (charInput.isAllowedChatCharacter() && !this.searchBox.isFocused()) {
            this.setFocused(this.searchBox);
        }
        if (this.searchBox.charTyped(charInput)) return true;
        return super.charTyped(charInput);
    }

    private TabBounds getTabBounds(int index) {
        boolean top = index < TOP_TAB_COUNT;
        int tabIndex = top ? index : index - TOP_TAB_COUNT;
        int tabX = this.leftPos + 20 + tabIndex * (TAB_WIDTH + TAB_GAP);
        int tabY = top ? this.topPos - TAB_HEIGHT + 4 : this.topPos + this.imageHeight - 4;
        return new TabBounds(tabX, tabY, TAB_WIDTH, TAB_HEIGHT);
    }

    private static Item getTabIcon(ShopCategory category) {
        return switch (category) {
            case ALL -> Items.COMPASS;
            case BUILDING -> Items.BRICKS;
            case COLORED -> Items.WOOL.cyan();
            case NATURAL -> Items.GRASS_BLOCK;
            case FUNCTIONAL -> Items.CRAFTING_TABLE;
            case REDSTONE -> Items.REDSTONE;
            case TOOLS -> Items.DIAMOND_PICKAXE;
            case COMBAT -> Items.DIAMOND_SWORD;
            case FOOD -> Items.GOLDEN_APPLE;
            case INGREDIENTS -> Items.IRON_INGOT;
            case SPAWN_EGGS -> Items.PIG_SPAWN_EGG;
            case MISC -> Items.BUNDLE;
            case BLACK_MARKET -> Items.NETHER_STAR;
            case BUYBACK -> Items.CHEST;
        };
    }

    private static Component getTabName(ShopCategory category) {
        return Component.translatable("vcoins.tab." + category.name().toLowerCase(Locale.ROOT));
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static long safeMultiply(long value, int count) {
        try {
            return Math.multiplyExact(value, (long) count);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private record TabBounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
