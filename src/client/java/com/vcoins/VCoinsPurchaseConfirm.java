package com.vcoins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Locale;

/**
 * Manages high-value purchase confirmation (>= 100,000 coins).
 * Designed for extreme user convenience:
 * - Toggleable: can be switched OFF to allow instant spam-buying without confirmation.
 * - In-place double-click verification (no moving mouse to separate OK button).
 * - Spacebar or Enter key verification.
 * - Spam-friendly grace window: once confirmed, rapid subsequent clicks on the same item execute immediately!
 */
public class VCoinsPurchaseConfirm {
    public static final long THRESHOLD = 100_000L;
    public static final long TIMEOUT_MS = 2800L;
    public static final long SPAM_GRACE_MS = 1600L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/vcoins_client.json");
    private static boolean confirmationEnabled = true;
    private static boolean configLoaded = false;
    private static boolean reducedMotion = false;

    private int pendingSlot = -1;
    private boolean pendingBuyStack = false;
    private long pendingTimestamp = 0L;
    private String pendingItemName = "";
    private int pendingAmount = 0;
    private long pendingCost = 0L;

    private int lastBoughtSlot = -1;
    private boolean lastBoughtBuyStack = false;
    private long lastBoughtTimestamp = 0L;

    public record ConfirmedAction(int slotIndex, boolean buyStack) {}

    public interface ConfirmationCallback {
        void execute(int slotIndex, boolean buyStack);
    }

    public static boolean isReducedMotion() { ensureLoaded(); return reducedMotion; }

    public static boolean toggleReducedMotion() {
        ensureLoaded();
        reducedMotion = !reducedMotion;
        saveConfig();
        return reducedMotion;
    }

    public static boolean isConfirmationEnabled() {
        ensureLoaded();
        return confirmationEnabled;
    }

    public static boolean toggleConfirmation() {
        ensureLoaded();
        confirmationEnabled = !confirmationEnabled;
        saveConfig();
        return confirmationEnabled;
    }

    public static void setConfirmationEnabled(boolean enabled) {
        ensureLoaded();
        confirmationEnabled = enabled;
        saveConfig();
    }

    public static Component getToggleLabel() {
        return Component.translatable(isConfirmationEnabled() ? "vcoins.verify.toggle_on" : "vcoins.verify.toggle_off");
    }

    public static Tooltip getToggleTooltip() {
        return Tooltip.create(Component.translatable(isConfirmationEnabled() ? "vcoins.verify.tooltip_on" : "vcoins.verify.tooltip_off"));
    }

