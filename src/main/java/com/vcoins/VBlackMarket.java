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
    public static final double GOD_ITEM_ROLL_CHANCE = 0.0005; // 0.05% roll rate (1 in 2000)
    public static final int DAILY_ITEM_COUNT = 5; // Exactly 5 cards per day
    /** Pity system: guaranteed Roman God item every this many individual card flips (lifetime, across all days) */
    public static final int PITY_THRESHOLD = 300;
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
        /** OP-scheduled legend guarantee: inject god item on next daily reset */
        public boolean legendNextReset;

        public PlayerDailyRecord(long day, int revealedMask, int purchasedMask, int bankedResets, int resetSequence, List<ItemStack> customItems, int lifetimeFlipCount, boolean legendNextReset) {
            this.day = day;
            this.revealedMask = revealedMask;
            this.purchasedMask = purchasedMask;
            this.bankedResets = bankedResets;
            this.resetSequence = resetSequence;
            this.customItems = customItems != null ? new ArrayList<>(customItems) : null;
            this.lifetimeFlipCount = lifetimeFlipCount;
            this.legendNextReset = legendNextReset;
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
            Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE
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

    public record GeneratedName(Component component, String canonicalViName, String canonicalEnName) {}

    public static GeneratedName generateLocalizedRomanName(Random random, RomanArchetype archetype) {
        RomanNoun noun = archetype.nouns()[random.nextInt(archetype.nouns().length)];
        RomanModifier mod = ROMAN_MODIFIERS[random.nextInt(ROMAN_MODIFIERS.length)];
        RomanFigure fig = ROMAN_FIGURES[random.nextInt(ROMAN_FIGURES.length)];
        RomanSuffix suf = ROMAN_SUFFIXES[random.nextInt(ROMAN_SUFFIXES.length)];

        int pattern = random.nextInt(5);
        Component comp = switch (pattern) {
            case 0 -> Component.translatable("vcoins.roman.pattern.0",
                    Component.translatable(noun.key()),
                    Component.translatable(mod.key()),
                    Component.translatable(fig.key()));
            case 1 -> Component.translatable("vcoins.roman.pattern.1",
                    Component.translatable(noun.key()),
                    Component.translatable(fig.key()));
            case 2 -> Component.translatable("vcoins.roman.pattern.2",
                    Component.translatable(noun.key()),
                    Component.translatable(mod.key()),
                    Component.translatable(suf.key()));
            case 3 -> Component.translatable("vcoins.roman.pattern.3",
                    Component.translatable(noun.key()),
                    Component.translatable(fig.key()),
                    Component.translatable(suf.key()));
            default -> Component.translatable("vcoins.roman.pattern.4",
                    Component.translatable(noun.key()),
                    Component.translatable(mod.key()));
        };

        String vi = switch (pattern) {
            case 0 -> noun.vi() + " " + mod.vi() + " " + fig.vi();
            case 1 -> noun.vi() + " Của " + fig.vi();
            case 2 -> noun.vi() + " " + mod.vi() + ": " + suf.vi();
            case 3 -> noun.vi() + " " + fig.vi() + " (" + suf.vi() + ")";
            default -> noun.vi() + " " + mod.vi();
        };

        String en = switch (pattern) {
            case 0 -> mod.en() + " " + noun.en() + " of " + fig.en();
            case 1 -> noun.en() + " of " + fig.en();
            case 2 -> mod.en() + " " + noun.en() + ": " + suf.en();
            case 3 -> fig.en() + "'s " + noun.en() + " (" + suf.en() + ")";
            default -> mod.en() + " " + noun.en();
        };

        return new GeneratedName(comp, vi, en);
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
        lore.add(Component.translatable("vcoins.roman.price", String.format(Locale.ROOT, "%,d", price)).withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        // Apply Enchantments
        var lookup = getRegistryLookup().lookupOrThrow(Registries.ENCHANTMENT);

        List<ResourceKey<Enchantment>> enchantsToApply = new ArrayList<>(archetype.primaryEnchants());
        int extraCount = (int) Math.round(powerRatio * archetype.secondaryEnchants().size());
        for (int i = 0; i < extraCount && i < archetype.secondaryEnchants().size(); i++) {
            enchantsToApply.add(archetype.secondaryEnchants().get(i));
        }

        for (ResourceKey<Enchantment> key : enchantsToApply) {
            var holderOpt = lookup.get(key);
            if (holderOpt.isPresent()) {
                var holder = holderOpt.get();
                int maxLevel = holder.value().getMaxLevel();
                int level;
                if (maxLevel <= 1) {
                    level = 1;
                } else if (powerRatio >= 0.85) {
                    level = maxLevel;
                } else {
                    level = Math.max(1, (int) Math.round(maxLevel * (0.60 + 0.40 * powerRatio)));
                }
                stack.enchant(holder, level);
            }
        }

        // Tag custom data
        long finalPrice = price;
        String finalName = generatedName.canonicalViName();
        String finalLoreKey = loreKey;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean("VRomanGodItem", true);
            tag.putLong("VCoinsBlackMarketPrice", finalPrice);
            tag.putString("VRomanName", finalName);
            tag.putString("VRomanLore", finalLoreKey);
            tag.putBoolean("VAnnounced", false);
        });

        return stack;
    }

    public static ItemStack rollCardItem(Random random, Item fallbackItem) {
        if (random.nextDouble() < GOD_ITEM_ROLL_CHANCE) {
            return generateRomanGodItem(random);
        }
        return new ItemStack(fallbackItem);
    }

    public static boolean isRomanGodItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("VRomanGodItem").orElse(false);
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
        String playerName = player != null ? player.getName().getString() : "Người chơi";
        long price = getRomanGodItemPrice(stack);
        int remainingDurability = stack.getMaxDamage() > 0 ? stack.getMaxDamage() - stack.getDamageValue() : 0;
        int maxDurability = stack.getMaxDamage();

        Component separator = Component.literal("§6§l╔════════════════════════════════════════════════╗");
        Component header = Component.translatable("vcoins.roman.broadcast.discovery.header");
        Component playerInfo = Component.translatable("vcoins.roman.broadcast.discovery.player", playerName);
        Component itemNameComp = Component.translatable("vcoins.roman.item_wrapper", stack.getHoverName());
        Component statsComp = Component.translatable("vcoins.roman.broadcast.discovery.stats",
                String.format(Locale.ROOT, "%,d", price),
                maxDurability > 0 ? Component.translatable("vcoins.roman.broadcast.discovery.durability", remainingDurability, maxDurability) : Component.empty());
        Component loreComp = Component.translatable("vcoins.roman.broadcast.discovery.lore");
        Component footer = Component.literal("§6§l╚════════════════════════════════════════════════╝");

        for (ServerPlayer p : activeServer.getPlayerList().getPlayers()) {
            p.sendSystemMessage(separator);
            p.sendSystemMessage(header);
            p.sendSystemMessage(playerInfo);
            p.sendSystemMessage(itemNameComp);
            p.sendSystemMessage(statsComp);
            p.sendSystemMessage(loreComp);
            p.sendSystemMessage(footer);

            VTradeScreenHandler.sendSoundToPlayer(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            VTradeScreenHandler.sendSoundToPlayer(p, SoundEvents.PLAYER_LEVELUP, 1.0f, 0.8f);
        }
    }

    public static void broadcastGodItemPurchased(ServerPlayer player, ItemStack stack, long cost) {
        if (activeServer == null) return;
        String playerName = player.getName().getString();

        Component separator = Component.literal("§6§l╔════════════════════════════════════════════════╗");
        Component title = Component.translatable("vcoins.roman.broadcast.purchased.title");
        Component body = Component.translatable("vcoins.roman.broadcast.purchased.body", playerName, String.format(Locale.ROOT, "%,d", cost));
        Component itemComp = Component.translatable("vcoins.roman.broadcast.purchased.item", stack.getHoverName());
        Component lore = Component.translatable("vcoins.roman.broadcast.purchased.lore");
        Component footer = Component.literal("§6§l╚════════════════════════════════════════════════╝");

        for (ServerPlayer p : activeServer.getPlayerList().getPlayers()) {
            p.sendSystemMessage(separator);
            p.sendSystemMessage(title);
            p.sendSystemMessage(body);
            p.sendSystemMessage(itemComp);
            p.sendSystemMessage(lore);
            p.sendSystemMessage(footer);

            VTradeScreenHandler.sendSoundToPlayer(p, SoundEvents.TOTEM_USE, 0.9f, 1.0f);
            VTradeScreenHandler.sendSoundToPlayer(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
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
        long godPrice = getRomanGodItemPrice(stack);
        if (godPrice > 0) {
            return godPrice;
        }

        // Regular black market items use STATIC base reference price without dynamic market multiplier
        long referencePrice = VCoinsPricing.getReferencePrice(stack);
        if (referencePrice <= 0) return 0;
        int discount = getDiscountPercent(stack, epochDay, resetSequence);
        return Math.max(1L, Math.round(referencePrice * ((100 - discount) / 100.0)));
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

    public static synchronized void addBankedResets(UUID uuid, int count) {
        PlayerDailyRecord playerRecord = getPlayerRecord(uuid);
        playerRecord.bankedResets = Math.max(0, playerRecord.bankedResets + count);
        if (activeServer != null) {
            save(activeServer);
        }
    }

    /**
     * OP command: guarantee a Roman God item at the player's next daily reset.
     * The god item will be injected into slot 0 of their custom items when the day rolls over.
     */
    public static synchronized void scheduleLegendGuarantee(ServerPlayer target) {
        PlayerDailyRecord rec = getPlayerRecord(target.getUUID());
        rec.legendNextReset = true;
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

        if (player.containerMenu instanceof VBlackMarketScreenHandler market) {
            market.refreshMarketSlots();
        }
        syncToPlayer(player);
        if (activeServer != null) {
            save(activeServer);
        }
    }

    public static synchronized void revealCard(UUID uuid, int slotIndex) {
        PlayerDailyRecord playerRecord = getPlayerRecord(uuid);
        int prevRevealedMask = playerRecord.revealedMask;

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

        // Pity check: if lifetime flips >= PITY_THRESHOLD, inject guaranteed god item
        if (playerRecord.lifetimeFlipCount >= PITY_THRESHOLD) {
            playerRecord.lifetimeFlipCount = 0;
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
     * Replaces the first non-purchased, non-god card in the set with a newly generated god item.
     * If the player has no custom items, initialises a fresh set first.
     */
    private static void triggerPityGodItem(UUID uuid, PlayerDailyRecord playerRecord) {
        if (activeServer == null) return;
        ServerPlayer player = activeServer.getPlayerList().getPlayer(uuid);

        if (playerRecord.customItems == null || playerRecord.customItems.isEmpty()) {
            playerRecord.customItems = new ArrayList<>(getDailyItems());
        }

        Random rng = new Random();
        int targetSlot = -1;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < playerRecord.customItems.size() && i < DAILY_ITEM_COUNT; i++) {
                boolean purchased = (playerRecord.purchasedMask & (1 << i)) != 0;
                boolean revealed  = (playerRecord.revealedMask  & (1 << i)) != 0;
                boolean isGod = isRomanGodItem(playerRecord.customItems.get(i));
                if (isGod || purchased) continue;
                if (pass == 0 && revealed) continue; // pass 0: prefer unrevealed
                targetSlot = i;
                break;
            }
            if (targetSlot >= 0) break;
        }
        if (targetSlot < 0) return;

        long pityPrice = 750_000_000L + (long)(rng.nextDouble() * 250_000_000L);
        pityPrice = (pityPrice / 1_000_000L) * 1_000_000L;
        ItemStack godItem = generateRomanGodItem(rng, pityPrice);
        playerRecord.customItems.set(targetSlot, godItem);

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

    public static synchronized void buyItem(ServerPlayer player, int slotIndex, boolean buyStack) {
        if (player == null || slotIndex < 0 || slotIndex >= DAILY_ITEM_COUNT || !player.isAlive() || player.isRemoved()) {
            return;
        }

        List<ItemStack> items = getItemsForPlayer(player.getUUID());
        if (slotIndex >= items.size()) {
            return;
        }

        if (hasPurchasedToday(player.getUUID(), slotIndex)) {
            player.sendOverlayMessage(Component.translatable("vcoins.black_market.already_bought")
                    .withStyle(ChatFormatting.RED));
            VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            syncToPlayer(player);
            return;
        }

        ItemStack displayed = items.get(slotIndex);
        if (displayed.isEmpty()) {
            return;
        }

        int resetSequence = getPlayerRecord(player.getUUID()).resetSequence;
        long unitPrice = getDiscountedPrice(displayed, getCurrentDay(), resetSequence);
        if (unitPrice <= 0) {
            return;
        }

        int amount = 1;
        long totalCost = unitPrice;
        long currentCoins = VCoinsState.getCoins(player.getUUID());

        if (currentCoins < totalCost) {
            player.sendOverlayMessage(Component.translatable("vcoins.message.not_enough",
                    String.format(Locale.ROOT, "%,d", totalCost), String.format(Locale.ROOT, "%,d", currentCoins)).withStyle(ChatFormatting.RED));
            VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        VCoinsState.removeCoins(player.getUUID(), totalCost);
        VCoinsMod.syncCoins(player);

        markPurchasedToday(player.getUUID(), slotIndex);
        if (player.level().getServer() != null) {
            save(player.level().getServer());
        }

        ItemStack purchased = displayed.copy();
        purchased.setCount(amount);
        player.getInventory().placeItemBackInInventory(purchased, Prediction.SERVER_ONLY);

        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.ITEM_PICKUP, 0.9f, 1.25f);
        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.NOTE_BLOCK_CHIME, 0.6f, 1.75f);
        VTradeScreenHandler.sendSoundToPlayer(player, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.5f);
        player.sendOverlayMessage(Component.translatable("vcoins.message.buy_success",
                amount, displayed.getHoverName(), String.format(Locale.ROOT, "%,d", totalCost)).withStyle(ChatFormatting.GREEN));

        if (isRomanGodItem(displayed)) {
            broadcastGodItemPurchased(player, displayed, totalCost);
        }

        syncToPlayer(player);
    }

    public static void syncToPlayer(ServerPlayer player) {
        VMarketEngine.syncToPlayer(player);
        PlayerDailyRecord playerRecord = getPlayerRecord(player.getUUID());
        long secondsLeft = getSecondsUntilReset();
        List<ItemStack> items = getItemsForPlayer(player.getUUID());
        ServerPlayNetworking.send(player, new BlackMarketSyncPayload(
                secondsLeft, playerRecord.revealedMask, playerRecord.purchasedMask, playerRecord.day,
                playerRecord.bankedResets, playerRecord.resetSequence, items,
                playerRecord.lifetimeFlipCount
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

        // Apply any pending legend guarantees for online players
        if (server != null) {
            for (PlayerDailyRecord rec : playerRecords.values()) {
                if (rec.legendNextReset) {
                    rec.legendNextReset = false;
                    applyLegendGuarantee(rec);
                }
            }
        }

        if (server != null) {
            save(server);
        }
    }

    private static void applyLegendGuarantee(PlayerDailyRecord rec) {
        if (rec.customItems == null || rec.customItems.isEmpty()) {
            rec.customItems = new ArrayList<>(getDailyItems());
        }
        Random rng = new Random();
        long price = 750_000_000L + (long)(rng.nextDouble() * 250_000_000L);
        price = (price / 1_000_000L) * 1_000_000L;
        ItemStack godItem = generateRomanGodItem(rng, price);
        // Replace slot 0 (guaranteed visible)
        rec.customItems.set(0, godItem);
        rec.revealedMask &= ~1; // un-reveal slot 0 so the player flips it themselves
    }

    public static void registerEvents() {
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
                    prd.lifetimeFlipCount, prd.legendNextReset));
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
                        rec.lifetimeFlipCount, rec.legendNextReset));
            }

            MarketSaveData data = new MarketSaveData(currentEpochDay, lastSavedEpochSecond, adminResetCount, itemEntries, recordMap);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            VCoinsMod.LOGGER.error("Failed to save black market data: {}", e.getMessage());
        }
    }

    public static class BlackMarketItemEntry {
        public String itemId;
        public boolean isGodItem;
        public String romanName;
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
            this.itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            this.isGodItem = isRomanGodItem(stack);
            if (this.isGodItem) {
                this.romanName = getRomanGodItemName(stack);
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
            ItemStack stack = new ItemStack(item);
            if (isGodItem) {
                int maxDur = stack.getMaxDamage();
                stack.setDamageValue(damage);
                int remainingDur = maxDur > 0 ? Math.max(0, maxDur - damage) : 0;

                stack.set(DataComponents.CUSTOM_NAME, Component.translatable("vcoins.roman.item_wrapper", Component.literal(romanName))
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
                lore.add(Component.translatable("vcoins.roman.price", String.format(Locale.ROOT, "%,d", godPrice)).withStyle(ChatFormatting.GOLD));
                stack.set(DataComponents.LORE, new ItemLore(lore));

                long finalPrice = godPrice;
                String finalName = romanName;
                String finalLore = loreDesc;
                boolean finalAnnounced = broadcasted;
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                    tag.putBoolean("VRomanGodItem", true);
                    tag.putLong("VCoinsBlackMarketPrice", finalPrice);
                    tag.putString("VRomanName", finalName);
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
                entry.isGodItem = obj.has("isGodItem") && obj.get("isGodItem").getAsBoolean();
                entry.romanName = obj.has("romanName") ? obj.get("romanName").getAsString() : "";
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

    private record PlayerRecordData(long day, int revealedMask, int purchasedMask, int bankedResets, int resetSequence, List<BlackMarketItemEntry> customItems, int lifetimeFlipCount, boolean legendNextReset) {}
    private record MarketSaveData(long day, long lastSavedEpochSecond, int adminResetCount, List<BlackMarketItemEntry> items, Map<String, PlayerRecordData> playerRecords) {}
}
