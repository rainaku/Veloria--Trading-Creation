package com.vcoins;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class VCoinsClientMod implements ClientModInitializer {
    private static final KeyBinding.Category VTRADING_CATEGORY = KeyBinding.Category.create(
            Identifier.of(VCoinsMod.MOD_ID, "main")
    );
    private static KeyBinding openShopKey;

    @Override
    public void onInitializeClient() {
        System.out.println("Initializing Veloria client...");
        
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(VCoinsMod.VTRADE_SCREEN_HANDLER, VTradeScreen::new);
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(VCoinsMod.VDUPLICATE_SCREEN_HANDLER, VDuplicateScreen::new);

        // Register Keybinding
        openShopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.vcoins.open_shop", 
            InputUtil.Type.KEYSYM, 
            GLFW.GLFW_KEY_B, 
            VTRADING_CATEGORY
        ));

        // Listen for Key Press
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openShopKey.wasPressed()) {
                ClientPlayNetworking.send(new OpenShopPayload());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(VCoinsSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.client().player != null) {
                    VCoinsState.setCoins(context.client().player.getUuid(), payload.coins());
                }
            });
        });
    }
}
