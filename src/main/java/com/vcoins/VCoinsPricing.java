package com.vcoins;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

public class VCoinsPricing {
    private static final Map<String, Long> prices = new HashMap<>();
    private static final Map<String, ShopCategory> categories = new HashMap<>();

    public static void init() {
        prices.clear();
        categories.clear();
        
        // Base Materials
        setPrice("minecraft:cobblestone", 4);
        setPrice("minecraft:dirt", 4);
        setPrice("minecraft:sand", 4);
        setPrice("minecraft:gravel", 4);
        setPrice("minecraft:netherrack", 4);
        
        setPrice("minecraft:oak_log", 16);
        setPrice("minecraft:spruce_log", 16);
        setPrice("minecraft:birch_log", 16);
        setPrice("minecraft:jungle_log", 16);
        setPrice("minecraft:acacia_log", 16);
        setPrice("minecraft:dark_oak_log", 16);
        setPrice("minecraft:mangrove_log", 16);
        setPrice("minecraft:cherry_log", 16);
        setPrice("minecraft:oak_planks", 4);
        setPrice("minecraft:stick", 2);
        
        // Minerals
        setPrice("minecraft:coal", 64);
        setPrice("minecraft:charcoal", 64);
        setPrice("minecraft:coal_block", 576);
        setPrice("minecraft:raw_copper", 96);
        setPrice("minecraft:copper_ingot", 128);
        setPrice("minecraft:raw_copper_block", 864);
        setPrice("minecraft:copper_block", 1152);
        setPrice("minecraft:raw_iron", 192);
        setPrice("minecraft:iron_ingot", 256);
        setPrice("minecraft:iron_nugget", 29);
        setPrice("minecraft:raw_iron_block", 1728);
        setPrice("minecraft:iron_block", 2304);
        setPrice("minecraft:raw_gold", 512);
        setPrice("minecraft:gold_ingot", 768);
        setPrice("minecraft:gold_nugget", 85);
        setPrice("minecraft:raw_gold_block", 4608);
        setPrice("minecraft:gold_block", 6912);
        setPrice("minecraft:redstone", 32);
        setPrice("minecraft:redstone_block", 288);
        setPrice("minecraft:lapis_lazuli", 256);
        setPrice("minecraft:lapis_block", 2304);
        setPrice("minecraft:diamond", 16000);
        setPrice("minecraft:diamond_block", 144000);
        setPrice("minecraft:emerald", 8000);
        setPrice("minecraft:emerald_block", 72000);
        setPrice("minecraft:ancient_debris", 32768);
        setPrice("minecraft:netherite_scrap", 32768);
        setPrice("minecraft:netherite_ingot", 160000);
        setPrice("minecraft:netherite_block", 1440000);
        setPrice("minecraft:quartz", 128);
        setPrice("minecraft:quartz_block", 512);
        setPrice("minecraft:amethyst_shard", 128);
        setPrice("minecraft:amethyst_block", 512);
        
        // Mobs
        setPrice("minecraft:rotten_flesh", 8);
        setPrice("minecraft:bone", 24);
        setPrice("minecraft:string", 16);
        setPrice("minecraft:spider_eye", 32);
        setPrice("minecraft:gunpowder", 64);
        setPrice("minecraft:ender_pearl", 1024);
        setPrice("minecraft:blaze_rod", 2048);
        setPrice("minecraft:ghast_tear", 8192);
        setPrice("minecraft:slime_ball", 64);
        setPrice("minecraft:slime_block", 576);
        setPrice("minecraft:magma_cream", 128);
        setPrice("minecraft:shulker_shell", 8192);
        setPrice("minecraft:nether_star", 200000);
        setPrice("minecraft:heart_of_the_sea", 50000);
        setPrice("minecraft:echo_shard", 12000);
        setPrice("minecraft:heavy_core", 200000);
        setPrice("minecraft:breeze_rod", 4096);
        setPrice("minecraft:dragon_egg", 1000000);
        
        // Food
        setPrice("minecraft:wheat_seeds", 4);
        setPrice("minecraft:wheat", 12);
        setPrice("minecraft:hay_block", 108);
        setPrice("minecraft:potato", 12);
        setPrice("minecraft:carrot", 12);
        setPrice("minecraft:apple", 32);
        setPrice("minecraft:beef", 32);
        setPrice("minecraft:porkchop", 32);
        setPrice("minecraft:mutton", 32);
        setPrice("minecraft:chicken", 32);
        setPrice("minecraft:golden_apple", 4096);
        setPrice("minecraft:enchanted_golden_apple", 250000);
        
        // Common items
        setPrice("minecraft:glass", 8);
        setPrice("minecraft:white_wool", 16);
        setPrice("minecraft:torch", 8);
        setPrice("minecraft:bone_block", 216);
        
        // Every registered item (including items added by other mods) belongs to the
        // catalogue. Explicit prices above win; everything else receives a sensible
        // fallback price so the "All items" tab is actually complete.
        for (Item item : Registries.ITEM) {
            String id = Registries.ITEM.getId(item).toString();

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
    }
    
    public static void calculateRecipes(MinecraftServer server) {
        // We disabled recipe parsing due to 1.21.11 API changes and use smart fallback pricing instead.
    }

    private static void setPrice(String id, long price) {
        prices.put(id, price);
    }

    public static long getPrice(String itemId) {
        return prices.getOrDefault(itemId, 0L);
    }

    public static long getSellPrice(String itemId) {
        long buyPrice = getPrice(itemId);
        if (buyPrice <= 0 || isCreativeOnly(itemId)) {
            return 0L;
        }

        // Round up so the integer sell price is never lower than two thirds
        // of the item's buy price. This form also avoids overflowing buyPrice * 2.
        return buyPrice / 3L * 2L + buyPrice % 3L;
    }

    public static long getBuybackPrice(String itemId) {
        long sellPrice = getSellPrice(itemId);
        if (sellPrice <= 0) {
            return 0L;
        }
        return sellPrice + Math.max(1L, sellPrice / 10L);
    }

    public static ShopCategory getCategory(String itemId) {
        return categories.getOrDefault(itemId, ShopCategory.ALL);
    }

    public static Map<String, Long> getAllPrices() {
        return prices;
    }

    public static boolean isTradeable(Item item) {
        return item != Items.AIR && !isCreativeOnly(Registries.ITEM.getId(item).toString());
    }

    private static long getFallbackPrice(String id, ShopCategory category) {
        String path = id.substring(id.indexOf(':') + 1);
        if (path.endsWith("_spawn_egg")) {
            return 0L;
        }
        if (path.contains("smithing_template")) {
            return path.contains("netherite_upgrade") ? 150_000L : 100_000L;
        }
        if (path.equals("elytra")) {
            return 500_000L;
        }
        if (path.equals("totem_of_undying")) {
            return 150_000L;
        }
        if (path.equals("beacon")) {
            return 250_000L;
        }
        if (path.equals("mace")) {
            return 250_000L;
        }
        if (path.equals("trident")) {
            return 100_000L;
        }
        if (path.equals("bow")) {
            return 512L;
        }
        if (path.equals("crossbow")) {
            return 1_024L;
        }
        if (path.contains("arrow")) {
            return path.equals("arrow") ? 16L : 64L;
        }
        if (path.contains("shulker_box")) {
            return 30_000L;
        }
        if (isCopperStorageBlock(path)) {
            return 1_152L;
        }
        if (path.endsWith("_planks")) {
            return 4L;
        }
        if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae")) {
            return 16L;
        }
        if (category == ShopCategory.TOOLS) {
            return materialPrice(path, 32L, 128L, 768L, 512L, 24_000L, 200_000L);
        }
        if (category == ShopCategory.COMBAT) {
            return materialPrice(path, 256L, 768L, 1_536L, 1_024L, 32_000L, 240_000L);
        }
        return switch (category) {
            case BUILDING, COLORED, NATURAL -> 8L;
            case FOOD -> 64L;
            case FUNCTIONAL -> 128L;
            case REDSTONE -> 96L;
            case INGREDIENTS -> 128L;
            case SPAWN_EGGS -> 20_000L;
            case MISC, ALL, BUYBACK -> 64L;
            case TOOLS, COMBAT -> 256L;
        };
    }

    private static long materialPrice(String id, long wood, long stone, long iron, long gold, long diamond, long netherite) {
        if (id.contains("netherite")) return netherite;
        if (id.contains("diamond")) return diamond;
        if (id.contains("iron") || id.contains("chainmail")) return iron;
        if (id.contains("gold")) return gold;
        if (id.contains("stone")) return stone;
        if (id.contains("wooden") || id.contains("leather")) return wood;
        return iron;
    }

    private static boolean isCopperStorageBlock(String id) {
        String unWaxed = id.startsWith("waxed_") ? id.substring("waxed_".length()) : id;
        return unWaxed.equals("copper_block") || unWaxed.equals("exposed_copper")
                || unWaxed.equals("weathered_copper") || unWaxed.equals("oxidized_copper");
    }
    
    public static boolean matchesCategory(Item item, ShopCategory category) {
        if (category == ShopCategory.ALL) {
            return item != Items.AIR;
        }

        String id = Registries.ITEM.getId(item).getPath().toLowerCase(Locale.ROOT);
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
        String id = Registries.ITEM.getId(item).getPath().toLowerCase(Locale.ROOT);

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
        if (item.getComponents().contains(DataComponentTypes.FOOD) || id.contains("potion")) {
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
