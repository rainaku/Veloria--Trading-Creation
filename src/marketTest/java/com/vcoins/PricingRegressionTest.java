package com.vcoins;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public final class PricingRegressionTest {
    private static HolderLookup.Provider registries;
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static ItemStack book(ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        stack.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), level);
        return stack;
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createWorldLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(pending -> pending.apply());
        VCoinsPricing.ensureInitialized();
        ItemStack mending = book(Enchantments.MENDING, 1);
        ItemStack infinity = book(Enchantments.INFINITY, 1);
        ItemStack silk = book(Enchantments.SILK_TOUCH, 1);
        ItemStack efficiency = book(Enchantments.EFFICIENCY, 1);
        ItemStack ordinary = book(Enchantments.SMITE, 1);
        for (ItemStack hot : new ItemStack[]{mending, infinity, silk, efficiency}) {
            require(VCoinsPricing.getReferencePrice(hot) > VCoinsPricing.getReferencePrice(ordinary), "Hot enchantment premium");
        }
        require(!VCoinsPricing.getMarketKey(mending).equals(VCoinsPricing.getMarketKey(efficiency)), "Distinct book markets");
        long previousBuy = 0, previousSell = 0;
        for (int level = 1; level <= 5; level++) {
            ItemStack stack = book(Enchantments.EFFICIENCY, level);
            require(VCoinsPricing.getMarketKey(stack).equals(VCoinsPricing.getMarketKey(efficiency)), "Levels share cycle");
            long buy = VCoinsPricing.getPrice(stack), sell = VCoinsPricing.getSellPrice(stack);
            require(buy > previousBuy && sell > previousSell, "Higher levels must cost more");
            require(sell < buy, "Buy/sell spread");
            previousBuy = buy; previousSell = sell;
            ItemStack gear = new ItemStack(Items.DIAMOND_PICKAXE);
            gear.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), level);
            require(VCoinsPricing.getReferencePrice(gear) > VCoinsPricing.getReferencePrice(new ItemStack(Items.DIAMOND_PICKAXE)), "Equipment premium");
        }
        // Cover every vanilla enchantment, not only the popular examples.
        for (var enchantment : registries.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()) {
            long last = 0;
            for (int level = 1; level <= enchantment.value().getMaxLevel(); level++) {
                ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
                stack.enchant(enchantment, level);
                long price = VCoinsPricing.getPrice(stack);
                require(price > last, "Every enchantment level must increase price: " + enchantment.getRegisteredName());
                last = price;
            }
        }
        // Verify high-level enchantment 15x buff to avoid early-game inflation
        require(VCoinsPricing.getReferencePrice(book(Enchantments.EFFICIENCY, 5)) >= 20_000_000L, "Efficiency 5 15x buff");
        require(VCoinsPricing.getReferencePrice(book(Enchantments.EFFICIENCY, 1)) <= 150_000L, "Efficiency 1 stays affordable early");
        require(VCoinsPricing.getReferencePrice(book(Enchantments.MENDING, 1)) >= 10_000_000L, "Mending 15x buff");
        require(VCoinsPricing.getReferencePrice(book(Enchantments.FORTUNE, 3)) >= 15_000_000L, "Fortune 3 15x buff");
        require(VCoinsPricing.getReferencePrice(book(Enchantments.PROTECTION, 4)) >= 10_000_000L, "Protection 4 15x buff");

        ItemStack comboA = book(Enchantments.MENDING, 1);
        comboA.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
        ItemStack comboB = book(Enchantments.UNBREAKING, 3);
        comboB.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        require(VCoinsPricing.getMarketKey(comboA).equals(VCoinsPricing.getMarketKey(comboB)), "Enchantment order does not split markets");
        require(VCoinsPricing.getReferencePrice(comboA) == VCoinsPricing.getReferencePrice(comboB), "Order-independent valuation");
        require(VCoinsPricing.getReferencePrice(comboA) > VCoinsPricing.getReferencePrice(mending), "Multiple-enchantment premium");
        comboB.setCount(10);
        require(VCoinsPricing.getPrice(comboA) == VCoinsPricing.getPrice(comboB), "Unit price ignores stack count");
        ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), 5);
        long intactSell = VCoinsPricing.getSellPrice(damaged);
        String intactKey = VCoinsPricing.getMarketKey(damaged);
        damaged.setDamageValue(damaged.getMaxDamage() / 2);
        require(intactKey.equals(VCoinsPricing.getMarketKey(damaged)), "Durability does not create market variants");
        require(VCoinsPricing.getSellPrice(damaged) < intactSell, "Damage lowers resale value");
        for (int percent : new int[]{-75, -25, -14, -1, 0, 1, 200, 300}) {
            var trend = new VMarketEngine.MarketTrend(1 + percent / 100.0, percent,
                    VMarketEngine.directionForPercent(percent), "vcoins.market.reason.cycle");
            require(trend.getArrow().equals(percent < 0 ? "↓" : percent > 0 ? "↑" : "→"),
                    "Arrow matches displayed percent: " + percent);
            require(trend.direction().getColor() == (percent < 0 ? net.minecraft.ChatFormatting.RED
                    : percent > 0 ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GRAY),
                    "Color matches displayed percent: " + percent);
        }
        for (var item : new net.minecraft.world.item.Item[]{Items.OAK_WOOD, Items.STRIPPED_OAK_WOOD, Items.DIAMOND}) {
            ItemStack stack = new ItemStack(item);
            for (var trend : new VMarketEngine.MarketTrend[]{VMarketEngine.getTrend(stack), VMarketEngine.getSellTrend(stack)}) {
                require(trend.direction() == VMarketEngine.directionForPercent(trend.percentChange()),
                        "Actual buy/sell badge agrees with rounded quote");
            }
        }
        String key = VCoinsPricing.getMarketKey(mending);
        double before = VMarketEngine.getSellMultiplier(key);
        VMarketEngine.recordBuy(key, 100);
        require(Math.abs(before - VMarketEngine.getSellMultiplier(key)) < 0.001, "Buying cannot pump sell payouts");
        require(VMarketEngine.getVolumeModifier(VCoinsPricing.getMarketKey(infinity), 5000, VMarketEngine.getEffectiveEpochSecond()) == 0, "Other book volume isolated");
        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        for (int day = 20000; day < 21000; day++) {
            int discount = VBlackMarket.getDiscountPercent(totem, day);
            long market = VCoinsPricing.getPrice(totem), black = VBlackMarket.getDiscountedPrice(totem, day);
            require(discount >= 20 && discount <= 50, "Discount bounds");
            require(Math.abs(black - Math.round(market * ((100 - discount) / 100.0))) <= 10, "Discounted charge matches rate");
        }
        require(VMarketEngine.getVolumeModifier("minecraft:totem_of_undying", 1000000, VMarketEngine.getEffectiveEpochSecond()) == 0, "Black-market quotes never alter volume");
        // Pure decay must not flatten under frequent 5-second market ticks.
        var state = new VMarketEngine.MarketItemState(100, 1000);
        require(VMarketEngine.getDecayedVolume(state, 2800) == 50, "Volume half-life");
        // A positive premium does not imply a rising wave.
        String id = "minecraft:totem_of_undying";
        boolean foundFallingAboveBase = false;
        for (long t = 1800000000L; t < 1801000000L; t += 3600) {
            double current = VMarketEngine.multiplierAt(id, t, false);
            if (current > 1 && VMarketEngine.multiplierAt(id, t + 60, false) < current) foundFallingAboveBase = true;
        }
        require(foundFallingAboveBase, "Above-base falling phase exists");
        // Shared JVM must never let a synced client override authoritative server volume.
        VMarketEngine.registerClientThread();
        VMarketEngine.applyClientSync(VMarketEngine.getEffectiveEpochSecond(), java.util.Map.of(key, -100));
        require(VMarketEngine.getVolumeModifier(key, 5000, VMarketEngine.getEffectiveEpochSecond()) < 0, "Client snapshot used");
        var serverValue = new java.util.concurrent.atomic.AtomicReference<Double>();
        Thread server = new Thread(() -> serverValue.set(VMarketEngine.getVolumeModifier(key, 5000, java.time.Instant.now().getEpochSecond())));
        server.start(); server.join();
        require(serverValue.get() > 0, "Integrated server isolated from client cache");
        VMarketEngine.clearClientSync();
        require(VMarketEngine.getVolumeModifier(key, 5000, VMarketEngine.getEffectiveEpochSecond()) == 0, "Empty client cache stays empty");
        System.out.println("Pricing regression checks passed: " + checks);
    }
}
