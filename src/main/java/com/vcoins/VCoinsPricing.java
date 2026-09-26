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
        // ==================== CƠ BẢN / KHỐI DỄ FARM ====================
        setPrice("minecraft:cobblestone", 1);
        setPrice("minecraft:dirt", 1);
        setPrice("minecraft:sand", 2);
        setPrice("minecraft:red_sand", 4);
        setPrice("minecraft:gravel", 2);
        setPrice("minecraft:netherrack", 1);
        setPrice("minecraft:stone", 2);
        setPrice("minecraft:cobbled_deepslate", 2);
        setPrice("minecraft:deepslate", 2);
        setPrice("minecraft:blackstone", 4);
        setPrice("minecraft:end_stone", 10);
        setPrice("minecraft:obsidian", 150);
        setPrice("minecraft:crying_obsidian", 1_500);
        setPrice("minecraft:clay_ball", 4);
        setPrice("minecraft:clay", 16);
        setPrice("minecraft:snowball", 1);
        setPrice("minecraft:snow_block", 4);
        setPrice("minecraft:ice", 16);
        setPrice("minecraft:packed_ice", 144);
        setPrice("minecraft:blue_ice", 1_296);
        
        // ==================== GỖ & TRE (Tree Farm / Bamboo Farm) ====================
        setPrice("minecraft:oak_log", 4);
        setPrice("minecraft:spruce_log", 4);
        setPrice("minecraft:birch_log", 4);
        setPrice("minecraft:jungle_log", 4);
        setPrice("minecraft:acacia_log", 4);
        setPrice("minecraft:dark_oak_log", 4);
        setPrice("minecraft:mangrove_log", 4);
        setPrice("minecraft:cherry_log", 4);
        setPrice("minecraft:pale_oak_log", 4);
        setPrice("minecraft:oak_planks", 1);
        setPrice("minecraft:stick", 1);
        setPrice("minecraft:bamboo", 1);

        // ==================== KHOÁNG SẢN (Iron Golem Farm & Gold Piglin Farm cân bằng lại) ====================
        setPrice("minecraft:coal", 24);
        setPrice("minecraft:charcoal", 8);
        setPrice("minecraft:coal_block", 216);
        setPrice("minecraft:raw_copper", 8);
        setPrice("minecraft:copper_ingot", 12);
        setPrice("minecraft:copper_nugget", 1);
        setPrice("minecraft:raw_copper_block", 72);
        setPrice("minecraft:copper_block", 108);
        setPrice("minecraft:waxed_copper_block", 120);

        // Sắt và Vàng: dễ AFK quy mô công nghiệp -> Giảm giá mua xuống mức hợp lý
        setPrice("minecraft:raw_iron", 24);
        setPrice("minecraft:iron_ingot", 32); // AFK 1 rương sắt không còn phá nát nền kinh tế
        setPrice("minecraft:iron_nugget", 3);
        setPrice("minecraft:raw_iron_block", 216);
        setPrice("minecraft:iron_block", 288);

        setPrice("minecraft:raw_gold", 48);
        setPrice("minecraft:gold_ingot", 64); // Piglin farm
        setPrice("minecraft:gold_nugget", 7);
        setPrice("minecraft:raw_gold_block", 432);
        setPrice("minecraft:gold_block", 576);

        setPrice("minecraft:redstone", 12);
        setPrice("minecraft:redstone_block", 108);
        setPrice("minecraft:lapis_lazuli", 24);
        setPrice("minecraft:lapis_block", 216);
        setPrice("minecraft:quartz", 32);
        setPrice("minecraft:quartz_block", 128);
        setPrice("minecraft:amethyst_shard", 48);
        setPrice("minecraft:amethyst_block", 192);

        // Tài nguyên giá trị cao & không thể AFK vô hạn
        setPrice("minecraft:emerald", 128);
        setPrice("minecraft:emerald_block", 1_152);
        setPrice("minecraft:diamond", 25_000);
        setPrice("minecraft:diamond_block", 225_000);
        setPrice("minecraft:ancient_debris", 60_000);
        setPrice("minecraft:netherite_scrap", 60_000);
        setPrice("minecraft:netherite_ingot", 300_000);
        setPrice("minecraft:netherite_block", 2_700_000);
        
        // ==================== MOB VÀ PHẦN THƯỞNG CHIẾN ĐẤU ====================
        // Mob thường từ Spawner AFK: Giảm mạnh
        setPrice("minecraft:rotten_flesh", 2);
        setPrice("minecraft:bone", 4);
        setPrice("minecraft:bone_meal", 1);
        setPrice("minecraft:string", 4);
        setPrice("minecraft:spider_eye", 6);
        setPrice("minecraft:feather", 2);
        setPrice("minecraft:leather", 16);
        setPrice("minecraft:gunpowder", 12);
        setPrice("minecraft:slime_ball", 20);
        setPrice("minecraft:slime_block", 180);
        setPrice("minecraft:magma_cream", 24);
        setPrice("minecraft:prismarine_shard", 16);
        setPrice("minecraft:prismarine_crystals", 32);
        setPrice("minecraft:ender_pearl", 64);
        setPrice("minecraft:blaze_rod", 256);
        setPrice("minecraft:blaze_powder", 128);
        setPrice("minecraft:ghast_tear", 4_096);
        setPrice("minecraft:nautilus_shell", 8_000);
        setPrice("minecraft:shulker_shell", 25_000);
        setPrice("minecraft:breeze_rod", 12_000);

        // Boss & Mini-boss Drops: TĂNG MẠNH
        setPrice("minecraft:wither_skeleton_skull", 250_000);
        setPrice("minecraft:nether_star", 2_500_000);
        setPrice("minecraft:heart_of_the_sea", 1_200_000);
        setPrice("minecraft:echo_shard", 100_000);
        setPrice("minecraft:heavy_core", 5_000_000);
        setPrice("minecraft:dragon_head", 3_000_000);
        setPrice("minecraft:dragon_egg", 50_000_000);
        
        // ==================== CÂY TRỒNG VÀ NÔNG SẢN (Farm tự động) ====================
        setPrice("minecraft:wheat_seeds", 1);
        setPrice("minecraft:wheat", 2);
        setPrice("minecraft:hay_block", 18);
        setPrice("minecraft:potato", 2);
        setPrice("minecraft:carrot", 2);
        setPrice("minecraft:beetroot", 2);
        setPrice("minecraft:beetroot_seeds", 1);
        setPrice("minecraft:melon_slice", 1);
        setPrice("minecraft:melon", 9);
        setPrice("minecraft:pumpkin", 6);
        setPrice("minecraft:sugar_cane", 2);
        setPrice("minecraft:kelp", 1);
        setPrice("minecraft:dried_kelp", 1);
        setPrice("minecraft:dried_kelp_block", 9);
        setPrice("minecraft:sweet_berries", 2);
        setPrice("minecraft:glow_berries", 4);
        setPrice("minecraft:cocoa_beans", 3);
        setPrice("minecraft:cactus", 2);
        setPrice("minecraft:apple", 12);
        setPrice("minecraft:beef", 8);
        setPrice("minecraft:porkchop", 8);
        setPrice("minecraft:mutton", 8);
        setPrice("minecraft:chicken", 8);
        setPrice("minecraft:golden_apple", 20_000);
        setPrice("minecraft:enchanted_golden_apple", 3_500_000);
        
        // ==================== VẬT LIỆU CHẾ TẠO PHỔ BIẾN ====================
        setPrice("minecraft:glass", 4);
        setPrice("minecraft:white_wool", 4);
        setPrice("minecraft:torch", 4);
        setPrice("minecraft:bone_block", 36);
        setPrice("minecraft:glowstone_dust", 12);
        setPrice("minecraft:glowstone", 48);
        setPrice("minecraft:honeycomb", 8);
        setPrice("minecraft:honeycomb_block", 32);
        setPrice("minecraft:honey_block", 48);
        setPrice("minecraft:resin_clump", 16);
        setPrice("minecraft:resin_block", 144);
        setPrice("minecraft:disc_fragment_5", 15_000);

        // ==================== VẬT PHẨM ENDGAME / HIẾM (Tăng mạnh tạo mục tiêu cày cuốc) ====================
        setPrice("minecraft:enchanted_book", 5_000);
        setPrice("minecraft:elytra", 8_000_000);
        setPrice("minecraft:totem_of_undying", 1_500_000);
        setPrice("minecraft:beacon", 3_500_000);
        setPrice("minecraft:conduit", 1_800_000);
        setPrice("minecraft:mace", 6_000_000);
        setPrice("minecraft:trident", 1_200_000);
        setPrice("minecraft:sniffer_egg", 800_000);
        
        // Every registered item (including items added by other mods) belongs to the
        // catalogue. Explicit prices above win; everything else receives a sensible
        // fallback price so the "All items" tab is actually complete.
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();

            if (item == Items.AIR) {
                continue;
            }

            ShopCategory category = getCategoryForItem(item);
            categories.put(id, category);

            if (!isTradeable(item)) {
                prices.remove(id);
                continue;
            }

            if (!prices.containsKey(id)) {
                prices.put(id, getFallbackPrice(id, category));
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

    public static long getPrice(String itemId) {
        ensureInitialized();
        return prices.getOrDefault(itemId, 0L);
    }

    /**
     * Returns the exact price of a stack, including its normal and stored
     * enchantments. The count is intentionally ignored; callers multiply by it.
     */
    public static long getPrice(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        long basePrice = getPrice(itemId);
        // Vanilla rarity colors are not a measure of acquisition effort (a
        // craftable golden apple is RARE, for example). Our vanilla catalogue is
        // priced explicitly; the floor remains useful only for unknown mod items.
        if (!itemId.startsWith("minecraft:")) {
            basePrice = Math.max(basePrice,
                    getRarityFloor(stack.getItem().getDefaultInstance().getRarity()));
        }
        if (basePrice <= 0) {
            return 0L;
        }
        return safeAdd(basePrice, getEnchantmentPremium(stack));
    }

    public static long getSellPrice(String itemId) {
        long buyPrice = getPrice(itemId);
        return calculateSellPrice(itemId, buyPrice);
    }

    public static long getSellPrice(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return calculateSellPrice(itemId, getDurabilityAdjustedPrice(stack, getPrice(stack)));
    }

    private static long calculateSellPrice(String itemId, long buyPrice) {
        if (buyPrice <= 0 || isCreativeOnly(itemId)) {
            return 0L;
        }

        String path = itemId.substring(itemId.indexOf(':') + 1);

        // Chống sao chép Smithing Template kiếm tiền vô hạn
        if (path.contains("smithing_template")) {
            return Math.min(buyPrice / 4L, 25_000L);
        }

        // Tỷ lệ thu mua phân tầng (Tiered Sell Margins):
        // 1. Các tài nguyên siêu dễ AFK farm (đá cuội, cát, đất, cây trồng, mob drop thông thường, sắt, vàng):
        // Chỉ thu mua ở mức 20% - 25% giá mua để triệt tiêu việc cắm máy AFK làm giàu.
        if (isEasilyFarmable(path)) {
            return Math.max(1L, buyPrice / 5L); // 20% giá mua
        }

        // 2. Các vật phẩm nông sản, thức ăn, gỗ, khối xây dựng cơ bản:
        ShopCategory category = getCategory(itemId);
        if (category == ShopCategory.FOOD || category == ShopCategory.NATURAL || category == ShopCategory.BUILDING) {
            return Math.max(1L, buyPrice / 4L); // 25% giá mua
        }

        // 3. Các vật phẩm khác (Tools, Combat, Functional, Rare loot): 35% giá mua
        return Math.max(1L, buyPrice * 35L / 100L);
    }

    private static boolean isEasilyFarmable(String path) {
        return containsAny(path,
                // Cobble / Stone / Dirt gen
                "cobblestone", "cobbled_deepslate", "dirt", "gravel", "sand", "netherrack", "basalt",
                // Golem iron farm & Gold piglin farm
                "iron_ingot", "iron_nugget", "raw_iron", "gold_ingot", "gold_nugget", "raw_gold", "copper_ingot", "raw_copper",
                // Mob farms (Zombies, Skeletons, Spiders, Creepers, Endermen, Witches, Slimes)
                "rotten_flesh", "bone", "bone_meal", "string", "spider_eye", "gunpowder", "feather", "arrow",
                "slime_ball", "magma_cream", "glowstone_dust", "redstone", "sugar", "glass_bottle", "ender_pearl",
                // Crop / Plant / Tree automated farms
                "sugar_cane", "bamboo", "cactus", "kelp", "dried_kelp", "melon", "melon_slice", "pumpkin",
                "wheat", "wheat_seeds", "carrot", "potato", "beetroot", "beetroot_seeds", "sweet_berries", "glow_berries",
                "cocoa_beans", "nether_wart", "chorus_fruit", "egg", "honey_bottle", "honeycomb",
                // Wood tree farms & Wool sheep farms
                "log", "stem", "wood", "hyphae", "planks", "stick", "wool", "carpet");
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

    private static long calculateBuybackPrice(long sellPrice) {
        if (sellPrice <= 0) {
            return 0L;
        }
        return sellPrice + Math.max(1L, sellPrice / 10L);
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
            return 8_000_000L;
        }
        if (path.equals("totem_of_undying")) {
            return 1_500_000L;
        }
        if (path.equals("beacon")) {
            return 3_500_000L;
        }
        if (path.equals("mace")) {
            return 6_000_000L;
        }
        if (path.equals("trident")) {
            return 1_200_000L;
        }
        if (path.equals("dragon_head")) {
            return 3_000_000L;
        }
        if (path.endsWith("_head") || path.endsWith("_skull")) {
            return path.equals("wither_skeleton_skull") ? 250_000L : 75_000L;
        }
        if (path.startsWith("music_disc_")) {
            return getMusicDiscPrice(path);
        }
        if (path.equals("ominous_trial_key")) {
            return 250_000L;
        }
        if (path.equals("trial_key")) {
            return 60_000L;
        }
        if (path.equals("enchanted_book")) {
            return 5_000L;
        }
        if (path.contains("shulker_box")) {
            return 75_000L;
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
            case MISC, ALL, BUYBACK -> 32L;
            case TOOLS, COMBAT -> 256L;
        };
    }

    private static long getEquipmentPrice(String path) {
        long exactPrice = switch (path) {
            case "bow" -> 40L;
            case "crossbow" -> 160L;
            case "arrow" -> 2L;
            case "spectral_arrow" -> 16L;
            case "tipped_arrow" -> 32L;
            case "shield" -> 64L;
            case "shears" -> 70L;
            case "flint_and_steel" -> 50L;
            case "fishing_rod" -> 24L;
            case "carrot_on_a_stick", "warped_fungus_on_a_stick" -> 30L;
            case "brush" -> 32L;
            case "spyglass" -> 80L;
            case "compass" -> 160L;
            case "clock" -> 320L;
            case "recovery_compass" -> 800_000L;
            case "bucket" -> 100L;
            case "turtle_helmet" -> 8_000L;
            case "wolf_armor" -> 6_000L;
            case "leather_horse_armor" -> 150L;
            case "iron_horse_armor" -> 15_000L;
            case "golden_horse_armor" -> 30_000L;
            case "diamond_horse_armor" -> 250_000L;
            case "netherite_horse_armor" -> 1_500_000L;
            case "copper_nautilus_armor" -> 4_000L;
            case "iron_nautilus_armor" -> 15_000L;
            case "golden_nautilus_armor" -> 30_000L;
            case "diamond_nautilus_armor" -> 250_000L;
            case "netherite_nautilus_armor" -> 1_500_000L;
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
            // Netherite ingot (300k) + Netherite upgrade template reproduction cost (7 diamonds ~175k + catalyst)
            long reproducibleTemplateCost = 175_000L;
            return addCraftingEffort(safeAdd(diamondItem,
                    safeAdd(300_000L, reproducibleTemplateCost)), 10);
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
        if (path.startsWith("wooden_")) return 1L;
        if (path.startsWith("stone_")) return 2L;
        if (path.startsWith("copper_")) return 12L;
        if (path.startsWith("iron_") || path.startsWith("chainmail_")) return 32L;
        if (path.startsWith("golden_")) return 64L;
        if (path.startsWith("diamond_")) return 25_000L;
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
        if (path.contains("shulker_box")) return 30_000L;
        if (path.endsWith("_stained_glass_pane")) return 4L;
        if (path.endsWith("_stained_glass")) return 10L;
        if (path.endsWith("_glazed_terracotta")) return 70L;
        if (path.endsWith("_terracotta")) return 58L;
        if (path.endsWith("_concrete_powder")) return 7L;
        if (path.endsWith("_concrete")) return 10L;
        if (path.endsWith("_carpet")) return 12L;
        if (path.endsWith("_wool")) return 16L;
        if (path.endsWith("_bed")) return 72L;
        if (path.endsWith("_banner")) return 120L;
        if (path.endsWith("_candle")) return 60L;
        if (path.endsWith("_dye")) return 8L;
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
            case "diamond_ore" -> 16_000L;
            case "deepslate_diamond_ore" -> 17_000L;
            case "emerald_ore" -> 4_000L;
            case "deepslate_emerald_ore" -> 4_500L;
            case "nether_quartz_ore" -> 112L;
            case "granite", "diorite", "andesite", "tuff", "calcite",
                    "dripstone_block", "pointed_dripstone", "basalt",
                    "smooth_basalt", "magma_block" -> 12L;
            case "grass_block", "podzol", "mycelium", "rooted_dirt", "mud",
                    "coarse_dirt", "moss_block", "pale_moss_block" -> 8L;
            case "soul_sand", "soul_soil" -> 12L;
            case "amethyst_cluster" -> 256L;
            case "large_amethyst_bud" -> 192L;
            case "medium_amethyst_bud" -> 128L;
            case "small_amethyst_bud" -> 64L;
            case "sponge", "wet_sponge" -> 20_000L;
            case "sculk" -> 32L;
            case "sculk_sensor" -> 512L;
            case "calibrated_sculk_sensor" -> 896L;
            case "sculk_catalyst" -> 4_000L;
            case "sculk_shrieker" -> 8_000L;
            case "bee_nest" -> 512L;
            case "beehive" -> 160L;
            case "turtle_egg" -> 512L;
            case "frogspawn" -> 512L;
            case "ochre_froglight", "pearlescent_froglight", "verdant_froglight" -> 512L;
            case "chorus_flower" -> 96L;
            case "chorus_plant", "chorus_fruit" -> 32L;
            case "spore_blossom", "wither_rose" -> 256L;
            default -> 0L;
        };
        if (exactPrice > 0L) {
            return exactPrice;
        }
        if (path.contains("coral")) return 64L;
        if (containsAny(path, "flower", "tulip", "daisy", "orchid", "bluet",
                "allium", "dandelion", "poppy", "lilac", "peony", "sunflower")) {
            return 12L;
        }
        return 0L;
    }

    private static long getBuildingItemPrice(String path) {
        long exactPrice = switch (path) {
            case "bricks" -> 64L;
            case "packed_mud", "mud_bricks" -> 24L;
            case "nether_bricks" -> 32L;
            case "red_nether_bricks" -> 36L;
            case "iron_bars" -> 116L;
            case "chain" -> 377L;
            case "end_rod" -> 640L;
            case "sea_lantern" -> 540L;
            case "prismarine" -> 154L;
            case "prismarine_bricks" -> 346L;
            case "dark_prismarine" -> 320L;
            case "purpur_block", "purpur_pillar" -> 48L;
            case "end_stone_bricks" -> 20L;
            case "terracotta" -> 56L;
            case "glass_pane" -> 4L;
            case "tinted_glass" -> 416L;
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
            case "crafting_table" -> 20L;
            case "chest" -> 40L;
            case "trapped_chest" -> 480L;
            case "barrel" -> 32L;
            case "furnace" -> 40L;
            case "smoker" -> 125L;
            case "blast_furnace" -> 1_600L;
            case "stonecutter" -> 330L;
            case "grindstone" -> 22L;
            case "smithing_table" -> 635L;
            case "fletching_table", "loom" -> 48L;
            case "cartography_table" -> 31L;
            case "anvil" -> 9_600L;
            case "chipped_anvil" -> 6_400L;
            case "damaged_anvil" -> 3_200L;
            case "enchanting_table" -> 39_000L;
            case "brewing_stand" -> 2_500L;
            case "ender_chest" -> 4_250L;
            case "cauldron" -> 1_800L;
            case "composter" -> 18L;
            case "jukebox" -> 19_250L;
            case "note_block" -> 80L;
            case "respawn_anchor" -> 5_400L;
            case "lodestone" -> 192_000L;
            case "bell" -> 8_000L;
            case "decorated_pot" -> 8_500L;
            case "bookshelf" -> 260L;
            case "chiseled_bookshelf" -> 36L;
            case "lectern" -> 325L;
            case "ladder" -> 6L;
            case "scaffolding" -> 6L;
            case "armor_stand" -> 18L;
            case "flower_pot" -> 58L;
            case "item_frame" -> 96L;
            case "glow_item_frame" -> 144L;
            case "iron_door" -> 615L;
            case "iron_trapdoor" -> 1_230L;
            default -> 0L;
        };
    }

    private static long getRedstoneItemPrice(String path) {
        return switch (path) {
            case "redstone_torch" -> 42L;
            case "lever" -> 8L;
            case "stone_button" -> 6L;
            case "repeater" -> 160L;
            case "comparator" -> 320L;
            case "piston" -> 384L;
            case "sticky_piston" -> 540L;
            case "observer" -> 260L;
            case "dispenser" -> 168L;
            case "dropper" -> 72L;
            case "hopper" -> 1_600L;
            case "crafter" -> 2_100L;
            case "daylight_detector" -> 320L;
            case "target" -> 285L;
            case "redstone_lamp" -> 310L;
            case "tripwire_hook" -> 320L;
            case "tnt" -> 410L;
            case "rail" -> 116L;
            case "powered_rail" -> 800L;
            case "detector_rail" -> 350L;
            case "activator_rail" -> 250L;
            case "minecart" -> 1_536L;
            case "chest_minecart" -> 1_900L;
            case "hopper_minecart" -> 3_750L;
            case "furnace_minecart" -> 1_900L;
            case "tnt_minecart" -> 2_350L;
            default -> 0L;
        };
    }

    private static long getIngredientPrice(String path) {
        long exactPrice = switch (path) {
            case "flint" -> 12L;
            case "paper" -> 5L;
            case "book" -> 76L;
            case "brick" -> 16L;
            case "nether_brick" -> 8L;
            case "ink_sac", "glow_ink_sac" -> 32L;
            case "rabbit_hide" -> 16L;
            case "rabbit_foot" -> 256L;
            case "phantom_membrane" -> 512L;
            case "armadillo_scute" -> 640L;
            case "turtle_scute", "scute" -> 1_024L;
            case "fermented_spider_eye" -> 64L;
            case "fire_charge" -> 480L;
            case "ender_eye" -> 2_500L;
            case "wind_charge" -> 1_200L;
            case "breeze_rod" -> 4_096L;
            case "echo_shard" -> 25_000L;
            case "heavy_core" -> 1_000_000L;
            case "trial_key" -> 40_000L;
            case "ominous_trial_key" -> 150_000L;
            default -> 0L;
        };
        if (exactPrice > 0L) {
            return exactPrice;
        }
        if (path.endsWith("_dye")) return 8L;
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
            case "beetroot", "sweet_berries", "glow_berries", "kelp" -> 8L;
            case "pumpkin", "cocoa_beans", "sugar_cane" -> 12L;
            case "nether_wart" -> 16L;
            case "beetroot_seeds", "melon_seeds", "pumpkin_seeds" -> 4L;
            case "egg" -> 12L;
            case "sugar" -> 4L;
            case "bread" -> 44L;
            case "baked_potato" -> 20L;
            case "cooked_beef", "cooked_porkchop", "cooked_mutton",
                    "cooked_chicken", "cooked_rabbit" -> 48L;
            case "cod", "salmon", "rabbit" -> 32L;
            case "cooked_cod", "cooked_salmon" -> 48L;
            case "pufferfish", "tropical_fish" -> 64L;
            case "cookie" -> 6L;
            case "pumpkin_pie" -> 34L;
            case "cake" -> 220L;
            case "mushroom_stew", "beetroot_soup", "rabbit_stew", "suspicious_stew" -> 48L;
            case "golden_carrot", "glistering_melon_slice" -> 850L;
            case "honey_bottle" -> 24L;
            case "potion" -> 32L;
            case "splash_potion" -> 112L;
            case "lingering_potion" -> 256L;
            default -> 0L;
        };
    }

    private static long getMiscItemPrice(String path) {
        return switch (path) {
            case "bowl" -> 2L;
            case "glass_bottle" -> 10L;
            case "water_bucket" -> 950L;
            case "lava_bucket", "milk_bucket", "powder_snow_bucket" -> 1_024L;
            case "cod_bucket", "salmon_bucket", "tropical_fish_bucket",
                    "pufferfish_bucket" -> 1_200L;
            case "axolotl_bucket", "tadpole_bucket" -> 2_000L;
            case "lead" -> 90L;
            case "name_tag" -> 10_000L;
            case "saddle" -> 8_000L;
            case "experience_bottle" -> 10_000L;
            case "firework_rocket" -> 28L;
            case "firework_star" -> 96L;
            case "map", "empty_map" -> 1_300L;
            case "painting" -> 40L;
            case "writable_book" -> 128L;
            case "written_book" -> 192L;
            case "ominous_bottle" -> 40_000L;
            default -> 0L;
        };
    }

    private static long addCraftingEffort(long ingredientPrice, int percent) {
        return safeAdd(ingredientPrice, percentageOf(ingredientPrice, percent));
    }

    private static long getSmithingTemplatePrice(String path) {
        if (path.contains("silence_armor_trim")) {
            return 2_500_000L; // Rơi cực hiếm tại Ancient City (1.2% rương)
        }
        if (path.contains("netherite_upgrade")) {
            return 800_000L; // Tìm thấy tại Bastion Remnant
        }
        if (containsAny(path, "ward_armor_trim", "spire_armor_trim", "rib_armor_trim")) {
            return 1_200_000L; // Ancient City, End City, Nether Fortress
        }
        if (containsAny(path, "eye_armor_trim", "snout_armor_trim", "vex_armor_trim",
                "tide_armor_trim", "flow_armor_trim", "bolt_armor_trim")) {
            return 600_000L; // Stronghold, Bastion, Woodland Mansion, Ocean Monument, Trial Chamber
        }
        return 350_000L;
    }

    private static long getMusicDiscPrice(String path) {
        if (containsAny(path, "pigstep", "otherside", "relic", "creator", "precipice")) {
            return 450_000L;
        }
        return 120_000L;
    }

    private static long getRarityFloor(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 0L;
            case UNCOMMON -> 5_000L;
            case RARE -> 50_000L;
            case EPIC -> 250_000L;
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
            case "mending" -> 350_000L;
            case "wind_burst" -> 400_000L;
            case "swift_sneak" -> 250_000L;
            case "soul_speed" -> 150_000L;
            case "silk_touch", "infinity", "channeling" -> 80_000L;
            default -> {
                if (weight >= 10) yield 8_000L;
                if (weight >= 5) yield 20_000L;
                if (weight >= 2) yield 50_000L;
                yield 100_000L;
            }
        };
    }

    private static Map<String, EnchantmentValue> getEnchantmentValues(ItemStack stack) {
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

    private record EnchantmentValue(Holder<Enchantment> enchantment, int level) {
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
