package com.vcoins;

import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

public final class VGiftBoxMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final ContainerData state = new SimpleContainerData(6);
    private long nextAction;
    public VGiftBoxMenu(int id, Inventory inventory) {
        super(VCoinsMod.VGIFT_MENU, id); this.inventory = inventory; addDataSlots(state); refresh();
    }
    public int value(int index) { return state.get(index); }
    private void refresh() {
        if (inventory.player instanceof ServerPlayer server)
            for (int i = 0; i < 5; i++) state.set(i, VGiftBox.owned(server, i));
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean clickMenuButton(Player player, int action) {
        if (!(player instanceof ServerPlayer server) || !stillValid(player) || player.containerMenu != this
                || action < 0 || action >= 10) return false;
        // Acknowledge even throttled clicks so the client never waits for its timeout.
        state.set(5, (state.get(5) + 1) & 32767);
        if (System.nanoTime() < nextAction) { broadcastChanges(); return true; }
        nextAction = System.nanoTime() + 100_000_000L;
        int tier = action % 5;
        if (action < 5) {
            if (!VGiftBox.purchase(server, tier))
                server.sendSystemMessage(net.minecraft.network.chat.Component.translatable("vcoins.gift.buy_failed"));
        } else if (VGiftBox.open(server, tier) == 1) {
            server.closeContainer();
            return true;
        }
        refresh(); broadcastChanges(); return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player == inventory.player && player.isAlive() && !player.isRemoved(); }
}
