package com.vcoins;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class VBlackMarketScreen extends AbstractContainerScreen<VBlackMarketScreenHandler> {
    private static final Identifier CARD_BACK =
            Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_back.png");
    private static final Identifier CARD_FRONT =
            Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_front.png");

    private static final long FLIP_DURATION_MS = 480L;
    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    // Pre-allocated static components to prevent GC pressure during hover/render loops
    private static final List<Component> UNREVEALED_TOOLTIP = List.of(
            Component.translatable("vcoins.black_market.card_mystery").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            Component.translatable("vcoins.black_market.card_hint").withStyle(ChatFormatting.AQUA)
    );
    private static final List<Component> SOLD_TOOLTIP = List.of(
            Component.translatable("vcoins.black_market.sold").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            Component.translatable("vcoins.black_market.sold_tooltip").withStyle(ChatFormatting.GRAY)
    );
    private static final Component INVENTORY_LABEL = Component.translatable("vcoins.inventory");
    private static final Component MERCHANT_TAG = Component.translatable("vcoins.black_market.merchant_tag")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC);
    private static final Component BUY_LEFT = Component.translatable("vcoins.tooltip.buy_left")
            .withStyle(ChatFormatting.GRAY);

    // Static sync state shared across open instances
    private static long baseSecondsRemaining = 86400L;
    private static long lastSyncTimeMs = System.currentTimeMillis();
    private static int syncedRevealedMask = 0;
    private static int syncedPurchasedMask = 0;
    private static long syncedEpochDay = 0L;
    private static int syncedBankedResets = 0;
    private static int syncedResetSequence = 0;
    private static List<ItemStack> syncedCards = new ArrayList<>();
    private static int syncedLifetimeFlipCount = 0;

    public static long getSyncedDay() { return syncedEpochDay; }
    public static int getSyncedBankedResets() { return syncedBankedResets; }
    public static int getSyncedResetSequence() { return syncedResetSequence; }
    public static List<ItemStack> getSyncedCards() { return syncedCards; }
    public static int getSyncedRevealedMask() { return syncedRevealedMask; }
    public static int getSyncedPurchasedMask() { return syncedPurchasedMask; }
    public static long getBaseSecondsRemaining() { return baseSecondsRemaining; }
    public static long getLastSyncTimeMs() { return lastSyncTimeMs; }

    // Zero-allocation particle pool (expanded for legendary god pull fireworks)
    private static final int MAX_PARTICLES = 256;
    private static final String[] PARTICLE_SYMBOLS = {"✦", "✧", "⋆", "★", "⚡", "☼", "⚜", "👑", "•", "ᛟ", "ᚱ", "◇"};
    private final float[] partX = new float[MAX_PARTICLES];
    private final float[] partY = new float[MAX_PARTICLES];
    private final float[] partVx = new float[MAX_PARTICLES];
    private final float[] partVy = new float[MAX_PARTICLES];
    private final int[] partColor = new int[MAX_PARTICLES];
    private final byte[] partSymbol = new byte[MAX_PARTICLES];
    private final long[] partSpawn = new long[MAX_PARTICLES];
    private final int[] partLife = new int[MAX_PARTICLES];
    private int particleCount = 0;

    // Locally triggered flips
    private final Set<Integer> localRevealedCards = new HashSet<>();
    private final long[] flipStartTime = new long[VBlackMarketScreenHandler.MARKET_SLOT_COUNT];
    private final boolean[] hasSpawnedStartParticles = new boolean[VBlackMarketScreenHandler.MARKET_SLOT_COUNT];
    private final boolean[] hasSpawnedMidParticles = new boolean[VBlackMarketScreenHandler.MARKET_SLOT_COUNT];
    private final boolean[] hasSpawnedFinishParticles = new boolean[VBlackMarketScreenHandler.MARKET_SLOT_COUNT];

    // God Pull Cinematic State
    private long godPullStartTime = 0L;
    private int godPullCardIndex = -1;
    private static final long GOD_CINEMATIC_DURATION_MS = 2500L;

    // Reset animation state
    private static final long RESET_ANIM_DURATION_MS = 500L;
    private long resetAnimationStartTime = 0L;
    private int lastObservedResetSequence = 0;
    private long lastObservedDay = 0L;

    // Balance text cache
    private long lastBalance = -1L;
    private Component cachedBalanceComponent = null;

    private Button revealAllButton;
    private Button verifyToggleButton;
    private Button resetButton;
    private final VCoinsPurchaseConfirm purchaseConfirm = new VCoinsPurchaseConfirm();

    public static void handleSyncPayload(BlackMarketSyncPayload payload) {
        if (payload.epochDay() != syncedEpochDay) {
            syncedEpochDay = payload.epochDay();
        }
        baseSecondsRemaining = payload.secondsUntilReset();
        lastSyncTimeMs = System.currentTimeMillis();
        syncedRevealedMask = payload.revealedMask();
        syncedPurchasedMask = payload.purchasedMask();
        syncedBankedResets = payload.bankedResets();
        syncedResetSequence = payload.resetSequence();
        syncedLifetimeFlipCount = payload.lifetimeFlipCount();
        if (payload.items() != null) {
            syncedCards = new ArrayList<>(payload.items());
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.gui != null) {
            if (mc.gui.screen() instanceof VBlackMarketScreen screen) {
                screen.onSyncReceived(payload.resetSequence(), payload.epochDay());
            } else if (mc.gui.screen() instanceof VTradeScreen tradeScreen) {
                tradeScreen.onBlackMarketSyncReceived(payload.resetSequence(), payload.epochDay());
            }
        }
    }

    public void onSyncReceived(int newResetSequence, long newDay) {
        boolean sequenceChanged = (newResetSequence != this.lastObservedResetSequence);
        boolean dayChanged = (newDay != this.lastObservedDay);
        this.lastObservedResetSequence = newResetSequence;
        this.lastObservedDay = newDay;

        if (sequenceChanged || dayChanged) {
            triggerResetAnimation(false);
        }
        updateButtonStates();
    }

    public VBlackMarketScreen(VBlackMarketScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 256, 246);
        this.inventoryLabelX = 47;
        this.inventoryLabelY = 127;
        this.titleLabelX = 1000; // hide default title; custom header is rendered
    }

    @Override
    protected void init() {
        super.init();

        // Nút quay lại Cửa hàng chính
        this.addRenderableWidget(VeloriaButton.create(Component.translatable("vcoins.back_to_shop"), button -> handleBackToShopClick())
                .bounds(this.leftPos + 8, this.topPos + 5, 36, 16)
                .build());

        // Công tắc bật/tắt xác minh giao dịch >100k
        this.verifyToggleButton = this.addRenderableWidget(VeloriaButton.create(
                VCoinsPurchaseConfirm.getToggleLabel(),
                button -> handleVerifyToggleClick())
                .bounds(this.leftPos + 46, this.topPos + 5, 52, 16)
                .tooltip(VCoinsPurchaseConfirm.getToggleTooltip())
                .build());

        // Nút Lật tất cả các lá bài (Reveal All)
        this.revealAllButton = this.addRenderableWidget(VeloriaButton.create(Component.translatable("vcoins.black_market.reveal_all"), button -> revealAllCards())
                .bounds(this.leftPos + 188, this.topPos + 5, 60, 16)
                .build());

        // Nút Đặt lại Chợ đen (Banked Reset)
        this.resetButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.black_market.banked_reset", syncedBankedResets),
                button -> handleResetClick())
                .bounds(this.leftPos + 188, this.topPos + 24, 60, 16)
                .tooltip(Tooltip.create(Component.translatable("vcoins.black_market.banked_reset_tooltip")))
                .build());

        this.lastObservedResetSequence = syncedResetSequence;
        this.lastObservedDay = syncedEpochDay;
        updateButtonStates();
    }

    private void handleBackToShopClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
        ClientPlayNetworking.send(new OpenShopPayload());
    }

    private void handleVerifyToggleClick() {
        boolean enabled = VCoinsPurchaseConfirm.toggleConfirmation();
        updateVerifyToggleButton();
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), enabled ? 1.15f : 0.85f));
            if (this.minecraft.player != null) {
                this.minecraft.player.sendOverlayMessage(
                        Component.translatable(enabled ? "vcoins.verify.msg_on" : "vcoins.verify.msg_off")
                                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GOLD)
                );
            }
        }
    }

    private void handleResetClick() {
        if (syncedBankedResets <= 0) {
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
        ClientPlayNetworking.send(new BlackMarketResetActionPayload());
        triggerResetAnimation(true);
    }

    public void triggerResetAnimation(boolean playTriggerSounds) {
        long now = System.currentTimeMillis();
        this.resetAnimationStartTime = now;
        this.godPullStartTime = 0L;
        this.godPullCardIndex = -1;
        this.localRevealedCards.clear();
        for (int i = 0; i < VBlackMarketScreenHandler.MARKET_SLOT_COUNT; i++) {
            flipStartTime[i] = 0;
            hasSpawnedStartParticles[i] = false;
            hasSpawnedMidParticles[i] = false;
            hasSpawnedFinishParticles[i] = false;
        }

        if (this.minecraft != null && playTriggerSounds) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUNDLE_DROP_CONTENTS, 1.25f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.15f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35f));
        }

        spawnResetWaveParticles();
        updateButtonStates();
    }

    private void spawnResetWaveParticles() {
        if (VCoinsPurchaseConfirm.isReducedMotion()) return;
        long now = System.currentTimeMillis();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int[] palette = {0xFFFFD700, 0xFFC586C0, 0xFF00E5FF, 0xFFFFFFFF};

        for (int slot = 0; slot < VBlackMarketScreenHandler.MARKET_SLOT_COUNT; slot++) {
            int cardX = VBlackMarketScreenHandler.MARKET_X + slot * VBlackMarketScreenHandler.MARKET_COL_SPACING;
            int cardY = VBlackMarketScreenHandler.MARKET_Y;
            float cx = this.leftPos + cardX + VBlackMarketScreenHandler.CARD_SIZE * 0.5f;
            float cy = this.topPos + cardY + VBlackMarketScreenHandler.CARD_SIZE * 0.5f;

            for (int p = 0; p < 6; p++) {
                double angle = rng.nextDouble(0, TWO_PI);
                float speed = (float) rng.nextDouble(0.5, 1.4);
                int color = palette[rng.nextInt(palette.length)];
                int symIdx = rng.nextInt(PARTICLE_SYMBOLS.length);
                int life = rng.nextInt(350, 600);
                addParticle(
                        cx, cy,
                        (float) Math.cos(angle) * speed,
                        (float) Math.sin(angle) * speed,
                        color, symIdx, life
                );
            }
        }
    }

    private void updateVerifyToggleButton() {
        if (this.verifyToggleButton != null) {
            this.verifyToggleButton.setMessage(VCoinsPurchaseConfirm.getToggleLabel());
            this.verifyToggleButton.setTooltip(VCoinsPurchaseConfirm.getToggleTooltip());
        }
    }

    private void revealAllCards() {
        long now = System.currentTimeMillis();
        boolean anyNew = false;
        int staggerIndex = 0;
        for (int i = 0; i < VBlackMarketScreenHandler.MARKET_SLOT_COUNT; i++) {
            boolean isRevealed = ((syncedRevealedMask & (1 << i)) != 0) || localRevealedCards.contains(i);
            if (!isRevealed) {
                localRevealedCards.add(i);
                flipStartTime[i] = now + staggerIndex * 75L;
                hasSpawnedStartParticles[i] = false;
                hasSpawnedMidParticles[i] = false;
                hasSpawnedFinishParticles[i] = false;
                staggerIndex++;
                anyNew = true;
            }
        }
        if (anyNew) {
            ClientPlayNetworking.send(new BlackMarketRevealPayload(-1));
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.0f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f));
            }
        }
        updateButtonStates();
    }

    private void updateRevealAllButtonState() {
        if (revealAllButton != null) {
            int revealedCount = 0;
            for (int i = 0; i < VBlackMarketScreenHandler.MARKET_SLOT_COUNT; i++) {
                if (((syncedRevealedMask & (1 << i)) != 0) || localRevealedCards.contains(i)) {
                    revealedCount++;
                }
            }
            revealAllButton.active = revealedCount < VBlackMarketScreenHandler.MARKET_SLOT_COUNT;
        }
    }

    private void updateButtonStates() {
        updateRevealAllButtonState();
        if (resetButton != null) {
            resetButton.visible = (syncedBankedResets > 0 && !purchaseConfirm.isArmed());
            resetButton.setMessage(Component.translatable("vcoins.black_market.banked_reset", syncedBankedResets));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractBackground(extractor, mouseX, mouseY, delta);

        if (resetButton != null) {
            boolean shouldShow = (syncedBankedResets > 0 && !purchaseConfirm.isArmed());
            if (resetButton.visible != shouldShow) {
                resetButton.visible = shouldShow;
            }
        }

        InventoryTextures.panel(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        InventoryTextures.slots(extractor, this.leftPos + VBlackMarketScreenHandler.PLAYER_X,
                this.topPos + VBlackMarketScreenHandler.PLAYER_INVENTORY_Y, 9, 3);
        InventoryTextures.slots(extractor, this.leftPos + VBlackMarketScreenHandler.PLAYER_X,
                this.topPos + VBlackMarketScreenHandler.PLAYER_HOTBAR_Y, 9, 1);

        // Player coin balance (centered in header plaque)
        long balance = this.minecraft != null && this.minecraft.player != null
                ? VCoinsState.getCoins(this.minecraft.player.getUUID())
                : 0L;
        extractor.centeredText(this.font, getBalanceComponent(balance),
                this.leftPos + 128, this.topPos + 9, 0xFFE8B829);

        // Reset Countdown Timer or Purchase Confirmation Banner
        if (purchaseConfirm.isArmed()) {
            purchaseConfirm.renderBanner(extractor, this.font, this.leftPos + 20, this.topPos + 26, 216, 18);
        } else {
            long now = System.currentTimeMillis();
            long elapsedSec = (now - lastSyncTimeMs) / 1000L;
            long currentSecondsLeft = Math.max(0L, baseSecondsRemaining - elapsedSec);
            long hours = currentSecondsLeft / 3600L;
            long minutes = (currentSecondsLeft % 3600L) / 60L;
            long seconds = currentSecondsLeft % 60L;
            String timerFormatted = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);

            Component timerComponent = Component.translatable("vcoins.black_market.reset_timer", timerFormatted);
            extractor.centeredText(this.font, timerComponent,
                    this.leftPos + 128, this.topPos + 31, 0xFF00E5FF);

            // Pity counter: X / 300 flips until guaranteed god item
            int flips = syncedLifetimeFlipCount;
            int pity = VBlackMarket.PITY_THRESHOLD;
            // Color: green when close (>= 250), yellow otherwise
            int pityColor = flips >= 250 ? 0xFFFFD700 : 0xFFC8A96E;
            if (flips >= 250) {
                // Pulsing gold glow when close to pity
                long nowMs = System.currentTimeMillis();
                float pulse = (float)(0.5 + 0.5 * Math.sin(nowMs * 0.006));
                pityColor = 0xFF000000 | blendColor(0xFFD700, 0xFF8C00, pulse);
            }
            Component pityComponent = Component.translatable("vcoins.black_market.pity_counter", flips, pity);
            extractor.centeredText(this.font, pityComponent,
                    this.leftPos + 128, this.topPos + 42, pityColor);
        }
        VeloriaMenuEffects.draw(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    /** Linear blend between two packed RGB colors (no alpha) */
    private static int blendColor(int a, int b, float t) {
        int ra = (a >> 16) & 0xFF, ga = (a >> 8) & 0xFF, ba2 = a & 0xFF;
        int rb = (b >> 16) & 0xFF, gb = (b >> 8) & 0xFF, bb2 = b & 0xFF;
        int r = (int)(ra + (rb - ra) * t);
        int g = (int)(ga + (gb - ga) * t);
        int bl = (int)(ba2 + (bb2 - ba2) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private Component getBalanceComponent(long balance) {
        if (balance != lastBalance || cachedBalanceComponent == null) {
            lastBalance = balance;
            cachedBalanceComponent = Component.translatable("vcoins.balance_short", formatCompactNumber(balance));
        }
        return cachedBalanceComponent;
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY) {
        if (slot.index >= VBlackMarketScreenHandler.MARKET_SLOT_COUNT) {
            super.extractSlot(extractor, slot, mouseX, mouseY);
            return;
        }

        int slotIndex = slot.index;
        long now = System.currentTimeMillis();
        long start = flipStartTime[slotIndex];
        boolean isRevealed = ((syncedRevealedMask & (1 << slotIndex)) != 0) || localRevealedCards.contains(slotIndex);
        boolean isPurchased = (syncedPurchasedMask & (1 << slotIndex)) != 0;
        boolean isHovered = (getMarketSlotAt(mouseX, mouseY) == slotIndex);

        int cardX = VBlackMarketScreenHandler.MARKET_X + slotIndex * VBlackMarketScreenHandler.MARKET_COL_SPACING;
        int cardY = VBlackMarketScreenHandler.MARKET_Y;
        int cardSize = VBlackMarketScreenHandler.CARD_SIZE;

        boolean isResetting = !VCoinsPurchaseConfirm.isReducedMotion()
                && resetAnimationStartTime > 0
                && now < resetAnimationStartTime + RESET_ANIM_DURATION_MS;

        if (isResetting) {
            float progress = (float) (now - resetAnimationStartTime) / (float) RESET_ANIM_DURATION_MS;
            renderResettingCard(extractor, slotIndex, cardX, cardY, cardSize, progress);
            return;
        }

        boolean hasNotStartedYet = !VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now < start;
        boolean isFlipping = !VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now >= start && now < start + FLIP_DURATION_MS;
        boolean isFullyRevealed = isRevealed && (VCoinsPurchaseConfirm.isReducedMotion() || start == 0 || now >= start + FLIP_DURATION_MS);

        if (!isRevealed || hasNotStartedYet) {
            renderUnrevealedCard(extractor, slot, cardX, cardY, cardSize, isHovered, now);
        } else if (isFlipping) {
            float progress = (float) (now - start) / (float) FLIP_DURATION_MS;
            renderFlippingCard(extractor, slot, slotIndex, cardX, cardY, cardSize, progress);
        } else if (isFullyRevealed) {
            renderRevealedCard(extractor, slot, cardX, cardY, cardSize, isPurchased, isHovered);
        }
    }

    private void renderResettingCard(GuiGraphicsExtractor extractor, int slotIndex,
                                     int cardX, int cardY, int cardSize, float progress) {
        float delay = slotIndex * 0.08f;
        float t = Math.clamp((progress - delay) / 0.6f, 0.0f, 1.0f);
        float sinT = (float) Math.sin(t * PI);

        float liftY = 7.0f * sinT;
        float wobbleX = (float) Math.sin(t * TWO_PI * 1.5f) * 2.0f;
        float cx = cardX + cardSize * 0.5f + wobbleX;

        float shadowW = (cardSize - 2) * (1.0f - sinT * 0.2f);
        extractor.fill((int) (cx - shadowW * 0.5f), cardY + cardSize - 2,
                (int) (cx + shadowW * 0.5f), cardY + cardSize + 3, 0x44000000);

        extractor.pose().pushMatrix();
        extractor.pose().translate(wobbleX, -liftY);

        drawCardTexture(extractor, CARD_BACK, cardX, cardY, cardSize, cardSize);

        int shimmerAlpha = (int) (sinT * 90);
        if (shimmerAlpha > 0) {
            extractor.fill(cardX, cardY, cardX + cardSize, cardY + cardSize, (shimmerAlpha << 24) | 0xE0B0FF);
        }
        extractor.pose().popMatrix();
    }

    private void renderUnrevealedCard(GuiGraphicsExtractor extractor, Slot slot, int cardX, int cardY, int cardSize, boolean isHovered, long now) {
        int liftY = isHovered && !VCoinsPurchaseConfirm.isReducedMotion() ? 1 : 0;
        int drawY = cardY - liftY;

        boolean isGod = slot != null && slot.hasItem() && VBlackMarket.isRomanGodItem(slot.getItem());
        if (isGod) {
            float pulse = VCoinsPurchaseConfirm.isReducedMotion() ? 0.5f : (float) (0.5 + 0.5 * Math.sin(now * 0.005));
            int glowAlpha = (int) (60 + pulse * 65);
            extractor.fill(cardX - 1, drawY - 1, cardX + cardSize + 1, drawY + cardSize + 1, (glowAlpha << 24) | 0xFFD700);
        }

        // Draw custom card back with Veloria Golden Crest scaled to full 40x40 card
        drawCardTexture(extractor, CARD_BACK, cardX, drawY, cardSize, cardSize);

        if (isHovered) {
            // Smooth single-pass golden pulse overlay
            float pulse = VCoinsPurchaseConfirm.isReducedMotion() ? 0f : (float) Math.sin((now & 1023) * (TWO_PI / 1024.0f));
            int alpha = (int) (35 + 25 * Math.max(0.0f, pulse));
            extractor.fill(cardX, drawY, cardX + cardSize, drawY + cardSize, (alpha << 24) | 0xFFD700);
        }
    }

    private void renderFlippingCard(GuiGraphicsExtractor extractor, Slot slot, int slotIndex,
                                     int cardX, int cardY, int cardSize, float progress) {
        ItemStack stack = slot != null && slot.hasItem() ? slot.getItem() : ItemStack.EMPTY;
        boolean isGod = !stack.isEmpty() && VBlackMarket.isRomanGodItem(stack);

        float p = Math.clamp(progress, 0.0f, 1.0f);

        // Smooth cosine ease-in-out curve
        float ease = (1.0f - (float) Math.cos(p * PI)) * 0.5f;

        // Angle from 0 to PI
        float angle = ease * PI;
        float cosVal = (float) Math.cos(angle);
        boolean isBackSide = cosVal > 0.0f;
        float scaleX = Math.abs(cosVal);

        // 3D Elevation arc: peaks at mid-flip (God items leap higher towards camera)
        float lift = (float) Math.sin(p * PI);
        float liftY = isGod ? (lift * 9.5f) : (lift * 5.5f);

        // 3D scale pop towards the camera at peak lift
        float zPop = isGod ? (1.0f + 0.15f * lift) : (1.0f + 0.05f * lift);

        float cx = cardX + cardSize * 0.5f;
        float cy = cardY + cardSize * 0.5f - liftY;
        int half = cardSize / 2;

        // Soft drop shadow beneath the lifted card
        float shadowW = (cardSize - 2) * (0.35f + 0.65f * scaleX);
        int shadowAlpha = (int) ((0.42f - lift * 0.18f) * 255.0f);
        if (shadowAlpha > 0) {
            extractor.fill(
                    (int) (cx - shadowW * 0.5f), cardY + cardSize - 2,
                    (int) (cx + shadowW * 0.5f), cardY + cardSize + 3,
                    (shadowAlpha << 24)
            );
        }

        // Particle trigger: start
        if (!hasSpawnedStartParticles[slotIndex]) {
            hasSpawnedStartParticles[slotIndex] = true;
            spawnFlipStartParticles(cardX, cardY, cardSize);
        }

        // Particle trigger: mid-flip turnover
        if (!isBackSide && !hasSpawnedMidParticles[slotIndex]) {
            hasSpawnedMidParticles[slotIndex] = true;
            if (isGod) {
                godPullStartTime = System.currentTimeMillis();
                godPullCardIndex = slotIndex;
                triggerGodPullCelebration(cardX, cardY, cardSize, stack);
            } else {
                spawnFlipMidParticles(cardX, cardY, cardSize, stack);
            }
        }

        // 3D Matrix Transform
        extractor.pose().pushMatrix();
        extractor.pose().translate(cx, cy);
        extractor.pose().scale(Math.max(0.015f, scaleX) * zPop, zPop);

        if (isBackSide) {
            // ─── CARD BACK ───
            if (isGod) {
                long nowMs = System.currentTimeMillis();
                float auraPulse = (float) (0.5 + 0.5 * Math.sin(nowMs * 0.008));
                int auraCol = ((int) (180 + auraPulse * 75) << 24) | 0xFFFFD700;
                extractor.fill(-half - 2, -half - 2, half + 2, half + 2, auraCol);
                extractor.fill(-half - 1, -half - 1, half + 1, half + 1, 0xFF8B0000);
            }

            drawCardTexture(extractor, CARD_BACK, -half, -half, cardSize, cardSize);

            // Shading as it turns edge-on
            int shadeAlpha = (int) ((1.0f - scaleX) * 70);
            if (shadeAlpha > 0) {
                extractor.fill(-half, -half, half, half, (shadeAlpha << 24));
            }
        } else {
            // ─── CARD FRONT & REVEALED ITEM ───
            float revealT = Math.clamp((p - 0.5f) / 0.5f, 0.0f, 1.0f);

            if (isGod) {
                long nowMs = System.currentTimeMillis();
                float solarPulse = (float) (0.5 + 0.5 * Math.sin(nowMs * 0.01));
                int auraAlpha = (int) (200 + solarPulse * 55);
                // Outer molten solar corona
                extractor.fill(-half - 3, -half - 3, half + 3, half + 3, (auraAlpha << 24) | 0xFFFF4500);
                // Imperial gold border
                extractor.fill(-half - 2, -half - 2, half + 2, half + 2, 0xFFFFD700);
                // Crimson inner frame
                extractor.fill(-half - 1, -half - 1, half + 1, half + 1, 0xFF8B0000);
                // Molten pedestal shimmer
                extractor.fill(-half + 2, -half + 2, half - 2, half - 2, 0x60FFA500);
            }

            // Draw Card Front
            drawCardTexture(extractor, CARD_FRONT, -half, -half, cardSize, cardSize);

            if (!stack.isEmpty()) {
                // Rarity glow on pedestal
                if (!isGod) {
                    int rarityGlow = getRarityGlowColor(stack.getRarity());
                    if (rarityGlow != 0) {
                        extractor.fill(-half + 4, -half + 4, half - 4, half - 4, rarityGlow);
                    }
                } else {
                    // Holy solar halo on pedestal behind the god item
                    extractor.fill(-half + 3, -half + 3, half - 3, half - 3, 0x66FFD700);
                    extractor.fill(-10, -10, 10, 10, 0x88FF4500);
                }

                // Render item centered at (0, 0)
                extractor.item(stack, -8, -8);
                extractor.itemDecorations(this.font, stack, -8, -8);

                // Discount or God badge label smoothly appears as card widens
                if (revealT > 0.25f) {
                    if (isGod) {
                        extractor.centeredText(this.font, Component.literal("✦ CẤP THẦN ✦"), 1, half - 9, 0xFF880000);
                        extractor.centeredText(this.font, Component.literal("✦ CẤP THẦN ✦"), 0, half - 10, 0xFFFFD700);
                    } else {
                        extractor.centeredText(this.font,
                                Component.literal("↓ -" + VBlackMarket.getDiscountPercent(stack, syncedEpochDay, syncedResetSequence) + "%"),
                                0, half - 10, 0xFF55FF55);
                    }
                }
            }

            // Divine Supernova flash for God Item, or warm white-gold gleam for normal items
            if (isGod && revealT <= 0.75f) {
                float flashT = revealT / 0.75f;
                float flashIntensity = (float) Math.sin(flashT * PI);
                int flashAlpha = (int) (flashIntensity * 220);
                if (flashAlpha > 0) {
                    extractor.fill(-half - 4, -half - 4, half + 4, half + 4, (flashAlpha << 24) | 0xFFFFF0);
                    extractor.fill(-half - 1, -half - 1, half + 1, half + 1, (flashAlpha << 24) | 0xFFFFD700);
                }
            } else if (revealT <= 0.65f) {
                float gleamT = revealT / 0.65f;
                float gleamIntensity = (float) Math.sin(gleamT * PI);
                int gleamAlpha = (int) (gleamIntensity * 130);
                if (gleamAlpha > 0) {
                    extractor.fill(-half, -half, half, half, (gleamAlpha << 24) | 0xFFFDF0);
                }
            }
        }

        extractor.pose().popMatrix();

        // 3D Golden Rim when edge-on (glinting edge)
        if (scaleX < 0.12f) {
            int edgeAlpha = (int) (240 * (1.0f - scaleX / 0.12f));
            int edgeColor = (edgeAlpha << 24) | (isGod ? 0xFFFFD700 : 0xFFE082);
            int top = (int) (cy - half);
            int bottom = (int) (cy + half);
            extractor.fill((int) (cx - 1), top, (int) (cx + 1), bottom, edgeColor);
        }

        // Fanfare particles upon completion
        if (p >= 0.95f && !hasSpawnedFinishParticles[slotIndex]) {
            hasSpawnedFinishParticles[slotIndex] = true;
            if (isGod) {
                triggerGodFinishCelebration(cardX, cardY, cardSize, slotIndex, stack);
            } else {
                spawnFinishParticles(cardX, cardY, cardSize, slotIndex, stack);
            }
        }
    }

    private void renderRevealedCard(GuiGraphicsExtractor extractor, Slot slot, int cardX, int cardY, int cardSize,
                                    boolean isPurchased, boolean isHovered) {
        boolean isGod = slot.hasItem() && VBlackMarket.isRomanGodItem(slot.getItem());
        if (isGod && !isPurchased) {
            long now = System.currentTimeMillis();
            float pulse = (float) (0.5 + 0.5 * Math.sin(now * 0.007));
            int borderAlpha = (int) (190 + pulse * 65);
            int innerGlow = (int) (45 + pulse * 50);

            // Tier 1: Outer cosmic blaze
            extractor.fill(cardX - 3, cardY - 3, cardX + cardSize + 3, cardY + cardSize + 3, ((int)(borderAlpha * 0.6f) << 24) | 0xFFFF4500);
            // Tier 2: Outer divine golden aura border
            extractor.fill(cardX - 2, cardY - 2, cardX + cardSize + 2, cardY + cardSize + 2, (borderAlpha << 24) | 0xFFFFD700);
            // Tier 3: Inner imperial crimson border
            extractor.fill(cardX - 1, cardY - 1, cardX + cardSize + 1, cardY + cardSize + 1, 0xFF8B0000);
            // Tier 4: Inner pedestal molten shimmer
            extractor.fill(cardX + 2, cardY + 2, cardX + cardSize - 2, cardY + cardSize - 2, (innerGlow << 24) | 0xFFFFA500);

            // 4 Corner Celestial Sun Sparks
            float cornerPhase = (float) Math.sin(now * 0.006);
            int cornerAlpha = (int) (160 + cornerPhase * 80);
            int cornerCol = (cornerAlpha << 24) | 0xFFFFD700;
            extractor.text(this.font, "☼", cardX - 5, cardY - 5, cornerCol, false);
            extractor.text(this.font, "☼", cardX + cardSize - 2, cardY - 5, cornerCol, false);
            extractor.text(this.font, "☼", cardX - 5, cardY + cardSize - 2, cornerCol, false);
            extractor.text(this.font, "☼", cardX + cardSize - 2, cardY + cardSize - 2, cornerCol, false);

            if (now % 3 == 0) {
                spawnGodCardAmbientParticle(cardX, cardY, cardSize);
            }
        }

        // Draw Card Front (golden ornate frame and circular pedestal)
        drawCardTexture(extractor, CARD_FRONT, cardX, cardY, cardSize, cardSize);

        // Subtle rarity glow on pedestal (skip common for maximum performance)
        if (slot.hasItem()) {
            if (!isGod) {
                int rarityGlow = getRarityGlowColor(slot.getItem().getRarity());
                if (rarityGlow != 0 && !isPurchased) {
                    extractor.fill(cardX + 4, cardY + 4, cardX + cardSize - 4, cardY + cardSize - 4, rarityGlow);
                }
            } else if (!isPurchased) {
                // Sacred pedestal halo glow
                extractor.fill(cardX + 3, cardY + 3, cardX + cardSize - 3, cardY + cardSize - 3, 0x55FFD700);
                extractor.fill(cardX + 6, cardY + 6, cardX + cardSize - 6, cardY + cardSize - 6, 0x40FF4500);
            }
            extractor.item(slot.getItem(), slot.x, slot.y);
            extractor.itemDecorations(this.font, slot.getItem(), slot.x, slot.y);
            if (isGod) {
                long now = System.currentTimeMillis();
                int badgeW = 38;
                int badgeH = 9;
                int badgeX = cardX + (cardSize - badgeW) / 2;
                int badgeY = cardY + cardSize - 11;
                extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xEE140206);
                extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, 0xFFFFD700);
                extractor.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, 0xFFFFD700);
                int textColor = (now % 800 < 400) ? 0xFFFFD700 : 0xFFFFF0;
                extractor.centeredText(this.font, Component.literal("✦ CẤP THẦN ✦"), cardX + cardSize / 2, badgeY + 1, textColor);
            } else {
                extractor.centeredText(this.font,
                        Component.literal("↓ -" + VBlackMarket.getDiscountPercent(slot.getItem(), syncedEpochDay, syncedResetSequence) + "%"),
                        cardX + cardSize / 2, cardY + cardSize - 10, 0xFF55FF55);
            }
        }

        if (isPurchased) {
            // Darkened overlay and prominent golden SOLD badge
            extractor.fill(cardX, cardY, cardX + cardSize, cardY + cardSize, 0x88000000);
            int badgeY = cardY + (cardSize - 10) / 2;
            extractor.fill(cardX + 2, badgeY - 2, cardX + cardSize - 2, badgeY + 11, 0xDD140A20);
            extractor.fill(cardX + 2, badgeY - 2, cardX + cardSize - 2, badgeY - 1, 0xFFD4AF37);
            extractor.fill(cardX + 2, badgeY + 10, cardX + cardSize - 2, badgeY + 11, 0xFFD4AF37);
            extractor.centeredText(this.font, Component.translatable("vcoins.black_market.sold"),
                    cardX + cardSize / 2, badgeY, 0xFFE8B829);
        } else if (purchaseConfirm.isSlotPending(slot.index)) {
            purchaseConfirm.renderSlotWarningPulse(extractor, cardX, cardY, cardSize);
        } else if (isHovered) {
            // Golden purchase hover highlight
            extractor.fill(cardX, cardY, cardX + cardSize, cardY + cardSize, 0x35FFD700);
        }
    }

    private static void drawCardTexture(GuiGraphicsExtractor extractor, Identifier texture, int x, int y, int width, int height) {
        extractor.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, 128, 128, 128, 128);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        long godElapsed = (godPullStartTime > 0L) ? (now - godPullStartTime) : 999999L;
        boolean shaking = godElapsed < 420L && !VCoinsPurchaseConfirm.isReducedMotion();
        if (shaking) {
            float shakeProg = (float) godElapsed / 420.0f;
            float shakeAmp = (1.0f - shakeProg) * (1.0f - shakeProg) * 5.5f;
            float shakeX = (float) Math.sin(godElapsed * 0.14f) * shakeAmp;
            float shakeY = (float) Math.cos(godElapsed * 0.18f) * (shakeAmp * 0.65f);
            extractor.pose().pushMatrix();
            extractor.pose().translate(shakeX, shakeY);
        }

        super.extractRenderState(extractor, mouseX, mouseY, delta);
        renderGodPullCinematic(extractor);
        renderParticles(extractor);

        if (shaking) {
            extractor.pose().popMatrix();
        }
    }

    private void addParticle(float x, float y, float vx, float vy, int color, int symbol, int life) {
        int idx = particleCount < MAX_PARTICLES ? particleCount++ : ThreadLocalRandom.current().nextInt(MAX_PARTICLES);
        partX[idx] = x;
        partY[idx] = y;
        partVx[idx] = vx;
        partVy[idx] = vy;
        partColor[idx] = color;
        partSymbol[idx] = (byte) symbol;
        partSpawn[idx] = System.currentTimeMillis();
        partLife[idx] = life;
    }

    private void renderParticles(GuiGraphicsExtractor extractor) {
        if (VCoinsPurchaseConfirm.isReducedMotion()) { particleCount = 0; return; }
        if (particleCount == 0) return;
        long now = System.currentTimeMillis();

        for (int i = 0; i < particleCount; i++) {
            long elapsed = now - partSpawn[i];
            int life = partLife[i];
            if (elapsed >= life) {
                int last = particleCount - 1;
                if (i != last) {
                    partX[i] = partX[last];
                    partY[i] = partY[last];
                    partVx[i] = partVx[last];
                    partVy[i] = partVy[last];
                    partColor[i] = partColor[last];
                    partSymbol[i] = partSymbol[last];
                    partSpawn[i] = partSpawn[last];
                    partLife[i] = partLife[last];
                }
                particleCount--;
                i--;
            } else {
                float progress = (float) elapsed / life;
                float alpha = 1.0f - progress;
                int a = (int) (alpha * 255.0f);
                if (a > 0) {
                    float ease = (1.0f - (float) Math.pow(1.0f - progress, 2.5f));
                    float curX = partX[i] + partVx[i] * ease * 42.0f;
                    float curY = partY[i] + partVy[i] * ease * 42.0f - progress * progress * 24.0f;
                    int argb = (a << 24) | (partColor[i] & 0x00FFFFFF);
                    extractor.text(this.font, PARTICLE_SYMBOLS[partSymbol[i]], (int) curX, (int) curY, argb, false);
                }
            }
        }
    }

    private void renderGodPullCinematic(GuiGraphicsExtractor extractor) {
        if (VCoinsPurchaseConfirm.isReducedMotion()) return;
        if (godPullStartTime <= 0L || godPullCardIndex < 0) return;

        long now = System.currentTimeMillis();
        long elapsed = now - godPullStartTime;
        if (elapsed >= GOD_CINEMATIC_DURATION_MS) {
            return;
        }

        // Intensity curve: rises in first 200ms, holds, then gently fades
        float intensity;
        if (elapsed < 200L) {
            intensity = (float) elapsed / 200.0f;
        } else {
            intensity = 1.0f - (float) (elapsed - 200L) / (float) (GOD_CINEMATIC_DURATION_MS - 200L);
        }
        intensity = Math.clamp(intensity, 0.0f, 1.0f);

        // Center of the God card on screen
        int cardX = VBlackMarketScreenHandler.MARKET_X + godPullCardIndex * VBlackMarketScreenHandler.MARKET_COL_SPACING;
        int cardY = VBlackMarketScreenHandler.MARKET_Y;
        int cardSize = VBlackMarketScreenHandler.CARD_SIZE;
        float cx = this.leftPos + cardX + cardSize * 0.5f;
        float cy = this.topPos + cardY + cardSize * 0.5f;

        // 1. Time-Freeze Supernova Flash (first 260ms of turnover)
        if (elapsed < 260L) {
            float flashT = (float) elapsed / 260.0f;
            float flashCurve = (1.0f - flashT) * (1.0f - flashT);
            int flashA = (int) (flashCurve * 205);
            if (flashA > 0) {
                extractor.fill(0, 0, this.width, this.height, (flashA << 24) | 0xFFFFF8);
                int haloR = (int) (cardSize * 3.2f * (1.0f - flashT * 0.4f));
                extractor.fill((int) (cx - haloR), (int) (cy - haloR), (int) (cx + haloR), (int) (cy + haloR),
                        ((flashA / 2) << 24) | 0xFFFFD700);
            }
        }

        // 2. Cinematic Ambient Darkness & Letterbox Bars (Spotlight on God Card)
        int dimAlpha = (int) (intensity * 155);
        if (dimAlpha > 0) {
            extractor.fill(0, 0, this.width, this.height, (dimAlpha << 24));
        }
        int barH = 14;
        int barAlpha = (int) (intensity * 210);
        if (barAlpha > 0) {
            extractor.fill(0, 0, this.width, barH, (barAlpha << 24));
            extractor.fill(0, barH - 1, this.width, barH, (barAlpha << 24) | 0x88FFD700);
            extractor.fill(0, this.height - barH, this.width, this.height, (barAlpha << 24));
            extractor.fill(0, this.height - barH, this.width, this.height - barH + 1, (barAlpha << 24) | 0x88FFD700);
        }

        // 3. Pillar of the Heavens (5-tier volumetric divine light shaft with ascending aurora ribbons)
        int baseW = (int) (cardSize * (1.3f + 0.25f * (float) Math.sin(now * 0.007f)));
        int w1 = (int) (baseW * 3.4f);
        extractor.fill((int) (cx - w1 * 0.5f), 0, (int) (cx + w1 * 0.5f), this.height, ((int) (intensity * 26) << 24) | 0xFFFFD700);
        int w2 = (int) (baseW * 2.2f);
        extractor.fill((int) (cx - w2 * 0.5f), 0, (int) (cx + w2 * 0.5f), this.height, ((int) (intensity * 48) << 24) | 0xFFFFA000);
        int w3 = (int) (baseW * 1.35f);
        extractor.fill((int) (cx - w3 * 0.5f), 0, (int) (cx + w3 * 0.5f), this.height, ((int) (intensity * 80) << 24) | 0xFFFFE082);
        int w4 = (int) (baseW * 0.7f);
        extractor.fill((int) (cx - w4 * 0.5f), 0, (int) (cx + w4 * 0.5f), this.height, ((int) (intensity * 125) << 24) | 0xFFFFFFEE);
        int w5 = Math.max(3, (int) (baseW * 0.28f));
        extractor.fill((int) (cx - w5 * 0.5f), 0, (int) (cx + w5 * 0.5f), this.height, ((int) (intensity * 180) << 24) | 0xFFFFFFFF);

        // Ascending Aurora Energy Ribbons
        for (int s = 0; s < 8; s++) {
            float xOff = (float) Math.sin(s * 1.7f + now * 0.003f) * (cardSize * 0.55f);
            float yPos = (float) ((now * (0.32f + s * 0.07f) + s * 70) % this.height);
            int streakH = 20 + (s % 3) * 10;
            int streakAlpha = (int) (intensity * (60 + (s % 3) * 35));
            extractor.fill((int) (cx + xOff - 1), (int) (this.height - yPos - streakH),
                    (int) (cx + xOff + 1), (int) (this.height - yPos),
                    (streakAlpha << 24) | 0xFFFFFF);
        }

        // 4. Triple Concentric Expanding Shockwaves
        float wave1 = Math.clamp(elapsed / 900.0f, 0.0f, 1.0f);
        if (wave1 < 1.0f) {
            float r1 = wave1 * 260.0f;
            int alpha1 = (int) ((1.0f - wave1) * 230 * intensity);
            drawShockwave(extractor, cx, cy, r1, (alpha1 << 24) | 0xFFFFD700, 2);
        }

        if (elapsed > 140L) {
            float wave2 = Math.clamp((elapsed - 140L) / 900.0f, 0.0f, 1.0f);
            if (wave2 < 1.0f) {
                float r2 = wave2 * 210.0f;
                int alpha2 = (int) ((1.0f - wave2) * 200 * intensity);
                drawShockwave(extractor, cx, cy, r2, (alpha2 << 24) | 0xFFFF4500, 2);
            }
        }

        if (elapsed > 280L) {
            float wave3 = Math.clamp((elapsed - 280L) / 900.0f, 0.0f, 1.0f);
            if (wave3 < 1.0f) {
                float r3 = wave3 * 160.0f;
                int alpha3 = (int) ((1.0f - wave3) * 170 * intensity);
                drawShockwave(extractor, cx, cy, r3, (alpha3 << 24) | 0xFFFFF8, 2);
            }
        }

        // 5. Dual Counter-Rotating Celestial Mandalas (Outer Solar Wheel + Inner Octagram Seal)
        float baseAngle = now * 0.0014f;
        int rayAlpha = (int) (intensity * 190);
        if (rayAlpha > 0) {
            for (int r = 0; r < 12; r++) {
                double a = baseAngle + r * (TWO_PI / 12.0);
                float rayLen = 44.0f + 20.0f * (float) Math.sin(now * 0.007f + r * 0.8f);
                float rx = cx + (float) Math.cos(a) * rayLen;
                float ry = cy + (float) Math.sin(a) * rayLen;
                int dotCol = (rayAlpha << 24) | (r % 3 == 0 ? 0xFFFFFFFF : (r % 2 == 0 ? 0xFFFFD700 : 0xFFFF8C00));
                extractor.fill((int) (rx - 1.5f), (int) (ry - 1.5f), (int) (rx + 2.5f), (int) (ry + 2.5f), dotCol);
            }
        }

        float octAngle = now * -0.0022f;
        int sealAlpha = (int) (intensity * 180);
        if (sealAlpha > 0) {
            float sealRadius = 36.0f + 4.0f * (float) Math.sin(now * 0.009f);
            String[] sealGlyphs = {"✦", "⚜", "★", "☼", "✧", "⚜", "★", "☼"};
            for (int p = 0; p < 8; p++) {
                double a = octAngle + p * (TWO_PI / 8.0);
                float px = cx + (float) Math.cos(a) * sealRadius;
                float py = cy + (float) Math.sin(a) * sealRadius;
                int glyphCol = (sealAlpha << 24) | (p % 2 == 0 ? 0xFFFFD700 : 0xFFFF4500);
                extractor.text(this.font, sealGlyphs[p], (int) (px - 3), (int) (py - 4), glyphCol, false);
            }
        }

        // 6. Centerpiece: The Heroic Floating Transcended God Card
        ItemStack godStack = (this.menu.slots.size() > godPullCardIndex) ? this.menu.slots.get(godPullCardIndex).getItem() : ItemStack.EMPTY;
        if (!godStack.isEmpty()) {
            float hoverBob = (float) Math.sin(now * 0.008f) * 3.5f;
            float heroScale = 1.16f + 0.04f * (float) Math.sin(now * 0.006f);
            int half = cardSize / 2;

            extractor.pose().pushMatrix();
            extractor.pose().translate(cx, cy - hoverBob);
            extractor.pose().scale(heroScale, heroScale);

            // 4-layer blazing solar corona
            extractor.fill(-half - 5, -half - 5, half + 5, half + 5, 0x90FF4500);
            extractor.fill(-half - 3, -half - 3, half + 3, half + 3, 0xD0FFD700);
            extractor.fill(-half - 2, -half - 2, half + 2, half + 2, 0xFFFF8C00);
            extractor.fill(-half - 1, -half - 1, half + 1, half + 1, 0xFF8B0000);

            drawCardTexture(extractor, CARD_FRONT, -half, -half, cardSize, cardSize);

            // Holy solar halo on pedestal
            extractor.fill(-half + 3, -half + 3, half - 3, half - 3, 0x66FFD700);
            extractor.fill(-10, -10, 10, 10, 0x90FFA500);

            extractor.item(godStack, -8, -8);
            extractor.itemDecorations(this.font, godStack, -8, -8);

            // Golden badge
            int badgeW = 38;
            int badgeH = 9;
            int badgeX = -badgeW / 2;
            int badgeY = half - 11;
            extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xEE140206);
            extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, 0xFFFFD700);
            extractor.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, 0xFFFFD700);
            int textColor = (now % 800 < 400) ? 0xFFFFD700 : 0xFFFFF0;
            extractor.centeredText(this.font, Component.literal("✦ CẤP THẦN ✦"), 0, badgeY + 1, textColor);

            extractor.pose().popMatrix();
        }

        // 7. Imperial Floating Decree Banner ("✦ LA MÃ THẦN BẢO GIÁNG THẾ ✦")
        if (elapsed > 100L) {
            float bannerT = Math.clamp((elapsed - 100L) / 280.0f, 0.0f, 1.0f);
            float bannerAlphaF = bannerT * intensity;
            int textAlpha = (int) (bannerAlphaF * 255.0f);
            if (textAlpha > 10) {
                float bob = (float) Math.sin(now * 0.005f) * 2.5f;
                int bannerY = (int) (this.topPos + 14 - bob);
                int screenMidX = this.width / 2;

                int plaqueW = 240;
                int plaqueH = 26;
                int plaqueX = screenMidX - plaqueW / 2;
                int bgAlpha = (int) (bannerAlphaF * 230.0f);

                extractor.fill(plaqueX, bannerY - 3, plaqueX + plaqueW, bannerY + plaqueH, (bgAlpha << 24) | 0x140206);
                int borderCol = (textAlpha << 24) | 0xFFFFD700;
                int subBorderCol = (textAlpha << 24) | 0xFF8B0000;
                extractor.fill(plaqueX, bannerY - 4, plaqueX + plaqueW, bannerY - 3, borderCol);
                extractor.fill(plaqueX, bannerY - 3, plaqueX + plaqueW, bannerY - 2, subBorderCol);
                extractor.fill(plaqueX, bannerY + plaqueH - 1, plaqueX + plaqueW, bannerY + plaqueH, subBorderCol);
                extractor.fill(plaqueX, bannerY + plaqueH, plaqueX + plaqueW, bannerY + plaqueH + 1, borderCol);

                // Left & Right ornate brackets
                extractor.text(this.font, "«« ⚜", plaqueX + 4, bannerY + 4, (textAlpha << 24) | 0xFFFFD700, false);
                extractor.text(this.font, "⚜ »»", plaqueX + plaqueW - 28, bannerY + 4, (textAlpha << 24) | 0xFFFFD700, false);

                // Crown & Sparkles centered above
                Component crownComp = Component.literal("✧  👑  ✧");
                extractor.centeredText(this.font, crownComp, screenMidX, bannerY - 14, (textAlpha << 24) | 0xFFFFD700);

                // Main Title: ✦ LA MÃ THẦN BẢO GIÁNG THẾ ✦
                Component titleComp = Component.translatable("vcoins.black_market.cinematic.title");
                extractor.centeredText(this.font, titleComp, screenMidX + 1, bannerY + 2, (textAlpha << 24) | 0x880000);
                extractor.centeredText(this.font, titleComp, screenMidX, bannerY + 1, (textAlpha << 24) | 0xFFFFF0);

                // Subtitle: ⚜ QUYỀN NĂNG TỐI CAO ĐÃ THỨC TỈNH ⚜
                Component subComp = Component.translatable("vcoins.black_market.cinematic.subtitle");
                int subCol = (now % 600 < 300) ? 0xFFFFD700 : 0xFFFF8C00;
                extractor.centeredText(this.font, subComp, screenMidX, bannerY + 14, (textAlpha << 24) | subCol);
            }
        }
    }

    private static void drawShockwave(GuiGraphicsExtractor extractor, float cx, float cy, float radius, int color, int thickness) {
        int r = (int) radius;
        if (r <= 2) return;
        int d = (int) (r * 0.7071f);
        extractor.fill((int) (cx - r * 0.4f), (int) (cy - r - thickness), (int) (cx + r * 0.4f), (int) (cy - r), color);
        extractor.fill((int) (cx - r * 0.4f), (int) (cy + r), (int) (cx + r * 0.4f), (int) (cy + r + thickness), color);
        extractor.fill((int) (cx - r - thickness), (int) (cy - r * 0.4f), (int) (cx - r), (int) (cy + r * 0.4f), color);
        extractor.fill((int) (cx + r), (int) (cy - r * 0.4f), (int) (cx + r + thickness), (int) (cy + r * 0.4f), color);

        extractor.fill((int) (cx - d - thickness), (int) (cy - d - thickness), (int) (cx - d * 0.5f), (int) (cy - d), color);
        extractor.fill((int) (cx + d * 0.5f), (int) (cy - d - thickness), (int) (cx + d + thickness), (int) (cy - d), color);
        extractor.fill((int) (cx - d - thickness), (int) (cy + d), (int) (cx - d * 0.5f), (int) (cy + d + thickness), color);
        extractor.fill((int) (cx + d * 0.5f), (int) (cy + d), (int) (cx + d + thickness), (int) (cy + d + thickness), color);
    }

    private void triggerGodPullCelebration(int cardX, int cardY, int cardSize, ItemStack stack) {
        float originX = this.leftPos + cardX + cardSize * 0.5f;
        float originY = this.topPos + cardY + cardSize * 0.5f;

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 1. Audio Orchestra: Totem of Undying + Toast Challenge + Level Up + Beacon hum
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 0.90f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.15f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 0.70f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.35f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.4f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f));
        }

        // 2. High-speed Shockwave Starburst Ring (42 particles shooting outward)
        int[] palette = {0xFFFFD700, 0xFFFF4500, 0xFFFF2244, 0xFFFFFFFF, 0xFFFFE082, 0xFFFF8C00, 0xFFFF1744};
        for (int i = 0; i < 42; i++) {
            double angle = (TWO_PI / 42.0) * i + rng.nextDouble(-0.15, 0.15);
            float speed = (float) rng.nextDouble(1.8, 3.6);
            int color = palette[rng.nextInt(palette.length)];
            int symIdx = rng.nextInt(PARTICLE_SYMBOLS.length);
            int life = rng.nextInt(650, 1050);
            addParticle(
                    originX, originY,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    color, symIdx, life
            );
        }

        // 3. Ascending Divine Embers (24 holy embers floating upwards towards heavens)
        for (int i = 0; i < 24; i++) {
            float ox = originX + (rng.nextFloat() - 0.5f) * cardSize * 1.4f;
            float oy = originY + (rng.nextFloat() - 0.5f) * cardSize * 0.6f;
            float vx = (rng.nextFloat() - 0.5f) * 0.7f;
            float vy = -1.2f - rng.nextFloat() * 1.8f;
            int color = rng.nextBoolean() ? 0xFFFFD700 : 0xFFFF8C00;
            int symIdx = rng.nextInt(4); // ✦, ✧, ⋆, ★
            int life = rng.nextInt(850, 1500);
            addParticle(ox, oy, vx, vy, color, symIdx, life);
        }

        // 4. Sacred Crown & Lightning Runes (12 majestic symbols)
        int[] royalSyms = {4, 6, 7, 5, 9, 10}; // ⚡, ⚜, 👑, ☼, ᛟ, ᚱ
        for (int i = 0; i < 12; i++) {
            double angle = rng.nextDouble(0, TWO_PI);
            float speed = (float) rng.nextDouble(0.8, 1.8);
            int color = rng.nextBoolean() ? 0xFFFFD700 : 0xFFFF2244;
            int symIdx = royalSyms[rng.nextInt(royalSyms.length)];
            int life = rng.nextInt(750, 1200);
            addParticle(
                    originX, originY,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    color, symIdx, life
            );
        }
    }

    private void triggerGodFinishCelebration(int cardX, int cardY, int cardSize, int slotIndex, ItemStack stack) {
        float originX = this.leftPos + cardX + cardSize * 0.5f;
        float originY = this.topPos + cardY + cardSize * 0.5f;

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.25f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.45f));
        }

        // Grand Fanfare Nova: 42 sparkling particles bursting outward
        int[] palette = {0xFFFFD700, 0xFFFFE082, 0xFFFF8C00, 0xFFFFFFFF, 0xFFFF1744, 0xFFFF4500};
        for (int i = 0; i < 42; i++) {
            double angle = (TWO_PI / 42.0) * i + rng.nextDouble(-0.1, 0.1);
            float speed = (float) rng.nextDouble(1.2, 3.0);
            int color = palette[rng.nextInt(palette.length)];
            int symIdx = rng.nextInt(PARTICLE_SYMBOLS.length);
            int life = rng.nextInt(700, 1200);
            addParticle(
                    originX, originY,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    color, symIdx, life
            );
        }
    }

    private void spawnFlipStartParticles(int cardX, int cardY, int cardSize) {
        float originX = this.leftPos + cardX + cardSize * 0.5f;
        float originY = this.topPos + cardY + cardSize * 0.5f;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            double angle = rng.nextDouble(0, TWO_PI);
            float speed = (float) rng.nextDouble(0.4, 1.0);
            int life = rng.nextInt(250, 480);
            addParticle(
                    originX, originY,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    0xFFD700, 1, life
            );
        }
    }

    private void spawnFlipMidParticles(int cardX, int cardY, int cardSize, ItemStack stack) {
        float originX = this.leftPos + cardX + cardSize * 0.5f;
        float originY = this.topPos + cardY + cardSize * 0.5f;

        boolean isGod = !stack.isEmpty() && VBlackMarket.isRomanGodItem(stack);
        int baseColor = isGod ? 0xFFFFD700 : 0xFFE0FFFF;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = isGod ? 12 : 8;
        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble(0, TWO_PI);
            float speed = (float) rng.nextDouble(0.8, 1.8);
            int color = rng.nextBoolean() ? baseColor : 0xFFFFFFFF;
            int symIdx = rng.nextInt(PARTICLE_SYMBOLS.length);
            int life = rng.nextInt(280, 500);
            addParticle(
                    originX, originY,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    color, symIdx, life
            );
        }

        if (this.minecraft != null) {
            float pitch = 1.15f + (rng.nextFloat() * 0.2f);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, pitch));
        }
    }

    private void spawnGodCardAmbientParticle(int cardX, int cardY, int cardSize) {
        float originX = this.leftPos + cardX + ThreadLocalRandom.current().nextFloat(cardSize);
        float originY = this.topPos + cardY + ThreadLocalRandom.current().nextFloat(cardSize);
        addParticle(
                originX, originY,
                (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.4f,
                -0.6f - ThreadLocalRandom.current().nextFloat() * 0.4f,
                ThreadLocalRandom.current().nextBoolean() ? 0xFFFFD700 : 0xFFFF4500,
                ThreadLocalRandom.current().nextInt(PARTICLE_SYMBOLS.length),
                ThreadLocalRandom.current().nextInt(300, 600)
        );
    }

    private void spawnFinishParticles(int cardX, int cardY, int cardSize, int slotIndex, ItemStack stack) {
        float originX = this.leftPos + cardX + cardSize * 0.5f;
        float originY = this.topPos + cardY + cardSize * 0.5f;

        boolean isGod = VBlackMarket.isRomanGodItem(stack);
        Rarity rarity = stack.getRarity();
        int baseColor = switch (rarity) {
            case EPIC -> 0xFF3DF0;      // Magenta
            case RARE -> 0x00E5FF;      // Cyan
            case UNCOMMON -> 0xFFD700;  // Gold
            case COMMON -> 0xFFFFFF;    // White
        };

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = isGod ? 28 : switch (rarity) {
            case EPIC -> 12;
            case RARE -> 8;
            default -> 5;
        };
        float angleStep = TWO_PI / count;

        for (int i = 0; i < count; i++) {
            double angle = angleStep * i + rng.nextDouble(-0.3, 0.3);
            float speed = (float) rng.nextDouble(isGod ? 0.8 : 0.6, isGod ? 1.8 : 1.3);
            int symIdx = rng.nextInt(PARTICLE_SYMBOLS.length);
            int color;
            if (isGod) {
                color = rng.nextBoolean() ? 0xFFFFD700 : 0xFFFF2222;
            } else {
                color = rng.nextBoolean() ? baseColor : 0xFFD700;
            }
            int life = rng.nextInt(isGod ? 500 : 350, isGod ? 850 : 650);
            addParticle(
                    originX, originY,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    color, symIdx, life
            );
        }

        if (this.minecraft != null) {
            if (isGod) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.1f));
            } else {
                float pitch = 1.2f + (slotIndex % 6) * 0.1f;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch));
            }
        }
    }

    private static int getRarityGlowColor(Rarity rarity) {
        if (rarity == null) return 0;
        return switch (rarity) {
            case EPIC -> 0x3DF040FB;    // Magenta glow
            case RARE -> 0x3D00E5FF;    // Cyan glow
            case UNCOMMON -> 0x35FFD700;// Amber gold glow
            case COMMON -> 0;           // Common skips fill to save draw calls
        };
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.text(this.font, INVENTORY_LABEL, this.inventoryLabelX, this.inventoryLabelY, 0xFFC8A96E, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int hoveredMarketSlot = getMarketSlotAt(mouseX, mouseY);
        if (hoveredMarketSlot >= 0) {
            extractMarketSlotTooltip(extractor, mouseX, mouseY, hoveredMarketSlot);
            return;
        }

        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            renderSlotItemTooltip(extractor, mouseX, mouseY, this.hoveredSlot.index);
            return;
        }

        super.extractTooltip(extractor, mouseX, mouseY);
    }

    private void extractMarketSlotTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY, int slot) {
        long now = System.currentTimeMillis();
        boolean isResetting = !VCoinsPurchaseConfirm.isReducedMotion()
                && resetAnimationStartTime > 0
                && now < resetAnimationStartTime + RESET_ANIM_DURATION_MS;
        if (isResetting) {
            return;
        }

        long start = flipStartTime[slot];
        boolean isFlipping = !VCoinsPurchaseConfirm.isReducedMotion() && start > 0 && now >= start && now < start + FLIP_DURATION_MS;
        if (isFlipping) {
            return;
        }

        boolean isRevealed = ((syncedRevealedMask & (1 << slot)) != 0) || localRevealedCards.contains(slot);
        if (!isRevealed || (start > 0 && now < start)) {
            extractor.setTooltipForNextFrame(this.font, UNREVEALED_TOOLTIP, Optional.empty(), mouseX, mouseY);
            return;
        }

        boolean isPurchased = (syncedPurchasedMask & (1 << slot)) != 0;
        if (isPurchased) {
            extractor.setTooltipForNextFrame(this.font, SOLD_TOOLTIP, Optional.empty(), mouseX, mouseY);
            return;
        }
        renderSlotItemTooltip(extractor, mouseX, mouseY, slot);
    }

    private void renderSlotItemTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY, int slotIdx) {
        long now = System.currentTimeMillis();
        long start = (slotIdx >= 0 && slotIdx < VBlackMarketScreenHandler.MARKET_SLOT_COUNT) ? flipStartTime[slotIdx] : 0;
        boolean isFlipping = !VCoinsPurchaseConfirm.isReducedMotion() && start > 0 && now >= start && now < start + FLIP_DURATION_MS;

        boolean isRevealed = (slotIdx < VBlackMarketScreenHandler.MARKET_SLOT_COUNT)
                && (((syncedRevealedMask & (1 << slotIdx)) != 0) || localRevealedCards.contains(slotIdx));
        if (slotIdx < VBlackMarketScreenHandler.MARKET_SLOT_COUNT && (!isRevealed || isFlipping)) {
            return;
        }

        ItemStack stack = this.menu.slots.get(slotIdx).getItem();
        List<Component> tooltip = VeloriaTooltip.withoutPrices(this.getTooltipFromContainerItem(stack));
        long buyPrice = VCoinsPricing.getPrice(stack);

        if (slotIdx < VBlackMarketScreenHandler.MARKET_SLOT_COUNT) {
            buildMarketItemTooltip(tooltip, stack, VBlackMarket.getDiscountedPrice(stack, syncedEpochDay, syncedResetSequence));
        } else {
            buildInventoryItemTooltip(tooltip, stack, buyPrice);
        }

        extractor.setTooltipForNextFrame(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY,
                    stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
    }

    private void buildMarketItemTooltip(List<Component> tooltip, ItemStack stack, long buyPrice) {
        if (VBlackMarket.isRomanGodItem(stack)) {
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("✦ BẢO VẬT LA MÃ CẤP THẦN ✦").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            tooltip.add(Component.literal("Định giá Độc Lập Chợ Đen:").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("★ " + formatNumber(buyPrice) + " Velicoins ★").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            tooltip.add(BUY_LEFT);
            return;
        }
        tooltip.add(Component.empty());
        tooltip.add(MERCHANT_TAG);
        tooltip.add(Component.translatable("vcoins.black_market.discount", VBlackMarket.getDiscountPercent(stack, syncedEpochDay, syncedResetSequence)).withStyle(ChatFormatting.GREEN));
        if (buyPrice > 0) {
            tooltip.add(Component.translatable("vcoins.black_market.buy_price", formatNumber(buyPrice))
                    .withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(BUY_LEFT);
    }

    private void buildInventoryItemTooltip(List<Component> tooltip, ItemStack stack, long buyPrice) {
        long sellPrice = VCoinsPricing.getSellPrice(stack);
        if (buyPrice > 0 || sellPrice > 0) {
            tooltip.add(Component.empty());
            if (buyPrice > 0) {
                tooltip.add(Component.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                    .withStyle(ChatFormatting.YELLOW));
            }
        }
        if (sellPrice > 0) {
            tooltip.add(Component.translatable("vcoins.tooltip.sell_price", formatNumber(sellPrice))
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        int slot = getMarketSlotAt(click.x(), click.y());
        if (slot >= 0) {
            return handleMarketSlotClick(slot, click);
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean handleMarketSlotClick(int slot, net.minecraft.client.input.MouseButtonEvent click) {
        long now = System.currentTimeMillis();
        boolean isResetting = !VCoinsPurchaseConfirm.isReducedMotion()
                && resetAnimationStartTime > 0
                && now < resetAnimationStartTime + RESET_ANIM_DURATION_MS;
        if (isResetting) {
            return true;
        }

        long start = flipStartTime[slot];
        boolean isRevealed = ((syncedRevealedMask & (1 << slot)) != 0) || localRevealedCards.contains(slot);
        boolean isPurchased = (syncedPurchasedMask & (1 << slot)) != 0;

        // If already purchased or currently flipping, ignore click
        if (isPurchased || (!VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now >= start && now < start + FLIP_DURATION_MS)) {
            return true;
        }

        if (!isRevealed) {
            flipCard(slot, now);
            return true;
        }

        purchaseMarketSlot(slot, click);
        return true;
    }

    private void flipCard(int slot, long now) {
        localRevealedCards.add(slot);
        flipStartTime[slot] = now;
        hasSpawnedStartParticles[slot] = false;
        hasSpawnedMidParticles[slot] = false;
        hasSpawnedFinishParticles[slot] = false;

        ClientPlayNetworking.send(new BlackMarketRevealPayload(slot));

        if (this.minecraft != null) {
            float pitch = 0.9f + (slot % 8) * 0.1f;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.1f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch));
        }
        updateButtonStates();
    }

    private void purchaseMarketSlot(int slot, net.minecraft.client.input.MouseButtonEvent click) {
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT && click.button() != InputConstants.MOUSE_BUTTON_RIGHT) {
            return;
        }
        boolean buyStack = click.button() == InputConstants.MOUSE_BUTTON_RIGHT || click.hasShiftDown();

        Slot s = this.menu.slots.get(slot);
        if (s == null || !s.hasItem()) {
            return;
        }
        ItemStack stack = s.getItem();
        long unitPrice = VBlackMarket.getDiscountedPrice(stack, syncedEpochDay, syncedResetSequence);
        int amount = buyStack ? stack.getMaxStackSize() : 1;
        long totalCost = safeMultiply(unitPrice, amount);

        if (purchaseConfirm.checkOrArm(slot, buyStack, stack, totalCost, this.minecraft)) {
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            }
            ClientPlayNetworking.send(new BlackMarketBuyPayload(slot, buyStack));
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        boolean isConfirmKey = event.key() == InputConstants.KEY_SPACE
                || event.key() == InputConstants.KEY_RETURN
                || event.key() == InputConstants.KEY_NUMPADENTER;
        if (isConfirmKey && purchaseConfirm.handleKeyPress((slot, buyStack) -> {
            ClientPlayNetworking.send(new BlackMarketBuyPayload(slot, buyStack));
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
            }
        })) {
            return true;
        }
        return super.keyPressed(event);
    }

    private static long safeMultiply(long price, int count) {
        try {
            return Math.multiplyExact(price, (long) count);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private int getMarketSlotAt(double mouseX, double mouseY) {
        double localX = mouseX - (this.leftPos + VBlackMarketScreenHandler.MARKET_X);
        double localY = mouseY - (this.topPos + VBlackMarketScreenHandler.MARKET_Y);
        if (localX < 0 || localY < 0 || localY >= VBlackMarketScreenHandler.CARD_SIZE) {
            return -1;
        }

        int col = (int) (localX / VBlackMarketScreenHandler.MARKET_COL_SPACING);
        if (col >= VBlackMarketScreenHandler.MARKET_COLUMNS) {
            return -1;
        }

        if (localX - col * VBlackMarketScreenHandler.MARKET_COL_SPACING >= VBlackMarketScreenHandler.CARD_SIZE) {
            return -1;
        }

        return col;
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatCompactNumber(long value) {
        if (value < 1_000L) {
            return Long.toString(value);
        }
        double scaled;
        String suffix;
        if (value >= 1_000_000_000_000L) {
            scaled = value / 1_000_000_000_000.0;
            suffix = "T";
        } else if (value >= 1_000_000_000L) {
            scaled = value / 1_000_000_000.0;
            suffix = "B";
        } else if (value >= 1_000_000L) {
            scaled = value / 1_000_000.0;
            suffix = "M";
        } else {
            scaled = value / 1_000.0;
            suffix = "K";
        }
        return (scaled >= 100.0 ? String.format(Locale.ROOT, "%.0f", scaled) : String.format(Locale.ROOT, "%.1f", scaled).replace(".0", "")) + suffix;
    }
}
