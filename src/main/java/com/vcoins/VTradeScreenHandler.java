package com.vcoins;

import java.util.ArrayList;
import java.util.Collection;
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
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;
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

    public static final int SHOP_X = 22;
    public static final int SHOP_Y = 38;
    public static final int PLAYER_X = 22;
    public static final int PLAYER_INVENTORY_Y = 166;
    public static final int PLAYER_HOTBAR_Y = 224;
    private static final int MAX_BUYBACK_ENTRIES = SHOP_SLOT_COUNT;

    private static final Map<UUID, LinkedList<BuybackEntry>> BUYBACK = new ConcurrentHashMap<>();
    private final net.minecraft.world.inventory.ContainerData buybackPrices =
            new net.minecraft.world.inventory.SimpleContainerData(SHOP_SLOT_COUNT * 4);

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
        addDataSlots(buybackPrices);
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

        if (this.player instanceof ServerPlayer serverPlayer) {
            VBlackMarket.syncToPlayer(serverPlayer);
        }
    }

    public boolean handleDuplicate(ServerPlayer player, int inventorySlotIndex) {
        if (player != this.player || inventorySlotIndex < 0 || inventorySlotIndex >= player.getInventory().getContainerSize()
                || !player.isAlive() || player.isRemoved()) {
            return false;
        }

        ItemStack sample = player.getInventory().getItem(inventorySlotIndex);
        if (!VDuplicatePricing.canDuplicate(sample)) {
            player.sendOverlayMessage(Component.translatable("vcoins.duplicate.invalid_item").withStyle(ChatFormatting.RED));
            sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        long coinCost = VDuplicatePricing.getCoinCost(sample);
        int levelCost = VDuplicatePricing.getExperienceLevelCost(sample);
        long balance = VCoinsState.getCoins(player.getUUID());

        if (balance < coinCost) {
            player.sendOverlayMessage(Component.translatable("vcoins.duplicate.not_enough_coins",
                    formatNumber(coinCost), formatNumber(balance)).withStyle(ChatFormatting.RED));
            sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        if (player.experienceLevel < levelCost) {
            player.sendOverlayMessage(Component.translatable("vcoins.duplicate.not_enough_xp",
                    levelCost, player.experienceLevel).withStyle(ChatFormatting.RED));
            sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        VCoinsState.removeCoins(player.getUUID(), coinCost);
        player.giveExperienceLevels(-levelCost);
        player.getInventory().placeItemBackInInventory(sample.copyWithCount(1), Prediction.SERVER_ONLY);
        VCoinsState.checkpoint(player);
        VCoinsMod.syncCoins(player);

        sendSoundToPlayer(player, SoundEvents.ANVIL_USE, 1.0f, 1.15f);
        sendSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, 0.65f, 1.55f);
        player.sendOverlayMessage(Component.translatable("vcoins.duplicate.success", sample.getHoverName(),
                formatNumber(coinCost), levelCost).withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static void clearBuybackHistory() { BUYBACK.clear(); }

    public static void addBuyback(Player player, ItemStack stack, long paidUnitPrice) {
        if (stack.isEmpty()) {
            return;
        }

        LinkedList<BuybackEntry> history = BUYBACK.computeIfAbsent(player.getUUID(), ignored -> new LinkedList<>());
        history.addFirst(new BuybackEntry(stack, paidUnitPrice));
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
        List<BuybackEntry> receipts = filteredBuyback();
        for (int slot = 0; slot < SHOP_SLOT_COUNT; slot++) {
            int itemIndex = startIndex + slot;
            if (itemIndex < items.size()) {
                shopInventory.setItem(slot, items.get(itemIndex).copy());
            }
            if (!player.level().isClientSide()) {
                long quote = currentCategory == ShopCategory.BUYBACK && itemIndex < receipts.size()
                        ? receipts.get(itemIndex).unitPrice() : 0;
                for (int word = 0; word < 4; word++)
                    buybackPrices.set(slot * 4 + word, (int) (quote >>> (word * 16)) & 65535);
            }
        }
    }

    public long getBuybackUnitPrice(int slot) {
        if (slot < 0 || slot >= SHOP_SLOT_COUNT) return 0;
        long value = 0;
        for (int word = 0; word < 4; word++)
            value |= (buybackPrices.get(slot * 4 + word) & 65535L) << (word * 16);
        return value;
    }

    private List<BuybackEntry> filteredBuyback() {
        return BUYBACK.getOrDefault(player.getUUID(), new LinkedList<>()).stream()
                .filter(receipt -> matchesSearch(receipt.stack())).toList();
    }

    public int getMaxRows() {
        return this.maxRows;
    }

    private int calculateMaxRows(int itemCount) {
        int rows = (itemCount + SHOP_COLUMNS - 1) / SHOP_COLUMNS;
        return Math.max(0, rows - SHOP_ROWS);
    }

    private List<ItemStack> getFilteredItems() {
        if (currentCategory == ShopCategory.BUYBACK) {
            return filteredBuyback().stream().map(BuybackEntry::stack).toList();
        }
        if (currentCategory == ShopCategory.BLACK_MARKET) {
            return filterList(VBlackMarket.getDailyItems());
        }

        List<ItemStack> items = new ArrayList<>();
        for (ItemStack creativeStack : creativeCatalog) {
            Item item = creativeStack.getItem();
            if (VCoinsPricing.isTradeable(item) && VCoinsPricing.matchesCategory(item, currentCategory) && matchesSearch(creativeStack)) {
                items.add(creativeStack.copy());
            }
        }
        return items;
    }

    private List<ItemStack> filterList(Collection<ItemStack> source) {
        List<ItemStack> filtered = new ArrayList<>();
        for (ItemStack stack : source) {
            if (matchesSearch(stack)) {
                filtered.add(stack.copy());
            }
        }
        return filtered;
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
        return VeloriaShopSearch.matches(stack, searchQuery);
    }

    private long getBuyPrice(ItemStack stack) {
        return VCoinsPricing.getPrice(stack);
    }

    private long getSellPrice(ItemStack stack) {
        return VCoinsPricing.getSellPrice(stack);
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
        if (player != this.player || slotIndex < 0 || slotIndex >= SHOP_SLOT_COUNT || !player.isAlive() || player.isRemoved()) {
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
        if (cursorStack.isEmpty() || cursorStack.getCount() <= 0) {
            return;
        }

        long unitPrice = getSellPrice(cursorStack);
        if (unitPrice <= 0) {
            rejectTrade(player);
            return;
        }

        String cursorItemId = VCoinsPricing.getSellMarketKey(cursorStack);
        int count = cursorStack.getCount();
        long total = safeMultiply(unitPrice, count);
        VCoinsState.addCoins(player.getUUID(), total);
        VCoinsMod.syncCoins((ServerPlayer) player);
        addBuyback(player, cursorStack.copy(), unitPrice);
        this.setCarried(ItemStack.EMPTY);
        VCoinsState.checkpoint((ServerPlayer) player);
        VMarketEngine.recordSell(cursorItemId, count);
        if (player.level().getServer() != null) {
            VMarketEngine.syncToActiveShoppers(player.level().getServer());
        }
        sendSoundToPlayer((ServerPlayer) player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.85f, 1.2f);
        sendSoundToPlayer((ServerPlayer) player, SoundEvents.BUNDLE_DROP_CONTENTS, 0.6f, 1.3f);
        player.sendOverlayMessage(Component.translatable("vcoins.message.sell_success",
                count, cursorStack.getHoverName(), formatNumber(total)).withStyle(ChatFormatting.GREEN));
        updateShopItems();
    }

    private void buyFromShop(Player player, int slotIndex, boolean buyStack) {
        if (currentCategory == ShopCategory.BUYBACK) {
            LinkedList<BuybackEntry> history = BUYBACK.get(player.getUUID());
            if (history == null) {
                return;
            }

            BuybackEntry receipt = null;
            synchronized (history) {
                int targetIndex = this.scrollOffset * SHOP_COLUMNS + slotIndex;
                int visibleIndex = 0;
                var iterator = history.iterator();
                while (iterator.hasNext()) {
                    BuybackEntry candidate = iterator.next();
                    if (matchesSearch(candidate.stack())) {
                        if (visibleIndex == targetIndex) {
                            receipt = candidate;
                            iterator.remove(); // Atomically pop to prevent duplicate buyback exploits
                            break;
                        }
                        visibleIndex++;
                    }
                }
            }

            if (receipt == null) {
                updateShopItems();
                return;
            }

            ItemStack targetHistoryStack = receipt.stack();
            long unitPrice = receipt.unitPrice();
            if (unitPrice <= 0) {
                synchronized (history) {
                    history.addFirst(receipt);
                }
                return;
            }

            long total = safeMultiply(unitPrice, targetHistoryStack.getCount());
            if (!tryCharge(player, total)) {
                // Restore item back into history if charge fails
                synchronized (history) {
                    history.addFirst(receipt);
                }
                updateShopItems();
                return;
            }

            player.getInventory().placeItemBackInInventory(targetHistoryStack.copy(), Prediction.SERVER_ONLY);
            VCoinsState.checkpoint((ServerPlayer) player);
            String buybackItemId = VCoinsPricing.getSellMarketKey(targetHistoryStack);
            VMarketEngine.recordBuy(buybackItemId, targetHistoryStack.getCount());
            if (player.level().getServer() != null) {
                VMarketEngine.syncToActiveShoppers(player.level().getServer());
            }
            sendSoundToPlayer((ServerPlayer) player, SoundEvents.ITEM_PICKUP, 0.9f, 1.15f);
            sendSoundToPlayer((ServerPlayer) player, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1.6f);
            player.sendOverlayMessage(Component.translatable("vcoins.message.buyback_success",
                    targetHistoryStack.getCount(), targetHistoryStack.getHoverName(), formatNumber(total)).withStyle(ChatFormatting.GREEN));
            updateShopItems();
            return;
        }

        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack displayedStack = slot.getItem();
        long unitPrice = getBuyPrice(displayedStack);
        if (unitPrice <= 0) {
            return;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(displayedStack.getItem()).toString();
        boolean isTool = VCoinsPricing.getCategory(itemId) == ShopCategory.TOOLS;
        int maxAllowed = displayedStack.getMaxStackSize();
        if (maxAllowed <= 0) {
            maxAllowed = 1;
        }
        int requestedAmount = buyStack && !isTool ? maxAllowed : 1;
        int amount = Math.max(1, Math.min(requestedAmount, maxAllowed));
        long total = safeMultiply(unitPrice, amount);
        if (!tryCharge(player, total)) {
            return;
        }

        ItemStack purchased = displayedStack.copy();
        purchased.setCount(amount);
        player.getInventory().placeItemBackInInventory(purchased, Prediction.SERVER_ONLY);
        VCoinsState.checkpoint((ServerPlayer) player);
        VMarketEngine.recordBuy(VCoinsPricing.getMarketKey(displayedStack), amount);
        if (player.level().getServer() != null) {
            VMarketEngine.syncToActiveShoppers(player.level().getServer());
        }
        sendSoundToPlayer((ServerPlayer) player, SoundEvents.ITEM_PICKUP, 0.9f, 1.25f);
        sendSoundToPlayer((ServerPlayer) player, SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 1.75f);
        player.sendOverlayMessage(Component.translatable("vcoins.message.buy_success",
                amount, displayedStack.getHoverName(), formatNumber(total)).withStyle(ChatFormatting.GREEN));
        updateShopItems();
    }

    private boolean tryCharge(Player player, long amount) {
        if (amount <= 0 || VCoinsState.getCoins(player.getUUID()) < amount) {
            player.sendOverlayMessage(Component.translatable("vcoins.message.not_enough",
                    formatNumber(amount), formatNumber(VCoinsState.getCoins(player.getUUID()))).withStyle(ChatFormatting.RED));
            sendSoundToPlayer((ServerPlayer) player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
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
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || stack.getCount() <= 0) {
            return;
        }

        long unitPrice = getSellPrice(stack);
        if (unitPrice <= 0) {
            rejectTrade(player);
            return;
        }

        String soldItemId = VCoinsPricing.getSellMarketKey(stack);
        int count = stack.getCount();
        long total = safeMultiply(unitPrice, count);
        VCoinsState.addCoins(player.getUUID(), total);
        VCoinsMod.syncCoins((ServerPlayer) player);
        addBuyback(player, stack.copy(), unitPrice);
        slot.setByPlayer(ItemStack.EMPTY);
        VCoinsState.checkpoint((ServerPlayer) player);
        VMarketEngine.recordSell(soldItemId, count);
        if (player.level().getServer() != null) {
            VMarketEngine.syncToActiveShoppers(player.level().getServer());
        }
        sendSoundToPlayer((ServerPlayer) player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.85f, 1.2f);
        sendSoundToPlayer((ServerPlayer) player, SoundEvents.BUNDLE_DROP_CONTENTS, 0.6f, 1.3f);
        player.sendOverlayMessage(Component.translatable("vcoins.message.sell_success",
                count, stack.getHoverName(), formatNumber(total)).withStyle(ChatFormatting.GREEN));
        updateShopItems();
    }

    private static void rejectTrade(Player player) {
        player.sendOverlayMessage(Component.translatable("vcoins.message.cannot_trade").withStyle(ChatFormatting.RED));
        if (player instanceof ServerPlayer serverPlayer) {
            sendSoundToPlayer(serverPlayer, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private static long safeMultiply(long price, int count) {
        if (price <= 0 || count <= 0) {
            return 0L;
        }
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
        return player != null && !player.isRemoved() && player.isAlive();
    }

    public static boolean isProtectedFromBulkSell(ItemStack stack) {
        if (stack.isEmpty()) return true;
        // Protect damageable enchanted equipment (weapons, tools, armor)
        if (stack.isDamageableItem() && stack.isEnchanted()) return true;
        // Protect custom named items
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) return true;
        // Protect shulker boxes (whether empty or filled)
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi
                && bi.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return true;
        // Protect high tier rare items
        if (VBlackMarket.isRomanGodItem(stack) || VFortuna.isReward(stack)
                || stack.is(Items.ELYTRA) || stack.is(Items.TOTEM_OF_UNDYING)
                || stack.is(Items.HEAVY_CORE) || stack.is(Items.MACE)) return true;
        return false;
    }

    public void sellAll() {
        if (player.level().isClientSide() || !player.isAlive() || player.isRemoved()) {
            return;
        }

        long totalEarned = 0;
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = playerInventory.getItem(inventorySlot);
            if (stack.isEmpty() || stack.getCount() <= 0 || isProtectedFromBulkSell(stack)) {
                continue;
            }

            long unitPrice = getSellPrice(stack);
            if (unitPrice <= 0) {
                continue;
            }

            int count = stack.getCount();
            String soldItemId = VCoinsPricing.getSellMarketKey(stack);
            VMarketEngine.recordSell(soldItemId, count);
            long stackValue = safeMultiply(unitPrice, count);
            totalEarned = totalEarned > Long.MAX_VALUE - stackValue ? Long.MAX_VALUE : totalEarned + stackValue;
            addBuyback(player, stack.copy(), unitPrice);
            playerInventory.setItem(inventorySlot, ItemStack.EMPTY);
        }

        if (totalEarned > 0) {
            VCoinsState.addCoins(player.getUUID(), totalEarned);
            VCoinsState.checkpoint((ServerPlayer) player);
            VCoinsMod.syncCoins((ServerPlayer) player);
            if (player.level().getServer() != null) {
                VMarketEngine.syncToActiveShoppers(player.level().getServer());
            }
            sendSoundToPlayer((ServerPlayer) player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
            sendSoundToPlayer((ServerPlayer) player, SoundEvents.BUNDLE_DROP_CONTENTS, 0.7f, 1.25f);
            updateShopItems();
        } else {
            sendSoundToPlayer((ServerPlayer) player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    public static void sendSoundToPlayer(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }
        net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> holder = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        player.connection.send(new ClientboundSoundPacket(
                holder, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()
        ));
    }

    public static void sendSoundToPlayer(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> soundHolder, float volume, float pitch) {
        if (player == null || soundHolder == null) {
            return;
        }
        player.connection.send(new ClientboundSoundPacket(
                soundHolder, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()
        ));
    }
}
