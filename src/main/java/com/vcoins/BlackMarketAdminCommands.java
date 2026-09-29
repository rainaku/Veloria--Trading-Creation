package com.vcoins;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

public final class BlackMarketAdminCommands {
    private BlackMarketAdminCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var target = Commands.argument("player", EntityArgument.player());
        for (var stat : VBlackMarket.AdminStat.values()) {
            String name = stat.name().toLowerCase(java.util.Locale.ROOT);
            dispatcher.register(Commands.literal(name)
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> run(ctx, stat, "get"))
                            .then(Commands.argument("value", IntegerArgumentType.integer(0, VBlackMarket.adminStatLimit(stat)))
                                    .executes(ctx -> run(ctx, stat, "set")))
                            .then(Commands.literal("add")
                                    .then(Commands.argument("value", IntegerArgumentType.integer())
                                            .executes(ctx -> run(ctx, stat, "add"))))));
            target.then(Commands.literal(name)
                    .then(Commands.literal("get").executes(ctx -> run(ctx, stat, "get")))
                    .then(Commands.literal("reset").executes(ctx -> run(ctx, stat, "reset")))
                    .then(Commands.literal("set")
                            .then(Commands.argument("value", IntegerArgumentType.integer(0, VBlackMarket.adminStatLimit(stat)))
                                    .executes(ctx -> run(ctx, stat, "set"))))
                    .then(Commands.literal("add")
                            .then(Commands.argument("value", IntegerArgumentType.integer())
                                    .executes(ctx -> run(ctx, stat, "add")))));
        }
        dispatcher.register(Commands.literal("blackmarket")
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(target)));
    }

    private static int run(CommandContext<CommandSourceStack> context, VBlackMarket.AdminStat stat,
                           String operation) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        int value;
        if (operation.equals("get")) {
            value = VBlackMarket.getAdminStat(player.getUUID(), stat);
        } else {
            int requested = operation.equals("reset") ? 0 : IntegerArgumentType.getInteger(context, "value");
            value = VBlackMarket.changeAdminStat(player.getUUID(), stat, requested, operation.equals("add"));
            VBlackMarket.syncToPlayer(player);
        }
        context.getSource().sendSuccess(() -> Component.translatable("vcoins.command.blackmarket.admin_stat",
                player.getName(), stat.name().toLowerCase(java.util.Locale.ROOT), value), !operation.equals("get"));
        return 1;
    }
}
