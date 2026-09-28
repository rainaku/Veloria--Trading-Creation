package com.vcoins.mixin;

import com.vcoins.VeloriaTextFallbacks;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TranslatableContents.class)
public abstract class VeloriaTextFallbackMixin {
    @Shadow @Final @Mutable private String fallback;

    // Covers newly generated text and older components decoded from saved inventories/chests.
    @Inject(method = "<init>(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V", at = @At("RETURN"))
    private void veloria$persistReadableText(String key, String fallback, Object[] args, CallbackInfo ci) {
        this.fallback = VeloriaTextFallbacks.fallback(key, fallback);
    }
}
