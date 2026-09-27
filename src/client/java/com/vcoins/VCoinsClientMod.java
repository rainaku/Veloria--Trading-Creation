package com.vcoins;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class VCoinsClientMod implements ClientModInitializer {
    private static final KeyMapping.Category VTRADING_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "main")
    );

    @Override
    public void onInitializeClient() {
        System.out.println("Initializing Veloria client...");
        
        VMarketEngine.registerClientThread();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> VMarketEngine.clearClientSync());
        registerScreens();
        registerKeybindings();
        registerNetworking();
        registerTooltips();
    }

    private static void registerScreens() {
        net.minecraft.client.gui.screens.MenuScreens.register(VCoinsMod.VTRADE_SCREEN_HANDLER, VTradeScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(VCoinsMod.VDUPLICATE_SCREEN_HANDLER, VDuplicateScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(VCoinsMod.VBLACK_MARKET_SCREEN_HANDLER, VBlackMarketScreen::new);
    }

    private static void registerKeybindings() {
        KeyMapping openShopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.vcoins.open_shop", 
            InputConstants.Type.KEYBOARD, 
            InputConstants.KEY_B, 
            VTRADING_CATEGORY
        ));

        KeyMapping motionKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.vcoins.reduced_motion", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), VTRADING_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (motionKey.consumeClick()) {
                boolean reduced = VCoinsPurchaseConfirm.toggleReducedMotion();
                if (client.player != null) client.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(
                        reduced ? "vcoins.motion.reduced" : "vcoins.motion.full"));
            }
            while (openShopKey.consumeClick()) {
                ClientPlayNetworking.send(new OpenShopPayload());
            }
        });
    }

    private static void registerNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(VCoinsSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    VeloriaMenuEffects.balanceChanged(VCoinsState.getCoins(context.client().player.getUUID()), payload.coins());
                    VCoinsState.setCoins(context.client().player.getUUID(), payload.coins());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(BlackMarketSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                VBlackMarketScreen.handleSyncPayload(payload);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(MarketSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                VMarketEngine.applyClientSync(payload.serverEpochSecond(), payload.volumes());
            });
        });
    }

    private static void registerTooltips() {
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof VBlackMarketScreenHandler market) {
                for (int i = 0; i < VBlackMarketScreenHandler.MARKET_SLOT_COUNT; i++) {
                    if (market.slots.get(i).getItem() == stack) {
                        long day = VBlackMarketScreen.getSyncedDay();
                        int seq = VBlackMarketScreen.getSyncedResetSequence();
                        lines.add(net.minecraft.network.chat.Component.translatable("vcoins.black_market.buy_price",
                                String.format(java.util.Locale.ROOT, "%,d", VBlackMarket.getDiscountedPrice(stack, day, seq)))
                                .withStyle(net.minecraft.ChatFormatting.YELLOW));
                        lines.add(net.minecraft.network.chat.Component.translatable("vcoins.black_market.discount",
                                VBlackMarket.getDiscountPercent(stack, day, seq)).withStyle(net.minecraft.ChatFormatting.GREEN));
                        return;
                    }
                }
            }
            long sellPrice = VCoinsPricing.getSellPrice(stack);
            long buyPrice = VCoinsPricing.getPrice(stack);

            if (buyPrice <= 0 && sellPrice <= 0) {
                return;
            }

            for (net.minecraft.network.chat.Component line : lines) {
                if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && (text.getKey().equals("vcoins.tooltip.buy_price")
                        || text.getKey().equals("vcoins.tooltip.sell_price"))) {
                    return;
                }
            }

            VMarketEngine.MarketTrend trend = VMarketEngine.getTrend(stack);

            lines.add(net.minecraft.network.chat.Component.empty());
            if (buyPrice > 0) {
                net.minecraft.network.chat.MutableComponent buyComp = net.minecraft.network.chat.Component.translatable(
                        "vcoins.tooltip.buy_price", String.format(java.util.Locale.ROOT, "%,d", buyPrice))
                        .withStyle(net.minecraft.ChatFormatting.YELLOW);
                buyComp.append(net.minecraft.network.chat.Component.literal(" ")).append(trend.getBadge());
                lines.add(buyComp);
            }
            if (sellPrice > 0) {
                net.minecraft.network.chat.MutableComponent sellComp = net.minecraft.network.chat.Component.translatable(
                        "vcoins.tooltip.sell_price", String.format(java.util.Locale.ROOT, "%,d", sellPrice))
                        .withStyle(net.minecraft.ChatFormatting.GREEN);
                sellComp.append(net.minecraft.network.chat.Component.literal(" ")).append(VMarketEngine.getSellTrend(stack).getBadge());
                lines.add(sellComp);
            }
        });
    }
}
