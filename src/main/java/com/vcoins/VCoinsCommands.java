package com.vcoins;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

public final class VCoinsCommands {
    private VCoinsCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("vcoins")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("get")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                .executes(context -> {
                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");
                                    long coins = VCoinsState.getCoins(target.getUuid());
                                    context.getSource().sendMessage(Text.translatable(
                                            "vcoins.command.balance", target.getName(), coins));
                                    return 1;
                                })))
                .then(CommandManager.literal("set")
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .then(CommandManager.argument("amount", LongArgumentType.longArg(0))
                                        .executes(context -> {
                                            Collection<ServerPlayerEntity> targets =
                                                    EntityArgumentType.getPlayers(context, "targets");
                                            long amount = LongArgumentType.getLong(context, "amount");

                                            for (ServerPlayerEntity target : targets) {
                                                VCoinsState.setCoins(target.getUuid(), amount);
                                                VCoinsMod.syncCoins(target);
                                            }
                                            context.getSource().sendMessage(Text.translatable(
                                                    "vcoins.command.set_balance", targets.size(), amount));
                                            return 1;
                                        }))))
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                                .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                                        .executes(context -> {
                                            Collection<ServerPlayerEntity> targets =
                                                    EntityArgumentType.getPlayers(context, "targets");
                                            long amount = LongArgumentType.getLong(context, "amount");

                                            for (ServerPlayerEntity target : targets) {
                                                VCoinsState.addCoins(target.getUuid(), amount);
                                                VCoinsMod.syncCoins(target);
                                            }
                                            context.getSource().sendMessage(Text.translatable(
                                                    "vcoins.command.add_balance", amount, targets.size()));
                                            return 1;
                                        })))));

        dispatcher.register(CommandManager.literal("shop")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                    player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                            (syncId, inventory, playerEntity) -> new VTradeScreenHandler(syncId, inventory),
                            Text.translatable("vcoins.title")
                    ));
                    VCoinsMod.syncCoins(player);
                    return 1;
                }));

        dispatcher.register(CommandManager.literal("sell")
                .then(CommandManager.literal("hand")
                        .executes(context -> sellHand(context.getSource().getPlayerOrThrow())))
                .then(CommandManager.literal("all")
                        .executes(context -> sellInventory(context.getSource().getPlayerOrThrow()))));
    }

    private static int sellHand(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            reject(player, "vcoins.command.empty_hand");
            return 0;
        }

        long unitPrice = getSellPrice(stack);
        if (unitPrice <= 0) {
            reject(player, "vcoins.command.cannot_sell");
            return 0;
        }

        int count = stack.getCount();
        Text itemName = stack.getName();
        long value = safeMultiply(unitPrice, count);
        VCoinsState.addCoins(player.getUuid(), value);
        VCoinsMod.syncCoins(player);
        VTradeScreenHandler.addBuyback(player, stack);
        stack.setCount(0);

        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("vcoins.command.sell_hand_success",
                count, itemName, value).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int sellInventory(ServerPlayerEntity player) {
        long totalEarned = 0L;

        // Only sell the 36 main inventory and hotbar slots. Equipped items and
        // the offhand are intentionally left alone.
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }

            long unitPrice = getSellPrice(stack);
            if (unitPrice <= 0) {
                continue;
            }

            totalEarned = safeAdd(totalEarned, safeMultiply(unitPrice, stack.getCount()));
            VTradeScreenHandler.addBuyback(player, stack);
            stack.setCount(0);
        }

        if (totalEarned <= 0) {
            reject(player, "vcoins.command.nothing_to_sell");
            return 0;
        }

        VCoinsState.addCoins(player.getUuid(), totalEarned);
        VCoinsMod.syncCoins(player);
        player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        player.sendMessage(Text.translatable("vcoins.command.sell_all_success",
                totalEarned).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static long getSellPrice(ItemStack stack) {
        return VCoinsPricing.getSellPrice(stack);
    }

    private static void reject(ServerPlayerEntity player, String translationKey) {
        player.sendMessage(Text.translatable(translationKey).formatted(Formatting.RED), false);
        player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
    }

    private static long safeMultiply(long value, int count) {
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
