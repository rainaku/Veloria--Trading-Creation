package com.vcoins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class VBlackMarket {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DAILY_ITEM_COUNT = 20;
    private static final List<ItemStack> dailyItems = new ArrayList<>();
    private static String lastGeneratedDate = "";

    /**
     * Danh sách vật phẩm có giá trị cao, hiếm, đòi hỏi công sức lớn để thu thập.
     * Tuyệt đối không bao gồm rác hay đồ farm tự động dễ kiếm.
     */
    private static final Item[] VALUABLE_POOL = new Item[] {
            // Khám phá End & Công trình đặc biệt
            Items.ELYTRA,
            Items.SHULKER_BOX,
            Items.SHULKER_SHELL,
            Items.DRAGON_HEAD,
            Items.DRAGON_BREATH,
            Items.END_CRYSTAL,

            // Kho báu & Đại dương
            Items.HEART_OF_THE_SEA,
            Items.NAUTILUS_SHELL,
            Items.TRIDENT,
            Items.CONDUIT,
            Items.SPONGE,

            // Trùm & Mini-boss / Trial Chambers
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

            // Deep Dark & Cổ xưa
            Items.ECHO_SHARD,
            Items.RECOVERY_COMPASS,
            Items.SNIFFER_EGG,
            Items.SCULK_CATALYST,
            Items.SCULK_SHRIEKER,

            // Netherite & Khoáng sản quý hiếm
            Items.NETHERITE_INGOT,
            Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
            Items.ANCIENT_DEBRIS,
            Items.NETHERITE_SCRAP,
            Items.DIAMOND_BLOCK,
            Items.ENCHANTED_GOLDEN_APPLE,

            // Giáp thú & Đĩa nhạc quý
            Items.DIAMOND_HORSE_ARMOR,
            Items.NETHERITE_HORSE_ARMOR,
            Items.MUSIC_DISC_PIGSTEP,
            Items.MUSIC_DISC_OTHERSIDE,
            Items.MUSIC_DISC_RELIC,
            Items.MUSIC_DISC_CREATOR,
            Items.MUSIC_DISC_PRECIPICE,

            // Bản mẫu rèn giáp hiếm (Armor Trims)
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

    public static synchronized void checkAndRefreshDaily(boolean force, MinecraftServer server) {
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        if (!force && today.equals(lastGeneratedDate) && !dailyItems.isEmpty()) {
            return;
        }

        dailyItems.clear();
        // Dùng seed ngày hôm nay để đảm bảo mọi người chơi trên server nhìn thấy cùng 20 món đồ trong ngày
        long seed = today.hashCode();
        Random random = new Random(seed);

        List<Item> candidates = new ArrayList<>();
        for (Item item : VALUABLE_POOL) {
            candidates.add(item);
        }
        Collections.shuffle(candidates, random);

        int count = Math.min(DAILY_ITEM_COUNT, candidates.size());
        for (int i = 0; i < count; i++) {
            Item item = candidates.get(i);
            dailyItems.add(new ItemStack(item));
        }

        lastGeneratedDate = today;
        if (server != null) {
            save(server);
        }
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> load(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save(server));
    }

    private static void load(MinecraftServer server) {
        File file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "vcoins_blackmarket.json");
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<MarketSaveData>(){}.getType();
                MarketSaveData data = GSON.fromJson(reader, type);
                if (data != null && data.date != null && data.items != null) {
                    lastGeneratedDate = data.date;
                    dailyItems.clear();
                    for (String itemId : data.items) {
                        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId))
                                .map(net.minecraft.core.Holder::value)
                                .orElse(Items.AIR);
                        if (item != Items.AIR) {
                            dailyItems.add(new ItemStack(item));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Kiểm tra xem đã sang ngày mới chưa
        checkAndRefreshDaily(false, server);
    }

    private static void save(MinecraftServer server) {
        File file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "vcoins_blackmarket.json");
        try (FileWriter writer = new FileWriter(file)) {
            List<String> itemIds = new ArrayList<>();
            for (ItemStack stack : dailyItems) {
                itemIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
            MarketSaveData data = new MarketSaveData(lastGeneratedDate, itemIds);
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private record MarketSaveData(String date, List<String> items) {}
}
