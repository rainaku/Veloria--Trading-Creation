package com.vcoins.mixin.client;

import com.vcoins.VeloriaPreviewRenderers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class VeloriaDispatcherMixin {
    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    private void veloriaModel(EntityRenderState state, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
        if (VeloriaPreviewRenderers.states.contains(state) && VeloriaPreviewRenderers.get() != null)
            cir.setReturnValue(VeloriaPreviewRenderers.get());
    }
}
