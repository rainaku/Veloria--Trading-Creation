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
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VMarketEngine {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final double MIN_MULTIPLIER = 0.25;
    public static final double MAX_MULTIPLIER = 4.00;
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
        FALLING(ChatFormatting.RED),
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
        public String getArrow() {
            return switch (direction) {
                case RISING, SURGING -> "↑";
                case FALLING, CRASHING -> "↓";
                case STABLE -> "→";
            };
        }

        public Component getBadge() {
            String prefix = percentChange > 0 ? "+" : "";
            return Component.translatable("vcoins.market.price_badge", getArrow(), prefix + percentChange)
                    .withStyle(direction.getColor());
        }
    }

    // Server-side active commodity tracking
    private static final Map<String, MarketItemState> serverItemStates = new ConcurrentHashMap<>();
    private static long lastSavedEpochSecond = 0L;

    // Client-side synchronization cache
    private static final Map<String, MarketItemState> clientItemStates = new ConcurrentHashMap<>();
    private static volatile Thread clientThread;

    public static void registerClientThread() { clientThread = Thread.currentThread(); }
    private static boolean isClientThread() { return Thread.currentThread() == clientThread; }
    public static void clearClientSync() { clientItemStates.clear(); clientServerEpochDelta = 0; }
    private static long clientServerEpochDelta = 0L;

    public static long getEffectiveEpochSecond() {
        return Instant.now().getEpochSecond() + (isClientThread() ? clientServerEpochDelta : 0);
    }

    /** Continuous multi-day waves; no random jump at midnight. */
    public static double getDailyMultiplier(String itemId, long epochSecond) {
        return MarketCycle.multiplier(itemId, VCoinsPricing.isRareMarketItem(itemId), epochSecond);
    }

    /** Market depth decreases smoothly as the base price increases. */
    public static int calculateMarketDepth(long basePrice) {
        if (basePrice <= 0) {
            basePrice = 1;
        }
        // Smoothly scale market depth according to base item valuation.
        // Even the highest-tier endgame treasures have sufficient market depth (minimum 120),
        // preventing a small handful of purchases from artificially and abruptly spiking prices.
        double depth = 4500.0 / Math.pow((double) basePrice, 0.22);
        return Math.max(120, Math.min(5000, (int) Math.round(depth)));
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
        double absRatio = Math.abs(ratio);
        // Progressive elasticity: small routine retail purchases (|volume| < 10% depth)
        // produce gentle, cushion-absorbed price shifts rather than immediate twitchy spikes.
        // Heavy sustained volume progressively exerts standard market pressure up to limits.
        double progressive = Math.pow(absRatio, 1.25);
        double delta = Math.signum(ratio) * progressive * 0.25;
        return Math.max(-0.65, Math.min(1.35, delta));
    }

    public static double getVolumeModifier(String key, long basePrice, long nowEpoch) {
        MarketItemState state = (isClientThread() ? clientItemStates : serverItemStates).get(key);
        return state == null ? 0 : calculateVolumeModifier(getDecayedVolume(state, nowEpoch), basePrice);
    }

    public static double getMultiplier(String key) {
        return multiplierAt(key, getEffectiveEpochSecond(), false);
    }

    public static double getSellMultiplier(String key) {
        return multiplierAt(key, getEffectiveEpochSecond(), true);
    }

    public static double multiplierAt(String key, long epoch, boolean selling) {
        long base = VCoinsPricing.getBasePrice(key);
        if (base <= 0) return 1;
        double volume = getVolumeModifier(key, base, epoch);
        return MarketCycle.withPressure(getDailyMultiplier(key, epoch), selling ? Math.min(0, volume) : volume,
                VCoinsPricing.isRareMarketItem(key));
    }

    public static MarketTrend getTrend(String key) { return trend(key, false, null); }
    public static MarketTrend getTrend(ItemStack stack) { return trend(VCoinsPricing.getMarketKey(stack), false, stack); }
    public static MarketTrend getSellTrend(ItemStack stack) { return trend(VCoinsPricing.getMarketKey(stack), true, stack); }

    private static MarketTrend trend(String key, boolean selling, ItemStack stack) {
        long now = getEffectiveEpochSecond();
        double mult = multiplierAt(key, now, selling);
        int percent = (int) Math.round((mult - 1) * 100);
        if (stack != null) {
            long reference = selling ? VCoinsPricing.getReferenceSellPrice(stack) : VCoinsPricing.getReferencePrice(stack);
            long current = selling ? VCoinsPricing.getSellPrice(stack) : VCoinsPricing.getPrice(stack);
            percent = reference <= 0 ? 0 : (int) Math.round((current / (double) reference - 1) * 100);
        }
        // Icon and badge describe the same rounded deviation from the reference price.
        TrendDirection direction = directionForPercent(percent);
        double volume = getVolumeModifier(key, VCoinsPricing.getBasePrice(key), now);
        String reason = Math.abs(volume) < 0.04 ? "vcoins.market.reason.cycle"
                : volume > 0 ? "vcoins.market.reason.demand" : "vcoins.market.reason.supply";
        return new MarketTrend(mult, percent, direction, reason);
    }

    static TrendDirection directionForPercent(int percent) {
        return percent > 0 ? TrendDirection.RISING
                : percent < 0 ? TrendDirection.FALLING : TrendDirection.STABLE;
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
            return false;
        });

        syncToActiveShoppers(server);
    }

    public static void syncToActiveShoppers(MinecraftServer server) {
        if (server == null) return;
        MarketSyncPayload payload = createSyncPayload();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof VTradeScreenHandler || player.containerMenu instanceof VBlackMarketScreenHandler) {
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
        Map<String, Integer> volumes = new HashMap<>();
        for (var entry : serverItemStates.entrySet()) {
            int volume = getDecayedVolume(entry.getValue(), now);
            if (volume != 0) volumes.put(entry.getKey(), volume);
        }
        return new MarketSyncPayload(now, volumes);
    }

    public static void applyClientSync(long serverEpochSecond, Map<String, Integer> volumes) {
        clientServerEpochDelta = serverEpochSecond - Instant.now().getEpochSecond();
        clientItemStates.clear();
        if (volumes != null) volumes.forEach((key, volume) ->
                clientItemStates.put(key, new MarketItemState(volume, serverEpochSecond)));
    }

    public static void reset(MinecraftServer server) {
        serverItemStates.clear();
        clearClientSync();
        if (server != null) {
            save(server);
            syncToActiveShoppers(server);
        }
    }

    public static int getTrackedCount() {
        return serverItemStates.size();
    }

    public static void load(MinecraftServer server) {
        serverItemStates.clear();
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
                    itemsMap.put(entry.getKey(), new ItemSaveRecord(decayed, now));
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
