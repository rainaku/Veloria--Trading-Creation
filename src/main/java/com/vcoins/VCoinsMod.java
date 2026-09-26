package com.vcoins;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class VCoinsMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("vcoins");
    public static final String MOD_ID = "vcoins";

    public static final MenuType<VTradeScreenHandler> VTRADE_SCREEN_HANDLER = Registry.register(
            BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(MOD_ID, "vtrade"),
            new MenuType<>(VTradeScreenHandler::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET)
    );
    public static final MenuType<VDuplicateScreenHandler> VDUPLICATE_SCREEN_HANDLER = Registry.register(
            BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(MOD_ID, "vduplicate"),
            new MenuType<>(VDuplicateScreenHandler::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET)
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Veloria: Trading & Creation...");

        // Register custom payload for shop actions
        PayloadTypeRegistry.serverboundPlay().register(ShopActionPayload.ID, ShopActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ShopTransactionPayload.ID, ShopTransactionPayload.CODEC);
        
        ServerPlayNetworking.registerGlobalReceiver(ShopActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof VTradeScreenHandler shop) {
                    if (payload.action().equals("SCROLL")) {
                        try {
                            shop.setScrollOffset(Integer.parseInt(payload.data()));
                        } catch (Exception ignored) {}
                    } else if (payload.action().equals("TAB")) {
                        try {
                            shop.setCategory(ShopCategory.valueOf(payload.data()));
                        } catch (Exception ignored) {}
                    } else if (payload.action().equals("SEARCH")) {
                        shop.setSearchQuery(payload.data());
                    } else if (payload.action().equals("SELL_ALL")) {
                        shop.sellAll();
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ShopTransactionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof VTradeScreenHandler shop) {
                    shop.handleTransaction(context.player(), payload.slotIndex(), payload.buyStack());
                }
            });
        });

        // Register custom payload for opening shop
        PayloadTypeRegistry.serverboundPlay().register(OpenShopPayload.ID, OpenShopPayload.CODEC);
        
        ServerPlayNetworking.registerGlobalReceiver(OpenShopPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                context.player().openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (syncId, inv, p) -> new VTradeScreenHandler(syncId, inv),
                        Component.translatable("vcoins.title")
                ));
                context.player().playSound(SoundEvents.CHEST_OPEN, 0.65f, 1.1f);
                syncCoins(context.player());
            });
        });

        PayloadTypeRegistry.serverboundPlay().register(OpenDuplicatePayload.ID, OpenDuplicatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenDuplicatePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (!(context.player().containerMenu instanceof VTradeScreenHandler)) {
                    return;
                }
                context.player().openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (syncId, inv, player) -> new VDuplicateScreenHandler(syncId, inv),
                        Component.translatable("vcoins.duplicate.title")
                ));
                syncCoins(context.player());
            });
        });

        // Register Payloads
        PayloadTypeRegistry.clientboundPlay().register(VCoinsSyncPayload.ID, VCoinsSyncPayload.CODEC);

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            VCoinsCommands.register(dispatcher);
        });

        // Initialize Pricing Engine on server start or on demand
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            VCoinsPricing.ensureInitialized();
            VCoinsPricing.calculateRecipes(server);
        });
        
        VCoinsState.registerEvents();
        VBlackMarket.registerEvents();
    }

    public static void syncCoins(ServerPlayer player) {
        long coins = VCoinsState.getCoins(player.getUUID());
        ServerPlayNetworking.send(player, new VCoinsSyncPayload(coins));
    }
}
