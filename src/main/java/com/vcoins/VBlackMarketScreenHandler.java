package com.vcoins;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

public class VBlackMarketScreenHandler extends AbstractContainerMenu {
    public static final int MENU_W = 354;
    public static final int MENU_H = 286;
    public static final int PLAYER_X = (MENU_W - 9 * 18) / 2; // = 96
    public static final int PLAYER_INVENTORY_Y = 196;
    public static final int PLAYER_HOTBAR_Y = 254;

    private final Inventory playerInventory;
    private final Player player;

    public VBlackMarketScreenHandler(int syncId, Inventory playerInventory) {
        super(VCoinsMod.VBLACK_MARKET_SCREEN_HANDLER, syncId);
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
            VBlackMarket.syncToPlayer(serverPlayer);
            VCoinsMod.syncCoins(serverPlayer);
        }
    }

    public void refreshMarketSlots() {
        if (this.player instanceof ServerPlayer serverPlayer) {
            VBlackMarket.syncToPlayer(serverPlayer);
        }
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
}
