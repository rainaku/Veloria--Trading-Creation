package com.vcoins;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class VCoinsPricing {
    private static final Map<String, Long> prices = new HashMap<>();
    private static final Map<String, ShopCategory> categories = new HashMap<>();
    private static volatile boolean initialized = false;

    public static synchronized void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }

    public static synchronized void init() {
        prices.clear();
        categories.clear();
        // ==================== BASIC / EASILY FARMABLE BLOCKS (Priced to eliminate AFK exploit loops) ====================
        setPrice("minecraft:cobblestone", 10);
        setPrice("minecraft:dirt", 8);
        setPrice("minecraft:sand", 10);
        setPrice("minecraft:red_sand", 12);
        setPrice("minecraft:gravel", 10);
        setPrice("minecraft:netherrack", 6);
        setPrice("minecraft:stone", 12);
        setPrice("minecraft:smooth_stone", 16);
        setPrice("minecraft:cobbled_deepslate", 10);
        setPrice("minecraft:deepslate", 12);
        setPrice("minecraft:blackstone", 10);
        setPrice("minecraft:basalt", 10);
        setPrice("minecraft:smooth_basalt", 14);
        setPrice("minecraft:end_stone", 16);
        setPrice("minecraft:obsidian", 150);
        setPrice("minecraft:crying_obsidian", 400);
        setPrice("minecraft:clay_ball", 8);
        setPrice("minecraft:clay", 32);
        setPrice("minecraft:mud", 8);
        setPrice("minecraft:packed_mud", 20);
        setPrice("minecraft:mud_bricks", 24);
        setPrice("minecraft:pointed_dripstone", 15);
        setPrice("minecraft:dripstone_block", 20);
        setPrice("minecraft:snowball", 4);
        setPrice("minecraft:snow_block", 16);
        setPrice("minecraft:ice", 12);
        setPrice("minecraft:packed_ice", 108);
        setPrice("minecraft:blue_ice", 972);
        
        // ==================== WOOD & BAMBOO (Tree Farms / Bamboo Farms) ====================
        setPrice("minecraft:oak_log", 32);
        setPrice("minecraft:spruce_log", 32);
        setPrice("minecraft:birch_log", 32);
        setPrice("minecraft:jungle_log", 32);
        setPrice("minecraft:acacia_log", 32);
        setPrice("minecraft:dark_oak_log", 32);
        setPrice("minecraft:mangrove_log", 32);
        setPrice("minecraft:cherry_log", 32);
        setPrice("minecraft:pale_oak_log", 32);
        setPrice("minecraft:oak_planks", 8);
        setPrice("minecraft:stick", 4);
        setPrice("minecraft:bamboo", 6);

        // ==================== MINERALS (Balanced against Iron Golem & Piglin Gold Farms) ====================
        setPrice("minecraft:coal", 45);
        setPrice("minecraft:charcoal", 35);
        setPrice("minecraft:coal_block", 405);
        setPrice("minecraft:raw_copper", 20);
        setPrice("minecraft:copper_ingot", 25);
        setPrice("minecraft:copper_nugget", 3);
        setPrice("minecraft:raw_copper_block", 180);
        setPrice("minecraft:copper_block", 225);
        setPrice("minecraft:waxed_copper_block", 250);

        // Iron and Gold: rewarding for mining and balanced for industrial farms
        setPrice("minecraft:raw_iron", 60);
        setPrice("minecraft:iron_ingot", 80);
        setPrice("minecraft:iron_nugget", 8);
        setPrice("minecraft:raw_iron_block", 540);
        setPrice("minecraft:iron_block", 720);

        setPrice("minecraft:raw_gold", 100);
        setPrice("minecraft:gold_ingot", 140);
        setPrice("minecraft:gold_nugget", 15);
        setPrice("minecraft:raw_gold_block", 900);
        setPrice("minecraft:gold_block", 1260);

        setPrice("minecraft:redstone", 35);
        setPrice("minecraft:redstone_block", 315);
        setPrice("minecraft:lapis_lazuli", 45);
        setPrice("minecraft:lapis_block", 405);
        setPrice("minecraft:quartz", 40);
        setPrice("minecraft:quartz_block", 160);
        setPrice("minecraft:amethyst_shard", 40);
        setPrice("minecraft:amethyst_block", 160);
        setPrice("minecraft:amethyst_cluster", 160);

        // Economy Currencies & Finite High-Value Minerals (Raid Farm / Villager Trading balanced)
        setPrice("minecraft:emerald", 75);
        setPrice("minecraft:emerald_block", 675);
        setPrice("minecraft:diamond", 10_000);
        setPrice("minecraft:diamond_block", 90_000);
        setPrice("minecraft:ancient_debris", 3_500_000);
        setPrice("minecraft:netherite_scrap", 3_500_000);
        setPrice("minecraft:netherite_ingot", 15_000_000);
        setPrice("minecraft:netherite_block", 135_000_000);
        
        // ==================== MOB & COMBAT REWARDS (AFK Farm Adjusted - Rewarding Grind) ====================
        setPrice("minecraft:rotten_flesh", 10);
        setPrice("minecraft:bone", 18);
        setPrice("minecraft:bone_meal", 6);
        setPrice("minecraft:string", 15);
        setPrice("minecraft:spider_eye", 18);
        setPrice("minecraft:feather", 10);
        setPrice("minecraft:leather", 30);
        setPrice("minecraft:gunpowder", 45);
        setPrice("minecraft:slime_ball", 50);
        setPrice("minecraft:slime_block", 450);
        setPrice("minecraft:magma_cream", 55);
        setPrice("minecraft:magma_block", 220);
        setPrice("minecraft:prismarine_shard", 30);
        setPrice("minecraft:prismarine_crystals", 50);
        setPrice("minecraft:ender_pearl", 60);
        setPrice("minecraft:blaze_rod", 90);
        setPrice("minecraft:blaze_powder", 45);
        setPrice("minecraft:ghast_tear", 400);
        setPrice("minecraft:nautilus_shell", 1_500);
        setPrice("minecraft:shulker_shell", 1_500_000);
        setPrice("minecraft:breeze_rod", 200_000);
        setPrice("minecraft:phantom_membrane", 200);
        setPrice("minecraft:ink_sac", 30);
        setPrice("minecraft:glow_ink_sac", 40);
        setPrice("minecraft:rabbit_hide", 20);
        setPrice("minecraft:rabbit_foot", 120);
        setPrice("minecraft:turtle_scute", 300);
        setPrice("minecraft:armadillo_scute", 200);
        setPrice("minecraft:saddle", 2_500);
        setPrice("minecraft:sponge", 6_000);
        setPrice("minecraft:wet_sponge", 5_000);
        setPrice("minecraft:dragon_breath", 500);

        // Mob Heads (Charged Creeper farm balanced)
        setPrice("minecraft:creeper_head", 5_000);
        setPrice("minecraft:zombie_head", 5_000);
        setPrice("minecraft:skeleton_skull", 5_000);
        setPrice("minecraft:piglin_head", 5_000);

        // Music Discs (Skeleton-Killed-Creeper farm balanced)
        setPrice("minecraft:music_disc_13", 350);
        setPrice("minecraft:music_disc_cat", 350);
        setPrice("minecraft:music_disc_blocks", 350);
        setPrice("minecraft:music_disc_chirp", 350);
        setPrice("minecraft:music_disc_far", 350);
        setPrice("minecraft:music_disc_mall", 350);
        setPrice("minecraft:music_disc_mellohi", 350);
        setPrice("minecraft:music_disc_stal", 350);
        setPrice("minecraft:music_disc_strad", 350);
        setPrice("minecraft:music_disc_ward", 350);
        setPrice("minecraft:music_disc_11", 350);
        setPrice("minecraft:music_disc_wait", 350);
        setPrice("minecraft:music_disc_otherside", 25_000);
        setPrice("minecraft:music_disc_relic", 50_000);
        setPrice("minecraft:music_disc_creator", 25_000);
        setPrice("minecraft:music_disc_creator_music_box", 25_000);
        setPrice("minecraft:music_disc_precipice", 35_000);
        setPrice("minecraft:music_disc_pigstep", 50_000);

        // Froglights (Magma Cube Frog Farm)
        setPrice("minecraft:ochre_froglight", 45);
        setPrice("minecraft:verdant_froglight", 45);
        setPrice("minecraft:pearlescent_froglight", 45);

        // Sculk (Warden / Sculk Farm)
        setPrice("minecraft:sculk", 12);
        setPrice("minecraft:sculk_vein", 4);
        setPrice("minecraft:sculk_sensor", 96);
        setPrice("minecraft:sculk_catalyst", 1_200);
        setPrice("minecraft:sculk_shrieker", 2_500);

        // Boss & Mini-boss Drops: high reward for combat exploration
        setPrice("minecraft:wither_skeleton_skull", 1_800_000);
        setPrice("minecraft:nether_star", 40_000_000);
        setPrice("minecraft:heart_of_the_sea", 20_000_000);
        setPrice("minecraft:echo_shard", 2_000_000);
        setPrice("minecraft:heavy_core", 120_000_000);
        setPrice("minecraft:dragon_head", 50_000_000);
        setPrice("minecraft:dragon_egg", 500_000_000);
        
        // ==================== CROPS & AGRICULTURAL PRODUCTS (Rewarding Grinding) ====================
        setPrice("minecraft:wheat_seeds", 4);
        setPrice("minecraft:wheat", 16);
        setPrice("minecraft:hay_block", 144);
        setPrice("minecraft:potato", 12);
        setPrice("minecraft:baked_potato", 18);
        setPrice("minecraft:poisonous_potato", 5);
        setPrice("minecraft:carrot", 12);
        setPrice("minecraft:beetroot", 12);
        setPrice("minecraft:beetroot_seeds", 4);
        setPrice("minecraft:melon_slice", 5);
        setPrice("minecraft:melon", 35);
        setPrice("minecraft:pumpkin", 25);
        setPrice("minecraft:sugar_cane", 12);
        setPrice("minecraft:kelp", 6);
        setPrice("minecraft:dried_kelp", 8);
        setPrice("minecraft:dried_kelp_block", 72);
        setPrice("minecraft:sweet_berries", 8);
        setPrice("minecraft:glow_berries", 10);
        setPrice("minecraft:cocoa_beans", 12);
        setPrice("minecraft:cactus", 12);
        setPrice("minecraft:apple", 25);
        setPrice("minecraft:beef", 22);
        setPrice("minecraft:porkchop", 22);
        setPrice("minecraft:mutton", 20);
        setPrice("minecraft:chicken", 18);
        setPrice("minecraft:bread", 48);
        setPrice("minecraft:golden_carrot", 180);
        setPrice("minecraft:glistering_melon_slice", 180);
        setPrice("minecraft:golden_apple", 1_500);
        setPrice("minecraft:enchanted_golden_apple", 80_000_000);
        setPrice("minecraft:pumpkin_pie", 75);
        setPrice("minecraft:cake", 500);
        setPrice("minecraft:chorus_fruit", 15);
        setPrice("minecraft:chorus_flower", 60);
        setPrice("minecraft:sea_pickle", 15);
        setPrice("minecraft:shroomlight", 50);
        
        // ==================== COMMONLY CRAFTED MATERIALS ====================
        setPrice("minecraft:glass", 12);
        setPrice("minecraft:white_wool", 20);
        setPrice("minecraft:torch", 12);
        setPrice("minecraft:bone_block", 162);
        setPrice("minecraft:glowstone_dust", 15);
        setPrice("minecraft:glowstone", 60);
        setPrice("minecraft:honeycomb", 20);
        setPrice("minecraft:honeycomb_block", 80);
        setPrice("minecraft:honey_block", 120);
        setPrice("minecraft:honey_bottle", 40);
        setPrice("minecraft:resin_clump", 25);
        setPrice("minecraft:resin_block", 225);
        setPrice("minecraft:disc_fragment_5", 10_000);

        // ==================== ENDGAME / RARE PROGRESSION ITEMS ====================
        setPrice("minecraft:enchanted_book", 50_000);
        setPrice("minecraft:elytra", 200_000_000);
        setPrice("minecraft:totem_of_undying", 20_000_000);
        setPrice("minecraft:beacon", 80_000_000);
        setPrice("minecraft:conduit", 40_000_000);
        setPrice("minecraft:mace", 150_000_000);
        setPrice("minecraft:trident", 3_500_000);
        setPrice("minecraft:sniffer_egg", 80_000);
        
        // Every registered item (including items added by other mods) belongs to the
        // catalogue. Explicit prices above win; everything else receives a sensible
        // fallback price so the "All items" tab is actually complete.
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                ShopCategory category = getCategoryForItem(item);
                categories.put(id, category);

                if (!isTradeable(item)) {
                    prices.remove(id);
                } else if (!prices.containsKey(id)) {
                    prices.put(id, getFallbackPrice(id, category));
                }
            }
        }
        initialized = true;
    }
    
    public static void calculateRecipes(MinecraftServer server) {
        // We disabled recipe parsing due to 1.21.11 API changes and use smart fallback pricing instead.
    }

    private static void setPrice(String id, long price) {
        prices.put(id, price);
    }

    public static long getBasePrice(String itemId) {
        ensureInitialized();
        return prices.getOrDefault(itemId.split("\\|", 2)[0], 0L);
    }

    public static boolean isRareMarketItem(String itemId) {
        if (itemId.contains("|")) return true; // Enchanted variants use rare-item limits.
        Item item = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(itemId));
        Rarity rarity = item.getDefaultInstance().getRarity();
        return rarity == Rarity.RARE || rarity == Rarity.EPIC || VBlackMarket.isValuableItem(item)
                || itemId.contains("diamond") || itemId.contains("netherite")
                || itemId.equals("minecraft:dragon_egg");
    }

    public static long getPrice(String itemId) {
        long base = getBasePrice(itemId);
        if (base <= 0) {
            return 0L;
        }
        double multiplier = VMarketEngine.getMultiplier(itemId);
        return Math.max(1L, Math.round(base * multiplier));
    }

    /** Same enchantment types share a cycle across levels, preserving level ordering. */
    public static String getMarketKey(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var enchantments = new java.util.TreeSet<>(getEnchantmentValues(stack).keySet());
        return enchantments.isEmpty() ? id : id + "|" + String.join(",", enchantments);
    }

    public static long getReferencePrice(ItemStack stack) {
        if (!isTradeable(stack)) return 0L;
        return getBlackMarketReferencePrice(stack);
    }

    /** Acquisition valuation only; bound-item resale/duplication stays forbidden. */
    static long getBlackMarketReferencePrice(ItemStack stack) {
        if (stack.isEmpty() || !isTradeable(stack.getItem())) return 0L;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        long base = getBasePrice(id);
        if (base <= 0) return 0L;
        if (!id.startsWith("minecraft:")) base = Math.max(base, getRarityFloor(stack.getItem().getDefaultInstance().getRarity()));
        return safeAdd(base, getEnchantmentPremium(stack));
    }

    public static long getPrice(ItemStack stack) {
        long reference = getReferencePrice(stack);
        return reference <= 0 ? 0 : Math.max(1L, Math.round(reference * VMarketEngine.getMultiplier(getMarketKey(stack))));
    }

    public static long getReferenceSellPrice(ItemStack stack) {
        if (!isTradeable(stack)) return 0L;
        String id = getSellMarketKey(stack);
        return calculateSellPrice(id, getDurabilityAdjustedPrice(stack, getBasePrice(id)));
    }

    /** Enchantments can be added/combined outside the shop; resale buys materials only. */
    public static String getSellMarketKey(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) ? "minecraft:book"
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static long getSellPrice(String itemId) {
        itemId = itemId.split("\\|", 2)[0];
        if (itemId.equals("minecraft:enchanted_book")) itemId = "minecraft:book";
        long basePrice = getBasePrice(itemId);
        if (basePrice <= 0) {
            return 0L;
        }
        double sellMultiplier = VMarketEngine.getSellMultiplier(itemId);
        long marketAdjustedBuyPrice = Math.max(1L, Math.round(basePrice * sellMultiplier));
        return calculateSellPrice(itemId, marketAdjustedBuyPrice);
    }

    public static long getSellPrice(ItemStack stack) {
        if (!isTradeable(stack)) return 0L;
        String id = getSellMarketKey(stack);
        long reference = getBasePrice(id);
        if (reference <= 0) return 0L;
        long adjusted = Math.max(1L, Math.round(reference * VMarketEngine.getSellMultiplier(id)));
        return calculateSellPrice(id, getDurabilityAdjustedPrice(stack, adjusted));
    }

    private static long calculateSellPrice(String itemId, long buyPrice) {
        if (buyPrice <= 0 || isCreativeOnly(itemId)) {
            return 0L;
        }

        String path = itemId.substring(itemId.indexOf(':') + 1);

        // Prevent infinite money exploits via Smithing Template duplication
        if (path.contains("smithing_template")) {
            return Math.min(buyPrice / 4L, 50_000L);
        }

        // Tiered Sell Margins:
        // 1. Easily farmable resources, basic blocks, mob drops, iron/gold, and craftable redstone/functional items:
        // 30% sell margin, with a minimum floor of 1 coin so grind items are never refused with 0 payout.
        if (isFarmableOrCrafted(path)) {
            return Math.max(1L, percentageOf(buyPrice, 30));
        }

        // 2. Agricultural crops, food, wood, and standard building blocks:
        ShopCategory category = getCategory(itemId);
        if (category == ShopCategory.FOOD || category == ShopCategory.NATURAL || category == ShopCategory.BUILDING) {
            return Math.max(1L, percentageOf(buyPrice, 35));
        }

        // 3. Other items (Tools, Combat, Rare exploration loot): 40% sell margin
        return Math.max(1L, percentageOf(buyPrice, 40));
    }

    private static boolean isFarmableOrCrafted(String path) {
        return containsAny(path,
                // Cobble / Stone / Dirt gen & Raw materials
                "cobblestone", "cobbled_deepslate", "dirt", "gravel", "sand", "netherrack", "basalt",
                "diorite", "andesite", "granite", "tuff", "calcite", "blackstone",
                // Golem iron farm & Gold piglin farm & Copper
                "iron_", "raw_iron", "gold_", "raw_gold", "copper_", "raw_copper",
                // Mob farms (Zombies, Skeletons, Spiders, Creepers, Endermen, Witches, Slimes)
                "rotten_flesh", "bone", "string", "spider_eye", "gunpowder", "feather", "arrow",
                "slime_ball", "slime_block", "magma_cream", "glowstone_dust", "redstone", "sugar",
                "glass_bottle", "ender_pearl",
                // Crop / Plant / Tree automated farms
                "sugar_cane", "bamboo", "cactus", "kelp", "melon", "pumpkin",
                "wheat", "carrot", "potato", "beetroot", "sweet_berries", "glow_berries",
                "cocoa_beans", "nether_wart", "chorus_fruit", "egg", "honey_bottle", "honeycomb",
                // Wood tree farms & Wool sheep farms
                "log", "stem", "wood", "hyphae", "planks", "stick", "wool", "carpet",
                // Functional / Crafting tables / Utility blocks
                "crafting_table", "furnace", "smoker", "blast_furnace", "stonecutter", "grindstone",
                "smithing_table", "fletching_table", "cartography_table", "loom", "anvil", "chest",
                "barrel", "cauldron", "composter", "note_block", "bookshelf", "lectern", "door",
                "trapdoor", "ladder", "scaffolding", "armor_stand", "item_frame",
                // Redstone mechanisms & Rails
                "piston", "repeater", "comparator", "observer", "dispenser", "dropper", "hopper",
                "crafter", "daylight_detector", "target", "tripwire_hook", "rail", "minecart", "tnt",
                // Metal building materials
                "iron_bars", "chain",
                // High-yield mob / AFK drops & boss / ocean farm products
                "ghast_tear", "blaze_rod", "blaze_powder", "phantom_membrane", "prismarine",
                "nautilus_shell", "shulker_shell", "wither_skeleton_skull", "totem_of_undying",
                "emerald", "froglight", "scute", "rabbit_foot", "rabbit_hide", "ink_sac",
                "glow_ink_sac", "music_disc", "sponge", "sculk", "mud", "clay", "ice",
                "amethyst", "shroomlight", "vine", "moss", "dripstone", "saddle", "chorus", "sniffer_egg");
    }

    private static long getDurabilityAdjustedPrice(ItemStack stack, long fullPrice) {
        if (fullPrice <= 0L || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return fullPrice;
        }

        long remaining = Math.max(0L, (long) stack.getMaxDamage() - stack.getDamageValue());
        // Twenty percent represents the material/enchantment salvage value; the
        // other eighty percent follows remaining durability.
        long durabilityPercent = 20L + remaining * 80L / stack.getMaxDamage();
        return safeMultiply(fullPrice / 100L, durabilityPercent)
                + fullPrice % 100L * durabilityPercent / 100L;
    }

    public static long getBuybackPrice(String itemId) {
        long sellPrice = getSellPrice(itemId);
        return calculateBuybackPrice(sellPrice);
    }

    public static long getBuybackPrice(ItemStack stack) {
        return calculateBuybackPrice(getSellPrice(stack));
    }

    public static long calculateBuybackPrice(long sellPrice) {
        if (sellPrice <= 0) {
            return 0L;
        }
        return safeAdd(sellPrice, Math.max(1L, sellPrice / 10L));
    }

    public static int getEnchantmentCount(ItemStack stack) {
        return getEnchantmentValues(stack).size();
    }

    public static int getTotalEnchantmentLevels(ItemStack stack) {
        int total = 0;
        for (EnchantmentValue value : getEnchantmentValues(stack).values()) {
            total = Math.min(1000, total + value.level());
        }
        return total;
    }

    public static ShopCategory getCategory(String itemId) {
        ensureInitialized();
        return categories.getOrDefault(itemId, ShopCategory.ALL);
    }

    public static Map<String, Long> getAllPrices() {
        ensureInitialized();
        return prices;
    }

    public static boolean isTradeable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Legend and Mythic items (VNoTrade tag) cannot be traded, sold, or duplicated
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().getBoolean("VNoTrade").orElse(false)) {
            return false;
        }
        return !VBlackMarket.isRomanGodItem(stack)
                && !VFortuna.isReward(stack)
                && !(VBlackMarket.isBlackMarketItem(stack) && VBlackMarket.isEquipment(stack))
                && isTradeable(stack.getItem());
    }

    public static boolean isTradeable(Item item) {
        return item != Items.AIR && !isCreativeOnly(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    private static long getFallbackPrice(String id, ShopCategory category) {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.endsWith("_spawn_egg")) {
            return 0L;
        }
        if (path.contains("smithing_template")) {
            return getSmithingTemplatePrice(path);
        }
        if (path.equals("elytra")) {
            return 200_000_000L;
        }
        if (path.equals("totem_of_undying")) {
            return 20_000_000L;
        }
        if (path.equals("beacon")) {
            return 80_000_000L;
        }
        if (path.equals("mace")) {
            return 150_000_000L;
        }
        if (path.equals("trident")) {
            return 3_500_000L;
        }
        if (path.equals("dragon_head")) {
            return 50_000_000L;
        }
        if (path.endsWith("_head") || path.endsWith("_skull")) {
            return path.equals("wither_skeleton_skull") ? 1_800_000L : 5_000L;
        }
        if (path.startsWith("music_disc_")) {
            return getMusicDiscPrice(path);
        }
        if (path.equals("ominous_trial_key")) {
            return 800_000L;
        }
        if (path.equals("trial_key")) {
            return 150_000L;
        }
        if (path.equals("enchanted_book")) {
            return 50_000L;
        }
        if (path.contains("shulker_box")) {
            return 3_000_000L;
        }

        long effortPrice = getEquipmentPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }
        effortPrice = getWoodFamilyPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }
        effortPrice = getCopperFamilyPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }
        effortPrice = getColoredItemPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }
        effortPrice = getNaturalItemPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }
        effortPrice = getBuildingItemPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }
        effortPrice = getFunctionalItemPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }
        effortPrice = getRedstoneItemPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }
        effortPrice = getIngredientPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }
        effortPrice = getFoodPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }
        effortPrice = getMiscItemPrice(path);
        if (effortPrice > 0L) {
            return effortPrice;
        }

        // This final tier is mainly for items added by other mods. Vanilla items
        // are handled by the effort-based families above.
        return switch (category) {
            case BUILDING, COLORED, NATURAL -> 8L;
            case FOOD -> 12L;
            case FUNCTIONAL -> 128L;
            case REDSTONE -> 96L;
            case INGREDIENTS -> 32L;
            case SPAWN_EGGS -> 50_000L;
            case MISC, ALL, BUYBACK, BLACK_MARKET -> 32L;
            case TOOLS, COMBAT -> 256L;
        };
    }

    private static long getEquipmentPrice(String path) {
        long exactPrice = switch (path) {
            case "bow" -> 40L;
            case "crossbow" -> 65L;
            case "arrow" -> 2L;
            case "spectral_arrow" -> 16L;
            case "tipped_arrow" -> 32L;
            case "shield" -> 40L;
            case "shears" -> 60L;
            case "flint_and_steel" -> 45L;
            case "fishing_rod" -> 15L;
            case "carrot_on_a_stick", "warped_fungus_on_a_stick" -> 25L;
            case "brush" -> 32L;
            case "spyglass" -> 80L;
            case "compass" -> 140L;
            case "clock" -> 260L;
            case "recovery_compass" -> 25_000_000L;
            case "bucket" -> 90L;
            case "turtle_helmet" -> 8_000L;
            case "wolf_armor" -> 6_000L;
            case "leather_horse_armor" -> 150L;
            case "iron_horse_armor" -> 15_000L;
            case "golden_horse_armor" -> 30_000L;
            case "diamond_horse_armor" -> 120_000L;
            case "netherite_horse_armor" -> 60_000_000L;
            case "copper_nautilus_armor" -> 4_000L;
            case "iron_nautilus_armor" -> 12_000L;
            case "golden_nautilus_armor" -> 24_000L;
            case "diamond_nautilus_armor" -> 120_000L;
            case "netherite_nautilus_armor" -> 60_000_000L;
            default -> 0L;
        };
        if (exactPrice > 0L) {
            return exactPrice;
        }

        int materialUnits = getEquipmentMaterialUnits(path);
        if (materialUnits <= 0) {
            return 0L;
        }

        if (path.startsWith("netherite_")) {
            String diamondPath = "diamond_" + path.substring("netherite_".length());
            long diamondItem = getEquipmentPrice(diamondPath);
            // Netherite ingot (15M) + Netherite upgrade template reproduction cost (expensive)
            long reproducibleTemplateCost = 5_000_000L;
            return addCraftingEffort(safeAdd(diamondItem,
                    safeAdd(15_000_000L, reproducibleTemplateCost)), 10);
        }

        long materialUnitPrice = getEquipmentMaterialUnitPrice(path);
        if (materialUnitPrice <= 0L) {
            return 0L;
        }

        int stickCount = getEquipmentStickCount(path);
        long ingredients = safeAdd(safeMultiply(materialUnitPrice, materialUnits),
                safeMultiply(1L, stickCount));
        return addCraftingEffort(ingredients, 20);
    }

    private static int getEquipmentMaterialUnits(String path) {
        if (path.endsWith("_helmet")) return 5;
        if (path.endsWith("_chestplate")) return 8;
        if (path.endsWith("_leggings")) return 7;
        if (path.endsWith("_boots")) return 4;
        if (path.endsWith("_pickaxe") || path.endsWith("_axe")) return 3;
        if (path.endsWith("_sword") || path.endsWith("_hoe")) return 2;
        if (path.endsWith("_shovel") || path.endsWith("_spear")) return 1;
        return 0;
    }

    private static int getEquipmentStickCount(String path) {
        if (path.endsWith("_pickaxe") || path.endsWith("_axe")
                || path.endsWith("_hoe") || path.endsWith("_shovel")) {
            return 2;
        }
        if (path.endsWith("_spear")) {
            return 2;
        }
        return path.endsWith("_sword") ? 1 : 0;
    }

    private static long getEquipmentMaterialUnitPrice(String path) {
        if (path.startsWith("wooden_")) return 2L;
        if (path.startsWith("stone_")) return 6L;
        if (path.startsWith("copper_")) return 10L;
        if (path.startsWith("iron_") || path.startsWith("chainmail_")) return 30L;
        if (path.startsWith("golden_")) return 60L;
        if (path.startsWith("diamond_")) return 10_000L;
        if (path.startsWith("leather_")) return 16L;
        return 0L;
    }

    private static long getWoodFamilyPrice(String path) {
        if (!containsAny(path, "oak", "spruce", "birch", "jungle", "acacia",
                "mangrove", "cherry", "crimson", "warped", "bamboo")) {
            return 0L;
        }

        if (path.equals("bamboo_block") || path.equals("stripped_bamboo_block")) return 18L;
        if (path.equals("bamboo_planks")) return 9L;
        if (path.equals("bamboo_mosaic")) return 10L;
        if (path.equals("bamboo_mosaic_slab")) return 5L;
        if (path.equals("bamboo_mosaic_stairs")) return 15L;
        if (path.equals("bamboo_chest_raft")) return 113L;
        if (path.equals("bamboo_raft")) return 54L;
        if (path.equals("bamboo_hanging_sign")) return 173L;
        if (path.equals("bamboo_sign")) return 23L;
        if (path.equals("bamboo_fence_gate")) return 31L;
        if (path.equals("bamboo_fence")) return 16L;
        if (path.equals("bamboo_trapdoor")) return 33L;
        if (path.equals("bamboo_door")) return 22L;
        if (path.equals("bamboo_pressure_plate")) return 22L;
        if (path.equals("bamboo_button")) return 11L;
        if (path.equals("bamboo_shelf")) return 21L;
        if (path.equals("bamboo_stairs")) return 14L;
        if (path.equals("bamboo_slab")) return 5L;
        if (path.endsWith("_chest_boat") || path.endsWith("_chest_raft")) return 80L;
        if (path.endsWith("_boat") || path.endsWith("_raft")) return 24L;
        if (path.endsWith("_hanging_sign")) return 96L;
        if (path.endsWith("_sign")) return 12L;
        if (path.endsWith("_fence_gate")) return 20L;
        if (path.endsWith("_fence")) return 8L;
        if (path.endsWith("_trapdoor")) return 15L;
        if (path.endsWith("_door")) return 10L;
        if (path.endsWith("_pressure_plate")) return 10L;
        if (path.endsWith("_button")) return 5L;
        if (path.endsWith("_shelf")) return 20L;
        if (path.endsWith("_stairs")) return 6L;
        if (path.endsWith("_slab")) return 2L;
        if (path.endsWith("_planks")) return 4L;
        if (path.endsWith("_leaves")) return 4L;
        if (path.endsWith("_sapling") || path.endsWith("_propagule")) return 16L;
        if (path.endsWith("_wood") || path.endsWith("_hyphae")) return 22L;
        if (path.endsWith("_log") || path.endsWith("_stem")) return 16L;
        return 0L;
    }

    private static long getCopperFamilyPrice(String path) {
        boolean waxed = path.startsWith("waxed_");
        String normalized = waxed ? path.substring("waxed_".length()) : path;
        if (normalized.startsWith("exposed_")) {
            normalized = normalized.substring("exposed_".length());
        } else if (normalized.startsWith("weathered_")) {
            normalized = normalized.substring("weathered_".length());
        } else if (normalized.startsWith("oxidized_")) {
            normalized = normalized.substring("oxidized_".length());
        }

        if (!normalized.contains("copper")
                || containsAny(normalized, "raw_copper", "copper_ore", "copper_ingot",
                "copper_nugget", "copper_pickaxe", "copper_axe", "copper_hoe",
                "copper_shovel", "copper_sword", "copper_spear", "copper_helmet",
                "copper_chestplate", "copper_leggings", "copper_boots")) {
            return 0L;
        }

        long basePrice;
        long waxSurcharge = 32L;
        if (normalized.equals("copper_block")) {
            basePrice = 576L;
        } else if (normalized.contains("cut_copper_slab")) {
            // A stonecutter makes eight slabs from one copper block.
            basePrice = 72L;
            waxSurcharge = 4L;
        } else if (normalized.contains("cut_copper")) {
            // A stonecutter makes four cut blocks or stairs from one block.
            basePrice = 144L;
            waxSurcharge = 8L;
        } else if (normalized.contains("chiseled_copper")) {
            basePrice = 144L;
            waxSurcharge = 8L;
        } else if (normalized.contains("copper_grate")) {
            basePrice = 576L;
        } else if (normalized.contains("copper_bulb")) {
            basePrice = 840L;
        } else if (normalized.contains("copper_trapdoor")) {
            basePrice = 308L;
        } else if (normalized.contains("copper_door")) {
            basePrice = 154L;
        } else if (normalized.contains("copper_bars")) {
            basePrice = 30L;
        } else if (normalized.contains("copper_chain") || normalized.contains("copper_lantern")) {
            basePrice = 96L;
        } else if (normalized.contains("copper_torch")) {
            basePrice = 24L;
        } else if (normalized.contains("copper_chest")) {
            basePrice = 664L;
        } else if (normalized.contains("copper_golem_statue")) {
            basePrice = 640L;
        } else if (normalized.contains("lightning_rod")) {
            basePrice = 230L;
        } else {
            basePrice = 576L;
        }
        return waxed ? safeAdd(basePrice, waxSurcharge) : basePrice;
    }

    private static long getColoredItemPrice(String path) {
        if (path.endsWith("_stained_glass_pane")) return 4L;
        if (path.endsWith("_stained_glass")) return 10L;
        if (path.endsWith("_glazed_terracotta")) return 70L;
        if (path.endsWith("_terracotta")) return 58L;
        if (path.endsWith("_concrete_powder")) return 7L;
        if (path.endsWith("_concrete")) return 10L;
        if (path.endsWith("_carpet")) return 6L;
        if (path.endsWith("_wool")) return 8L;
        if (path.endsWith("_bed")) return 72L;
        if (path.endsWith("_banner")) return 120L;
        if (path.endsWith("_candle")) return 60L;
        if (path.endsWith("_dye")) return 4L;
        if (path.endsWith("_bundle")) return 96L;
        if (path.endsWith("_harness")) return 210L;
        return 0L;
    }

    private static long getNaturalItemPrice(String path) {
        long exactPrice = switch (path) {
            case "coal_ore" -> 72L;
            case "deepslate_coal_ore" -> 80L;
            case "copper_ore" -> 144L;
            case "deepslate_copper_ore" -> 160L;
            case "iron_ore" -> 192L;
            case "deepslate_iron_ore" -> 208L;
            case "gold_ore", "nether_gold_ore" -> 512L;
            case "deepslate_gold_ore" -> 544L;
            case "redstone_ore" -> 160L;
            case "deepslate_redstone_ore" -> 176L;
            case "lapis_ore" -> 384L;
            case "deepslate_lapis_ore" -> 416L;
            case "diamond_ore" -> 9_000L;
            case "deepslate_diamond_ore" -> 9_500L;
            case "emerald_ore" -> 30_000L;
            case "deepslate_emerald_ore" -> 35_000L;
            case "nether_quartz_ore" -> 112L;
            case "granite", "diorite", "andesite", "tuff", "calcite",
                    "dripstone_block", "pointed_dripstone", "basalt",
                    "smooth_basalt", "magma_block" -> 8L;
            case "grass_block", "podzol", "mycelium", "rooted_dirt", "mud",
                    "coarse_dirt", "moss_block", "pale_moss_block" -> 6L;
            case "soul_sand", "soul_soil" -> 8L;
            case "amethyst_cluster" -> 64L;
            case "large_amethyst_bud" -> 48L;
            case "medium_amethyst_bud" -> 32L;
            case "small_amethyst_bud" -> 16L;
            case "sponge", "wet_sponge" -> 3_500L;
            case "sculk" -> 12L;
            case "sculk_sensor" -> 96L;
            case "calibrated_sculk_sensor" -> 180L;
            case "sculk_catalyst" -> 1_200L;
            case "sculk_shrieker" -> 2_500L;
            case "bee_nest" -> 512L;
            case "beehive" -> 160L;
            case "turtle_egg" -> 512L;
            case "frogspawn" -> 512L;
            case "ochre_froglight", "pearlescent_froglight", "verdant_froglight" -> 45L;
            case "chorus_flower" -> 32L;
            case "chorus_plant", "chorus_fruit" -> 8L;
            case "spore_blossom", "wither_rose" -> 256L;
            default -> 0L;
        };
        if (exactPrice > 0L) {
            return exactPrice;
        }
        if (path.contains("coral")) return 64L;
        if (containsAny(path, "flower", "tulip", "daisy", "orchid", "bluet",
                "allium", "dandelion", "poppy", "lilac", "peony", "sunflower")) {
            return 4L;
        }
        return 0L;
    }

    private static long getBuildingItemPrice(String path) {
        long exactPrice = switch (path) {
            case "bricks" -> 64L;
            case "packed_mud", "mud_bricks" -> 24L;
            case "nether_bricks" -> 32L;
            case "red_nether_bricks" -> 36L;
            case "iron_bars" -> 15L;
            case "chain" -> 42L;
            case "end_rod" -> 65L;
            case "sea_lantern" -> 220L;
            case "prismarine" -> 154L;
            case "prismarine_bricks" -> 346L;
            case "dark_prismarine" -> 320L;
            case "purpur_block", "purpur_pillar" -> 48L;
            case "end_stone_bricks" -> 20L;
            case "terracotta" -> 56L;
            case "glass_pane" -> 4L;
            case "tinted_glass" -> 55L;
            default -> 0L;
        };
        if (exactPrice > 0L) {
            return exactPrice;
        }

        long basePrice = getStoneFamilyBasePrice(path);
        if (basePrice <= 0L) {
            return 0L;
        }
        if (path.endsWith("_slab")) {
            return Math.max(2L, (basePrice + 1L) / 2L);
        }
        if (containsAny(path, "polished_", "smooth_", "_bricks", "_brick_")) {
            return safeAdd(basePrice, Math.max(2L, basePrice / 4L));
        }
        return basePrice;
    }

    private static long getStoneFamilyBasePrice(String path) {
        if (path.contains("red_sandstone")) return 32L;
        if (path.contains("sandstone")) return 16L;
        if (path.contains("quartz")) return 384L;
        if (path.contains("dark_prismarine")) return 320L;
        if (path.contains("prismarine_brick")) return 346L;
        if (path.contains("prismarine")) return 154L;
        if (path.contains("red_nether_brick")) return 36L;
        if (path.contains("nether_brick")) return 32L;
        if (path.contains("mud_brick")) return 24L;
        if (path.startsWith("brick_")) return 64L;
        if (path.contains("resin_brick")) return 64L;
        if (path.contains("end_stone")) return 16L;
        if (path.contains("blackstone")) return 12L;
        if (path.contains("deepslate")) return 8L;
        if (containsAny(path, "granite", "diorite", "andesite", "tuff")) return 8L;
        if (path.equals("stone") || path.startsWith("stone_")
                || path.startsWith("smooth_stone") || path.contains("stone_brick")) {
            return 6L;
        }
        if (path.contains("cobblestone")) return 4L;
        return 0L;
    }

    private static long getFunctionalItemPrice(String path) {
        return switch (path) {
            case "crafting_table" -> 8L;
            case "chest" -> 16L;
            case "trapped_chest" -> 36L;
            case "barrel" -> 16L;
            case "furnace" -> 15L;
            case "smoker" -> 60L;
            case "blast_furnace" -> 220L;
            case "stonecutter" -> 55L;
            case "grindstone" -> 30L;
            case "smithing_table" -> 85L;
            case "fletching_table", "loom" -> 20L;
            case "cartography_table" -> 25L;
            case "anvil" -> 1_100L;
            case "chipped_anvil" -> 750L;
            case "damaged_anvil" -> 400L;
            case "enchanting_table" -> 22_000L;
            case "brewing_stand" -> 220L;
            case "ender_chest" -> 1_300L;
            case "cauldron" -> 250L;
            case "composter" -> 18L;
            case "jukebox" -> 10_800L;
            case "note_block" -> 35L;
            case "respawn_anchor" -> 4_200L;
            case "lodestone" -> 192_000L;
            case "bell" -> 8_000L;
            case "decorated_pot" -> 8_500L;
            case "bookshelf" -> 135L;
            case "chiseled_bookshelf" -> 20L;
            case "lectern" -> 150L;
            case "ladder" -> 4L;
            case "scaffolding" -> 3L;
            case "armor_stand" -> 15L;
            case "flower_pot" -> 50L;
            case "item_frame" -> 30L;
            case "glow_item_frame" -> 75L;
            case "iron_door" -> 75L;
            case "iron_trapdoor" -> 145L;
            default -> 0L;
        };
    }

    private static long getRedstoneItemPrice(String path) {
        return switch (path) {
            case "redstone_torch" -> 14L;
            case "lever" -> 8L;
            case "stone_button" -> 8L;
            case "repeater" -> 55L;
            case "comparator" -> 75L;
            case "piston" -> 65L;
            case "sticky_piston" -> 95L;
            case "observer" -> 60L;
            case "dispenser" -> 105L;
            case "dropper" -> 55L;
            case "hopper" -> 200L;
            case "crafter" -> 240L;
            case "daylight_detector" -> 75L;
            case "target" -> 60L;
            case "redstone_lamp" -> 75L;
            case "tripwire_hook" -> 16L;
            case "tnt" -> 100L;
            case "rail" -> 15L;
            case "powered_rail" -> 75L;
            case "detector_rail" -> 42L;
            case "activator_rail" -> 42L;
            case "minecart" -> 180L;
            case "chest_minecart" -> 195L;
            case "hopper_minecart" -> 380L;
            case "furnace_minecart" -> 195L;
            case "tnt_minecart" -> 280L;
            default -> 0L;
        };
    }

    private static long getIngredientPrice(String path) {
        long exactPrice = switch (path) {
            case "flint" -> 12L;
            case "paper" -> 4L;
            case "book" -> 35L;
            case "brick" -> 16L;
            case "nether_brick" -> 8L;
            case "ink_sac" -> 15L;
            case "glow_ink_sac" -> 20L;
            case "rabbit_hide" -> 8L;
            case "rabbit_foot" -> 60L;
            case "phantom_membrane" -> 120L;
            case "armadillo_scute" -> 120L;
            case "turtle_scute", "scute" -> 180L;
            case "fermented_spider_eye" -> 20L;
            case "fire_charge" -> 45L;
            case "ender_eye" -> 150L;
            case "wind_charge" -> 50_000L;
            case "breeze_rod" -> 200_000L;
            case "echo_shard" -> 2_000_000L;
            case "heavy_core" -> 120_000_000L;
            case "trial_key" -> 1_000_000L;
            case "ominous_trial_key" -> 5_000_000L;
            default -> 0L;
        };
        if (exactPrice > 0L) {
            return exactPrice;
        }
        if (path.endsWith("_dye")) return 4L;
        if (path.contains("pottery_sherd") || path.contains("pottery_shard")) return 2_000L;
        if (path.equals("flower_banner_pattern")) return 20L;
        if (path.equals("field_masoned_banner_pattern")) return 84L;
        if (path.equals("bordure_indented_banner_pattern")) return 26L;
        if (path.equals("creeper_banner_pattern") || path.equals("skull_banner_pattern")) return 90_000L;
        if (path.equals("mojang_banner_pattern")) return 1_200_000L;
        if (path.endsWith("_banner_pattern")) return 25_000L;
        return 0L;
    }

    private static long getFoodPrice(String path) {
        return switch (path) {
            case "beetroot", "sweet_berries", "glow_berries", "kelp" -> 4L;
            case "pumpkin" -> 8L;
            case "cocoa_beans", "sugar_cane", "nether_wart" -> 4L;
            case "beetroot_seeds", "melon_seeds", "pumpkin_seeds" -> 2L;
            case "egg" -> 3L;
            case "sugar" -> 2L;
            case "bread" -> 18L;
            case "baked_potato" -> 6L;
            case "cooked_beef", "cooked_porkchop", "cooked_mutton" -> 12L;
            case "cooked_chicken", "cooked_rabbit" -> 10L;
            case "beef", "porkchop", "mutton" -> 8L;
            case "chicken", "rabbit" -> 6L;
            case "cod", "salmon" -> 10L;
            case "cooked_cod", "cooked_salmon" -> 18L;
            case "pufferfish", "tropical_fish" -> 36L;
            case "cookie" -> 3L;
            case "pumpkin_pie" -> 30L;
            case "cake" -> 350L;
            case "mushroom_stew", "beetroot_soup", "rabbit_stew", "suspicious_stew" -> 24L;
            case "golden_carrot", "glistering_melon_slice" -> 70L;
            case "honey_bottle" -> 16L;
            case "potion" -> 32L;
            case "splash_potion" -> 80L;
            case "lingering_potion" -> 180L;
            default -> 0L;
        };
    }

    private static long getMiscItemPrice(String path) {
        return switch (path) {
            case "bowl" -> 3L;
            case "glass_bottle" -> 5L;
            case "water_bucket" -> 95L;
            case "lava_bucket" -> 120L;
            case "milk_bucket", "powder_snow_bucket" -> 110L;
            case "cod_bucket", "salmon_bucket", "tropical_fish_bucket",
                    "pufferfish_bucket" -> 130L;
            case "axolotl_bucket", "tadpole_bucket" -> 200L;
            case "lead" -> 18L;
            case "name_tag" -> 8_000L;
            case "saddle" -> 1_500L;
            case "experience_bottle" -> 8_000L;
            case "firework_rocket" -> 20L;
            case "firework_star" -> 60L;
            case "map", "empty_map" -> 120L;
            case "painting" -> 20L;
            case "writable_book" -> 40L;
            case "written_book" -> 60L;
            case "ominous_bottle" -> 30_000L;
            default -> 0L;
        };
    }

    private static long addCraftingEffort(long ingredientPrice, int percent) {
        return safeAdd(ingredientPrice, percentageOf(ingredientPrice, percent));
    }

    private static long getSmithingTemplatePrice(String path) {
        if (path.contains("silence_armor_trim")) {
            return 60_000_000L; // Extremely rare drop in Ancient City (1.2% chest chance)
        }
        if (path.contains("netherite_upgrade")) {
            return 20_000_000L; // Found in Bastion Remnants â€” bottleneck for netherite gear
        }
        if (containsAny(path, "ward_armor_trim", "spire_armor_trim", "rib_armor_trim")) {
            return 25_000_000L; // Ancient City, End City, Nether Fortress
        }
        if (containsAny(path, "eye_armor_trim", "snout_armor_trim", "vex_armor_trim",
                "tide_armor_trim", "flow_armor_trim", "bolt_armor_trim")) {
            return 12_000_000L; // Stronghold, Bastion, Woodland Mansion, Ocean Monument, Trial Chamber
        }
        return 7_000_000L;
    }

    private static long getMusicDiscPrice(String path) {
        if (containsAny(path, "pigstep", "relic")) {
            return 50_000L;
        }
        if (containsAny(path, "otherside", "creator", "precipice")) {
            return 25_000L;
        }
        return 350L;
    }

    private static long getRarityFloor(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 0L;
            case UNCOMMON -> 50_000L;
            case RARE -> 500_000L;
            case EPIC -> 2_500_000L;
        };
    }

    private static long getEnchantmentPremium(ItemStack stack) {
        Map<String, EnchantmentValue> enchantments = getEnchantmentValues(stack);
        if (enchantments.isEmpty()) {
            return 0L;
        }

        long premium = 0L;
        for (Map.Entry<String, EnchantmentValue> entry : enchantments.entrySet()) {
            EnchantmentValue value = entry.getValue();
            int level = Math.max(1, value.level());
            long unitPrice = getEnchantmentUnitPrice(entry.getKey(), value.enchantment().value().getWeight());
            long enchantmentPrice = safeMultiply(unitPrice, (long) level * level);

            // A max-level enchantment is more desirable than an unfinished one.
            if (level >= value.enchantment().value().getMaxLevel()) {
                enchantmentPrice = safeAdd(enchantmentPrice, percentageOf(enchantmentPrice, 25));
            }

            // High-level enchantments receive a 15x price buff to prevent early-game economy inflation.
            if (isHighLevelEnchantment(entry.getKey(), level, value.enchantment().value().getMaxLevel())) {
                enchantmentPrice = safeMultiply(enchantmentPrice, 15L);
            }
            premium = safeAdd(premium, enchantmentPrice);
        }

        String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (itemPath.endsWith("enchanted_book")) {
            // Books are directly reusable upgrade materials, so their enchantment
            // value is higher than the same enchantments already bound to an item.
            premium = safeAdd(premium, percentageOf(premium, 50));
        }

        // Multi-enchantment combinations are disproportionately useful. Each
        // enchantment after the first adds 15%, capped at a 75% combo bonus.
        int comboPercent = Math.min(75, (enchantments.size() - 1) * 15);
        return safeAdd(premium, percentageOf(premium, comboPercent));
    }

    private static long getEnchantmentUnitPrice(String id, int weight) {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.contains("curse")) {
            return 2_000L;
        }

        return switch (path) {
            case "mending" -> 5_000_000L;
            case "wind_burst" -> 3_000_000L;
            case "swift_sneak" -> 2_000_000L;
            case "soul_speed" -> 1_000_000L;
            case "silk_touch", "infinity" -> 2_500_000L;
            case "efficiency" -> 300_000L;
            case "fortune", "looting" -> 800_000L;
            case "unbreaking" -> 400_000L;
            case "protection", "sharpness", "power" -> 250_000L;
            case "channeling" -> 600_000L;
            default -> {
                if (weight >= 10) yield 50_000L;
                if (weight >= 5) yield 150_000L;
                if (weight >= 2) yield 400_000L;
                yield 800_000L;
            }
        };
    }

    private static boolean isHighLevelEnchantment(String id, int level, int maxLevel) {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.contains("curse")) {
            return false;
        }
        if (maxLevel <= 1) {
            return true;
        }
        if (maxLevel <= 3) {
            return level >= 2;
        }
        return level >= 3;
    }

    public static Map<String, EnchantmentValue> getEnchantmentValues(ItemStack stack) {
        Map<String, EnchantmentValue> values = new HashMap<>();
        if (stack.isEmpty()) {
            return values;
        }

        collectEnchantments(values, stack.getEnchantments());
        collectEnchantments(values, stack.getOrDefault(
                DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY));
        return values;
    }

    private static void collectEnchantments(Map<String, EnchantmentValue> values,
                                            ItemEnchantments component) {
        for (Holder<Enchantment> enchantment : component.keySet()) {
            String id = enchantment.getRegisteredName();
            int level = component.getLevel(enchantment);
            EnchantmentValue previous = values.get(id);
            if (previous == null || level > previous.level()) {
                values.put(id, new EnchantmentValue(enchantment, level));
            }
        }
    }

    private static long percentageOf(long value, int percentage) {
        if (value <= 0L || percentage <= 0) {
            return 0L;
        }
        return safeAdd(safeMultiply(value / 100L, percentage),
                (value % 100L) * percentage / 100L);
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

    public record EnchantmentValue(Holder<Enchantment> enchantment, int level) {
    }

    public static boolean matchesCategory(Item item, ShopCategory category) {
        if (category == ShopCategory.ALL) {
            return item != Items.AIR;
        }

        String id = BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(Locale.ROOT);
        ShopCategory primary = getCategoryForItem(item);

        if (category == ShopCategory.BUILDING) {
            return item instanceof BlockItem && primary != ShopCategory.REDSTONE && primary != ShopCategory.FUNCTIONAL;
        }
        if (category == ShopCategory.COLORED) {
            return containsAny(id, "wool", "carpet", "concrete", "terracotta", "stained_glass", "glazed_terracotta",
                    "candle", "banner", "_bed", "dye");
        }
        if (category == ShopCategory.NATURAL) {
            return primary != ShopCategory.REDSTONE && containsAny(id, "log", "wood", "stem", "hyphae", "leaves", "sapling", "propagule", "flower",
                    "mushroom", "coral", "dirt", "grass", "sand", "gravel", "clay", "stone", "ore", "ice", "snow",
                    "moss", "vine", "roots", "bamboo", "cactus", "kelp", "dripleaf", "azalea");
        }

        return primary == category;
    }

    private static ShopCategory getCategoryForItem(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(Locale.ROOT);

        if (id.endsWith("_spawn_egg")) {
            return ShopCategory.SPAWN_EGGS;
        }
        if (id.contains("sword") || id.contains("helmet") || id.contains("chestplate") || id.contains("leggings")
                || id.contains("boots") || id.contains("bow") || id.contains("shield") || id.contains("trident")
                || id.contains("mace") || id.contains("arrow")) {
            return ShopCategory.COMBAT;
        }
        if (id.contains("pickaxe") || id.contains("_axe") || id.contains("shovel") || id.contains("hoe")
                || id.contains("shears") || id.contains("flint_and_steel") || id.contains("fishing_rod")
                || id.contains("brush") || id.contains("spyglass") || id.contains("compass") || id.contains("clock")) {
            return ShopCategory.TOOLS;
        }
        if (isFood(item, id)) {
            return ShopCategory.FOOD;
        }
        if (isRedstoneItem(id)) {
            return ShopCategory.REDSTONE;
        }
        if (isFunctionalItem(id)) {
            return ShopCategory.FUNCTIONAL;
        }
        if (item instanceof BlockItem) {
            return ShopCategory.BUILDING;
        }
        if (isIngredient(id)) {
            return ShopCategory.INGREDIENTS;
        }
        return ShopCategory.MISC;
    }

    private static boolean isFood(Item item, String id) {
        if (id.contains("potion") || id.contains("stew") || id.contains("soup") || id.contains("bottle")
                || id.contains("apple") || id.contains("bread") || id.contains("beef") || id.contains("porkchop")
                || id.contains("mutton") || id.contains("chicken") || id.contains("rabbit") || id.contains("fish")
                || id.contains("salmon") || id.contains("cod") || id.contains("berry") || id.contains("berries")
                || id.contains("carrot") || id.contains("potato") || id.contains("beetroot") || id.contains("melon")
                || id.contains("pie") || id.contains("cake") || id.contains("cookie") || id.contains("meat")) {
            return true;
        }
        try {
            if (item.builtInRegistryHolder().areComponentsBound()) {
                return item.components().has(DataComponents.FOOD);
            }
        } catch (Throwable ignored) {
            // Registry components may not be bound yet or food component is absent
        }
        return false;
    }

    private static boolean isRedstoneItem(String id) {
        return containsAny(id, "redstone", "piston", "repeater", "comparator", "observer", "dispenser", "dropper",
                "hopper", "lever", "pressure_plate", "tripwire", "daylight_detector", "target", "sculk_sensor",
                "crafter", "rail", "minecart", "tnt");
    }

    private static boolean isFunctionalItem(String id) {
        return containsAny(id, "crafting_table", "furnace", "smoker", "blast_furnace", "stonecutter", "grindstone",
                "smithing_table", "fletching_table", "cartography_table", "loom", "anvil", "chest", "barrel",
                "shulker_box", "ender_chest", "enchanting_table", "brewing_stand", "beacon", "conduit", "cauldron",
                "composter", "jukebox", "note_block", "respawn_anchor", "lodestone", "bell", "decorated_pot",
                "bookshelf", "lectern", "_bed", "door", "trapdoor", "fence_gate", "ladder", "scaffolding");
    }

    private static boolean isIngredient(String id) {
        return containsAny(id, "ingot", "nugget", "raw_", "coal", "diamond", "emerald", "quartz", "amethyst",
                "shard", "dust", "slime_ball", "magma_cream", "bone", "string", "leather", "feather", "blaze_rod",
                "blaze_powder", "stick", "paper", "book", "dye", "membrane", "shell", "pearl", "tear", "flint",
                "prismarine_crystals", "prismarine_shard", "nether_star", "echo_shard", "breeze_rod", "trial_key");
    }

    private static boolean isCreativeOnly(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        return path.endsWith("_spawn_egg")
                || path.equals("command_block") || path.equals("chain_command_block") || path.equals("repeating_command_block")
                || path.equals("command_block_minecart") || path.equals("barrier") || path.equals("structure_block")
                || path.equals("structure_void") || path.equals("jigsaw") || path.equals("debug_stick")
                || path.equals("light") || path.equals("knowledge_book") || path.equals("spawner")
                || path.equals("trial_spawner") || path.equals("vault") || path.equals("bedrock")
                || path.equals("end_portal_frame") || path.equals("budding_amethyst")
                || path.equals("small_amethyst_bud") || path.equals("medium_amethyst_bud")
                || path.equals("large_amethyst_bud") || path.equals("frogspawn")
                || path.equals("suspicious_sand") || path.equals("suspicious_gravel")
                || path.equals("reinforced_deepslate") || path.equals("petrified_oak_slab")
                || path.equals("test_block") || path.equals("test_instance_block")
                || path.startsWith("infested_");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
