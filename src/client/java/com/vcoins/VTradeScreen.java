package com.vcoins;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VTradeScreen extends HandledScreen<VTradeScreenHandler> {
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_MID = 0xFF8B8B8B;
    private static final int PANEL_DARK = 0xFF373737;
    private static final int SLOT_COLOR = 0xFF8B8B8B;
    private static final int TAB_WIDTH = 27;
    private static final int TAB_HEIGHT = 28;
    private static final int TAB_GAP = 1;
    private static final int TOP_TAB_COUNT = 7;
    private static final int SCROLLBAR_X = 176;
    private static final int SCROLLBAR_Y = 34;
    private static final int SCROLLBAR_HEIGHT = 89;
    private static final int SCROLL_THUMB_HEIGHT = 15;

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

    private TextFieldWidget searchBox;
    private ShopCategory selectedCategory = ShopCategory.ALL;
    private float scrollPosition;
    private boolean scrolling;
    private int lastScrollOffset = -1;

    public VTradeScreen(VTradeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 195;
        this.backgroundHeight = 222;
    }

    @Override
    protected void init() {
        super.init();

        this.searchBox = new TextFieldWidget(this.textRenderer, this.x + 91, this.y + 7, 95, 18,
                Text.translatable("vcoins.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setPlaceholder(Text.translatable("vcoins.search"));
        this.searchBox.setChangedListener(this::onSearchChanged);
        this.addDrawableChild(this.searchBox);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("vcoins.buyback"), button ->
                        selectCategory(ShopCategory.BUYBACK))
                .dimensions(this.x + 83, this.y + 126, 51, 14)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("vcoins.duplicate.open"), button ->
                        ClientPlayNetworking.send(new OpenDuplicatePayload()))
                .dimensions(this.x + 136, this.y + 126, 50, 14)
                .build());
    }

    private void onSearchChanged(String query) {
        this.scrollPosition = 0.0f;
        this.lastScrollOffset = 0;
        this.handler.setSearchQuery(query);
        ClientPlayNetworking.send(new ShopActionPayload("SEARCH", query));
    }

    private void selectCategory(ShopCategory category) {
        if (this.selectedCategory == category) {
            return;
        }

        this.selectedCategory = category;
        this.scrollPosition = 0.0f;
        this.lastScrollOffset = 0;
        this.handler.setCategory(category);
        ClientPlayNetworking.send(new ShopActionPayload("TAB", category.name()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        drawTabs(context);
        drawPanel(context);
        drawSlotGrid(context, 9, 34, VTradeScreenHandler.SHOP_COLUMNS, VTradeScreenHandler.SHOP_ROWS);
        drawSlotGrid(context, 9, 143, 9, 3);
        drawSlotGrid(context, 9, 201, 9, 1);
        drawScrollbar(context);

        long balance = this.client != null && this.client.player != null
                ? VCoinsState.getCoins(this.client.player.getUuid())
                : 0L;
        context.drawText(this.textRenderer, Text.translatable("vcoins.balance_short", formatCompactNumber(balance)),
                this.x + 9, this.y + 18, 0xFFE8B829, true);
        context.drawText(this.textRenderer, Text.translatable("vcoins.inventory"),
                this.x + 9, this.y + 130, 0xFF404040, false);
    }

    private void drawPanel(DrawContext context) {
        context.fill(this.x, this.y, this.x + this.backgroundWidth, this.y + this.backgroundHeight, PANEL_COLOR);
        context.fill(this.x, this.y, this.x + this.backgroundWidth, this.y + 1, PANEL_LIGHT);
        context.fill(this.x, this.y, this.x + 1, this.y + this.backgroundHeight, PANEL_LIGHT);
        context.fill(this.x, this.y + this.backgroundHeight - 1,
                this.x + this.backgroundWidth, this.y + this.backgroundHeight, PANEL_DARK);
        context.fill(this.x + this.backgroundWidth - 1, this.y,
                this.x + this.backgroundWidth, this.y + this.backgroundHeight, PANEL_DARK);

        context.drawText(this.textRenderer, Text.translatable("vcoins.title"),
                this.x + 9, this.y + 7, 0xFF404040, false);
    }

    private void drawSlotGrid(DrawContext context, int relativeX, int relativeY, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int slotX = this.x + relativeX + column * 18 - 1;
                int slotY = this.y + relativeY + row * 18 - 1;
                context.fill(slotX, slotY, slotX + 18, slotY + 18, PANEL_DARK);
                context.fill(slotX + 1, slotY + 1, slotX + 18, slotY + 18, PANEL_LIGHT);
                context.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, SLOT_COLOR);
            }
        }
    }

    private void drawTabs(DrawContext context) {
        for (int index = 0; index < TABS.length; index++) {
            TabBounds bounds = getTabBounds(index);
            boolean selected = TABS[index] == this.selectedCategory;
            int color = selected ? PANEL_COLOR : 0xFFA6A6A6;

            context.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL_DARK);
            context.fill(bounds.x() + 1, bounds.y() + 1,
                    bounds.x() + bounds.width() - 1, bounds.y() + bounds.height() - 1, color);
            context.fill(bounds.x() + 1, bounds.y() + 1,
                    bounds.x() + bounds.width() - 1, bounds.y() + 2, PANEL_LIGHT);
            context.fill(bounds.x() + 1, bounds.y() + 1,
                    bounds.x() + 2, bounds.y() + bounds.height() - 1, PANEL_LIGHT);

            if (selected) {
                if (index < TOP_TAB_COUNT) {
                    context.fill(bounds.x() + 1, bounds.y() + bounds.height() - 2,
                            bounds.x() + bounds.width() - 1, bounds.y() + bounds.height(), PANEL_COLOR);
                } else {
                    context.fill(bounds.x() + 1, bounds.y(),
                            bounds.x() + bounds.width() - 1, bounds.y() + 2, PANEL_COLOR);
                }
            }

            Item icon = getTabIcon(TABS[index]);
            context.drawItem(new ItemStack(icon), bounds.x() + 5, bounds.y() + 6);
        }
    }

    private void drawScrollbar(DrawContext context) {
        int trackX = this.x + SCROLLBAR_X;
        int trackY = this.y + SCROLLBAR_Y;
        int maxRows = this.handler.getMaxRows();
        boolean enabled = maxRows > 0;

        context.fill(trackX, trackY, trackX + 12, trackY + SCROLLBAR_HEIGHT, PANEL_DARK);
        context.fill(trackX + 1, trackY + 1, trackX + 11, trackY + SCROLLBAR_HEIGHT - 1, 0xFF555555);

        int travel = SCROLLBAR_HEIGHT - SCROLL_THUMB_HEIGHT;
        int thumbY = trackY + Math.round(this.scrollPosition * travel);
        int thumbColor = enabled ? PANEL_COLOR : 0xFF777777;
        context.fill(trackX, thumbY, trackX + 12, thumbY + SCROLL_THUMB_HEIGHT, PANEL_DARK);
        context.fill(trackX + 1, thumbY + 1, trackX + 11, thumbY + SCROLL_THUMB_HEIGHT - 1, thumbColor);
        context.fill(trackX + 2, thumbY + 2, trackX + 10, thumbY + 3, PANEL_LIGHT);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // All labels are positioned explicitly in drawBackground.
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext context, int mouseX, int mouseY) {
        if (this.focusedSlot != null && this.focusedSlot.hasStack()) {
            ItemStack stack = this.focusedSlot.getStack();
            List<Text> tooltip = new ArrayList<>(this.getTooltipFromItem(stack));
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            long buyPrice = VCoinsPricing.getPrice(itemId);

            if (this.focusedSlot.id < VTradeScreenHandler.SHOP_SLOT_COUNT) {
                tooltip.add(Text.empty());
                if (this.selectedCategory == ShopCategory.BUYBACK) {
                    long totalPrice = safeMultiply(VCoinsPricing.getBuybackPrice(itemId), stack.getCount());
                    if (buyPrice > 0) {
                        tooltip.add(Text.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                                .formatted(Formatting.YELLOW));
                    }
                    tooltip.add(Text.translatable("vcoins.tooltip.buyback", formatNumber(totalPrice))
                            .formatted(Formatting.GOLD));
                } else {
                    tooltip.add(Text.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                            .formatted(Formatting.YELLOW));
                    tooltip.add(Text.translatable("vcoins.tooltip.buy_left").formatted(Formatting.GRAY));
                    tooltip.add(Text.translatable("vcoins.tooltip.buy_shift").formatted(Formatting.GRAY));
                    tooltip.add(Text.translatable("vcoins.tooltip.buy_right", stack.getMaxCount())
                            .formatted(Formatting.GRAY));
                }
            } else {
                long sellPrice = VCoinsPricing.getSellPrice(itemId);
                if (buyPrice > 0 || sellPrice > 0) {
                    tooltip.add(Text.empty());
                    if (buyPrice > 0) {
                        tooltip.add(Text.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                                .formatted(Formatting.YELLOW));
                    }
                }
                if (sellPrice > 0) {
                    tooltip.add(Text.translatable("vcoins.tooltip.sell_price", formatNumber(sellPrice))
                            .formatted(Formatting.GREEN));
                    tooltip.add(Text.translatable("vcoins.tooltip.sell_shift").formatted(Formatting.GRAY));
                    tooltip.add(Text.translatable("vcoins.tooltip.sell_drag").formatted(Formatting.GRAY));
                }
            }

            context.drawTooltip(this.textRenderer, tooltip, stack.getTooltipData(), mouseX, mouseY);
            return;
        }

        ShopCategory hoveredTab = getHoveredTab(mouseX, mouseY);
        if (hoveredTab != null) {
            context.drawTooltip(this.textRenderer, getTabName(hoveredTab), mouseX, mouseY);
            return;
        }

        if (mouseX >= this.x + 8 && mouseX < this.x + 89 && mouseY >= this.y + 17 && mouseY < this.y + 28
                && this.client != null && this.client.player != null) {
            long balance = VCoinsState.getCoins(this.client.player.getUuid());
            context.drawTooltip(this.textRenderer, Text.translatable("vcoins.balance", formatNumber(balance)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();

        for (int index = 0; index < TABS.length; index++) {
            TabBounds bounds = getTabBounds(index);
            if (bounds.contains(mouseX, mouseY)) {
                selectCategory(TABS[index]);
                return true;
            }
        }

        int trackX = this.x + SCROLLBAR_X;
        int trackY = this.y + SCROLLBAR_Y;
        if (mouseX >= trackX && mouseX < trackX + 12
                && mouseY >= trackY && mouseY < trackY + SCROLLBAR_HEIGHT
                && this.handler.getMaxRows() > 0) {
            this.scrolling = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        if (this.scrolling) {
            updateScrollFromMouse(click.y());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.scrolling = false;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxRows = this.handler.getMaxRows();
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
        int maxRows = this.handler.getMaxRows();
        if (maxRows <= 0) {
            return;
        }

        int trackY = this.y + SCROLLBAR_Y;
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
        this.handler.setScrollOffset(offset);
        ClientPlayNetworking.send(new ShopActionPayload("SCROLL", Integer.toString(offset)));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        if (this.searchBox.keyPressed(keyInput)) {
            return true;
        }
        if (this.searchBox.isFocused() && keyInput.key() != GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput charInput) {
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
        int startX = this.x + (this.backgroundWidth - totalWidth) / 2;
        int tabX = startX + rowIndex * (TAB_WIDTH + TAB_GAP);
        int tabY = top ? this.y - TAB_HEIGHT + 4 : this.y + this.backgroundHeight - 4;
        return new TabBounds(tabX, tabY, TAB_WIDTH, TAB_HEIGHT);
    }

    private static Item getTabIcon(ShopCategory category) {
        return switch (category) {
            case ALL -> Items.COMPASS;
            case BUILDING -> Items.BRICKS;
            case COLORED -> Items.CYAN_WOOL;
            case NATURAL -> Items.GRASS_BLOCK;
            case FUNCTIONAL -> Items.CRAFTING_TABLE;
            case REDSTONE -> Items.REDSTONE;
            case TOOLS -> Items.DIAMOND_PICKAXE;
            case COMBAT -> Items.DIAMOND_SWORD;
            case FOOD -> Items.GOLDEN_APPLE;
            case INGREDIENTS -> Items.IRON_INGOT;
            case SPAWN_EGGS -> Items.PIG_SPAWN_EGG;
            case MISC -> Items.BUNDLE;
            case BUYBACK -> Items.CHEST;
        };
    }

    private static Text getTabName(ShopCategory category) {
        return Text.translatable("vcoins.tab." + category.name().toLowerCase(Locale.ROOT));
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
