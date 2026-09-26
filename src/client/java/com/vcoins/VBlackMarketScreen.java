package com.vcoins;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
    private static final Identifier BLACK_MARKET_MENU =
            Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/black_market_menu.png");
    private static final Identifier CARD_BACK =
            Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_back.png");
    private static final Identifier CARD_FRONT =
            Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_front.png");

    private static final long FLIP_DURATION_MS = 450L;
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

    // Zero-allocation particle pool
    private static final int MAX_PARTICLES = 64;
    private static final String[] PARTICLE_SYMBOLS = {"✦", "✧", "⋆", "•"};
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
    private final boolean[] hasSpawnedFinishParticles = new boolean[VBlackMarketScreenHandler.MARKET_SLOT_COUNT];

    // Balance text cache
    private long lastBalance = -1L;
    private Component cachedBalanceComponent = null;

    private Button revealAllButton;
    private Button verifyToggleButton;
    private final VCoinsPurchaseConfirm purchaseConfirm = new VCoinsPurchaseConfirm();

    public static void handleSyncPayload(BlackMarketSyncPayload payload) {
        if (payload.epochDay() != syncedEpochDay) {
            syncedEpochDay = payload.epochDay();
        }
        baseSecondsRemaining = payload.secondsUntilReset();
        lastSyncTimeMs = System.currentTimeMillis();
        syncedRevealedMask = payload.revealedMask();
        syncedPurchasedMask = payload.purchasedMask();
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
        this.addRenderableWidget(Button.builder(Component.translatable("vcoins.back_to_shop"), button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenShopPayload());
                })
                .bounds(this.leftPos + 8, this.topPos + 5, 36, 16)
                .build());

        // Công tắc bật/tắt xác minh giao dịch >100k
        this.verifyToggleButton = this.addRenderableWidget(Button.builder(
                VCoinsPurchaseConfirm.getToggleLabel(),
                button -> {
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
                })
                .bounds(this.leftPos + 46, this.topPos + 5, 52, 16)
                .tooltip(VCoinsPurchaseConfirm.getToggleTooltip())
                .build());

        // Nút Lật tất cả các lá bài (Reveal All)
        this.revealAllButton = this.addRenderableWidget(Button.builder(Component.translatable("vcoins.black_market.reveal_all"), button -> revealAllCards())
                .bounds(this.leftPos + 188, this.topPos + 5, 60, 16)
                .build());
        updateRevealAllButtonState();
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
                flipStartTime[i] = now + staggerIndex * 60L;
                hasSpawnedStartParticles[i] = false;
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
        updateRevealAllButtonState();
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

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractBackground(extractor, mouseX, mouseY, delta);

        // Draw custom 256x246 Black Market GUI
        extractor.blit(RenderPipelines.GUI_TEXTURED, BLACK_MARKET_MENU,
                this.leftPos, this.topPos, 0.0f, 0.0f, 256, 246, 256, 256);

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
        }
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

        boolean hasNotStartedYet = isRevealed && start > 0 && now < start;
        boolean isFlipping = isRevealed && start > 0 && now >= start && now < start + FLIP_DURATION_MS;
        boolean isFullyRevealed = isRevealed && (start == 0 || now >= start + FLIP_DURATION_MS);

        if (!isRevealed || hasNotStartedYet) {
            renderUnrevealedCard(extractor, cardX, cardY, cardSize, isHovered, now);
        } else if (isFlipping) {
            float progress = (float) (now - start) / (float) FLIP_DURATION_MS;
            renderFlippingCard(extractor, slot, slotIndex, cardX, cardY, cardSize, progress, now);
        } else if (isFullyRevealed) {
            renderRevealedCard(extractor, slot, cardX, cardY, cardSize, isPurchased, isHovered);
        }
    }

    private void renderUnrevealedCard(GuiGraphicsExtractor extractor, int cardX, int cardY, int cardSize, boolean isHovered, long now) {
        int liftY = isHovered ? 1 : 0;
        int drawY = cardY - liftY;

        // Draw custom card back with Veloria Golden Crest scaled to full 40x40 card
        drawCardTexture(extractor, CARD_BACK, cardX, drawY, cardSize, cardSize);

        if (isHovered) {
            // Smooth single-pass golden pulse overlay
            float pulse = (float) Math.sin((now & 1023) * (TWO_PI / 1024.0f));
            int alpha = (int) (35 + 25 * Math.max(0.0f, pulse));
            extractor.fill(cardX, drawY, cardX + cardSize, drawY + cardSize, (alpha << 24) | 0xFFD700);
        }
    }

    private void renderFlippingCard(GuiGraphicsExtractor extractor, Slot slot, int slotIndex,
                                    int cardX, int cardY, int cardSize, float progress, long now) {
        if (!hasSpawnedStartParticles[slotIndex]) {
            hasSpawnedStartParticles[slotIndex] = true;
            spawnFlipStartParticles(cardX, cardY, cardSize);
        }

        float progressRad = progress * PI;
        float sinProgress = (float) Math.sin(progressRad);

        // Smooth cosine easing
        float t = (1.0f - (float) Math.cos(progressRad)) * 0.5f;
        float rawScale = (float) Math.cos(t * PI);
        float scaleX = Math.abs(rawScale);

        // Tactile elastic spring bounce near end
        if (progress > 0.75f) {
            float bounce = (float) Math.sin((progress - 0.75f) * 4.0f * PI) * 0.12f;
            scaleX = Math.min(1.08f, scaleX + bounce);
        }

        // 3D card lift off table
        float liftY = 5.0f * sinProgress;
        float cx = cardX + cardSize * 0.5f;
        float cy = cardY + cardSize * 0.5f;

        // Shadow beneath the lifted card
        float shadowW = (cardSize - 2) * (0.3f + 0.7f * scaleX);
        extractor.fill((int) (cx - shadowW * 0.5f), cardY + cardSize - 2,
                (int) (cx + shadowW * 0.5f), cardY + cardSize + 3, 0x44000000);

        // 3D Transform
        extractor.pose().pushMatrix();
        extractor.pose().translate(0.0f, -liftY);
        extractor.pose().scaleAround(Math.max(0.04f, scaleX), 1.0f, cx, cy);

        if (t < 0.5f) {
            drawCardTexture(extractor, CARD_BACK, cardX, cardY, cardSize, cardSize);
        } else {
            drawCardTexture(extractor, CARD_FRONT, cardX, cardY, cardSize, cardSize);
            if (slot.hasItem()) {
                extractor.item(slot.getItem(), slot.x, slot.y);
            }
        }

        // Luminous cyan light sweep shine - inside matrix so it transforms with card rotation
        if (progress >= 0.35f && progress <= 0.75f) {
            float shine = (float) Math.sin((progress - 0.35f) * (PI / 0.40f));
            int shineAlpha = (int) (shine * 150);
            int shineColor = (shineAlpha << 24) | 0x00E0FFFF;
            extractor.fill(cardX, cardY, cardX + cardSize, cardY + cardSize, shineColor);
        }
        extractor.pose().popMatrix();

        // 3D Card Edge Thickness when angled
        if (scaleX < 0.85f) {
            int edgeAlpha = (int) (210 * (1.0f - scaleX));
            int edgeColor = (edgeAlpha << 24) | 0xFFE082; // Gilded gold card rim
            int edgeW = Math.max(1, (int) (3.0f * (1.0f - scaleX)));
            int edgeTop = (int) (cardY - liftY);
            int edgeBottom = edgeTop + cardSize;
            extractor.fill((int) (cx - edgeW * 0.5f), edgeTop,
                    (int) (cx + edgeW * 0.5f), edgeBottom, edgeColor);
        }

        // Sparkle burst upon completion
        if (progress >= 0.90f && !hasSpawnedFinishParticles[slotIndex]) {
            hasSpawnedFinishParticles[slotIndex] = true;
            spawnFinishParticles(cardX, cardY, cardSize, slotIndex, slot.getItem());
        }
    }

    private void renderRevealedCard(GuiGraphicsExtractor extractor, Slot slot, int cardX, int cardY, int cardSize,
                                    boolean isPurchased, boolean isHovered) {
        // Draw Card Front (golden ornate frame and circular pedestal)
        drawCardTexture(extractor, CARD_FRONT, cardX, cardY, cardSize, cardSize);

        // Subtle rarity glow on pedestal (skip common for maximum performance)
        if (slot.hasItem()) {
            int rarityGlow = getRarityGlowColor(slot.getItem().getRarity());
            if (rarityGlow != 0 && !isPurchased) {
                extractor.fill(cardX + 4, cardY + 4, cardX + cardSize - 4, cardY + cardSize - 4, rarityGlow);
            }
            extractor.item(slot.getItem(), slot.x, slot.y);
            extractor.itemDecorations(this.font, slot.getItem(), slot.x, slot.y);
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
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        renderParticles(extractor);
    }

    private void addParticle(float x, float y, float vx, float vy, int color, int symbol, long now, int life) {
        int idx = particleCount < MAX_PARTICLES ? particleCount++ : ThreadLocalRandom.current().nextInt(MAX_PARTICLES);
        partX[idx] = x;
        partY[idx] = y;
        partVx[idx] = vx;
        partVy[idx] = vy;
        partColor[idx] = color;
        partSymbol[idx] = (byte) symbol;
        partSpawn[idx] = now;
        partLife[idx] = life;
    }

    private void renderParticles(GuiGraphicsExtractor extractor) {
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
                continue;
            }
            float progress = (float) elapsed / life;
            float alpha = 1.0f - progress;
            float curX = partX[i] + partVx[i] * progress * 20.0f;
            float curY = partY[i] + partVy[i] * progress * 20.0f - progress * 14.0f;
            int a = (int) (alpha * 255.0f);
            if (a <= 0) continue;
            int argb = (a << 24) | (partColor[i] & 0x00FFFFFF);
            extractor.text(this.font, PARTICLE_SYMBOLS[partSymbol[i]], (int) curX, (int) curY, argb, false);
        }
    }

    private void spawnFlipStartParticles(int cardX, int cardY, int cardSize) {
        float originX = this.leftPos + cardX + cardSize * 0.5f;
        float originY = this.topPos + cardY + cardSize * 0.5f;
        long now = System.currentTimeMillis();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            double angle = rng.nextDouble(0, TWO_PI);
            float speed = (float) rng.nextDouble(0.4, 0.9);
            int life = rng.nextInt(250, 450);
            addParticle(
                    originX, originY,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    0xFFD700, 1, now, life
            );
        }
    }

    private void spawnFinishParticles(int cardX, int cardY, int cardSize, int slotIndex, ItemStack stack) {
        float originX = this.leftPos + cardX + cardSize * 0.5f;
        float originY = this.topPos + cardY + cardSize * 0.5f;
        long now = System.currentTimeMillis();

        Rarity rarity = stack.getRarity();
        int baseColor = switch (rarity) {
            case EPIC -> 0xFF3DF0;      // Magenta
            case RARE -> 0x00E5FF;      // Cyan
            case UNCOMMON -> 0xFFD700;  // Gold
            case COMMON -> 0xFFFFFF;    // White
        };

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = rarity == Rarity.EPIC ? 12 : (rarity == Rarity.RARE ? 8 : 5);
        float angleStep = TWO_PI / count;

        for (int i = 0; i < count; i++) {
            double angle = angleStep * i + rng.nextDouble(-0.3, 0.3);
            float speed = (float) rng.nextDouble(0.6, 1.3);
            int symIdx = rng.nextInt(PARTICLE_SYMBOLS.length);
            int color = rng.nextBoolean() ? baseColor : 0xFFD700;
            int life = rng.nextInt(350, 650);
            addParticle(
                    originX, originY,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    color, symIdx, now, life
            );
        }

        if (this.minecraft != null) {
            float pitch = 1.2f + (slotIndex % 6) * 0.1f;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch));
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
        long now = System.currentTimeMillis();
        if (hoveredMarketSlot >= 0) {
            long start = flipStartTime[hoveredMarketSlot];
            boolean isFlipping = start > 0 && now >= start && now < start + FLIP_DURATION_MS;
            if (isFlipping) {
                return;
            }

            boolean isRevealed = ((syncedRevealedMask & (1 << hoveredMarketSlot)) != 0) || localRevealedCards.contains(hoveredMarketSlot);
            if (!isRevealed || (start > 0 && now < start)) {
                extractor.setTooltipForNextFrame(this.font, UNREVEALED_TOOLTIP, Optional.empty(), mouseX, mouseY);
                return;
            }

            boolean isPurchased = (syncedPurchasedMask & (1 << hoveredMarketSlot)) != 0;
            if (isPurchased) {
                extractor.setTooltipForNextFrame(this.font, SOLD_TOOLTIP, Optional.empty(), mouseX, mouseY);
                return;
            }
        }

        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            renderSlotItemTooltip(extractor, mouseX, mouseY);
            return;
        }

        super.extractTooltip(extractor, mouseX, mouseY);
    }

    private void renderSlotItemTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int slotIdx = this.hoveredSlot.index;
        long now = System.currentTimeMillis();
        long start = (slotIdx >= 0 && slotIdx < VBlackMarketScreenHandler.MARKET_SLOT_COUNT) ? flipStartTime[slotIdx] : 0;
        boolean isFlipping = start > 0 && now >= start && now < start + FLIP_DURATION_MS;

        boolean isRevealed = (slotIdx < VBlackMarketScreenHandler.MARKET_SLOT_COUNT)
                && (((syncedRevealedMask & (1 << slotIdx)) != 0) || localRevealedCards.contains(slotIdx));
        if (slotIdx < VBlackMarketScreenHandler.MARKET_SLOT_COUNT && (!isRevealed || isFlipping)) {
            return;
        }

        ItemStack stack = this.hoveredSlot.getItem();
        List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(stack));
        long buyPrice = VCoinsPricing.getPrice(stack);

        if (slotIdx < VBlackMarketScreenHandler.MARKET_SLOT_COUNT) {
            buildMarketItemTooltip(tooltip, stack, buyPrice);
        } else {
            buildInventoryItemTooltip(tooltip, stack, buyPrice);
        }

        extractor.setTooltipForNextFrame(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
    }

    private void buildMarketItemTooltip(List<Component> tooltip, ItemStack stack, long buyPrice) {
        tooltip.add(Component.empty());
        tooltip.add(MERCHANT_TAG);
        if (buyPrice > 0) {
            tooltip.add(Component.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
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
        double mouseX = click.x();
        double mouseY = click.y();

        int slot = getMarketSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            long now = System.currentTimeMillis();
            long start = flipStartTime[slot];
            boolean isRevealed = ((syncedRevealedMask & (1 << slot)) != 0) || localRevealedCards.contains(slot);
            boolean isPurchased = (syncedPurchasedMask & (1 << slot)) != 0;

            // If already purchased or currently flipping, ignore click
            if (isPurchased || (isRevealed && start > 0 && now >= start && now < start + FLIP_DURATION_MS)) {
                return true;
            }

            if (!isRevealed) {
                // Click to Flip Card (Lật lá bài!)
                localRevealedCards.add(slot);
                flipStartTime[slot] = now;
                hasSpawnedStartParticles[slot] = false;
                hasSpawnedFinishParticles[slot] = false;

                ClientPlayNetworking.send(new BlackMarketRevealPayload(slot));

                if (this.minecraft != null) {
                    float pitch = 0.9f + (slot % 8) * 0.1f;
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3f));
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.1f));
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f));
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch));
                }
                updateRevealAllButtonState();
                return true;
            }

            // Already revealed -> Purchase card item
            if (click.button() == InputConstants.MOUSE_BUTTON_LEFT || click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                boolean buyStack = click.button() == InputConstants.MOUSE_BUTTON_RIGHT || click.hasShiftDown();

                Slot s = this.menu.slots.get(slot);
                if (s != null && s.hasItem()) {
                    ItemStack stack = s.getItem();
                    long unitPrice = VCoinsPricing.getPrice(stack);
                    int amount = buyStack ? stack.getMaxStackSize() : 1;
                    long totalCost = safeMultiply(unitPrice, amount);

                    if (purchaseConfirm.checkOrArm(slot, buyStack, stack, totalCost, this.minecraft)) {
                        if (this.minecraft != null) {
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                        }
                        ClientPlayNetworking.send(new BlackMarketBuyPayload(slot, buyStack));
                    }
                }
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE || event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            if (purchaseConfirm.handleKeyPress((slot, buyStack) -> {
                ClientPlayNetworking.send(new BlackMarketBuyPayload(slot, buyStack));
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                }
            })) {
                return true;
            }
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
