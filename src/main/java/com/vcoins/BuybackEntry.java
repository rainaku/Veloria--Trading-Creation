package com.vcoins;

import net.minecraft.world.item.ItemStack;

/** Immutable sale receipt. The original components never contain UI pricing data. */
public final class BuybackEntry {
    private final ItemStack stack;
    private final long unitPrice;

    public BuybackEntry(ItemStack stack, long paidUnitPrice) {
        if (stack.isEmpty() || paidUnitPrice <= 0) throw new IllegalArgumentException("Invalid sale receipt");
        this.stack = stack.copy();
        this.unitPrice = VCoinsPricing.calculateBuybackPrice(paidUnitPrice);
    }

    public ItemStack stack() { return stack.copy(); }
    public long unitPrice() { return unitPrice; }
}
