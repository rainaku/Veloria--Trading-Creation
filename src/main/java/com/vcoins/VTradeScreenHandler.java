package com.vcoins;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VTradeScreenHandler extends ScreenHandler {
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

    private final PlayerInventory playerInventory;
    private final PlayerEntity player;
    private final Inventory shopInventory = new SimpleInventory(SHOP_SLOT_COUNT);
    private final List<ItemStack> creativeCatalog;

    private ShopCategory currentCategory = ShopCategory.ALL;
    private String searchQuery = "";
    private int scrollOffset;
    private int maxRows;

    public VTradeScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(VCoinsMod.VTRADE_SCREEN_HANDLER, syncId);
        this.playerInventory = playerInventory;
        this.player = playerInventory.player;
        this.creativeCatalog = buildCreativeCatalog();

        for (int row = 0; row < SHOP_ROWS; row++) {
            for (int column = 0; column < SHOP_COLUMNS; column++) {
                this.addSlot(new Slot(shopInventory, column + row * SHOP_COLUMNS,
                        SHOP_X + column * 18, SHOP_Y + row * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean canTakeItems(PlayerEntity playerEntity) {
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

    public static void addBuyback(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        LinkedList<ItemStack> history = BUYBACK.computeIfAbsent(player.getUuid(), ignored -> new LinkedList<>());
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

        shopInventory.clear();
        for (int slot = 0; slot < SHOP_SLOT_COUNT; slot++) {
            int itemIndex = startIndex + slot;
            if (itemIndex < items.size()) {
                shopInventory.setStack(slot, items.get(itemIndex).copy());
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
            for (ItemStack stack : BUYBACK.getOrDefault(player.getUuid(), new LinkedList<>())) {
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
            ItemGroups.updateDisplayContext(player.getEntityWorld().getEnabledFeatures(), false,
                    player.getEntityWorld().getRegistryManager());
            for (ItemStack stack : ItemGroups.getSearchGroup().getSearchTabStacks()) {
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
        for (Item item : Registries.ITEM) {
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
        Identifier id = Registries.ITEM.getId(item);
        String fullId = id.toString().toLowerCase(Locale.ROOT);
        String translatedName = stack.getName().getString().toLowerCase(Locale.ROOT);
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
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex >= 0 && slotIndex < SHOP_SLOT_COUNT) {
            // Display slots never transact through Minecraft's slot-click pipeline.
            // Actual shop clicks use ShopTransactionPayload, which keeps scrolling,
            // dragging, hotbar swaps, and synthetic slot actions side-effect free.
            return;
        }

        if (actionType == SlotActionType.QUICK_MOVE && slotIndex >= SHOP_SLOT_COUNT) {
            if (!player.getEntityWorld().isClient()) {
                sellPlayerSlot(player, slotIndex);
            }
            return;
        }

        super.onSlotClick(slotIndex, button, actionType, player);
    }

    public void handleTransaction(ServerPlayerEntity player, int slotIndex, boolean buyStack) {
        if (player != this.player || slotIndex < 0 || slotIndex >= SHOP_SLOT_COUNT) {
            return;
        }

        ItemStack cursorStack = getCursorStack();
        if (!cursorStack.isEmpty()) {
            sellCursorStack(player, cursorStack);
            return;
        }

        buyFromShop(player, slotIndex, buyStack);
    }

    private void sellCursorStack(PlayerEntity player, ItemStack cursorStack) {
        long unitPrice = getSellPrice(cursorStack);
        if (unitPrice <= 0) {
            rejectTrade(player);
            return;
        }

        long total = safeMultiply(unitPrice, cursorStack.getCount());
        VCoinsState.addCoins(player.getUuid(), total);
        VCoinsMod.syncCoins((ServerPlayerEntity) player);
        addBuyback(player, cursorStack);
        this.setCursorStack(ItemStack.EMPTY);
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("vcoins.message.sell_success",
                cursorStack.getCount(), cursorStack.getName(), formatNumber(total)).formatted(Formatting.GREEN), true);
        updateShopItems();
    }

    private void buyFromShop(PlayerEntity player, int slotIndex, boolean buyStack) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasStack()) {
            return;
        }

        ItemStack displayedStack = slot.getStack();
        if (currentCategory == ShopCategory.BUYBACK) {
            long unitPrice = getBuybackPrice(displayedStack);
            if (unitPrice <= 0) {
                return;
            }
            long total = safeMultiply(unitPrice, displayedStack.getCount());
            if (!tryCharge(player, total)) {
                return;
            }

            player.getInventory().offerOrDrop(displayedStack.copy());
            LinkedList<ItemStack> history = BUYBACK.get(player.getUuid());
            if (history != null) {
                removeVisibleBuybackEntry(history, slotIndex);
            }
            player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            player.sendMessage(Text.translatable("vcoins.message.buyback_success",
                    displayedStack.getCount(), displayedStack.getName(), formatNumber(total)).formatted(Formatting.GREEN), true);
            updateShopItems();
            return;
        }

        long unitPrice = getBuyPrice(displayedStack);
        if (unitPrice <= 0) {
            return;
        }

        String itemId = Registries.ITEM.getId(displayedStack.getItem()).toString();
        boolean isTool = VCoinsPricing.getCategory(itemId) == ShopCategory.TOOLS;
        int requestedAmount = buyStack && !isTool ? displayedStack.getMaxCount() : 1;
        int amount = Math.max(1, Math.min(requestedAmount, displayedStack.getMaxCount()));
        long total = safeMultiply(unitPrice, amount);
        if (!tryCharge(player, total)) {
            return;
        }

        ItemStack purchased = displayedStack.copy();
        purchased.setCount(amount);
        player.getInventory().offerOrDrop(purchased);
        player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("vcoins.message.buy_success",
                amount, displayedStack.getName(), formatNumber(total)).formatted(Formatting.GREEN), true);
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

    private boolean tryCharge(PlayerEntity player, long amount) {
        if (amount <= 0 || VCoinsState.getCoins(player.getUuid()) < amount) {
            player.sendMessage(Text.translatable("vcoins.message.not_enough",
                    formatNumber(amount), formatNumber(VCoinsState.getCoins(player.getUuid()))).formatted(Formatting.RED), true);
            player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
            return false;
        }
        VCoinsState.removeCoins(player.getUuid(), amount);
        VCoinsMod.syncCoins((ServerPlayerEntity) player);
        return true;
    }

    private void sellPlayerSlot(PlayerEntity player, int slotIndex) {
        if (slotIndex >= this.slots.size()) {
            return;
        }

        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasStack()) {
            return;
        }

        ItemStack stack = slot.getStack();
        long unitPrice = getSellPrice(stack);
        if (unitPrice <= 0) {
            rejectTrade(player);
            return;
        }

        long total = safeMultiply(unitPrice, stack.getCount());
        VCoinsState.addCoins(player.getUuid(), total);
        VCoinsMod.syncCoins((ServerPlayerEntity) player);
        addBuyback(player, stack);
        slot.setStack(ItemStack.EMPTY);
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("vcoins.message.sell_success",
                stack.getCount(), stack.getName(), formatNumber(total)).formatted(Formatting.GREEN), true);
        updateShopItems();
    }

    private static void rejectTrade(PlayerEntity player) {
        player.sendMessage(Text.translatable("vcoins.message.cannot_trade").formatted(Formatting.RED), true);
        player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
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
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    public void sellAll() {
        if (player.getEntityWorld().isClient()) {
            return;
        }

        long totalEarned = 0;
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = playerInventory.getStack(inventorySlot);
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
            playerInventory.setStack(inventorySlot, ItemStack.EMPTY);
        }

        if (totalEarned > 0) {
            VCoinsState.addCoins(player.getUuid(), totalEarned);
            VCoinsMod.syncCoins((ServerPlayerEntity) player);
            player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            updateShopItems();
        }
    }
}
