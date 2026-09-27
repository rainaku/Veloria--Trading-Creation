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
    public static final MenuType<VBlackMarketScreenHandler> VBLACK_MARKET_SCREEN_HANDLER = Registry.register(
            BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(MOD_ID, "vblack_market"),
            new MenuType<>(VBlackMarketScreenHandler::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET)
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Veloria: Trading & Creation...");

        registerActionNetworking();
        registerMenuNetworking();

        // Register Payloads
        PayloadTypeRegistry.clientboundPlay().register(VCoinsSyncPayload.ID, VCoinsSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BlackMarketSyncPayload.ID, BlackMarketSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MarketSyncPayload.ID, MarketSyncPayload.CODEC);

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            VCoinsCommands.register(dispatcher);
        });

        // Initialize Pricing Engine on server start or on demand
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            VCoinsPricing.ensureInitialized();
            VCoinsPricing.calculateRecipes(server);
            VMarketEngine.load(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            VMarketEngine.save(server);
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Reconcile live quotes every second; trades also push immediate updates.
            if (server.getTickCount() % 20 == 0) {
                VMarketEngine.tick(server);
            }
        });
        
        VCoinsState.registerEvents();
        VBlackMarket.registerEvents();
    }

    private static void registerActionNetworking() {
        PayloadTypeRegistry.serverboundPlay().register(ShopActionPayload.ID, ShopActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ShopTransactionPayload.ID, ShopTransactionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BlackMarketBuyPayload.ID, BlackMarketBuyPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BlackMarketRevealPayload.ID, BlackMarketRevealPayload.CODEC);
        
        ServerPlayNetworking.registerGlobalReceiver(ShopActionPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof VTradeScreenHandler shop) {
                    handleShopAction(shop, payload.action(), payload.data());
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

        ServerPlayNetworking.registerGlobalReceiver(BlackMarketBuyPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().containerMenu instanceof VBlackMarketScreenHandler market) {
                    market.handlePurchase(context.player(), payload.slotIndex(), payload.buyStack());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(BlackMarketRevealPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                VBlackMarket.revealCard(context.player().getUUID(), payload.slotIndex());
                if (context.server() != null) {
                    VBlackMarket.save(context.server());
                }
                VBlackMarket.syncToPlayer(context.player());
            });
        });
    }

    private static void handleShopAction(VTradeScreenHandler shop, String action, String data) {
        if ("SCROLL".equals(action)) {
            try {
                shop.setScrollOffset(Integer.parseInt(data));
            } catch (Exception ignored) {}
        } else if ("TAB".equals(action)) {
            try {
                shop.setCategory(ShopCategory.valueOf(data));
            } catch (Exception ignored) {}
        } else if ("SEARCH".equals(action)) {
            shop.setSearchQuery(data);
        } else if ("SELL_ALL".equals(action)) {
            shop.sellAll();
        }
    }

    private static void registerMenuNetworking() {
        PayloadTypeRegistry.serverboundPlay().register(OpenShopPayload.ID, OpenShopPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenShopPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                context.player().openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (syncId, inv, p) -> new VTradeScreenHandler(syncId, inv),
                        Component.translatable("vcoins.title")
                ));
                context.player().playSound(SoundEvents.CHEST_OPEN, 0.65f, 1.1f);
                syncCoins(context.player());
                VMarketEngine.syncToPlayer(context.player());
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

        PayloadTypeRegistry.serverboundPlay().register(OpenBlackMarketPayload.ID, OpenBlackMarketPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenBlackMarketPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                context.player().openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (syncId, inv, player) -> new VBlackMarketScreenHandler(syncId, inv),
                        Component.translatable("vcoins.black_market.title")
                ));
                context.player().playSound(SoundEvents.PORTAL_TRAVEL, 0.35f, 1.8f);
                context.player().playSound(SoundEvents.CHEST_OPEN, 0.65f, 1.2f);
                syncCoins(context.player());
            });
        });
    }

    public static void syncCoins(ServerPlayer player) {
        long coins = VCoinsState.getCoins(player.getUUID());
        ServerPlayNetworking.send(player, new VCoinsSyncPayload(coins));
    }
}
