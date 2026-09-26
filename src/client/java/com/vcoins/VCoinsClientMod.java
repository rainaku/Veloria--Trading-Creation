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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openShopKey.consumeClick()) {
                ClientPlayNetworking.send(new OpenShopPayload());
            }
        });
    }

    private static void registerNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(VCoinsSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
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
                VMarketEngine.applyClientSync(payload.serverEpochSecond(), payload.volumeModifiers());
            });
        });
    }

    private static void registerTooltips() {
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            long sellPrice = VCoinsPricing.getSellPrice(stack);
            long buyPrice = VCoinsPricing.getPrice(stack);

            if (buyPrice <= 0 && sellPrice <= 0) {
                return;
            }

            for (net.minecraft.network.chat.Component line : lines) {
                if (line.getString().contains("Velicoins")) {
                    return;
                }
            }

            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            VMarketEngine.MarketTrend trend = VMarketEngine.getTrend(itemId);

            lines.add(net.minecraft.network.chat.Component.empty());
            if (buyPrice > 0) {
                net.minecraft.network.chat.MutableComponent buyComp = net.minecraft.network.chat.Component.translatable(
                        "vcoins.tooltip.buy_price", String.format(java.util.Locale.ROOT, "%,d", buyPrice))
                        .withStyle(net.minecraft.ChatFormatting.YELLOW);
                if (trend.percentChange() != 0) {
                    buyComp.append(net.minecraft.network.chat.Component.literal(" ")).append(trend.getBadge());
                }
                lines.add(buyComp);
            }
            if (sellPrice > 0) {
                net.minecraft.network.chat.MutableComponent sellComp = net.minecraft.network.chat.Component.translatable(
                        "vcoins.tooltip.sell_price", String.format(java.util.Locale.ROOT, "%,d", sellPrice))
                        .withStyle(net.minecraft.ChatFormatting.GREEN);
                if (trend.percentChange() != 0) {
                    sellComp.append(net.minecraft.network.chat.Component.literal(" ")).append(trend.getBadge());
                }
                lines.add(sellComp);
            }
        });
    }
}
