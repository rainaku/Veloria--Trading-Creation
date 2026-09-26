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
    private static KeyMapping openShopKey;

    @Override
    public void onInitializeClient() {
        System.out.println("Initializing Veloria client...");
        
        net.minecraft.client.gui.screens.MenuScreens.register(VCoinsMod.VTRADE_SCREEN_HANDLER, VTradeScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(VCoinsMod.VDUPLICATE_SCREEN_HANDLER, VDuplicateScreen::new);

        // Register Keybinding
        openShopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.vcoins.open_shop", 
            InputConstants.Type.KEYBOARD, 
            InputConstants.KEY_B, 
            VTRADING_CATEGORY
        ));

        // Listen for Key Press
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openShopKey.consumeClick()) {
                ClientPlayNetworking.send(new OpenShopPayload());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(VCoinsSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    VCoinsState.setCoins(context.client().player.getUUID(), payload.coins());
                }
            });
        });
    }
}
