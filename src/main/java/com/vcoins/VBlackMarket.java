package com.vcoins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int DAILY_ITEM_COUNT = 5; // Exactly 5 cards per day
    private static final List<ItemStack> dailyItems = new ArrayList<>();
    
    private static long currentEpochDay = 0L;
    private static long lastSavedEpochSecond = 0L;
    private static boolean timeAnomalyDetected = false;
    private static int tickCounter = 0;

    public static class PlayerDailyRecord {
        public long day;
        public int revealedMask;
        public int purchasedMask;

        public PlayerDailyRecord(long day, int revealedMask, int purchasedMask) {
            this.day = day;
            this.revealedMask = revealedMask;
            this.purchasedMask = purchasedMask;
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

    public static synchronized List<ItemStack> getDailyItems() {
        checkAndRefreshDaily(false, null);
        return dailyItems;
    }

    public static synchronized long getSecondsUntilReset() {
        long nowEpoch = Instant.now().getEpochSecond();
        return Math.max(0L, 86400L - (nowEpoch % 86400L));
    }

    public static synchronized long getCurrentDay() {
        return Instant.now().getEpochSecond() / 86400L;
    }

    public static synchronized boolean isTimeAnomalyDetected() {
        return timeAnomalyDetected;
    }

    public static synchronized PlayerDailyRecord getPlayerRecord(UUID uuid) {
        long today = getCurrentDay();
        PlayerDailyRecord record = playerRecords.get(uuid);
        if (record == null || record.day != today) {
            record = new PlayerDailyRecord(today, 0, 0);
            playerRecords.put(uuid, record);
        }
        return record;
    }

    public static synchronized void revealCard(UUID uuid, int slotIndex) {
        PlayerDailyRecord record = getPlayerRecord(uuid);
        if (slotIndex == -1) {
            record.revealedMask = (1 << DAILY_ITEM_COUNT) - 1;
        } else if (slotIndex >= 0 && slotIndex < DAILY_ITEM_COUNT) {
            record.revealedMask |= (1 << slotIndex);
        }
    }

    public static synchronized boolean hasPurchasedToday(UUID uuid, int slotIndex) {
        PlayerDailyRecord record = getPlayerRecord(uuid);
        return (record.purchasedMask & (1 << slotIndex)) != 0;
    }

    public static synchronized void markPurchasedToday(UUID uuid, int slotIndex) {
        PlayerDailyRecord record = getPlayerRecord(uuid);
        if (slotIndex >= 0 && slotIndex < DAILY_ITEM_COUNT) {
            record.purchasedMask |= (1 << slotIndex);
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        PlayerDailyRecord record = getPlayerRecord(player.getUUID());
        long secondsLeft = getSecondsUntilReset();
        ServerPlayNetworking.send(player, new BlackMarketSyncPayload(
                secondsLeft, record.revealedMask, record.purchasedMask, record.day
        ));
    }

    /**
     * Anti-time-exploit daily validation and refresh.
     * Guaranteed deterministic seed per UTC day + rollback detection.
     */
    public static synchronized void checkAndRefreshDaily(boolean force, MinecraftServer server) {
        long nowEpoch = Instant.now().getEpochSecond();
        long today = nowEpoch / 86400L;

        // Anti-rollback check: if clock went backwards by more than 60 seconds
        if (lastSavedEpochSecond > 0 && nowEpoch < (lastSavedEpochSecond - 60)) {
            timeAnomalyDetected = true;
            VCoinsMod.LOGGER.warn("Time anomaly detected! System clock was moved backwards from {} to {}. Black market refresh locked.",
                    lastSavedEpochSecond, nowEpoch);
            return;
        }

        timeAnomalyDetected = false;
        lastSavedEpochSecond = Math.max(lastSavedEpochSecond, nowEpoch);

        if (!force && today == currentEpochDay && !dailyItems.isEmpty()) {
            return;
        }

        dailyItems.clear();
        currentEpochDay = today;

        // Deterministic generation per UTC day (resistant to reboots or tampering)
        long seed = (today * 3123456789L + 1013904223L);
        Random random = new Random(seed);

        List<Item> candidates = new ArrayList<>(List.of(VALUABLE_POOL));
        Collections.shuffle(candidates, random);

        for (int i = 0; i < DAILY_ITEM_COUNT && i < candidates.size(); i++) {
            dailyItems.add(new ItemStack(candidates.get(i)));
        }

        if (server != null) {
            save(server);
        }
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> load(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save(server));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter % 100 == 0) { // Check every 5 seconds
                long nowEpoch = Instant.now().getEpochSecond();
                long today = nowEpoch / 86400L;
                if (today != currentEpochDay) {
                    checkAndRefreshDaily(false, server);
                    // Update connected black market screens
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        if (player.containerMenu instanceof VBlackMarketScreenHandler market) {
                            market.refreshMarketSlots();
                            syncToPlayer(player);
                        }
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
            if (data != null) {
                currentEpochDay = data.day;
                lastSavedEpochSecond = data.lastSavedEpochSecond;
                dailyItems.clear();
                if (data.items != null) {
                    populateLoadedItems(data.items);
                }
                if (data.playerRecords != null) {
                    playerRecords.clear();
                    for (Map.Entry<String, PlayerRecordData> entry : data.playerRecords.entrySet()) {
                        try {
                            UUID id = UUID.fromString(entry.getKey());
                            PlayerRecordData prd = entry.getValue();
                            playerRecords.put(id, new PlayerDailyRecord(prd.day, prd.revealedMask, prd.purchasedMask));
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (IOException e) {
            VCoinsMod.LOGGER.error("Failed to load black market data: {}", e.getMessage());
        }
    }

    private static void populateLoadedItems(List<String> items) {
        for (String itemId : items) {
            for (Item regItem : BuiltInRegistries.ITEM) {
                if (BuiltInRegistries.ITEM.getKey(regItem).toString().equals(itemId)) {
                    if (regItem != Items.AIR) {
                        dailyItems.add(new ItemStack(regItem));
                    }
                    break;
                }
            }
        }
    }

    public static void save(MinecraftServer server) {
        File file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "vcoins_blackmarket.json");
        try (FileWriter writer = new FileWriter(file)) {
            List<String> itemIds = new ArrayList<>();
            for (ItemStack stack : dailyItems) {
                itemIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }

            Map<String, PlayerRecordData> recordMap = new HashMap<>();
            for (Map.Entry<UUID, PlayerDailyRecord> entry : playerRecords.entrySet()) {
                PlayerDailyRecord rec = entry.getValue();
                recordMap.put(entry.getKey().toString(), new PlayerRecordData(rec.day, rec.revealedMask, rec.purchasedMask));
            }

            MarketSaveData data = new MarketSaveData(currentEpochDay, lastSavedEpochSecond, itemIds, recordMap);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            VCoinsMod.LOGGER.error("Failed to save black market data: {}", e.getMessage());
        }
    }

    private record PlayerRecordData(long day, int revealedMask, int purchasedMask) {}
    private record MarketSaveData(long day, long lastSavedEpochSecond, List<String> items, Map<String, PlayerRecordData> playerRecords) {}
}
