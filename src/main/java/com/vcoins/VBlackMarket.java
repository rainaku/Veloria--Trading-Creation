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

    public record RomanGodTemplate(
            Item item,
            String romanName,
            List<ResourceKey<Enchantment>> primaryEnchants,
            List<ResourceKey<Enchantment>> secondaryEnchants
    ) {}

    private static final List<RomanGodTemplate> ROMAN_GOD_TEMPLATES = List.of(
            // Swords
            new RomanGodTemplate(Items.NETHERITE_SWORD, "Gladius của Hoàng Đế Caesar",
                    List.of(Enchantments.SHARPNESS, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.LOOTING, Enchantments.FIRE_ASPECT, Enchantments.SWEEPING_EDGE)),
            new RomanGodTemplate(Items.NETHERITE_SWORD, "Lưỡi Gươm Bất Bại Invictus",
                    List.of(Enchantments.SHARPNESS, Enchantments.UNBREAKING, Enchantments.FIRE_ASPECT),
                    List.of(Enchantments.LOOTING, Enchantments.MENDING, Enchantments.KNOCKBACK)),
            new RomanGodTemplate(Items.NETHERITE_SWORD, "Trảm Kích Nữ Thần Bellona",
                    List.of(Enchantments.SHARPNESS, Enchantments.SWEEPING_EDGE, Enchantments.MENDING),
                    List.of(Enchantments.FIRE_ASPECT, Enchantments.UNBREAKING, Enchantments.LOOTING)),
            new RomanGodTemplate(Items.NETHERITE_SWORD, "Đoản Kiếm Cấm Vệ Praetorian",
                    List.of(Enchantments.SHARPNESS, Enchantments.LOOTING, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING, Enchantments.SWEEPING_EDGE)),

            // Mace
            new RomanGodTemplate(Items.MACE, "Trọng Chùy Thần Rèn Vulcan",
                    List.of(Enchantments.DENSITY, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.BREACH, Enchantments.WIND_BURST, Enchantments.FIRE_ASPECT)),
            new RomanGodTemplate(Items.MACE, "Thiên Uy Sấm Sét Fulgur Jovis",
                    List.of(Enchantments.DENSITY, Enchantments.BREACH, Enchantments.WIND_BURST),
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING)),

            // Trident
            new RomanGodTemplate(Items.TRIDENT, "Đinh Ba Hải Thần Neptune",
                    List.of(Enchantments.IMPALING, Enchantments.LOYALTY, Enchantments.CHANNELING),
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING)),
            new RomanGodTemplate(Items.TRIDENT, "Cuồng Nộ Triều Cường Tiber",
                    List.of(Enchantments.IMPALING, Enchantments.RIPTIDE, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING)),

            // Bow & Crossbow
            new RomanGodTemplate(Items.BOW, "Cung Quang Minh Thần Apollo",
                    List.of(Enchantments.POWER, Enchantments.FLAME, Enchantments.INFINITY),
                    List.of(Enchantments.PUNCH, Enchantments.UNBREAKING)),
            new RomanGodTemplate(Items.BOW, "Hỏa Diễm Nữ Thần Vesta",
                    List.of(Enchantments.POWER, Enchantments.FLAME, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING, Enchantments.PUNCH)),
            new RomanGodTemplate(Items.CROSSBOW, "Nỏ Thần Binh Đoàn Legio IX",
                    List.of(Enchantments.QUICK_CHARGE, Enchantments.MULTISHOT, Enchantments.UNBREAKING),
                    List.of(Enchantments.PIERCING, Enchantments.MENDING)),
            new RomanGodTemplate(Items.CROSSBOW, "Trọng Pháo Hạng Nặng Ballista",
                    List.of(Enchantments.QUICK_CHARGE, Enchantments.PIERCING, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING)),

            // Axe & Pickaxe
            new RomanGodTemplate(Items.NETHERITE_AXE, "Chiến Rìu Đấu Trường Colosseum",
                    List.of(Enchantments.SHARPNESS, Enchantments.EFFICIENCY, Enchantments.UNBREAKING),
                    List.of(Enchantments.SILK_TOUCH, Enchantments.MENDING)),
            new RomanGodTemplate(Items.NETHERITE_PICKAXE, "Khai Sơn Thần Cuốc Saturn",
                    List.of(Enchantments.EFFICIENCY, Enchantments.FORTUNE, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING)),

            // Armor
            new RomanGodTemplate(Items.NETHERITE_CHESTPLATE, "Chiến Giáp Hoàng Đế Imperator",
                    List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.THORNS)),
            new RomanGodTemplate(Items.NETHERITE_CHESTPLATE, "Bảo Hộ Khiên Thành Minerva",
                    List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.THORNS),
                    List.of(Enchantments.MENDING)),
            new RomanGodTemplate(Items.NETHERITE_HELMET, "Chiến Khôi Thần Chiến Mars",
                    List.of(Enchantments.PROTECTION, Enchantments.RESPIRATION, Enchantments.AQUA_AFFINITY),
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING)),
            new RomanGodTemplate(Items.NETHERITE_HELMET, "Vương Miện Corona Triumphalis",
                    List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of(Enchantments.RESPIRATION)),
            new RomanGodTemplate(Items.NETHERITE_LEGGINGS, "Chiến Xà Bách Binh Centurion",
                    List.of(Enchantments.PROTECTION, Enchantments.SWIFT_SNEAK, Enchantments.UNBREAKING),
                    List.of(Enchantments.MENDING, Enchantments.THORNS)),
            new RomanGodTemplate(Items.NETHERITE_BOOTS, "Hộ Xà Thần Phong Mercury",
                    List.of(Enchantments.PROTECTION, Enchantments.FEATHER_FALLING, Enchantments.DEPTH_STRIDER),
                    List.of(Enchantments.SOUL_SPEED, Enchantments.UNBREAKING, Enchantments.MENDING)),

            // Elytra & Shield
            new RomanGodTemplate(Items.ELYTRA, "Đôi Cánh Đế Chế Roma Aeterna",
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of()),
            new RomanGodTemplate(Items.SHIELD, "Đại Khiên Thần Vực Scutum",
                    List.of(Enchantments.UNBREAKING, Enchantments.MENDING),
                    List.of())
    );

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

        RomanGodTemplate template = ROMAN_GOD_TEMPLATES.get(random.nextInt(ROMAN_GOD_TEMPLATES.size()));
        ItemStack stack = new ItemStack(template.item());

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

        // Custom Name
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("⚔ " + template.romanName() + " ⚔")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        // Custom Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("✦ BẢO VẬT LA MÃ CẤP THẦN ✦").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        lore.add(Component.literal("Bảo vật huyền thoại từ thời Đế Chế La Mã Cổ Đại.").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        if (maxDur > 0) {
            lore.add(Component.literal("Độ bền: " + remainingDur + " / " + maxDur).withStyle(ChatFormatting.GRAY));
        }
        lore.add(Component.literal("Định giá Chợ Đen: " + String.format(Locale.ROOT, "%,d", price) + " Velicoins").withStyle(ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        // Apply Enchantments
        var lookup = getRegistryLookup().lookupOrThrow(Registries.ENCHANTMENT);

        List<ResourceKey<Enchantment>> enchantsToApply = new ArrayList<>(template.primaryEnchants());
        int extraCount = (int) Math.round(powerRatio * template.secondaryEnchants().size());
        for (int i = 0; i < extraCount && i < template.secondaryEnchants().size(); i++) {
            enchantsToApply.add(template.secondaryEnchants().get(i));
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
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean("VRomanGodItem", true);
            tag.putLong("VCoinsBlackMarketPrice", finalPrice);
            tag.putString("VRomanName", template.romanName());
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
        String itemName = getRomanGodItemName(stack);
        long price = getRomanGodItemPrice(stack);
        int remainingDurability = stack.getMaxDamage() > 0 ? stack.getMaxDamage() - stack.getDamageValue() : 0;
        int maxDurability = stack.getMaxDamage();

        Component separator = Component.literal("§6§l╔════════════════════════════════════════════════╗");
        Component header = Component.literal("§e§l   ✦ [BẢO VẬT LA MÃ CẤP THẦN GIÁNG THẾ] ✦");
        Component playerInfo = Component.literal("§fDũng sĩ §a§l" + playerName + " §fvừa khai mở Bảo Vật Thần Thoại:");
        Component itemNameComp = Component.literal("§6§l       ⚔ " + itemName + " ⚔");
        Component statsComp = Component.literal("§eĐịnh giá: §6§l" + String.format(Locale.ROOT, "%,d", price) + " Velicoins"
                + (maxDurability > 0 ? " §7| §bĐộ bền: §f" + remainingDurability + "/" + maxDurability : ""));
        Component loreComp = Component.literal("§d   \"Hào quang Đế Chế La Mã rực sáng nơi Chợ Đen!\"");
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
        String itemName = getRomanGodItemName(stack);

        Component separator = Component.literal("§6§l╔════════════════════════════════════════════════╗");
        Component title = Component.literal("§6§l  ★ [HUYỀN THOẠI ĐẾ CHẾ LA MÃ XUẤT THẾ] ★");
        Component body = Component.literal("§fDũng sĩ §a§l" + playerName + " §fđã chi trả §6§l" + String.format(Locale.ROOT, "%,d", cost) + " Velicoins");
        Component itemComp = Component.literal("§fđể chính thức sở hữu Bảo Vật Cấp Thần: §e§l⚔ " + itemName + " ⚔§f!");
        Component lore = Component.literal("§d  \"Một trang sử mới của lục địa Veloria đã được khắc ghi!\"");
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
        if (activeServer != null && activeServer.overworld() != null) {
            long dayTicks = activeServer.overworld().getOverworldClockTime() % 24000L;
            return Math.max(0L, (24000L - dayTicks) / 20L);
        }
        long nowEpoch = Instant.now().getEpochSecond();
        return Math.max(0L, 86400L - (nowEpoch % 86400L));
    }

    public static synchronized long getCurrentDay() {
        if (activeServer != null && activeServer.overworld() != null) {
            return activeServer.overworld().getOverworldClockTime() / 24000L;
        }
        return currentEpochDay > 0 ? currentEpochDay : Instant.now().getEpochSecond() / 86400L;
    }

    public static synchronized boolean isTimeAnomalyDetected() {
        return timeAnomalyDetected;
    }

    public static synchronized PlayerDailyRecord getPlayerRecord(UUID uuid) {
        long today = getCurrentDay();
        PlayerDailyRecord playerRecord = playerRecords.get(uuid);
        if (playerRecord == null) {
            playerRecord = new PlayerDailyRecord(today, 0, 0, 0, 0, null);
            playerRecords.put(uuid, playerRecord);
        } else if (playerRecord.day != today) {
            // New Minecraft day: retain banked resets, reset daily progress
            playerRecord.day = today;
            playerRecord.revealedMask = 0;
            playerRecord.purchasedMask = 0;
            playerRecord.resetSequence = 0;
            playerRecord.customItems = null;
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
        if (slotIndex == -1) {
            playerRecord.revealedMask = (1 << DAILY_ITEM_COUNT) - 1;
        } else if (slotIndex >= 0 && slotIndex < DAILY_ITEM_COUNT) {
            playerRecord.revealedMask |= (1 << slotIndex);
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
                playerRecord.bankedResets, playerRecord.resetSequence, items
        ));
    }

    /**
     * Anti-time-exploit daily validation and refresh.
     * Guaranteed deterministic seed per Minecraft in-game day + rollback detection.
     */
    public static synchronized void checkAndRefreshDaily(boolean force, MinecraftServer server) {
        long inGameDay = (server != null && server.overworld() != null)
                ? server.overworld().getOverworldClockTime() / 24000L
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
            dailyItems.add(rollCardItem(random, candidates.get(i)));
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
                long inGameDay = server.overworld().getOverworldClockTime() / 24000L;
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
                    prd.bankedResets, prd.resetSequence, custom));
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
                        rec.bankedResets, rec.resetSequence, customEntries));
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

                stack.set(DataComponents.CUSTOM_NAME, Component.literal("⚔ " + romanName + " ⚔")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.literal("✦ BẢO VẬT LA MÃ CẤP THẦN ✦").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                lore.add(Component.literal("Bảo vật huyền thoại từ thời Đế Chế La Mã Cổ Đại.").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
                if (maxDur > 0) {
                    lore.add(Component.literal("Độ bền: " + remainingDur + " / " + maxDur).withStyle(ChatFormatting.GRAY));
                }
                lore.add(Component.literal("Định giá Chợ Đen: " + String.format(Locale.ROOT, "%,d", godPrice) + " Velicoins").withStyle(ChatFormatting.GOLD));
                stack.set(DataComponents.LORE, new ItemLore(lore));

                long finalPrice = godPrice;
                String finalName = romanName;
                boolean finalAnnounced = broadcasted;
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                    tag.putBoolean("VRomanGodItem", true);
                    tag.putLong("VCoinsBlackMarketPrice", finalPrice);
                    tag.putString("VRomanName", finalName);
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

    private record PlayerRecordData(long day, int revealedMask, int purchasedMask, int bankedResets, int resetSequence, List<BlackMarketItemEntry> customItems) {}
    private record MarketSaveData(long day, long lastSavedEpochSecond, int adminResetCount, List<BlackMarketItemEntry> items, Map<String, PlayerRecordData> playerRecords) {}
}
