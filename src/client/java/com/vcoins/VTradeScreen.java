package com.vcoins;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VTradeScreen extends AbstractContainerScreen<VTradeScreenHandler> {
    private static final int TAB_WIDTH = 27;
    private static final int TAB_HEIGHT = 28;
    private static final int TAB_GAP = 1;
    private static final int TOP_TAB_COUNT = 7;
    private static final int SCROLLBAR_X = 176;
    private static final int SCROLLBAR_Y = 34;
    private static final int SCROLLBAR_HEIGHT = 89;
    private static final int SCROLL_THUMB_HEIGHT = 15;
    private static final int SHOP_X = 9;
    private static final int SHOP_Y = 34;
    private static final int SLOT_SPACING = 18;
    private static final int SLOT_SIZE = 16;

    private static final ShopCategory[] TABS = {
            ShopCategory.ALL,
            ShopCategory.BLACK_MARKET,
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
        super(handler, inventory, title, 195, 222);
    }

    @Override
    protected void init() {
        super.init();

        this.searchBox = new EditBox(this.font, this.leftPos + 91, this.topPos + 7, 95, 18,
                Component.translatable("vcoins.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setHint(Component.translatable("vcoins.search"));
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(Component.translatable("vcoins.tab.black_market"), button ->
                        selectCategory(ShopCategory.BLACK_MARKET))
                .bounds(this.leftPos + 31, this.topPos + 126, 50, 14)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("vcoins.buyback"), button ->
                        selectCategory(ShopCategory.BUYBACK))
                .bounds(this.leftPos + 83, this.topPos + 126, 51, 14)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("vcoins.duplicate.open"), button ->
                        ClientPlayNetworking.send(new OpenDuplicatePayload()))
                .bounds(this.leftPos + 136, this.topPos + 126, 50, 14)
                .build());

        // Opening the market should be enough to start typing a search.
        this.setInitialFocus(this.searchBox);
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

        this.selectedCategory = category;
        this.scrollPosition = 0.0f;
        this.lastScrollOffset = 0;
        this.menu.setCategory(category);
        ClientPlayNetworking.send(new ShopActionPayload("TAB", category.name()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractBackground(extractor, mouseX, mouseY, delta);
        drawTabs(extractor);
        drawPanel(extractor);
        drawSlotGrid(extractor, 9, 34, VTradeScreenHandler.SHOP_COLUMNS, VTradeScreenHandler.SHOP_ROWS);
        drawSlotGrid(extractor, 9, 143, 9, 3);
        drawSlotGrid(extractor, 9, 201, 9, 1);
        drawScrollbar(extractor);

        long balance = this.minecraft != null && this.minecraft.player != null
                ? VCoinsState.getCoins(this.minecraft.player.getUUID())
                : 0L;
        extractor.text(this.font, Component.translatable("vcoins.balance_short", formatCompactNumber(balance)),
                this.leftPos + 9, this.topPos + 18, 0xFFE8B829, true);
        extractor.text(this.font, Component.translatable("vcoins.inventory"),
                this.leftPos + 9, this.topPos + 130, 0xFF404040, false);
    }

    private void drawPanel(GuiGraphicsExtractor extractor) {
        InventoryTextures.panel(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);

        Component title = this.selectedCategory == ShopCategory.BLACK_MARKET
                ? Component.translatable("vcoins.black_market.title")
                : Component.translatable("vcoins.title");

        extractor.text(this.font, title,
                this.leftPos + 9, this.topPos + 7,
                this.selectedCategory == ShopCategory.BLACK_MARKET ? 0xFF8A2BE2 : 0xFF404040, false);
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
            Identifier sprite = Identifier.withDefaultNamespace("container/creative_inventory/tab_"
                    + row + (selected ? "_selected_" : "_unselected_") + position);
            extractor.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                    bounds.x(), bounds.y(), bounds.width(), bounds.height());
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
        Identifier sprite = Identifier.withDefaultNamespace("container/creative_inventory/"
                + (enabled ? "scroller" : "scroller_disabled"));
        extractor.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, trackX, thumbY, 12, SCROLL_THUMB_HEIGHT);
    }
    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        // All labels are positioned explicitly in extractBackground.
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack stack = this.hoveredSlot.getItem();
            List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(stack));
            long buyPrice = VCoinsPricing.getPrice(stack);

            if (this.hoveredSlot.index < VTradeScreenHandler.SHOP_SLOT_COUNT) {
                tooltip.add(Component.empty());
                if (this.selectedCategory == ShopCategory.BUYBACK) {
                    long totalPrice = safeMultiply(VCoinsPricing.getBuybackPrice(stack), stack.getCount());
                    if (buyPrice > 0) {
                        tooltip.add(Component.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                                .withStyle(ChatFormatting.YELLOW));
                    }
                    tooltip.add(Component.translatable("vcoins.tooltip.buyback", formatNumber(totalPrice))
                            .withStyle(ChatFormatting.GOLD));
                } else {
                    if (this.selectedCategory == ShopCategory.BLACK_MARKET) {
                        tooltip.add(Component.translatable("vcoins.black_market.merchant_tag")
                                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                    }
                    tooltip.add(Component.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                            .withStyle(ChatFormatting.YELLOW));
                    tooltip.add(Component.translatable("vcoins.tooltip.buy_left").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("vcoins.tooltip.buy_shift").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("vcoins.tooltip.buy_right", stack.getMaxStackSize())
                            .withStyle(ChatFormatting.GRAY));
                }
            } else {
                long sellPrice = VCoinsPricing.getSellPrice(stack);
                if (buyPrice > 0 || sellPrice > 0) {
                    tooltip.add(Component.empty());
                    if (buyPrice > 0) {
                        tooltip.add(Component.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                                .withStyle(ChatFormatting.YELLOW));
                    }
                }
                if (sellPrice > 0) {
                    tooltip.add(Component.translatable("vcoins.tooltip.sell_price", formatNumber(sellPrice))
                            .withStyle(ChatFormatting.GREEN));
                    tooltip.add(Component.translatable("vcoins.tooltip.sell_shift").withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("vcoins.tooltip.sell_drag").withStyle(ChatFormatting.GRAY));
                }
            }

            extractor.setTooltipForNextFrame(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
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

        super.extractTooltip(extractor, mouseX, mouseY);
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
                ClientPlayNetworking.send(new ShopTransactionPayload(shopSlot, buyStack));
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
