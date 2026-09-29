package com.vcoins.mixin.client;

import com.vcoins.VeloriaAnvilNames;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreen.class)
public abstract class VeloriaAnvilScreenMixin {
    @Shadow private EditBox name;
    @Unique private ItemStack veloria$lastInput;

    @Inject(method = "subInit", at = @At("RETURN"))
    private void veloria$readableCodes(CallbackInfo ci) {
        // Formatting belongs in the output preview, never in the editable source string.
        name.addFormatter((text, offset) -> FormattedCharSequence.forward(text, Style.EMPTY));
        veloria$lastInput = null;
    }

    @Inject(method = "slotChanged", at = @At("HEAD"), cancellable = true)
    private void veloria$preserveDraft(AbstractContainerMenu menu, int slot, ItemStack stack, CallbackInfo ci) {
        if (slot != 0) return;
        // A full server resync must not replace a draft with the unchanged input item's name.
        if (veloria$lastInput != null && ItemStack.matches(veloria$lastInput, stack)) {
            ci.cancel();
            return;
        }
        veloria$lastInput = stack.copy();
    }

    @Redirect(method = "onNameChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/AnvilMenu;setItemName(Ljava/lang/String;)Z"))
    private boolean veloria$sendDraft(AnvilMenu menu, String text) {
        menu.setItemName(text);
        // Local prediction and authoritative server state can differ after a slot update.
        // The server still validates the name and calculates the actual output and XP cost.
        return VeloriaAnvilNames.filter(text).length() <= 50;
    }
}

