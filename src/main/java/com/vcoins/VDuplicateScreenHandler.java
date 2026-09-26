package com.vcoins;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Prediction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class VDuplicateScreenHandler extends AbstractContainerMenu {
    private static final int SAMPLE_SLOT = 0;
    private static final int PREVIEW_SLOT = 1;
    private static final int PLAYER_SLOT_START = 2;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 36;

    private final Container sampleInventory = new SimpleContainer(1);
    private final Container previewInventory = new SimpleContainer(1);

    public VDuplicateScreenHandler(int syncId, Inventory playerInventory) {
        super(VCoinsMod.VDUPLICATE_SCREEN_HANDLER, syncId);

        this.addSlot(new Slot(sampleInventory, 0, 27, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return VCoinsPricing.isTradeable(stack.getItem());
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return 1;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        this.addSlot(new Slot(previewInventory, 0, 134, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }

        updatePreview();
    }

    public ItemStack getSampleStack() {
        return sampleInventory.getItem(0);
    }

    @Override
    public void broadcastChanges() {
        updatePreview();
        super.broadcastChanges();
    }

    private void updatePreview() {
        ItemStack sample = getSampleStack();
        ItemStack wanted = sample.isEmpty() || !VCoinsPricing.isTradeable(sample.getItem())
                ? ItemStack.EMPTY
                : sample.copyWithCount(1);
        if (!ItemStack.matches(previewInventory.getItem(0), wanted)) {
            previewInventory.setItem(0, wanted);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return duplicate(serverPlayer);
    }

    private boolean duplicate(ServerPlayer player) {
        ItemStack sample = getSampleStack();
        if (sample.isEmpty() || !VCoinsPricing.isTradeable(sample.getItem())) {
            fail(player, "vcoins.duplicate.invalid_item");
            return false;
        }

        long coinCost = VDuplicatePricing.getCoinCost(sample);
        int levelCost = VDuplicatePricing.getExperienceLevelCost(sample);
        long balance = VCoinsState.getCoins(player.getUUID());

        if (balance < coinCost) {
            player.sendOverlayMessage(Component.translatable("vcoins.duplicate.not_enough_coins",
                    formatNumber(coinCost), formatNumber(balance)).withStyle(ChatFormatting.RED));
            player.playSound(SoundEvents.VILLAGER_NO, 0.9f, 1.0f);
            return false;
        }
        if (player.experienceLevel < levelCost) {
            player.sendOverlayMessage(Component.translatable("vcoins.duplicate.not_enough_xp",
                    levelCost, player.experienceLevel).withStyle(ChatFormatting.RED));
            player.playSound(SoundEvents.VILLAGER_NO, 0.9f, 1.0f);
            return false;
        }

        VCoinsState.removeCoins(player.getUUID(), coinCost);
        player.giveExperienceLevels(-levelCost);
        player.getInventory().placeItemBackInInventory(sample.copyWithCount(1), Prediction.SERVER_ONLY);
        VCoinsMod.syncCoins(player);

        player.playSound(SoundEvents.ANVIL_USE, 1.0f, 1.15f);
        player.playSound(SoundEvents.PLAYER_LEVELUP, 0.65f, 1.55f);
        player.sendOverlayMessage(Component.translatable("vcoins.duplicate.success", sample.getHoverName(),
                formatNumber(coinCost), levelCost).withStyle(ChatFormatting.GREEN));
        return true;
    }

    private void fail(ServerPlayer player, String translationKey) {
        player.sendOverlayMessage(Component.translatable(translationKey).withStyle(ChatFormatting.RED));
        player.playSound(SoundEvents.VILLAGER_NO, 0.9f, 1.0f);
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput actionType, Player player) {
        if (slotIndex == PREVIEW_SLOT) {
            return;
        }
        super.clicked(slotIndex, button, actionType, player);
        updatePreview();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size() || slotIndex == PREVIEW_SLOT) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (slotIndex == SAMPLE_SLOT) {
            if (!this.moveItemStackTo(source, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!VCoinsPricing.isTradeable(source.getItem())
                || !this.moveItemStackTo(source, SAMPLE_SLOT, SAMPLE_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        updatePreview();
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            this.clearContainer(player, sampleInventory);
        }
        previewInventory.clearContent();
    }

    @Override
    public boolean stillValid(Player player) {
        return player != null && !player.isRemoved() && player.isAlive();
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
