package com.vcoins;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

public class VBlackMarketScreenHandler extends AbstractContainerMenu {
    public static final int MARKET_COLUMNS = 5;
    public static final int MARKET_ROWS = 1;
    public static final int MARKET_SLOT_COUNT = 5; // Exactly 5 daily cards

    public static final int CARD_SIZE = 40;
    public static final int MARKET_COL_SPACING = 44;
    public static final int MARKET_ROW_SPACING = 0;
    public static final int MARKET_X = 20;
    public static final int MARKET_Y = 58;

    public static final int PLAYER_X = 47;
    public static final int PLAYER_INVENTORY_Y = 138;
    public static final int PLAYER_HOTBAR_Y = 196;

    private final Inventory playerInventory;
    private final Player player;
    private long displayedEpochDay;
    private final Container marketInventory = new SimpleContainer(MARKET_SLOT_COUNT);

    public VBlackMarketScreenHandler(int syncId, Inventory playerInventory) {
        super(VCoinsMod.VBLACK_MARKET_SCREEN_HANDLER, syncId);
        this.playerInventory = playerInventory;
        this.player = playerInventory.player;

        // 5 Daily market slots (1 row of 5 prominent cards)
        for (int column = 0; column < MARKET_COLUMNS; column++) {
            int slotX = MARKET_X + column * MARKET_COL_SPACING + 12; // (40 - 16) / 2 = 12
            int slotY = MARKET_Y + 12;
            this.addSlot(new Slot(marketInventory, column, slotX, slotY) {
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

        // Player Inventory (3 rows x 9 columns)
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_X + column * 18 + 1, PLAYER_INVENTORY_Y + row * 18 + 1));
            }
        }

        // Player Hotbar (9 slots)
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, PLAYER_X + column * 18 + 1, PLAYER_HOTBAR_Y + 1));
        }

        refreshMarketSlots();

        if (this.player instanceof ServerPlayer serverPlayer) {
            VBlackMarket.syncToPlayer(serverPlayer);
        }
    }

    public void refreshMarketSlots() {
        List<ItemStack> items = VBlackMarket.getDailyItems();
        displayedEpochDay = VBlackMarket.getCurrentDay();
        marketInventory.clearContent();
        for (int i = 0; i < Math.min(items.size(), MARKET_SLOT_COUNT); i++) {
            marketInventory.setItem(i, items.get(i).copy());
        }
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        if (slotIndex >= 0 && slotIndex < MARKET_SLOT_COUNT) {
            // Market interaction uses buyItem
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
    }

    public void handlePurchase(ServerPlayer player, int slotIndex, boolean buyStack) {
        if (player != this.player || slotIndex < 0 || slotIndex >= MARKET_SLOT_COUNT || !player.isAlive() || player.isRemoved()) {
            return;
        }

        if (displayedEpochDay != VBlackMarket.getCurrentDay()) {
            refreshMarketSlots();
            VBlackMarket.syncToPlayer(player);
            return; // Never charge a newly rotated card for a click on yesterday's card.
        }

        // Anti-exploit check: Has the card already been bought today?
        if (VBlackMarket.hasPurchasedToday(player.getUUID(), slotIndex)) {
            player.sendOverlayMessage(Component.translatable("vcoins.black_market.already_bought")
                    .withStyle(ChatFormatting.RED));
            VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            VBlackMarket.syncToPlayer(player);
            return;
        }

        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return;
        }

        ItemStack displayed = slot.getItem();
        long unitPrice = VBlackMarket.getDiscountedPrice(displayed, VBlackMarket.getCurrentDay());
        if (unitPrice <= 0) {
            return;
        }

        // The black market offers unique daily cards: strictly 1 item per card per day
        int amount = 1;
        long totalCost = unitPrice;
        long currentCoins = VCoinsState.getCoins(player.getUUID());

        if (currentCoins < totalCost) {
            player.sendOverlayMessage(Component.translatable("vcoins.message.not_enough",
                    formatNumber(totalCost), formatNumber(currentCoins)).withStyle(ChatFormatting.RED));
            VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        VCoinsState.removeCoins(player.getUUID(), totalCost);
        VCoinsMod.syncCoins(player);

        // Mark as purchased for today permanently in save data BEFORE placing item
        VBlackMarket.markPurchasedToday(player.getUUID(), slotIndex);
        if (player.level().getServer() != null) {
            VBlackMarket.save(player.level().getServer());
        }

        ItemStack purchased = displayed.copy();
        purchased.setCount(amount);
        player.getInventory().placeItemBackInInventory(purchased, Prediction.SERVER_ONLY);

        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.ITEM_PICKUP, 0.9f, 1.25f);
        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 1.75f);
        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.5f);
        player.sendOverlayMessage(Component.translatable("vcoins.message.buy_success",
                amount, displayed.getHoverName(), formatNumber(totalCost)).withStyle(ChatFormatting.GREEN));

        // Sync updated state to client immediately
        VBlackMarket.syncToPlayer(player);
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
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player != null && !player.isRemoved() && player.isAlive();
    }
}
