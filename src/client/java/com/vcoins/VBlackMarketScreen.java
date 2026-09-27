package com.vcoins;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class VBlackMarketScreen extends VeloriaContainerScreen<VBlackMarketScreenHandler> {
    private final VeloriaMerchantPreview merchantPreview = new VeloriaMerchantPreview();
    private VCoinsPurchaseConfirm purchaseConfirm = new VCoinsPurchaseConfirm();

    // Portrait textures and display dimensions share the same 4:5 aspect ratio.
    private static final Identifier CARD_BACK  = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_back.png");
    private static final Identifier CARD_FRONT = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_front.png");
    private static final int BM_CARD_W       = 36;
    private static final int BM_CARD_H       = 45;
    private static final int BM_CARD_SPACING = 43;
    private static final int BM_CARD_START_X = (236 - (4 * BM_CARD_SPACING + BM_CARD_W)) / 2;
    private static final int BM_CARD_Y       = 58;
    private static final long BM_FLIP_MS     = 480L;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

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
    public static int getSyncedLifetimeFlipCount() { return syncedLifetimeFlipCount; }

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
            }
        }
    }

    public void onSyncReceived(int newResetSequence, long newDay) {
        updateWidgets();
    }

    // Interactive Buttons
    private Button backToShopButton;
    private Button bmRevealAllButton;
    private Button bmResetButton;
    private Button verifyToggleButton;
    private Button shopTabButton;
    private Button blackMarketTabButton;
    private Button duplicateTabButton;

    // Flip Animations & Particle State
    private final Set<Integer> localRevealedCards = new HashSet<>();
    private final long[] flipStartTime = new long[5];
    private final boolean[] hasSpawnedStartParticles = new boolean[5];
    private final boolean[] hasSpawnedMidParticles = new boolean[5];
    private final boolean[] hasSpawnedFinishParticles = new boolean[5];

    // Particle pool (expanded for legendary god pull fireworks)
    private static final int MAX_PARTICLES = 256;
    private static final String[] PARTICLE_SYMBOLS = {"✦", "✧", "⋆", "★", "⚡", "☼", "⚜", "👑", "•", "ᛟ", "ᚱ", "◇"};
    private long godPullStartTime = 0L;
    private int godPullCardIndex = -1;
    private int legendSoundStage;
    private static final long GOD_CINEMATIC_DURATION_MS = 4200L;
    private final float[] partX = new float[MAX_PARTICLES];
    private final float[] partY = new float[MAX_PARTICLES];
    private final float[] partVx = new float[MAX_PARTICLES];
    private final float[] partVy = new float[MAX_PARTICLES];
    private final int[] partColor = new int[MAX_PARTICLES];
    private final byte[] partSymbol = new byte[MAX_PARTICLES];
    private final long[] partSpawn = new long[MAX_PARTICLES];
    private final int[] partLife = new int[MAX_PARTICLES];
    private int particleCount = 0;

    public VBlackMarketScreen(VBlackMarketScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 236, 250);
        this.inventoryLabelX = 1000;
        this.titleLabelX = 1000;
    }

    @Override
    protected void init() {
        super.init();

        // Nút quay lại Mua hàng (Shop)
        this.backToShopButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.black_market.back_shop"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenShopPayload());
                })
                .bounds(this.leftPos + 14, this.topPos + 22, 48, 14)
                .build());

        // Nút Đặt lại Chợ Đen tích lũy
        this.bmResetButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.black_market.reset_count", syncedBankedResets),
                button -> useBankedReset())
                .bounds(this.leftPos + 76, this.topPos + 22, 70, 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("vcoins.black_market.reset_hint", syncedBankedResets)))
                .build());

        // Nút Lật tất cả
        this.bmRevealAllButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.black_market.reveal_all"),
                button -> revealAllCards())
                .bounds(this.leftPos + 160, this.topPos + 22, 62, 14)
                .build());

        // Công tắc bật/tắt xác minh giao dịch
        this.verifyToggleButton = this.addRenderableWidget(VeloriaButton.create(
                VCoinsPurchaseConfirm.getCompactToggleLabel(),
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
                .bounds(this.leftPos + 206, this.topPos + 151, 16, 12)
                .tooltip(VCoinsPurchaseConfirm.getToggleTooltip())
                .build());

        // Toolbar: Cửa hàng | Chợ trời | Nhân bản
        this.shopTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.title"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenShopPayload());
                })
                .bounds(this.leftPos + 14, this.topPos + 132, 64, 16)
                .build());

        this.blackMarketTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.tab.black_market"),
                button -> {})
                .bounds(this.leftPos + 82, this.topPos + 132, 72, 16)
                .build());

        this.duplicateTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.duplicate.open"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenDuplicatePayload());
                })
                .bounds(this.leftPos + 158, this.topPos + 132, 64, 16)
                .build());

        updateWidgets();
    }

    private void updateVerifyToggleButton() {
        if (this.verifyToggleButton != null) {
            this.verifyToggleButton.setMessage(VCoinsPurchaseConfirm.getCompactToggleLabel());
            this.verifyToggleButton.setTooltip(VCoinsPurchaseConfirm.getToggleTooltip());
        }
    }

    private void updateWidgets() {
        if (this.bmRevealAllButton != null) {
            int count = 0;
            for (int i = 0; i < 5; i++) {
                if (((syncedRevealedMask & (1 << i)) != 0) || localRevealedCards.contains(i)) {
                    count++;
                }
            }
            this.bmRevealAllButton.active = (count < 5);
        }
        if (this.bmResetButton != null) {
            this.bmResetButton.visible = (syncedBankedResets > 0);
            if (this.bmResetButton.visible) {
                this.bmResetButton.setMessage(Component.translatable("vcoins.black_market.reset_count", syncedBankedResets));
                this.bmResetButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("vcoins.black_market.reset_hint", syncedBankedResets)));
            }
        }
    }

    private void revealAllCards() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            boolean isRevealed = ((syncedRevealedMask & (1 << i)) != 0) || localRevealedCards.contains(i);
            if (!isRevealed) {
                localRevealedCards.add(i);
                flipStartTime[i] = now + i * 75L;
                hasSpawnedStartParticles[i] = false;
                hasSpawnedMidParticles[i] = false;
                hasSpawnedFinishParticles[i] = false;
            }
        }
        ClientPlayNetworking.send(new BlackMarketRevealPayload(-1));
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.15f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35f));
        }
        updateWidgets();
    }

    private void useBankedReset() {
        purchaseConfirm = new VCoinsPurchaseConfirm();
        ClientPlayNetworking.send(new BlackMarketResetActionPayload());
        localRevealedCards.clear();
        Arrays.fill(flipStartTime, 0L);
        Arrays.fill(hasSpawnedStartParticles, false);
        Arrays.fill(hasSpawnedMidParticles, false);
        Arrays.fill(hasSpawnedFinishParticles, false);
        godPullStartTime = 0L;
        godPullCardIndex = -1;
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f));
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (legendPresentationActive()) {
            godPullStartTime = 0L; // Consume dismissal so a hidden card cannot be purchased.
            return true;
        }
        int card = getBlackMarketCardAt(click.x(), click.y());
        if (card >= 0) {
            handleBlackMarketCardClick(card, click);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void handleBlackMarketCardClick(int card, net.minecraft.client.input.MouseButtonEvent click) {
        long now = System.currentTimeMillis();
        boolean isRevealed = ((syncedRevealedMask & (1 << card)) != 0) || localRevealedCards.contains(card);
        boolean isPurchased = (syncedPurchasedMask & (1 << card)) != 0;
        long start = flipStartTime[card];

        if (isPurchased || (!VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now < start + BM_FLIP_MS)) {
            return;
        }

        if (!isRevealed) {
            localRevealedCards.add(card);
            flipStartTime[card] = now;
            hasSpawnedStartParticles[card] = false;
            hasSpawnedMidParticles[card] = false;
            hasSpawnedFinishParticles[card] = false;

            ClientPlayNetworking.send(new BlackMarketRevealPayload(card));
            if (this.minecraft != null) {
                float pitch = 0.9f + (card % 8) * 0.1f;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch));
            }
            updateWidgets();
            return;
        }

        // Revealed: trigger purchase
        boolean buyStack = click.button() == InputConstants.MOUSE_BUTTON_RIGHT || click.hasShiftDown();
        if (card >= syncedCards.size()) return;
        ItemStack item = syncedCards.get(card);
        long price = VBlackMarket.getDiscountedPrice(item, syncedEpochDay, syncedResetSequence);
        // Black Market sells one item per card, even for a right click.
        if (!purchaseConfirm.checkOrArm(card, false, item, price, this.minecraft)) return;
        ClientPlayNetworking.send(new BlackMarketBuyPayload(card, buyStack));
    }

    private int getBlackMarketCardAt(double mouseX, double mouseY) {
        int cardY = this.topPos + BM_CARD_Y;
        if (mouseY < cardY || mouseY >= cardY + BM_CARD_H) {
            return -1;
        }
        for (int i = 0; i < 5; i++) {
            int cardX = this.leftPos + BM_CARD_START_X + i * BM_CARD_SPACING;
            if (mouseX >= cardX && mouseX < cardX + BM_CARD_W) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        renderParticles(extractor);
        renderGodPullCinematic(extractor);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractBackground(extractor, mouseX, mouseY, delta);
        merchantPreview.drawBehindMenu(extractor, this.leftPos, this.topPos, this.height, mouseX, mouseY);
        InventoryTextures.panel(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);

        // Header Title
        extractor.centeredText(this.font, Component.translatable("vcoins.black_market.title"), this.leftPos + this.imageWidth / 2, this.topPos + 8, 0xFFFFAA00);

        renderBlackMarketContent(extractor, mouseX, mouseY, delta);

        // Player Inventory Slots Grid
        InventoryTextures.slots(extractor, this.leftPos + VBlackMarketScreenHandler.PLAYER_X, this.topPos + VBlackMarketScreenHandler.PLAYER_INVENTORY_Y, 9, 3);
        InventoryTextures.slots(extractor, this.leftPos + VBlackMarketScreenHandler.PLAYER_X, this.topPos + VBlackMarketScreenHandler.PLAYER_HOTBAR_Y, 9, 1);

        // Toolbar decorative tray
        extractor.fill(this.leftPos + 12, this.topPos + 130, this.leftPos + 224, this.topPos + 150, 0x55080310);
        extractor.fill(this.leftPos + 12, this.topPos + 130, this.leftPos + 224, this.topPos + 131, 0x22D4AF37);
        extractor.fill(this.leftPos + 12, this.topPos + 149, this.leftPos + 224, this.topPos + 150, 0x22D4AF37);

        // Highlight line under active mode button (Black Market - orange)
        extractor.fill(this.leftPos + 82, this.topPos + 147, this.leftPos + 154, this.topPos + 148, 0xFFFFAA00);

        // Player Inventory label
        extractor.text(this.font, Component.translatable("vcoins.inventory"),
                this.leftPos + VBlackMarketScreenHandler.PLAYER_X, this.topPos + 153, 0xFFC8A96E, false);

        VeloriaMenuEffects.draw(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    private void renderBlackMarketContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        long elapsedSec = (System.currentTimeMillis() - lastSyncTimeMs) / 1000L;
        long remainingSec = Math.max(0L, baseSecondsRemaining - elapsedSec);
        long hours = remainingSec / 3600L;
        long mins  = (remainingSec % 3600L) / 60L;
        long secs  = remainingSec % 60L;
        String timerStr = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, mins, secs);

        long balance = this.minecraft != null && this.minecraft.player != null
                ? VCoinsState.getCoins(this.minecraft.player.getUUID()) : 0L;

        extractor.fill(this.leftPos + 14, this.topPos + 39, this.leftPos + 222, this.topPos + 52, 0x88080310);
        extractor.text(this.font, Component.literal("§b" + timerStr), this.leftPos + 18, this.topPos + 41, 0xFF55FFFF, true);
        int pityRemaining = 300 - (syncedLifetimeFlipCount % 300);
        extractor.centeredText(this.font, Component.translatable("vcoins.black_market.pity_remaining", pityRemaining),
                this.leftPos + this.imageWidth / 2, this.topPos + 41, 0xFFAAAAAA);
        String balStr = formatCompactNumber(balance);
        extractor.text(this.font, Component.literal(balStr), this.leftPos + 218 - this.font.width(balStr),
                this.topPos + 41, 0xFFFFFF55, true);

        if (purchaseConfirm.isArmed()) {
            purchaseConfirm.renderBanner(extractor, this.font, this.leftPos + 14, this.topPos + 39, 208, 13);
        }

        // 5 Cards Rendering
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            int cardBaseX = this.leftPos + BM_CARD_START_X + i * BM_CARD_SPACING;
            int cardBaseY = this.topPos + BM_CARD_Y;
            boolean isRevealed = ((syncedRevealedMask & (1 << i)) != 0) || localRevealedCards.contains(i);
            boolean isPurchased = (syncedPurchasedMask & (1 << i)) != 0;

            ItemStack stack = (i < syncedCards.size()) ? syncedCards.get(i) : ItemStack.EMPTY;
            if (stack.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
                List<ItemStack> fallback = VBlackMarket.getItemsForPlayer(this.minecraft.player.getUUID());
                if (i < fallback.size()) stack = fallback.get(i);
            }
            boolean isGod = !stack.isEmpty() && VBlackMarket.isRomanGodItem(stack);

            boolean isHovered = mouseX >= cardBaseX && mouseX < cardBaseX + BM_CARD_W
                    && mouseY >= cardBaseY && mouseY < cardBaseY + BM_CARD_H;

            long start = flipStartTime[i];
            boolean isFlipping = !VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now >= start && now < start + BM_FLIP_MS;

            if (isFlipping) {
                float p = Math.clamp((float) (now - start) / BM_FLIP_MS, 0.0f, 1.0f);
                float angle = p * (float) Math.PI;
                float scaleX = (float) Math.abs(Math.cos(angle));
                boolean isBackSide = angle < (Math.PI * 0.5);

                float zPop = 1.0f + 0.10f * (float) Math.sin(angle);
                float cx = cardBaseX + BM_CARD_W * 0.5f;
                float cy = cardBaseY + BM_CARD_H * 0.5f;
                int halfW = BM_CARD_W / 2;
                int halfH = BM_CARD_H / 2;

                if (!hasSpawnedStartParticles[i]) {
                    hasSpawnedStartParticles[i] = true;
                    if (!isGod) spawnCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2);
                }

                if (p >= 0.5f && !hasSpawnedMidParticles[i]) {
                    hasSpawnedMidParticles[i] = true;
                    if (isGod) {
                        godPullStartTime = System.currentTimeMillis();
                        godPullCardIndex = i;
                        triggerGodPullCelebration(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                    } else {
                        spawnCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2);
                        if (this.minecraft != null) {
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.25f));
                        }
                    }
                }

                extractor.pose().pushMatrix();
                extractor.pose().translate(cx, cy);
                extractor.pose().scale(Math.max(0.015f, scaleX) * zPop, zPop);

                if (isBackSide) {
                    drawMysteryCard(extractor, stack, -halfW, -halfH, now, i, isHovered);

                    int shadeAlpha = (int) ((1.0f - scaleX) * 70);
                    if (shadeAlpha > 0) {
                        extractor.fill(-halfW, -halfH, halfW, halfH, (shadeAlpha << 24));
                    }
                } else {
                    float revealT = Math.clamp((p - 0.5f) / 0.5f, 0.0f, 1.0f);

                    if (isGod) {
                        extractor.fill(-halfW - 2, -halfH - 2, halfW + 2, halfH + 2, 0xD0FFD700);
                        extractor.fill(-halfW - 1, -halfH - 1, halfW + 1, halfH + 1, 0xFF8B0000);
                    } else {
                        extractor.fill(-halfW - 1, -halfH - 1, halfW + 1, halfH + 1, 0x88D4AF37);
                    }

                    drawCardTexture(extractor, CARD_FRONT, -halfW, -halfH, BM_CARD_W, BM_CARD_H);
                    if (isGod) drawLegendFrame(extractor, -halfW, -halfH, false);

                    if (!stack.isEmpty()) {
                        extractor.item(stack, -8, -halfH + (BM_CARD_H - 16) / 2);
                        extractor.itemDecorations(this.font, stack, -8, -halfH + (BM_CARD_H - 16) / 2);
                    }

                    if (!isGod && revealT <= 0.65f) {
                        float gleamT = revealT / 0.65f;
                        float gleamIntensity = (float) Math.sin(gleamT * Math.PI);
                        int gleamAlpha = (int) (gleamIntensity * 130);
                        if (gleamAlpha > 0) {
                            extractor.fill(-halfW, -halfH, halfW, halfH, (gleamAlpha << 24) | 0xFFFDF0);
                        }
                    }
                }

                extractor.pose().popMatrix();

                if (scaleX < 0.12f) {
                    int edgeAlpha = (int) (240 * (1.0f - scaleX / 0.12f));
                    int edgeColor = (edgeAlpha << 24) | (isGod ? 0xFFFFD700 : 0xFFE082);
                    int top = (int) (cy - halfH);
                    int bottom = (int) (cy + halfH);
                    extractor.fill((int) (cx - 1), top, (int) (cx + 1), bottom, edgeColor);
                }

                if (p >= 0.95f && !hasSpawnedFinishParticles[i]) {
                    hasSpawnedFinishParticles[i] = true;
                    if (isGod) {
                        triggerGodFinishCelebration(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                    } else {
                        spawnCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2);
                    }
                }

            } else if (!isRevealed || (!VCoinsPurchaseConfirm.isReducedMotion() && start > now)) {
                // Reveal-all schedules staggered starts. Server state may already be revealed,
                // but a queued card must retain its back until its own animation begins.
                drawMysteryCard(extractor, stack, cardBaseX, cardBaseY, now, i, isHovered);

                extractor.centeredText(this.font, Component.literal("§7?"), cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 3, 0xFFAAAAAA);

            } else {
                // ─── FULLY REVEALED CARD ───
                if (!isGod) {
                    extractor.fill(cardBaseX - 1, cardBaseY - 1, cardBaseX + BM_CARD_W + 1, cardBaseY,               0x88D4AF37);
                    extractor.fill(cardBaseX - 1, cardBaseY + BM_CARD_H, cardBaseX + BM_CARD_W + 1, cardBaseY + BM_CARD_H + 1, 0x44D4AF37);
                    extractor.fill(cardBaseX - 1, cardBaseY,             cardBaseX,                 cardBaseY + BM_CARD_H, 0x44D4AF37);
                    extractor.fill(cardBaseX + BM_CARD_W, cardBaseY,     cardBaseX + BM_CARD_W + 1, cardBaseY + BM_CARD_H, 0x44D4AF37);
                }

                drawCardTexture(extractor, CARD_FRONT, cardBaseX, cardBaseY, BM_CARD_W, BM_CARD_H);
                if (isGod) drawLegendFrame(extractor, cardBaseX, cardBaseY, false);
                if (!isPurchased) drawCardAura(extractor, stack, cardBaseX, cardBaseY, now, i, isHovered, false);

                if (!stack.isEmpty()) {
                    int itemX = cardBaseX + (BM_CARD_W - 16) / 2;
                    int itemY = cardBaseY + (BM_CARD_H - 16) / 2;
                    extractor.item(stack, itemX, itemY);
                    extractor.itemDecorations(this.font, stack, itemX, itemY);
                }

                if (isHovered && !isPurchased) {
                    extractor.fill(cardBaseX, cardBaseY, cardBaseX + BM_CARD_W, cardBaseY + BM_CARD_H, 0x28FFFFFF);
                }

                if (isPurchased) {
                    extractor.fill(cardBaseX, cardBaseY, cardBaseX + BM_CARD_W, cardBaseY + BM_CARD_H, 0x88000000);
                    extractor.centeredText(this.font, Component.translatable("vcoins.black_market.purchased_short").withStyle(ChatFormatting.GREEN), cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 4, 0xFF55FF55);
                } else {
                    int disc  = VBlackMarket.getDiscountPercent(stack, syncedEpochDay, syncedResetSequence);
                    long price = VBlackMarket.getDiscountedPrice(stack, syncedEpochDay, syncedResetSequence);

                    if (isGod) {
                        long nowMs = System.currentTimeMillis();
                        int badgeW = 32;
                        int badgeH = 9;
                        int badgeX = cardBaseX + (BM_CARD_W - badgeW) / 2;
                        int badgeY = cardBaseY + BM_CARD_H + 2;
                        extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xEE140206);
                        extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, 0xFFFFD700);
                        extractor.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, 0xFFFFD700);
                        int textColor = 0xFFFFE6A3;
                        extractor.centeredText(this.font, Component.translatable("vcoins.black_market.god_badge"), cardBaseX + BM_CARD_W / 2, badgeY + 1, textColor);
                        extractor.centeredText(this.font, Component.literal("§6"  + formatCompactNumber(price)),  cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 13, 0xFFFFAA00);
                    } else {
                        extractor.centeredText(this.font, Component.literal("§a-" + disc + "%"),                  cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 3,  0xFF55FF55);
                        extractor.centeredText(this.font, Component.literal("§e"  + formatCompactNumber(price)),  cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 13, 0xFFFFFF55);
                    }
                }
            }
        }
    }

    // Roman relics take precedence over the item's vanilla rarity.
    // Exclusive currently maps to RARE; the data model has no separate exclusive tier.
    private static int mysteryCardColor(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (VBlackMarket.isRomanGodItem(stack)) return 0xFFD700;
        return switch (stack.getRarity()) {
            case EPIC -> 0xBC65FF;
            case RARE -> 0x35E8FF;
            default -> 0;
        };
    }

    private void drawMysteryCard(GuiGraphicsExtractor g, ItemStack stack, int x, int y,
                                 long now, int index, boolean hovered) {
        drawCardTexture(g, CARD_BACK, x, y, BM_CARD_W, BM_CARD_H);
        if (VBlackMarket.isRomanGodItem(stack)) drawLegendFrame(g, x, y, true);
        drawCardAura(g, stack, x, y, now, index, hovered, true);
    }

    /** Static tier identity: a bounded number of quads, including with reduced motion. */
    private static void drawLegendFrame(GuiGraphicsExtractor g, int x, int y, boolean hidden) {
        g.fill(x + 2, y + 2, x + BM_CARD_W - 2, y + BM_CARD_H - 2, 0xFF260F25);
        g.fill(x + 5, y + 5, x + BM_CARD_W - 5, y + BM_CARD_H - 5, 0xFF110D1B);
        drawCardOutline(g, x - 1, y - 1, BM_CARD_W + 2, BM_CARD_H + 2, 0xFF9F6124);
        drawCardOutline(g, x, y, BM_CARD_W, BM_CARD_H, 0xFFFFD56D);
        drawCardOutline(g, x + 2, y + 2, BM_CARD_W - 4, BM_CARD_H - 4, 0xFFB38745);
        // Gold corner brackets make the silhouette distinct from ordinary thin borders.
        for (int side = 0; side < 2; side++) {
            int cx = side == 0 ? x : x + BM_CARD_W - 7;
            g.fill(cx, y, cx + 7, y + 3, 0xFFFFE7A2);
            g.fill(cx, y + BM_CARD_H - 3, cx + 7, y + BM_CARD_H, 0xFFFFE7A2);
        }
        // Small crown above the item; larger central seal while the card is hidden.
        int cx = x + BM_CARD_W / 2;
        int cy = hidden ? y + BM_CARD_H / 2 - 2 : y + 7;
        g.fill(cx - 4, cy, cx + 5, cy + 3, 0xFFFFD56D);
        g.fill(cx - 5, cy - 2, cx - 3, cy + 1, 0xFFFFE7A2);
        g.fill(cx - 1, cy - 3, cx + 2, cy + 1, 0xFFFFE7A2);
        g.fill(cx + 4, cy - 2, cx + 6, cy + 1, 0xFFFFE7A2);
        g.fill(cx - 3, cy + 4, cx + 4, cy + 5, 0xFFB38745);
    }

    private void drawCardAura(GuiGraphicsExtractor g, ItemStack stack, int x, int y,
                              long now, int index, boolean hovered, boolean hidden) {
        int color = mysteryCardColor(stack);
        if (color == 0) {
            if (hovered) drawCardOutline(g, x - 1, y - 1, BM_CARD_W + 2, BM_CARD_H + 2, 0xCCD4AF37);
            return;
        }
        boolean legend = VBlackMarket.isRomanGodItem(stack);
        if (legendPresentationActive()) {
            drawCardOutline(g, x, y, BM_CARD_W, BM_CARD_H, 0xCC000000 | color);
            return;
        }
        boolean still = VCoinsPurchaseConfirm.isReducedMotion();
        double time = still ? index * 0.7 : (now % 120000L) / 1000.0 + index * 0.7;
        float breath = still ? 0.65f : (float) (0.65 + 0.35 * Math.sin(time * 2.2));
        float power = (hovered ? 1f : 0.78f) * (hidden ? 1f : 0.7f);
        int highlight = legend ? 0xFFF3C5 : color == 0xBC65FF ? 0xEED8FF : 0xD8FFFF;
        // Compact bloom stays inside each card's gutter and never washes out its item.
        for (int layer = 3; layer >= 1; layer--) {
            int alpha = (int) ((4 - layer) * (legend ? 34 : 26) * breath * power);
            drawCardOutline(g, x - layer, y - layer, BM_CARD_W + 2 * layer,
                    BM_CARD_H + 2 * layer, (alpha << 24) | color);
        }
        drawCardOutline(g, x, y, BM_CARD_W, BM_CARD_H, ((int) (220 * power) << 24) | color);
        // Internal edge illumination fades toward the centre.
        for (int inset = 1; inset <= 4; inset++) {
            int alpha = (int) ((5 - inset) * 12 * breath * power);
            drawCardOutline(g, x + inset, y + inset, BM_CARD_W - 2 * inset,
                    BM_CARD_H - 2 * inset, (alpha << 24) | color);
        }
        int perimeter = 2 * (BM_CARD_W - 1 + BM_CARD_H - 1);
        int head = (int) (time * (legend ? 43 : 29));
        int comets = legend ? 3 : 2;
        for (int comet = 0; comet < comets; comet++) {
            for (int tail = 13; tail >= 0; tail--) {
                int step = Math.floorMod(head + comet * perimeter / comets - tail, perimeter);
                int w = BM_CARD_W - 1, h = BM_CARD_H - 1;
                int dx, dy;
                if (step < w) { dx = step; dy = 0; }
                else if (step < w + h) { dx = w; dy = step - w; }
                else if (step < 2 * w + h) { dx = 2 * w + h - step; dy = h; }
                else { dx = 0; dy = perimeter - step; }
                int alpha = (int) (245 * (1 - tail / 14f) * power);
                g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1,
                        (alpha << 24) | (tail < 3 ? highlight : color));
            }
        }
        // Rising motes hug the two edges; every trajectory depends on time, not FPS.
        for (int i = 0; i < (legend ? 10 : 6); i++) {
            float t = (float) ((time * 0.32 + i * 0.173) % 1);
            int px = x + (i % 2 == 0 ? 2 : BM_CARD_W - 3);
            int py = y + BM_CARD_H - 3 - (int) (t * (BM_CARD_H - 6));
            int alpha = (int) (Math.sin(t * Math.PI) * 190 * power);
            g.fill(px, py, px + 1, py + 2, (alpha << 24) | highlight);
        }
        if (hidden) {
            // A subtle diagonal sheen travels across the back, bounded by its frame.
            float sweep = (float) ((time * 0.22) % 1) * (BM_CARD_H + BM_CARD_W + 16) - 8;
            for (int row = 4; row < BM_CARD_H - 4; row++) {
                int sx = Math.round(sweep - row);
                int left = Math.max(4, sx - 2), right = Math.min(BM_CARD_W - 4, sx + 2);
                if (right > left) g.fill(x + left, y + row, x + right, y + row + 1,
                        ((int) (32 * power) << 24) | highlight);
            }
            if (legend) {
                float radius = 8 + 1.5f * breath;
                float cx = x + BM_CARD_W / 2f, cy = y + BM_CARD_H / 2f;
                for (int i = 0; i < 12; i++) {
                    double angle = time * 0.55 + i * Math.PI / 6;
                    int px = Math.round(cx + (float) Math.cos(angle) * radius);
                    int py = Math.round(cy + (float) Math.sin(angle) * radius);
                    g.fill(px, py, px + 1, py + 1, ((int) (180 * power) << 24) | highlight);
                }
                int alpha = (int) ((130 + 90 * breath) * power);
                g.fill((int) cx - 3, (int) cy, (int) cx + 4, (int) cy + 1, (alpha << 24) | highlight);
                g.fill((int) cx, (int) cy - 3, (int) cx + 1, (int) cy + 4, (alpha << 24) | highlight);
            }
        }
    }

    private static void drawCardOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void drawCardTexture(GuiGraphicsExtractor extractor, Identifier texture, int x, int y, int w, int h) {
        extractor.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, w, h, w, h);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        int cardIndex = getBlackMarketCardAt(mouseX, mouseY);
        if (cardIndex >= 0) {
            ItemStack stack = (cardIndex < syncedCards.size()) ? syncedCards.get(cardIndex) : ItemStack.EMPTY;
            if (stack.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
                List<ItemStack> fallback = VBlackMarket.getItemsForPlayer(this.minecraft.player.getUUID());
                if (cardIndex < fallback.size()) stack = fallback.get(cardIndex);
            }
            boolean isRevealed = ((syncedRevealedMask & (1 << cardIndex)) != 0) || localRevealedCards.contains(cardIndex);
            long start = flipStartTime[cardIndex];
            long now = System.currentTimeMillis();
            boolean isFlipping = !VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now < start + BM_FLIP_MS;
            if (isFlipping) {
                return;
            }

            if (isRevealed && !stack.isEmpty()) {
                List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(stack));
                long price = VBlackMarket.getDiscountedPrice(stack, syncedEpochDay, syncedResetSequence);
                int discount = VBlackMarket.getDiscountPercent(stack, syncedEpochDay, syncedResetSequence);

                tooltip.add(Component.empty());
                if (VBlackMarket.isRomanGodItem(stack)) {
                    tooltip.add(Component.translatable("vcoins.black_market.god_tooltip").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
                if (discount > 0) {
                    tooltip.add(Component.translatable("vcoins.black_market.discount", discount).withStyle(ChatFormatting.GREEN));
                }
                tooltip.add(Component.translatable("vcoins.black_market.buy_price", formatNumber(price)).withStyle(ChatFormatting.YELLOW));
                boolean isPurchased = (syncedPurchasedMask & (1 << cardIndex)) != 0;
                if (isPurchased) {
                    tooltip.add(Component.translatable("vcoins.black_market.already_bought").withStyle(ChatFormatting.RED));
                } else {
                    tooltip.add(Component.translatable("vcoins.tooltip.buy_left").withStyle(ChatFormatting.GRAY));
                }
                extractor.setTooltipForNextFrame(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY,
                        stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
                return;
            } else if (!isRevealed) {
                extractor.setTooltipForNextFrame(this.font, Component.translatable("vcoins.black_market.card_mystery").withStyle(ChatFormatting.GOLD), mouseX, mouseY);
                return;
            }
        }

        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            ItemStack stack = this.hoveredSlot.getItem();
            extractor.setTooltipForNextFrame(this.font, this.getTooltipFromContainerItem(stack), stack.getTooltipImage(), mouseX, mouseY,
                    stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
        }
    }

    // Particle & Cinematic Methods
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
        if (VCoinsPurchaseConfirm.isReducedMotion() || particleCount == 0) {
            particleCount = 0;
            return;
        }
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

    private boolean legendPresentationActive() {
        long elapsed = System.currentTimeMillis() - godPullStartTime;
        return !VCoinsPurchaseConfirm.isReducedMotion() && godPullStartTime > 0L
                && elapsed >= 0 && elapsed < GOD_CINEMATIC_DURATION_MS;
    }

    private void renderGodPullCinematic(GuiGraphicsExtractor g) {
        if (!legendPresentationActive() || godPullCardIndex < 0 || godPullCardIndex >= syncedCards.size()) return;
        long elapsed = System.currentTimeMillis() - godPullStartTime;
        playLegendScore(elapsed);
        float entrance = Math.clamp(elapsed / 700f, 0f, 1f);
        float settle = 1 - (float) Math.pow(1 - entrance, 3);
        float departure = Math.clamp((elapsed - 3600f) / 600f, 0f, 1f);
        float presence = settle * (1 - departure * departure);
        float envelope = Math.min(1, elapsed / 180f) * (1 - departure);
        g.fill(0, 0, this.width, this.height, ((int) (envelope * 195) << 24) | 0x050711);

        // All geometry shares a viewport-aware stage; small GUI scales retain margins.
        float stage = Math.min(1f, Math.min(this.width / 360f, this.height / 280f));
        float sourceX = this.leftPos + BM_CARD_START_X + godPullCardIndex * BM_CARD_SPACING + BM_CARD_W / 2f;
        float sourceY = this.topPos + BM_CARD_Y + BM_CARD_H / 2f;
        float cx = sourceX + (this.width / 2f - sourceX) * presence;
        float cy = sourceY + (this.height / 2f - 12 * stage - sourceY) * presence;
        float impact = Math.clamp((elapsed - 700f) / 420f, 0f, 1f);
        float rotation = elapsed * 0.00023f;
        int gold = 0xE8BC64;

        // Twelve broad translucent rays cost twelve quads, independent of screen resolution.
        if (elapsed >= 700) {
            for (int ray = 0; ray < 12; ray++) {
                g.pose().pushMatrix();
                g.pose().translate(cx, cy);
                g.pose().rotate((float) (ray * Math.PI / 6 + rotation));
                int length = Math.round((80 + 35 * impact) * stage);
                int alpha = (int) (envelope * (1 - impact * 0.55f) * (ray % 2 == 0 ? 35 : 20));
                g.fill(-2, -length, 2, -28, (alpha << 24) | 0xFFD983);
                g.pose().popMatrix();
            }
        }

        // Fixed-budget orbital points; never rasterize a halo pixel by pixel.
        for (int ring = 0; ring < 2; ring++) {
            float radius = (64 + ring * 15) * stage * presence;
            drawLegendRing(g, cx, cy, radius, (int) (envelope * 100), ring == 0 ? gold : 0x9D89CB);
            for (int spoke = 0; spoke < 12; spoke++) {
                double angle = spoke * Math.PI / 6 + rotation * (ring == 0 ? 1 : -0.65);
                float inner = radius - (spoke % 3 == 0 ? 5 : 2) * stage;
                drawLegendRay(g, cx, cy, angle, inner, radius, (int) (envelope * 190), gold);
            }
        }
        // Inward sparks resolve into a single outward burst, then drifting embers.
        for (int i = 0; i < 24; i++) {
            double angle = i * 2.399963 + rotation * 0.3;
            float burst = Math.clamp((elapsed - 700f - (i % 8) * 18) / 1700f, 0f, 1f);
            float radius = elapsed < 700 ? 95 * (1 - elapsed / 700f) : 15 + 100 * (1 - (1 - burst) * (1 - burst));
            radius *= stage;
            int alpha = (int) (envelope * (elapsed < 700 ? 160 : 220 * (1 - burst)));
            if (alpha <= 0) continue;
            int px = Math.round(cx + (float) Math.cos(angle) * radius);
            int py = Math.round(cy + (float) Math.sin(angle) * radius * 0.8f - burst * burst * 15 * stage);
            int color = (alpha << 24) | (i % 5 == 0 ? 0xE3D5FF : 0xFFE7A4);
            g.fill(px, py, px + 1, py + 1, color);
            if (i % 7 == 0) {
                g.fill(px - 2, py, px + 3, py + 1, color);
                g.fill(px, py - 2, px + 1, py + 3, color);
            }
        }
        float wave = Math.clamp((elapsed - 700f) / 900f, 0f, 1f);
        if (wave > 0 && wave < 1) drawLegendRing(g, cx, cy, (25 + wave * 95) * stage,
                (int) (200 * (1 - wave) * envelope), 0xFFF2C6);

        float punch = elapsed >= 700 && elapsed < 1120 ? (float) Math.sin(impact * Math.PI) * 0.22f : 0;
        float scale = 1 + presence * ((2.0f + punch) * stage);
        // A narrow gold flare at impact; no full-screen white flash.
        if (elapsed >= 700 && elapsed < 1120) {
            int flare = (int) (140 * (1 - impact));
            int reach = Math.round((30 + impact * 100) * stage);
            g.fill((int) cx - reach, (int) cy - 1, (int) cx + reach, (int) cy + 1, (flare << 24) | 0xFFF0C2);
        }
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        drawCardOutline(g, -BM_CARD_W / 2 - 1, -BM_CARD_H / 2 - 1, BM_CARD_W + 2, BM_CARD_H + 2, 0xFFE8BC64);
        boolean sealed = elapsed < 700;
        drawCardTexture(g, sealed ? CARD_BACK : CARD_FRONT, -BM_CARD_W / 2, -BM_CARD_H / 2, BM_CARD_W, BM_CARD_H);
        drawLegendFrame(g, -BM_CARD_W / 2, -BM_CARD_H / 2, sealed);
        ItemStack relic = syncedCards.get(godPullCardIndex);
        if (!sealed) g.item(relic, -8, -8);
        g.pose().popMatrix();

        // Letter-spaced title and a separate item name leave the reveal unobscured.
        float textFade = Math.clamp((elapsed - 850f) / 350f, 0f, 1f) * (1 - departure);
        int textAlpha = (int) (textFade * 255);
        if (textAlpha > 4) {
            int titleY = Math.round(this.height / 2f - 105 * stage);
            g.centeredText(this.font, Component.translatable("vcoins.roman.reveal.title").withStyle(ChatFormatting.BOLD),
                    this.width / 2, titleY, (textAlpha << 24) | 0xFFE0A0);
            int nameY = Math.round(this.height / 2f + 76 * stage);
            String fullName = relic.getHoverName().getString();
            int maxNameWidth = Math.min(260, this.width - 24);
            String name = this.font.width(fullName) > maxNameWidth
                    ? this.font.plainSubstrByWidth(fullName, maxNameWidth - this.font.width("…")) + "…" : fullName;
            g.centeredText(this.font, Component.literal(name), this.width / 2, nameY, (textAlpha << 24) | 0xFFF2D7);
            g.centeredText(this.font, Component.translatable("vcoins.roman.reveal.dismiss"),
                    this.width / 2, nameY + 15, ((int) (textFade * 150) << 24) | 0xB9B4C7);
        }
    }

    private static void drawLegendRay(GuiGraphicsExtractor g, float cx, float cy, double angle,
                                       float inner, float outer, int alpha, int rgb) {
        if (alpha <= 0) return;
        // One small marker replaces a ray made of overlapping per-pixel quads.
        float radius = (inner + outer) * 0.5f;
        int x = Math.round(cx + (float) Math.cos(angle) * radius);
        int y = Math.round(cy + (float) Math.sin(angle) * radius);
        g.fill(x, y, x + 2, y + 2, (alpha << 24) | rgb);
    }

    private static final int LEGEND_RING_POINTS = 48;
    private static final float[] LEGEND_RING_X = new float[LEGEND_RING_POINTS];
    private static final float[] LEGEND_RING_Y = new float[LEGEND_RING_POINTS];
    static {
        for (int i = 0; i < LEGEND_RING_POINTS; i++) {
            double angle = i * Math.PI * 2 / LEGEND_RING_POINTS;
            LEGEND_RING_X[i] = (float) Math.cos(angle);
            LEGEND_RING_Y[i] = (float) Math.sin(angle);
        }
    }

    private static void drawLegendRing(GuiGraphicsExtractor g, float cx, float cy, float radius, int alpha, int rgb) {
        if (alpha <= 0 || radius < 1) return;
        // Bounded draw cost regardless of viewport size or animation radius.
        for (int i = 0; i < LEGEND_RING_POINTS; i++) {
            int px = Math.round(cx + LEGEND_RING_X[i] * radius);
            int py = Math.round(cy + LEGEND_RING_Y[i] * radius);
            g.fill(px, py, px + 1, py + 1, (alpha << 24) | rgb);
        }
    }

    private void spawnCardBurst(int cx, int cy) {
        if (VCoinsPurchaseConfirm.isReducedMotion()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[] colors = {0xFFFFD700, 0xFFFFA000, 0xFFFFFFFF, 0xFFFFE082, 0xFF81D4FA};
        for (int p = 0; p < 18; p++) {
            float angle = (float) (r.nextDouble() * TWO_PI);
            float speed = 0.5f + r.nextFloat() * 1.8f;
            addParticle(cx, cy, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed - 0.3f,
                    colors[r.nextInt(colors.length)], r.nextInt(PARTICLE_SYMBOLS.length), 400 + r.nextInt(450));
        }
    }

    private void triggerGodPullCelebration(int cx, int cy, ItemStack stack) {
        legendSoundStage = 0;
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65f, 0.45f));
        }
    }

    private void playLegendScore(long elapsed) {
        if (this.minecraft == null) return;
        // Crossed stages are consumed once. Skip stale cues after a long frame stall.
        if (legendSoundStage < 1 && elapsed >= 350) {
            legendSoundStage = 1;
            if (elapsed < 650) this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35f, 0.4f));
        }
        if (legendSoundStage < 2 && elapsed >= 700) {
            legendSoundStage = 2;
            if (elapsed < 1150) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.85f, 0.5f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 0.85f, 0.6f));
            }
        }
        if (legendSoundStage < 3 && elapsed >= 1350) {
            legendSoundStage = 3;
            if (elapsed < 1800) this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.65f, 0.35f));
        }
    }

    private void triggerGodFinishCelebration(int cx, int cy, ItemStack stack) {
        // The single reveal timeline owns the particles and sound; no second explosion.
    }

    private static String formatNumber(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String formatCompactNumber(long value) {
        if (value >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        } else if (value >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        } else if (value >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }
}
