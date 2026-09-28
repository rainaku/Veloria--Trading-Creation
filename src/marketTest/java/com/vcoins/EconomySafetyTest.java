package com.vcoins;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public final class EconomySafetyTest {
    private static int checks;
    private static HolderLookup.Provider registries;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        registries = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(p -> p.apply());
        VCoinsPricing.ensureInitialized();
        containers();
        enchanting();
        buyback();
        persistence();
        marketWorldIsolation();
        marketPayment();
        marketRarity();
        boundMarketPricing();
        marketProvenance();
        giftBoxes();
        System.out.println("Economy safety checks passed: " + checks);
    }

    private static void containers() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        require(VDuplicatePricing.canDuplicate(box), "Empty shulker remains duplicable");
        var contents = new ArrayList<ItemStack>();
        for (int i = 0; i < 27; i++) contents.add(new ItemStack(Items.NETHERITE_BLOCK, 64));
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        require(!VDuplicatePricing.canDuplicate(box), "Filled shulker rejected");
        require(VDuplicatePricing.getCoinCost(box) == 0 && VDuplicatePricing.getExperienceLevelCost(box) == 0,
                "Filled shulker has no duplicate quote");
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        require(VDuplicatePricing.canDuplicate(bundle), "Empty bundle allowed");
        bundle.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY.copyWithContents(contents.stream().limit(1)));
        require(!VDuplicatePricing.canDuplicate(bundle), "Filled bundle rejected");
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        require(!VDuplicatePricing.canDuplicate(crossbow), "Loaded crossbow rejected");
        ItemStack opaque = new ItemStack(Items.COD_BUCKET);
        opaque.set(DataComponents.BUCKET_ENTITY_DATA, CustomData.of(new CompoundTag()));
        require(!VDuplicatePricing.canDuplicate(opaque), "Opaque entity payload rejected");
        var run = new FortunaRun();
        var bound = VFortuna.reward(run, 3, registries);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(bound)));
        require(!VDuplicatePricing.canDuplicate(box), "Container cannot bypass bound reward restriction");
        require(!VDuplicatePricing.canDuplicate(bound), "Bound item itself remains blocked");
        require(!VDuplicatePricing.canDuplicate(new ItemStack(Items.COMMAND_BLOCK)), "Creative item remains blocked");
    }

    private static void enchanting() {
        VMarketEngine.reset(null);
        for (var enchantment : registries.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList()) {
            ItemStack prior = null;
            for (int level = 1; level <= enchantment.value().getMaxLevel(); level++) {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                book.enchant(enchantment, level);
                require(VCoinsPricing.getSellPrice(book) == VCoinsPricing.getSellPrice(new ItemStack(Items.BOOK)),
                        "Book resale ignores enchantment " + enchantment.getRegisteredName());
                if (prior != null) {
                    require(VCoinsPricing.getSellPrice(book) < 2 * VCoinsPricing.getPrice(prior),
                            "Combining equal levels cannot mint coins");
                }
                ItemStack gear = new ItemStack(Items.DIAMOND_PICKAXE);
                gear.enchant(enchantment, level);
                require(VCoinsPricing.getSellPrice(gear) == VCoinsPricing.getSellPrice(new ItemStack(Items.DIAMOND_PICKAXE)),
                        "Equipment resale ignores upgrade premium");
                require(VCoinsPricing.getSellMarketKey(gear).equals("minecraft:diamond_pickaxe"),
                        "Combining types cannot reset sale pressure");
                prior = book;
            }
        }
        require(VCoinsPricing.getSellPrice("minecraft:enchanted_book") == VCoinsPricing.getSellPrice("minecraft:book"),
                "String and stack resale paths agree");
    }

    private static void buyback() {
        VMarketEngine.reset(null);
        var receipts = new ArrayList<BuybackEntry>();
        long proceeds = 0;
        for (int i = 0; i < 6; i++) {
            ItemStack ingots = new ItemStack(Items.NETHERITE_INGOT, 64);
            long paid = VCoinsPricing.getSellPrice(ingots);
            var receipt = new BuybackEntry(ingots, paid);
            receipts.add(receipt); proceeds += paid * 64;
            VMarketEngine.recordSell(VCoinsPricing.getSellMarketKey(ingots), 64);
            ingots.setCount(0);
            require(receipt.stack().getCount() == 64, "Receipt owns original stack copy");
        }
        long repurchase = receipts.stream().mapToLong(r -> r.unitPrice() * r.stack().getCount()).sum();
        require(repurchase > proceeds, "Sell six stacks then buy back must lose money");
        long before = receipts.getFirst().unitPrice();
        VMarketEngine.recordSell("minecraft:netherite_ingot", 100000);
        require(receipts.getFirst().unitPrice() == before, "Pressure never changes receipt quote");
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).listElements().findFirst().orElseThrow(), 1);
        var receipt = new BuybackEntry(book, 123);
        require(ItemStack.isSameItemSameComponents(book, receipt.stack()), "Buyback preserves original components");
        require(VCoinsPricing.calculateBuybackPrice(Long.MAX_VALUE) == Long.MAX_VALUE, "Markup saturates without overflow");
        require(VCoinsPricing.calculateBuybackPrice(1) == 2, "Tiny receipts still have markup");
        VMarketEngine.reset(null);
    }

    private static void giftBoxes() {
        int[] outcomes = new int[4];
        for (int roll = 0; roll < 1000; roll++) outcomes[VGiftBox.quality(roll)]++;
        require(Arrays.equals(outcomes, new int[]{750, 200, 45, 5}), "Gift odds exact across all roll buckets");
        UUID id = UUID.randomUUID();
        for (int tier = 0; tier < 5; tier++) {
            long price = VGiftBox.PRICES[tier];
            VCoinsState.setCoins(id, price - 1);
            require(!VGiftBox.purchase(id, tier) && VGiftBox.boxes(id)[tier] == 0, "Gift purchase rejects insufficient balance");
            VCoinsState.setCoins(id, price);
            require(VGiftBox.purchase(id, tier) && VCoinsState.getCoins(id) == 0 && VGiftBox.boxes(id)[tier] == 1,
                    "Gift purchase charges exact tier price");
        }
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        VGiftBox.write(id, output);
        VGiftBox.read(id, TagValueInput.create(ProblemReporter.DISCARDING, registries, new CompoundTag()));
        require(Arrays.equals(VGiftBox.boxes(id), new int[5]), "Missing gift save starts empty");
        VGiftBox.read(id, TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult()));
        require(Arrays.equals(VGiftBox.boxes(id), new int[]{1,1,1,1,1}), "Gift stock round trips all five tiers");
        require(!VGiftBox.purchase(id, -1) && !VGiftBox.purchase(id, 5), "Invalid gift tier rejected");
    }

    private static void marketProvenance() {
        var material = new ItemStack(Items.IRON_INGOT);
        CustomData.update(DataComponents.CUSTOM_DATA, material, tag -> tag.putBoolean("VBlackMarketPurchased", true));
        VBlackMarket.stampEquipment(material);
        require(VCoinsPricing.isTradeable(material) && VCoinsPricing.getSellPrice(material) > 0,
                "Old purchased materials can be traded normally");
        require(!material.get(DataComponents.CUSTOM_DATA).copyTag().contains("VBlackMarketEquipmentId"),
                "Materials do not receive equipment identity");
        var gear = new ItemStack(Items.DIAMOND_LEGGINGS);
        VBlackMarket.stampEquipment(gear);
        String id = gear.get(DataComponents.CUSTOM_DATA).copyTag().getString("VBlackMarketEquipmentId").orElseThrow();
        UUID.fromString(id);
        VBlackMarket.stampEquipment(gear);
        require(gear.get(DataComponents.LORE).lines().size() == 2, "Restamping does not duplicate provenance lore");
        var restored = new VBlackMarket.BlackMarketItemEntry(gear).toItemStack();
        require(restored.get(DataComponents.CUSTOM_DATA).copyTag().getString("VBlackMarketEquipmentId").orElseThrow().equals(id),
                "Equipment identity survives market save/load");
        require(!VCoinsPricing.isTradeable(restored) && !VDuplicatePricing.canDuplicate(restored),
                "Restored equipment remains bound");
        var second = new ItemStack(Items.DIAMOND_LEGGINGS);
        VBlackMarket.stampEquipment(second);
        require(!second.get(DataComponents.CUSTOM_DATA).copyTag().getString("VBlackMarketEquipmentId").orElseThrow().equals(id),
                "Separate equipment receives distinct identity");
        var legacy = new VBlackMarket.BlackMarketItemEntry("minecraft:iron_sword").toItemStack();
        require(!VCoinsPricing.isTradeable(legacy) && legacy.has(DataComponents.LORE),
                "Legacy equipment listings gain binding and provenance");
    }

    private static void boundMarketPricing() {
        UUID id = UUID.randomUUID();
        VBlackMarket.clearWorldState();
        for (var item : List.of(Items.DIAMOND_LEGGINGS, Items.NETHERITE_PICKAXE)) {
            var original = new ItemStack(item);
            original.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
            long reference = VCoinsPricing.getReferencePrice(original);
            var bound = original.copy();
            CustomData.update(DataComponents.CUSTOM_DATA, bound, tag -> {
                tag.putBoolean("VNoTrade", true);
                tag.putString("VCardTier", "exclusive");
            });
            var rec = VBlackMarket.getPlayerRecord(id);
            rec.customItems = new ArrayList<>(List.of(bound));
            rec.revealedMask = 1;
            rec.purchasedMask = 0;
            long price = VBlackMarket.getDiscountedPrice(bound, rec.day, rec.resetSequence);
            require(price > 0 && price == VBlackMarket.getDiscountedPrice(original, rec.day, rec.resetSequence),
                    "Bound market gear retains material and enchant acquisition price");
            require(VCoinsPricing.getBlackMarketReferencePrice(bound) == reference,
                    "Acquisition valuation includes enchant premium");
            require(VCoinsPricing.getPrice(bound) == 0 && VCoinsPricing.getSellPrice(bound) == 0
                    && !VDuplicatePricing.canDuplicate(bound), "Bound gear remains blocked outside market purchase");
            require(bound.get(DataComponents.CUSTOM_DATA).copyTag().getBooleanOr("VNoTrade", false),
                    "Quoting never removes binding from original stack");
            VCoinsState.setCoins(id, price - 1);
            require(!VBlackMarket.payForCard(id, 0), "Bound gear cannot be acquired with insufficient funds");
            VCoinsState.setCoins(id, price);
            require(VBlackMarket.payForCard(id, 0) && VCoinsState.getCoins(id) == 0,
                    "Bound gear charges the displayed positive price");
        }
        VBlackMarket.clearWorldState();
    }

    private static void marketRarity() {
        for (int seed = 0; seed < 100; seed++) {
            var common = VBlackMarket.rollCardItem(new Random(seed) {
                @Override public double nextDouble() { return 0.99; }
            }, Items.DIAMOND);
            require(VBlackMarket.isCommonCardItem(common.getItem()), "Common roll excludes higher-tier pools");
            require(common.getCount() >= 30 && common.getCount() <= 64, "Common bundle quantity 30 to 64");
            var saved = new VBlackMarket.BlackMarketItemEntry(common).toItemStack();
            require(saved.getCount() == common.getCount(), "Card quantity survives save/load");
            long total = VBlackMarket.getDiscountedPrice(common, 123);
            require(total == VBlackMarket.getDiscountedPrice(common.copyWithCount(1), 123) * common.getCount(),
                    "Card price covers all delivered units");
        }
        for (int seed = 0; seed < 100; seed++) {
            var legend = VBlackMarket.generateRomanGodItem(new Random(seed), 300_000_000L);
            var enchants = legend.get(DataComponents.ENCHANTMENTS);
            require(enchants != null && !enchants.isEmpty(), "Legend has survival enchantments");
            for (var entry : enchants.entrySet()) {
                require(entry.getIntValue() == entry.getKey().value().getMaxLevel(),
                        "Even cheapest Legend uses maximum survival enchant levels");
            }
        }
        for (double roll : new double[]{0, 0.0001249, 0.000125, 0.0026249, 0.002625, 0.9}) {
            ItemStack item = VBlackMarket.rollCardItem(new Random(42) {
                @Override public double nextDouble() { return roll; }
            }, Items.DIAMOND);
            require(VBlackMarket.isMythicItem(item) == (roll < 0.000125), "Mythic roll boundary");
            require(VBlackMarket.isRomanGodItem(item) == (roll < 0.002625), "God-tier roll boundary");
        }
        VBlackMarket.clearWorldState();
        UUID id = UUID.randomUUID();
        var rec = VBlackMarket.getPlayerRecord(id);
        rec.customItems = new ArrayList<>(List.of(VBlackMarket.generateRomanGodItem(new Random(42))));
        rec.lifetimeFlipCount = 499;
        rec.luckyFlipCount = 499;
        VBlackMarket.revealCard(id, 0);
        require(rec.lifetimeFlipCount == 0 && rec.luckyFlipCount == 0, "Natural Legend consumes pity on discovery");
        VBlackMarket.revealCard(id, 0);
        require(rec.lifetimeFlipCount == 0 && rec.luckyFlipCount == 0, "Repeated reveal cannot farm pity");
        VBlackMarket.clearWorldState();
    }

    private static void marketPayment() {
        VBlackMarket.clearWorldState();
        UUID id = UUID.randomUUID();
        var rec = VBlackMarket.getPlayerRecord(id);
        rec.customItems = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND)));
        rec.revealedMask = 1;
        long price = VBlackMarket.getDiscountedPrice(rec.customItems.getFirst(), rec.day, rec.resetSequence);
        VCoinsState.setCoins(id, 0);
        require(!VBlackMarket.payForCard(id, 0) && rec.purchasedMask == 0, "Zero balance cannot claim market card");
        VCoinsState.setCoins(id, price - 1);
        require(!VBlackMarket.payForCard(id, 0) && VCoinsState.getCoins(id) == price - 1,
                "Insufficient payment leaves balance unchanged");
        VCoinsState.setCoins(id, price);
        rec.revealedMask = 0;
        require(!VBlackMarket.payForCard(id, 0), "Hidden card cannot be purchased");
        rec.revealedMask = 1;
        require(!VBlackMarket.payForCard(id, -1) && !VBlackMarket.payForCard(id, 10)
                && !VBlackMarket.payForCard(id, 9), "Invalid and missing card rejected");
        require(VBlackMarket.payForCard(id, 0) && VCoinsState.getCoins(id) == 0 && rec.purchasedMask == 1,
                "Exact server price is charged before delivery");
        VCoinsState.setCoins(id, price * 2);
        require(!VBlackMarket.payForCard(id, 0) && VCoinsState.getCoins(id) == price * 2,
                "Replayed purchase cannot charge or deliver twice");
        VBlackMarket.clearWorldState();
    }

    private static void marketWorldIsolation() throws Exception {
        UUID id = UUID.randomUUID();
        var record = VBlackMarket.getPlayerRecord(id);
        record.bankedResets = 9;
        record.customItems = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND_SWORD)));
        var itemsField = VBlackMarket.class.getDeclaredField("dailyItems");
        itemsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var items = (List<ItemStack>) itemsField.get(null);
        items.add(new ItemStack(Items.ENCHANTED_BOOK));
        VBlackMarket.clearWorldState();
        require(items.isEmpty(), "World unload discards registry-bound market stacks");
        var next = VBlackMarket.getPlayerRecord(id);
        require(next != record && next.bankedResets == 0 && next.customItems == null,
                "Next world cannot inherit old personal market stacks or progress");
        VBlackMarket.clearWorldState();
    }

    private static CompoundTag snapshot(UUID id, int diamonds) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.store("Inventory", ItemContainerContents.CODEC,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, diamonds))));
        VCoinsState.writePlayerBalance(id, output);
        VFortuna.writePlayerState(id, output);
        return output.buildResult();
    }

    private static void persistence() throws Exception {
        UUID id = UUID.randomUUID();
        // Exact legacy shape: old five-tier runs must survive migration and a
        // second login after being written into the player's NBT snapshot.
        var legacyFortuna = new CompoundTag();
        legacyFortuna.putString("veloria:fortuna", "{\"active\":false,\"tier\":3,\"charm\":0,\"anchor\":3,\"fragments\":22,\"result\":3,\"revision\":28,\"used\":true,\"choices\":[0,0,2,2,0]}");
        VFortuna.readPlayerState(id, TagValueInput.create(ProblemReporter.DISCARDING, registries, legacyFortuna));
        var migrated = VFortuna.run(id);
        require(Arrays.equals(migrated.choices, new int[]{0,0,2,2,0,0}), "Five-tier legacy outcomes preserved");
        require(migrated.fragments == 22 && migrated.anchor == 3 && migrated.tier == 3
                && migrated.used && !migrated.active && migrated.revision == 28, "Legacy pact fields preserved");
        var migratedSnapshot = snapshot(id, 1);
        VFortuna.readPlayerState(id, TagValueInput.create(ProblemReporter.DISCARDING, registries, migratedSnapshot));
        require(VFortuna.run(id).choices.length == 6 && VFortuna.run(id).fragments == 22,
                "Migrated pact can be loaded again");
        legacyFortuna.putString("veloria:fortuna", "{\"active\":true,\"tier\":4,\"choices\":[2,1,0,1,2]}");
        VFortuna.readPlayerState(id, TagValueInput.create(ProblemReporter.DISCARDING, registries, legacyFortuna));
        require(VFortuna.run(id).active && VFortuna.run(id).tier == 4
                && Arrays.equals(VFortuna.run(id).choices, new int[]{2,1,0,1,2,0}),
                "Active legacy pact retains paid outcomes without reroll");
        legacyFortuna.putString("veloria:fortuna", "{\"choices\":[0,0,0,0,9]}");
        boolean invalidChoiceRejected = false;
        try {
            VFortuna.readPlayerState(id, TagValueInput.create(ProblemReporter.DISCARDING, registries, legacyFortuna));
        } catch (IllegalStateException expected) { invalidChoiceRejected = true; }
        require(invalidChoiceRejected, "Legacy migration does not accept invalid reward indices");
        VFortuna.run(id).active = false;
        var worldA = Files.createTempDirectory("veloria-coins-a-");
        var worldB = Files.createTempDirectory("veloria-coins-b-");
        Files.writeString(worldA.resolve("vcoins.json"), "{\"" + id + "\":1000000}");
        VCoinsState.loadWorld(worldA);
        require(VCoinsState.getCoins(id) == 1000000, "Legacy balance migrated");
        VCoinsState.readPlayerBalance(id, TagValueInput.create(ProblemReporter.DISCARDING, registries, new CompoundTag()));
        require(VCoinsState.getCoins(id) == 1000000, "Missing NBT balance uses legacy migration");
        CompoundTag before = snapshot(id, 1);
        VCoinsState.removeCoins(id, 250000);
        VFortuna.run(id).start(0, new Random(7));
        CompoundTag after = snapshot(id, 2);
        for (var saved : List.of(before, after)) {
            VCoinsState.loadWorld(worldA); // stale legacy JSON must not override committed player data
            var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, saved);
            VCoinsState.readPlayerBalance(id, input);
            VFortuna.readPlayerState(id, input);
            int count = input.read("Inventory", ItemContainerContents.CODEC).orElseThrow().itemCopies()
                    .mapToInt(ItemStack::getCount).sum();
            require(count == 1 ? VCoinsState.getCoins(id) == 1000000 : VCoinsState.getCoins(id) == 750000,
                    "Crash recovery selects a complete inventory/payment snapshot");
            require(VFortuna.run(id).active == (count == 2), "Pact cannot survive rollback of its payment");
        }
        VCoinsState.setCoins(id, 0);
        CompoundTag zero = snapshot(id, 1);
        VCoinsState.loadWorld(worldA);
        VCoinsState.readPlayerBalance(id, TagValueInput.create(ProblemReporter.DISCARDING, registries, zero));
        require(VCoinsState.getCoins(id) == 0, "Zero NBT balance must not resurrect legacy money");
        VCoinsState.setCoins(id, 900);
        VCoinsState.setClientCoins(id, 5000000);
        require(VCoinsState.getCoins(id) == 900, "Client synchronization cannot overwrite integrated-server balance");
        VCoinsState.loadWorld(worldB);
        require(VCoinsState.getCoins(id) == 0, "New world cannot inherit previous world balance");
        VCoinsState.clearClientCoins();
        require(VCoinsState.getClientCoins(id) == 0, "Disconnect clears client display");
        Files.writeString(worldB.resolve("vcoins.json"), "not json");
        boolean rejected = false;
        try { VCoinsState.loadWorld(worldB); } catch (IllegalStateException expected) { rejected = true; }
        require(rejected && VCoinsState.getCoins(id) == 0, "Corrupt legacy data fails closed");
        Files.delete(worldA.resolve("vcoins.json")); Files.delete(worldA);
        Files.delete(worldB.resolve("vcoins.json")); Files.delete(worldB);
    }
}
