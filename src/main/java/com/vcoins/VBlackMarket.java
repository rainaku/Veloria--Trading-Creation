package com.vcoins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VBlackMarket {
    /** 0.25% (1 in 400) chance of rolling a Legend (Roman God) item per card */
    public static final double GOD_ITEM_ROLL_CHANCE = 0.0025;
    /** 0.0125% (1 in 8000) chance of rolling a Mythic item per card */
    public static final double MYTHIC_ROLL_CHANCE = 0.000125;
    /** 10% chance of rolling an Exclusive item per card */
    public static final double EXCLUSIVE_ROLL_CHANCE = 0.10;
    /** 20% chance of rolling an Epic item per card */
    public static final double EPIC_ROLL_CHANCE = 0.20;
    public static final int DAILY_ITEM_COUNT = 10; // 10 cards per day
    /** Pity: guaranteed item every PITY_THRESHOLD flips; Lucky bar boosts Mythic chance */
    public static final int PITY_THRESHOLD = 500;
    /** Lucky bar: after this many non-legend flips, luckyPercent increases by LUCKY_STEP */
    public static final int LUCKY_STEP_FLIPS = 250;
    /** Lucky bar: Mystic chance in pity increases by this % per step */
    public static final int LUCKY_STEP_PERCENT = 1;
    public static final int MAX_LUCKY_PERCENT = 1;
    private static final List<ItemStack> dailyItems = new ArrayList<>();

    private static long currentEpochDay = 0L;
    private static long lastSavedEpochSecond = 0L;
    private static int adminResetCount = 0;
    private static boolean timeAnomalyDetected = false;
    private static int tickCounter = 0;
    private static MinecraftServer activeServer = null;

    // Anti-cheat: nanoTime is monotonic and unaffected by system clock changes
    private static long nanoAnchor = 0L;
    private static long epochAnchor = 0L;
    private static final long FORWARD_TOLERANCE_SEC = 90L;
    private static final long BACKWARD_TOLERANCE_SEC = 30L;

    public static class PlayerDailyRecord {
        public long day;
        public int revealedMask;
        public int purchasedMask;
        public int bankedResets;
        public int resetSequence;
        public List<ItemStack> customItems;
        /** Lifetime flip counter; resets to 0 after pity triggers */
        public int lifetimeFlipCount;
        /** OP-scheduled legend guarantee: inject god item on next successful reset */
        public boolean legendNextReset;
        public boolean mythicNextReset;
        /**
         * Lucky Bar: counts flips since the last Legend/Mystic drop.
         * Every LUCKY_STEP_FLIPS flips without a legend, luckyPercent increases by LUCKY_STEP_PERCENT.
         * Resets to 0 when a Legend or Mystic drops.
         */
        public int luckyFlipCount;
        /** Current lucky bonus %, used to boost Mystic chance in pity reward. */
        public int luckyPercent;

        public PlayerDailyRecord(long day, int revealedMask, int purchasedMask, int bankedResets, int resetSequence, List<ItemStack> customItems, int lifetimeFlipCount, boolean legendNextReset) {
            this(day, revealedMask, purchasedMask, bankedResets, resetSequence, customItems, lifetimeFlipCount, legendNextReset, false);
        }

        public PlayerDailyRecord(long day, int revealedMask, int purchasedMask, int bankedResets, int resetSequence, List<ItemStack> customItems, int lifetimeFlipCount, boolean legendNextReset, boolean mythicNextReset) {
            this(day, revealedMask, purchasedMask, bankedResets, resetSequence, customItems, lifetimeFlipCount, legendNextReset, mythicNextReset, 0, 0);
        }

        public PlayerDailyRecord(long day, int revealedMask, int purchasedMask, int bankedResets, int resetSequence, List<ItemStack> customItems, int lifetimeFlipCount, boolean legendNextReset, boolean mythicNextReset, int luckyFlipCount, int luckyPercent) {
            this.day = day;
            this.revealedMask = revealedMask;
            this.purchasedMask = purchasedMask;
            this.bankedResets = bankedResets;
            this.resetSequence = resetSequence;
            this.customItems = customItems != null ? new ArrayList<>(customItems) : null;
            this.lifetimeFlipCount = lifetimeFlipCount;
            this.legendNextReset = legendNextReset;
            this.mythicNextReset = mythicNextReset;
            this.luckyFlipCount = luckyFlipCount;
            this.luckyPercent = Math.clamp(luckyPercent, 0, MAX_LUCKY_PERCENT);
        }
    }

    private static final Map<UUID, PlayerDailyRecord> playerRecords = new ConcurrentHashMap<>();

    /**
     * High-value, rare item pool strictly excluding junk or farmable blocks.
     */
    private static final Item[] VALUABLE_POOL = new Item[] {
            // End Exploration & Special Structures
            Items.ELYTRA,
            Items.SHULKER_BOX,
            Items.SHULKER_SHELL,
            Items.DRAGON_HEAD,
            Items.DRAGON_BREATH,
            Items.END_CRYSTAL,

            // Treasures & Ocean Monuments
            Items.HEART_OF_THE_SEA,
            Items.NAUTILUS_SHELL,
            Items.TRIDENT,
            Items.CONDUIT,
            Items.SPONGE,

            // Bosses & Mini-boss Drops / Trial Chambers
            Items.NETHER_STAR,
            Items.BEACON,
            Items.TOTEM_OF_UNDYING,
            Items.WITHER_SKELETON_SKULL,
            Items.HEAVY_CORE,
            Items.MACE,
            Items.BREEZE_ROD,
            Items.WIND_CHARGE,
            Items.TRIAL_KEY,
            Items.OMINOUS_TRIAL_KEY,
            Items.OMINOUS_BOTTLE,

            // Deep Dark & Ancient Relics
            Items.ECHO_SHARD,
            Items.RECOVERY_COMPASS,
            Items.SNIFFER_EGG,
            Items.SCULK_CATALYST,
            Items.SCULK_SHRIEKER,

            // Netherite & Precious Minerals
            Items.NETHERITE_INGOT,
            Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
            Items.ANCIENT_DEBRIS,
            Items.NETHERITE_SCRAP,
            Items.DIAMOND_BLOCK,
            Items.ENCHANTED_GOLDEN_APPLE,

            // Horse Armor & Rare Music Discs
            Items.DIAMOND_HORSE_ARMOR,
            Items.NETHERITE_HORSE_ARMOR,
            Items.MUSIC_DISC_PIGSTEP,
            Items.MUSIC_DISC_OTHERSIDE,
            Items.MUSIC_DISC_RELIC,
            Items.MUSIC_DISC_CREATOR,
            Items.MUSIC_DISC_PRECIPICE,

            // Rare Armor Trims / Smithing Templates
            Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.AMETHYST_SHARD, Items.COPPER_INGOT, Items.GOLD_BLOCK, Items.EMERALD,
            Items.QUARTZ, Items.OBSIDIAN, Items.CRYING_OBSIDIAN, Items.GLOWSTONE,
            Items.SEA_LANTERN, Items.PRISMARINE, Items.PURPUR_BLOCK, Items.END_ROD,
            Items.EXPERIENCE_BOTTLE, Items.GOLDEN_CARROT, Items.GOLDEN_APPLE,
            Items.FIREWORK_ROCKET, Items.NAME_TAG, Items.SADDLE, Items.LEAD,
            Items.ENDER_PEARL, Items.BLAZE_ROD, Items.GHAST_TEAR, Items.MAGMA_CREAM,
            Items.HONEY_BLOCK, Items.SLIME_BLOCK, Items.PISTON, Items.OBSERVER,
            Items.HOPPER, Items.DISPENSER, Items.RAIL, Items.POWERED_RAIL,
            Items.BOOKSHELF, Items.ENCHANTING_TABLE, Items.ANVIL, Items.LODESTONE,
            Items.CHERRY_SAPLING, Items.MANGROVE_PROPAGULE, Items.BAMBOO,
            Items.TURTLE_SCUTE, Items.ARMADILLO_SCUTE, Items.GOAT_HORN
    };

    public record RomanGodTemplate(
            Item item,
            String romanName,
            List<ResourceKey<Enchantment>> primaryEnchants,
            List<ResourceKey<Enchantment>> secondaryEnchants
    ) {}

    public static final List<RomanGodTemplate> ROMAN_GOD_TEMPLATES = List.of(
            // Swords
            new RomanGodTemplate(Items.NETHERITE_SWORD, "Gladius Viễn Chinh Caesar",
                    List.of(Enchantments.SHARPNESS, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.LOOTING, Enchantments.FIRE_ASPECT, Enchantments.SWEEPING_EDGE)),
            new RomanGodTemplate(Items.NETHERITE_SWORD, "Lưỡi Spatha Bất Bại Invictus",
                    List.of(Enchantments.SHARPNESS, Enchantments.UNBREAKING, Enchantments.FIRE_ASPECT),
                    List.of(Enchantments.LOOTING, Enchantments.MENDING, Enchantments.KNOCKBACK)),
            new RomanGodTemplate(Items.NETHERITE_SWORD, "Lưỡi Kiếm Của Bellona",
                    List.of(Enchantments.SHARPNESS, Enchantments.SWEEPING_EDGE, Enchantments.MENDING),
                    List.of(Enchantments.FIRE_ASPECT, Enchantments.UNBREAKING, Enchantments.LOOTING)),
            new RomanGodTemplate(Items.NETHERITE_SWORD, "Đoản Kiếm Cấm Vệ Praetorian",
                    List.of(Enchantments.SHARPNESS, Enchantments.LOOTING, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING, Enchantments.SWEEPING_EDGE)),

            // Mace
            new RomanGodTemplate(Items.MACE, "Thiết Trọng Chùy Thợ Rèn Vulcan",
                    List.of(Enchantments.DENSITY, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.BREACH, Enchantments.WIND_BURST, Enchantments.FIRE_ASPECT)),
            new RomanGodTemplate(Items.MACE, "Chùy Gai Sấm Sét Jupiter",
                    List.of(Enchantments.DENSITY, Enchantments.BREACH, Enchantments.WIND_BURST),
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING)),

            // Trident
            new RomanGodTemplate(Items.TRIDENT, "Đinh Ba Thần Biển Neptune",
                    List.of(Enchantments.IMPALING, Enchantments.LOYALTY, Enchantments.CHANNELING),
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING)),
            new RomanGodTemplate(Items.TRIDENT, "Hải Thương Cuồng Nộ Sông Tiber",
                    List.of(Enchantments.IMPALING, Enchantments.RIPTIDE, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING)),

            // Bow & Crossbow
            new RomanGodTemplate(Items.BOW, "Cung Quang Minh Của Apollo",
                    List.of(Enchantments.POWER, Enchantments.FLAME, Enchantments.INFINITY),
                    List.of(Enchantments.PUNCH, Enchantments.UNBREAKING)),
            new RomanGodTemplate(Items.BOW, "Cung Ngọn Lửa Vĩnh Cửu Vesta",
                    List.of(Enchantments.POWER, Enchantments.FLAME, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING, Enchantments.PUNCH)),
            new RomanGodTemplate(Items.CROSSBOW, "Nỏ Hãm Thành Binh Đoàn Legio IX",
                    List.of(Enchantments.QUICK_CHARGE, Enchantments.MULTISHOT, Enchantments.UNBREAKING),
                    List.of(Enchantments.PIERCING, Enchantments.MENDING)),
            new RomanGodTemplate(Items.CROSSBOW, "Nỏ Khổn Mã Ballista: Khắc Tinh Quái Vật",
                    List.of(Enchantments.QUICK_CHARGE, Enchantments.PIERCING, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING)),

            // Axe & Pickaxe
            new RomanGodTemplate(Items.NETHERITE_AXE, "Chiến Rìu Đấu Trường Colosseum",
                    List.of(Enchantments.SHARPNESS, Enchantments.EFFICIENCY, Enchantments.UNBREAKING),
                    List.of(Enchantments.SILK_TOUCH, Enchantments.MENDING)),
            new RomanGodTemplate(Items.NETHERITE_PICKAXE, "Cuốc Khai Khoáng Của Saturn",
                    List.of(Enchantments.EFFICIENCY, Enchantments.FORTUNE, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING)),

            // Armor
            new RomanGodTemplate(Items.NETHERITE_CHESTPLATE, "Chiến Giáp Bản Mạ Của Hoàng Đế",
                    List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.THORNS)),
            new RomanGodTemplate(Items.NETHERITE_CHESTPLATE, "Bảo Hộ Khiên Thành Minerva",
                    List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.THORNS),
                    List.of(Enchantments.MENDING)),
            new RomanGodTemplate(Items.NETHERITE_HELMET, "Chiến Khôi Thần Chiến Mars",
                    List.of(Enchantments.PROTECTION, Enchantments.RESPIRATION, Enchantments.AQUA_AFFINITY),
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING)),
            new RomanGodTemplate(Items.NETHERITE_HELMET, "Vương Miện Nguyệt Quế Khải Hoàn",
                    List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.RESPIRATION)),
            new RomanGodTemplate(Items.NETHERITE_LEGGINGS, "Quần Giáp Xích Đen Centurion",
                    List.of(Enchantments.PROTECTION, Enchantments.SWIFT_SNEAK, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING, Enchantments.THORNS)),
            new RomanGodTemplate(Items.NETHERITE_BOOTS, "Chiến Ủng Bọc Thép Mercury",
                    List.of(Enchantments.PROTECTION, Enchantments.FEATHER_FALLING, Enchantments.DEPTH_STRIDER),
                    List.of(Enchantments.SOUL_SPEED, Enchantments.UNBREAKING, Enchantments.MENDING)),

            // Elytra & Shield
            new RomanGodTemplate(Items.ELYTRA, "Đôi Cánh Đế Chế Roma Aeterna",
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING, Enchantments.FEATHER_FALLING),
                    List.of()),
            new RomanGodTemplate(Items.SHIELD, "Đại Khiên Scutum Cấm Vệ Quân",
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING, Enchantments.PROTECTION),
                    List.of())
    );

    public record RomanNoun(String key, String vi, String en) {}
    public record RomanModifier(String key, String vi, String en) {}
    public record RomanFigure(String key, String vi, String en) {}
    public record RomanSuffix(String key, String vi, String en) {}

    public record RomanArchetype(
            Item item,
            RomanNoun[] nouns,
            List<ResourceKey<Enchantment>> primaryEnchants,
            List<ResourceKey<Enchantment>> secondaryEnchants
    ) {}

    public static final List<RomanArchetype> ROMAN_ARCHETYPES = List.of(
            // 1. Netherite Sword
            new RomanArchetype(
                    Items.NETHERITE_SWORD,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.gladius", "Thanh Gladius", "Gladius"),
                            new RomanNoun("vcoins.roman.noun.spatha", "Lưỡi Spatha", "Spatha"),
                            new RomanNoun("vcoins.roman.noun.longsword", "Trường Kiếm", "Longsword"),
                            new RomanNoun("vcoins.roman.noun.dark_shortsword", "Đoản Kiếm Thép Đen", "Dark Steel Shortsword"),
                            new RomanNoun("vcoins.roman.noun.centurion_sword", "Bảo Kiếm Bách Binh", "Centurion Blade"),
                            new RomanNoun("vcoins.roman.noun.war_sword", "Chiến Kiếm", "War Sword"),
                            new RomanNoun("vcoins.roman.noun.netherite_blade", "Lưỡi Gươm Hắc Kim", "Netherite Blade"),
                            new RomanNoun("vcoins.roman.noun.vanguard_sword", "Thanh Kiếm Tiên Phong", "Vanguard Sword")
                    },
                    List.of(Enchantments.SHARPNESS, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.LOOTING, Enchantments.FIRE_ASPECT, Enchantments.SWEEPING_EDGE, Enchantments.KNOCKBACK)
            ),
            // 2. Mace
            new RomanArchetype(
                    Items.MACE,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.heavy_mace", "Thiết Trọng Chùy", "Heavy Iron Mace"),
                            new RomanNoun("vcoins.roman.noun.spiked_mace", "Chùy Gai Hắc Kim", "Spiked Netherite Mace"),
                            new RomanNoun("vcoins.roman.noun.siege_mace", "Chiến Chùy Phá Thành", "Siege Breaker Mace"),
                            new RomanNoun("vcoins.roman.noun.colosseum_mace", "Trọng Chùy Đấu Trường", "Colosseum Heavy Mace"),
                            new RomanNoun("vcoins.roman.noun.centurion_mace", "Chùy Sắt Bách Binh", "Centurion Iron Mace"),
                            new RomanNoun("vcoins.roman.noun.gravity_mace", "Chùy Trọng Lực", "Gravitational Mace"),
                            new RomanNoun("vcoins.roman.noun.bulwark_mace", "Cự Chùy Hộ Thành", "Bulwark Great Mace")
                    },
                    List.of(Enchantments.DENSITY, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.BREACH, Enchantments.WIND_BURST, Enchantments.FIRE_ASPECT)
            ),
            // 3. Trident
            new RomanArchetype(
                    Items.TRIDENT,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.deep_trident", "Đinh Ba Biển Sâu", "Deep Sea Trident"),
                            new RomanNoun("vcoins.roman.noun.tidal_spear", "Hải Thương Triều Cường", "Tidal War Spear"),
                            new RomanNoun("vcoins.roman.noun.tri_lance", "Tam Xoa Kích", "Tri-Barbed Lance"),
                            new RomanNoun("vcoins.roman.noun.thunder_javelin", "Mũi Lao Sấm Sét", "Thunder Javelin"),
                            new RomanNoun("vcoins.roman.noun.ocean_pike", "Trường Kích Biển Khơi", "Oceanic Pike"),
                            new RomanNoun("vcoins.roman.noun.surge_trident", "Chiến Thương Sóng Cồn", "Surge Combat Spear"),
                            new RomanNoun("vcoins.roman.noun.tide_harpoon", "Mũi Phóng Hải Triều", "Tide Harpoon")
                    },
                    List.of(Enchantments.IMPALING, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.LOYALTY, Enchantments.CHANNELING)
            ),
            // 4. Bow
            new RomanArchetype(
                    Items.BOW,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.composite_bow", "Cung Dài Hợp Kim", "Composite Longbow"),
                            new RomanNoun("vcoins.roman.noun.marksman_bow", "Cung Săn Thiện Xạ", "Marksman Hunting Bow"),
                            new RomanNoun("vcoins.roman.noun.laurel_bow", "Chiến Cung Nguyệt Quế", "Laurel War Bow"),
                            new RomanNoun("vcoins.roman.noun.centurion_recurve", "Cung Khúc Bách Binh", "Centurion Recurve Bow"),
                            new RomanNoun("vcoins.roman.noun.imperial_greatbow", "Đại Cung Hoàng Gia", "Imperial Greatbow"),
                            new RomanNoun("vcoins.roman.noun.netherite_longbow", "Cung Tầm Xa Hắc Kim", "Netherite Longbow")
                    },
                    List.of(Enchantments.POWER, Enchantments.UNBREAKING, Enchantments.FLAME),
                    List.of(Enchantments.PUNCH, Enchantments.INFINITY, Enchantments.MENDING)
            ),
            // 5. Crossbow
            new RomanArchetype(
                    Items.CROSSBOW,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.ballista_crossbow", "Nỏ Hãm Thành Ballista", "Ballista Siege Crossbow"),
                            new RomanNoun("vcoins.roman.noun.arbalest", "Nỏ Khổn Mã", "Heavy Arbalest"),
                            new RomanNoun("vcoins.roman.noun.dark_crossbow", "Trọng Nỏ Thép Đen", "Dark Steel Heavy Crossbow"),
                            new RomanNoun("vcoins.roman.noun.repeater_crossbow", "Cơ Nỏ Bắn Nhanh", "Rapid Repeating Crossbow"),
                            new RomanNoun("vcoins.roman.noun.armor_piercer", "Nỏ Phá Giáp Bách Binh", "Armor-Piercing Crossbow"),
                            new RomanNoun("vcoins.roman.noun.skirmish_crossbow", "Chiến Nỏ Đột Kích", "Skirmisher Crossbow")
                    },
                    List.of(Enchantments.QUICK_CHARGE, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.PIERCING, Enchantments.MULTISHOT)
            ),
            // 6. Netherite Axe
            new RomanArchetype(
                    Items.NETHERITE_AXE,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.executioner_axe", "Chiến Rìu Phạt Tội", "Executioner War Axe"),
                            new RomanNoun("vcoins.roman.noun.arena_greataxe", "Đại Rìu Đấu Trường", "Arena Greataxe"),
                            new RomanNoun("vcoins.roman.noun.netherite_axe", "Rìu Khai Phá Hắc Kim", "Netherite Cleaver"),
                            new RomanNoun("vcoins.roman.noun.vanguard_axe", "Rìu Thép Tiên Phong", "Vanguard Steel Axe"),
                            new RomanNoun("vcoins.roman.noun.centurion_axe", "Chiến Rìu Bách Binh", "Centurion War Axe"),
                            new RomanNoun("vcoins.roman.noun.crescent_axe", "Rìu Lưỡi Bán Nguyệt", "Crescent Battle Axe")
                    },
                    List.of(Enchantments.SHARPNESS, Enchantments.EFFICIENCY, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING, Enchantments.FORTUNE)
            ),
            // 7. Netherite Pickaxe
            new RomanArchetype(
                    Items.NETHERITE_PICKAXE,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.mining_pickaxe", "Cuốc Khai Khoáng", "Mining Pickaxe"),
                            new RomanNoun("vcoins.roman.noun.tunneler_pickaxe", "Cuốc Đào Hầm Quân Tiên Phong", "Sapper Tunneling Pickaxe"),
                            new RomanNoun("vcoins.roman.noun.sediment_pickaxe", "Thiết Cuốc Trầm Tích", "Sedimentary Iron Pickaxe"),
                            new RomanNoun("vcoins.roman.noun.ore_piercer", "Cuốc Xuyên Đá Hắc Sa", "Dark Sand Ore Piercer"),
                            new RomanNoun("vcoins.roman.noun.royal_miner", "Cuốc Thợ Mỏ Hoàng Gia", "Imperial Mining Pickaxe"),
                            new RomanNoun("vcoins.roman.noun.stratum_pickaxe", "Cuốc Địa Tầng", "Stratum Excavator")
                    },
                    List.of(Enchantments.EFFICIENCY, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.FORTUNE)
            ),
            // 8. Netherite Shovel
            new RomanArchetype(
                    Items.NETHERITE_SHOVEL,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.trench_shovel", "Xẻng Công Sự", "Entrenching Shovel"),
                            new RomanNoun("vcoins.roman.noun.military_spade", "Thiết Xẻng Quân Đội", "Military Iron Spade"),
                            new RomanNoun("vcoins.roman.noun.centurion_spade", "Xẻng Đào Hào Bách Binh", "Centurion Trench Spade"),
                            new RomanNoun("vcoins.roman.noun.archaeo_shovel", "Xẻng Khảo Cổ Hoàng Gia", "Imperial Archaeological Shovel"),
                            new RomanNoun("vcoins.roman.noun.skirmish_shovel", "Xẻng Tiên Phong Đột Kích", "Vanguard Assault Spade"),
                            new RomanNoun("vcoins.roman.noun.dark_shovel", "Xẻng Khai Hào Thép Đen", "Dark Steel Excavation Shovel")
                    },
                    List.of(Enchantments.EFFICIENCY, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.SILK_TOUCH, Enchantments.FORTUNE)
            ),
            // 9. Netherite Hoe
            new RomanArchetype(
                    Items.NETHERITE_HOE,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.harvest_scythe", "Lưỡi Hái Mùa Vụ", "Harvest Scythe"),
                            new RomanNoun("vcoins.roman.noun.sickle_reaper", "Câu Liêm Trầm Tích", "Sediment Sickle"),
                            new RomanNoun("vcoins.roman.noun.silver_sickle", "Lưỡi Liềm Bạc", "Silver Harvesting Sickle"),
                            new RomanNoun("vcoins.roman.noun.ceres_scythe", "Liêm Đao Thần Nông", "Scythe of Ceres"),
                            new RomanNoun("vcoins.roman.noun.imperial_scythe", "Lưỡi Hái Hoàng Gia", "Imperial Field Scythe"),
                            new RomanNoun("vcoins.roman.noun.ancient_reaper", "Lưỡi Cắt Cổ Xưa", "Ancient Reaper")
                    },
                    List.of(Enchantments.EFFICIENCY, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.FORTUNE)
            ),
            // 10. Netherite Chestplate
            new RomanArchetype(
                    Items.NETHERITE_CHESTPLATE,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.plate_armor", "Chiến Giáp Bản Mạ", "Plated Cuirass"),
                            new RomanNoun("vcoins.roman.noun.dark_chestplate", "Giáp Ngực Thép Đen", "Dark Steel Chestplate"),
                            new RomanNoun("vcoins.roman.noun.campaign_tunic", "Chiến Bào Viễn Chinh", "Expeditionary Battle Cuirass"),
                            new RomanNoun("vcoins.roman.noun.lorica_segmentata", "Hộ Tâm Phiến Lorica", "Lorica Segmentata"),
                            new RomanNoun("vcoins.roman.noun.centurion_cuirass", "Chiến Bào Bách Binh", "Centurion Cuirass"),
                            new RomanNoun("vcoins.roman.noun.netherite_armor", "Áo Giáp Hắc Kim", "Netherite Breastplate")
                    },
                    List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.THORNS)
            ),
            // 11. Netherite Helmet
            new RomanArchetype(
                    Items.NETHERITE_HELMET,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.galea_helm", "Chiến Khôi Galea", "Galea War Helm"),
                            new RomanNoun("vcoins.roman.noun.gladiator_helm", "Mũ Sắt Giác Đấu Sĩ", "Gladiator Iron Helm"),
                            new RomanNoun("vcoins.roman.noun.plumed_helm", "Mũ Trùm Lông Đỏ", "Red-Plumed Centurion Helm"),
                            new RomanNoun("vcoins.roman.noun.laurel_crown", "Vương Miện Nguyệt Quế", "Golden Laurel Crown"),
                            new RomanNoun("vcoins.roman.noun.netherite_helm", "Chiến Khôi Hắc Kim", "Netherite Great Helm"),
                            new RomanNoun("vcoins.roman.noun.centurion_helm", "Mũ Giáp Bách Binh", "Centurion War Helm")
                    },
                    List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.RESPIRATION, Enchantments.AQUA_AFFINITY, Enchantments.THORNS)
            ),
            // 12. Netherite Leggings
            new RomanArchetype(
                    Items.NETHERITE_LEGGINGS,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.greaves", "Xà Cạp Thép Hộ Chân", "Steel Greaves"),
                            new RomanNoun("vcoins.roman.noun.chain_leggings", "Quần Giáp Xích Đen", "Dark Chain Leggings"),
                            new RomanNoun("vcoins.roman.noun.greaves_armor", "Giáp Ống Chân Greaves", "Reinforced Shin Greaves"),
                            new RomanNoun("vcoins.roman.noun.warding_leggings", "Hộ Vệ Giáp Chân", "Warding Leg Guards"),
                            new RomanNoun("vcoins.roman.noun.plated_leggings", "Quần Giáp Bản Mạ", "Plated War Leggings")
                    },
                    List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.SWIFT_SNEAK, Enchantments.THORNS)
            ),
            // 13. Netherite Boots
            new RomanArchetype(
                    Items.NETHERITE_BOOTS,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.steel_boots", "Chiến Ủng Bọc Thép", "Armored Steel Boots"),
                            new RomanNoun("vcoins.roman.noun.caligae_boots", "Đôi Giày Hành Quân Caligae", "Marching Caligae"),
                            new RomanNoun("vcoins.roman.noun.desert_boots", "Ủng Viễn Chinh Sa Mạc", "Desert Expedition Boots"),
                            new RomanNoun("vcoins.roman.noun.swift_boots", "Chiến Hài Thần Tốc", "Swiftstride War Boots"),
                            new RomanNoun("vcoins.roman.noun.cavalry_boots", "Ủng Thiết Kỵ", "Iron Cavalry Boots")
                    },
                    List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.FEATHER_FALLING, Enchantments.DEPTH_STRIDER, Enchantments.SOUL_SPEED, Enchantments.THORNS)
            ),
            // 14. Shield
            new RomanArchetype(
                    Items.SHIELD,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.scutum_shield", "Khiên Tháp Scutum", "Scutum Tower Shield"),
                            new RomanNoun("vcoins.roman.noun.parma_shield", "Khiên Tròn Parma", "Parma Round Shield"),
                            new RomanNoun("vcoins.roman.noun.bastion_shield", "Đại Khiên Hộ Thành", "Bastion Greatshield"),
                            new RomanNoun("vcoins.roman.noun.netherite_shield", "Thuẫn Chắn Hắc Kim", "Netherite Aegis"),
                            new RomanNoun("vcoins.roman.noun.arena_shield", "Khiên Đấu Trường", "Arena Buckler"),
                            new RomanNoun("vcoins.roman.noun.centurion_shield", "Khiên Bách Binh", "Centurion War Shield")
                    },
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING, Enchantments.PROTECTION),
                    List.of(Enchantments.THORNS)
            ),
            // 15. Elytra
            new RomanArchetype(
                    Items.ELYTRA,
                    new RomanNoun[] {
                            new RomanNoun("vcoins.roman.noun.hermes_wings", "Đôi Cánh Thần Sứ", "Wings of the Messenger"),
                            new RomanNoun("vcoins.roman.noun.aquila_wings", "Cánh Ưng Biểu Aquila", "Aquila Eagle Wings"),
                            new RomanNoun("vcoins.roman.noun.triumphal_wings", "Đôi Cánh Khải Hoàn", "Triumphal Glider Wings"),
                            new RomanNoun("vcoins.roman.noun.aeterna_wings", "Cánh Lượn Roma Aeterna", "Roma Aeterna Wings"),
                            new RomanNoun("vcoins.roman.noun.sky_wings", "Đôi Cánh Bầu Trời", "Celestial Wings")
                    },
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING, Enchantments.FEATHER_FALLING),
                    List.of(Enchantments.PROTECTION)
            )
    );

    public static final RomanModifier[] ROMAN_MODIFIERS = new RomanModifier[] {
            new RomanModifier("vcoins.roman.mod.expedition", "Viễn Chinh", "Expeditionary"),
            new RomanModifier("vcoins.roman.mod.undying", "Bất Diệt", "Undying"),
            new RomanModifier("vcoins.roman.mod.invincible", "Bất Bại", "Invincible"),
            new RomanModifier("vcoins.roman.mod.triumphal", "Khải Hoàn", "Triumphal"),
            new RomanModifier("vcoins.roman.mod.realmguard", "Trấn Quốc", "Realm-Guarding"),
            new RomanModifier("vcoins.roman.mod.netherite", "Hắc Kim", "Netherite"),
            new RomanModifier("vcoins.roman.mod.darksteel", "Thép Đen", "Dark Steel"),
            new RomanModifier("vcoins.roman.mod.vanguard", "Tiên Phong", "Vanguard"),
            new RomanModifier("vcoins.roman.mod.vengeance", "Phục Hận", "Avenging"),
            new RomanModifier("vcoins.roman.mod.fury", "Cuồng Nộ", "Furious"),
            new RomanModifier("vcoins.roman.mod.armorbreaker", "Phá Giáp", "Armor-Breaker"),
            new RomanModifier("vcoins.roman.mod.warding", "Hộ Mệnh", "Warding"),
            new RomanModifier("vcoins.roman.mod.fortified", "Kiên Cố", "Fortified"),
            new RomanModifier("vcoins.roman.mod.bloodforged", "Huyết Chiến", "Blood-Forged"),
            new RomanModifier("vcoins.roman.mod.blazing", "Rực Lửa", "Blazing"),
            new RomanModifier("vcoins.roman.mod.tempest", "Bão Tố", "Tempestuous"),
            new RomanModifier("vcoins.roman.mod.ancient", "Cổ Xưa", "Ancient"),
            new RomanModifier("vcoins.roman.mod.majestic", "Uy Nghi", "Majestic"),
            new RomanModifier("vcoins.roman.mod.thunder", "Sấm Sét", "Thunderous"),
            new RomanModifier("vcoins.roman.mod.veteran", "Bách Chiến", "Battle-Hardened"),
            new RomanModifier("vcoins.roman.mod.forged", "Thép Đúc", "Cast-Steel"),
            new RomanModifier("vcoins.roman.mod.refined", "Tinh Luyện", "Refined")
    };

    public static final RomanFigure[] ROMAN_FIGURES = new RomanFigure[] {
            new RomanFigure("vcoins.roman.fig.caesar", "Caesar", "Caesar"),
            new RomanFigure("vcoins.roman.fig.augustus", "Augustus", "Augustus"),
            new RomanFigure("vcoins.roman.fig.jupiter", "Jupiter", "Jupiter"),
            new RomanFigure("vcoins.roman.fig.mars", "Mars", "Mars"),
            new RomanFigure("vcoins.roman.fig.vulcan", "Vulcan", "Vulcan"),
            new RomanFigure("vcoins.roman.fig.neptune", "Neptune", "Neptune"),
            new RomanFigure("vcoins.roman.fig.minerva", "Minerva", "Minerva"),
            new RomanFigure("vcoins.roman.fig.apollo", "Apollo", "Apollo"),
            new RomanFigure("vcoins.roman.fig.mercury", "Mercury", "Mercury"),
            new RomanFigure("vcoins.roman.fig.bellona", "Bellona", "Bellona"),
            new RomanFigure("vcoins.roman.fig.pluto", "Pluto", "Pluto"),
            new RomanFigure("vcoins.roman.fig.saturn", "Saturn", "Saturn"),
            new RomanFigure("vcoins.roman.fig.vesta", "Vesta", "Vesta"),
            new RomanFigure("vcoins.roman.fig.diana", "Diana", "Diana"),
            new RomanFigure("vcoins.roman.fig.sol_invictus", "Sol Invictus", "Sol Invictus"),
            new RomanFigure("vcoins.roman.fig.janus", "Janus", "Janus"),
            new RomanFigure("vcoins.roman.fig.fortuna", "Fortuna", "Fortuna"),
            new RomanFigure("vcoins.roman.fig.scipio", "Scipio", "Scipio"),
            new RomanFigure("vcoins.roman.fig.trajan", "Trajan", "Trajan"),
            new RomanFigure("vcoins.roman.fig.spartacus", "Spartacus", "Spartacus"),
            new RomanFigure("vcoins.roman.fig.aurelian", "Aurelian", "Aurelian"),
            new RomanFigure("vcoins.roman.fig.marcus_aurelius", "Marcus Aurelius", "Marcus Aurelius"),
            new RomanFigure("vcoins.roman.fig.centurion", "Centurion Bách Binh", "Centurion"),
            new RomanFigure("vcoins.roman.fig.praetorian", "Cấm Vệ Quân Praetorian", "Praetorian Guard"),
            new RomanFigure("vcoins.roman.fig.legio_x", "Binh Đoàn Legio X", "Legio X Equestris"),
            new RomanFigure("vcoins.roman.fig.legio_xiii", "Quân Đoàn Legio XIII", "Legio XIII Gemina")
    };

    public static final RomanSuffix[] ROMAN_SUFFIXES = new RomanSuffix[] {
            new RomanSuffix("vcoins.roman.suf.city_guard", "Trấn Giữ Đô Thành", "Guardian of Rome"),
            new RomanSuffix("vcoins.roman.suf.monster_slayer", "Khắc Tinh Quái Vật", "Scourge of Beasts"),
            new RomanSuffix("vcoins.roman.suf.frontline_breaker", "Xuyên Phá Tiền Tuyến", "Frontline Breaker"),
            new RomanSuffix("vcoins.roman.suf.northern_conquest", "Chinh Phục Phương Bắc", "Northern Conquest"),
            new RomanSuffix("vcoins.roman.suf.desert_rule", "Thống Trị Sa Mạc", "Ruler of the Sands"),
            new RomanSuffix("vcoins.roman.suf.triumphal_arch", "Khải Hoàn Môn", "Triumphal Arch"),
            new RomanSuffix("vcoins.roman.suf.colosseum", "Đấu Trường Colosseum", "Colosseum Arena"),
            new RomanSuffix("vcoins.roman.suf.etna", "Núi Lửa Etna", "Mount Etna"),
            new RomanSuffix("vcoins.roman.suf.tiber", "Dòng Sông Tiber", "Tiber River"),
            new RomanSuffix("vcoins.roman.suf.capitoline", "Đồi Capitoline", "Capitoline Hill"),
            new RomanSuffix("vcoins.roman.suf.senate_guard", "Bảo Vệ Viện Nguyên Lão", "Shield of the Senate"),
            new RomanSuffix("vcoins.roman.suf.alps", "Vượt Dãy Alps", "Crossing the Alps"),
            new RomanSuffix("vcoins.roman.suf.eternal_time", "Bất Diệt Thời Gian", "Timeless Eternity"),
            new RomanSuffix("vcoins.roman.suf.pax_romana", "Thời Kỳ Pax Romana", "Pax Romana"),
            new RomanSuffix("vcoins.roman.suf.gaul_campaign", "Chiến Dịch Xứ Gaul", "Campaign of Gaul"),
            new RomanSuffix("vcoins.roman.suf.border_peace", "Bình Định Biên Giới", "Frontier Pacifier")
    };

    public static final int ROMAN_LORE_COUNT = 24;

    /**
     * Calculates the exact number of possible unique Roman God item combinations across all archetypes.
     * Guaranteed to exceed 120,000 distinct items.
     */
    public static int getPossibleGodItemCount() {
        int total = 0;
        int m = ROMAN_MODIFIERS.length;
        int f = ROMAN_FIGURES.length;
        int s = ROMAN_SUFFIXES.length;
        for (RomanArchetype arch : ROMAN_ARCHETYPES) {
            int n = arch.nouns().length;
            total += (n * m * f) + (n * f) + (n * m * s) + (n * f * s) + (n * m);
        }
        return total;
    }

    public record GeneratedName(Component component, String canonicalViName, String canonicalEnName, String nounKey, String figKey) {
        public GeneratedName(Component component, String canonicalViName, String canonicalEnName) {
            this(component, canonicalViName, canonicalEnName, "", "");
        }
    }

    public static GeneratedName generateLocalizedRomanName(Random random, RomanArchetype archetype) {
        RomanNoun noun = archetype.nouns()[random.nextInt(archetype.nouns().length)];
        RomanFigure fig = ROMAN_FIGURES[random.nextInt(ROMAN_FIGURES.length)];
        Component name = Component.translatable(noun.key()).append(" ").append(Component.translatable(fig.key()));
        return new GeneratedName(name, noun.vi() + " " + fig.vi(), noun.en() + " " + fig.en(), noun.key(), fig.key());
    }

    public static String generateHumanizedRomanName(Random random, RomanArchetype archetype) {
        return generateLocalizedRomanName(random, archetype).canonicalViName();
    }

    public static boolean isValuableItem(Item item) {
        for (Item valuable : VALUABLE_POOL) {
            if (valuable == item) return true;
        }
        return false;
    }

    public static HolderLookup.Provider getRegistryLookup() {
        if (activeServer != null) {
            return activeServer.registryAccess();
        }
        return VanillaRegistries.createWorldLookup();
    }

    public static ItemStack generateRomanGodItem(Random random) {
        // Price ranges from 300,000,000 to 1,000,000,000 Velicoins
        long price = 300_000_000L + (long) (random.nextDouble() * 700_000_000L);
        price = (price / 1_000_000L) * 1_000_000L;
        return generateRomanGodItem(random, price);
    }

    public static ItemStack generateRomanGodItem(Random random, long price) {
        if (price < 300_000_000L) {
            price = 300_000_000L;
        }
        double powerRatio = Math.max(0.0, Math.min(1.0, (price - 300_000_000L) / 700_000_000.0));

        RomanArchetype archetype = ROMAN_ARCHETYPES.get(random.nextInt(ROMAN_ARCHETYPES.size()));
        ItemStack stack = new ItemStack(archetype.item());

        // Durability calculation: at least 200 durability remaining; scales up to full durability at 1B
        int maxDur = stack.getMaxDamage();
        int remainingDur;
        if (maxDur > 200) {
            remainingDur = (int) Math.round(200 + powerRatio * (maxDur - 200));
            int damage = Math.max(0, maxDur - remainingDur);
            stack.setDamageValue(damage);
        } else {
            stack.setDamageValue(0);
            remainingDur = maxDur;
        }

        // Procedural localized name & lore generation
        GeneratedName generatedName = generateLocalizedRomanName(random, archetype);
        int loreIndex = random.nextInt(ROMAN_LORE_COUNT);
        String loreKey = "vcoins.roman.lore." + loreIndex;

        // Custom Name (Localized with translatable component)
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("vcoins.roman.item_wrapper", generatedName.component())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Custom Lore (Fully localized with translatable components)
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("vcoins.roman.header").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        lore.add(Component.translatable(loreKey).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        if (maxDur > 0) {
            lore.add(Component.translatable("vcoins.roman.durability", remainingDur, maxDur).withStyle(ChatFormatting.GRAY));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));

        // Max survival levels, with archetype preferences and compatible additions.
        var candidates = applicableEnchantments(stack);
        List<ResourceKey<Enchantment>> preferred = new ArrayList<>(archetype.primaryEnchants());
        preferred.addAll(archetype.secondaryEnchants());
        candidates.sort(Comparator.comparingInt(h -> {
            int index = preferred.indexOf(h.unwrapKey().orElse(null));
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        List<Holder<Enchantment>> applied = new ArrayList<>();
        for (var enchantment : candidates) {
            if (applied.stream().anyMatch(other -> !areEnchantmentsCompatible(other, enchantment))) continue;
            stack.enchant(enchantment, enchantment.value().getMaxLevel());
            applied.add(enchantment);
        }

        // Tag custom data — Legend items cannot be traded
        long finalPrice = price;
        String finalName = generatedName.canonicalViName();
        String finalLoreKey = loreKey;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean("VRomanGodItem", true);
            tag.putLong("VCoinsBlackMarketPrice", finalPrice);
            tag.putString("VRomanName", finalName);
            tag.putString("VRomanNounKey", generatedName.nounKey());
            tag.putString("VRomanFigKey", generatedName.figKey());
            tag.putString("VRomanLore", finalLoreKey);
            tag.putBoolean("VAnnounced", false);
            tag.putBoolean("VNoTrade", true); // Legend cannot be traded in any form
        });

        stampEquipment(stack);
        return stack;
    }

    private static final Item[] EPIC_WEAPONS_ARMOR = {
            Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_HOE,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS };

    private static final Item[] EXCLUSIVE_WEAPONS_ARMOR = {
            Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS };

    /**
     * Safe item pool for Epic bundles (≤500k total value each).
     * Avoids shulker shells, netherite, ancient debris, rare boss drops.
     */
    private static final Item[] EPIC_BUNDLE_POOL = {
            Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.QUARTZ,
            Items.COAL, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.GHAST_TEAR, Items.SLIME_BALL,
            Items.MAGMA_CREAM, Items.BONE_MEAL, Items.GUNPOWDER, Items.STRING, Items.FEATHER,
            Items.LEATHER, Items.AMETHYST_SHARD, Items.COPPER_INGOT, Items.EMERALD,
            Items.GOLDEN_CARROT, Items.GOLDEN_APPLE, Items.RABBIT_FOOT,
            Items.PHANTOM_MEMBRANE, Items.TURTLE_SCUTE, Items.ARMADILLO_SCUTE,
            Items.GLOWSTONE, Items.OBSIDIAN, Items.NETHER_BRICK,
            Items.NAUTILUS_SHELL, Items.INK_SAC, Items.HONEY_BLOCK
    };

    /**
     * Safe item pool for Exclusive bundles (≤1.5M total value each).
     * Higher-value materials but still no game-breaking drops.
     */
    private static final Item[] EXCLUSIVE_BUNDLE_POOL = {
            Items.DIAMOND, Items.GOLD_BLOCK, Items.IRON_BLOCK, Items.DIAMOND_BLOCK,
            Items.PRISMARINE_CRYSTALS, Items.PRISMARINE_SHARD,
            Items.ECHO_SHARD, Items.SNIFFER_EGG,
            Items.EXPERIENCE_BOTTLE, Items.SADDLE, Items.NAME_TAG, Items.LEAD,
            Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_RELIC, Items.MUSIC_DISC_OTHERSIDE,
            Items.SCULK_CATALYST, Items.SCULK_SHRIEKER,
            Items.BREEZE_ROD, Items.TRIAL_KEY, Items.OMINOUS_TRIAL_KEY,
            Items.CONDUIT, Items.TOTEM_OF_UNDYING
    };

    /** Max value (Velicoins) of total item bundle for each tier. */
    private static final long EPIC_BUNDLE_MAX_VALUE    = 500_000L;
    private static final long EXCLUSIVE_BUNDLE_MAX_VALUE = 1_500_000L;

    public static String getCardTier(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString("VCardTier").orElse("");
    }

    public static boolean isMythicItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && "mythic".equals(getCardTier(stack));
    }

    private static List<Holder<Enchantment>> applicableEnchantments(ItemStack stack) {
        var lookup = getRegistryLookup().lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> result = lookup.listElements()
                .filter(h -> h.value().canEnchant(stack))
                .filter(h -> !h.unwrapKey().orElseThrow().identifier().getPath().contains("curse"))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        // Offline generation/tests have no bound item tags. Use explicit archetype enchantments
        // as a fallback; on a running server the registry includes every applicable enchantment.
        if (result.isEmpty()) {
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                    .replace("iron_", "netherite_").replace("diamond_", "netherite_");
            Set<ResourceKey<Enchantment>> keys = new LinkedHashSet<>();
            for (RomanArchetype archetype : ROMAN_ARCHETYPES) {
                if (BuiltInRegistries.ITEM.getKey(archetype.item()).getPath().equals(path)) {
                    keys.addAll(archetype.primaryEnchants());
                    keys.addAll(archetype.secondaryEnchants());
                }
            }
            if (path.endsWith("_shovel") || path.endsWith("_hoe")) {
                keys.add(Enchantments.EFFICIENCY); keys.add(Enchantments.UNBREAKING);
                keys.add(Enchantments.MENDING); keys.add(Enchantments.FORTUNE); keys.add(Enchantments.SILK_TOUCH);
            }
            for (var key : keys) lookup.get(key).ifPresent(result::add);
        }
        return result;
    }

    private static final List<Set<ResourceKey<Enchantment>>> INCOMPATIBLE_ENCHANTMENT_GROUPS = List.of(
            Set.of(Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.PROJECTILE_PROTECTION),
            Set.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS),
            Set.of(Enchantments.SILK_TOUCH, Enchantments.FORTUNE),
            Set.of(Enchantments.DEPTH_STRIDER, Enchantments.FROST_WALKER),
            Set.of(Enchantments.INFINITY, Enchantments.MENDING),
            Set.of(Enchantments.MULTISHOT, Enchantments.PIERCING)
    );

    private static boolean areEnchantmentsCompatible(Holder<Enchantment> first, Holder<Enchantment> second) {
        if (first.equals(second)) {
            return false;
        }
        try {
            return Enchantment.areCompatible(first, second);
        } catch (IllegalStateException ignored) {
            // Tags not bound (e.g. offline/unit tests without loaded datapack tags)
            var keyA = first.unwrapKey().orElse(null);
            var keyB = second.unwrapKey().orElse(null);
            if (keyA == null || keyB == null || keyA.equals(keyB)) {
                return false;
            }
            if (keyA.equals(Enchantments.RIPTIDE) && (keyB.equals(Enchantments.LOYALTY) || keyB.equals(Enchantments.CHANNELING))) {
                return false;
            }
            if (keyB.equals(Enchantments.RIPTIDE) && (keyA.equals(Enchantments.LOYALTY) || keyA.equals(Enchantments.CHANNELING))) {
                return false;
            }
            for (Set<ResourceKey<Enchantment>> group : INCOMPATIBLE_ENCHANTMENT_GROUPS) {
                if (group.contains(keyA) && group.contains(keyB)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Epic card: 50% chance → iron tool/armor with 1 random enchant OR diamond tool/armor with 0 enchants.
     *            50% chance → item bundle from EPIC_BUNDLE_POOL, total value ≤ EPIC_BUNDLE_MAX_VALUE.
     * Gear drops are NOT tradeable (VNoTrade=true); bundles ARE tradeable (VNoTrade=false).
     */
    public static ItemStack generateTierEquipment(Random random, boolean exclusive) {
        String tier = exclusive ? "exclusive" : "epic";
        ItemStack stack;
        boolean isGear;

        if (exclusive) {
            isGear = random.nextBoolean();
            stack = isGear
                    ? generateExclusiveGear(random)
                    : generateBundleStack(random, EXCLUSIVE_BUNDLE_POOL, EXCLUSIVE_BUNDLE_MAX_VALUE, tier);
        } else {
            isGear = random.nextBoolean();
            stack = isGear
                    ? generateEpicGear(random)
                    : generateBundleStack(random, EPIC_BUNDLE_POOL, EPIC_BUNDLE_MAX_VALUE, tier);
        }

        // Gear from Black Market cannot be traded; bundles can
        final boolean noTrade = isGear;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString("VCardTier", tier);
            tag.putBoolean("VNoTrade", noTrade);
        });
        stampEquipment(stack);
        return stack;
    }

    /**
     * Epic gear: iron tool/armor + 1 random enchant, OR diamond tool/armor with 0 enchants.
     */
    private static ItemStack generateEpicGear(Random random) {
        boolean ironVariant = random.nextBoolean();
        Item[] ironPool = {
                Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE, Items.IRON_SHOVEL,
                Items.IRON_HOE, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS
        };
        Item[] diamondPool = {
                Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL,
                Items.DIAMOND_HOE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS
        };

        ItemStack stack;
        if (ironVariant) {
            stack = new ItemStack(ironPool[random.nextInt(ironPool.length)]);
            // 1 random enchant, no Fortune/Looting
            var candidates = applicableEnchantments(stack);
            candidates.removeIf(h -> {
                var key = h.unwrapKey().orElse(null);
                return key != null && (key.equals(Enchantments.FORTUNE) || key.equals(Enchantments.LOOTING));
            });
            if (!candidates.isEmpty()) {
                var chosen = candidates.get(random.nextInt(candidates.size()));
                stack.enchant(chosen, 1 + random.nextInt(Math.max(1, chosen.value().getMaxLevel())));
            }
        } else {
            // Diamond, no enchant
            stack = new ItemStack(diamondPool[random.nextInt(diamondPool.length)]);
        }
        return stack;
    }

    /**
     * Exclusive gear: diamond tool/armor with 1-2 enchants, OR netherite tool/armor with 0 enchants.
     */
    private static ItemStack generateExclusiveGear(Random random) {
        Item[] diamondPool = {
                Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL,
                Items.DIAMOND_HOE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS
        };
        Item[] netheritePool = {
                Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE,
                Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE,
                Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS
        };

        boolean diamondVariant = random.nextBoolean();
        ItemStack stack;
        if (diamondVariant) {
            stack = new ItemStack(diamondPool[random.nextInt(diamondPool.length)]);
            // 1-2 enchants, no Fortune/Looting
            var candidates = applicableEnchantments(stack);
            candidates.removeIf(h -> {
                var key = h.unwrapKey().orElse(null);
                return key != null && (key.equals(Enchantments.FORTUNE) || key.equals(Enchantments.LOOTING));
            });
            Collections.shuffle(candidates, random);
            List<Holder<Enchantment>> chosen = new ArrayList<>();
            for (var enchantment : candidates) {
                if (chosen.stream().anyMatch(other -> !areEnchantmentsCompatible(other, enchantment))) continue;
                stack.enchant(enchantment, 1 + random.nextInt(enchantment.value().getMaxLevel()));
                chosen.add(enchantment);
                if (chosen.size() >= (1 + random.nextInt(2))) break; // 1 or 2 enchants
            }
        } else {
            // Netherite, no enchant
            stack = new ItemStack(netheritePool[random.nextInt(netheritePool.length)]);
        }
        return stack;
    }

    /**
     * Generate an ItemStack from the given pool with count 1-30, ensuring total reference value ≤ maxValue.
     * Uses VCoinsPricing.getReferencePrice to enforce the cap.
     */
    private static ItemStack generateBundleStack(Random random, Item[] pool, long maxValue, String tier) {
        VCoinsPricing.ensureInitialized();
        // Shuffle pool and pick a safe item
        List<Item> shuffled = new ArrayList<>(List.of(pool));
        Collections.shuffle(shuffled, random);
        for (Item item : shuffled) {
            ItemStack sample = new ItemStack(item);
            CustomData.update(DataComponents.CUSTOM_DATA, sample, tag -> tag.putString("VCardTier", tier));
            long unitPrice = VCoinsPricing.getReferencePrice(sample);
            if (unitPrice <= 0 || unitPrice > maxValue) continue;
            int maxCount = (int) Math.min(Math.min(30, getCardQuantityLimit(sample)), maxValue / unitPrice);
            if (maxCount < 1) continue;
            int count = 1 + random.nextInt(maxCount);
            return new ItemStack(item, count);
        }
        // Fallback: iron ingot x1
        return new ItemStack(Items.IRON_INGOT, 1);
    }

    public static ItemStack generateMythicItem(Random random) {
        // Reuse the existing relic data contract, but retain an explicit Mythic tier.
        ItemStack stack = generateRomanGodItem(random, 1_000_000_000L);
        long price = 15_001_000_000L + random.nextInt(10_000) * 1_000_000L;
        stack.setDamageValue(0);
        stack.set(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        applicableEnchantments(stack).forEach(h -> stack.enchant(h, 10));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString("VCardTier", "mythic");
            tag.putLong("VCoinsBlackMarketPrice", price);
            tag.putBoolean("VNoTrade", true); // Mythic cannot be traded in any form
        });
        applyMythicPresentation(stack);
        stampEquipment(stack);
        return stack;
    }

    private static void applyMythicPresentation(ItemStack stack) {
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("vcoins.mythic.name", getRomanGodItemComponent(stack))
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("vcoins.mythic.header").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                Component.translatable("vcoins.black_market.free_claim").withStyle(ChatFormatting.GREEN))));
    }

    public static Component getRomanGodItemComponent(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Component.empty();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            var tag = data.copyTag();
            if (tag.contains("VRomanNounKey") && tag.contains("VRomanFigKey")) {
                String nounKey = tag.getString("VRomanNounKey").orElse("");
                String figKey = tag.getString("VRomanFigKey").orElse("");
                if (!nounKey.isEmpty() && !figKey.isEmpty()) {
                    return Component.translatable(nounKey).append(" ").append(Component.translatable(figKey));
                }
            }
        }
        String name = getRomanGodItemName(stack);
        return Component.literal(name);
    }

    /**
     * Pity reward uses the player's Lucky Bar to boost Mystic chance.
     * Base: (1 + luckyPercent)% Mythic, rest Legend.
     */
    public static ItemStack generatePityReward(Random random, int luckyPercent) {
        int mythicChance = 1 + Math.clamp(luckyPercent, 0, MAX_LUCKY_PERCENT);
        if (random.nextInt(100) < mythicChance) return generateMythicItem(random);
        long price = 750_000_000L + random.nextInt(250) * 1_000_000L;
        return generateRomanGodItem(random, price);
    }

    /** Overload for backward compat where luckyPercent is unavailable. */
    public static ItemStack generatePityReward(Random random) {
        return generatePityReward(random, 0);
    }

    /**
     * Roll a single card item with the drop-rate table:
     *   Mythic    0.0125% (1 in 8000)
     *   Legend    0.25% (1 in 400)
     *   Exclusive 10% (1 in 10)
     *   Epic      20% (1 in 5)
     *   Common    69.7375% remaining
     */
    public static ItemStack rollCardItem(Random random, Item fallbackItem) {
        double roll = random.nextDouble();
        if (roll < MYTHIC_ROLL_CHANCE) return generateMythicItem(random);
        if (roll < MYTHIC_ROLL_CHANCE + GOD_ITEM_ROLL_CHANCE) return generateRomanGodItem(random);
        if (roll < MYTHIC_ROLL_CHANCE + GOD_ITEM_ROLL_CHANCE + EXCLUSIVE_ROLL_CHANCE) return generateTierEquipment(random, true);
        if (roll < MYTHIC_ROLL_CHANCE + GOD_ITEM_ROLL_CHANCE + EXCLUSIVE_ROLL_CHANCE + EPIC_ROLL_CHANCE) return generateTierEquipment(random, false);
        List<Item> common = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) if (isCommonCardItem(item)) common.add(item);
        if (common.isEmpty()) throw new IllegalStateException("No eligible common Black Market items");
        ItemStack fallback = new ItemStack(common.get(random.nextInt(common.size())), 30 + random.nextInt(35));
        fallback.setCount(Math.min(fallback.getCount(), getCardQuantityLimit(fallback)));
        stampEquipment(fallback);
        return fallback;
    }

    static boolean isCommonCardItem(Item item) {
        if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")
                || !VCoinsPricing.isTradeable(item) || VCoinsPricing.getBasePrice(BuiltInRegistries.ITEM.getKey(item).toString()) <= 0) return false;
        for (Item high : EPIC_WEAPONS_ARMOR) if (item == high) return false;
        for (Item high : EXCLUSIVE_WEAPONS_ARMOR) if (item == high) return false;
        for (Item high : EPIC_BUNDLE_POOL) if (item == high) return false;
        for (Item high : EXCLUSIVE_BUNDLE_POOL) if (item == high) return false;
        for (var archetype : ROMAN_ARCHETYPES) if (item == archetype.item()) return false;
        return true;
    }

    public static boolean isEquipment(ItemStack stack) {
        return stack.isDamageableItem() || stack.has(DataComponents.EQUIPPABLE);
    }

    /** Reusable utility items are sold individually, even when vanilla allows stacking. */
    static int getCardQuantityLimit(ItemStack stack) {
        if (isEquipment(stack) || stack.is(Items.MAP) || stack.is(Items.FILLED_MAP)
                || stack.is(Items.COMPASS) || stack.is(Items.RECOVERY_COMPASS)
                || stack.is(Items.CLOCK) || stack.is(Items.BUCKET)) return 1;
        return Math.max(1, stack.getMaxStackSize());
    }

    static void stampEquipment(ItemStack stack) {
        if (stack.isEmpty() || !isEquipment(stack)) return;
        String existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getString("VBlackMarketEquipmentId").orElse("");
        String identity = existing.isEmpty() ? UUID.randomUUID().toString() : existing;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean("VNoTrade", true);
            tag.putString("VBlackMarketEquipmentId", identity);
        });
        var lore = new ArrayList<>(stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
        lore.removeIf(line -> line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && (text.getKey().equals("vcoins.black_market.origin") || text.getKey().equals("vcoins.black_market.equipment_id")));
        lore.add(Component.translatable("vcoins.black_market.origin").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        lore.add(Component.translatable("vcoins.black_market.equipment_id", identity).withStyle(ChatFormatting.GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    public static boolean isRomanGodItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("VRomanGodItem").orElse(false);
    }

    public static boolean isBlackMarketItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("VBlackMarketPurchased").orElse(false);
    }

    public static long getRomanGodItemPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("VCoinsBlackMarketPrice")) {
            return data.copyTag().getLong("VCoinsBlackMarketPrice").orElse(0L);
        }
        return 0L;
    }

    public static String getRomanGodItemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("VRomanName")) {
            return data.copyTag().getString("VRomanName").orElse("");
        }
        return stack.getHoverName().getString();
    }

    public static String getRomanGodItemLore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().contains("VRomanLore")) {
            return data.copyTag().getString("VRomanLore").orElse("");
        }
        return "";
    }

    public static boolean hasBeenAnnounced(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("VAnnounced").orElse(false);
    }

    public static void markAnnounced(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("VAnnounced", true));
    }

    public static void broadcastGodItemDiscovery(UUID uuid, ItemStack stack) {
        if (activeServer == null) return;
        ServerPlayer player = activeServer.getPlayerList().getPlayer(uuid);
        String playerName = player != null ? player.getName().getString() : "Player";
        broadcastRelic(playerName, stack, getRomanGodItemPrice(stack), false);
    }

    public static void broadcastGodItemPurchased(ServerPlayer player, ItemStack stack, long cost) {
        broadcastRelic(player.getName().getString(), stack, cost, true);
    }

    private static void broadcastRelic(String playerName, ItemStack stack, long price, boolean purchased) {
        if (activeServer == null) return;
        boolean mythic = isMythicItem(stack);
        Component heading = Component.translatable(mythic
                ? (purchased ? "vcoins.mythic.chat.purchased" : "vcoins.mythic.chat.discovered")
                : (purchased ? "vcoins.roman.chat.purchased" : "vcoins.roman.chat.discovered"))
                .withStyle(ChatFormatting.BOLD)
                .withStyle(style -> style.withColor(purchased ? (mythic ? 0xE8B5FF : 0xE8C989) : (mythic ? 0x7BEBFF : 0xFFD76A)));
        Component actor = Component.translatable(purchased
                ? (mythic ? "vcoins.mythic.chat.buyer" : "vcoins.roman.chat.buyer")
                : (mythic ? "vcoins.mythic.chat.finder" : "vcoins.roman.chat.finder"),
                Component.literal(playerName).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY);
        Component itemLink = Component.literal("[").append(stack.getHoverName()).append("]")
                .withStyle(ChatFormatting.BOLD)
                .withStyle(style -> style.withColor(mythic ? 0xF2B5FF : 0xFFF0C2)
                        .withHoverEvent(stack.getDisplayName().getStyle().getHoverEvent()));
        Component detail = Component.literal(mythic ? "  ✧ " : "  ◆ ")
                .withStyle(mythic ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD).append(itemLink);
        Component value = Component.translatable("vcoins.black_market.free_claim").withStyle(ChatFormatting.GREEN);
        for (ServerPlayer recipient : activeServer.getPlayerList().getPlayers()) {
            recipient.sendSystemMessage(Component.empty().append(heading).append("\n")
                    .append(actor).append("\n").append(detail).append("\n").append(value));
            VTradeScreenHandler.sendSoundToPlayer(recipient,
                    mythic ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.AMETHYST_BLOCK_CHIME,
                    0.45f, purchased ? 1.25f : 0.85f);
        }
    }

    public static int getDiscountPercent(ItemStack stack, long epochDay) {
        return getDiscountPercent(stack, epochDay, 0);
    }

    public static int getDiscountPercent(ItemStack stack, long epochDay, int resetSequence) {
        if (isRomanGodItem(stack)) {
            return 0;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        long seed = (epochDay + resetSequence * 10007L) * 73428767L ^ (id.hashCode() * 912931L);
        return 20 + new Random(seed).nextInt(31);
    }

    public static long getDiscountedPrice(ItemStack stack, long epochDay) {
        return getDiscountedPrice(stack, epochDay, 0);
    }

    /**
     * Strictly independent pricing: black market items are independent of dynamic market volume/cycle waves.
     */
    public static long getDiscountedPrice(ItemStack stack, long epochDay, int resetSequence) {
        if (stack == null || stack.isEmpty()) return 0;
        if (isRomanGodItem(stack) || isMythicItem(stack)) return 0L;

        // Regular black market items use STATIC base reference price without dynamic market multiplier
        long referencePrice = VCoinsPricing.getBlackMarketReferencePrice(stack);
        if (referencePrice <= 0) return 0;
        int discount = getDiscountPercent(stack, epochDay, resetSequence);
        long unit = Math.max(1L, Math.round(referencePrice * ((100 - discount) / 100.0)));
        return unit > Long.MAX_VALUE / stack.getCount() ? Long.MAX_VALUE : unit * stack.getCount();
    }

    public static synchronized List<ItemStack> getDailyItems() {
        checkAndRefreshDaily(false, activeServer);
        return dailyItems;
    }

    public static synchronized List<ItemStack> getItemsForPlayer(UUID uuid) {
        PlayerDailyRecord playerRecord = getPlayerRecord(uuid);
        if (playerRecord.customItems != null && !playerRecord.customItems.isEmpty()) {
            return playerRecord.customItems;
        }
        return getDailyItems();
    }

    public static synchronized long getSecondsUntilReset() {
        // Always use real wall-clock time: reset at midnight UTC (every 24 real hours)
        long nowEpoch = Instant.now().getEpochSecond();
        return Math.max(0L, 86400L - (nowEpoch % 86400L));
    }

    public static synchronized long getCurrentDay() {
        // Real-world epoch day (UTC), not Minecraft in-game day
        return Instant.now().getEpochSecond() / 86400L;
    }

    public static synchronized boolean isTimeAnomalyDetected() {
        return timeAnomalyDetected;
    }

    public static synchronized PlayerDailyRecord getPlayerRecord(UUID uuid) {
        long today = getCurrentDay();
        PlayerDailyRecord playerRecord = playerRecords.get(uuid);
        if (playerRecord == null) {
            playerRecord = new PlayerDailyRecord(today, 0, 0, 0, 0, null, 0, false);
            playerRecords.put(uuid, playerRecord);
        } else if (playerRecord.day != today) {
            // New real-world day: retain banked resets and lifetime flip count, reset daily progress
            playerRecord.day = today;
            playerRecord.revealedMask = 0;
            playerRecord.purchasedMask = 0;
            playerRecord.resetSequence = 0;
            playerRecord.customItems = null;
            // lifetimeFlipCount persists across days intentionally
        }
        return playerRecord;
    }

    public static synchronized int getBankedResets(UUID uuid) {
        return getPlayerRecord(uuid).bankedResets;
    }

    public enum AdminStat { LUCK, PITY, RESETS }

    public static int adminStatLimit(AdminStat stat) {
        return switch (stat) {
            case LUCK -> MAX_LUCKY_PERCENT;
            case PITY -> PITY_THRESHOLD - 1;
            case RESETS -> Integer.MAX_VALUE;
        };
    }

    public static synchronized int getAdminStat(UUID uuid, AdminStat stat) {
        var rec = getPlayerRecord(uuid);
        return switch (stat) {
            case LUCK -> rec.luckyPercent;
            case PITY -> rec.lifetimeFlipCount;
            case RESETS -> rec.bankedResets;
        };
    }

    public static synchronized int changeAdminStat(UUID uuid, AdminStat stat, int value, boolean add) {
        int updated = (int) Math.clamp((add ? (long) getAdminStat(uuid, stat) : 0L) + value,
                0L, (long) adminStatLimit(stat));
        var rec = getPlayerRecord(uuid);
        switch (stat) {
            case LUCK -> {
                rec.luckyPercent = updated;
                rec.luckyFlipCount = updated * LUCKY_STEP_FLIPS / LUCKY_STEP_PERCENT;
            }
            case PITY -> rec.lifetimeFlipCount = updated;
            case RESETS -> rec.bankedResets = updated;
        }
        if (activeServer != null) save(activeServer);
        return updated;
    }

    public static synchronized void addBankedResets(UUID uuid, int count) {
        PlayerDailyRecord playerRecord = getPlayerRecord(uuid);
        playerRecord.bankedResets = (int) Math.clamp((long) playerRecord.bankedResets + count, 0L, (long) Integer.MAX_VALUE);
        if (activeServer != null) {
            save(activeServer);
        }
    }

    /**
     * OP command: guarantee a Roman God item at the player's next successful reset.
     * The god item will be injected into a random slot of their next batch of cards.
     */
    public static synchronized void scheduleLegendGuarantee(ServerPlayer target) {
        PlayerDailyRecord rec = getPlayerRecord(target.getUUID());
        rec.legendNextReset = true;
        rec.mythicNextReset = false;
        if (activeServer != null) save(activeServer);
    }

    public static synchronized void scheduleMythicGuarantee(ServerPlayer target) {
        PlayerDailyRecord rec = getPlayerRecord(target.getUUID());
        rec.mythicNextReset = true;
        rec.legendNextReset = false;
        if (activeServer != null) save(activeServer);
    }



    public static synchronized boolean useBankedReset(ServerPlayer player) {
        PlayerDailyRecord playerRecord = getPlayerRecord(player.getUUID());
        if (playerRecord.bankedResets <= 0) {
            return false;
        }
        playerRecord.bankedResets--;
        playerRecord.resetSequence++;
        playerRecord.revealedMask = 0;
        playerRecord.purchasedMask = 0;

        // Roll dedicated new batch of cards for this player's reset
        long seed = (playerRecord.day * 3123456789L + playerRecord.resetSequence * 10007L + player.getUUID().getMostSignificantBits());
        Random random = new Random(seed);
        List<Item> candidates = new ArrayList<>(List.of(VALUABLE_POOL));
        Collections.shuffle(candidates, random);

        playerRecord.customItems = new ArrayList<>();
        for (int i = 0; i < DAILY_ITEM_COUNT && i < candidates.size(); i++) {
            playerRecord.customItems.add(rollCardItem(random, candidates.get(i)));
        }

        consumeLegendGuarantee(playerRecord);

        // Native sounds
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8f, 1.3f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f);

        if (player.containerMenu instanceof VBlackMarketScreenHandler market) {
            market.refreshMarketSlots();
        }
        syncToPlayer(player);
        if (activeServer != null) {
            save(activeServer);
        }
        return true;
    }

    public static synchronized void adminReset(MinecraftServer server) {
        adminResetCount++;
        long today = getCurrentDay();
        currentEpochDay = today;
        dailyItems.clear();

        long seed = (today * 3123456789L + adminResetCount * 987654321L + 1013904223L);
        Random random = new Random(seed);
        List<Item> candidates = new ArrayList<>(List.of(VALUABLE_POOL));
        Collections.shuffle(candidates, random);

        for (int i = 0; i < DAILY_ITEM_COUNT && i < candidates.size(); i++) {
            dailyItems.add(rollCardItem(random, candidates.get(i)));
        }

        // Reset progress for all players
        for (PlayerDailyRecord dailyRecord : playerRecords.values()) {
            dailyRecord.day = today;
            dailyRecord.revealedMask = 0;
            dailyRecord.purchasedMask = 0;
            dailyRecord.resetSequence = 0;
            dailyRecord.customItems = null;
            consumeLegendGuarantee(dailyRecord);
        }

        if (server != null) {
            save(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.containerMenu instanceof VBlackMarketScreenHandler market) {
                    market.refreshMarketSlots();
                }
                syncToPlayer(player);
            }
        }
    }

    public static synchronized void resetForPlayer(ServerPlayer player) {
        PlayerDailyRecord playerRecord = getPlayerRecord(player.getUUID());
        playerRecord.revealedMask = 0;
        playerRecord.purchasedMask = 0;
        playerRecord.resetSequence++;

        long seed = (playerRecord.day * 3123456789L + playerRecord.resetSequence * 10007L + player.getUUID().getMostSignificantBits());
        Random random = new Random(seed);
        List<Item> candidates = new ArrayList<>(List.of(VALUABLE_POOL));
        Collections.shuffle(candidates, random);

        playerRecord.customItems = new ArrayList<>();
        for (int i = 0; i < DAILY_ITEM_COUNT && i < candidates.size(); i++) {
            playerRecord.customItems.add(rollCardItem(random, candidates.get(i)));
        }

        consumeLegendGuarantee(playerRecord);

        if (player.containerMenu instanceof VBlackMarketScreenHandler market) {
            market.refreshMarketSlots();
        }
        syncToPlayer(player);
        if (activeServer != null) {
            save(activeServer);
        }
    }

    public static synchronized void revealCard(UUID uuid, int slotIndex) {
        if (slotIndex < -1 || slotIndex >= DAILY_ITEM_COUNT) return;
        PlayerDailyRecord playerRecord = getPlayerRecord(uuid);
        int prevRevealedMask = playerRecord.revealedMask;
        int requestedMask = slotIndex == -1 ? (1 << DAILY_ITEM_COUNT) - 1 : 1 << slotIndex;
        if ((requestedMask & ~prevRevealedMask) == 0) return;

        if (slotIndex == -1) {
            // Reveal-all: count how many new cards are revealed for pity
            int newlyRevealed = Integer.bitCount(~prevRevealedMask & ((1 << DAILY_ITEM_COUNT) - 1));
            playerRecord.revealedMask = (1 << DAILY_ITEM_COUNT) - 1;
            playerRecord.lifetimeFlipCount += newlyRevealed;
        } else if (slotIndex >= 0 && slotIndex < DAILY_ITEM_COUNT) {
            boolean wasNew = (prevRevealedMask & (1 << slotIndex)) == 0;
            playerRecord.revealedMask |= (1 << slotIndex);
            if (wasNew) playerRecord.lifetimeFlipCount++;
        }

        // Lucky Bar: count non-legend flips for the lucky step bonus
        List<ItemStack> allItems = getItemsForPlayer(uuid);
        // Count newly revealed slots
        int newlyRevealedCount;
        if (slotIndex == -1) {
            newlyRevealedCount = Integer.bitCount(~prevRevealedMask & ((1 << DAILY_ITEM_COUNT) - 1));
        } else {
            newlyRevealedCount = ((prevRevealedMask & (1 << slotIndex)) == 0) ? 1 : 0;
        }

        // For each newly revealed card, check if it was a legend/mystic to reset lucky bar
        boolean legendDropped = false;
        if (slotIndex == -1) {
            for (int i = 0; i < allItems.size() && i < DAILY_ITEM_COUNT; i++) {
                if ((prevRevealedMask & (1 << i)) == 0) {
                    ItemStack s = allItems.get(i);
                    if (isRomanGodItem(s) || isMythicItem(s)) { legendDropped = true; break; }
                }
            }
        } else if (slotIndex >= 0 && slotIndex < allItems.size()) {
            ItemStack s = allItems.get(slotIndex);
            legendDropped = isRomanGodItem(s) || isMythicItem(s);
        }

        if (legendDropped) {
            playerRecord.lifetimeFlipCount = 0;
            playerRecord.luckyFlipCount = 0;
            playerRecord.luckyPercent = 0;
        } else {
            playerRecord.luckyFlipCount += newlyRevealedCount;
            int newSteps = playerRecord.luckyFlipCount / LUCKY_STEP_FLIPS;
            playerRecord.luckyPercent = Math.clamp(newSteps * LUCKY_STEP_PERCENT, 0, MAX_LUCKY_PERCENT);
        }

        // Pity check: if lifetime flips >= PITY_THRESHOLD, inject guaranteed item
        if (playerRecord.lifetimeFlipCount >= PITY_THRESHOLD) {
            triggerPityGodItem(uuid, playerRecord);
        }

        // Check if any revealed card is a Roman God Item and hasn't been announced yet
        List<ItemStack> items = getItemsForPlayer(uuid);
        for (int i = 0; i < items.size() && i < DAILY_ITEM_COUNT; i++) {
            if ((playerRecord.revealedMask & (1 << i)) != 0) {
                ItemStack stack = items.get(i);
                if (isRomanGodItem(stack) && !hasBeenAnnounced(stack)) {
                    markAnnounced(stack);
                    broadcastGodItemDiscovery(uuid, stack);
                    if (activeServer != null) {
                        save(activeServer);
                    }
                }
            }
        }
        if (activeServer != null) save(activeServer);
    }

    /**
     * Injects a god-tier Roman item into this player's current custom card set as a pity reward.
     * Replaces a random non-purchased, non-god card in the set with a newly generated god item.
     * If the player has no custom items, initialises a fresh set first.
     */
    private static void triggerPityGodItem(UUID uuid, PlayerDailyRecord playerRecord) {
        if (activeServer == null) return;
        ServerPlayer player = activeServer.getPlayerList().getPlayer(uuid);

        if (playerRecord.customItems == null || playerRecord.customItems.isEmpty()) {
            playerRecord.customItems = new ArrayList<>(getDailyItems());
        }

        for (int i = 0; i < playerRecord.customItems.size() && i < DAILY_ITEM_COUNT; i++) {
            if (isRomanGodItem(playerRecord.customItems.get(i)) && (playerRecord.purchasedMask & (1 << i)) == 0) {
                return;
            }
        }

        Random rng = new Random();
        int targetSlot = -1;
        for (int pass = 0; pass < 2; pass++) {
            List<Integer> eligibleSlots = new ArrayList<>();
            for (int i = 0; i < playerRecord.customItems.size() && i < DAILY_ITEM_COUNT; i++) {
                boolean purchased = (playerRecord.purchasedMask & (1 << i)) != 0;
                boolean revealed  = (playerRecord.revealedMask  & (1 << i)) != 0;
                boolean isGod = isRomanGodItem(playerRecord.customItems.get(i));
                if (isGod || purchased) continue;
                if (pass == 0 && revealed) continue; // pass 0: prefer unrevealed
                eligibleSlots.add(i);
            }
            if (!eligibleSlots.isEmpty()) {
                targetSlot = eligibleSlots.get(rng.nextInt(eligibleSlots.size()));
                break;
            }
        }
        if (targetSlot < 0) return;

        ItemStack godItem = generatePityReward(rng, playerRecord.luckyPercent);
        playerRecord.customItems.set(targetSlot, godItem);
        // Consume the guarantee when generated, not when eventually purchased.
        playerRecord.lifetimeFlipCount = 0;
        playerRecord.luckyFlipCount = 0;
        playerRecord.luckyPercent = 0;

        playerRecord.revealedMask &= ~(1 << targetSlot);

        if (player != null) {
            if (player.containerMenu instanceof VBlackMarketScreenHandler market) {
                market.refreshMarketSlots();
            }
            player.sendOverlayMessage(
                Component.translatable("vcoins.black_market.pity_trigger").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
            syncToPlayer(player);
        }
    }

    public static synchronized boolean hasPurchasedToday(UUID uuid, int slotIndex) {
        PlayerDailyRecord playerRecord = getPlayerRecord(uuid);
        return (playerRecord.purchasedMask & (1 << slotIndex)) != 0;
    }

    public static synchronized void markPurchasedToday(UUID uuid, int slotIndex) {
        PlayerDailyRecord playerRecord = getPlayerRecord(uuid);
        if (slotIndex >= 0 && slotIndex < DAILY_ITEM_COUNT) {
            playerRecord.purchasedMask |= (1 << slotIndex);
        }
    }

    /**
     * One paid item per revealed card. Both packet-compatible entry points use
     * the same server-authoritative payment path.
     */
    public static synchronized void buyItem(ServerPlayer player, int slotIndex, boolean buyStack) {
        claimCard(player, slotIndex);
    }

    public static synchronized void claimCard(ServerPlayer player, int slotIndex) {
        if (player == null || slotIndex < 0 || slotIndex >= DAILY_ITEM_COUNT || !player.isAlive() || player.isRemoved()
                || !(player.containerMenu instanceof VBlackMarketScreenHandler)) {
            return;
        }

        List<ItemStack> items = getItemsForPlayer(player.getUUID());
        if (slotIndex >= items.size()) {
            return;
        }

        // Card must be revealed before claiming
        PlayerDailyRecord rec = getPlayerRecord(player.getUUID());
        if ((rec.revealedMask & (1 << slotIndex)) == 0) {
            return;
        }

        if (hasPurchasedToday(player.getUUID(), slotIndex)) {
            player.sendOverlayMessage(Component.translatable("vcoins.black_market.already_claimed")
                    .withStyle(ChatFormatting.RED));
            VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            syncToPlayer(player);
            return;
        }

        ItemStack displayed = items.get(slotIndex);
        if (displayed.isEmpty()) {
            return;
        }

        long price = getDiscountedPrice(displayed, rec.day, rec.resetSequence);
        if (!payForCard(player.getUUID(), slotIndex)) {
            player.sendOverlayMessage(Component.translatable("vcoins.message.not_enough",
                    String.format(java.util.Locale.ROOT, "%,d", price),
                    String.format(java.util.Locale.ROOT, "%,d", VCoinsState.getCoins(player.getUUID())))
                    .withStyle(ChatFormatting.RED));
            VCoinsMod.syncCoins(player);
            return;
        }
        if (player.level().getServer() != null) {
            save(player.level().getServer());
        }

        ItemStack reward = displayed.copy();
        // Only equipment is bound; ordinary materials remain normal trade goods.
        if (isEquipment(reward)) {
            stampEquipment(reward);
            CustomData.update(DataComponents.CUSTOM_DATA, reward, tag -> {
                tag.putBoolean("VBlackMarketPurchased", true);
            });
        }
        while (!reward.isEmpty()) {
            ItemStack part = reward.split(Math.min(reward.getCount(), reward.getMaxStackSize()));
            player.getInventory().placeItemBackInInventory(part, Prediction.SERVER_ONLY);
        }
        VCoinsState.checkpoint(player);
        VCoinsMod.syncCoins(player);

        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.ITEM_PICKUP, 0.9f, 1.25f);
        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 1.75f);
        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.5f);
        player.sendOverlayMessage(Component.translatable("vcoins.black_market.claim_success",
                displayed.getHoverName()).withStyle(ChatFormatting.GREEN));

        if (isRomanGodItem(displayed) || isMythicItem(displayed)) {
            broadcastGodItemPurchased(player, displayed, getRomanGodItemPrice(displayed));
            PlayerDailyRecord claimRec = getPlayerRecord(player.getUUID());
            claimRec.lifetimeFlipCount = 0;
            // Reset lucky bar on legend/mystic claim
            claimRec.luckyFlipCount = 0;
            claimRec.luckyPercent = 0;
        }

        syncToPlayer(player);
    }

    static synchronized boolean payForCard(UUID id, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= DAILY_ITEM_COUNT || timeAnomalyDetected) return false;
        var rec = getPlayerRecord(id);
        int bit = 1 << slotIndex;
        if ((rec.revealedMask & bit) == 0 || (rec.purchasedMask & bit) != 0) return false;
        var items = getItemsForPlayer(id);
        if (slotIndex >= items.size() || items.get(slotIndex).isEmpty()) return false;
        long price = getDiscountedPrice(items.get(slotIndex), rec.day, rec.resetSequence);
        boolean freeRelic = isRomanGodItem(items.get(slotIndex)) || isMythicItem(items.get(slotIndex));
        if (!freeRelic && (price <= 0 || VCoinsState.getCoins(id) < price)) return false;
        if (!freeRelic) VCoinsState.removeCoins(id, price);
        rec.purchasedMask |= bit;
        return true;
    }

    public static void syncToPlayer(ServerPlayer player) {
        VMarketEngine.syncToPlayer(player);
        PlayerDailyRecord playerRecord = getPlayerRecord(player.getUUID());
        long secondsLeft = getSecondsUntilReset();
        List<ItemStack> items = getItemsForPlayer(player.getUUID());
        ServerPlayNetworking.send(player, new BlackMarketSyncPayload(
                secondsLeft, playerRecord.revealedMask, playerRecord.purchasedMask, playerRecord.day,
                playerRecord.bankedResets, playerRecord.resetSequence, items,
                playerRecord.lifetimeFlipCount, playerRecord.luckyPercent
        ));
    }

    public static synchronized void checkAndRefreshDaily(boolean force, MinecraftServer server) {
        long today = Instant.now().getEpochSecond() / 86400L;
        checkAndRefreshDaily(today, force, server);
    }

    private static synchronized void checkAndRefreshDaily(long today, boolean force, MinecraftServer server) {
        long nowEpoch = Instant.now().getEpochSecond();
        long nowNano  = System.nanoTime();

        if (nanoAnchor == 0L) {
            nanoAnchor  = nowNano;
            epochAnchor = nowEpoch;
        }

        long realElapsedSec = (nowNano - nanoAnchor) / 1_000_000_000L;
        long wallElapsedSec = nowEpoch - epochAnchor;
        if (wallElapsedSec > realElapsedSec + FORWARD_TOLERANCE_SEC) {
            timeAnomalyDetected = true;
            VCoinsMod.LOGGER.warn("[VBlackMarket] Clock forward anomaly: wall={}s mono={}s",
                wallElapsedSec, realElapsedSec);
            return;
        }

        if (lastSavedEpochSecond > 0 && nowEpoch < (lastSavedEpochSecond - BACKWARD_TOLERANCE_SEC)) {
            timeAnomalyDetected = true;
            VCoinsMod.LOGGER.warn("[VBlackMarket] Clock rollback detected: saved={} now={}",
                lastSavedEpochSecond, nowEpoch);
            return;
        }

        timeAnomalyDetected = false;
        if (nowEpoch > epochAnchor) {
            nanoAnchor  = nowNano;
            epochAnchor = nowEpoch;
        }
        lastSavedEpochSecond = Math.max(lastSavedEpochSecond, nowEpoch);

        if (!force && today == currentEpochDay && !dailyItems.isEmpty()) {
            return;
        }

        dailyItems.clear();
        currentEpochDay = today;

        long seed = (today * 3123456789L + (long) adminResetCount * 987654321L + 1013904223L);
        Random random = new Random(seed);

        List<Item> candidates = new ArrayList<>(List.of(VALUABLE_POOL));
        Collections.shuffle(candidates, random);

        for (int i = 0; i < DAILY_ITEM_COUNT && i < candidates.size(); i++) {
            dailyItems.add(rollCardItem(random, candidates.get(i)));
        }

        // Apply guarantees for players who will use the shared daily batch (no banked resets).
        // Players with banked resets retain their flag — it will fire when they use a reset,
        // giving them a fresh personal card batch with the god item injected.
        if (server != null) {
            for (UUID uuid : playerRecords.keySet()) {
                PlayerDailyRecord pr = getPlayerRecord(uuid);
                if (pr.bankedResets <= 0) {
                    consumeLegendGuarantee(pr);
                }
            }
        }

        if (server != null) {
            save(server);
        }
    }

    static void consumeLegendGuarantee(PlayerDailyRecord rec) {
        if (!rec.legendNextReset && !rec.mythicNextReset) return;
        applyLegendGuarantee(rec);
        rec.legendNextReset = false;
        rec.mythicNextReset = false;
    }

    private static void applyLegendGuarantee(PlayerDailyRecord rec) {
        if (rec.customItems == null || rec.customItems.isEmpty()) {
            rec.customItems = new ArrayList<>(getDailyItems());
        }
        Random rng = new Random();
        long price = 750_000_000L + (long)(rng.nextDouble() * 250_000_000L);
        price = (price / 1_000_000L) * 1_000_000L;
        ItemStack godItem = rec.mythicNextReset ? generateMythicItem(rng) : generateRomanGodItem(rng, price);
        int targetSlot = rng.nextInt(Math.min(DAILY_ITEM_COUNT, rec.customItems.size()));
        rec.customItems.set(targetSlot, godItem);
        rec.revealedMask &= ~(1 << targetSlot); // let the player flip the chosen card
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> clearWorldState());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            activeServer = server;
            nanoAnchor  = System.nanoTime();
            epochAnchor = Instant.now().getEpochSecond();
            load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            save(server);
            activeServer = null;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearWorldState());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            activeServer = server;
            tickCounter++;
            if (tickCounter % 200 == 0) {
                long nowNano  = System.nanoTime();
                long nowEpoch = Instant.now().getEpochSecond();

                if (nanoAnchor != 0L) {
                    long realElapsed = (nowNano - nanoAnchor) / 1_000_000_000L;
                    long wallElapsed = nowEpoch - epochAnchor;
                    if (wallElapsed > realElapsed + FORWARD_TOLERANCE_SEC) {
                        if (!timeAnomalyDetected) {
                            timeAnomalyDetected = true;
                            VCoinsMod.LOGGER.warn("[VBlackMarket] Tick drift: wall={}s mono={}s",
                                wallElapsed, realElapsed);
                            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                p.sendSystemMessage(Component.literal(
                                    "§c[Veloria] §eChợ Đen tạm khóa do phát hiện chỉnh sửa đồng hồ hệ thống."));
                            }
                        }
                        lastSavedEpochSecond = Math.max(lastSavedEpochSecond, nowEpoch);
                        return;
                    }
                }

                long today = nowEpoch / 86400L;
                if (today != currentEpochDay) {
                    checkAndRefreshDaily(today, false, server);
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        if (player.containerMenu instanceof VBlackMarketScreenHandler market) {
                            market.refreshMarketSlots();
                        }
                        syncToPlayer(player);
                    }
                }
                lastSavedEpochSecond = Math.max(lastSavedEpochSecond, nowEpoch);
            }
        });
    }

    static void clearWorldState() {
        // Item enchantment holders belong to one world's registry lookup.
        dailyItems.clear();
        playerRecords.clear();
        currentEpochDay = 0L;
        lastSavedEpochSecond = 0L;
        adminResetCount = 0;
        timeAnomalyDetected = false;
        tickCounter = 0;
        activeServer = null;
        nanoAnchor = 0L;
        epochAnchor = 0L;
    }

    private static void load(MinecraftServer server) {
        File file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "vcoins_blackmarket.json");
        if (file.exists()) {
            readSaveFile(file);
        }
        checkAndRefreshDaily(false, server);
    }

    private static void readSaveFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<MarketSaveData>(){}.getType();
            MarketSaveData data = GSON.fromJson(reader, type);
            if (data == null) {
                return;
            }
            currentEpochDay = data.day;
            lastSavedEpochSecond = data.lastSavedEpochSecond;
            adminResetCount = data.adminResetCount;
            dailyItems.clear();
            if (data.items != null) {
                populateLoadedEntries(data.items, dailyItems);
            }
            if (data.playerRecords != null) {
                playerRecords.clear();
                for (Map.Entry<String, PlayerRecordData> entry : data.playerRecords.entrySet()) {
                    loadPlayerRecord(entry);
                }
            }
        } catch (IOException e) {
            VCoinsMod.LOGGER.error("Failed to load black market data: {}", e.getMessage());
        }
    }

    private static void loadPlayerRecord(Map.Entry<String, PlayerRecordData> entry) {
        try {
            UUID id = UUID.fromString(entry.getKey());
            PlayerRecordData prd = entry.getValue();
            List<ItemStack> custom = null;
            if (prd.customItems != null && !prd.customItems.isEmpty()) {
                custom = new ArrayList<>();
                populateLoadedEntries(prd.customItems, custom);
            }
            playerRecords.put(id, new PlayerDailyRecord(
                    prd.day, prd.revealedMask, prd.purchasedMask,
                    prd.bankedResets, prd.resetSequence, custom,
                    prd.lifetimeFlipCount, prd.legendNextReset, prd.mythicNextReset,
                    prd.luckyFlipCount, prd.luckyPercent));
        } catch (IllegalArgumentException e) {
            VCoinsMod.LOGGER.warn("Skipping corrupt black market player UUID entry: {}", entry.getKey());
        }
    }

    private static void populateLoadedEntries(List<BlackMarketItemEntry> entries, List<ItemStack> target) {
        if (entries == null) return;
        for (BlackMarketItemEntry entry : entries) {
            ItemStack stack = entry.toItemStack();
            if (!stack.isEmpty()) {
                target.add(stack);
            }
        }
    }

    public static void save(MinecraftServer server) {
        File file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "vcoins_blackmarket.json");
        try (FileWriter writer = new FileWriter(file)) {
            List<BlackMarketItemEntry> itemEntries = new ArrayList<>();
            for (ItemStack stack : dailyItems) {
                itemEntries.add(new BlackMarketItemEntry(stack));
            }

            Map<String, PlayerRecordData> recordMap = new HashMap<>();
            for (Map.Entry<UUID, PlayerDailyRecord> entry : playerRecords.entrySet()) {
                PlayerDailyRecord rec = entry.getValue();
                List<BlackMarketItemEntry> customEntries = null;
                if (rec.customItems != null) {
                    customEntries = new ArrayList<>();
                    for (ItemStack s : rec.customItems) {
                        customEntries.add(new BlackMarketItemEntry(s));
                    }
                }
                recordMap.put(entry.getKey().toString(), new PlayerRecordData(
                        rec.day, rec.revealedMask, rec.purchasedMask,
                        rec.bankedResets, rec.resetSequence, customEntries,
                        rec.lifetimeFlipCount, rec.legendNextReset, rec.mythicNextReset,
                        rec.luckyFlipCount, rec.luckyPercent));
            }

            MarketSaveData data = new MarketSaveData(currentEpochDay, lastSavedEpochSecond, adminResetCount, itemEntries, recordMap);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            VCoinsMod.LOGGER.error("Failed to save black market data: {}", e.getMessage());
        }
    }

    public static class BlackMarketItemEntry {
        public String itemId;
        public int count = 1;
        public String equipmentId;
        public boolean isGodItem;
        public String cardTier;
        public String romanName;
        public String nounKey;
        public String figKey;
        public String romanLore;
        public long godPrice;
        public int damage;
        public boolean broadcasted;
        public Map<String, Integer> enchantments;

        public BlackMarketItemEntry() {}

        public BlackMarketItemEntry(String itemId) {
            this.itemId = itemId;
            this.isGodItem = false;
        }

        public BlackMarketItemEntry(ItemStack stack) {
            this.count = stack.getCount();
            this.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            this.equipmentId = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                    .copyTag().getString("VBlackMarketEquipmentId").orElse(null);
            this.isGodItem = isRomanGodItem(stack);
            this.cardTier = getCardTier(stack);
            if (this.isGodItem || !this.cardTier.isEmpty()) {
                this.romanName = getRomanGodItemName(stack);
                var data = stack.get(DataComponents.CUSTOM_DATA);
                if (data != null) {
                    var tag = data.copyTag();
                    this.nounKey = tag.getString("VRomanNounKey").orElse(null);
                    this.figKey = tag.getString("VRomanFigKey").orElse(null);
                }
                this.romanLore = getRomanGodItemLore(stack);
                this.godPrice = getRomanGodItemPrice(stack);
                this.damage = stack.getDamageValue();
                this.broadcasted = hasBeenAnnounced(stack);
                this.enchantments = new HashMap<>();
                for (var entry : VCoinsPricing.getEnchantmentValues(stack).entrySet()) {
                    this.enchantments.put(entry.getKey(), entry.getValue().level());
                }
            }
        }

        public ItemStack toItemStack() {
            if (itemId == null || itemId.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
            if (item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item, Math.clamp(count, 1, 64));
            // Repair quantities from older saved market cards as well.
            stack.setCount(Math.min(stack.getCount(), getCardQuantityLimit(stack)));
            if (isGodItem) {
                int maxDur = stack.getMaxDamage();
                stack.setDamageValue(damage);
                int remainingDur = maxDur > 0 ? Math.max(0, maxDur - damage) : 0;

                Component nameComp = (nounKey != null && !nounKey.isEmpty() && figKey != null && !figKey.isEmpty())
                        ? Component.translatable(nounKey).append(" ").append(Component.translatable(figKey))
                        : Component.literal(romanName != null ? romanName : "");

                stack.set(DataComponents.CUSTOM_NAME, Component.translatable("vcoins.roman.item_wrapper", nameComp)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

                String loreDesc = (romanLore != null && !romanLore.isEmpty())
                        ? romanLore
                        : "vcoins.roman.lore.0";

                Component loreLineComp = loreDesc.startsWith("vcoins.")
                        ? Component.translatable(loreDesc).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC)
                        : Component.literal(loreDesc).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);

                List<Component> lore = new ArrayList<>();
                lore.add(Component.translatable("vcoins.roman.header").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                lore.add(loreLineComp);
                if (maxDur > 0) {
                    lore.add(Component.translatable("vcoins.roman.durability", remainingDur, maxDur).withStyle(ChatFormatting.GRAY));
                }
                stack.set(DataComponents.LORE, new ItemLore(lore));

                long finalPrice = godPrice;
                String finalName = romanName != null ? romanName : "";
                String finalNounKey = nounKey;
                String finalFigKey = figKey;
                String finalLore = loreDesc;
                boolean finalAnnounced = broadcasted;
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                    tag.putBoolean("VRomanGodItem", true);
                    tag.putLong("VCoinsBlackMarketPrice", finalPrice);
                    tag.putString("VRomanName", finalName);
                    if (finalNounKey != null) tag.putString("VRomanNounKey", finalNounKey);
                    if (finalFigKey != null) tag.putString("VRomanFigKey", finalFigKey);
                    tag.putString("VRomanLore", finalLore);
                    tag.putBoolean("VAnnounced", finalAnnounced);
                });

                if (enchantments != null && !enchantments.isEmpty()) {
                    var lookup = getRegistryLookup().lookupOrThrow(Registries.ENCHANTMENT);
                    for (Map.Entry<String, Integer> e : enchantments.entrySet()) {
                        var holderOpt = lookup.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(e.getKey())));
                        holderOpt.ifPresent(holder -> stack.enchant(holder, e.getValue()));
                    }
                }
            }
            if (cardTier != null && !cardTier.isEmpty()) {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("VCardTier", cardTier));
                if (!isGodItem && enchantments != null) {
                    var lookup = getRegistryLookup().lookupOrThrow(Registries.ENCHANTMENT);
                    enchantments.forEach((id, level) -> lookup.get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(id)))
                            .ifPresent(holder -> stack.enchant(holder, level)));
                }
                if (isMythicItem(stack)) applyMythicPresentation(stack);
            }
            if (equipmentId != null && !equipmentId.isEmpty())
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("VBlackMarketEquipmentId", equipmentId));
            stampEquipment(stack);
            return stack;
        }
    }

    public static class BlackMarketItemEntryDeserializer implements com.google.gson.JsonDeserializer<BlackMarketItemEntry> {
        @Override
        public BlackMarketItemEntry deserialize(com.google.gson.JsonElement json, Type typeOfT, com.google.gson.JsonDeserializationContext context) {
            if (json.isJsonPrimitive()) {
                return new BlackMarketItemEntry(json.getAsString());
            } else if (json.isJsonObject()) {
                com.google.gson.JsonObject obj = json.getAsJsonObject();
                BlackMarketItemEntry entry = new BlackMarketItemEntry();
                entry.itemId = obj.has("itemId") ? obj.get("itemId").getAsString() : "minecraft:air";
                entry.count = obj.has("count") ? Math.clamp(obj.get("count").getAsInt(), 1, 64) : 1;
                entry.equipmentId = obj.has("equipmentId") && !obj.get("equipmentId").isJsonNull()
                        ? obj.get("equipmentId").getAsString() : null;
                entry.cardTier = obj.has("cardTier") ? obj.get("cardTier").getAsString() : "";
                entry.isGodItem = obj.has("isGodItem") && obj.get("isGodItem").getAsBoolean();
                entry.romanName = obj.has("romanName") ? obj.get("romanName").getAsString() : "";
                entry.nounKey = obj.has("nounKey") ? obj.get("nounKey").getAsString() : null;
                entry.figKey = obj.has("figKey") ? obj.get("figKey").getAsString() : null;
                entry.romanLore = obj.has("romanLore") ? obj.get("romanLore").getAsString() : null;
                entry.godPrice = obj.has("godPrice") ? obj.get("godPrice").getAsLong() : 0L;
                entry.damage = obj.has("damage") ? obj.get("damage").getAsInt() : 0;
                entry.broadcasted = obj.has("broadcasted") && obj.get("broadcasted").getAsBoolean();
                if (obj.has("enchantments") && obj.get("enchantments").isJsonObject()) {
                    entry.enchantments = new HashMap<>();
                    for (Map.Entry<String, com.google.gson.JsonElement> e : obj.getAsJsonObject("enchantments").entrySet()) {
                        entry.enchantments.put(e.getKey(), e.getValue().getAsInt());
                    }
                }
                return entry;
            }
            return new BlackMarketItemEntry("minecraft:air");
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(BlackMarketItemEntry.class, new BlackMarketItemEntryDeserializer())
            .setPrettyPrinting()
            .create();

    private record PlayerRecordData(long day, int revealedMask, int purchasedMask, int bankedResets, int resetSequence, List<BlackMarketItemEntry> customItems, int lifetimeFlipCount, boolean legendNextReset, boolean mythicNextReset, int luckyFlipCount, int luckyPercent) {}
    private record MarketSaveData(long day, long lastSavedEpochSecond, int adminResetCount, List<BlackMarketItemEntry> items, Map<String, PlayerRecordData> playerRecords) {}
}
