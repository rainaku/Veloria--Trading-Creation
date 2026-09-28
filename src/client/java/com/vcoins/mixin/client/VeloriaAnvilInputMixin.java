package com.vcoins.mixin.client;

import com.vcoins.VeloriaAnvilNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EditBox.class)
public abstract class VeloriaAnvilInputMixin {
    @Redirect(method = "insertText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/StringUtil;filterText(Ljava/lang/String;)Ljava/lang/String;"))
    private String veloria$pasteFormatting(String text) {
        return Minecraft.getInstance().gui.screen() instanceof AnvilScreen
                ? VeloriaAnvilNames.filter(text) : StringUtil.filterText(text);
    }

    @Redirect(method = "charTyped", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/input/CharacterEvent;isAllowedChatCharacter()Z"))
    private boolean veloria$typeFormatting(CharacterEvent event) {
        return event.isAllowedChatCharacter() || (Minecraft.getInstance().gui.screen() instanceof AnvilScreen
                && "\u00a7".equals(event.codepointAsString()));
    }
}

