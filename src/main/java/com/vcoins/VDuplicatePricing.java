package com.vcoins;

import net.minecraft.item.ItemStack;

public final class VDuplicatePricing {
    private static final long MINIMUM_COIN_COST = 500_000L;
    private static final long ITEM_PRICE_MULTIPLIER = 4L;
    private static final long COMPONENT_SURCHARGE = 25_000L;
    private static final long CUSTOM_NAME_SURCHARGE = 100_000L;

    private VDuplicatePricing() {
    }

    public static long getCoinCost(ItemStack sample) {
        if (sample.isEmpty() || !VCoinsPricing.isTradeable(sample.getItem())) {
            return 0L;
        }

        long itemPrice = Math.max(1_000L, VCoinsPricing.getPrice(sample));
        long cost = Math.max(MINIMUM_COIN_COST, safeMultiply(itemPrice, ITEM_PRICE_MULTIPLIER));
        cost = safeAdd(cost, safeMultiply(
                Math.min(20, sample.getComponentChanges().size()), COMPONENT_SURCHARGE));

        if (sample.getCustomName() != null) {
            cost = safeAdd(cost, CUSTOM_NAME_SURCHARGE);
        }
        return cost;
    }

    public static int getExperienceLevelCost(ItemStack sample) {
        if (sample.isEmpty() || !VCoinsPricing.isTradeable(sample.getItem())) {
            return 0;
        }

        int levels = 30 + Math.min(30, sample.getComponentChanges().size() * 3);
        levels += Math.min(35, VCoinsPricing.getTotalEnchantmentLevels(sample) * 2);
        levels += Math.min(25, VCoinsPricing.getEnchantmentCount(sample) * 5);
        if (sample.getCustomName() != null) {
            levels += 5;
        }

        long price = VCoinsPricing.getPrice(sample);
        if (price >= 500_000L) {
            levels += 20;
        } else if (price >= 100_000L) {
            levels += 10;
        }
        return Math.min(100, levels);
    }

    private static long safeMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
