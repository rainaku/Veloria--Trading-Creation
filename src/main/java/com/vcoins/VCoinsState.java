package com.vcoins;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class VCoinsState {
    public static final String BALANCE_TAG = "veloria:coins";
    private static final Map<UUID, Long> playerCoins = new HashMap<>();
    private static final Map<UUID, Long> clientCoins = new HashMap<>();

    public static long getCoins(UUID player) { return playerCoins.getOrDefault(player, 0L); }
    public static void setCoins(UUID player, long amount) { playerCoins.put(player, Math.max(0L, amount)); }
    public static long getClientCoins(UUID player) { return clientCoins.getOrDefault(player, 0L); }
    public static void setClientCoins(UUID player, long amount) { clientCoins.put(player, Math.max(0L, amount)); }
    public static void clearClientCoins() { clientCoins.clear(); }

    public static void addCoins(UUID player, long amount) {
        if (amount <= 0) return;
        long current = getCoins(player);
        setCoins(player, current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount);
    }

    public static void removeCoins(UUID player, long amount) {
        if (amount > 0 && getCoins(player) >= amount) setCoins(player, getCoins(player) - amount);
    }

    public static void checkpoint(net.minecraft.server.level.ServerPlayer player) {
        ((com.vcoins.mixin.PlayerListSaveInvoker) player.level().getServer().getPlayerList())
                .veloria$savePlayer(player);
    }

    public static void registerEvents() {
        // Before any player data is read, including the integrated-server owner.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            loadWorld(server.getWorldPath(LevelResource.ROOT));
            VTradeScreenHandler.clearBuybackHistory();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            playerCoins.clear();
            VTradeScreenHandler.clearBuybackHistory();
        });
    }

    /** Legacy JSON is migration input only. Player NBT always takes precedence. */
    static void loadWorld(Path world) {
        playerCoins.clear();
        Path legacy = world.resolve("vcoins.json");
        if (!Files.exists(legacy)) return;
        try (var reader = Files.newBufferedReader(legacy)) {
            Map<UUID, Long> loaded = new Gson().fromJson(reader,
                    new TypeToken<Map<UUID, Long>>() {}.getType());
            if (loaded == null) throw new IllegalStateException("Empty legacy coin save");
            for (var entry : loaded.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)
                    throw new IllegalStateException("Invalid legacy coin balance");
            }
            playerCoins.putAll(loaded);
        } catch (Exception exception) {
            // Never silently replace a damaged save with zero balances.
            throw new IllegalStateException("Cannot load " + legacy, exception);
        }
    }

    public static void readPlayerBalance(UUID player, ValueInput input) {
        setCoins(player, input.getLong(BALANCE_TAG).orElseGet(() -> getCoins(player)));
    }

    public static void writePlayerBalance(UUID player, ValueOutput output) {
        // Serialized in the same vanilla player snapshot as Inventory and XP.
        // A crash rolls back the whole snapshot, never just the payment.
        output.putLong(BALANCE_TAG, getCoins(player));
    }
}
