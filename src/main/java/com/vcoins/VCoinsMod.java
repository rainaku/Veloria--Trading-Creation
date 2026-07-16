package com.vcoins;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class VCoinsMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("vcoins");
    public static final String MOD_ID = "vcoins";

    public static final ScreenHandlerType<VTradeScreenHandler> VTRADE_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(MOD_ID, "vtrade"),
            new ScreenHandlerType<>(VTradeScreenHandler::new, net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES)
    );
    public static final ScreenHandlerType<VDuplicateScreenHandler> VDUPLICATE_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(MOD_ID, "vduplicate"),
            new ScreenHandlerType<>(VDuplicateScreenHandler::new, net.minecraft.resource.featuretoggle.FeatureFlags.VANILLA_FEATURES)
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Veloria: Trading & Creation...");

        // Register custom payload for shop actions
        PayloadTypeRegistry.playC2S().register(ShopActionPayload.ID, ShopActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShopTransactionPayload.ID, ShopTransactionPayload.CODEC);
        
        ServerPlayNetworking.registerGlobalReceiver(ShopActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().currentScreenHandler instanceof VTradeScreenHandler shop) {
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
                if (context.player().currentScreenHandler instanceof VTradeScreenHandler shop) {
                    shop.handleTransaction(context.player(), payload.slotIndex(), payload.buyStack());
                }
            });
        });

        // Register custom payload for opening shop
        PayloadTypeRegistry.playC2S().register(OpenShopPayload.ID, OpenShopPayload.CODEC);
        
        ServerPlayNetworking.registerGlobalReceiver(OpenShopPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                context.player().openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                        (syncId, inv, p) -> new VTradeScreenHandler(syncId, inv),
                        Text.translatable("vcoins.title")
                ));
                context.player().playSound(SoundEvents.BLOCK_CHEST_OPEN, 0.65f, 1.1f);
                syncCoins(context.player());
            });
        });

        PayloadTypeRegistry.playC2S().register(OpenDuplicatePayload.ID, OpenDuplicatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenDuplicatePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (!(context.player().currentScreenHandler instanceof VTradeScreenHandler)) {
                    return;
                }
                context.player().openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                        (syncId, inv, player) -> new VDuplicateScreenHandler(syncId, inv),
                        Text.translatable("vcoins.duplicate.title")
                ));
                syncCoins(context.player());
            });
        });

        // Register Payloads
        PayloadTypeRegistry.playS2C().register(VCoinsSyncPayload.ID, VCoinsSyncPayload.CODEC);

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            VCoinsCommands.register(dispatcher);
        });

        // Initialize Pricing Engine
        VCoinsPricing.init();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            VCoinsPricing.calculateRecipes(server);
        });
        
        VCoinsState.registerEvents();
    }

    public static void syncCoins(ServerPlayerEntity player) {
        long coins = VCoinsState.getCoins(player.getUuid());
        ServerPlayNetworking.send(player, new VCoinsSyncPayload(coins));
    }
}