    private static synchronized void ensureLoaded() {
        if (configLoaded) return;
        configLoaded = true;
        try {
            if (CONFIG_FILE.exists()) {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                    if (obj != null && obj.has("reducedMotion")) reducedMotion = obj.get("reducedMotion").getAsBoolean();
                    if (obj != null && obj.has("confirm100k")) {
                        confirmationEnabled = obj.get("confirm100k").getAsBoolean();
                    }
                }
            }
        } catch (Exception ignored) {
            confirmationEnabled = true;
        }
    }

    private static synchronized void saveConfig() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("confirm100k", confirmationEnabled);
            obj.addProperty("reducedMotion", reducedMotion);
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(obj, writer);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Checks if the purchase requires confirmation.
     * If confirmation is disabled via toggle or cost < 100k, immediately returns true (free buying).
     * @return true if the purchase should be executed immediately, false if confirmation was armed.
     */
    public boolean checkOrArm(int slot, boolean buyStack, ItemStack stack, long totalCost, Minecraft minecraft) {
        if (!isConfirmationEnabled() || totalCost < THRESHOLD) {
            lastBoughtSlot = slot;
            lastBoughtBuyStack = buyStack;
            lastBoughtTimestamp = System.currentTimeMillis();
            return true;
        }

        long now = System.currentTimeMillis();

        // Spam-buy grace window: if player just confirmed and bought this exact slot within grace window, continue buying!
        if (lastBoughtSlot == slot && lastBoughtBuyStack == buyStack && (now - lastBoughtTimestamp) < SPAM_GRACE_MS) {
            lastBoughtTimestamp = now;
            return true;
        }

        // Second click on the same slot within timeout: CONFIRMED!
        if (pendingSlot == slot && pendingBuyStack == buyStack && (now - pendingTimestamp) < TIMEOUT_MS) {
            pendingSlot = -1;
            lastBoughtSlot = slot;
            lastBoughtBuyStack = buyStack;
            lastBoughtTimestamp = now;

            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            }
            return true;
        }

        // Arm new confirmation
        pendingSlot = slot;
        pendingBuyStack = buyStack;
        pendingTimestamp = now;
        pendingItemName = stack.getHoverName().getString();
        pendingAmount = buyStack ? stack.getMaxStackSize() : 1;
        pendingCost = totalCost;

        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 0.95f));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f));
        }

        return false;
    }

    /**
     * Confirms the pending purchase via keyboard (Space or Enter).
     */
    public boolean handleKeyPress(ConfirmationCallback callback) {
        if (!isConfirmationEnabled()) return false;
        long now = System.currentTimeMillis();
        if (pendingSlot >= 0 && (now - pendingTimestamp) < TIMEOUT_MS) {
            int slot = pendingSlot;
            boolean buyStack = pendingBuyStack;
            pendingSlot = -1;
            lastBoughtSlot = slot;
            lastBoughtBuyStack = buyStack;
            lastBoughtTimestamp = now;

            callback.execute(slot, buyStack);
            return true;
        }
        return false;
    }

    public boolean isArmed() {
        return isConfirmationEnabled() && pendingSlot >= 0 && (System.currentTimeMillis() - pendingTimestamp) < TIMEOUT_MS;
    }

    public boolean isSlotPending(int slot) {
        return isArmed() && pendingSlot == slot;
    }

    public void renderBanner(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int height) {
        if (!isArmed()) return;

        long now = System.currentTimeMillis();
        float pulse = isReducedMotion() ? 0f : (float) Math.sin((now & 1023) * ((Math.PI * 2.0) / 1024.0));
        int alpha = (int) (220 + 35 * Math.max(0.0f, pulse));

        // Dark amber backdrop
        extractor.fill(x, y, x + width, y + height, (alpha << 24) | 0x221100);
        // Amber outline
        extractor.fill(x, y, x + width, y + 1, 0xFFFFB300);
        extractor.fill(x, y + height - 1, x + width, y + height, 0xFFFFB300);
        extractor.fill(x, y, x + 1, y + height, 0xFFFFB300);
        extractor.fill(x + width - 1, y, x + width, y + height, 0xFFFFB300);

        Component text = Component.translatable("vcoins.confirm.banner",
                pendingItemName, pendingAmount, formatCompact(pendingCost));
        int maxW = width - 8;
        if (font.width(text) > maxW) {
            Component dummy = Component.translatable("vcoins.confirm.banner",
                    "", pendingAmount, formatCompact(pendingCost));
            int availableForItem = Math.max(16, maxW - font.width(dummy));
            String trimmedName = font.plainSubstrByWidth(pendingItemName, availableForItem - font.width("..")) + "..";
            text = Component.translatable("vcoins.confirm.banner",
                    trimmedName, pendingAmount, formatCompact(pendingCost));
            if (font.width(text) > maxW) {
                text = Component.literal(font.plainSubstrByWidth(text.getString(), maxW - font.width("..")) + "..");
            }
        }
        extractor.centeredText(font, text, x + width / 2, y + (height - 8) / 2, 0xFFFFCC00);
    }

    public void renderSlotWarningPulse(GuiGraphicsExtractor extractor, int slotX, int slotY, int slotSize) {
        if (!isArmed()) return;
        long now = System.currentTimeMillis();
        float pulse = isReducedMotion() ? 0f : (float) Math.sin((now & 511) * ((Math.PI * 2.0) / 512.0));
        int a = (int) (60 + 50 * Math.max(0.0f, pulse));
        extractor.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, (a << 24) | 0xFFB300);
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatCompact(long value) {
        if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0).replace(".0M", "M");
        } else if (value >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0).replace(".0K", "K");
        }
        return Long.toString(value);
    }
}
