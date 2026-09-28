package com.vcoins;

import java.util.*;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.storage.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Virtual, world-local boxes, saved atomically with player money and inventory. */
public final class VGiftBox {
    public static void showMenu(ServerPlayer player) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new VGiftBoxMenu(id, inv),
                Component.literal("Gift Box")));
        VCoinsMod.syncCoins(player);
    }
    static final long[] PRICES = {25_000, 250_000, 2_500_000, 25_000_000, 100_000_000};
    private static final Map<UUID, int[]> BOXES = new HashMap<>();
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    private static final Item[][] POOLS = {
        {Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_CHESTPLATE, Items.IRON_BOOTS},
        {Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS},
        {Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE},
        {Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_HELMET, Items.NETHERITE_BOOTS},
        {Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_AXE, Items.NETHERITE_PICKAXE}
    };
    static int quality(int roll) {
        if (roll < 0 || roll >= 1000) throw new IllegalArgumentException("roll");
        return roll < 750 ? 0 : roll < 950 ? 1 : roll < 995 ? 2 : 3;
    }
    static int[] boxes(UUID id) { return BOXES.computeIfAbsent(id, ignored -> new int[5]); }
    static boolean purchase(UUID id, int tier) {
        if (tier < 0 || tier >= 5 || boxes(id)[tier] >= 1000 || VCoinsState.getCoins(id) < PRICES[tier]) return false;
        VCoinsState.removeCoins(id, PRICES[tier]); boxes(id)[tier]++; return true;
    }
    public static void read(UUID id, ValueInput input) {
        int[] counts = new int[5];
        for (int i = 0; i < 5; i++) counts[i] = Math.clamp(input.getIntOr("veloria:giftbox_" + i, 0), 0, 1000);
        BOXES.put(id, counts);
    }
    public static void write(UUID id, ValueOutput output) {
        for (int i = 0; i < 5; i++) output.putInt("veloria:giftbox_" + i, boxes(id)[i]);
    }
    public static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("giftbox").executes(ctx -> {
            var player = ctx.getSource().getPlayerOrException();
            showMenu(player);
            player.sendSystemMessage(Component.literal("Gift Box: /giftbox buy <1-5> | /giftbox open <1-5>"));
            player.sendSystemMessage(Component.translatable("vcoins.gift.odds"));
            for (int i = 0; i < 5; i++) player.sendSystemMessage(Component.literal(
                    "Tier " + (i + 1) + " • " + String.format(Locale.ROOT, "%,d", PRICES[i]) + " coins • " + boxes(player.getUUID())[i] + " box"));
            return 1;
        }).then(Commands.literal("buy").then(Commands.argument("tier", IntegerArgumentType.integer(1, 5)).executes(ctx -> {
            var player = ctx.getSource().getPlayerOrException(); int tier = IntegerArgumentType.getInteger(ctx, "tier") - 1;
            if (!purchase(player.getUUID(), tier)) { player.sendSystemMessage(Component.translatable("vcoins.gift.buy_failed")); return 0; }
            VCoinsState.checkpoint(player); VCoinsMod.syncCoins(player);
            player.sendSystemMessage(Component.translatable("vcoins.gift.bought", tier + 1)); return 1;
        }))).then(Commands.literal("open").then(Commands.argument("tier", IntegerArgumentType.integer(1, 5)).executes(ctx ->
                open(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "tier") - 1)))));
    }
    static int open(ServerPlayer player, int tier) {
        var counts = boxes(player.getUUID());
        int slot = player.getInventory().getFreeSlot();
        if (counts[tier] <= 0 || slot < 0 || !player.isAlive()) {
            player.sendSystemMessage(Component.translatable("vcoins.gift.open_failed")); return 0;
        }
        int quality = quality(RANDOM.nextInt(1000));
        ItemStack reward = new ItemStack(POOLS[tier][RANDOM.nextInt(POOLS[tier].length)]);
        var candidates = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .filter(h -> h.value().canEnchant(reward))
                .filter(h -> !h.getRegisteredName().contains("curse")).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates, RANDOM);
        var chosen = new ArrayList<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>>();
        int limit = quality == 3 ? Integer.MAX_VALUE : Math.min(3, quality + tier / 2);
        for (var enchant : candidates) {
            if (chosen.size() >= limit) break;
            if (chosen.stream().anyMatch(other -> !net.minecraft.world.item.enchantment.Enchantment.areCompatible(other, enchant))) continue;
            int max = enchant.value().getMaxLevel();
            reward.enchant(enchant, quality == 3 ? max : Math.max(1, Math.min(max, 1 + quality + tier / 2)));
            chosen.add(enchant);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, reward, tag -> {
            tag.putBoolean("VNoTrade", true);
            tag.putString("VGiftRewardId", UUID.randomUUID().toString());
        });
        reward.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("vcoins.gift.origin", tier + 1),
                Component.translatable("vcoins.gift.bound"))));
        counts[tier]--;
        player.getInventory().setItem(slot, reward);
        VCoinsState.checkpoint(player);
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu instanceof VGiftBoxMenu menu) menu.revealed(reward, quality, tier);
        player.sendSystemMessage(Component.translatable("vcoins.gift.opened", reward.getHoverName(), quality + 1));
        return 1;
    }
    public static void registerLifecycle() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> BOXES.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BOXES.clear());
    }
}
