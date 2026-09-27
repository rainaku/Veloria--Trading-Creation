package com.vcoins;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public class VDuplicateScreenHandler extends AbstractContainerMenu {
    public static final int PLAYER_X = VTradeScreenHandler.PLAYER_X;
    public static final int PLAYER_INVENTORY_Y = VTradeScreenHandler.PLAYER_INVENTORY_Y;
    public static final int PLAYER_HOTBAR_Y = VTradeScreenHandler.PLAYER_HOTBAR_Y;

    private final Inventory playerInventory;
    private final Player player;

    public VDuplicateScreenHandler(int syncId, Inventory playerInventory) {
        super(VCoinsMod.VDUPLICATE_SCREEN_HANDLER, syncId);
        this.playerInventory = playerInventory;
        this.player = playerInventory.player;

        // Player Inventory (3 rows x 9 columns)
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }

        // Player Hotbar (9 slots)
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, PLAYER_X + column * 18, PLAYER_HOTBAR_Y));
        }

        if (this.player instanceof ServerPlayer serverPlayer) {
            VCoinsMod.syncCoins(serverPlayer);
        }
    }

    public boolean handleDuplicate(ServerPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= player.getInventory().getContainerSize()) {
            return false;
        }

        ItemStack sample = player.getInventory().getItem(slotIndex);
        if (sample.isEmpty() || !VCoinsPricing.isTradeable(sample)) {
            player.sendOverlayMessage(Component.translatable("vcoins.duplicate.invalid_item").withStyle(ChatFormatting.RED));
            VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        long coinCost = VDuplicatePricing.getCoinCost(sample);
        int levelCost = VDuplicatePricing.getExperienceLevelCost(sample);
        long currentCoins = VCoinsState.getCoins(player.getUUID());

        if (currentCoins < coinCost) {
            player.sendOverlayMessage(Component.translatable("vcoins.duplicate.not_enough_coins",
                    formatNumber(coinCost), formatNumber(currentCoins)).withStyle(ChatFormatting.RED));
            VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        if (player.experienceLevel < levelCost) {
            player.sendOverlayMessage(Component.translatable("vcoins.duplicate.not_enough_xp",
                    levelCost, player.experienceLevel).withStyle(ChatFormatting.RED));
            VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        VCoinsState.removeCoins(player.getUUID(), coinCost);
        player.giveExperienceLevels(-levelCost);

        ItemStack duplicate = sample.copyWithCount(1);
        player.getInventory().placeItemBackInInventory(duplicate, Prediction.SERVER_ONLY);
        VCoinsMod.syncCoins(player);

        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.ANVIL_USE, 1.0f, 1.15f);
        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, 0.65f, 1.55f);
        player.sendOverlayMessage(Component.translatable("vcoins.duplicate.success", sample.getHoverName(),
                formatNumber(coinCost), levelCost).withStyle(ChatFormatting.GREEN));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemStack = stackInSlot.copy();
            if (slotIndex < 27) {
                if (!this.moveItemStackTo(stackInSlot, 27, 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stackInSlot, 0, 27, false)) {
                return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return player != null && !player.isRemoved() && player.isAlive();
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
