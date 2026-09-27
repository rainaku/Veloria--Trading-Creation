package com.vcoins;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VTradeScreen extends AbstractContainerScreen<VTradeScreenHandler> {
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

    public VTradeScreen(VTradeScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 236, 250);
    }

    private Button verifyToggleButton;

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
                VCoinsPurchaseConfirm.getToggleLabel(),
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
                .bounds(this.leftPos + 15, this.topPos + 132, 46, 16)
                .tooltip(VCoinsPurchaseConfirm.getToggleTooltip())
                .build());

        // Nút mở Menu Chợ Trời riêng
        this.addRenderableWidget(VeloriaButton.create(Component.translatable("vcoins.tab.black_market"), button ->
                        ClientPlayNetworking.send(new OpenBlackMarketPayload()))
                .bounds(this.leftPos + 64, this.topPos + 132, 54, 16)
                .build());

        this.addRenderableWidget(VeloriaButton.create(Component.translatable("vcoins.buyback"), button ->
                        selectCategory(ShopCategory.BUYBACK))
                .bounds(this.leftPos + 121, this.topPos + 132, 48, 16)
                .build());

        this.addRenderableWidget(VeloriaButton.create(Component.translatable("vcoins.duplicate.open"), button ->
                        ClientPlayNetworking.send(new OpenDuplicatePayload()))
                .bounds(this.leftPos + 172, this.topPos + 132, 50, 16)
                .build());

        // Opening the market should be enough to start typing a search.
        this.setInitialFocus(this.searchBox);
    }

    private void updateVerifyToggleButton() {
        if (this.verifyToggleButton != null) {
            this.verifyToggleButton.setMessage(VCoinsPurchaseConfirm.getToggleLabel());
            this.verifyToggleButton.setTooltip(VCoinsPurchaseConfirm.getToggleTooltip());
        }
    }

    private void onSearchChanged(String query) {
        if (!query.isEmpty() && this.selectedCategory != ShopCategory.ALL) {
            selectCategory(ShopCategory.ALL);
        }
        this.scrollPosition = 0.0f;
        this.lastScrollOffset = 0;
        this.menu.setSearchQuery(query);
        ClientPlayNetworking.send(new ShopActionPayload("SEARCH", query));
    }

    private void selectCategory(ShopCategory category) {
        if (this.selectedCategory == category) {
            return;
        }

        VeloriaMenuEffects.categoryChanged();
        this.selectedCategory = category;
        this.scrollPosition = 0.0f;
        this.lastScrollOffset = 0;
        this.menu.setCategory(category);
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
        ClientPlayNetworking.send(new ShopActionPayload("TAB", category.name()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractBackground(extractor, mouseX, mouseY, delta);
        merchantPreview.drawBehindMenu(extractor, this.leftPos, this.topPos, this.height, mouseX, mouseY);
        drawTabs(extractor);
        drawPanel(extractor);
        drawSlotGrid(extractor, SHOP_X, SHOP_Y, VTradeScreenHandler.SHOP_COLUMNS, VTradeScreenHandler.SHOP_ROWS);
        drawSlotGrid(extractor, PLAYER_X, PLAYER_INVENTORY_Y, 9, 3);
        drawSlotGrid(extractor, PLAYER_X, PLAYER_HOTBAR_Y, 9, 1);
        drawScrollbar(extractor);

        if (purchaseConfirm.isArmed()) {
            purchaseConfirm.renderBanner(extractor, this.font, this.leftPos + 16, this.topPos + 22, 204, 13);
        } else {
            long balance = this.minecraft != null && this.minecraft.player != null
                    ? VCoinsState.getCoins(this.minecraft.player.getUUID())
                    : 0L;
            extractor.fill(this.leftPos + 16, this.topPos + 22, this.leftPos + 220, this.topPos + 35, 0x88080310);
            extractor.fill(this.leftPos + 16, this.topPos + 22, this.leftPos + 220, this.topPos + 23, 0x33D4AF37);
            extractor.fill(this.leftPos + 16, this.topPos + 34, this.leftPos + 220, this.topPos + 35, 0x33D4AF37);
            extractor.text(this.font, Component.translatable("vcoins.balance", formatNumber(balance)),
                    this.leftPos + 20, this.topPos + 24, 0xFFE8B829, true);

        }

        // Toolbar background decorative tray
        extractor.fill(this.leftPos + 12, this.topPos + 130, this.leftPos + 224, this.topPos + 150, 0x55080310);
        extractor.fill(this.leftPos + 12, this.topPos + 130, this.leftPos + 224, this.topPos + 131, 0x22D4AF37);
        extractor.fill(this.leftPos + 12, this.topPos + 149, this.leftPos + 224, this.topPos + 150, 0x22D4AF37);

        // Player Inventory label
        extractor.text(this.font, Component.translatable("vcoins.inventory"),
                this.leftPos + PLAYER_X, this.topPos + 153, 0xFFC8A96E, false);
        VeloriaMenuEffects.draw(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
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

        extractor.text(this.font, Component.translatable("vcoins.title"),
                this.leftPos + 18, this.topPos + 9, 0xFFD4AF37, false);
    }

    private void drawSlotGrid(GuiGraphicsExtractor extractor, int relativeX, int relativeY, int columns, int rows) {
        InventoryTextures.slots(extractor, this.leftPos + relativeX, this.topPos + relativeY, columns, rows);
    }

    private void drawTabs(GuiGraphicsExtractor extractor) {
        for (int index = 0; index < TABS.length; index++) {
            TabBounds bounds = getTabBounds(index);
            boolean selected = TABS[index] == this.selectedCategory;
            String row = index < TOP_TAB_COUNT ? "top" : "bottom";
            int position = index < TOP_TAB_COUNT ? index + 1 : index - TOP_TAB_COUNT + 1;
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
        // All labels are positioned explicitly in extractBackground.
    }

    private static final String TOOLTIP_BUY_PRICE = "vcoins.tooltip.buy_price";

    @Override
    protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            extractSlotTooltip(extractor, mouseX, mouseY);
            return;
        }

        ShopCategory hoveredTab = getHoveredTab(mouseX, mouseY);
        if (hoveredTab != null) {
            extractor.setTooltipForNextFrame(this.font, getTabName(hoveredTab), mouseX, mouseY);
            return;
        }

        if (mouseX >= this.leftPos + 8 && mouseX < this.leftPos + 89 && mouseY >= this.topPos + 17 && mouseY < this.topPos + 28
                && this.minecraft != null && this.minecraft.player != null) {
            long balance = VCoinsState.getCoins(this.minecraft.player.getUUID());
            extractor.setTooltipForNextFrame(this.font, Component.translatable("vcoins.balance", formatNumber(balance)), mouseX, mouseY);
            return;
        }

        if (mouseX >= this.leftPos + 155 && mouseX < this.leftPos + 220 && mouseY >= this.topPos + 22 && mouseY < this.topPos + 35) {
            List<Component> marketTips = List.of(
                    Component.translatable("vcoins.market.tooltip_title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.translatable("vcoins.market.tooltip_desc").withStyle(ChatFormatting.GRAY)
            );
            extractor.setTooltipForNextFrame(this.font, marketTips, java.util.Optional.empty(), mouseX, mouseY);
            return;
        }

        super.extractTooltip(extractor, mouseX, mouseY);
    }

    private void extractSlotTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        ItemStack stack = this.hoveredSlot.getItem();
        List<Component> tooltip = this.getTooltipFromContainerItem(stack);

        extractor.setTooltipForNextFrame(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY,
                    stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = VeloriaTooltip.withoutPrices(super.getTooltipFromContainerItem(stack));
        long buyPrice = VCoinsPricing.getPrice(stack);
        if (this.hoveredSlot != null && this.hoveredSlot.index < VTradeScreenHandler.SHOP_SLOT_COUNT) {
            appendShopSlotTooltip(tooltip, stack, buyPrice);
        } else {
            appendInventorySlotTooltip(tooltip, stack, buyPrice);
        }
        return tooltip;
    }

    private void appendShopSlotTooltip(List<Component> tooltip, ItemStack stack, long buyPrice) {
        VMarketEngine.MarketTrend trend = VMarketEngine.getTrend(stack);

        tooltip.add(Component.empty());
        if (this.selectedCategory == ShopCategory.BUYBACK) {
            long totalPrice = safeMultiply(VCoinsPricing.getBuybackPrice(stack), stack.getCount());
            if (buyPrice > 0) {
                MutableComponent priceComp = Component.translatable(TOOLTIP_BUY_PRICE, formatNumber(buyPrice))
                        .withStyle(ChatFormatting.YELLOW);
                priceComp.append(Component.literal(" ")).append(trend.getBadge());
                tooltip.add(priceComp);
            }
            tooltip.add(Component.translatable("vcoins.tooltip.buyback", formatNumber(totalPrice))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            if (this.selectedCategory == ShopCategory.BLACK_MARKET) {
                tooltip.add(Component.translatable("vcoins.black_market.merchant_tag")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            }
            MutableComponent priceComp = Component.translatable(TOOLTIP_BUY_PRICE, formatNumber(buyPrice))
                    .withStyle(ChatFormatting.YELLOW);
            priceComp.append(Component.literal(" ")).append(trend.getBadge());
            tooltip.add(priceComp);

            tooltip.add(Component.translatable(trend.reasonKey()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

            tooltip.add(Component.translatable("vcoins.tooltip.buy_left").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("vcoins.tooltip.buy_shift").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("vcoins.tooltip.buy_right", stack.getMaxStackSize())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void appendInventorySlotTooltip(List<Component> tooltip, ItemStack stack, long buyPrice) {
        long sellPrice = VCoinsPricing.getSellPrice(stack);
        VMarketEngine.MarketTrend trend = VMarketEngine.getTrend(stack);

        if (buyPrice > 0 || sellPrice > 0) {
            tooltip.add(Component.empty());
            if (buyPrice > 0) {
                MutableComponent buyComp = Component.translatable(TOOLTIP_BUY_PRICE, formatNumber(buyPrice))
                        .withStyle(ChatFormatting.YELLOW);
                buyComp.append(Component.literal(" ")).append(trend.getBadge());
                tooltip.add(buyComp);
            }
        }
        if (sellPrice > 0) {
            MutableComponent sellComp = Component.translatable("vcoins.tooltip.sell_price", formatNumber(sellPrice))
                    .withStyle(ChatFormatting.GREEN);
            sellComp.append(Component.literal(" ")).append(VMarketEngine.getSellTrend(stack).getBadge());
            tooltip.add(sellComp);

            if (VMarketEngine.getSellTrend(stack).percentChange() > 0) {
                tooltip.add(Component.translatable("vcoins.market.sell_opportunity_high").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
            } else if (VMarketEngine.getSellTrend(stack).percentChange() < 0) {
                tooltip.add(Component.translatable("vcoins.market.sell_opportunity_low").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            }

            tooltip.add(Component.translatable("vcoins.tooltip.sell_shift").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("vcoins.tooltip.sell_drag").withStyle(ChatFormatting.GRAY));
        }
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
                boolean buyStack = click.button() == InputConstants.MOUSE_BUTTON_RIGHT
                        || click.hasShiftDown();

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
            // Never pass a shop-grid input to vanilla slot handling.
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
            // The matching press was consumed by the custom scrollbar, so its
            // release must not reach vanilla slot handling.
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxRows = this.menu.getMaxRows();
        if (maxRows > 0 && verticalAmount != 0.0) {
            int currentOffset = Math.round(this.scrollPosition * maxRows);
            int nextOffset = Math.max(0, Math.min(maxRows,
                    currentOffset - (int) Math.signum(verticalAmount)));
            setScrollOffset(nextOffset, maxRows);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxRows = this.menu.getMaxRows();
        if (maxRows <= 0) {
            return;
        }

        int trackY = this.topPos + SCROLLBAR_Y;
        float travel = SCROLLBAR_HEIGHT - SCROLL_THUMB_HEIGHT;
        float value = (float) ((mouseY - trackY - SCROLL_THUMB_HEIGHT / 2.0) / travel);
        this.scrollPosition = Math.max(0.0f, Math.min(1.0f, value));
        setScrollOffset(Math.round(this.scrollPosition * maxRows), maxRows);
    }

    private void setScrollOffset(int offset, int maxRows) {
        this.scrollPosition = maxRows == 0 ? 0.0f : (float) offset / maxRows;
        if (offset == this.lastScrollOffset) {
            return;
        }

        this.lastScrollOffset = offset;
        this.menu.setScrollOffset(offset);
        ClientPlayNetworking.send(new ShopActionPayload("SCROLL", Integer.toString(offset)));
    }

    private int getShopSlotAt(double mouseX, double mouseY) {
        double localX = mouseX - (this.leftPos + SHOP_X);
        double localY = mouseY - (this.topPos + SHOP_Y);
        if (localX < 0 || localY < 0) {
            return -1;
        }

        int column = (int) (localX / SLOT_SPACING);
        int row = (int) (localY / SLOT_SPACING);
        if (column >= VTradeScreenHandler.SHOP_COLUMNS || row >= VTradeScreenHandler.SHOP_ROWS) {
            return -1;
        }

        if (localX - column * SLOT_SPACING >= SLOT_SIZE
                || localY - row * SLOT_SPACING >= SLOT_SIZE) {
            return -1;
        }
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
        if (this.searchBox.keyPressed(keyInput)) {
            return true;
        }
        if (this.searchBox.isFocused() && !keyInput.isEscape()) {
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent charInput) {
        if (charInput.isAllowedChatCharacter() && !this.searchBox.isFocused()) {
            this.setFocused(this.searchBox);
        }
        if (this.searchBox.charTyped(charInput)) {
            return true;
        }
        return super.charTyped(charInput);
    }

    private ShopCategory getHoveredTab(double mouseX, double mouseY) {
        for (int index = 0; index < TABS.length; index++) {
            if (getTabBounds(index).contains(mouseX, mouseY)) {
                return TABS[index];
            }
        }
        return null;
    }

    private TabBounds getTabBounds(int index) {
        boolean top = index < TOP_TAB_COUNT;
        int rowIndex = top ? index : index - TOP_TAB_COUNT;
        int count = top ? TOP_TAB_COUNT : TABS.length - TOP_TAB_COUNT;
        int totalWidth = count * TAB_WIDTH + (count - 1) * TAB_GAP;
        int startX = this.leftPos + (this.imageWidth - totalWidth) / 2;
        int tabX = startX + rowIndex * (TAB_WIDTH + TAB_GAP);
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

    private static String formatCompactNumber(long value) {
        if (value < 1_000L) {
            return Long.toString(value);
        }

        double scaled;
        String suffix;
        if (value >= 1_000_000_000_000L) {
            scaled = value / 1_000_000_000_000.0;
            suffix = "T";
        } else if (value >= 1_000_000_000L) {
            scaled = value / 1_000_000_000.0;
            suffix = "B";
        } else if (value >= 1_000_000L) {
            scaled = value / 1_000_000.0;
            suffix = "M";
        } else {
            scaled = value / 1_000.0;
            suffix = "K";
        }

        String number = scaled >= 100.0
                ? String.format(Locale.ROOT, "%.0f", scaled)
                : String.format(Locale.ROOT, "%.1f", scaled).replace(".0", "");
        return number + suffix;
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
