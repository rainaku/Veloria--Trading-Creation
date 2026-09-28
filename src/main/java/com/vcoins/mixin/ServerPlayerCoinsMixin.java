package com.vcoins.mixin;

import com.vcoins.VCoinsState;
import com.vcoins.VFortuna;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerCoinsMixin {
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void veloria$readCoins(ValueInput input, CallbackInfo ci) {
        VCoinsState.readPlayerBalance(((ServerPlayer) (Object) this).getUUID(), input);
        VFortuna.readPlayerState(((ServerPlayer) (Object) this).getUUID(), input);
        com.vcoins.VGiftBox.read(((ServerPlayer) (Object) this).getUUID(), input);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void veloria$writeCoins(ValueOutput output, CallbackInfo ci) {
        VCoinsState.writePlayerBalance(((ServerPlayer) (Object) this).getUUID(), output);
        VFortuna.writePlayerState(((ServerPlayer) (Object) this).getUUID(), output);
        com.vcoins.VGiftBox.write(((ServerPlayer) (Object) this).getUUID(), output);
    }
}
