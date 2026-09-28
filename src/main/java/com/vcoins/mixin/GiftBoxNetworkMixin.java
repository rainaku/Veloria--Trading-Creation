package com.vcoins.mixin;

import com.vcoins.VGiftBox;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Disk codecs remain untouched; only the network copy hides sealed rewards. */
@Mixin(targets = "net.minecraft.world.item.ItemStack$1")
public abstract class GiftBoxNetworkMixin {
    @ModifyVariable(method = "encode(Lnet/minecraft/network/RegistryFriendlyByteBuf;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), argsOnly = true)
    private ItemStack veloria$hideGiftContents(ItemStack stack) {
        return VGiftBox.networkView(stack);
    }
}
