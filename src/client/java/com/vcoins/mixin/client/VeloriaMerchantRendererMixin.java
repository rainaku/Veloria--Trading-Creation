package com.vcoins.mixin.client;

import net.minecraft.client.renderer.entity.WanderingTraderRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WanderingTraderRenderer.class)
public class VeloriaMerchantRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void veloria$context(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context, CallbackInfo ci) {
        if (!((Object) this instanceof com.vcoins.VeloriaMerchantRenderer)) com.vcoins.VeloriaPreviewRenderers.reset(context);
    }
}
