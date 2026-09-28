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

/** Physical gift Shulkers; legacy virtual balances remain redeemable. */
public final class VGiftBox {
    public static void showMenu(ServerPlayer player) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new VGiftBoxMenu(id, inv),
                Component.literal("Gift Box")));
        VCoinsMod.syncCoins(player);
    }
    static final long[] PRICES = {25_000, 250_000, 2_500_000, 25_000_000, 100_000_000};
    private static final Map<UUID, int[]> BOXES = new HashMap<>();
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    // Each row is an exact distribution out of 1000, from standard to jackpot.
    private static final int[][] WEIGHTS = {
        {520, 300, 130, 50}, {480, 320, 150, 50}, {440, 330, 180, 50},
        {400, 340, 210, 50}, {360, 350, 240, 50}
    };
    private static final int[] MIN_VALUE_PERCENT = {30, 110, 180, 350};
    private static final int[] MAX_VALUE_PERCENT = {40, 140, 220, 450};
    // Nineteen ordinary item types plus one reserved jackpot per tier.
    private static final Item[][] BOX_POOLS = {
        {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COAL, Items.COPPER_INGOT, Items.REDSTONE,
         Items.LAPIS_LAZULI, Items.QUARTZ, Items.OBSIDIAN, Items.EXPERIENCE_BOTTLE, Items.ENDER_PEARL,
         Items.FIREWORK_ROCKET, Items.GOLDEN_CARROT, Items.COOKED_BEEF, Items.LEATHER, Items.GUNPOWDER,
         Items.STRING, Items.ARROW, Items.IRON_BLOCK, Items.GLASS, Items.DIAMOND_SWORD},
        {Items.IRON_BLOCK, Items.GOLD_INGOT, Items.DIAMOND, Items.GOLD_BLOCK, Items.EXPERIENCE_BOTTLE,
         Items.ENDER_PEARL, Items.FIREWORK_ROCKET, Items.GOLDEN_CARROT, Items.OBSIDIAN, Items.CRYING_OBSIDIAN,
         Items.IRON_PICKAXE, Items.IRON_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SWORD,
         Items.DIAMOND_HELMET, Items.DIAMOND_BOOTS, Items.IRON_CHESTPLATE, Items.LAPIS_BLOCK, Items.DIAMOND_BLOCK},
        {Items.DIAMOND_BLOCK, Items.GOLD_BLOCK, Items.IRON_BLOCK, Items.DIAMOND, Items.EXPERIENCE_BOTTLE,
         Items.CRYING_OBSIDIAN, Items.OBSIDIAN, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SWORD,
         Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
         Items.BREEZE_ROD, Items.ECHO_SHARD, Items.TRIAL_KEY, Items.GOLDEN_APPLE, Items.NETHERITE_SCRAP, Items.SHULKER_SHELL},
        {Items.NETHERITE_SCRAP, Items.NETHERITE_INGOT, Items.DIAMOND_BLOCK, Items.GOLD_BLOCK, Items.ECHO_SHARD,
         Items.BREEZE_ROD, Items.TRIAL_KEY, Items.OMINOUS_TRIAL_KEY, Items.SHULKER_SHELL, Items.TOTEM_OF_UNDYING,
         Items.TRIDENT, Items.NETHERITE_PICKAXE, Items.NETHERITE_SWORD, Items.NETHERITE_HELMET,
         Items.NETHERITE_BOOTS, Items.NETHERITE_AXE, Items.GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE, Items.CRYING_OBSIDIAN, Items.NETHER_STAR},
        {Items.NETHERITE_SCRAP, Items.NETHERITE_INGOT, Items.DIAMOND_BLOCK, Items.ECHO_SHARD, Items.SHULKER_SHELL,
         Items.TOTEM_OF_UNDYING, Items.TRIDENT, Items.NETHER_STAR, Items.CONDUIT, Items.BEACON,
         Items.NETHERITE_PICKAXE, Items.NETHERITE_SWORD, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
         Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Items.NETHERITE_AXE, Items.HEAVY_CORE, Items.MACE, Items.ELYTRA}
    };
    static List<Item> pool(int tier) { return List.of(BOX_POOLS[tier]); }
    static Item jackpot(int tier) { return BOX_POOLS[tier][19]; }
    static List<ItemStack> boxRewards(int tier, int quality, Random random) {
        long minimum = minimumValue(tier, quality), maximum = maximumValue(tier, quality);
        var regular = new ArrayList<>(pool(tier).subList(0, 19));
        for (int attempt = 0; attempt < 2000; attempt++) {
            Collections.shuffle(regular, random);
            var result = new ArrayList<ItemStack>();
            if (quality == 3) result.add(new ItemStack(jackpot(tier)));
            for (int i = 0; result.size() < 5; i++) result.add(new ItemStack(regular.get(i)));
            long total = result.stream().mapToLong(VCoinsPricing::getReferencePrice).sum();
            if (total > maximum) continue;
            // Fill quantities without replacing types or exceeding a single stack per type.
            var order = new ArrayList<>(result);
            Collections.shuffle(order, random);
            for (ItemStack stack : order) {
                long unit = VCoinsPricing.getReferencePrice(stack);
                int capacity = (int) Math.min(stack.getMaxStackSize() - 1, (maximum - total) / unit);
                if (capacity <= 0) continue;
                int needed = (int) Math.max(0, (minimum - total + unit - 1) / unit);
                int add = Math.min(capacity, needed);
                stack.grow(add);
                total += unit * add;
            }
            if (total < minimum) continue;
            for (ItemStack stack : result) {
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("VGiftRewardId", UUID.randomUUID().toString()));
                stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("vcoins.gift.origin", tier + 1),
                        Component.translatable("vcoins.gift.tradeable"))));
            }
            return result;
        }
        throw new IllegalStateException("Cannot fill five distinct gift rewards within tier budget: " + tier + "/" + quality);
    }
    static int weight(int tier, int quality) { return WEIGHTS[tier][quality]; }
    static long minimumValue(int tier, int quality) { return PRICES[tier] * MIN_VALUE_PERCENT[quality] / 100; }
    static long maximumValue(int tier, int quality) { return PRICES[tier] * MAX_VALUE_PERCENT[quality] / 100; }
    static String odds(int tier) {
        return String.format(Locale.ROOT, "%s%% / %s%% / %s%% / %s%%",
                WEIGHTS[tier][0] / 10, WEIGHTS[tier][1] / 10, WEIGHTS[tier][2] / 10, WEIGHTS[tier][3] / 10);
    }
    static int quality(int tier, int roll) {
        if (tier < 0 || tier >= PRICES.length || roll < 0 || roll >= 1000) throw new IllegalArgumentException("gift roll");
        for (int q = 0; q < 4; q++) {
            if (roll < WEIGHTS[tier][q]) return q;
            roll -= WEIGHTS[tier][q];
        }
        throw new IllegalStateException("Gift weights must total 1000");
    }
    static int[] boxes(UUID id) { return BOXES.computeIfAbsent(id, ignored -> new int[5]); }
    static final Item[] BOX_ITEMS = {net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:light_blue_shulker_box")), net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:lime_shulker_box")),
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:blue_shulker_box")), net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:purple_shulker_box")), net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("minecraft:yellow_shulker_box"))};
    static ItemStack createBox(int tier, Random random, net.minecraft.core.HolderLookup.Provider registries) {
        int quality = quality(tier, random.nextInt(1000));
        List<ItemStack> prizes = boxRewards(tier, quality, random);
        ItemStack box = new ItemStack(BOX_ITEMS[tier]);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(prizes));
        box.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        box.set(DataComponents.CUSTOM_NAME, Component.translatable("vcoins.gift.box_name", tier + 1));
        box.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("vcoins.gift.history." + tier + ".1").withStyle(net.minecraft.ChatFormatting.GRAY),
                Component.translatable("vcoins.gift.history." + tier + ".2").withStyle(net.minecraft.ChatFormatting.GRAY),
                Component.translatable("vcoins.gift.use_hint").withStyle(net.minecraft.ChatFormatting.YELLOW))));
        CustomData.update(DataComponents.CUSTOM_DATA, box, tag -> {
            tag.putString("VGiftBoxId", UUID.randomUUID().toString());
            tag.putInt("VGiftBoxTier", tier);
            tag.putInt("VGiftBoxQuality", quality);
        });
        return box;
    }
    public static boolean isGiftBox(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains("VGiftBoxId");
    }
    /** Never mutate the authoritative stack: integrated servers share a JVM with the client. */
    public static ItemStack networkView(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        if (!isGiftBox(stack)) {
            ItemStack nested = stack;
            for (var component : stack.getComponents()) {
                if (component.value() instanceof ContainerComponent<?> contents) {
                    var originals = contents.itemCopies().toList();
                    var sanitized = originals.stream().map(VGiftBox::networkView).toList();
                    boolean changed = false;
                    for (int i = 0; i < originals.size(); i++) changed |= sanitized.get(i) != originals.get(i);
                    if (changed) {
                        if (nested == stack) nested = stack.copy();
                        replaceContents(nested, component.type(), contents.copyWithContents(sanitized.stream()));
                    }
                }
            }
            return nested;
        }
        ItemStack visible = stack.copy();
        visible.remove(DataComponents.CONTAINER);
        CustomData.update(DataComponents.CUSTOM_DATA, visible, tag -> tag.remove("VGiftBoxQuality"));
        return visible;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void replaceContents(ItemStack stack, net.minecraft.core.component.DataComponentType type, Object value) {
        stack.set(type, value);
    }
    static int boxTier(ItemStack stack) {
        if (!isGiftBox(stack)) return -1;
        int tier = stack.get(DataComponents.CUSTOM_DATA).copyTag().getIntOr("VGiftBoxTier", -1);
        return tier >= 0 && tier < BOX_ITEMS.length && stack.is(BOX_ITEMS[tier]) ? tier : -1;
    }
    static ItemStack purchaseBox(UUID id, int tier, boolean room, net.minecraft.core.HolderLookup.Provider registries) {
        if (!room || tier < 0 || tier >= PRICES.length || VCoinsState.getCoins(id) < PRICES[tier]) return ItemStack.EMPTY;
        ItemStack box = createBox(tier, RANDOM, registries);
        VCoinsState.removeCoins(id, PRICES[tier]);
        return box;
    }
    static boolean purchase(ServerPlayer player, int tier) {
        int slot = player.getInventory().getFreeSlot();
        ItemStack box = purchaseBox(player.getUUID(), tier, slot >= 0 && player.isAlive(), player.registryAccess());
        if (box.isEmpty()) return false;
        player.getInventory().setItem(slot, box);
        VCoinsState.checkpoint(player);
        VCoinsMod.syncCoins(player);
        player.inventoryMenu.broadcastChanges();
        return true;
    }
    static int owned(ServerPlayer player, int tier) {
        int count = boxes(player.getUUID())[tier];
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (boxTier(stack) == tier) count += stack.getCount();
        }
        return count;
    }
    static List<ItemStack> consumeBox(ItemStack box) {
        if (boxTier(box) < 0 || box.getCount() != 1) return List.of();
        List<ItemStack> contents = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .itemCopies().filter(stack -> !stack.isEmpty()).toList();
        if (contents.isEmpty()) return List.of();
        box.shrink(1);
        return contents;
    }
    private static int burst(ServerPlayer player, ItemStack box) {
        if (!player.isAlive() || player.isSpectator()) return 0;
        List<ItemStack> contents = consumeBox(box);
        if (contents.isEmpty()) return 0;
        for (ItemStack item : contents) player.drop(item, false, net.minecraft.util.Prediction.SERVER_ONLY);
        var level = player.level();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                player.getX(), player.getY() + 1, player.getZ(), 60, 0.45, 0.55, 0.45, 0.16);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.1f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.SHULKER_BOX_OPEN, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1f);
        VCoinsState.checkpoint(player);
        player.inventoryMenu.broadcastChanges();
        return 1;
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
                    "Tier " + (i + 1) + " • " + String.format(Locale.ROOT, "%,d", PRICES[i]) + " coins • " + owned(player, i) + " box"));
            return 1;
        }).then(Commands.literal("buy").then(Commands.argument("tier", IntegerArgumentType.integer(1, 5)).executes(ctx -> {
            var player = ctx.getSource().getPlayerOrException(); int tier = IntegerArgumentType.getInteger(ctx, "tier") - 1;
            if (!purchase(player, tier)) { player.sendSystemMessage(Component.translatable("vcoins.gift.buy_failed")); return 0; }
            VCoinsState.checkpoint(player); VCoinsMod.syncCoins(player);
            player.sendSystemMessage(Component.translatable("vcoins.gift.bought", tier + 1)); return 1;
        }))).then(Commands.literal("open").then(Commands.argument("tier", IntegerArgumentType.integer(1, 5)).executes(ctx ->
                open(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "tier") - 1)))));
    }
    static int open(ServerPlayer player, int tier) {
        if (tier < 0 || tier >= PRICES.length) return 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack box = player.getInventory().getItem(slot);
            if (boxTier(box) == tier) return burst(player, box);
        }
        // Redeem previously purchased virtual boxes without charging again.
        var counts = boxes(player.getUUID());
        if (counts[tier] > 0 && player.isAlive() && !player.isSpectator()) {
            ItemStack legacy = createBox(tier, RANDOM, player.registryAccess());
            counts[tier]--;
            return burst(player, legacy);
        }
        player.sendSystemMessage(Component.translatable("vcoins.gift.open_failed"));
        return 0;
    }
    public static void registerLifecycle() {
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack box = player.getItemInHand(hand);
            if (!isGiftBox(box)) return net.minecraft.world.InteractionResult.PASS;
            if (player instanceof ServerPlayer server) burst(server, box);
            return net.minecraft.world.InteractionResult.SUCCESS;
        });
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack box = player.getItemInHand(hand);
            if (!isGiftBox(box)) return net.minecraft.world.InteractionResult.PASS;
            if (player instanceof ServerPlayer server) burst(server, box);
            return net.minecraft.world.InteractionResult.SUCCESS;
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> BOXES.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BOXES.clear());
    }
}
