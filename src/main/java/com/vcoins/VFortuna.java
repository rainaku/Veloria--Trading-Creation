package com.vcoins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;

public final class VFortuna {
    public static final int REDEEM = 80;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, FortunaRun> RUNS = new HashMap<>();
    private static final java.util.Random RANDOM = new java.security.SecureRandom();
    private static final Item[][] POOL = {
        {Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE}, // tier 0
        {Items.DIAMOND, Items.EMERALD_BLOCK, Items.GOLDEN_CARROT},       // tier 1
        {Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE, Items.TOTEM_OF_UNDYING}, // tier 2
        {Items.DIAMOND_BLOCK, Items.RECOVERY_COMPASS, Items.TOTEM_OF_UNDYING}, // tier 3
        {Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.DIAMOND_CHESTPLATE}, // tier 4
        {Items.DIAMOND_SWORD, Items.NETHERITE_CHESTPLATE, Items.DIAMOND_PICKAXE} // tier 5: phase cuối!
    };
    public static FortunaRun run(UUID id) { return RUNS.computeIfAbsent(id, ignored -> new FortunaRun()); }
    public static boolean isReward(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr("fortuna", false);
    }
    public static ItemStack reward(FortunaRun run, int tier, net.minecraft.core.HolderLookup.Provider registries) {
        int count = switch (tier) {
            case 0 -> (run.choices[tier] == 0 ? 8 : (run.choices[tier] == 1 ? 16 : 32));
            case 1 -> (run.choices[tier] == 0 ? 12 : (run.choices[tier] == 1 ? 8 : 32));
            case 2 -> (run.choices[tier] == 0 ? 2 : (run.choices[tier] == 1 ? 16 : 1));
            case 3 -> (run.choices[tier] == 0 ? 4 : (run.choices[tier] == 1 ? 1 : 2));
            default -> 1;
        };
        ItemStack stack = new ItemStack(POOL[tier][run.choices[tier]], count);
        CompoundTag tag = new CompoundTag(); tag.putBoolean("fortuna", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.translatable("fortuna.bound").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE))));
        if (tier == 4) {
            var enchants = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var key = switch (run.choices[tier]) {
                case 0 -> net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY;
                case 1 -> net.minecraft.world.item.enchantment.Enchantments.SHARPNESS;
                default -> net.minecraft.world.item.enchantment.Enchantments.PROTECTION;
            };
            stack.enchant(enchants.getOrThrow(key), 4);
        }
        if (tier == 5) {
            int variant = run.choices[tier];
            if (variant == 0) { // Diamond Sword Max Enchant
                var enchants = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                stack.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5);
                stack.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
                stack.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING), 1);
                stack.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOOTING), 3);
                stack.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT), 2);
                stack.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE), 3);
            } else if (variant == 1) { // Netherite Chestplate, no enchant, durability 20-90%
                int maxDmg = stack.getMaxDamage();
                float durPct = 0.20f + ((run.revision * 31 + run.choices[tier] * 17 + 42) % 71) / 100.0f;
                int damage = Math.max(1, Math.round(maxDmg * (1.0f - durPct)));
                stack.setDamageValue(damage);
            } else { // Diamond Pickaxe Max Enchant
                var enchants = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                stack.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 5);
                stack.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE), 3);
                stack.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
                stack.enchant(enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING), 1);
            }
        }
        if (tier >= 3) {
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("fortuna.relic." + tier, stack.getHoverName()));
            if (!(tier == 5 && run.choices[tier] == 1)) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
        }
        return stack;
    }

    /** 200k fixed entry fee for the Fortuna Pact. */
    public static long entryPrice(net.minecraft.core.HolderLookup.Provider registries) {
        return 200_000L;
    }
    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new VFortunaMenu(id, inv), Component.translatable("fortuna.title")));
        VCoinsMod.syncCoins(player);
    }
    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
    }
    public static void action(ServerPlayer player, int action, long entry) {
        FortunaRun r = run(player.getUUID());
        long now = System.nanoTime();
        if (now < r.nextAction) return;
        r.nextAction = now + 500_000_000L;
        if (action >= 0 && action <= 2 && !r.active) {
            if (VCoinsState.getCoins(player.getUUID()) < entry) {
                player.sendOverlayMessage(Component.translatable("fortuna.insufficient", String.format(Locale.ROOT, "%,d", entry))); return;
            }
            VCoinsState.removeCoins(player.getUUID(), entry); r.start(action, RANDOM);
        } else if (action == 3 && r.active && r.tier < 5) {
            if (!r.risk(RANDOM.nextInt(100)) && r.anchor >= 0) give(player, reward(r, r.anchor, player.registryAccess()));
        } else if (action == 4 && r.active) {
            r.claim(); give(player, reward(r, r.tier, player.registryAccess()));
        } else if (action == 5 && r.active && !r.used && r.tier < 5) {
            r.useCharm(RANDOM);
        } else if (action >= 6 && action <= 8 && !r.active && r.fragments >= REDEEM) {
            r.redeem(REDEEM);
            FortunaRun redemption = new FortunaRun(); redemption.choices[3] = action - 6;
            give(player, reward(redemption, 3, player.registryAccess()));
        } else return;
        VCoinsState.checkpoint(player); VCoinsMod.syncCoins(player);
    }
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            RUNS.clear(); Path path = server.getWorldPath(LevelResource.ROOT).resolve("fortuna.json");
            if (Files.exists(path)) try (var reader = Files.newBufferedReader(path)) {
                Map<UUID, FortunaRun> loaded = GSON.fromJson(reader, new TypeToken<Map<UUID, FortunaRun>>(){}.getType());
                if (loaded != null) loaded.forEach((id, saved) -> RUNS.put(id, validateSavedRun(saved)));
            } catch (Exception e) { throw new IllegalStateException("Cannot load Fortuna saves", e); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUNS.clear());
    }

    public static void readPlayerState(UUID id, net.minecraft.world.level.storage.ValueInput input) {
        input.getString("veloria:fortuna").ifPresent(json -> {
            FortunaRun saved = validateSavedRun(GSON.fromJson(json, FortunaRun.class));
            RUNS.put(id, saved);
        });
    }

    private static FortunaRun validateSavedRun(FortunaRun saved) {
        // Older Fortuna saves had five tiers. Keep every existing outcome and
        // add a deterministic final-tier choice, never reroll a paid pact.
        if (saved != null && saved.choices != null && saved.choices.length == 5)
            saved.choices = Arrays.copyOf(saved.choices, 6);
        if (saved == null || saved.choices == null || saved.choices.length != 6
                    || saved.tier < 0 || saved.tier > 5 || saved.charm < 0 || saved.charm > 2
                    || saved.anchor < -1 || saved.anchor > 5 || saved.fragments < 0 || saved.fragments > 30000
                    || Arrays.stream(saved.choices).anyMatch(choice -> choice < 0 || choice > 2))
                throw new IllegalStateException("Invalid Fortuna player save");
        return saved;
    }

    public static void writePlayerState(UUID id, net.minecraft.world.level.storage.ValueOutput output) {
        // Payment, active pact, redeemed fragments and rewards share one player save.
        output.putString("veloria:fortuna", GSON.toJson(run(id)));
    }
}
