package com.vcoins;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Prediction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class VTradeScreenHandler extends AbstractContainerMenu {
    public static final int SHOP_COLUMNS = 9;
    public static final int SHOP_ROWS = 5;
    public static final int SHOP_SLOT_COUNT = SHOP_COLUMNS * SHOP_ROWS;

    private static final int SHOP_X = 9;
    private static final int SHOP_Y = 34;
    private static final int PLAYER_X = 9;
    private static final int PLAYER_INVENTORY_Y = 143;
    private static final int PLAYER_HOTBAR_Y = 201;
    private static final int MAX_BUYBACK_ENTRIES = SHOP_SLOT_COUNT;

    private static final Map<UUID, LinkedList<ItemStack>> BUYBACK = new ConcurrentHashMap<>();

    private final Inventory playerInventory;
    private final Player player;
    private final Container shopInventory = new SimpleContainer(SHOP_SLOT_COUNT);
    private final List<ItemStack> creativeCatalog;

    private ShopCategory currentCategory = ShopCategory.ALL;
    private String searchQuery = "";
    private int scrollOffset;
    private int maxRows;

    public VTradeScreenHandler(int syncId, Inventory playerInventory) {
        super(VCoinsMod.VTRADE_SCREEN_HANDLER, syncId);
        this.playerInventory = playerInventory;
        this.player = playerInventory.player;
        this.creativeCatalog = buildCreativeCatalog();

        for (int row = 0; row < SHOP_ROWS; row++) {
            for (int column = 0; column < SHOP_COLUMNS; column++) {
                this.addSlot(new Slot(shopInventory, column + row * SHOP_COLUMNS,
                        SHOP_X + column * 18, SHOP_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(Player playerEntity) {
                        return false;
                    }
                });
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, PLAYER_X + column * 18, PLAYER_HOTBAR_Y));
        }

        updateShopItems();
    }

    public static void addBuyback(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        LinkedList<ItemStack> history = BUYBACK.computeIfAbsent(player.getUUID(), ignored -> new LinkedList<>());
        history.addFirst(stack.copy());
        while (history.size() > MAX_BUYBACK_ENTRIES) {
            history.removeLast();
        }
    }

    public ShopCategory getCurrentCategory() {
        return currentCategory;
    }

    public void setCategory(ShopCategory category) {
        this.currentCategory = category == null ? ShopCategory.ALL : category;
        this.scrollOffset = 0;
        updateShopItems();
    }

    public void setSearchQuery(String query) {
        String safeQuery = query == null ? "" : query;
        if (safeQuery.length() > 50) {
            safeQuery = safeQuery.substring(0, 50);
        }
        this.searchQuery = safeQuery.trim().toLowerCase(Locale.ROOT);
        this.scrollOffset = 0;
        updateShopItems();
    }

    public void setScrollOffset(int offset) {
        this.scrollOffset = Math.max(0, Math.min(offset, getMaxRows()));
        updateShopItems();
    }

    public void updateShopItems() {
        List<ItemStack> items = getFilteredItems();
        this.maxRows = calculateMaxRows(items.size());
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxRows));
        int startIndex = this.scrollOffset * SHOP_COLUMNS;

        shopInventory.clearContent();
        for (int slot = 0; slot < SHOP_SLOT_COUNT; slot++) {
            int itemIndex = startIndex + slot;
            if (itemIndex < items.size()) {
                shopInventory.setItem(slot, items.get(itemIndex).copy());
            }
        }
    }

    public int getMaxRows() {
        return this.maxRows;
    }

    private int calculateMaxRows(int itemCount) {
        int rows = (itemCount + SHOP_COLUMNS - 1) / SHOP_COLUMNS;
        return Math.max(0, rows - SHOP_ROWS);
    }

    private List<ItemStack> getFilteredItems() {
        List<ItemStack> items = new ArrayList<>();

        if (currentCategory == ShopCategory.BUYBACK) {
            for (ItemStack stack : BUYBACK.getOrDefault(player.getUUID(), new LinkedList<>())) {
                if (matchesSearch(stack)) {
                    items.add(stack.copy());
                }
            }
            return items;
        }

        if (currentCategory == ShopCategory.BLACK_MARKET) {
            for (ItemStack stack : VBlackMarket.getDailyItems()) {
                if (matchesSearch(stack)) {
                    items.add(stack.copy());
                }
            }
            return items;
        }

        for (ItemStack creativeStack : creativeCatalog) {
            Item item = creativeStack.getItem();
            if (!VCoinsPricing.isTradeable(item) || !VCoinsPricing.matchesCategory(item, currentCategory) || !matchesSearch(creativeStack)) {
                continue;
            }
            items.add(creativeStack.copy());
        }

        return items;
    }

    private List<ItemStack> buildCreativeCatalog() {
        List<ItemStack> catalogue = new ArrayList<>();
        Set<Item> representedItems = new HashSet<>();

        try {
            CreativeModeTabs.tryRebuildTabContents(player.level().enabledFeatures(), false,
                    player.level().registryAccess());
            for (ItemStack stack : CreativeModeTabs.searchTab().getSearchTabDisplayItems()) {
                if (!stack.isEmpty()) {
                    catalogue.add(stack.copy());
                    representedItems.add(stack.getItem());
                }
            }
        } catch (RuntimeException exception) {
            VCoinsMod.LOGGER.warn("Could not build the Creative catalogue; falling back to the item registry", exception);
        }

        // Items added by mods are not required to join a Creative tab. Append any
        // missing registry entries so the shop remains complete for modpacks.
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR && representedItems.add(item)) {
                catalogue.add(new ItemStack(item));
            }
        }
        return List.copyOf(catalogue);
    }

    private boolean matchesSearch(ItemStack stack) {
        if (searchQuery.isEmpty()) {
            return true;
        }

        Item item = stack.getItem();
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        String fullId = id.toString().toLowerCase(Locale.ROOT);
        String translatedName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return fullId.contains(searchQuery) || id.getPath().contains(searchQuery) || translatedName.contains(searchQuery);
    }

    private long getBuyPrice(ItemStack stack) {
        return VCoinsPricing.getPrice(stack);
    }

    private long getSellPrice(ItemStack stack) {
        return VCoinsPricing.getSellPrice(stack);
    }

    private long getBuybackPrice(ItemStack stack) {
        return VCoinsPricing.getBuybackPrice(stack);
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        if (slotIndex >= 0 && slotIndex < SHOP_SLOT_COUNT) {
            // Display slots never transact through Minecraft's slot-click pipeline.
            // Actual shop clicks use ShopTransactionPayload, which keeps scrolling,
            // dragging, hotbar swaps, and synthetic slot actions side-effect free.
            return;
        }

        if (actionType == ContainerInput.QUICK_MOVE && slotIndex >= SHOP_SLOT_COUNT) {
            if (!player.level().isClientSide()) {
                sellPlayerSlot(player, slotIndex);
            }
            return;
        }

        super.clicked(slotIndex, button, actionType, player);
    }

    public void handleTransaction(ServerPlayer player, int slotIndex, boolean buyStack) {
        if (player != this.player || slotIndex < 0 || slotIndex >= SHOP_SLOT_COUNT) {
            return;
        }

        ItemStack cursorStack = getCarried();
        if (!cursorStack.isEmpty()) {
            sellCursorStack(player, cursorStack);
            return;
        }

        buyFromShop(player, slotIndex, buyStack);
    }

    private void sellCursorStack(Player player, ItemStack cursorStack) {
        long unitPrice = getSellPrice(cursorStack);
        if (unitPrice <= 0) {
            rejectTrade(player);
            return;
        }

        long total = safeMultiply(unitPrice, cursorStack.getCount());
        VCoinsState.addCoins(player.getUUID(), total);
        VCoinsMod.syncCoins((ServerPlayer) player);
        addBuyback(player, cursorStack);
        this.setCarried(ItemStack.EMPTY);
        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendOverlayMessage(Component.translatable("vcoins.message.sell_success",
                cursorStack.getCount(), cursorStack.getHoverName(), formatNumber(total)).withStyle(ChatFormatting.GREEN));
        updateShopItems();
    }

    private void buyFromShop(Player player, int slotIndex, boolean buyStack) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack displayedStack = slot.getItem();
        if (currentCategory == ShopCategory.BUYBACK) {
            long unitPrice = getBuybackPrice(displayedStack);
            if (unitPrice <= 0) {
                return;
            }
            long total = safeMultiply(unitPrice, displayedStack.getCount());
            if (!tryCharge(player, total)) {
                return;
            }

            player.getInventory().placeItemBackInInventory(displayedStack.copy(), Prediction.SERVER_ONLY);
            LinkedList<ItemStack> history = BUYBACK.get(player.getUUID());
            if (history != null) {
                removeVisibleBuybackEntry(history, slotIndex);
            }
            player.playSound(SoundEvents.ITEM_PICKUP, 1.0f, 1.0f);
            player.sendOverlayMessage(Component.translatable("vcoins.message.buyback_success",
                    displayedStack.getCount(), displayedStack.getHoverName(), formatNumber(total)).withStyle(ChatFormatting.GREEN));
            updateShopItems();
            return;
        }

        long unitPrice = getBuyPrice(displayedStack);
        if (unitPrice <= 0) {
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(displayedStack.getItem()).toString();
        boolean isTool = VCoinsPricing.getCategory(itemId) == ShopCategory.TOOLS;
        int requestedAmount = buyStack && !isTool ? displayedStack.getMaxStackSize() : 1;
        int amount = Math.max(1, Math.min(requestedAmount, displayedStack.getMaxStackSize()));
        long total = safeMultiply(unitPrice, amount);
        if (!tryCharge(player, total)) {
            return;
        }

        ItemStack purchased = displayedStack.copy();
        purchased.setCount(amount);
        player.getInventory().placeItemBackInInventory(purchased, Prediction.SERVER_ONLY);
        player.playSound(SoundEvents.ITEM_PICKUP, 1.0f, 1.0f);
        player.sendOverlayMessage(Component.translatable("vcoins.message.buy_success",
                amount, displayedStack.getHoverName(), formatNumber(total)).withStyle(ChatFormatting.GREEN));
        updateShopItems();
    }

    private void removeVisibleBuybackEntry(LinkedList<ItemStack> history, int visibleIndex) {
        int currentVisibleIndex = 0;
        var iterator = history.iterator();
        while (iterator.hasNext()) {
            ItemStack historyStack = iterator.next();
            if (!matchesSearch(historyStack)) {
                continue;
            }
            if (currentVisibleIndex == visibleIndex) {
                iterator.remove();
                return;
            }
            currentVisibleIndex++;
        }
    }

    private boolean tryCharge(Player player, long amount) {
        if (amount <= 0 || VCoinsState.getCoins(player.getUUID()) < amount) {
            player.sendOverlayMessage(Component.translatable("vcoins.message.not_enough",
                    formatNumber(amount), formatNumber(VCoinsState.getCoins(player.getUUID()))).withStyle(ChatFormatting.RED));
            player.playSound(SoundEvents.VILLAGER_NO, 0.9f, 1.0f);
            return false;
        }
        VCoinsState.removeCoins(player.getUUID(), amount);
        VCoinsMod.syncCoins((ServerPlayer) player);
        return true;
    }

    private void sellPlayerSlot(Player player, int slotIndex) {
        if (slotIndex >= this.slots.size()) {
            return;
        }

        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return;
        }

        ItemStack stack = slot.getItem();
        long unitPrice = getSellPrice(stack);
        if (unitPrice <= 0) {
            rejectTrade(player);
            return;
        }

        long total = safeMultiply(unitPrice, stack.getCount());
        VCoinsState.addCoins(player.getUUID(), total);
        VCoinsMod.syncCoins((ServerPlayer) player);
        addBuyback(player, stack);
        slot.setByPlayer(ItemStack.EMPTY);
        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendOverlayMessage(Component.translatable("vcoins.message.sell_success",
                stack.getCount(), stack.getHoverName(), formatNumber(total)).withStyle(ChatFormatting.GREEN));
        updateShopItems();
    }

    private static void rejectTrade(Player player) {
        player.sendOverlayMessage(Component.translatable("vcoins.message.cannot_trade").withStyle(ChatFormatting.RED));
        player.playSound(SoundEvents.VILLAGER_NO, 0.9f, 1.0f);
    }

    private static long safeMultiply(long price, int count) {
        try {
            return Math.multiplyExact(price, (long) count);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public void sellAll() {
        if (player.level().isClientSide()) {
            return;
        }

        long totalEarned = 0;
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = playerInventory.getItem(inventorySlot);
            if (stack.isEmpty()) {
                continue;
            }

            long unitPrice = getSellPrice(stack);
            if (unitPrice <= 0) {
                continue;
            }

            long stackValue = safeMultiply(unitPrice, stack.getCount());
            totalEarned = totalEarned > Long.MAX_VALUE - stackValue ? Long.MAX_VALUE : totalEarned + stackValue;
            addBuyback(player, stack);
            playerInventory.setItem(inventorySlot, ItemStack.EMPTY);
        }

        if (totalEarned > 0) {
            VCoinsState.addCoins(player.getUUID(), totalEarned);
            VCoinsMod.syncCoins((ServerPlayer) player);
            player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            updateShopItems();
        }
    }
}
