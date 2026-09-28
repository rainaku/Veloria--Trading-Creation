package com.vcoins;

import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;

public final class VGiftBoxMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final ContainerData state = new SimpleContainerData(10);
    private long nextAction;
    public VGiftBoxMenu(int id, Inventory inventory) {
        super(VCoinsMod.VGIFT_MENU, id); this.inventory = inventory; addDataSlots(state); refresh();
    }
    public int value(int index) { return state.get(index); }
    public ItemStack reward() { return new ItemStack(BuiltInRegistries.ITEM.byId(value(7))); }
    private void refresh() {
        if (inventory.player instanceof ServerPlayer)
            for (int i = 0; i < 5; i++) state.set(i, VGiftBox.boxes(inventory.player.getUUID())[i]);
    }
    public void revealed(ItemStack stack, int quality, int tier) {
        state.set(6, quality); state.set(7, BuiltInRegistries.ITEM.getId(stack.getItem()));
        state.set(8, tier); state.set(9, (state.get(9) + 1) & 32767);
    }
    @Override public boolean clickMenuButton(Player player, int action) {
        if (!(player instanceof ServerPlayer server) || !stillValid(player) || player.containerMenu != this
                || action < 0 || action >= 10 || System.nanoTime() < nextAction) return false;
        nextAction = System.nanoTime() + 350_000_000L;
        int tier = action % 5;
        if (action < 5) {
            if (VGiftBox.purchase(player.getUUID(), tier)) {
                VCoinsState.checkpoint(server); VCoinsMod.syncCoins(server);
            } else server.sendSystemMessage(net.minecraft.network.chat.Component.translatable("vcoins.gift.buy_failed"));
        } else if (VGiftBox.open(server, tier) == 1) nextAction = System.nanoTime() + 2_800_000_000L;
        state.set(5, (state.get(5) + 1) & 32767); refresh(); broadcastChanges(); return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player == inventory.player && player.isAlive() && !player.isRemoved(); }
}
