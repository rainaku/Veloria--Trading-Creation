package com.vcoins.mixin;

import com.vcoins.VeloriaAnvilNames;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilMenu.class)
public abstract class VeloriaAnvilNameMixin {
    @Redirect(method = "validateName", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/StringUtil;filterText(Ljava/lang/String;)Ljava/lang/String;"))
    private static String veloria$allowFormatting(String name) {
        return VeloriaAnvilNames.filter(name);
    }

    @Redirect(method = {"createResult", "setItemName"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent veloria$formattedName(String name) {
        return VeloriaAnvilNames.parse(name);
    }
}
