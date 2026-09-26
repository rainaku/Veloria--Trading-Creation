package com.vcoins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VMarketEngine {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final double MIN_MULTIPLIER = 0.30;
    public static final double MAX_MULTIPLIER = 2.50;
    public static final double HALF_LIFE_SECONDS = 1800.0; // 30 minutes mean-reversion half-life

    public static class MarketItemState {
        public int netVolume;
        public long lastTradedEpoch;

        public MarketItemState(int netVolume, long lastTradedEpoch) {
            this.netVolume = netVolume;
            this.lastTradedEpoch = lastTradedEpoch;
        }
    }

    public enum TrendDirection {
        SURGING(ChatFormatting.GOLD),
        RISING(ChatFormatting.GREEN),
        STABLE(ChatFormatting.GRAY),
        FALLING(ChatFormatting.GOLD),
        CRASHING(ChatFormatting.RED);

        private final ChatFormatting color;

        TrendDirection(ChatFormatting color) {
            this.color = color;
        }

        public ChatFormatting getColor() {
            return color;
        }
    }

    public record MarketTrend(
            double multiplier,
            int percentChange,
            TrendDirection direction,
            String reasonKey
    ) {
        public Component getBadge() {
            String prefix = percentChange > 0 ? "+" : "";
            return Component.literal("(" + prefix + percentChange + "%)")
                    .withStyle(direction.getColor());
        }
    }

    // Server-side active commodity tracking
    private static final Map<String, MarketItemState> serverItemStates = new ConcurrentHashMap<>();
    private static long lastSavedEpochSecond = 0L;

    // Client-side synchronization cache
    private static final Map<String, Float> clientVolumeModifiers = new ConcurrentHashMap<>();
    private static long clientServerEpochDelta = 0L;

    public static long getEffectiveEpochSecond() {
        return Instant.now().getEpochSecond() + clientServerEpochDelta;
    }

    /**
     * Calculates the macroeconomic harmonic cycle for an item.
     * Moves organically between -12% and +12% throughout real-world time.
     */
    public static double getMacroCycle(String itemId, ShopCategory category, long epochSecond) {
        int hash = Math.abs(itemId.hashCode());
        double itemPhase = (hash % 1000) * 0.006283; // 0 to 2*PI
        double categoryPhase = (category != null ? category.ordinal() : 0) * 0.7854; // PI/4 per category

        // 4-hour macroeconomic wave (amplitude 8%)
        double cycle4h = 0.08 * Math.sin(2.0 * Math.PI * (epochSecond % 14400) / 14400.0 + categoryPhase + itemPhase);
        // 1-hour micro commodity oscillation (amplitude 4%)
        double cycle1h = 0.04 * Math.cos(2.0 * Math.PI * (epochSecond % 3600) / 3600.0 + itemPhase * 1.5);

        return cycle4h + cycle1h;
    }

    /**
     * Calculates market depth (liquidity) based on base price.
     * High value items (diamonds) have small depth; cheap items (cobble) have large depth.
     */
    public static int calculateMarketDepth(long basePrice) {
        if (basePrice <= 0) {
            basePrice = 1;
        }
        double sqrtPrice = Math.sqrt((double) basePrice);
        int depth = (int) Math.round(2500.0 / sqrtPrice);
        return Math.max(15, Math.min(5000, depth));
    }

    public static int getDecayedVolume(MarketItemState state, long nowEpoch) {
        long elapsed = Math.max(0L, nowEpoch - state.lastTradedEpoch);
        if (elapsed <= 0) {
            return state.netVolume;
        }

        double halfLives = (double) elapsed / HALF_LIFE_SECONDS;
        double decay = Math.pow(0.5, halfLives);
        int decayed = (int) Math.round(state.netVolume * decay);
        return Math.abs(decayed) < 1 ? 0 : decayed;
    }

    public static double calculateVolumeModifier(int volume, long basePrice) {
        if (volume == 0) {
            return 0.0;
        }
        int depth = calculateMarketDepth(basePrice);
        double ratio = (double) volume / (double) depth;
        // 1 full depth of transactions moves the price by 25%
        return Math.max(-0.65, Math.min(1.35, ratio * 0.25));
    }

    public static double getVolumeModifier(String itemId, long basePrice, long nowEpoch) {
        if (!clientVolumeModifiers.isEmpty()) {
            // Client side or synced view
            return clientVolumeModifiers.getOrDefault(itemId, 0.0f);
        }
        // Server side authoritative view
        MarketItemState state = serverItemStates.get(itemId);
        if (state != null) {
            int volume = getDecayedVolume(state, nowEpoch);
            return calculateVolumeModifier(volume, basePrice);
        }
        return 0.0;
    }

    /**
     * Retrieves the current dynamic price multiplier for buying an item.
     */
    public static double getMultiplier(String itemId) {
        long basePrice = VCoinsPricing.getBasePrice(itemId);
        if (basePrice <= 0) {
            return 1.0;
        }

        long nowEpoch = getEffectiveEpochSecond();
        ShopCategory category = VCoinsPricing.getCategory(itemId);
        double macroCycle = getMacroCycle(itemId, category, nowEpoch);
        double volumeMod = getVolumeModifier(itemId, basePrice, nowEpoch);

        double total = 1.0 + macroCycle + volumeMod;
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, total));
    }

    /**
     * Retrieves the dynamic price multiplier for SELLING an item.
     * ANTI-PUMP-AND-DUMP:
     * Buying from the shop creates positive volumeMod (surging buy price), but positive volumeMod
     * CANNOT artificially pump the sell price above the macroeconomic baseline (1.0 + macroCycle).
     * However, oversupply / mass-selling (volumeMod < 0) immediately pushes the sell price down towards
     * MIN_MULTIPLIER (0.30), effectively penalizing automated farm dumping.
     */
    public static double getSellMultiplier(String itemId) {
        long basePrice = VCoinsPricing.getBasePrice(itemId);
        if (basePrice <= 0) {
            return 1.0;
        }

        long nowEpoch = getEffectiveEpochSecond();
        ShopCategory category = VCoinsPricing.getCategory(itemId);
        double macroCycle = getMacroCycle(itemId, category, nowEpoch);
        double volumeMod = getVolumeModifier(itemId, basePrice, nowEpoch);

        // Clamp positive volumeMod to 0.0 so buying spikes never inflate sell payouts!
        double effectiveVolumeMod = Math.min(0.0, volumeMod);
        double total = 1.0 + macroCycle + effectiveVolumeMod;
        return Math.max(MIN_MULTIPLIER, Math.min(1.0 + macroCycle, total));
    }

    /**
     * Retrieves rich trend information for UI rendering.
     */
    public static MarketTrend getTrend(String itemId) {
        double mult = getMultiplier(itemId);
        int percent = (int) Math.round((mult - 1.0) * 100.0);

        long nowEpoch = getEffectiveEpochSecond();
        ShopCategory category = VCoinsPricing.getCategory(itemId);
        double cycle = getMacroCycle(itemId, category, nowEpoch);

        long basePrice = VCoinsPricing.getBasePrice(itemId);
        double volumeMod;
        if (!clientVolumeModifiers.isEmpty()) {
            volumeMod = clientVolumeModifiers.getOrDefault(itemId, 0.0f);
        } else {
            MarketItemState state = serverItemStates.get(itemId);
            volumeMod = (state != null) ? calculateVolumeModifier(getDecayedVolume(state, nowEpoch), basePrice) : 0.0;
        }

        String reasonKey;
        if (Math.abs(volumeMod) >= 0.04) {
            reasonKey = volumeMod > 0 ? "vcoins.market.reason.demand" : "vcoins.market.reason.supply";
        } else {
            reasonKey = "vcoins.market.reason.cycle";
        }

        TrendDirection direction;
        if (percent >= 15) {
            direction = TrendDirection.SURGING;
        } else if (percent >= 3) {
            direction = TrendDirection.RISING;
        } else if (percent <= -15) {
            direction = TrendDirection.CRASHING;
        } else if (percent <= -3) {
            direction = TrendDirection.FALLING;
        } else {
            direction = TrendDirection.STABLE;
        }

        return new MarketTrend(mult, percent, direction, reasonKey);
    }

    public static void recordBuy(String itemId, int count) {
        if (count <= 0) return;
        long now = Instant.now().getEpochSecond();
        serverItemStates.compute(itemId, (k, v) -> {
            if (v == null) {
                return new MarketItemState(count, now);
            }
            int current = getDecayedVolume(v, now);
            return new MarketItemState(current + count, now);
        });
    }

    public static void recordSell(String itemId, int count) {
        if (count <= 0) return;
        long now = Instant.now().getEpochSecond();
        serverItemStates.compute(itemId, (k, v) -> {
            if (v == null) {
                return new MarketItemState(-count, now);
            }
            int current = getDecayedVolume(v, now);
            return new MarketItemState(current - count, now);
        });
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        long now = Instant.now().getEpochSecond();

        // Prune zeroed volumes and decay
        serverItemStates.entrySet().removeIf(entry -> {
            int decayed = getDecayedVolume(entry.getValue(), now);
            if (decayed == 0) {
                return true;
            }
            entry.getValue().netVolume = decayed;
            entry.getValue().lastTradedEpoch = now;
            return false;
        });

        syncToActiveShoppers(server);
    }

    public static void syncToActiveShoppers(MinecraftServer server) {
        if (server == null) return;
        MarketSyncPayload payload = createSyncPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof VTradeScreenHandler) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) return;
        ServerPlayNetworking.send(player, createSyncPayload());
    }

    public static MarketSyncPayload createSyncPayload() {
        long now = Instant.now().getEpochSecond();
        Map<String, Float> volumeMods = new HashMap<>();
        for (Map.Entry<String, MarketItemState> entry : serverItemStates.entrySet()) {
            long base = VCoinsPricing.getBasePrice(entry.getKey());
            int volume = getDecayedVolume(entry.getValue(), now);
            if (volume != 0) {
                float mod = (float) calculateVolumeModifier(volume, base);
                if (Math.abs(mod) >= 0.005f) {
                    volumeMods.put(entry.getKey(), mod);
                }
            }
        }
        return new MarketSyncPayload(now, volumeMods);
    }

    public static void applyClientSync(long serverEpochSecond, Map<String, Float> volumeModifiers) {
        long localEpoch = Instant.now().getEpochSecond();
        clientServerEpochDelta = serverEpochSecond - localEpoch;
        clientVolumeModifiers.clear();
        if (volumeModifiers != null) {
            clientVolumeModifiers.putAll(volumeModifiers);
        }
    }

    public static void reset(MinecraftServer server) {
        serverItemStates.clear();
        clientVolumeModifiers.clear();
        if (server != null) {
            save(server);
            syncToActiveShoppers(server);
        }
    }

    public static int getTrackedCount() {
        return serverItemStates.size();
    }

    public static void load(MinecraftServer server) {
        File file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "vcoins_market.json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<MarketSaveData>(){}.getType();
                MarketSaveData data = GSON.fromJson(reader, type);
                if (data != null && data.items != null) {
                    long now = Instant.now().getEpochSecond();
                    serverItemStates.clear();
                    lastSavedEpochSecond = data.lastSavedEpochSecond;

                    for (Map.Entry<String, ItemSaveRecord> entry : data.items.entrySet()) {
                        ItemSaveRecord rec = entry.getValue();
                        MarketItemState state = new MarketItemState(rec.netVolume, rec.lastTradedEpoch);
                        int decayed = getDecayedVolume(state, now);
                        if (decayed != 0) {
                            serverItemStates.put(entry.getKey(), new MarketItemState(decayed, now));
                        }
                    }
                }
            } catch (IOException e) {
                VCoinsMod.LOGGER.error("Failed to load market engine data: {}", e.getMessage());
            }
        }
    }

    public static void save(MinecraftServer server) {
        File file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "vcoins_market.json");
        try (FileWriter writer = new FileWriter(file)) {
            long now = Instant.now().getEpochSecond();
            Map<String, ItemSaveRecord> itemsMap = new HashMap<>();
            for (Map.Entry<String, MarketItemState> entry : serverItemStates.entrySet()) {
                int decayed = getDecayedVolume(entry.getValue(), now);
                if (decayed != 0) {
                    itemsMap.put(entry.getKey(), new ItemSaveRecord(decayed, entry.getValue().lastTradedEpoch));
                }
            }
            MarketSaveData data = new MarketSaveData(now, itemsMap);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            VCoinsMod.LOGGER.error("Failed to save market engine data: {}", e.getMessage());
        }
    }

    private record ItemSaveRecord(int netVolume, long lastTradedEpoch) {}
    private record MarketSaveData(long lastSavedEpochSecond, Map<String, ItemSaveRecord> items) {}
}
