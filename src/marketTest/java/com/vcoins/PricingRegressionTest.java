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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PricingRegressionTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("PricingRegressionTest");
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

        testEnchantmentPricing(efficiency);
        testEnchantmentCombinationsAndDurability(mending);
        testMarketTrends();
        testBlackMarketPricing();
        testMarketEngineVolumesAndThreading(mending, infinity);
        testEndgameAndMarketElasticity();

        LOGGER.info("Pricing regression checks passed: {}", checks);
    }

    private static void testEnchantmentPricing(ItemStack efficiency) {
        long previousBuy = 0;
        long previousSell = 0;
        for (int level = 1; level <= 5; level++) {
            ItemStack stack = book(Enchantments.EFFICIENCY, level);
            require(VCoinsPricing.getMarketKey(stack).equals(VCoinsPricing.getMarketKey(efficiency)), "Levels share cycle");
            long buy = VCoinsPricing.getPrice(stack);
            long sell = VCoinsPricing.getSellPrice(stack);
            require(buy > previousBuy && sell > previousSell, "Higher levels must cost more");
            require(sell < buy, "Buy/sell spread");
            previousBuy = buy;
            previousSell = sell;
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
        require(VCoinsPricing.getReferencePrice(book(Enchantments.EFFICIENCY, 5)) >= 100_000_000L, "Efficiency 5 15x buff");
        require(VCoinsPricing.getReferencePrice(book(Enchantments.EFFICIENCY, 1)) <= 2_000_000L, "Efficiency 1 stays affordable early");
        require(VCoinsPricing.getReferencePrice(book(Enchantments.MENDING, 1)) >= 8_000_000L, "Mending 15x buff");
        require(VCoinsPricing.getReferencePrice(book(Enchantments.FORTUNE, 3)) >= 80_000_000L, "Fortune 3 15x buff");
        require(VCoinsPricing.getReferencePrice(book(Enchantments.PROTECTION, 4)) >= 50_000_000L, "Protection 4 15x buff");
    }

    private static void testEnchantmentCombinationsAndDurability(ItemStack mending) {
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
    }

    private static void testMarketTrends() {
        for (int percent : new int[]{-75, -25, -14, -1, 0, 1, 200, 300}) {
            var trend = new VMarketEngine.MarketTrend(1 + percent / 100.0, percent,
                    VMarketEngine.directionForPercent(percent), "vcoins.market.reason.cycle");
            String expectedArrow;
            if (percent < 0) {
                expectedArrow = "↓";
            } else if (percent > 0) {
                expectedArrow = "↑";
            } else {
                expectedArrow = "→";
            }
            require(trend.getArrow().equals(expectedArrow),
                    "Arrow matches displayed percent: " + percent);

            net.minecraft.ChatFormatting expectedColor;
            if (percent < 0) {
                expectedColor = net.minecraft.ChatFormatting.RED;
            } else if (percent > 0) {
                expectedColor = net.minecraft.ChatFormatting.GREEN;
            } else {
                expectedColor = net.minecraft.ChatFormatting.GRAY;
            }
            require(trend.direction().getColor() == expectedColor,
                    "Color matches displayed percent: " + percent);
        }
        for (var item : new net.minecraft.world.item.Item[]{Items.OAK_WOOD, Items.STRIPPED_OAK_WOOD, Items.DIAMOND}) {
            ItemStack stack = new ItemStack(item);
            for (var trend : new VMarketEngine.MarketTrend[]{VMarketEngine.getTrend(stack), VMarketEngine.getSellTrend(stack)}) {
                require(trend.direction() == VMarketEngine.directionForPercent(trend.percentChange()),
                        "Actual buy/sell badge agrees with rounded quote");
            }
        }
    }

    private static void testBlackMarketPricing() {
        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        long reference = VCoinsPricing.getReferencePrice(totem);
        for (int day = 20000; day < 21000; day++) {
            int discount = VBlackMarket.getDiscountPercent(totem, day);
            long black = VBlackMarket.getDiscountedPrice(totem, day);
            require(discount >= 20 && discount <= 50, "Discount bounds");
            require(Math.abs(black - Math.round(reference * ((100 - discount) / 100.0))) <= 10, "Discounted charge matches reference rate");
        }
        for (int seq = 0; seq < 10; seq++) {
            int discount = VBlackMarket.getDiscountPercent(totem, 100, seq);
            require(discount >= 20 && discount <= 50, "Discount bounds with sequence");
            long black = VBlackMarket.getDiscountedPrice(totem, 100, seq);
            require(black > 0, "Positive black market price");
        }

        // Verify Black Market price is strictly independent of market cycle / volume
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        long blackBefore = VBlackMarket.getDiscountedPrice(diamond, 100);
        VMarketEngine.recordBuy(VCoinsPricing.getMarketKey(diamond), 100);
        long blackAfter = VBlackMarket.getDiscountedPrice(diamond, 100);
        require(blackBefore == blackAfter, "Black market price is strictly independent of market volatility");

        // Verify Roman God Item generation, pricing (>= 300M), durability (>= 200), and enchantments
        ItemStack godMin = VBlackMarket.generateRomanGodItem(new java.util.Random(42L), 300_000_000L);
        require(VBlackMarket.isRomanGodItem(godMin), "Is Roman god item");
        require(!VCoinsPricing.isTradeable(godMin), "God relic excluded from normal market");
        require(VCoinsPricing.getPrice(godMin) == 0, "God relic has no market buy quote");
        require(VCoinsPricing.getSellPrice(godMin) == 0, "God relic cannot be sold");
        require(VCoinsPricing.getBuybackPrice(godMin) == 0, "God relic has no buyback quote");
        require(VDuplicatePricing.getCoinCost(godMin) == 0, "God relic cannot become a cheap duplicate");
        require(VBlackMarket.getRomanGodItemPrice(godMin) == 300_000_000L, "God item minimum price 300M");
        require(VBlackMarket.getDiscountedPrice(godMin, 100) == 300_000_000L, "God item price is fixed independently");
        require(VBlackMarket.getDiscountPercent(godMin, 100) == 0, "God item has 0 discount (fixed prestige price)");
        if (godMin.getMaxDamage() > 0) {
            require(godMin.getMaxDamage() - godMin.getDamageValue() >= 200, "Durability >= 200 at minimum price: " + (godMin.getMaxDamage() - godMin.getDamageValue()));
        }
        require(!VCoinsPricing.getEnchantmentValues(godMin).isEmpty(), "God item must have high quality enchantments");

        // Verify high-end Roman God Item (1 Billion) has maximum durability and full power
        ItemStack godMax = VBlackMarket.generateRomanGodItem(new java.util.Random(1007L), 1_000_000_000L);
        require(VBlackMarket.isRomanGodItem(godMax), "Is Roman god item max");
        require(VBlackMarket.getRomanGodItemPrice(godMax) == 1_000_000_000L, "God item max price 1B");
        require(godMax.getDamageValue() == 0, "Max priced God item must have 100% full durability");
        require(VCoinsPricing.getEnchantmentValues(godMax).size() >= 3, "Max priced God item must have extensive enchantments");

        // Verify Roman God Item persistence via serialization
        VBlackMarket.BlackMarketItemEntry entry = new VBlackMarket.BlackMarketItemEntry(godMin);
        ItemStack restored = entry.toItemStack();
        require(VBlackMarket.isRomanGodItem(restored), "Restored item is Roman God item");
        require(VBlackMarket.getRomanGodItemPrice(restored) == 300_000_000L, "Restored price matches");
        require(restored.getDamageValue() == godMin.getDamageValue(), "Restored damage matches");
        require(VBlackMarket.getRomanGodItemName(restored).equals(VBlackMarket.getRomanGodItemName(godMin)), "Restored name matches");

        java.util.UUID testPlayer = java.util.UUID.randomUUID();
        require(VBlackMarket.getBankedResets(testPlayer) == 0, "Initial banked resets 0");
        VBlackMarket.addBankedResets(testPlayer, 3);
        require(VBlackMarket.getBankedResets(testPlayer) == 3, "Banked resets incremented to 3");
        VBlackMarket.addBankedResets(testPlayer, -1);
        require(VBlackMarket.getBankedResets(testPlayer) == 2, "Banked resets decremented to 2");

        var record = VBlackMarket.getPlayerRecord(testPlayer);
        record.customItems = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) record.customItems.add(new ItemStack(Items.DIAMOND));
        record.legendNextReset = true;
        record.revealedMask = 31;
        VBlackMarket.consumeLegendGuarantee(record);
        int legendSlot = -1;
        int legendCount = 0;
        for (int i = 0; i < 5; i++) {
            if (VBlackMarket.isRomanGodItem(record.customItems.get(i))) {
                legendSlot = i;
                legendCount++;
            }
        }
        require(legendCount == 1, "Pending legend replaces exactly one of five reset cards");
        require(!record.legendNextReset, "Successful guarantee consumes pending legend");
        require(record.revealedMask == (31 & ~(1 << legendSlot)), "Only the chosen legend card becomes hidden");
        record.customItems.set(legendSlot, new ItemStack(Items.DIAMOND));
        VBlackMarket.consumeLegendGuarantee(record);
        require(record.customItems.stream().noneMatch(VBlackMarket::isRomanGodItem), "Legend guarantee only applies once");
    }

    private static void testMarketEngineVolumesAndThreading(ItemStack mending, ItemStack infinity) throws Exception {
        String key = VCoinsPricing.getMarketKey(mending);
        double before = VMarketEngine.getSellMultiplier(key);
        VMarketEngine.recordBuy(key, 100);
        require(Math.abs(before - VMarketEngine.getSellMultiplier(key)) < 0.001, "Buying cannot pump sell payouts");
        require(VMarketEngine.getVolumeModifier(VCoinsPricing.getMarketKey(infinity), 5000, VMarketEngine.getEffectiveEpochSecond()) == 0, "Other book volume isolated");

        require(VMarketEngine.getVolumeModifier("minecraft:totem_of_undying", 1000000, VMarketEngine.getEffectiveEpochSecond()) == 0, "Black-market quotes never alter volume");
        // Pure decay must not flatten under frequent 5-second market ticks.
        var state = new VMarketEngine.MarketItemState(100, 1000);
        require(VMarketEngine.getDecayedVolume(state, 2800) == 50, "Volume half-life");
        // A positive premium does not imply a rising wave.
        String id = "minecraft:totem_of_undying";
        boolean foundFallingAboveBase = false;
        for (long t = 1800000000L; t < 1801000000L; t += 3600) {
            double current = VMarketEngine.multiplierAt(id, t, false);
            if (current > 1 && VMarketEngine.multiplierAt(id, t + 60, false) < current) {
                foundFallingAboveBase = true;
            }
        }
        require(foundFallingAboveBase, "Above-base falling phase exists");
        // Shared JVM must never let a synced client override authoritative server volume.
        VMarketEngine.registerClientThread();
        VMarketEngine.applyClientSync(VMarketEngine.getEffectiveEpochSecond(), java.util.Map.of(key, -100));
        require(VMarketEngine.getVolumeModifier(key, 5000, VMarketEngine.getEffectiveEpochSecond()) < 0, "Client snapshot used");
        var serverValue = new java.util.concurrent.atomic.AtomicReference<Double>();
        Thread server = new Thread(() -> serverValue.set(VMarketEngine.getVolumeModifier(key, 5000, java.time.Instant.now().getEpochSecond())));
        server.start();
        server.join();
        require(serverValue.get() > 0, "Integrated server isolated from client cache");
        VMarketEngine.clearClientSync();
        require(VMarketEngine.getVolumeModifier(key, 5000, VMarketEngine.getEffectiveEpochSecond()) == 0, "Empty client cache stays empty");
    }

    private static void testEndgameAndMarketElasticity() {
        // Endgame base pricing sanity checks
        require(VCoinsPricing.getBasePrice("minecraft:elytra") >= 100_000_000L, "Elytra endgame valuation");
        require(VCoinsPricing.getBasePrice("minecraft:beacon") >= 80_000_000L, "Beacon endgame valuation");
        require(VCoinsPricing.getBasePrice("minecraft:nether_star") >= 40_000_000L, "Nether star valuation");
        require(VCoinsPricing.getBasePrice("minecraft:heavy_core") >= 80_000_000L, "Heavy core valuation");
        require(VCoinsPricing.getBasePrice("minecraft:mace") >= 80_000_000L, "Mace valuation");
        require(VCoinsPricing.getBasePrice("minecraft:totem_of_undying") >= 20_000_000L, "Totem valuation");
        require(VCoinsPricing.getBasePrice("minecraft:netherite_ingot") >= 10_000_000L, "Netherite ingot valuation");
        require(VCoinsPricing.getBasePrice("minecraft:netherite_block") >= 80_000_000L, "Netherite block valuation");
        require(VCoinsPricing.getBasePrice("minecraft:shulker_box") >= 3_000_000L, "Shulker box valuation");
        require(VCoinsPricing.getBasePrice("minecraft:enchanted_golden_apple") >= 50_000_000L, "God apple valuation");
        require(VCoinsPricing.getBasePrice("minecraft:dragon_egg") >= 200_000_000L, "Dragon egg valuation");

        // Verify Market Depth does not drop to twitchy tiny numbers (like 15) for high-value items
        int depthElytra = VMarketEngine.calculateMarketDepth(VCoinsPricing.getBasePrice("minecraft:elytra"));
        require(depthElytra >= 120, "Endgame market depth must provide sufficient liquidity buffer: " + depthElytra);
        int depthNetherite = VMarketEngine.calculateMarketDepth(VCoinsPricing.getBasePrice("minecraft:netherite_ingot"));
        require(depthNetherite >= 120, "Netherite market depth buffer: " + depthNetherite);

        // Verify small retail purchases do not cause sudden jumpy price spikes (cushion / smooth progressive elasticity)
        double smallBuyMod = VMarketEngine.calculateVolumeModifier(2, VCoinsPricing.getBasePrice("minecraft:netherite_ingot"));
        require(smallBuyMod >= 0.0 && smallBuyMod < 0.005, "Buying 2 netherite ingots must not rush to spike price: " + smallBuyMod);
        double singleTotemMod = VMarketEngine.calculateVolumeModifier(1, VCoinsPricing.getBasePrice("minecraft:totem_of_undying"));
        require(singleTotemMod >= 0.0 && singleTotemMod < 0.002, "Buying 1 totem must not visibly twitch price: " + singleTotemMod);

        // Verify bulk purchases still progressively and smoothly exert upward market pressure
        double bulkMod = VMarketEngine.calculateVolumeModifier(60, VCoinsPricing.getBasePrice("minecraft:netherite_ingot"));
        require(bulkMod > 0.03 && bulkMod < 0.25, "Bulk buying still moves the market reasonably: " + bulkMod);
    }
}
