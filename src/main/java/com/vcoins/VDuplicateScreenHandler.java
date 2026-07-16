package com.vcoins;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

public class VDuplicateScreenHandler extends ScreenHandler {
    private static final int SAMPLE_SLOT = 0;
    private static final int PREVIEW_SLOT = 1;
    private static final int PLAYER_SLOT_START = 2;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 36;

    private final Inventory sampleInventory = new SimpleInventory(1);
    private final Inventory previewInventory = new SimpleInventory(1);

    public VDuplicateScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(VCoinsMod.VDUPLICATE_SCREEN_HANDLER, syncId);

        this.addSlot(new Slot(sampleInventory, 0, 27, 47) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return VCoinsPricing.isTradeable(stack.getItem());
            }

            @Override
            public int getMaxItemCount(ItemStack stack) {
                return 1;
            }

            @Override
            public int getMaxItemCount() {
                return 1;
            }
        });

        this.addSlot(new Slot(previewInventory, 0, 134, 47) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }

            @Override
            public boolean canTakeItems(PlayerEntity player) {
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
        return sampleInventory.getStack(0);
    }

    @Override
    public void sendContentUpdates() {
        updatePreview();
        super.sendContentUpdates();
    }

    private void updatePreview() {
        ItemStack sample = getSampleStack();
        ItemStack wanted = sample.isEmpty() || !VCoinsPricing.isTradeable(sample.getItem())
                ? ItemStack.EMPTY
                : sample.copyWithCount(1);
        if (!ItemStack.areEqual(previewInventory.getStack(0), wanted)) {
            previewInventory.setStack(0, wanted);
        }
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id != 0 || player.getEntityWorld().isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }
        return duplicate(serverPlayer);
    }

    private boolean duplicate(ServerPlayerEntity player) {
        ItemStack sample = getSampleStack();
        if (sample.isEmpty() || !VCoinsPricing.isTradeable(sample.getItem())) {
            fail(player, "vcoins.duplicate.invalid_item");
            return false;
        }

        long coinCost = VDuplicatePricing.getCoinCost(sample);
        int levelCost = VDuplicatePricing.getExperienceLevelCost(sample);
        long balance = VCoinsState.getCoins(player.getUuid());

        if (balance < coinCost) {
            player.sendMessage(Text.translatable("vcoins.duplicate.not_enough_coins",
                    formatNumber(coinCost), formatNumber(balance)).formatted(Formatting.RED), true);
            player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
            return false;
        }
        if (player.experienceLevel < levelCost) {
            player.sendMessage(Text.translatable("vcoins.duplicate.not_enough_xp",
                    levelCost, player.experienceLevel).formatted(Formatting.RED), true);
            player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
            return false;
        }

        VCoinsState.removeCoins(player.getUuid(), coinCost);
        player.addExperienceLevels(-levelCost);
        player.getInventory().offerOrDrop(sample.copyWithCount(1));
        VCoinsMod.syncCoins(player);

        player.playSound(SoundEvents.BLOCK_ANVIL_USE, 1.0f, 1.15f);
        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.65f, 1.55f);
        player.sendMessage(Text.translatable("vcoins.duplicate.success", sample.getName(),
                formatNumber(coinCost), levelCost).formatted(Formatting.GREEN), true);
        return true;
    }

    private void fail(ServerPlayerEntity player, String translationKey) {
        player.sendMessage(Text.translatable(translationKey).formatted(Formatting.RED), true);
        player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex == PREVIEW_SLOT) {
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
        updatePreview();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size() || slotIndex == PREVIEW_SLOT) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        if (slotIndex == SAMPLE_SLOT) {
            if (!this.insertItem(source, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!VCoinsPricing.isTradeable(source.getItem())
                || !this.insertItem(source, SAMPLE_SLOT, SAMPLE_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        updatePreview();
        return original;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (!player.getEntityWorld().isClient()) {
            this.dropInventory(player, sampleInventory);
        }
        previewInventory.clear();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
