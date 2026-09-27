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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
    private static int adminResetCount = 0;
    private static boolean timeAnomalyDetected = false;
    private static int tickCounter = 0;
    private static MinecraftServer activeServer = null;

    public static class PlayerDailyRecord {
        public long day;
        public int revealedMask;
        public int purchasedMask;
        public int bankedResets;
        public int resetSequence;
        public List<ItemStack> customItems;

        public PlayerDailyRecord(long day, int revealedMask, int purchasedMask, int bankedResets, int resetSequence, List<ItemStack> customItems) {
            this.day = day;
            this.revealedMask = revealedMask;
            this.purchasedMask = purchasedMask;
            this.bankedResets = bankedResets;
            this.resetSequence = resetSequence;
            this.customItems = customItems != null ? new ArrayList<>(customItems) : null;
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

    public static boolean isValuableItem(Item item) {
        for (Item valuable : VALUABLE_POOL) {
            if (valuable == item) return true;
        }
        return false;
    }

    public static int getDiscountPercent(ItemStack stack, long epochDay) {
        return getDiscountPercent(stack, epochDay, 0);
    }

    public static int getDiscountPercent(ItemStack stack, long epochDay, int resetSequence) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        long seed = (epochDay + (long) resetSequence * 10007L) * 73428767L ^ ((long) id.hashCode() * 912931L);
        return 20 + new Random(seed).nextInt(31);
    }

    public static long getDiscountedPrice(ItemStack stack, long epochDay) {
        return getDiscountedPrice(stack, epochDay, 0);
    }

    public static long getDiscountedPrice(ItemStack stack, long epochDay, int resetSequence) {
        long marketPrice = VCoinsPricing.getPrice(stack);
        if (marketPrice <= 0) return 0;
        return Math.max(1L, Math.round(marketPrice * ((100 - getDiscountPercent(stack, epochDay, resetSequence)) / 100.0)));
    }

    public static synchronized List<ItemStack> getDailyItems() {
        checkAndRefreshDaily(false, activeServer);
        return dailyItems;
    }

    public static synchronized List<ItemStack> getItemsForPlayer(UUID uuid) {
        PlayerDailyRecord record = getPlayerRecord(uuid);
        if (record.customItems != null && !record.customItems.isEmpty()) {
            return record.customItems;
        }
        return getDailyItems();
    }

    public static synchronized long getSecondsUntilReset() {
        if (activeServer != null && activeServer.overworld() != null) {
            long dayTicks = activeServer.overworld().getDayTime() % 24000L;
            return Math.max(0L, (24000L - dayTicks) / 20L);
        }
        long nowEpoch = Instant.now().getEpochSecond();
        return Math.max(0L, 86400L - (nowEpoch % 86400L));
    }

    public static synchronized long getCurrentDay() {
        if (activeServer != null && activeServer.overworld() != null) {
            return activeServer.overworld().getDayTime() / 24000L;
        }
        return currentEpochDay > 0 ? currentEpochDay : Instant.now().getEpochSecond() / 86400L;
    }

    public static synchronized boolean isTimeAnomalyDetected() {
        return timeAnomalyDetected;
    }

    public static synchronized PlayerDailyRecord getPlayerRecord(UUID uuid) {
        long today = getCurrentDay();
        PlayerDailyRecord record = playerRecords.get(uuid);
        if (record == null) {
            record = new PlayerDailyRecord(today, 0, 0, 0, 0, null);
            playerRecords.put(uuid, record);
        } else if (record.day != today) {
            // New Minecraft day: retain banked resets, reset daily progress
            record.day = today;
            record.revealedMask = 0;
            record.purchasedMask = 0;
            record.resetSequence = 0;
            record.customItems = null;
        }
        return record;
    }

    public static synchronized int getBankedResets(UUID uuid) {
        return getPlayerRecord(uuid).bankedResets;
    }

    public static synchronized void addBankedResets(UUID uuid, int count) {
        PlayerDailyRecord record = getPlayerRecord(uuid);
        record.bankedResets = Math.max(0, record.bankedResets + count);
        if (activeServer != null) {
            save(activeServer);
        }
    }

    public static synchronized boolean useBankedReset(ServerPlayer player) {
        PlayerDailyRecord record = getPlayerRecord(player.getUUID());
        if (record.bankedResets <= 0) {
            return false;
        }
        record.bankedResets--;
        record.resetSequence++;
        record.revealedMask = 0;
        record.purchasedMask = 0;

        // Roll dedicated new batch of cards for this player's reset
        long seed = (record.day * 3123456789L + (long) record.resetSequence * 10007L + player.getUUID().getMostSignificantBits());
        Random random = new Random(seed);
        List<Item> candidates = new ArrayList<>(List.of(VALUABLE_POOL));
        Collections.shuffle(candidates, random);

        record.customItems = new ArrayList<>();
        for (int i = 0; i < DAILY_ITEM_COUNT && i < candidates.size(); i++) {
            record.customItems.add(new ItemStack(candidates.get(i)));
        }

        // Native sounds
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.2f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8f, 1.3f);
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

        long seed = (today * 3123456789L + (long) adminResetCount * 987654321L + 1013904223L);
        Random random = new Random(seed);
        List<Item> candidates = new ArrayList<>(List.of(VALUABLE_POOL));
        Collections.shuffle(candidates, random);

        for (int i = 0; i < DAILY_ITEM_COUNT && i < candidates.size(); i++) {
            dailyItems.add(new ItemStack(candidates.get(i)));
        }

        // Reset progress for all players
        for (PlayerDailyRecord rec : playerRecords.values()) {
            rec.day = today;
            rec.revealedMask = 0;
            rec.purchasedMask = 0;
            rec.resetSequence = 0;
            rec.customItems = null;
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
        PlayerDailyRecord record = getPlayerRecord(player.getUUID());
        record.revealedMask = 0;
        record.purchasedMask = 0;
        record.resetSequence++;

        long seed = (record.day * 3123456789L + (long) record.resetSequence * 10007L + player.getUUID().getMostSignificantBits());
        Random random = new Random(seed);
        List<Item> candidates = new ArrayList<>(List.of(VALUABLE_POOL));
        Collections.shuffle(candidates, random);

        record.customItems = new ArrayList<>();
        for (int i = 0; i < DAILY_ITEM_COUNT && i < candidates.size(); i++) {
            record.customItems.add(new ItemStack(candidates.get(i)));
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
        VMarketEngine.syncToPlayer(player);
        PlayerDailyRecord record = getPlayerRecord(player.getUUID());
        long secondsLeft = getSecondsUntilReset();
        ServerPlayNetworking.send(player, new BlackMarketSyncPayload(
                secondsLeft, record.revealedMask, record.purchasedMask, record.day,
                record.bankedResets, record.resetSequence
        ));
    }

    /**
     * Anti-time-exploit daily validation and refresh.
     * Guaranteed deterministic seed per Minecraft in-game day + rollback detection.
     */
    public static synchronized void checkAndRefreshDaily(boolean force, MinecraftServer server) {
        long inGameDay = (server != null && server.overworld() != null)
                ? server.overworld().getDayTime() / 24000L
                : (Instant.now().getEpochSecond() / 86400L);

        checkAndRefreshDaily(inGameDay, force, server);
    }

    private static synchronized void checkAndRefreshDaily(long today, boolean force, MinecraftServer server) {
        long nowEpoch = Instant.now().getEpochSecond();

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

        // Deterministic generation per Minecraft in-game day (and admin resets)
        long seed = (today * 3123456789L + (long) adminResetCount * 987654321L + 1013904223L);
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
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            activeServer = server;
            load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            save(server);
            activeServer = null;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            activeServer = server;
            tickCounter++;
            if (tickCounter % 20 == 0) { // Check every second for in-game day changes
                long inGameDay = server.overworld().getDayTime() / 24000L;
                if (inGameDay != currentEpochDay) {
                    checkAndRefreshDaily(inGameDay, false, server);
                    // Update connected black market screens
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        if (player.containerMenu instanceof VBlackMarketScreenHandler market) {
                            market.refreshMarketSlots();
                        }
                        syncToPlayer(player);
                    }
                }
                lastSavedEpochSecond = Math.max(lastSavedEpochSecond, Instant.now().getEpochSecond());
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
                adminResetCount = data.adminResetCount;
                dailyItems.clear();
                if (data.items != null) {
                    populateLoadedItems(data.items, dailyItems);
                }
                if (data.playerRecords != null) {
                    playerRecords.clear();
                    for (Map.Entry<String, PlayerRecordData> entry : data.playerRecords.entrySet()) {
                        try {
                            UUID id = UUID.fromString(entry.getKey());
                            PlayerRecordData prd = entry.getValue();
                            List<ItemStack> custom = null;
                            if (prd.customItems != null && !prd.customItems.isEmpty()) {
                                custom = new ArrayList<>();
                                populateLoadedItems(prd.customItems, custom);
                            }
                            playerRecords.put(id, new PlayerDailyRecord(
                                    prd.day, prd.revealedMask, prd.purchasedMask,
                                    prd.bankedResets, prd.resetSequence, custom));
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (IOException e) {
            VCoinsMod.LOGGER.error("Failed to load black market data: {}", e.getMessage());
        }
    }

    private static void populateLoadedItems(List<String> items, List<ItemStack> target) {
        for (String itemId : items) {
            for (Item regItem : BuiltInRegistries.ITEM) {
                if (BuiltInRegistries.ITEM.getKey(regItem).toString().equals(itemId)) {
                    if (regItem != Items.AIR) {
                        target.add(new ItemStack(regItem));
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
                List<String> customIds = null;
                if (rec.customItems != null) {
                    customIds = new ArrayList<>();
                    for (ItemStack s : rec.customItems) {
                        customIds.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
                    }
                }
                recordMap.put(entry.getKey().toString(), new PlayerRecordData(
                        rec.day, rec.revealedMask, rec.purchasedMask,
                        rec.bankedResets, rec.resetSequence, customIds));
            }

            MarketSaveData data = new MarketSaveData(currentEpochDay, lastSavedEpochSecond, adminResetCount, itemIds, recordMap);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            VCoinsMod.LOGGER.error("Failed to save black market data: {}", e.getMessage());
        }
    }

    private record PlayerRecordData(long day, int revealedMask, int purchasedMask, int bankedResets, int resetSequence, List<String> customItems) {}
    private record MarketSaveData(long day, long lastSavedEpochSecond, int adminResetCount, List<String> items, Map<String, PlayerRecordData> playerRecords) {}
}
