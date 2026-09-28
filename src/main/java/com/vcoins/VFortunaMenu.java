package com.vcoins;

import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

public final class VFortunaMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final ContainerData state = new SimpleContainerData(18);
    public VFortunaMenu(int id, Inventory inventory) {
        super(VCoinsMod.VFORTUNA_MENU, id); this.inventory = inventory;
        addDataSlots(state);
        if (inventory.player instanceof ServerPlayer) {
            long price = VFortuna.entryPrice(inventory.player.registryAccess());
            for (int i = 0; i < 4; i++) state.set(14 + i, (int)(price >>> (i * 16)) & 65535);
        }
        refresh();
    }
    public long entryPrice() {
        long price = 0;
        for (int i = 0; i < 4; i++) price |= (state.get(14 + i) & 65535L) << (i * 16);
        return price;
    }
    public int value(int index) { return state.get(index); }
    public ItemStack preview(int tier) {
        FortunaRun run = new FortunaRun();
        for (int i = 0; i < 6; i++) run.choices[i] = state.get(8 + i);
        return VFortuna.reward(run, tier, inventory.player.registryAccess());
    }
    private void refresh() {
        if (!(inventory.player instanceof ServerPlayer)) return;
        FortunaRun r = VFortuna.run(inventory.player.getUUID());
        int[] values = {r.active ? 1 : 0, r.tier, r.charm, r.used ? 1 : 0, r.anchor + 1, r.fragments, r.result, r.revision & 32767};
        for (int i = 0; i < values.length; i++) state.set(i, values[i]);
        int visibleThrough = r.tier + (r.charm == 1 && r.used ? 2 : 1);
        for (int i = 0; i < 6; i++) state.set(8 + i,
                r.active && i >= r.tier && i <= visibleThrough ? r.choices[i] : 0);
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (player instanceof ServerPlayer server && player == inventory.player && stillValid(player)) {
            VFortuna.action(server, id, entryPrice()); refresh(); broadcastChanges(); return true;
        }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return player == inventory.player && player.isAlive(); }
}
