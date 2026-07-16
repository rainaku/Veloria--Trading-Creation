package com.vcoins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VCoinsState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Long> playerCoins = new HashMap<>();

    public static long getCoins(UUID player) {
        return playerCoins.getOrDefault(player, 0L);
    }

    public static void setCoins(UUID player, long amount) {
        playerCoins.put(player, amount);
    }

    public static void addCoins(UUID player, long amount) {
        if (amount <= 0) {
            return;
        }
        long current = getCoins(player);
        setCoins(player, current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount);
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> load(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save(server));
    }

    private static void load(MinecraftServer server) {
        File file = new File(server.getSavePath(WorldSavePath.ROOT).toFile(), "vcoins.json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<UUID, Long>>(){}.getType();
                Map<UUID, Long> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    playerCoins.clear();
                    playerCoins.putAll(loaded);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void save(MinecraftServer server) {
        File file = new File(server.getSavePath(WorldSavePath.ROOT).toFile(), "vcoins.json");
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(playerCoins, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void removeCoins(UUID player, long amount) {
        long current = getCoins(player);
        if (current >= amount) {
            playerCoins.put(player, current - amount);
        }
    }
}
