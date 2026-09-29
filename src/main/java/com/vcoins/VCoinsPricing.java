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
    private static final Map<String, Integer> baseTierPercents = new HashMap<>();
    private static volatile boolean initialized = false;

    public static synchronized void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }

    public static synchronized void init() {
        prices.clear();
        categories.clear();
        baseTierPercents.clear();
        // ==================== BASIC / EASILY FARMABLE BLOCKS (Priced to eliminate AFK exploit loops) ====================
        // Common material prices below are normalized together after catalogue creation.
        // Sand anchor: 507 buy -> 152 sell; 54 * 64 sand sells for ~525k coins.
        setPrice("minecraft:cobblestone", 650);
        setPrice("minecraft:dirt", 500);
        setPrice("minecraft:sand", 650);
        setPrice("minecraft:red_sand", 700);
        setPrice("minecraft:gravel", 650);
        setPrice("minecraft:netherrack", 400);
        setPrice("minecraft:stone", 700);
        setPrice("minecraft:smooth_stone", 950);
        setPrice("minecraft:cobbled_deepslate", 650);
        setPrice("minecraft:deepslate", 750);
        setPrice("minecraft:blackstone", 650);
        setPrice("minecraft:basalt", 650);
        setPrice("minecraft:smooth_basalt", 850);
        setPrice("minecraft:end_stone", 1_000);
        setPrice("minecraft:obsidian", 8_000);
        setPrice("minecraft:crying_obsidian", 20_000);
        setPrice("minecraft:clay_ball", 500);
        setPrice("minecraft:clay", 2_000);
        setPrice("minecraft:mud", 500);
        setPrice("minecraft:packed_mud", 1_200);
        setPrice("minecraft:mud_bricks", 1_500);
        setPrice("minecraft:pointed_dripstone", 900);
        setPrice("minecraft:dripstone_block", 1_200);
        setPrice("minecraft:snowball", 260);
        setPrice("minecraft:snow_block", 1_000);
        setPrice("minecraft:ice", 750);
        setPrice("minecraft:packed_ice", 6_750);
        setPrice("minecraft:blue_ice", 60_750);
        
        // ==================== WOOD & BAMBOO (Tree Farms / Bamboo Farms) ====================
        // Tree farms: log -> 4 planks -> 2 logs = 1 chest. prices reflect renewable supply
        setPrice("minecraft:oak_log", 1_300);
        setPrice("minecraft:spruce_log", 1_300);
        setPrice("minecraft:birch_log", 1_300);
        setPrice("minecraft:jungle_log", 1_300);
        setPrice("minecraft:acacia_log", 1_300);
        setPrice("minecraft:dark_oak_log", 1_300);
        setPrice("minecraft:mangrove_log", 1_500);
        setPrice("minecraft:cherry_log", 1_500);
        setPrice("minecraft:pale_oak_log", 1_300);
        setPrice("minecraft:oak_planks", 325);
        setPrice("minecraft:stick", 160);
        setPrice("minecraft:bamboo", 400);

        // ==================== MINERALS (Balanced against Iron Golem & Piglin Gold Farms) ====================
        // Minerals: coal mine sell target ~150 coins/each, iron ~400, gold ~1000
        setPrice("minecraft:coal", 500);
        setPrice("minecraft:charcoal", 380);
        setPrice("minecraft:coal_block", 4_500);
        setPrice("minecraft:raw_copper", 650);
        setPrice("minecraft:copper_ingot", 800);
        setPrice("minecraft:copper_nugget", 90);
        setPrice("minecraft:raw_copper_block", 5_850);
        setPrice("minecraft:copper_block", 7_200);
        setPrice("minecraft:waxed_copper_block", 8_000);

        // Iron and Gold: rewarding for mining and balanced for industrial farms
        setPrice("minecraft:raw_iron", 1_300);
        setPrice("minecraft:iron_ingot", 800);
        setPrice("minecraft:iron_nugget", 88);
        setPrice("minecraft:raw_iron_block", 11_700);
        setPrice("minecraft:iron_block", 7200);

        setPrice("minecraft:raw_gold", 3_300);
        setPrice("minecraft:gold_ingot", 1600);
        setPrice("minecraft:gold_nugget", 177);
        setPrice("minecraft:raw_gold_block", 29_700);
        setPrice("minecraft:gold_block", 14400);

        setPrice("minecraft:redstone", 420);
        setPrice("minecraft:redstone_block", 3_780);
        setPrice("minecraft:lapis_lazuli", 500);
        setPrice("minecraft:lapis_block", 4_500);
        setPrice("minecraft:quartz", 450);
        setPrice("minecraft:quartz_block", 1_800);
        setPrice("minecraft:amethyst_shard", 450);
        setPrice("minecraft:amethyst_block", 1_800);
        setPrice("minecraft:amethyst_cluster", 1_800);

        // Economy Currencies & Finite High-Value Minerals (Raid Farm / Villager Trading balanced)
        setPrice("minecraft:emerald", 40);
        setPrice("minecraft:emerald_block", 360);
        setPrice("minecraft:diamond", 10_000);
        setPrice("minecraft:diamond_block", 90_000);
        setPrice("minecraft:ancient_debris", 3_500_000);
        setPrice("minecraft:netherite_scrap", 3_500_000);
        setPrice("minecraft:netherite_ingot", 15_000_000);
        setPrice("minecraft:netherite_block", 135_000_000);
        
        // ==================== MOB & COMBAT REWARDS (AFK Farm Adjusted - Rewarding Grind) ====================
        // Mob drops: base prices reflect farm output
        setPrice("minecraft:rotten_flesh", 80);
        setPrice("minecraft:bone", 180);
        setPrice("minecraft:bone_meal", 60);
        setPrice("minecraft:string", 120);
        setPrice("minecraft:spider_eye", 160);
        setPrice("minecraft:feather", 80);
        setPrice("minecraft:leather", 1_000);
        setPrice("minecraft:gunpowder", 400);
        setPrice("minecraft:slime_ball", 400);
        setPrice("minecraft:slime_block", 3600);
        setPrice("minecraft:magma_cream", 500);
        setPrice("minecraft:magma_block", 2000);
        setPrice("minecraft:prismarine_shard", 240);
        setPrice("minecraft:prismarine_crystals", 400);
        setPrice("minecraft:ender_pearl", 600);
        setPrice("minecraft:blaze_rod", 1000);
        setPrice("minecraft:blaze_powder", 500);
        setPrice("minecraft:ghast_tear", 4000);
        setPrice("minecraft:nautilus_shell", 15000);
        setPrice("minecraft:shulker_shell", 80000);
        setPrice("minecraft:breeze_rod", 200_000);
        setPrice("minecraft:phantom_membrane", 1600);
        setPrice("minecraft:ink_sac", 240);
        setPrice("minecraft:glow_ink_sac", 320);
        setPrice("minecraft:rabbit_hide", 660);
        setPrice("minecraft:rabbit_foot", 3_600);
        setPrice("minecraft:turtle_scute", 9_000);
        setPrice("minecraft:armadillo_scute", 6_000);
        setPrice("minecraft:saddle", 65_000);
        setPrice("minecraft:sponge", 6_000);
        setPrice("minecraft:wet_sponge", 5_000);
        setPrice("minecraft:dragon_breath", 15_000);

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
        setPrice("minecraft:wither_skeleton_skull", 120000);
        setPrice("minecraft:nether_star", 1500000);
        setPrice("minecraft:heart_of_the_sea", 20_000_000);
        setPrice("minecraft:echo_shard", 2_000_000);
        setPrice("minecraft:heavy_core", 120_000_000);
        setPrice("minecraft:dragon_head", 50_000_000);
        setPrice("minecraft:dragon_egg", 500_000_000);
        
        // ==================== CROPS & AGRICULTURAL PRODUCTS (Rewarding Grinding) ====================
        // Crops: renewable crops use lower base prices
        setPrice("minecraft:wheat_seeds", 40);
        setPrice("minecraft:wheat", 280);
        setPrice("minecraft:hay_block", 7_650);
        setPrice("minecraft:potato", 220);
        setPrice("minecraft:baked_potato", 350);
        setPrice("minecraft:poisonous_potato", 320);
        setPrice("minecraft:carrot", 220);
        setPrice("minecraft:beetroot", 700);
        setPrice("minecraft:beetroot_seeds", 130);
        setPrice("minecraft:melon_slice", 80);
        setPrice("minecraft:melon", 720);
        setPrice("minecraft:pumpkin", 400);
        setPrice("minecraft:sugar_cane", 180);
        setPrice("minecraft:kelp", 100);
        setPrice("minecraft:dried_kelp", 150);
        setPrice("minecraft:dried_kelp_block", 1350);
        setPrice("minecraft:sweet_berries", 520);
        setPrice("minecraft:glow_berries", 650);
        setPrice("minecraft:cocoa_beans", 700);
        setPrice("minecraft:cactus", 180);
        setPrice("minecraft:apple", 1_600);
        setPrice("minecraft:beef", 1_400);
        setPrice("minecraft:porkchop", 1_400);
        setPrice("minecraft:mutton", 1_300);
        setPrice("minecraft:chicken", 1_160);
        setPrice("minecraft:bread", 2_900);
        setPrice("minecraft:golden_carrot", 10_000);
        setPrice("minecraft:glistering_melon_slice", 10_000);
        setPrice("minecraft:golden_apple", 80_000);
        setPrice("minecraft:enchanted_golden_apple", 80_000_000);
        setPrice("minecraft:pumpkin_pie", 4_500);
        setPrice("minecraft:cake", 28_000);
        setPrice("minecraft:chorus_fruit", 900);
        setPrice("minecraft:chorus_flower", 3_500);
        setPrice("minecraft:sea_pickle", 900);
        setPrice("minecraft:shroomlight", 1_600);
        
        // ==================== COMMONLY CRAFTED MATERIALS ====================
        setPrice("minecraft:glass", 700);
        setPrice("minecraft:white_wool", 1_250);
        setPrice("minecraft:torch", 700);
        setPrice("minecraft:bone_block", 5_400);
        setPrice("minecraft:glowstone_dust", 900);
        setPrice("minecraft:glowstone", 3_600);
        setPrice("minecraft:honeycomb", 320);
        setPrice("minecraft:honeycomb_block", 1280);
        setPrice("minecraft:honey_block", 2600);
        setPrice("minecraft:honey_bottle", 650);
        setPrice("minecraft:resin_clump", 1_560);
        setPrice("minecraft:resin_block", 14_000);
        setPrice("minecraft:disc_fragment_5", 10_000);

        // ==================== ENDGAME / RARE PROGRESSION ITEMS ====================
        setPrice("minecraft:enchanted_book", 50_000);
        setPrice("minecraft:elytra", 200_000_000);
        setPrice("minecraft:totem_of_undying", 80000);
        setPrice("minecraft:beacon", 1520000);
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
                baseTierPercents.put(id, rarityPricePercent(item.getDefaultInstance().getRarity()));

                if (!isTradeable(item)) {
                    prices.remove(id);
                } else if (!prices.containsKey(id)) {
                    prices.put(id, getFallbackPrice(id, category));
                }
            }
        }
        // Apply once per initialization to explicit prices and fallback variants alike.
        // Keep the existing common-block price baseline.
        prices.replaceAll((id, price) -> isCommonBuildingMaterial(id)
                ? Math.max(1L, percentageOf(price, 78)) : price);
        // Explicit support-item list keeps armor, weapons and ammunition unchanged.
        prices.replaceAll((id, price) -> isPvpSupportItem(id) ? safeMultiply(price, 2L) : price);
        initialized = true;
    }

    static boolean isPvpSupportItem(String id) {
        return switch (id) {
            case "minecraft:totem_of_undying", "minecraft:end_crystal",
                    "minecraft:golden_apple", "minecraft:enchanted_golden_apple",
                    "minecraft:ender_pearl", "minecraft:wind_charge",
                    "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion",
                    "minecraft:cobweb", "minecraft:obsidian", "minecraft:respawn_anchor",
                    "minecraft:glowstone", "minecraft:water_bucket", "minecraft:lava_bucket" -> true;
            default -> false;
        };
    }

    private static boolean isCommonBuildingMaterial(String id) {
        if (!id.startsWith("minecraft:")) return false;
        String path = id.substring("minecraft:".length());
        if (path.endsWith("_ore") || path.equals("glass_bottle")) return false;
        if (getWoodFamilyPrice(path) > 0L) return true;
        if (containsAny(path, "cobblestone", "deepslate", "blackstone", "basalt",
                "granite", "diorite", "andesite", "tuff", "sandstone", "end_stone",
                "stone_brick", "smooth_stone", "mud", "terracotta", "concrete",
                "glass", "_wool", "_carpet")) return true;
        return switch (path) {
            case "sand", "red_sand", "gravel", "dirt", "coarse_dirt", "rooted_dirt",
                    "grass_block", "podzol", "mycelium", "netherrack", "stone", "stone_slab",
                    "stone_stairs", "stone_button", "stone_pressure_plate", "calcite",
                    "clay", "clay_ball", "bricks", "brick", "brick_slab", "brick_stairs", "brick_wall",
                    "moss_block", "pale_moss_block", "moss_carpet", "pale_moss_carpet",
                    "snow", "snowball", "snow_block", "ice", "packed_ice", "blue_ice",
                    "dripstone_block", "pointed_dripstone", "soul_sand", "soul_soil",
                    "bamboo", "stick" -> true;
            default -> false;
        };
    }
    
    public static void calculateRecipes(MinecraftServer server) {
        // We disabled recipe parsing due to 1.21.11 API changes and use smart fallback pricing instead.
    }

    private static void setPrice(String id, long price) {
        prices.put(id, price);
    }

    public static long getBasePrice(String itemId) {
        ensureInitialized();
        String id = itemId.split("\\|", 2)[0];
        return percentageOf(prices.getOrDefault(id, 0L), baseTierPercents.getOrDefault(id, 100));
    }

    private static int rarityPricePercent(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 100;
            case UNCOMMON -> 125;
            case RARE -> 150;
            case EPIC -> 200;
        };
    }

    private static int cardPricePercent(ItemStack stack) {
        return switch (VBlackMarket.getCardTier(stack)) {
            case "uncommon" -> 125;
            case "rare" -> 150;
            case "epic" -> 200;
            case "exclusive" -> 300;
            default -> 100; // Legend/Mythic use their fixed relic prices.
        };
    }

    public static int getTierPricePercent(ItemStack stack) {
        // Use innate rarity: enchanting an item must not create a resale tier upgrade.
        return Math.max(rarityPricePercent(stack.getItem().getDefaultInstance().getRarity()),
                cardPricePercent(stack));
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
        ensureInitialized();
        long base = prices.getOrDefault(id, 0L);
        if (base <= 0) return 0L;
        if (!id.startsWith("minecraft:")) base = Math.max(base, getRarityFloor(stack.getItem().getDefaultInstance().getRarity()));
        base = safeAdd(base, getPotionPremium(stack, base));
        return percentageOf(safeAdd(base, getEnchantmentPremium(stack)), getTierPricePercent(stack));
    }

    /** Effect variants share the item's market wave so stronger/longer doses retain their premium. */
    private static long getPotionPremium(ItemStack stack, long base) {
        if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION)
                && !stack.is(Items.LINGERING_POTION) && !stack.is(Items.TIPPED_ARROW)) return 0L;
        var contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return 0L;
        long premium = 0L;
        for (var effect : contents.getAllEffects()) {
            String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).getPath();
            int weight = switch (id) {
                case "instant_health" -> 90;
                case "instant_damage" -> 110;
                case "regeneration" -> 100;
                case "strength" -> 95;
                case "resistance" -> 120;
                case "invisibility" -> 85;
                case "fire_resistance" -> 75;
                case "water_breathing" -> 65;
                case "night_vision" -> 55;
                case "speed" -> 60;
                case "slowness" -> 45;
                case "jump_boost" -> 40;
                case "poison" -> 70;
                case "weakness" -> 50;
                case "slow_falling" -> 80;
                case "wind_charged" -> 105;
                case "weaving" -> 115;
                case "oozing" -> 125;
                case "infested" -> 135;
                default -> 60;
            };
            // Bound custom effects and use long arithmetic; infinite duration gets the maximum.
            long level = Math.clamp((long) effect.getAmplifier() + 1L, 1L, 256L);
            long seconds = effect.getDuration() == -1 ? 3600L
                    : Math.clamp(effect.getDuration() / 20L, 0L, 3600L);
            long percent = weight * level * level;
            if (!effect.getEffect().value().isInstantaneous()) percent += seconds * weight / 180L;
            premium = safeAdd(premium, percentageOf(base, (int) percent));
        }
        return premium;
    }

    public static long getPrice(ItemStack stack) {
        long reference = getReferencePrice(stack);
        return reference <= 0 ? 0 : Math.max(1L, Math.round(reference * VMarketEngine.getMultiplier(getMarketKey(stack))));
    }

    public static long getReferenceSellPrice(ItemStack stack) {
        if (!isTradeable(stack)) return 0L;
        return calculateSellPrice(getSellMarketKey(stack), getDurabilityAdjustedPrice(stack, getReferencePrice(stack)));
    }

    /** Sales follow the same item and enchantment market as purchases. */
    public static String getSellMarketKey(ItemStack stack) {
        return getMarketKey(stack);
    }

    public static long getSellPrice(String itemId) {
        return calculateSellPrice(itemId, getPrice(itemId));
    }

    public static long getSellPrice(ItemStack stack) {
        if (!isTradeable(stack)) return 0L;
        return calculateSellPrice(getSellMarketKey(stack), getDurabilityAdjustedPrice(stack, getPrice(stack)));
    }

    private static long calculateSellPrice(String itemId, long buyPrice) {
        if (buyPrice <= 0 || isCreativeOnly(itemId.split("\\|", 2)[0])) return 0L;
        return percentageOf(buyPrice, 75);
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
        Map<String, Long> tieredPrices = new HashMap<>();
        prices.forEach((id, price) -> tieredPrices.put(id, getBasePrice(id)));
        return tieredPrices;
    }

    public static boolean isTradeable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (VGiftBox.isGiftBox(stack)) return false;
        // Legend and Mythic items (VNoTrade tag) cannot be traded, sold, or duplicated
        var customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().getBoolean("VNoTrade").orElse(false)) {
            if (!customData.copyTag().contains("VGiftRewardId")) {
                return false;
            }
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
            return 165_000L;
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
            case BUILDING, COLORED, NATURAL -> 1_300L;
            case FOOD -> 1_950L;
            case FUNCTIONAL -> 20_800L;
            case REDSTONE -> 15_600L;
            case INGREDIENTS -> 5_200L;
            case SPAWN_EGGS -> 50_000L;
            case MISC, ALL, BUYBACK, BLACK_MARKET -> 5_200L;
            case TOOLS, COMBAT -> 41_600L;
        };
    }

    private static long getEquipmentPrice(String path) {
        long exactPrice = switch (path) {
            case "bow" -> 6_500L;
            case "crossbow" -> 10_500L;
            case "arrow" -> 325L;
            case "spectral_arrow" -> 2_600L;
            case "tipped_arrow" -> 5_200L;
            case "shield" -> 6_500L;
            case "shears" -> 9_750L;
            case "flint_and_steel" -> 7_300L;
            case "fishing_rod" -> 2_400L;
            case "carrot_on_a_stick", "warped_fungus_on_a_stick" -> 4_000L;
            case "brush" -> 5_200L;
            case "spyglass" -> 13_000L;
            case "compass" -> 22_750L;
            case "clock" -> 42_250L;
            case "recovery_compass" -> 25_000_000L;
            case "bucket" -> 14_600L;
            case "turtle_helmet" -> 80_000L;
            case "wolf_armor" -> 60_000L;
            case "leather_horse_armor" -> 24_000L;
            case "iron_horse_armor" -> 150_000L;
            case "golden_horse_armor" -> 300_000L;
            case "diamond_horse_armor" -> 1_200_000L;
            case "netherite_horse_armor" -> 60_000_000L;
            case "copper_nautilus_armor" -> 40_000L;
            case "iron_nautilus_armor" -> 120_000L;
            case "golden_nautilus_armor" -> 240_000L;
            case "diamond_nautilus_armor" -> 1_200_000L;
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
                safeMultiply(160L, stickCount));
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
        if (path.startsWith("wooden_")) return 325L;
        if (path.startsWith("stone_")) return 700L;
        if (path.startsWith("copper_")) return 800L;
        if (path.startsWith("iron_") || path.startsWith("chainmail_")) return 1_700L;
        if (path.startsWith("golden_")) return 4_300L;
        if (path.startsWith("diamond_")) return 10_000L;
        if (path.startsWith("leather_")) return 1_000L;
        return 0L;
    }

    private static long getWoodFamilyPrice(String path) {
        if (!containsAny(path, "oak", "spruce", "birch", "jungle", "acacia",
                "dark_oak", "mangrove", "cherry", "pale_oak", "crimson", "warped", "bamboo")) {
            return 0L;
        }

        if (path.equals("bamboo_block") || path.equals("stripped_bamboo_block")) return 3_600L;
        if (path.equals("bamboo_planks")) return 1_800L;
        if (path.equals("bamboo_mosaic")) return 1_800L;
        if (path.equals("bamboo_mosaic_slab")) return 900L;
        if (path.equals("bamboo_mosaic_stairs")) return 2_700L;
        if (path.equals("bamboo_chest_raft")) return 11_600L;
        if (path.equals("bamboo_raft")) return 9_000L;
        if (path.equals("bamboo_hanging_sign")) return 28_000L;
        if (path.equals("bamboo_sign")) return 3_900L;
        if (path.equals("bamboo_fence_gate")) return 4_500L;
        if (path.equals("bamboo_fence")) return 2_600L;
        if (path.equals("bamboo_trapdoor")) return 5_400L;
        if (path.equals("bamboo_door")) return 3_600L;
        if (path.equals("bamboo_pressure_plate")) return 3_600L;
        if (path.equals("bamboo_button")) return 1_800L;
        if (path.equals("bamboo_shelf")) return 3_600L;
        if (path.equals("bamboo_stairs")) return 2_700L;
        if (path.equals("bamboo_slab")) return 900L;
        if (path.endsWith("_chest_boat") || path.endsWith("_chest_raft")) return 6_500L;
        if (path.endsWith("_boat") || path.endsWith("_raft")) return 3_900L;
        if (path.endsWith("_hanging_sign")) return 15_600L;
        if (path.endsWith("_sign")) return 1_950L;
        if (path.endsWith("_fence_gate")) return 3_250L;
        if (path.endsWith("_fence")) return 1_300L;
        if (path.endsWith("_trapdoor")) return 2_400L;
        if (path.endsWith("_door")) return 1_625L;
        if (path.endsWith("_pressure_plate")) return 1_625L;
        if (path.endsWith("_button")) return 800L;
        if (path.endsWith("_shelf")) return 3_250L;
        if (path.endsWith("_stairs")) return 975L;
        if (path.endsWith("_slab")) return 325L;
        if (path.endsWith("_planks")) return 325L;
        if (path.endsWith("_leaves")) return 650L;
        if (path.endsWith("_sapling") || path.endsWith("_propagule")) return 2_600L;
        if (path.endsWith("_wood") || path.endsWith("_hyphae")) return 3_500L;
        if (path.endsWith("_log") || path.endsWith("_stem")) return 2_600L;
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
        long waxSurcharge = 1_200L;
        if (normalized.equals("copper_block")) {
            basePrice = 7_200L;
        } else if (normalized.contains("cut_copper_slab")) {
            basePrice = 900L;
            waxSurcharge = 150L;
        } else if (normalized.contains("cut_copper")) {
            basePrice = 1_800L;
            waxSurcharge = 300L;
        } else if (normalized.contains("chiseled_copper")) {
            basePrice = 1_800L;
            waxSurcharge = 300L;
        } else if (normalized.contains("copper_grate")) {
            basePrice = 7_200L;
        } else if (normalized.contains("copper_bulb")) {
            basePrice = 12_500L;
        } else if (normalized.contains("copper_trapdoor")) {
            basePrice = 4_800L;
        } else if (normalized.contains("copper_door")) {
            basePrice = 2_400L;
        } else if (normalized.contains("copper_bars")) {
            basePrice = 600L;
        } else if (normalized.contains("copper_chain") || normalized.contains("copper_lantern")) {
            basePrice = 1_900L;
        } else if (normalized.contains("copper_torch")) {
            basePrice = 700L;
        } else if (normalized.contains("copper_chest")) {
            basePrice = 10_000L;
        } else if (normalized.contains("copper_golem_statue")) {
            basePrice = 10_000L;
        } else if (normalized.contains("lightning_rod")) {
            basePrice = 2_400L;
        } else {
            basePrice = 7_200L;
        }
        return waxed ? safeAdd(basePrice, waxSurcharge) : basePrice;
    }

    private static long getColoredItemPrice(String path) {
        if (path.endsWith("_stained_glass_pane")) return 650L;
        if (path.endsWith("_stained_glass")) return 1_600L;
        if (path.endsWith("_glazed_terracotta")) return 11_300L;
        if (path.endsWith("_terracotta")) return 9_400L;
        if (path.endsWith("_concrete_powder")) return 1_100L;
        if (path.endsWith("_concrete")) return 1_600L;
        if (path.endsWith("_carpet")) return 975L;
        if (path.endsWith("_wool")) return 1_300L;
        if (path.endsWith("_bed")) return 11_700L;
        if (path.endsWith("_banner")) return 19_500L;
        if (path.endsWith("_candle")) return 9_750L;
        if (path.endsWith("_dye")) return 650L;
        if (path.endsWith("_bundle")) return 15_600L;
        if (path.endsWith("_harness")) return 34_000L;
        return 0L;
    }

    private static long getNaturalItemPrice(String path) {
        long exactPrice = switch (path) {
            case "coal_ore" -> 11_700L;
            case "deepslate_coal_ore" -> 13_000L;
            case "copper_ore" -> 23_400L;
            case "deepslate_copper_ore" -> 26_000L;
            case "iron_ore" -> 31_200L;
            case "deepslate_iron_ore" -> 33_800L;
            case "gold_ore", "nether_gold_ore" -> 83_200L;
            case "deepslate_gold_ore" -> 88_400L;
            case "redstone_ore" -> 26_000L;
            case "deepslate_redstone_ore" -> 28_600L;
            case "lapis_ore" -> 62_400L;
            case "deepslate_lapis_ore" -> 67_600L;
            case "diamond_ore" -> 90_000L;
            case "deepslate_diamond_ore" -> 95_000L;
            case "emerald_ore" -> 300_000L;
            case "deepslate_emerald_ore" -> 350_000L;
            case "nether_quartz_ore" -> 18_200L;
            case "granite", "diorite", "andesite", "tuff", "calcite",
                    "dripstone_block", "pointed_dripstone", "basalt",
                    "smooth_basalt", "magma_block" -> 1_300L;
            case "grass_block", "podzol", "mycelium", "rooted_dirt", "mud",
                    "coarse_dirt", "moss_block", "pale_moss_block" -> 975L;
            case "soul_sand", "soul_soil" -> 1_300L;
            case "amethyst_cluster" -> 10_400L;
            case "large_amethyst_bud" -> 7_800L;
            case "medium_amethyst_bud" -> 5_200L;
            case "small_amethyst_bud" -> 2_600L;
            case "sponge", "wet_sponge" -> 6_000L;
            case "sculk" -> 1_950L;
            case "sculk_sensor" -> 15_600L;
            case "calibrated_sculk_sensor" -> 29_250L;
            case "sculk_catalyst" -> 195_000L;
            case "sculk_shrieker" -> 406_000L;
            case "bee_nest" -> 83_200L;
            case "beehive" -> 26_000L;
            case "turtle_egg" -> 83_200L;
            case "frogspawn" -> 83_200L;
            case "ochre_froglight", "pearlescent_froglight", "verdant_froglight" -> 7_300L;
            case "chorus_flower" -> 5_200L;
            case "chorus_plant", "chorus_fruit" -> 1_300L;
            case "spore_blossom", "wither_rose" -> 41_600L;
            default -> 0L;
        };
        if (exactPrice > 0L) {
            return exactPrice;
        }
        if (path.contains("coral")) return 10_400L;
        if (containsAny(path, "flower", "tulip", "daisy", "orchid", "bluet",
                "allium", "dandelion", "poppy", "lilac", "peony", "sunflower")) {
            return 650L;
        }
        return 0L;
    }

    private static long getBuildingItemPrice(String path) {
        long exactPrice = switch (path) {
            case "bricks" -> 10_400L;
            case "packed_mud", "mud_bricks" -> 3_900L;
            case "nether_bricks" -> 5_200L;
            case "red_nether_bricks" -> 5_850L;
            case "iron_bars" -> 2_400L;
            case "chain" -> 6_800L;
            case "end_rod" -> 10_500L;
            case "sea_lantern" -> 35_750L;
            case "prismarine" -> 25_000L;
            case "prismarine_bricks" -> 56_000L;
            case "dark_prismarine" -> 52_000L;
            case "purpur_block", "purpur_pillar" -> 7_800L;
            case "end_stone_bricks" -> 3_250L;
            case "terracotta" -> 9_100L;
            case "glass_pane" -> 650L;
            case "tinted_glass" -> 8_900L;
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
            return Math.max(325L, (basePrice + 1L) / 2L);
        }
        if (containsAny(path, "polished_", "smooth_", "_bricks", "_brick_")) {
            return safeAdd(basePrice, Math.max(325L, basePrice / 4L));
        }
        return basePrice;
    }

    private static long getStoneFamilyBasePrice(String path) {
        if (path.contains("red_sandstone")) return 5_200L;
        if (path.contains("sandstone")) return 2_600L;
        if (path.contains("quartz")) return 62_400L;
        if (path.contains("dark_prismarine")) return 52_000L;
        if (path.contains("prismarine_brick")) return 56_000L;
        if (path.contains("prismarine")) return 25_000L;
        if (path.contains("red_nether_brick")) return 5_850L;
        if (path.contains("nether_brick")) return 5_200L;
        if (path.contains("mud_brick")) return 3_900L;
        if (path.startsWith("brick_")) return 10_400L;
        if (path.contains("resin_brick")) return 10_400L;
        if (path.contains("end_stone")) return 2_600L;
        if (path.contains("blackstone")) return 1_950L;
        if (path.contains("deepslate")) return 1_300L;
        if (containsAny(path, "granite", "diorite", "andesite", "tuff")) return 1_300L;
        if (path.equals("stone") || path.startsWith("stone_")
                || path.startsWith("smooth_stone") || path.contains("stone_brick")) {
            return 975L;
        }
        if (path.contains("cobblestone")) return 650L;
        return 0L;
    }

    private static long getFunctionalItemPrice(String path) {
        // Anchor: chest = 2600 so 64 chest sell (30% margin) = 50,000 coins
        return switch (path) {
            case "crafting_table" -> 1_300L;
            case "chest" -> 2_600L;
            case "trapped_chest" -> 5_850L;
            case "barrel" -> 2_600L;
            case "furnace" -> 2_400L;
            case "smoker" -> 9_750L;
            case "blast_furnace" -> 35_750L;
            case "stonecutter" -> 8_900L;
            case "grindstone" -> 4_900L;
            case "smithing_table" -> 13_800L;
            case "fletching_table", "loom" -> 3_250L;
            case "cartography_table" -> 4_050L;
            case "anvil" -> 178_000L;
            case "chipped_anvil" -> 122_000L;
            case "damaged_anvil" -> 65_000L;
            case "enchanting_table" -> 3_570_000L;
            case "brewing_stand" -> 35_750L;
            case "ender_chest" -> 211_000L;
            case "cauldron" -> 40_600L;
            case "composter" -> 2_900L;
            case "jukebox" -> 1_755_000L;
            case "note_block" -> 5_700L;
            case "respawn_anchor" -> 682_000L;
            case "lodestone" -> 31_200_000L;
            case "bell" -> 1_300_000L;
            case "decorated_pot" -> 1_380_000L;
            case "bookshelf" -> 21_900L;
            case "chiseled_bookshelf" -> 3_250L;
            case "lectern" -> 24_400L;
            case "ladder" -> 650L;
            case "scaffolding" -> 490L;
            case "armor_stand" -> 2_400L;
            case "flower_pot" -> 8_100L;
            case "item_frame" -> 4_900L;
            case "glow_item_frame" -> 12_200L;
            case "iron_door" -> 12_200L;
            case "iron_trapdoor" -> 23_600L;
            default -> 0L;
        };
    }

    private static long getRedstoneItemPrice(String path) {
        return switch (path) {
            case "redstone_torch" -> 2_300L;
            case "lever" -> 1_300L;
            case "stone_button" -> 1_300L;
            case "repeater" -> 8_900L;
            case "comparator" -> 12_200L;
            case "piston" -> 10_600L;
            case "sticky_piston" -> 15_400L;
            case "observer" -> 9_750L;
            case "dispenser" -> 17_100L;
            case "dropper" -> 8_900L;
            case "hopper" -> 32_500L;
            case "crafter" -> 39_000L;
            case "daylight_detector" -> 12_200L;
            case "target" -> 9_750L;
            case "redstone_lamp" -> 12_200L;
            case "tripwire_hook" -> 2_600L;
            case "tnt" -> 16_300L;
            case "rail" -> 2_400L;
            case "powered_rail" -> 12_200L;
            case "detector_rail" -> 6_800L;
            case "activator_rail" -> 6_800L;
            case "minecart" -> 29_300L;
            case "chest_minecart" -> 31_700L;
            case "hopper_minecart" -> 61_750L;
            case "furnace_minecart" -> 31_700L;
            case "tnt_minecart" -> 45_500L;
            default -> 0L;
        };
    }

    private static long getIngredientPrice(String path) {
        long exactPrice = switch (path) {
            case "flint" -> 1_950L;
            case "paper" -> 650L;
            case "book" -> 5_700L;
            case "brick" -> 2_600L;
            case "nether_brick" -> 1_300L;
            case "ink_sac" -> 2_400L;
            case "glow_ink_sac" -> 3_250L;
            case "rabbit_hide" -> 1_300L;
            case "rabbit_foot" -> 9_750L;
            case "phantom_membrane" -> 19_500L;
            case "armadillo_scute" -> 19_500L;
            case "turtle_scute", "scute" -> 29_250L;
            case "fermented_spider_eye" -> 3_250L;
            case "fire_charge" -> 7_300L;
            case "ender_eye" -> 24_400L;
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
        if (path.endsWith("_dye")) return 650L;
        if (path.contains("pottery_sherd") || path.contains("pottery_shard")) return 200_000L;
        if (path.equals("flower_banner_pattern")) return 3_250L;
        if (path.equals("field_masoned_banner_pattern")) return 13_650L;
        if (path.equals("bordure_indented_banner_pattern")) return 4_200L;
        if (path.equals("creeper_banner_pattern") || path.equals("skull_banner_pattern")) return 90_000L;
        if (path.equals("mojang_banner_pattern")) return 1_200_000L;
        if (path.endsWith("_banner_pattern")) return 25_000L;
        return 0L;
    }

    private static long getFoodPrice(String path) {
        return switch (path) {
            case "beetroot", "sweet_berries", "glow_berries", "kelp" -> 650L;
            case "pumpkin" -> 1_300L;
            case "cocoa_beans", "sugar_cane", "nether_wart" -> 650L;
            case "beetroot_seeds", "melon_seeds", "pumpkin_seeds" -> 325L;
            case "egg" -> 490L;
            case "sugar" -> 325L;
            case "bread" -> 2_925L;
            case "baked_potato" -> 975L;
            case "cooked_beef", "cooked_porkchop", "cooked_mutton" -> 1_950L;
            case "cooked_chicken", "cooked_rabbit" -> 1_625L;
            case "beef", "porkchop", "mutton" -> 1_300L;
            case "chicken", "rabbit" -> 975L;
            case "cod", "salmon" -> 1_625L;
            case "cooked_cod", "cooked_salmon" -> 2_925L;
            case "pufferfish", "tropical_fish" -> 5_850L;
            case "cookie" -> 490L;
            case "pumpkin_pie" -> 4_875L;
            case "cake" -> 56_875L;
            case "mushroom_stew", "beetroot_soup", "rabbit_stew", "suspicious_stew" -> 3_900L;
            case "golden_carrot", "glistering_melon_slice" -> 10_000L;
            case "honey_bottle" -> 2_600L;
            case "potion" -> 5_200L;
            case "splash_potion" -> 13_000L;
            case "lingering_potion" -> 29_250L;
            default -> 0L;
        };
    }

    private static long getMiscItemPrice(String path) {
        return switch (path) {
            case "bowl" -> 490L;
            case "glass_bottle" -> 810L;
            case "water_bucket" -> 15_450L;
            case "lava_bucket" -> 19_500L;
            case "milk_bucket", "powder_snow_bucket" -> 17_875L;
            case "cod_bucket", "salmon_bucket", "tropical_fish_bucket",
                    "pufferfish_bucket" -> 21_125L;
            case "axolotl_bucket", "tadpole_bucket" -> 32_500L;
            case "lead" -> 2_925L;
            case "name_tag" -> 8_000L;
            case "saddle" -> 65_000L;
            case "experience_bottle" -> 8_000L;
            case "firework_rocket" -> 3_250L;
            case "firework_star" -> 9_750L;
            case "map", "empty_map" -> 19_500L;
            case "painting" -> 3_250L;
            case "writable_book" -> 6_500L;
            case "written_book" -> 9_750L;
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
            // Applying a renewable enchantment to equipment must not inflate its value.
            unitPrice = percentageOf(unitPrice, 20);
            long enchantmentPrice = safeMultiply(unitPrice, (long) level * level);

            // A max-level enchantment is more desirable than an unfinished one.
            if (level >= value.enchantment().value().getMaxLevel()) {
                enchantmentPrice = safeAdd(enchantmentPrice, percentageOf(enchantmentPrice, 25));
            }
            premium = safeAdd(premium, enchantmentPrice);
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
