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
    private static final Identifier CARD_LEGEND_BACK  = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_legend_back.png");
    private static final Identifier CARD_LEGEND_FRONT = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_legend_front.png");
    private static final Identifier CARD_MYTHIC_BACK  = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_mythic_back.png");
    private static final Identifier CARD_MYTHIC_FRONT = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_mythic_front.png");

    // Continuous, high-poly AAA aura beam and bloom halo textures (matching Image 2)
    private static final Identifier AURA_BEAM_MYTHIC_UP   = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/aura_beam_mythic_up.png");
    private static final Identifier AURA_BEAM_MYTHIC_DOWN = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/aura_beam_mythic_down.png");
    private static final Identifier AURA_BEAM_LEGEND_UP   = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/aura_beam_legend_up.png");
    private static final Identifier AURA_BEAM_LEGEND_DOWN = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/aura_beam_legend_down.png");
    private static final Identifier AURA_HALO_MYTHIC      = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/aura_halo_mythic.png");
    private static final Identifier AURA_HALO_LEGEND      = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/aura_halo_legend.png");

    // High-poly card hover glow textures (tiered)
    private static final Identifier CARD_HOVER_MYTHIC  = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_hover_mythic.png");
    private static final Identifier CARD_HOVER_LEGEND  = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_hover_legend.png");
    private static final Identifier CARD_HOVER_REGULAR = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_hover_regular.png");

    // Cinematic reveal VFX textures (GPU-accelerated, high-fidelity)
    private static final Identifier CINEMATIC_SUNBURST         = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/cinematic_sunburst.png");
    private static final Identifier CINEMATIC_SHOCKWAVE_MYTHIC = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/cinematic_shockwave_mythic.png");
    private static final Identifier CINEMATIC_SHOCKWAVE_LEGEND = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/cinematic_shockwave_legend.png");
    private static final Identifier CINEMATIC_BEAM_MYTHIC      = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/cinematic_beam_mythic.png");
    private static final Identifier CINEMATIC_BEAM_LEGEND      = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/cinematic_beam_legend.png");
    private static final Identifier CINEMATIC_VIGNETTE         = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/cinematic_vignette.png");

    private static Identifier getCardBackTexture(ItemStack stack) {
        if (VBlackMarket.isMythicItem(stack)) {
            return CARD_MYTHIC_BACK;
        }
        if (VBlackMarket.isRomanGodItem(stack)) {
            return CARD_LEGEND_BACK;
        }
        return CARD_BACK;
    }

    private static Identifier getCardFrontTexture(ItemStack stack) {
        if (VBlackMarket.isMythicItem(stack)) {
            return CARD_MYTHIC_FRONT;
        }
        if (VBlackMarket.isRomanGodItem(stack)) {
            return CARD_LEGEND_FRONT;
        }
        return CARD_FRONT;
    }
    private static final int BM_CARD_W       = 48;
    private static final int BM_CARD_H       = 60;
    private static final int BM_CARD_SPACING = 64;
    private static final int BM_CARD_START_X = (VBlackMarketScreenHandler.MENU_W - (4 * BM_CARD_SPACING + BM_CARD_W)) / 2;
    private static final int BM_CARD_Y       = 68;
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
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client != null && client.gui != null
                && client.gui.screen() instanceof VBlackMarketScreen screen
                && !syncedCards.isEmpty()
                && (payload.epochDay() != syncedEpochDay || payload.resetSequence() != syncedResetSequence)) {
            screen.beginDeckTransition();
        }
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

    private long deckTransitionStart;
    private List<ItemStack> outgoingCards = List.of();
    private int outgoingRevealedMask;
    private int outgoingPurchasedMask;

    private void beginDeckTransition() {
        outgoingCards = syncedCards.stream().map(ItemStack::copy).toList();
        outgoingRevealedMask = syncedRevealedMask;
        outgoingPurchasedMask = syncedPurchasedMask;
        deckTransitionStart = VCoinsPurchaseConfirm.isReducedMotion() ? 0 : System.currentTimeMillis();
        localRevealedCards.clear();
        Arrays.fill(flipStartTime, 0L);
        Arrays.fill(hasSpawnedStartParticles, false);
        Arrays.fill(hasSpawnedMidParticles, false);
        Arrays.fill(hasSpawnedFinishParticles, false);
        godPullStartTime = 0L;
        godPullCardIndex = -1;
        legendSoundStage = 0;
        mythicSoundStage = 0;
        particleCount = 0;
        purchaseConfirm = new VCoinsPurchaseConfirm();
        if (this.minecraft != null) this.minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.85f, 0.5f));
    }

    private boolean deckTransitionActive() {
        return !VCoinsPurchaseConfirm.isReducedMotion() && deckTransitionStart > 0
                && System.currentTimeMillis() - deckTransitionStart < 900;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
        if (!deckTransitionActive()) outgoingCards = List.of();
    }

    private void drawDeckTransition(GuiGraphicsExtractor g, long now) {
        long elapsed = now - deckTransitionStart;
        for (int i = 0; i < 5; i++) {
            boolean outgoing = elapsed < 350;
            float t = outgoing ? Math.clamp((elapsed - i * 22f) / 240f, 0f, 1f)
                    : Math.clamp((elapsed - 350 - i * 55f) / 300f, 0f, 1f);
            if ((outgoing && t >= 1) || (!outgoing && t <= 0)) continue;
            float eased = t * t * (3 - 2 * t);
            float size = outgoing ? 1 - eased : eased;
            float homeX = this.leftPos + BM_CARD_START_X + i * BM_CARD_SPACING + BM_CARD_W / 2f;
            float centerX = this.leftPos + this.imageWidth / 2f;
            float cx = outgoing ? homeX + (centerX - homeX) * eased : centerX + (homeX - centerX) * eased;
            // Integer half-height matches the resting renderer exactly at the final frame.
            float cy = this.topPos + BM_CARD_Y + BM_CARD_H / 2 + (1 - size) * 12;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().rotate((1 - size) * (i - 2) * 0.10f);
            g.pose().scale(Math.max(0.01f, size), Math.max(0.01f, size));
            boolean face = outgoing && (outgoingRevealedMask & (1 << i)) != 0;
            List<ItemStack> deck = outgoing ? outgoingCards : syncedCards;
            ItemStack card = i < deck.size() ? deck.get(i) : ItemStack.EMPTY;
            int cardX = -BM_CARD_W / 2, cardY = -BM_CARD_H / 2;
            if (!face) {
                // Same frame, aura and clock as the resting card: no effects popping on at landing.
                drawMysteryCard(g, card, cardX, cardY, now, i, false);
            } else {
                drawCardTexture(g, getCardFrontTexture(card), cardX, cardY, BM_CARD_W, BM_CARD_H);
                boolean purchased = (outgoingPurchasedMask & (1 << i)) != 0;
                if (!purchased) drawCardAura(g, card, cardX, cardY, now, i, false, false);
                g.item(card, cardX + (BM_CARD_W - 16) / 2, cardY + (BM_CARD_H - 16) / 2);
                if (purchased) g.fill(cardX, cardY, cardX + BM_CARD_W, cardY + BM_CARD_H, 0x88000000);
            }
            g.pose().popMatrix();
        }
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
    private final float[] cardHoverProgress = new float[5];
    private int lastHoveredCardIndex = -1;

    // Particle pool (expanded for legendary god pull fireworks)
    private static final int MAX_PARTICLES = 256;
    private static final String[] PARTICLE_SYMBOLS = {"✦", "✧", "⋆", "★", "⚡", "☼", "⚜", "👑", "•", "ᛟ", "ᚱ", "◇"};
    private long godPullStartTime = 0L;
    private int godPullCardIndex = -1;
    private int legendSoundStage;
    private int mythicSoundStage;
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
        super(handler, inventory, title, VBlackMarketScreenHandler.MENU_W, VBlackMarketScreenHandler.MENU_H);
        this.inventoryLabelX = 1000;
        this.titleLabelX = 1000;
    }

    @Override
    protected void init() {
        super.init();

        // Return to Shop button
        this.backToShopButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.black_market.back_shop"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenShopPayload());
                })
                .bounds(this.leftPos + 20, this.topPos + 20, 60, 15)
                .build());

        // Banked Black Market reset button
        this.bmResetButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.black_market.reset_count", syncedBankedResets),
                button -> useBankedReset())
                .bounds(this.leftPos + 128, this.topPos + 20, 92, 15)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("vcoins.black_market.reset_hint", syncedBankedResets)))
                .build());

        // Reveal All cards button
        this.bmRevealAllButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.black_market.reveal_all"),
                button -> revealAllCards())
                .bounds(this.leftPos + 259, this.topPos + 20, 75, 15)
                .build());

        // Transaction verification toggle button
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
                .bounds(this.leftPos + 318, this.topPos + 180, 16, 12)
                .tooltip(VCoinsPurchaseConfirm.getToggleTooltip())
                .build());

        // Navigation Toolbar: Shop | Black Market | Duplicate
        this.shopTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.title"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenShopPayload());
                })
                .bounds(this.leftPos + 40, this.topPos + 160, 80, 16)
                .build());

        this.blackMarketTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.tab.black_market"),
                button -> {})
                .bounds(this.leftPos + 131, this.topPos + 160, 92, 16)
                .build());

        this.duplicateTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.duplicate.open"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenDuplicatePayload());
                })
                .bounds(this.leftPos + 234, this.topPos + 160, 80, 16)
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
            this.bmRevealAllButton.active = (count < 5) && !deckTransitionActive();
        }
        if (this.bmResetButton != null) {
            this.bmResetButton.active = !deckTransitionActive();
            this.bmResetButton.visible = (syncedBankedResets > 0);
            if (this.bmResetButton.visible) {
                this.bmResetButton.setMessage(Component.translatable("vcoins.black_market.reset_count", syncedBankedResets));
                this.bmResetButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("vcoins.black_market.reset_hint", syncedBankedResets)));
            }
        }
    }

    private void revealAllCards() {
        if (deckTransitionActive()) return;
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
        if (deckTransitionActive()) return;
        // Keep the old deck intact until the authoritative reset succeeds.
        ClientPlayNetworking.send(new BlackMarketResetActionPayload());
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (deckTransitionActive()) return true;
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

            ItemStack clickedStack = (card < syncedCards.size()) ? syncedCards.get(card) : ItemStack.EMPTY;
            boolean isGodItem = !clickedStack.isEmpty() && (VBlackMarket.isRomanGodItem(clickedStack) || VBlackMarket.isMythicItem(clickedStack));
            if (isGodItem && !VCoinsPurchaseConfirm.isReducedMotion()) {
                godPullStartTime = now;
                godPullCardIndex = card;
                triggerGodPullCelebration(this.leftPos + BM_CARD_START_X + card * BM_CARD_SPACING + BM_CARD_W / 2,
                                          this.topPos + BM_CARD_Y + BM_CARD_H / 2, clickedStack);
            }

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
        extractor.fill(this.leftPos + 20, this.topPos + 158, this.leftPos + 334, this.topPos + 178, 0x55080310);
        extractor.fill(this.leftPos + 20, this.topPos + 158, this.leftPos + 334, this.topPos + 159, 0x22D4AF37);
        extractor.fill(this.leftPos + 20, this.topPos + 177, this.leftPos + 334, this.topPos + 178, 0x22D4AF37);

        // Highlight line under active mode button (Black Market - orange)
        extractor.fill(this.leftPos + 131, this.topPos + 175, this.leftPos + 223, this.topPos + 176, 0xFFFFAA00);

        // Player Inventory label
        extractor.text(this.font, Component.translatable("vcoins.inventory"),
                this.leftPos + VBlackMarketScreenHandler.PLAYER_X, this.topPos + 184, 0xFFC8A96E, false);

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

        extractor.fill(this.leftPos + 20, this.topPos + 39, this.leftPos + 334, this.topPos + 53, 0x88080310);
        extractor.text(this.font, Component.literal("§b" + timerStr), this.leftPos + 26, this.topPos + 42, 0xFF55FFFF, true);
        int pityRemaining = 300 - (syncedLifetimeFlipCount % 300);
        extractor.centeredText(this.font, Component.translatable("vcoins.black_market.pity_remaining", pityRemaining),
                this.leftPos + this.imageWidth / 2, this.topPos + 42, 0xFFAAAAAA);
        String balStr = formatCompactNumber(balance);
        extractor.text(this.font, Component.literal(balStr), this.leftPos + 328 - this.font.width(balStr),
                this.topPos + 42, 0xFFFFFF55, true);

        if (purchaseConfirm.isArmed()) {
            purchaseConfirm.renderBanner(extractor, this.font, this.leftPos + 20, this.topPos + 39, 314, 14);
        }

        // 5 Cards Rendering
        long now = System.currentTimeMillis();
        if (deckTransitionActive()) {
            drawDeckTransition(extractor, now);
            return;
        }
        for (int i = 0; i < 5; i++) {
            // The cinematic owns this card until it has returned to its slot.
            if (i == godPullCardIndex && legendPresentationActive()) continue;
            int cardBaseX = this.leftPos + BM_CARD_START_X + i * BM_CARD_SPACING;
            int cardBaseY = this.topPos + BM_CARD_Y;
            boolean isRevealed = ((syncedRevealedMask & (1 << i)) != 0) || localRevealedCards.contains(i);
            boolean isPurchased = (syncedPurchasedMask & (1 << i)) != 0;

            ItemStack stack = (i < syncedCards.size()) ? syncedCards.get(i) : ItemStack.EMPTY;
            if (stack.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
                List<ItemStack> fallback = VBlackMarket.getItemsForPlayer(this.minecraft.player.getUUID());
                if (i < fallback.size()) stack = fallback.get(i);
            }
            boolean isMythic = !stack.isEmpty() && VBlackMarket.isMythicItem(stack);
            boolean isLegend = !stack.isEmpty() && VBlackMarket.isRomanGodItem(stack);
            boolean isGod = isMythic || isLegend;
            boolean isEpic = !stack.isEmpty() && ("epic".equals(VBlackMarket.getCardTier(stack)) || stack.getRarity() == net.minecraft.world.item.Rarity.EPIC);
            boolean isRare = !stack.isEmpty() && ("exclusive".equals(VBlackMarket.getCardTier(stack)) || stack.getRarity() == net.minecraft.world.item.Rarity.RARE);
            boolean isSpecial = isGod || isEpic || isRare;

            boolean isHovered = mouseX >= cardBaseX && mouseX < cardBaseX + BM_CARD_W
                    && mouseY >= cardBaseY && mouseY < cardBaseY + BM_CARD_H;

            // Smooth dynamic hover interpolation & tactile audio tick
            float targetHover = (isHovered && !isPurchased && !legendPresentationActive()) ? 1.0f : 0.0f;
            float hover = cardHoverProgress[i];
            if (targetHover > hover) {
                hover = Math.min(1.0f, hover + 0.18f);
                if (hover > 0.05f && lastHoveredCardIndex != i) {
                    lastHoveredCardIndex = i;
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.65f, 0.22f));
                    }
                }
            } else {
                hover = Math.max(0.0f, hover - 0.12f);
                if (lastHoveredCardIndex == i && hover <= 0.01f) {
                    lastHoveredCardIndex = -1;
                }
            }
            cardHoverProgress[i] = hover;

            float liftY = hover * 3.5f;
            int drawY = (int) Math.round(cardBaseY - liftY);

            // Tiered High-Poly Card Hover Glow (Pre-rendered GPU bilinear halo)
            if (hover > 0.02f && !isPurchased) {
                int hGlowW = BM_CARD_W + 24;
                int hGlowH = BM_CARD_H + 24;
                int hGlowX = cardBaseX - 12;
                int hGlowY = drawY - 12;
                int hGlowAlpha = (int) (hover * 225);
                Identifier hoverTex = isMythic ? CARD_HOVER_MYTHIC
                        : (isLegend ? CARD_HOVER_LEGEND : CARD_HOVER_REGULAR);
                int glowTint = isEpic ? 0xBC65FF : (isRare ? 0x35E8FF : 0xFFFFFF);
                extractor.blit(RenderPipelines.GUI_TEXTURED, hoverTex, hGlowX, hGlowY, 0.0f, 0.0f, hGlowW, hGlowH, 256, 256, 256, 256, (hGlowAlpha << 24) | glowTint);
            }

            long start = flipStartTime[i];
            boolean isFlipping = !VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now >= start && now < start + BM_FLIP_MS;

            if (isFlipping) {
                float p = Math.clamp((float) (now - start) / BM_FLIP_MS, 0.0f, 1.0f);
                float angle = p * (float) Math.PI;
                float scaleX = (float) Math.abs(Math.cos(angle));
                boolean isBackSide = angle < (Math.PI * 0.5);

                int color = mysteryCardColor(stack) & 0xFFFFFF;

                float zPop = 1.0f + (isSpecial ? 0.22f : 0.10f) * (float) Math.sin(angle);
                float cx = cardBaseX + BM_CARD_W * 0.5f;
                float cy = cardBaseY + BM_CARD_H * 0.5f;
                int halfW = BM_CARD_W / 2;
                int halfH = BM_CARD_H / 2;

                if (!hasSpawnedStartParticles[i]) {
                    hasSpawnedStartParticles[i] = true;
                    if (!isGod) spawnCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                }

                if (p >= 0.5f && !hasSpawnedMidParticles[i]) {
                    hasSpawnedMidParticles[i] = true;
                    if (isGod) {
                        godPullStartTime = System.currentTimeMillis();
                        godPullCardIndex = i;
                        triggerGodPullCelebration(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                        continue; // Hand off immediately; do not also draw the source this frame.
                    } else if (isEpic) {
                        spawnCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                        if (this.minecraft != null) {
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.55f, 0.70f));
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.35f, 0.85f));
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.40f, 0.80f));
                        }
                    } else if (isRare) {
                        spawnCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                        if (this.minecraft != null) {
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.60f, 0.75f));
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.75f, 0.70f));
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.25f, 0.80f));
                        }
                    } else {
                        spawnCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                        if (this.minecraft != null) {
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.25f));
                        }
                    }
                }

                // Expanding bloom halo behind special cards on flip midpoint
                if (isSpecial && p >= 0.5f) {
                    float revealT = Math.clamp((p - 0.5f) / 0.5f, 0.0f, 1.0f);
                    if (revealT < 0.90f) {
                        float haloFade = 1.0f - revealT / 0.90f;
                        int haloA = (int) (haloFade * (isEpic ? 220 : (isRare ? 200 : 180)));
                        int haloSpread = (int) (BM_CARD_W + 24 + revealT * 48);
                        Identifier haloTex = (isMythic ? AURA_HALO_MYTHIC : AURA_HALO_LEGEND);
                        int haloColor = (color != 0) ? color : 0xFFD700;
                        extractor.blit(RenderPipelines.GUI_TEXTURED, haloTex,
                                (int) (cx - haloSpread / 2f), (int) (cy - haloSpread / 2f),
                                0.0f, 0.0f, haloSpread, haloSpread,
                                256, 256, 256, 256, (haloA << 24) | haloColor);
                    }
                }

                extractor.pose().pushMatrix();
                extractor.pose().translate(cx, cy);
                extractor.pose().scale(Math.max(0.015f, scaleX) * zPop, zPop);

                if (isBackSide) {
                    drawMysteryCard(extractor, stack, -halfW, -halfH, now, i, isHovered, 0f);

                    int shadeAlpha = (int) ((1.0f - scaleX) * 70);
                    if (shadeAlpha > 0) {
                        extractor.fill(-halfW, -halfH, halfW, halfH, (shadeAlpha << 24));
                    }
                } else {
                    float revealT = Math.clamp((p - 0.5f) / 0.5f, 0.0f, 1.0f);

                    if (isGod) {
                        if (isMythic) {
                            extractor.fill(-halfW - 1, -halfH - 1, halfW + 1, halfH + 1, 0xD0FF2200);
                        } else {
                            extractor.fill(-halfW - 1, -halfH - 1, halfW + 1, halfH + 1, 0xD0FFD700);
                        }
                    } else if (isEpic) {
                        extractor.fill(-halfW - 1, -halfH - 1, halfW + 1, halfH + 1, 0xA0BC65FF);
                    } else if (isRare) {
                        extractor.fill(-halfW - 1, -halfH - 1, halfW + 1, halfH + 1, 0xA035E8FF);
                    } else {
                        extractor.fill(-halfW - 1, -halfH - 1, halfW + 1, halfH + 1, 0x88D4AF37);
                    }

                    drawCardTexture(extractor, getCardFrontTexture(stack), -halfW, -halfH, BM_CARD_W, BM_CARD_H);

                    if (!stack.isEmpty()) {
                        float itemPop = 1.0f + (isSpecial ? 0.32f : 0.15f) * (float) Math.sin(revealT * Math.PI);
                        extractor.pose().pushMatrix();
                        extractor.pose().scale(itemPop, itemPop);
                        extractor.item(stack, -8, -halfH + (BM_CARD_H - 16) / 2);
                        extractor.itemDecorations(this.font, stack, -8, -halfH + (BM_CARD_H - 16) / 2);
                        extractor.pose().popMatrix();
                    }

                    // Dynamic 45-degree Holographic Specular Slash across the card face
                    if (revealT <= 0.75f) {
                        float sweepProgress = revealT / 0.75f;
                        int sweepX = Math.round(sweepProgress * (BM_CARD_W + BM_CARD_H + 20) - halfW - BM_CARD_H);
                        int slashAlpha = (int) (Math.sin(sweepProgress * Math.PI) * (isSpecial ? 230 : 130));
                        if (slashAlpha > 4) {
                            for (int row = -halfH + 2; row < halfH - 2; row++) {
                                int rx = sweepX + row;
                                int left = Math.max(-halfW + 2, rx - 3);
                                int right = Math.min(halfW - 2, rx + 4);
                                if (right > left) {
                                    int slashColor = isEpic ? 0xFFEAD0FF : (isRare ? 0xFFD0FFFF : 0xFFFFFDF0);
                                    extractor.fill(left, row, right, row + 1, (slashAlpha << 24) | slashColor);
                                }
                            }
                        }
                    }
                }

                extractor.pose().popMatrix();

                // Luminous vertical energy spine when card is edge-on
                if (scaleX < 0.14f) {
                    int edgeAlpha = (int) (245 * (1.0f - scaleX / 0.14f));
                    int edgeColor = (edgeAlpha << 24) | (color != 0 ? color : 0xFFE082);
                    int top = (int) (cy - halfH - (isSpecial ? 3 : 0));
                    int bottom = (int) (cy + halfH + (isSpecial ? 3 : 0));
                    extractor.fill((int) (cx - 1), top, (int) (cx + 2), bottom, edgeColor);
                    extractor.fill((int) (cx), top + 2, (int) (cx + 1), bottom - 2, (edgeAlpha << 24) | 0xFFFFFF);
                }

                if (p >= 0.95f && !hasSpawnedFinishParticles[i]) {
                    hasSpawnedFinishParticles[i] = true;
                    if (isGod) {
                        triggerGodFinishCelebration(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                    } else {
                        spawnCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                    }
                }

            } else if (!isRevealed || (!VCoinsPurchaseConfirm.isReducedMotion() && start > now)) {
                // Reveal-all schedules staggered starts. Server state may already be revealed,
                // but a queued card must retain its back until its own animation begins.
                drawMysteryCard(extractor, stack, cardBaseX, drawY, now, i, isHovered, hover);

                extractor.centeredText(this.font, Component.literal("§7?"), cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 3, 0xFFAAAAAA);

            } else {
                // ─── FULLY REVEALED CARD ───
                if (!isGod) {
                    int cardColor = mysteryCardColor(stack);
                    int outlineRgb = cardColor != 0 ? cardColor : 0xD4AF37;
                    extractor.fill(cardBaseX - 1, drawY - 1, cardBaseX + BM_CARD_W + 1, drawY, (0x88 << 24) | outlineRgb);
                    extractor.fill(cardBaseX - 1, drawY + BM_CARD_H, cardBaseX + BM_CARD_W + 1, drawY + BM_CARD_H + 1, (0x44 << 24) | outlineRgb);
                    extractor.fill(cardBaseX - 1, drawY,             cardBaseX,                 drawY + BM_CARD_H, (0x44 << 24) | outlineRgb);
                    extractor.fill(cardBaseX + BM_CARD_W, drawY,     cardBaseX + BM_CARD_W + 1, drawY + BM_CARD_H, (0x44 << 24) | outlineRgb);
                }

                drawCardTexture(extractor, getCardFrontTexture(stack), cardBaseX, drawY, BM_CARD_W, BM_CARD_H);
                if (!isPurchased) drawCardAura(extractor, stack, cardBaseX, drawY, now, i, isHovered, hover, false);

                if (!stack.isEmpty()) {
                    int itemX = cardBaseX + (BM_CARD_W - 16) / 2;
                    int itemY = drawY + (BM_CARD_H - 16) / 2;
                    extractor.item(stack, itemX, itemY);
                    extractor.itemDecorations(this.font, stack, itemX, itemY);
                }

                if (hover > 0.02f && !isPurchased) {
                    int sheenAlpha = (int) (hover * 42);
                    extractor.fill(cardBaseX, drawY, cardBaseX + BM_CARD_W, drawY + BM_CARD_H, (sheenAlpha << 24) | 0xFFFFFF);
                }

                if (isPurchased) {
                    extractor.fill(cardBaseX, cardBaseY, cardBaseX + BM_CARD_W, cardBaseY + BM_CARD_H, 0x88000000);
                    extractor.centeredText(this.font, Component.translatable("vcoins.black_market.purchased_short").withStyle(ChatFormatting.GREEN), cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 4, 0xFF55FF55);
                } else {
                    int disc  = VBlackMarket.getDiscountPercent(stack, syncedEpochDay, syncedResetSequence);
                    long price = VBlackMarket.getDiscountedPrice(stack, syncedEpochDay, syncedResetSequence);

                    if (isGod) {
                        int badgeW = 38;
                        int badgeH = 10;
                        int badgeX = cardBaseX + (BM_CARD_W - badgeW) / 2;
                        int badgeY = cardBaseY + BM_CARD_H + 3;
                        if (isMythic) {
                            extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xEE1E0406);
                            extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, 0xFFFF2E14);
                            extractor.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, 0xFFFF2E14);
                            int textColor = 0xFFFF7A66;
                            extractor.centeredText(this.font, Component.translatable("vcoins.mythic.badge"), cardBaseX + BM_CARD_W / 2, badgeY + 1, textColor);
                            extractor.centeredText(this.font, Component.literal("§c"  + formatCompactNumber(price)),  cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 15, 0xFFFF5555);
                        } else {
                            extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xEE140206);
                            extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, 0xFFFFD700);
                            extractor.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, 0xFFFFD700);
                            int textColor = 0xFFFFE6A3;
                            extractor.centeredText(this.font, Component.translatable("vcoins.black_market.god_badge"), cardBaseX + BM_CARD_W / 2, badgeY + 1, textColor);
                            extractor.centeredText(this.font, Component.literal("§6"  + formatCompactNumber(price)),  cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 15, 0xFFFFAA00);
                        }
                    } else {
                        extractor.centeredText(this.font, Component.literal("§a-" + disc + "%"),                  cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 3,  0xFF55FF55);
                        extractor.centeredText(this.font, Component.literal("§e"  + formatCompactNumber(price)),  cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 14, 0xFFFFFF55);
                    }
                }
            }
        }
    }

    // Roman relics take precedence over the item's vanilla rarity.
    // Exclusive currently maps to RARE; the data model has no separate exclusive tier.
    private static int mysteryCardColor(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (VBlackMarket.isMythicItem(stack)) return 0xFF2200;
        if (VBlackMarket.isRomanGodItem(stack)) return 0xFFD700;
        if ("epic".equals(VBlackMarket.getCardTier(stack))) return 0xBC65FF;
        if ("exclusive".equals(VBlackMarket.getCardTier(stack))) return 0x35E8FF;
        return switch (stack.getRarity()) {
            case EPIC -> 0xBC65FF;
            case RARE -> 0x35E8FF;
            default -> 0;
        };
    }

    private void drawMysteryCard(GuiGraphicsExtractor g, ItemStack stack, int x, int y,
                                 long now, int index, boolean hovered) {
        drawMysteryCard(g, stack, x, y, now, index, hovered, hovered ? 1.0f : 0.0f);
    }

    private void drawMysteryCard(GuiGraphicsExtractor g, ItemStack stack, int x, int y,
                                 long now, int index, boolean hovered, float hover) {
        drawCardTexture(g, getCardBackTexture(stack), x, y, BM_CARD_W, BM_CARD_H);
        drawCardAura(g, stack, x, y, now, index, hovered, hover, true);
    }

    private void drawCardAura(GuiGraphicsExtractor g, ItemStack stack, int x, int y,
                              long now, int index, boolean hovered, boolean hidden) {
        drawCardAura(g, stack, x, y, now, index, hovered, hovered ? 1.0f : 0.0f, hidden);
    }

    private void drawCardAura(GuiGraphicsExtractor g, ItemStack stack, int x, int y,
                              long now, int index, boolean hovered, float hover, boolean hidden) {
        int color = mysteryCardColor(stack) & 0xFFFFFF;
        if (color == 0) {
            if (hovered) drawCardOutline(g, x - 1, y - 1, BM_CARD_W + 2, BM_CARD_H + 2, 0xCCD4AF37);
            return;
        }
        boolean isMythic = VBlackMarket.isMythicItem(stack);
        boolean legend = VBlackMarket.isRomanGodItem(stack);
        if (legendPresentationActive()) {
            drawCardOutline(g, x, y, BM_CARD_W, BM_CARD_H, 0xCC000000 | color);
            return;
        }
        boolean still = VCoinsPurchaseConfirm.isReducedMotion();
        double time = still ? index * 0.7 : (now % 120000L) / 1000.0 + index * 0.7;
        float breath = still ? 0.65f : (float) (0.65 + 0.35 * Math.sin(time * 2.2));
        float power = (0.75f + 0.45f * hover) * (hidden ? 1f : 0.78f);

        if (isMythic) {
            drawMythicAura(g, x, y, time, power, breath, hover, hidden);
            return;
        }

        if (legend) {
            drawLegendAura(g, x, y, time, power, breath, hover, hidden);
            return;
        }

        // Standard / Epic / Rare ambient aura (delicate, no harsh boxes)
        int highlight = color == 0xBC65FF ? 0xEED8FF : 0xD8FFFF;
        drawCardOutline(g, x, y, BM_CARD_W, BM_CARD_H, ((int) (180 * power) << 24) | color);
        drawCardOutline(g, x - 1, y - 1, BM_CARD_W + 2, BM_CARD_H + 2, ((int) (40 * breath * power) << 24) | color);

        int perimeter = 2 * (BM_CARD_W - 1 + BM_CARD_H - 1);
        int head = (int) (time * 29);
        int comets = 2;
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
                g.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, (alpha << 24) | (tail < 3 ? highlight : color));
            }
        }
        for (int i = 0; i < 6; i++) {
            float t = (float) ((time * 0.32 + i * 0.173) % 1);
            int px = x + (i % 2 == 0 ? 2 : BM_CARD_W - 3);
            int py = y + BM_CARD_H - 3 - (int) (t * (BM_CARD_H - 6));
            int alpha = (int) (Math.sin(t * Math.PI) * 190 * power);
            g.fill(px, py, px + 1, py + 2, (alpha << 24) | highlight);
        }
        if (hidden) {
            float sweep = (float) ((time * 0.22) % 1) * (BM_CARD_H + BM_CARD_W + 16) - 8;
            for (int row = 4; row < BM_CARD_H - 4; row++) {
                int sx = Math.round(sweep - row);
                int left = Math.max(4, sx - 2), right = Math.min(BM_CARD_W - 4, sx + 2);
                if (right > left) g.fill(x + left, y + row, x + right, y + row + 1,
                        ((int) (32 * power) << 24) | highlight);
            }
        }
    }

    /**
     * Continuous, high-poly AAA vertical light pillar matching reference Image 2.
     * Projects BOTH upwards into the sky and downwards beneath the card with
     * razor-sharp boundary rays, incandescent white-hot core, and fanning god-rays.
     */
    private static void drawVerticalAuraBeam(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                             double time, float power, float breath, float hover, boolean isMythic) {
        int cx = x + w / 2;
        Identifier beamUpTex   = isMythic ? AURA_BEAM_MYTHIC_UP   : AURA_BEAM_LEGEND_UP;
        Identifier beamDownTex = isMythic ? AURA_BEAM_MYTHIC_DOWN : AURA_BEAM_LEGEND_DOWN;
        Identifier haloTex     = isMythic ? AURA_HALO_MYTHIC      : AURA_HALO_LEGEND;

        float pulse = 0.90f + 0.10f * (float) Math.sin(time * 3.2);
        int alpha = (int) (Math.clamp(power * pulse, 0.15f, 1.0f) * 255);
        int tint = (alpha << 24) | 0x00FFFFFF;

        // 1. Soft Ambient Radial Halo behind card (expands dynamically on hover)
        int haloW = (int) (w + 36 + hover * 12);
        int haloH = (int) (h + 36 + hover * 12);
        int haloX = cx - haloW / 2;
        int haloY = y + h / 2 - haloH / 2;
        int haloAlpha = (int) (alpha * (0.72f + 0.20f * hover));
        int haloTint = (Math.min(255, haloAlpha) << 24) | 0x00FFFFFF;
        g.blit(RenderPipelines.GUI_TEXTURED, haloTex, haloX, haloY, 0.0f, 0.0f, haloW, haloH, 256, 256, 256, 256, haloTint);

        // Beam geometry:
        // On hover, beam widens dynamically and shoots higher into the heavens
        int beamW = (int) (107 + hover * 10);
        int beamX = cx - beamW / 2;

        // 2. Upward Light Pillar (shooting into the heavens above the card)
        int beamHUp = (int) (64 + hover * 12);
        int beamYUp = y - beamHUp;
        g.blit(RenderPipelines.GUI_TEXTURED, beamUpTex, beamX, beamYUp, 0.0f, 0.0f, beamW, beamHUp, 512, 512, 512, 512, tint);

        // 3. Downward Light Pillar (shooting down beneath the card)
        int beamHDown = (int) (52 + hover * 8);
        int beamYDown = y + h;
        int downAlpha = (int) (alpha * 0.88f);
        int downTint = (downAlpha << 24) | 0x00FFFFFF;
        g.blit(RenderPipelines.GUI_TEXTURED, beamDownTex, beamX, beamYDown, 0.0f, 0.0f, beamW, beamHDown, 512, 512, 512, 512, downTint);
    }

    private static void drawLegendAura(GuiGraphicsExtractor g, int x, int y, double time, float power, float breath, float hover, boolean hidden) {
        int w = BM_CARD_W;
        int h = BM_CARD_H;

        // 1. Dual-Directional Vertical Radiance Pillar (Shooting BOTH upwards and downwards, from Image 2!)
        drawVerticalAuraBeam(g, x, y, w, h, time, power, breath, hover, false);

        // 2. Soft golden 1-pixel outline track
        int goldOutlineAlpha = (int) (190 * power);
        drawCardOutline(g, x, y, w, h, (goldOutlineAlpha << 24) | 0xFFFFD700);

        // 3. Two Counter-Rotating Celestial Rings of Divine Seals
        float cx = x + w / 2f, cy = y + h / 2f;
        for (int ring = 0; ring < 2; ring++) {
            float radius = ring == 0 ? 13f : 17f;
            for (int mark = 0; mark < 8; mark++) {
                double angle = mark * Math.PI / 4.0 + time * (ring == 0 ? 0.38 : -0.28);
                int px = Math.round(cx + (float) Math.cos(angle) * radius);
                int py = Math.round(cy + (float) Math.sin(angle) * radius);
                int alpha = (int) ((hidden ? 180 : 100) * power);
                int markColor = ring == 0 ? 0xFFFFE89A : 0xFFFFC033;
                g.fill(px, py, px + (mark % 2 == 0 ? 2 : 1), py + 1, (alpha << 24) | markColor);
            }
        }

        // 4. Sealed Divine Sheen & Center Golden Star Pulse
        if (hidden) {
            float sweep = (float) ((time * 0.22) % 1) * (h + w + 16) - 8;
            for (int row = 4; row < h - 4; row++) {
                int sx = Math.round(sweep - row);
                int left = Math.max(4, sx - 2), right = Math.min(w - 4, sx + 2);
                if (right > left) {
                    g.fill(x + left, y + row, x + right, y + row + 1, ((int) (40 * power) << 24) | 0xFFFFF5D0);
                }
            }
            float radius = 9 + 2.0f * breath;
            for (int i = 0; i < 12; i++) {
                double angle = time * 0.55 + i * Math.PI / 6.0;
                int px = Math.round(cx + (float) Math.cos(angle) * radius);
                int py = Math.round(cy + (float) Math.sin(angle) * radius);
                g.fill(px, py, px + 1, py + 1, ((int) (190 * power) << 24) | 0xFFFFE888);
            }
            int centerA = (int) ((140 + 90 * breath) * power);
            g.fill((int) cx - 3, (int) cy, (int) cx + 4, (int) cy + 1, (centerA << 24) | 0xFFFFF0A0);
            g.fill((int) cx, (int) cy - 3, (int) cx + 1, (int) cy + 4, (centerA << 24) | 0xFFFFF0A0);
        }

        // 5. 14 Divine Golden Particles (Stardust & Floating Star Sparkles)
        drawLegendParticles(g, x, y, time, power);
    }

    private static void drawLegendParticles(GuiGraphicsExtractor g, int x, int y, double time, float power) {
        int w = BM_CARD_W;
        int h = BM_CARD_H;

        // 8 Drifting Golden Motes hugging edges and light pillar with gentle sway
        for (int i = 0; i < 8; i++) {
            float t = (float) ((time * 0.28 + i * 0.125) % 1.0);
            float sway = (float) Math.sin(t * Math.PI * 2.0 + i) * 1.8f;
            int px = (i % 2 == 0) ? x - 2 + Math.round(sway) : x + w + 1 + Math.round(sway);
            int py = y + h - (int) (t * (h + 55));
            int alpha = (int) (Math.sin(t * Math.PI) * 220 * power);
            int color = t < 0.3f ? 0xFFFFF2B0 : (t < 0.7f ? 0xFFFFD700 : 0xFFE5A820);
            if (alpha > 4) {
                g.fill(px, py, px + 1, py + 1, (alpha << 24) | color);
            }
        }

        // 6 Floating Light Shaft Diamonds (Image 2 celestial stardust ascending through the beacon)
        for (int i = 0; i < 6; i++) {
            float t = (float) ((time * 0.20 + i * 0.166) % 1.0);
            float drift = (float) Math.cos(t * Math.PI * 2.0 + i * 1.5) * 3.0f;
            int px = x + 8 + (i * 6) + Math.round(drift);
            int py = y + h + 6 - (int) (t * (h + 65));
            int alpha = (int) (Math.sin(t * Math.PI) * 230 * power);
            if (alpha > 6) {
                g.fill(px, py, px + 1, py + 1, (alpha << 24) | 0xFFFFFDE0);
                if (alpha > 110 && t > 0.25f && t < 0.75f) {
                    int subA = (int) (alpha * 0.40f);
                    g.fill(px - 1, py, px, py + 1, (subA << 24) | 0xFFFFD700);
                    g.fill(px + 1, py, px + 2, py + 1, (subA << 24) | 0xFFFFD700);
                    g.fill(px, py - 1, px + 1, py, (subA << 24) | 0xFFFFD700);
                    g.fill(px, py + 1, px + 1, py + 2, (subA << 24) | 0xFFFFD700);
                }
            }
        }

        // 2 Corner Star Twinkles
        for (int side = 0; side < 2; side++) {
            float blink = (float) Math.max(0, Math.sin(time * 3.5 + side * Math.PI));
            int cx = side == 0 ? x : x + w - 1;
            int cy = y;
            int alpha = (int) (blink * 200 * power);
            if (alpha > 10) {
                g.fill(cx, cy, cx + 1, cy + 1, (alpha << 24) | 0xFFFFFFE0);
            }
        }
    }

    private static void drawMythicAura(GuiGraphicsExtractor g, int x, int y, double time, float power, float breath, float hover, boolean hidden) {
        int w = BM_CARD_W;
        int h = BM_CARD_H;

        // 1. Dual-Directional Vertical Radiance Pillar (Shooting BOTH upwards and downwards, straight from Image 2!)
        drawVerticalAuraBeam(g, x, y, w, h, time, power, breath, hover, true);

        // 2. Resting Molten 1-pixel Outline Track
        int trackAlpha = (int) (220 * power);
        drawCardOutline(g, x, y, w, h, (trackAlpha << 24) | 0xFFFF2200);

        // 3. Flowing 1-pixel Molten Lava Border (with 3 circulating speed heads)
        drawMythicLavaBorder(g, x, y, time, power, breath, hidden);

        // 4. Corner Fiery Eruption Flares
        for (int corner = 0; corner < 4; corner++) {
            float flarePulse = (float) Math.max(0, Math.sin(time * 4.8 + corner * 1.57));
            int flareReach = 1 + Math.round(flarePulse * 2.5f);
            int flareA = (int) (flarePulse * 230 * power);
            if (flareA > 8) {
                int cx = (corner % 2 == 0) ? x : x + w - 1;
                int cy = (corner < 2) ? y : y + h - 1;
                int flareColor = flareReach > 2 ? 0xFFFFF6B8 : 0xFFFF4808;
                g.fill(cx - (corner % 2 == 0 ? flareReach : 0), cy,
                       cx + (corner % 2 == 0 ? 1 : flareReach + 1), cy + 1, (flareA << 24) | flareColor);
                g.fill(cx, cy - (corner < 2 ? flareReach : 0),
                       cx + 1, cy + (corner < 2 ? 1 : flareReach + 1), (flareA << 24) | flareColor);
            }
        }

        // 5. Triple Rotating Fiery Abyssal Rings
        float cx = x + w / 2f, cy = y + h / 2f;
        for (int ring = 0; ring < 3; ring++) {
            float radius = ring == 0 ? 12f : (ring == 1 ? 16f : 20f);
            float dir = (ring % 2 == 0) ? 0.42f : -0.34f;
            for (int mark = 0; mark < 8; mark++) {
                double angle = mark * Math.PI / 4.0 + time * dir;
                int px = Math.round(cx + (float) Math.cos(angle) * radius);
                int py = Math.round(cy + (float) Math.sin(angle) * radius);
                int alpha = (int) ((hidden ? 190 : 110) * power);
                int markColor = (ring == 0) ? 0xFFFFF090 : ((ring == 1) ? 0xFFFF4400 : 0xFFB80800);
                g.fill(px, py, px + (mark % 2 == 0 ? 2 : 1), py + 1, (alpha << 24) | markColor);
            }
        }

        // 6. Sealed Magma Core Star & Dual Thermal Distortion Wave (when hidden)
        if (hidden) {
            for (int wIdx = 0; wIdx < 2; wIdx++) {
                float sweep = (float) (((time * 0.28 + wIdx * 0.5) % 1.0) * (h + w + 20) - 10);
                for (int row = 3; row < h - 3; row++) {
                    int sx = Math.round(sweep - row);
                    int left = Math.max(3, sx - 2), right = Math.min(w - 3, sx + 2);
                    if (right > left) {
                        int waveColor = wIdx == 0 ? 0xFFFFC040 : 0xFFFF2200;
                        g.fill(x + left, y + row, x + right, y + row + 1, ((int) (48 * power) << 24) | waveColor);
                    }
                }
            }

            float radius = 10 + 2.5f * breath;
            for (int i = 0; i < 16; i++) {
                double angle = time * 0.7 + i * Math.PI / 8.0;
                int px = Math.round(cx + (float) Math.cos(angle) * radius);
                int py = Math.round(cy + (float) Math.sin(angle) * radius);
                int starColor = (i % 2 == 0) ? 0xFFFFF080 : 0xFFFF2E08;
                g.fill(px, py, px + 1, py + 1, ((int) (210 * power) << 24) | starColor);
            }
            int coreA = (int) ((160 + 95 * breath) * power);
            g.fill((int) cx - 4, (int) cy, (int) cx + 5, (int) cy + 1, (coreA << 24) | 0xFFFFF2A0);
            g.fill((int) cx, (int) cy - 4, (int) cx + 1, (int) cy + 5, (coreA << 24) | 0xFFFFF2A0);
            g.fill((int) cx - 2, (int) cy - 2, (int) cx + 3, (int) cy + 3, (coreA << 24) | 0xFFFF4800);
        }

        // 7. Dense Shower of Volcanic Particles (40 particles - 2.5x more than Legend!)
        drawMythicParticles(g, x, y, time, power);
    }

    private static void drawMythicParticles(GuiGraphicsExtractor g, int x, int y, double time, float power) {
        int w = BM_CARD_W;
        int h = BM_CARD_H;

        // 1. Rising Molten Embers (12 particles) - surging upward with thermal wobble through the light column
        for (int i = 0; i < 12; i++) {
            float speed = 0.35f + (i % 4) * 0.08f;
            float t = (float) ((time * speed + i * 0.083) % 1.0);
            float wobble = (float) Math.sin(t * Math.PI * 4.0 + i * 1.7) * 2.2f;
            int px = (int) (x + (i * 3.8f) % (w + 4) - 2 + wobble);
            int py = y + h + 8 - (int) (t * (h + 65));
            int alpha = (int) (Math.sin(t * Math.PI) * 240 * power);
            int emberColor;
            if (t < 0.25f) {
                emberColor = 0xFFFFF6B8;
            } else if (t < 0.55f) {
                emberColor = 0xFFFF6E14;
            } else if (t < 0.82f) {
                emberColor = 0xFFFF1800;
            } else {
                emberColor = 0xFF8A0800;
            }
            if (alpha > 4) {
                g.fill(px, py, px + 1, py + (t < 0.4f ? 2 : 1), (alpha << 24) | emberColor);
            }
        }

        // 2. High-speed erratic sparks (10 particles) - shooting rapidly along flanks
        for (int i = 0; i < 10; i++) {
            float speed = 0.65f + (i % 3) * 0.18f;
            float t = (float) ((time * speed + i * 0.10) % 1.0);
            float jitter = (float) Math.sin(time * 18.0 + i * 4.0) * 1.5f;
            int px = (i % 2 == 0) ? x - 2 + Math.round(jitter) : x + w + 1 + Math.round(jitter);
            int py = y + h - (int) (t * (h + 12));
            int alpha = (int) (Math.sin(t * Math.PI) * 255 * power);
            int sparkColor = (i % 3 == 0) ? 0xFFFFF8D0 : ((i % 3 == 1) ? 0xFFFFB020 : 0xFFFF3300);
            if (alpha > 5) {
                g.fill(px, py, px + 1, py + 1, (alpha << 24) | sparkColor);
            }
        }

        // 3. Volcanic ash / soot flakes (6 particles) - slow, heavy atmospheric haze
        for (int i = 0; i < 6; i++) {
            float t = (float) ((time * 0.16 + i * 0.166) % 1.0);
            float drift = (float) Math.sin(t * Math.PI * 1.5 + i * 2.0) * 3.5f;
            int px = x + 4 + (i * 7) + Math.round(drift);
            int py = y + h - 2 - (int) (t * (h + 16));
            int alpha = (int) (Math.sin(t * Math.PI) * 160 * power);
            int ashColor = (i % 2 == 0) ? 0xFF6B1208 : 0xFF4A0A05;
            if (alpha > 5) {
                g.fill(px, py, px + 1, py + 1, (alpha << 24) | ashColor);
            }
        }

        // 4. Corner Magma Jet Eruptions (4 particles) - shooting outward diagonally from the 4 corners
        for (int c = 0; c < 4; c++) {
            float t = (float) ((time * 0.5 + c * 0.25) % 1.0);
            int cx = (c % 2 == 0) ? x : x + w - 1;
            int cy = (c < 2) ? y : y + h - 1;
            int dirX = (c % 2 == 0) ? -1 : 1;
            int dirY = (c < 2) ? -1 : 1;
            int px = cx + Math.round(dirX * t * 5.0f);
            int py = cy + Math.round(dirY * t * 5.0f);
            int alpha = (int) ((1.0f - t) * 220 * power);
            int jetColor = t < 0.3f ? 0xFFFFF0A0 : (t < 0.7f ? 0xFFFF5010 : 0xFFFF1100);
            if (alpha > 5) {
                g.fill(px, py, px + 1, py + 1, (alpha << 24) | jetColor);
            }
        }

        // 5. Light Shaft Diamonds (8 incandescent star motes drifting through the light column, Image 2 style)
        for (int i = 0; i < 8; i++) {
            float speed = 0.20f + (i % 4) * 0.05f;
            float t = (float) ((time * speed + i * 0.125) % 1.0);
            float sway = (float) Math.sin(t * Math.PI * 3.0 + i * 1.9) * 2.8f;
            int px = (int) (x + 8 + (i * 4.5f) + sway);
            int py = y + h + 6 - (int) (t * (h + 68));
            int alpha = (int) (Math.sin(t * Math.PI) * 255 * power);
            if (alpha > 8) {
                g.fill(px, py, px + 1, py + 1, (alpha << 24) | 0xFFFFFBE8);
                if (alpha > 120 && t > 0.25f && t < 0.75f) {
                    int haloA = (int) (alpha * 0.45f);
                    g.fill(px - 1, py, px, py + 1, (haloA << 24) | 0xFFFF7A18);
                    g.fill(px + 1, py, px + 2, py + 1, (haloA << 24) | 0xFFFF7A18);
                    g.fill(px, py - 1, px + 1, py, (haloA << 24) | 0xFFFF7A18);
                    g.fill(px, py + 1, px + 1, py + 2, (haloA << 24) | 0xFFFF7A18);
                }
            }
        }
    }

    private static void drawMythicLavaBorder(GuiGraphicsExtractor g, int x, int y, double time, float power, float breath, boolean hidden) {
        int w = BM_CARD_W - 1;
        int h = BM_CARD_H - 1;
        int perimeter = 2 * (w + h);

        int streamCount = 3;
        int streamLen = 30;
        double speed = time * 38.0 + Math.sin(time * 2.5) * 3.5;

        for (int s = 0; s < streamCount; s++) {
            int head = (int) (speed + s * (perimeter / (double) streamCount));
            for (int pos = 0; pos < streamLen; pos++) {
                int step = Math.floorMod(head - pos, perimeter);
                int px, py;
                if (step < w) {
                    px = step; py = 0;
                } else if (step < w + h) {
                    px = w; py = step - w;
                } else if (step < 2 * w + h) {
                    px = 2 * w + h - step; py = h;
                } else {
                    px = 0; py = perimeter - step;
                }

                int rgb;
                int alpha;

                if (pos < 2) {
                    rgb = 0xFFFFF0A8;
                    alpha = (int) (255 * power);
                } else if (pos < 6) {
                    rgb = 0xFFFFC033;
                    alpha = (int) (245 * power);
                } else if (pos < 14) {
                    float frac = (pos - 6) / 8f;
                    int gr = (int) (0x3A * (1 - frac) + 0x0E * frac);
                    rgb = (0xFF << 16) | (gr << 8);
                    alpha = (int) ((240 - frac * 30) * power);
                } else if (pos < 22) {
                    float frac = (pos - 14) / 8f;
                    int r = (int) (255 * (1 - frac) + 170 * frac);
                    rgb = (r << 16) | 0x0600;
                    alpha = (int) ((210 - frac * 60) * power);
                } else {
                    float frac = (pos - 22) / (float) (streamLen - 22);
                    int r = (int) (170 * (1 - frac) + 50 * frac);
                    rgb = (r << 16);
                    alpha = (int) ((140 * (1 - frac)) * power);
                }

                if (alpha > 5) {
                    g.fill(x + px, y + py, x + px + 1, y + py + 1, (alpha << 24) | (rgb & 0xFFFFFF));
                }
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
        if (deckTransitionActive()) return;
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
                VeloriaTooltip.removeDebugLines(tooltip, stack);
                long price = VBlackMarket.getDiscountedPrice(stack, syncedEpochDay, syncedResetSequence);
                int discount = VBlackMarket.getDiscountPercent(stack, syncedEpochDay, syncedResetSequence);

                tooltip.add(Component.empty());
                if (VBlackMarket.isRomanGodItem(stack)) {
                    tooltip.add(Component.translatable(VBlackMarket.isMythicItem(stack) ? "vcoins.mythic.header" : "vcoins.black_market.god_tooltip").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
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
            List<Component> slotTooltip = new ArrayList<>(this.getTooltipFromContainerItem(stack));
            VeloriaTooltip.removeDebugLines(slotTooltip, stack);
            extractor.setTooltipForNextFrame(this.font, slotTooltip, stack.getTooltipImage(), mouseX, mouseY,
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
        ItemStack relic = syncedCards.get(godPullCardIndex);
        if (VBlackMarket.isMythicItem(relic)) {
            renderMythicCinematic(g, relic);
        } else {
            renderLegendCinematic(g, relic);
        }
    }

    private void renderLegendCinematic(GuiGraphicsExtractor g, ItemStack relic) {
        long elapsed = System.currentTimeMillis() - godPullStartTime;
        playLegendScore(elapsed);
        float entrance = Math.clamp(elapsed / 700f, 0f, 1f);
        float settle = 1 - (float) Math.pow(1 - entrance, 3);
        float departure = Math.clamp((elapsed - 3600f) / 600f, 0f, 1f);
        float presence = settle * (1 - departure * departure);
        float envelope = Math.min(1, elapsed / 180f) * (1 - departure);

        // 1. Full-screen Cinematic Vignette & Deep Starlight Celestial Abyss
        g.fill(0, 0, this.width, this.height, ((int) (envelope * 220) << 24) | 0x03050C);
        g.blit(RenderPipelines.GUI_TEXTURED, CINEMATIC_VIGNETTE, 0, 0, 0.0f, 0.0f, this.width, this.height, 512, 512, 512, 512, ((int) (envelope * 255) << 24) | 0x00FFFFFF);

        // All geometry shares a viewport-aware stage; small GUI scales retain margins.
        float stage = Math.min(1f, Math.min(this.width / 360f, this.height / 280f));
        float sourceX = this.leftPos + BM_CARD_START_X + godPullCardIndex * BM_CARD_SPACING + BM_CARD_W / 2f;
        float sourceY = this.topPos + BM_CARD_Y + BM_CARD_H / 2;
        float cx = sourceX + (this.width / 2f - sourceX) * presence;
        float cy = sourceY + (this.height / 2f - 12 * stage - sourceY) * presence;

        // Cinematic Impact Screen Shake (700ms -> 1150ms)
        if (elapsed >= 700 && elapsed < 1150) {
            float shakeProgress = (elapsed - 700f) / 450f;
            float shakeAmp = (1.0f - shakeProgress) * 4.5f * stage;
            cx += (float) Math.sin(elapsed * 0.08) * shakeAmp;
            cy += (float) Math.cos(elapsed * 0.11) * shakeAmp * 0.7f;
        }

        float impact = Math.clamp((elapsed - 700f) / 420f, 0f, 1f);
        float rotation = elapsed * 0.00023f;
        int gold = 0xE8BC64;

        // 2. Towering Celestial Divine Light Pillar (matches table aura outside, magnified to full screen!)
        float beamCharge = elapsed < 700f ? (0.4f + 0.6f * entrance) : (1.0f - 0.25f * Math.clamp((elapsed - 700f) / 2900f, 0f, 1f));
        int beamWidth = Math.round((140 + 20 * (float) Math.sin(elapsed * 0.003)) * stage * (elapsed < 700f ? (0.6f + 0.4f * entrance) : 1f));
        int beamAlpha = (int) (envelope * beamCharge * 240);
        if (beamAlpha > 4) {
            g.blit(RenderPipelines.GUI_TEXTURED, CINEMATIC_BEAM_LEGEND,
                    (int) (cx - beamWidth / 2f), 0,
                    0.0f, 0.0f, beamWidth, this.height,
                    512, 512, 512, 512, (beamAlpha << 24) | 0x00FFFFFF);
        }

        // 3. Twelve Broad Celestial Translucent Rays (Phase 2 detonation)
        if (elapsed >= 700) {
            for (int ray = 0; ray < 12; ray++) {
                g.pose().pushMatrix();
                g.pose().translate(cx, cy);
                g.pose().rotate((float) (ray * Math.PI / 6 + rotation));
                int length = Math.round((90 + 40 * impact) * stage);
                int alpha = (int) (envelope * (1 - impact * 0.55f) * (ray % 2 == 0 ? 35 : 20));
                g.fill(-2, -length, 2, -28, (alpha << 24) | 0xFFD983);
                g.pose().popMatrix();
            }
        }

        // 4. Detonation Shockwaves & Anamorphic Sunburst Lens Flare (elapsed >= 700ms)
        if (elapsed >= 700) {
            // Relativistic Golden Shockwave Wave 1 (700ms -> 1550ms)
            float shock1 = Math.clamp((elapsed - 700f) / 850f, 0f, 1f);
            if (shock1 > 0 && shock1 < 1) {
                float shockEase1 = 1 - (1 - shock1) * (1 - shock1);
                int shockSize = Math.round((60 + shockEase1 * 260) * stage);
                int shockA = (int) (240 * (1 - shock1) * envelope);
                if (shockA > 4) {
                    g.blit(RenderPipelines.GUI_TEXTURED, CINEMATIC_SHOCKWAVE_LEGEND,
                            (int) (cx - shockSize / 2f), (int) (cy - shockSize / 2f),
                            0.0f, 0.0f, shockSize, shockSize,
                            512, 512, 512, 512, (shockA << 24) | 0x00FFFFFF);
                }
            }

            // Secondary Echo Shockwave (920ms -> 1800ms)
            float shock2 = Math.clamp((elapsed - 920f) / 880f, 0f, 1f);
            if (shock2 > 0 && shock2 < 1) {
                float shockEase2 = 1 - (1 - shock2) * (1 - shock2);
                int shockSize2 = Math.round((50 + shockEase2 * 220) * stage);
                int shockA2 = (int) (180 * (1 - shock2) * envelope);
                if (shockA2 > 4) {
                    g.blit(RenderPipelines.GUI_TEXTURED, CINEMATIC_SHOCKWAVE_LEGEND,
                            (int) (cx - shockSize2 / 2f), (int) (cy - shockSize2 / 2f),
                            0.0f, 0.0f, shockSize2, shockSize2,
                            512, 512, 512, 512, (shockA2 << 24) | 0x00FFFFFF);
                }
            }

            // Anamorphic Sunburst Lens Flare (700ms -> 1900ms)
            float burstProgress = Math.clamp((elapsed - 700f) / 1200f, 0f, 1f);
            if (burstProgress < 1f) {
                float burstEase = (float) Math.pow(1 - burstProgress, 2.0);
                int burstSize = Math.round((180 + (1 - burstEase) * 120) * stage);
                int burstAlpha = (int) (230 * burstEase * envelope);
                if (burstAlpha > 4) {
                    g.pose().pushMatrix();
                    g.pose().translate(cx, cy);
                    g.pose().rotate((float) (elapsed * 0.0003));
                    g.blit(RenderPipelines.GUI_TEXTURED, CINEMATIC_SUNBURST,
                            -burstSize / 2, -burstSize / 2,
                            0.0f, 0.0f, burstSize, burstSize,
                            512, 512, 512, 512, (burstAlpha << 24) | 0x00FFFFFF);
                    g.pose().popMatrix();

                    // Horizontal Anamorphic Flare Streak
                    int streakW = Math.round((120 + (1 - burstEase) * 220) * stage);
                    int streakH = Math.max(1, Math.round(2 * stage));
                    int streakA = (int) (burstAlpha * 0.85f);
                    g.fill((int) (cx - streakW), (int) (cy - streakH), (int) (cx + streakW), (int) (cy + streakH), (streakA << 24) | 0xFFF8DC);
                    g.fill((int) (cx - streakW / 2), (int) (cy - 1), (int) (cx + streakW / 2), (int) (cy + 1), (streakA << 24) | 0xFFFFFF);
                }
            }
        }

        // 5. Fixed-budget Orbital Rings & spoke markers
        for (int ring = 0; ring < 2; ring++) {
            float radius = (64 + ring * 15) * stage * presence;
            drawLegendRing(g, cx, cy, radius, (int) (envelope * 100), ring == 0 ? gold : 0x9D89CB);
            for (int spoke = 0; spoke < 12; spoke++) {
                double angle = spoke * Math.PI / 6 + rotation * (ring == 0 ? 1 : -0.65);
                float inner = radius - (spoke % 3 == 0 ? 5 : 2) * stage;
                drawLegendRay(g, cx, cy, angle, inner, radius, (int) (envelope * 190), gold);
            }
        }

        // 6. Inward sparks -> Outward burst of drifting embers
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

        // 7. Six ribbons turn into drifting gold fragments during victory hold
        if (elapsed >= 1050 && elapsed < 3000) {
            float progress = (elapsed - 1050) / 1950f;
            int alpha = (int) (170 * Math.sin(progress * Math.PI) * envelope);
            if (alpha > 0) {
                for (int ribbon = 0; ribbon < 6; ribbon++) {
                    float direction = ribbon % 2 == 0 ? -1 : 1;
                    float rx = cx + direction * (45 + progress * 50) * stage;
                    float ry = cy - 40 * stage + progress * 80 * stage + (ribbon / 2) * 15 * stage;
                    g.pose().pushMatrix();
                    g.pose().translate(rx, ry);
                    g.pose().rotate(direction * (progress * 3 + ribbon));
                    g.fill(-1, -4, 1, 4, (alpha << 24) | (ribbon % 3 == 0 ? 0xE9DCFF : 0xFFE2A0));
                    g.pose().popMatrix();
                }
            }
        }

        // 8. Card Scaling, Flip & Monumental 3D Floating Relic
        float punch = elapsed >= 700 && elapsed < 1120 ? (float) Math.sin(impact * Math.PI) * 0.55f : 0;
        float scale = 1 + presence * ((2.0f + punch) * stage);
        boolean sealed = elapsed < 700;

        // The Altar Card Frame
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        float flip = sealed ? Math.clamp((elapsed - 520f) / 180f, 0f, 1f) : 1.0f;
        float faceScale = sealed ? Math.max(0.025f, (float) Math.cos(flip * Math.PI * 0.5)) : 1.0f;
        g.pose().scale(scale * faceScale, scale);
        drawCardOutline(g, -BM_CARD_W / 2 - 1, -BM_CARD_H / 2 - 1, BM_CARD_W + 2, BM_CARD_H + 2, 0xFFE8BC64);
        drawCardTexture(g, sealed ? CARD_LEGEND_BACK : CARD_LEGEND_FRONT, -BM_CARD_W / 2, -BM_CARD_H / 2, BM_CARD_W, BM_CARD_H);
        g.pose().popMatrix();

        // 3D Monumental Floating Relic (in front of card, free from 2D squash!)
        if (!sealed) {
            float itemImpact = Math.clamp((elapsed - 700f) / 450f, 0f, 1f);
            float itemPunch = (1.0f - itemImpact) * 0.70f;
            float itemScale = (2.4f + itemPunch) * stage;
            float itemBob = (float) Math.sin(elapsed * 0.0035) * 4.5f * stage;

            // Radiant Divine Bloom Halo directly behind the weapon
            int haloSize = Math.round(110 * stage);
            int haloA = (int) (envelope * (0.85f + 0.15f * (float) Math.sin(elapsed * 0.004)) * 220);
            g.blit(RenderPipelines.GUI_TEXTURED, AURA_HALO_LEGEND,
                    (int) (cx - haloSize / 2f), (int) (cy + itemBob - haloSize / 2f),
                    0.0f, 0.0f, haloSize, haloSize,
                    256, 256, 256, 256, (haloA << 24) | 0x00FFFFFF);

            // Floating 3D Item Icon
            g.pose().pushMatrix();
            g.pose().translate(cx, cy + itemBob);
            g.pose().scale(itemScale, itemScale);
            g.item(relic, -8, -8);
            g.pose().popMatrix();

            // Celestial Stardust orbiting the weapon
            for (int m = 0; m < 6; m++) {
                double mAngle = elapsed * 0.002 + m * Math.PI / 3.0;
                float mDist = (26 + (m % 2) * 8) * stage;
                int mx = Math.round(cx + (float) Math.cos(mAngle) * mDist);
                int my = Math.round(cy + itemBob + (float) Math.sin(mAngle) * mDist * 0.75f);
                int mA = (int) (envelope * (140 + 80 * Math.sin(elapsed * 0.005 + m)));
                int mColor = m % 2 == 0 ? 0xFFFFF2D0 : 0xFFFFD700;
                g.fill(mx - 1, my - 1, mx + 1, my + 1, (mA << 24) | mColor);
            }
        }

        // 9. Letter-spaced title and item name
        float titleProgress = Math.clamp((elapsed - 700f) / 350f, 0f, 1f);
        float textFade = Math.clamp((elapsed - 700f) / 90f, 0f, 1f) * (1 - departure);
        int textAlpha = (int) (textFade * 255);
        if (textAlpha > 4) {
            int titleY = Math.round(this.height / 2f - 105 * stage);
            Component title = Component.translatable("vcoins.roman.reveal.title").withStyle(ChatFormatting.BOLD);
            float titleScale = Math.min(1.65f + 0.6f * (1 - titleProgress) * (1 - titleProgress),
                    (this.width - 24f) / Math.max(1, this.font.width(title)));
            g.pose().pushMatrix();
            g.pose().translate(this.width / 2f, titleY);
            g.pose().scale(titleScale, titleScale);
            g.centeredText(this.font, title, 1, 2, (textAlpha << 24) | 0x6E3918);
            g.centeredText(this.font, title, 0, 0, (textAlpha << 24) | 0xFFE0A0);
            g.pose().popMatrix();
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

    private void renderMythicCinematic(GuiGraphicsExtractor g, ItemStack relic) {
        long elapsed = System.currentTimeMillis() - godPullStartTime;
        playMythicScore(elapsed);
        float entrance = Math.clamp(elapsed / 700f, 0f, 1f);
        float settle = 1 - (float) Math.pow(1 - entrance, 3);
        float departure = Math.clamp((elapsed - 3600f) / 600f, 0f, 1f);
        float presence = settle * (1 - departure * departure);
        float envelope = Math.min(1, elapsed / 180f) * (1 - departure);

        // 1. Full-screen Cinematic Vignette over Deep Abyssal Void
        g.fill(0, 0, this.width, this.height, ((int) (envelope * 230) << 24) | 0x070104);
        g.blit(RenderPipelines.GUI_TEXTURED, CINEMATIC_VIGNETTE, 0, 0, 0.0f, 0.0f, this.width, this.height, 512, 512, 512, 512, ((int) (envelope * 255) << 24) | 0x00FFFFFF);

        float stage = Math.min(1f, Math.min(this.width / 360f, this.height / 280f));
        float sourceX = this.leftPos + BM_CARD_START_X + godPullCardIndex * BM_CARD_SPACING + BM_CARD_W / 2f;
        float sourceY = this.topPos + BM_CARD_Y + BM_CARD_H / 2;
        float cx = sourceX + (this.width / 2f - sourceX) * presence;
        float cy = sourceY + (this.height / 2f - 14 * stage - sourceY) * presence;

        // Gravitational micro-jitter right before detonation (520ms -> 700ms)
        if (elapsed >= 520 && elapsed < 700) {
            float tension = (elapsed - 520f) / 180f;
            float shake = (float) Math.sin(elapsed * 0.15) * tension * 2.2f * stage;
            cx += shake;
            cy += (float) Math.cos(elapsed * 0.17) * tension * 1.5f * stage;
        }

        // Relativistic Seismic Screen Shake during supernova detonation (700ms -> 1250ms)
        if (elapsed >= 700 && elapsed < 1250) {
            float shakeProgress = 1.0f - (elapsed - 700f) / 550f;
            float shakeMagnitude = shakeProgress * 6.5f * stage;
            cx += (float) (Math.sin(elapsed * 0.11) * shakeMagnitude);
            cy += (float) (Math.cos(elapsed * 0.13) * shakeMagnitude * 0.8f);
        }

        float impact = Math.clamp((elapsed - 700f) / 420f, 0f, 1f);
        float rotation = elapsed * 0.00035f;

        // 2. Towering Full-Height Volcanic Plasma Light Pillar (Shooting across entire screen!)
        float beamCharge = elapsed < 700f ? (0.45f + 0.55f * entrance) : (1.0f - 0.20f * Math.clamp((elapsed - 700f) / 2900f, 0f, 1f));
        int beamWidth = Math.round((155 + 24 * (float) Math.sin(elapsed * 0.0035)) * stage * (elapsed < 700f ? (0.55f + 0.45f * entrance) : 1f));
        int beamAlpha = (int) (envelope * beamCharge * 245);
        if (beamAlpha > 4) {
            g.blit(RenderPipelines.GUI_TEXTURED, CINEMATIC_BEAM_MYTHIC,
                    (int) (cx - beamWidth / 2f), 0,
                    0.0f, 0.0f, beamWidth, this.height,
                    512, 512, 512, 512, (beamAlpha << 24) | 0x00FFFFFF);
        }

        // 3. PHASE 1: SINGULARITY (0 -> 700ms) - Inward Cosmic Starlight Spiral
        if (elapsed < 700) {
            float compress = 1 - entrance;
            for (int k = 0; k < 12; k++) {
                double spiralAngle = k * (Math.PI / 6) + entrance * 3.8;
                float dist = (30 + compress * 140) * stage;
                float sx = cx + (float) Math.cos(spiralAngle) * dist;
                float sy = cy + (float) Math.sin(spiralAngle) * dist * 0.85f;
                int alpha = (int) (envelope * (0.3f + 0.7f * entrance) * 220);
                int color = (alpha << 24) | (k % 2 == 0 ? 0x63F4FF : 0xEA94FF);
                g.fill((int) sx - 1, (int) sy - 1, (int) sx + 2, (int) sy + 2, color);
            }
        }

        // 4. PHASE 2: SUPERNOVA DETONATION (elapsed >= 700ms)
        if (elapsed >= 700) {
            // Relativistic Volcanic Shockwave 1 (700ms -> 1550ms)
            float wave1 = Math.clamp((elapsed - 700f) / 850f, 0f, 1f);
            if (wave1 > 0 && wave1 < 1) {
                float eased1 = 1 - (1 - wave1) * (1 - wave1);
                int shockSize1 = Math.round((70 + eased1 * 280) * stage);
                int shockA1 = (int) (245 * (1 - wave1) * envelope);
                if (shockA1 > 4) {
                    g.blit(RenderPipelines.GUI_TEXTURED, CINEMATIC_SHOCKWAVE_MYTHIC,
                            (int) (cx - shockSize1 / 2f), (int) (cy - shockSize1 / 2f),
                            0.0f, 0.0f, shockSize1, shockSize1,
                            512, 512, 512, 512, (shockA1 << 24) | 0x00FFFFFF);
                }
            }

            // Secondary Echo Shockwave (880ms -> 1800ms)
            float wave2 = Math.clamp((elapsed - 880f) / 920f, 0f, 1f);
            if (wave2 > 0 && wave2 < 1) {
                float eased2 = 1 - (1 - wave2) * (1 - wave2);
                int shockSize2 = Math.round((55 + eased2 * 230) * stage);
                int shockA2 = (int) (190 * (1 - wave2) * envelope);
                if (shockA2 > 4) {
                    g.blit(RenderPipelines.GUI_TEXTURED, CINEMATIC_SHOCKWAVE_MYTHIC,
                            (int) (cx - shockSize2 / 2f), (int) (cy - shockSize2 / 2f),
                            0.0f, 0.0f, shockSize2, shockSize2,
                            512, 512, 512, 512, (shockA2 << 24) | 0x00FFFFFF);
                }
            }

            // Anamorphic Sunburst Lens Flare (700ms -> 2000ms)
            float flareProgress = Math.clamp((elapsed - 700f) / 1300f, 0f, 1f);
            if (flareProgress < 1f) {
                float flareEase = (float) Math.pow(1 - flareProgress, 2.0);
                int sunburstSize = Math.round((200 + (1 - flareEase) * 140) * stage);
                int flareAlpha = (int) (240 * flareEase * envelope);
                if (flareAlpha > 4) {
                    g.pose().pushMatrix();
                    g.pose().translate(cx, cy);
                    g.pose().rotate((float) (elapsed * 0.00035));
                    g.blit(RenderPipelines.GUI_TEXTURED, CINEMATIC_SUNBURST,
                            -sunburstSize / 2, -sunburstSize / 2,
                            0.0f, 0.0f, sunburstSize, sunburstSize,
                            512, 512, 512, 512, (flareAlpha << 24) | 0x00FFFFFF);
                    g.pose().popMatrix();

                    // Dual Anamorphic Laser Flare Streaks (Electric Cyan & Pure Starlight)
                    int hReach = Math.round((140 + (1 - flareEase) * 240) * stage);
                    int vReach = Math.max(1, Math.round(2 * stage));
                    int streakA = (int) (flareAlpha * 0.85f);
                    g.fill((int) (cx - hReach), (int) (cy - vReach), (int) (cx + hReach), (int) (cy + vReach), (streakA << 24) | 0x63F4FF);
                    g.fill((int) (cx - hReach / 2), (int) (cy - 1), (int) (cx + hReach / 2), (int) (cy + 1), (streakA << 24) | 0xFFFFFF);
                    int vStreakReach = Math.round((80 + (1 - flareEase) * 140) * stage);
                    g.fill((int) (cx - 1), (int) (cy - vStreakReach), (int) (cx + 1), (int) (cy + vStreakReach), ((streakA / 2) << 24) | 0xE285FF);
                }
            }

            // 8 Rotating Celestial Prismatic Light Blades
            float beamFade = 1 - Math.clamp((elapsed - 700f) / 2600f, 0f, 1f);
            if (beamFade > 0) {
                int bladeAlpha = (int) (envelope * beamFade * (55 + 25 * (1 - impact)));
                int bladeLen = Math.round((95 + 45 * impact) * stage);
                for (int ray = 0; ray < 8; ray++) {
                    g.pose().pushMatrix();
                    g.pose().translate(cx, cy);
                    g.pose().rotate((float) (ray * Math.PI / 4 + rotation));
                    int col = ray % 2 == 0 ? 0x6DF6FF : 0xE685FF;
                    // Tapered celestial beam
                    g.fill(-2, -bladeLen, 2, -26, (bladeAlpha << 24) | col);
                    g.fill(-1, -bladeLen - 12, 1, -bladeLen, ((bladeAlpha / 2) << 24) | 0xFFFFFF);
                    g.pose().popMatrix();
                }
            }
        }

        // 5. CONCENTRIC SACRED GEOMETRY (Dual Runic Rings & Octagram Nodes)
        float ringRadius1 = (68 + 18 * (float) Math.sin(elapsed * 0.0018)) * stage * presence;
        drawMythicRing(g, cx, cy, ringRadius1, (int) (envelope * 110), 0x5CE7FF);

        float ringRadius2 = (52 + 10 * (float) Math.cos(elapsed * 0.0022)) * stage * presence;
        drawMythicRing(g, cx, cy, ringRadius2, (int) (envelope * 95), 0xD577FF);

        for (int node = 0; node < 8; node++) {
            double nodeAngle = node * Math.PI / 4 + rotation;
            float nx = cx + (float) Math.cos(nodeAngle) * ringRadius1;
            float ny = cy + (float) Math.sin(nodeAngle) * ringRadius1;
            int nAlpha = (int) (envelope * 180);
            int nColor = (nAlpha << 24) | (node % 2 == 0 ? 0xFFFFFF : 0x76F6FF);
            g.fill((int) nx - 1, (int) ny - 1, (int) nx + 2, (int) ny + 2, nColor);
        }

        // 6. ORBITING ASTRAL CRYSTAL SHARDS (8 Floating 3D Prismatic Diamonds)
        if (elapsed >= 900 && elapsed < 3500) {
            float shardProgress = (elapsed - 900f) / 2600f;
            float shardFade = (float) Math.sin(shardProgress * Math.PI) * envelope;
            int shardAlpha = (int) (210 * shardFade);
            if (shardAlpha > 0) {
                for (int s = 0; s < 8; s++) {
                    double orbitAngle = s * (Math.PI / 4) + elapsed * 0.0009;
                    float rx = (72 + (s % 2) * 22) * stage;
                    float ry = (40 + (s % 2) * 14) * stage;
                    float sx = cx + (float) Math.cos(orbitAngle) * rx;
                    float sy = cy + (float) Math.sin(orbitAngle) * ry + (float) Math.sin(elapsed * 0.0035 + s * 1.5) * 6 * stage;

                    g.pose().pushMatrix();
                    g.pose().translate(sx, sy);
                    g.pose().rotate((float) (orbitAngle + elapsed * 0.003));
                    int bodyColor = (shardAlpha << 24) | (s % 2 == 0 ? 0x63F4FF : 0xF2B5FF);
                    int coreColor = (shardAlpha << 24) | 0xFFFFFF;
                    g.fill(-2, 0, 3, 1, coreColor);
                    g.fill(0, -2, 1, 3, coreColor);
                    g.fill(-1, -1, 2, 2, bodyColor);
                    g.pose().popMatrix();
                }
            }
        }

        // 7. Card Scaling, Flip & Monumental 3D Floating Relic
        float punch = elapsed >= 700 && elapsed < 1180 ? (float) Math.sin(impact * Math.PI) * 0.68f : 0;
        float scale = 1 + presence * ((2.15f + punch) * stage);
        boolean sealed = elapsed < 700;

        // The Altar Card Frame
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        float flip = sealed ? Math.clamp((elapsed - 500f) / 200f, 0f, 1f) : 1.0f;
        float faceScale = sealed ? Math.max(0.025f, (float) Math.cos(flip * Math.PI * 0.5)) : 1.0f;
        g.pose().scale(scale * faceScale, scale);

        if (elapsed >= 520 && elapsed < 700) {
            int ghostA = (int) (((elapsed - 520) / 180f) * 60);
            drawCardOutline(g, -BM_CARD_W / 2 - 3, -BM_CARD_H / 2 - 1, BM_CARD_W + 2, BM_CARD_H + 2, (ghostA << 24) | 0x00F0FF);
            drawCardOutline(g, -BM_CARD_W / 2 + 1, -BM_CARD_H / 2 - 1, BM_CARD_W + 2, BM_CARD_H + 2, (ghostA << 24) | 0xFF00D4);
        }

        drawCardOutline(g, -BM_CARD_W / 2 - 1, -BM_CARD_H / 2 - 1, BM_CARD_W + 2, BM_CARD_H + 2, 0xFFFF2E14);
        drawCardTexture(g, sealed ? CARD_MYTHIC_BACK : CARD_MYTHIC_FRONT, -BM_CARD_W / 2, -BM_CARD_H / 2, BM_CARD_W, BM_CARD_H);
        g.pose().popMatrix();

        // 3D Monumental Floating Relic (in front of card, free from 2D squash!)
        if (!sealed) {
            float itemImpact = Math.clamp((elapsed - 700f) / 450f, 0f, 1f);
            float itemPunch = (1.0f - itemImpact) * 0.85f;
            float itemScale = (2.6f + itemPunch) * stage;
            float itemBob = (float) Math.sin(elapsed * 0.0038) * 5.0f * stage;

            // Radiant Mythic Supernova Bloom Halo directly behind the weapon
            int haloSize = Math.round(130 * stage);
            int haloA = (int) (envelope * (0.85f + 0.15f * (float) Math.sin(elapsed * 0.004)) * 240);
            g.blit(RenderPipelines.GUI_TEXTURED, AURA_HALO_MYTHIC,
                    (int) (cx - haloSize / 2f), (int) (cy + itemBob - haloSize / 2f),
                    0.0f, 0.0f, haloSize, haloSize,
                    256, 256, 256, 256, (haloA << 24) | 0x00FFFFFF);

            // Floating 3D Item Icon
            g.pose().pushMatrix();
            g.pose().translate(cx, cy + itemBob);
            g.pose().scale(itemScale, itemScale);
            g.item(relic, -8, -8);
            g.pose().popMatrix();

            // Orbiting Volcanic Embers & Supernova Stardust
            for (int m = 0; m < 8; m++) {
                double mAngle = elapsed * 0.0024 + m * Math.PI / 4.0;
                float mDist = (30 + (m % 2) * 10) * stage;
                int mx = Math.round(cx + (float) Math.cos(mAngle) * mDist);
                int my = Math.round(cy + itemBob + (float) Math.sin(mAngle) * mDist * 0.75f);
                int mA = (int) (envelope * (160 + 80 * Math.sin(elapsed * 0.006 + m)));
                int mColor = (m % 2 == 0) ? 0xFF63F4FF : 0xFFFF3B14;
                g.fill(mx - 1, my - 1, mx + 2, my + 2, (mA << 24) | mColor);
            }
        }

        // 8. TRANSCENDENT TYPOGRAPHY & MULTI-LAYER CHROMATIC TITLE
        float titleProgress = Math.clamp((elapsed - 700f) / 350f, 0f, 1f);
        float textFade = Math.clamp((elapsed - 700f) / 90f, 0f, 1f) * (1 - departure);
        int textAlpha = (int) (textFade * 255);
        if (textAlpha > 4) {
            int titleY = Math.round(this.height / 2f - 108 * stage);
            Component title = Component.translatable("vcoins.mythic.reveal").withStyle(ChatFormatting.BOLD);
            float titleScale = Math.min(1.85f + 0.75f * (1 - titleProgress) * (1 - titleProgress),
                    (this.width - 24f) / Math.max(1, this.font.width(title)));
            g.pose().pushMatrix();
            g.pose().translate(this.width / 2f, titleY);
            g.pose().scale(titleScale, titleScale);
            g.centeredText(this.font, title, 2, 2, (textAlpha << 24) | 0x240438);
            g.centeredText(this.font, title, -1, 0, ((int) (textAlpha * 0.65f) << 24) | 0x00E5FF);
            g.centeredText(this.font, title, 0, 0, (textAlpha << 24) | 0xFFEDFF);
            g.pose().popMatrix();

            int nameY = Math.round(this.height / 2f + 78 * stage);
            String rawName = relic.getHoverName().getString();
            int maxNameWidth = Math.min(270, this.width - 24);
            String name = this.font.width(rawName) > maxNameWidth
                    ? this.font.plainSubstrByWidth(rawName, maxNameWidth - this.font.width("…")) + "…" : rawName;
            g.centeredText(this.font, Component.literal("✧ " + name + " ✧"), this.width / 2, nameY, (textAlpha << 24) | 0xFFF0BD);
            g.centeredText(this.font, Component.translatable("vcoins.mythic.header"),
                    this.width / 2, nameY + 13, ((int) (textFade * 210) << 24) | 0xD296FF);
            g.centeredText(this.font, Component.translatable("vcoins.roman.reveal.dismiss"),
                    this.width / 2, nameY + 26, ((int) (textFade * 150) << 24) | 0x9D95B3);
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

    private static final int LEGEND_RING_POINTS = 24;
    private static final float[] LEGEND_RING_X = new float[LEGEND_RING_POINTS];
    private static final float[] LEGEND_RING_Y = new float[LEGEND_RING_POINTS];
    private static final float[] LEGEND_SEGMENT_ANGLE = new float[LEGEND_RING_POINTS];
    static {
        for (int i = 0; i < LEGEND_RING_POINTS; i++) {
            double angle = i * Math.PI * 2 / LEGEND_RING_POINTS;
            LEGEND_RING_X[i] = (float) Math.cos(angle);
            LEGEND_RING_Y[i] = (float) Math.sin(angle);
            LEGEND_SEGMENT_ANGLE[i] = (float) (angle + Math.PI / LEGEND_RING_POINTS + Math.PI / 2);
        }
    }

    private static void drawLegendRing(GuiGraphicsExtractor g, float cx, float cy, float radius, int alpha, int rgb) {
        if (alpha <= 0 || radius < 1) return;
        // 24 connected segments replace 48 isolated points: fuller ring, half the quads.
        int length = Math.max(1, (int) Math.ceil(2 * radius * Math.sin(Math.PI / LEGEND_RING_POINTS)));
        for (int i = 0; i < LEGEND_RING_POINTS; i++) {
            float px = cx + LEGEND_RING_X[i] * radius;
            float py = cy + LEGEND_RING_Y[i] * radius;
            g.pose().pushMatrix();
            g.pose().translate(px, py);
            g.pose().rotate(LEGEND_SEGMENT_ANGLE[i]);
            g.fill(0, 0, length, 1, (alpha << 24) | rgb);
            g.pose().popMatrix();
        }
    }

    private static final int MYTHIC_RING_POINTS = 32;
    private static final float[] MYTHIC_RING_X = new float[MYTHIC_RING_POINTS];
    private static final float[] MYTHIC_RING_Y = new float[MYTHIC_RING_POINTS];
    private static final float[] MYTHIC_SEGMENT_ANGLE = new float[MYTHIC_RING_POINTS];
    static {
        for (int i = 0; i < MYTHIC_RING_POINTS; i++) {
            double angle = i * Math.PI * 2 / MYTHIC_RING_POINTS;
            MYTHIC_RING_X[i] = (float) Math.cos(angle);
            MYTHIC_RING_Y[i] = (float) Math.sin(angle);
            MYTHIC_SEGMENT_ANGLE[i] = (float) (angle + Math.PI / MYTHIC_RING_POINTS + Math.PI / 2);
        }
    }

    private static void drawMythicRing(GuiGraphicsExtractor g, float cx, float cy, float radius, int alpha, int rgb) {
        if (alpha <= 0 || radius < 1) return;
        int length = Math.max(1, (int) Math.ceil(2 * radius * Math.sin(Math.PI / MYTHIC_RING_POINTS)));
        for (int i = 0; i < MYTHIC_RING_POINTS; i++) {
            float px = cx + MYTHIC_RING_X[i] * radius;
            float py = cy + MYTHIC_RING_Y[i] * radius;
            g.pose().pushMatrix();
            g.pose().translate(px, py);
            g.pose().rotate(MYTHIC_SEGMENT_ANGLE[i]);
            g.fill(0, 0, length, 1, (alpha << 24) | rgb);
            g.pose().popMatrix();
        }
    }

    private void spawnCardBurst(int cx, int cy) {
        spawnCardBurst(cx, cy, ItemStack.EMPTY);
    }

    private void spawnCardBurst(int cx, int cy, ItemStack stack) {
        if (VCoinsPurchaseConfirm.isReducedMotion()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int[] colors;
        int count;
        if (VBlackMarket.isMythicItem(stack)) {
            colors = new int[]{0xFFFF2E08, 0xFFFFF090, 0xFFFF6E14, 0xFF63F4FF, 0xFFFFFFFF, 0xFFFF1800};
            count = 28;
        } else if (VBlackMarket.isRomanGodItem(stack)) {
            colors = new int[]{0xFFFFD700, 0xFFFFA000, 0xFFFFFFFF, 0xFFFFE2A0, 0xFFFFE082};
            count = 24;
        } else if ("epic".equals(VBlackMarket.getCardTier(stack)) || (!stack.isEmpty() && stack.getRarity() == net.minecraft.world.item.Rarity.EPIC)) {
            colors = new int[]{0xFFBC65FF, 0xFFE9DCFF, 0xFFD477FF, 0xFFFFFFFF, 0xFF8A2BE2, 0xFFF090FF};
            count = 22;
        } else if ("exclusive".equals(VBlackMarket.getCardTier(stack)) || (!stack.isEmpty() && stack.getRarity() == net.minecraft.world.item.Rarity.RARE)) {
            colors = new int[]{0xFF35E8FF, 0xFF81D4FA, 0xFFE0F7FA, 0xFFFFFFFF, 0xFF00B0FF};
            count = 18;
        } else {
            colors = new int[]{0xFFFFD700, 0xFFFFA000, 0xFFFFFFFF, 0xFFFFE082, 0xFF81D4FA};
            count = 14;
        }
        for (int p = 0; p < count; p++) {
            float angle = (float) (r.nextDouble() * TWO_PI);
            float speed = 0.6f + r.nextFloat() * 2.2f;
            addParticle(cx, cy, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed - 0.35f,
                    colors[r.nextInt(colors.length)], r.nextInt(PARTICLE_SYMBOLS.length), 450 + r.nextInt(450));
        }
    }

    private void triggerGodPullCelebration(int cx, int cy, ItemStack stack) {
        legendSoundStage = 0;
        mythicSoundStage = 0;
        if (this.minecraft != null) {
            if (VBlackMarket.isMythicItem(stack)) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.65f, 0.85f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.END_PORTAL_SPAWN, 1.6f, 0.45f));
            } else {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65f, 0.45f));
            }
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
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 0.85f, 0.55f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.7f));
            }
        }
        if (legendSoundStage < 3 && elapsed >= 1350) {
            legendSoundStage = 3;
            if (elapsed < 1800) this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.65f, 0.35f));
        }
    }

    private void playMythicScore(long elapsed) {
        if (this.minecraft == null) return;
        if (mythicSoundStage < 1 && elapsed >= 350) {
            mythicSoundStage = 1;
            if (elapsed < 650) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 1.45f, 0.6f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.55f, 0.7f));
            }
        }
        if (mythicSoundStage < 2 && elapsed >= 700) {
            mythicSoundStage = 2;
            if (elapsed < 1150) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.45f, 0.65f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 0.80f, 0.90f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.95f, 0.85f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.25f, 0.85f));
            }
        }
        if (mythicSoundStage < 3 && elapsed >= 1150) {
            mythicSoundStage = 3;
            if (elapsed < 1600) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.65f, 0.70f));
            }
        }
        if (mythicSoundStage < 4 && elapsed >= 1700) {
            mythicSoundStage = 4;
            if (elapsed < 2200) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_RESONATE, 1.75f, 0.60f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.90f, 0.50f));
            }
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
