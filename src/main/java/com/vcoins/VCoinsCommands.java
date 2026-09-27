package com.vcoins;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import java.util.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

public final class VCoinsCommands {
    private static final String ARG_TARGETS = "targets";
    private static final String ARG_AMOUNT = "amount";

    private VCoinsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vcoins")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("get")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                    long coins = VCoinsState.getCoins(target.getUUID());
                                    context.getSource().sendSystemMessage(Component.translatable(
                                            "vcoins.command.balance", target.getName(), coins));
                                    return 1;
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .then(Commands.argument(ARG_AMOUNT, LongArgumentType.longArg(0))
                                        .executes(context -> {
                                            Collection<ServerPlayer> targets =
                                                    EntityArgument.getPlayers(context, ARG_TARGETS);
                                            long amount = LongArgumentType.getLong(context, ARG_AMOUNT);

                                            for (ServerPlayer target : targets) {
                                                VCoinsState.setCoins(target.getUUID(), amount);
                                                VCoinsMod.syncCoins(target);
                                            }
                                            context.getSource().sendSystemMessage(Component.translatable(
                                                    "vcoins.command.set_balance", targets.size(), amount));
                                            return 1;
                                        }))))
                .then(Commands.literal("add")
                        .then(Commands.argument(ARG_TARGETS, EntityArgument.players())
                                .then(Commands.argument(ARG_AMOUNT, LongArgumentType.longArg(1))
                                        .executes(context -> {
                                            Collection<ServerPlayer> targets =
                                                    EntityArgument.getPlayers(context, ARG_TARGETS);
                                            long amount = LongArgumentType.getLong(context, ARG_AMOUNT);

                                            for (ServerPlayer target : targets) {
                                                VCoinsState.addCoins(target.getUUID(), amount);
                                                VCoinsMod.syncCoins(target);
                                            }
                                            context.getSource().sendSystemMessage(Component.translatable(
                                                    "vcoins.command.add_balance", amount, targets.size()));
                                            return 1;
                                        }))))
                .then(Commands.literal("market")
                        .then(Commands.literal("reset")
                                .executes(context -> {
                                    VMarketEngine.reset(context.getSource().getServer());
                                    context.getSource().sendSystemMessage(Component.translatable(
                                            "vcoins.command.market_reset").withStyle(ChatFormatting.GREEN));
                                    return 1;
                                }))
                        .then(Commands.literal("status")
                                .executes(context -> {
                                    int count = VMarketEngine.getTrackedCount();
                                    context.getSource().sendSystemMessage(Component.translatable(
                                            "vcoins.command.market_status", count).withStyle(ChatFormatting.AQUA));
                                    return 1;
                                }))));

        dispatcher.register(Commands.literal("shop")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.openMenu(new SimpleMenuProvider(
                            (syncId, inventory, playerEntity) -> new VTradeScreenHandler(syncId, inventory),
                            Component.translatable("vcoins.title")
                    ));
                    VCoinsMod.syncCoins(player);
                    VMarketEngine.syncToPlayer(player);
                    return 1;
                }));

        dispatcher.register(Commands.literal("sell")
                .then(Commands.literal("hand")
                        .executes(context -> sellHand(context.getSource().getPlayerOrException())))
                .then(Commands.literal("all")
                        .executes(context -> sellInventory(context.getSource().getPlayerOrException()))));
    }

    private static int sellHand(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || stack.getCount() <= 0) {
            reject(player, "vcoins.command.empty_hand");
            return 0;
        }

        long unitPrice = getSellPrice(stack);
        if (unitPrice <= 0) {
            reject(player, "vcoins.command.cannot_sell");
            return 0;
        }

        int count = stack.getCount();
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Component itemName = stack.getHoverName();
        long value = safeMultiply(unitPrice, count);
        VCoinsState.addCoins(player.getUUID(), value);
        VCoinsMod.syncCoins(player);
        VMarketEngine.recordSell(VCoinsPricing.getMarketKey(stack), count);
        VMarketEngine.syncToPlayer(player);
        VMarketEngine.syncToActiveShoppers(player.level().getServer());
        VTradeScreenHandler.addBuyback(player, stack.copy());
        stack.setCount(0);

        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
        player.playSound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.7f, 1.25f);
        player.sendSystemMessage(Component.translatable("vcoins.command.sell_hand_success",
                count, itemName, value).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int sellInventory(ServerPlayer player) {
        long totalEarned = 0L;

        // Only sell the 36 main inventory and hotbar slots. Equipped items and
        // the offhand are intentionally left alone.
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.getCount() <= 0) {
                continue;
            }

            long unitPrice = getSellPrice(stack);
            if (unitPrice <= 0) {
                continue;
            }

            int count = stack.getCount();
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            VMarketEngine.recordSell(VCoinsPricing.getMarketKey(stack), count);
            totalEarned = safeAdd(totalEarned, safeMultiply(unitPrice, count));
            VTradeScreenHandler.addBuyback(player, stack.copy());
            stack.setCount(0);
        }

        if (totalEarned <= 0) {
            reject(player, "vcoins.command.nothing_to_sell");
            return 0;
        }

        VCoinsState.addCoins(player.getUUID(), totalEarned);
        VCoinsMod.syncCoins(player);
        VMarketEngine.syncToPlayer(player);
        VMarketEngine.syncToActiveShoppers(player.level().getServer());
        player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
        player.playSound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.7f, 1.25f);
        player.sendSystemMessage(Component.translatable("vcoins.command.sell_all_success",
                totalEarned).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static long getSellPrice(ItemStack stack) {
        return VCoinsPricing.getSellPrice(stack);
    }

    private static void reject(ServerPlayer player, String translationKey) {
        player.sendSystemMessage(Component.translatable(translationKey).withStyle(ChatFormatting.RED));
        player.playSound(SoundEvents.VILLAGER_NO, 0.9f, 1.0f);
    }

    private static long safeMultiply(long value, int count) {
        if (value <= 0 || count <= 0) {
            return 0L;
        }
        try {
            return Math.multiplyExact(value, (long) count);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
