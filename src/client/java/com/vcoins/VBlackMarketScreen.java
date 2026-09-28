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

    private static final Identifier AURA_HALO_MYTHIC      = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/aura_halo_mythic.png");
    private static final Identifier AURA_HALO_LEGEND      = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/aura_halo_legend.png");

    // High-poly card hover glow textures (tiered)
    private static final Identifier CARD_HOVER_MYTHIC  = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_hover_mythic.png");
    private static final Identifier CARD_HOVER_LEGEND  = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_hover_legend.png");
    private static final Identifier CARD_HOVER_REGULAR = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_hover_regular.png");


    private static final Identifier PARTICLE_GLOW_ORB    = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/particle_glow_orb.png");

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
    private static final int BM_CARDS_PER_ROW = 5;
    private static final int BM_ROW_GAP      = 26;
    private static final int BM_CARD_START_X = (VBlackMarketScreenHandler.MENU_W - (4 * BM_CARD_SPACING + BM_CARD_W)) / 2;
    private static final int BM_CARD_Y       = 58;
    private static final long BM_FLIP_MS     = 480L;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final long VFX_EPOCH_NANOS = System.nanoTime();

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

    public static void clearClientSync() {
        baseSecondsRemaining = 86400L;
        lastSyncTimeMs = System.currentTimeMillis();
        syncedRevealedMask = 0;
        syncedPurchasedMask = 0;
        syncedEpochDay = 0L;
        syncedBankedResets = 0;
        syncedResetSequence = 0;
        syncedLifetimeFlipCount = 0;
        syncedCards = new ArrayList<>();
    }

    public static void handleSyncPayload(BlackMarketSyncPayload payload) {
        var client = net.minecraft.client.Minecraft.getInstance();
        // Deck transition triggers when epoch day or reset sequence changes.
        // Also trigger when the server clears revealedMask to 0 (e.g. admin reset
        // that keeps the same sequence) while the client previously had reveals,
        // so localRevealedCards gets flushed and the new card batch (with any
        // injected legend) becomes flippable.
        boolean serverClearedReveals = payload.revealedMask() == 0 && syncedRevealedMask != 0;
        if (client != null && client.gui != null
                && client.gui.screen() instanceof VBlackMarketScreen screen
                && !syncedCards.isEmpty()
                && (payload.epochDay() != syncedEpochDay
                        || payload.resetSequence() != syncedResetSequence
                        || serverClearedReveals)) {
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
        // Safety-net: if the server has reset revealed cards to 0 but we still hold
        // local flip state (e.g. from an admin reset that didn't change the sequence),
        // clear the stale local state so the freshly-dealt cards (including any
        // injected legend guarantee) can actually be flipped.
        if (syncedRevealedMask == 0 && !localRevealedCards.isEmpty()) {
            localRevealedCards.clear();
            Arrays.fill(flipStartTime, 0L);
            Arrays.fill(hasSpawnedStartParticles, false);
            Arrays.fill(hasSpawnedMidParticles, false);
            Arrays.fill(hasSpawnedFinishParticles, false);
        }
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
        for (int i = 0; i < VBlackMarket.DAILY_ITEM_COUNT; i++) {
            boolean outgoing = elapsed < 350;
            float t = outgoing ? Math.clamp((elapsed - i * 15f) / 240f, 0f, 1f)
                    : Math.clamp((elapsed - 350 - i * 40f) / 300f, 0f, 1f);
            if ((outgoing && t >= 1) || (!outgoing && t <= 0)) continue;
            float eased = t * t * (3 - 2 * t);
            float size = outgoing ? 1 - eased : eased;
            int col = i % BM_CARDS_PER_ROW;
            int row = i / BM_CARDS_PER_ROW;
            float homeX = this.leftPos + BM_CARD_START_X + col * BM_CARD_SPACING + BM_CARD_W / 2f;
            float homeY = this.topPos + BM_CARD_Y + row * (BM_CARD_H + BM_ROW_GAP) + BM_CARD_H / 2f;
            float centerX = this.leftPos + this.imageWidth / 2f;
            float centerY = this.topPos + BM_CARD_Y + BM_CARD_H + BM_ROW_GAP / 2f + BM_CARD_H / 2f;
            float cx = outgoing ? homeX + (centerX - homeX) * eased : centerX + (homeX - centerX) * eased;
            float cy = outgoing ? homeY + (centerY - homeY) * eased : centerY + (homeY - centerY) * eased;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().rotate((1 - size) * (col - 2) * 0.08f);
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
    private final long[] flipStartTime = new long[10];
    private final boolean[] hasSpawnedStartParticles = new boolean[10];
    private final boolean[] hasSpawnedMidParticles = new boolean[10];
    private final boolean[] hasSpawnedFinishParticles = new boolean[10];
    private final float[] cardHoverProgress = new float[10];
    private int lastHoveredCardIndex = -1;

    // Particle pool (expanded for legendary god pull fireworks)
    private static final int MAX_PARTICLES = 256;
    private static final String[] PARTICLE_SYMBOLS = {"✦", "✧", "⋆", "★", "⚡", "☼", "⚜", "👑", "•", "ᛟ", "ᚱ", "◇"};
    private long godPullStartTime = 0L;
    private int godPullCardIndex = -1;
    private int legendSoundStage;
    private int mythicSoundStage;
    private static final long GOD_CINEMATIC_DURATION_MS = 5200L;
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
                .bounds(this.leftPos + 318, this.topPos + 248, 16, 12)
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
                .bounds(this.leftPos + 40, this.topPos + 228, 80, 16)
                .build());

        this.blackMarketTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.tab.black_market"),
                button -> {})
                .bounds(this.leftPos + 131, this.topPos + 228, 92, 16)
                .build());

        this.duplicateTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.duplicate.open"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    ClientPlayNetworking.send(new OpenDuplicatePayload());
                })
                .bounds(this.leftPos + 234, this.topPos + 228, 80, 16)
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
            for (int i = 0; i < VBlackMarket.DAILY_ITEM_COUNT; i++) {
                if (((syncedRevealedMask & (1 << i)) != 0) || localRevealedCards.contains(i)) {
                    count++;
                }
            }
            this.bmRevealAllButton.active = (count < VBlackMarket.DAILY_ITEM_COUNT) && !deckTransitionActive();
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
        for (int i = 0; i < VBlackMarket.DAILY_ITEM_COUNT; i++) {
            boolean isRevealed = ((syncedRevealedMask & (1 << i)) != 0) || localRevealedCards.contains(i);
            if (!isRevealed) {
                localRevealedCards.add(i);
                flipStartTime[i] = now + i * 50L;
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
                int cardCol = card % BM_CARDS_PER_ROW;
                int cardRow = card / BM_CARDS_PER_ROW;
                triggerGodPullCelebration(
                        this.leftPos + BM_CARD_START_X + cardCol * BM_CARD_SPACING + BM_CARD_W / 2,
                        this.topPos + BM_CARD_Y + cardRow * (BM_CARD_H + BM_ROW_GAP) + BM_CARD_H / 2,
                        clickedStack);
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
        // One purchase delivers the complete displayed card quantity.
        if (!VBlackMarket.isRomanGodItem(item) && !VBlackMarket.isMythicItem(item)
                && !purchaseConfirm.checkOrArm(card, false, item, price, this.minecraft)) return;
        ClientPlayNetworking.send(new BlackMarketBuyPayload(card, buyStack));
    }

    private int getBlackMarketCardAt(double mouseX, double mouseY) {
        for (int row = 0; row < 2; row++) {
            int cardRowY = this.topPos + BM_CARD_Y + row * (BM_CARD_H + BM_ROW_GAP);
            if (mouseY >= cardRowY && mouseY < cardRowY + BM_CARD_H) {
                for (int col = 0; col < BM_CARDS_PER_ROW; col++) {
                    int cardX = this.leftPos + BM_CARD_START_X + col * BM_CARD_SPACING;
                    if (mouseX >= cardX && mouseX < cardX + BM_CARD_W) {
                        return row * BM_CARDS_PER_ROW + col;
                    }
                }
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
        extractor.fill(this.leftPos + 20, this.topPos + 226, this.leftPos + 334, this.topPos + 246, 0x55080310);
        extractor.fill(this.leftPos + 20, this.topPos + 226, this.leftPos + 334, this.topPos + 227, 0x22D4AF37);
        extractor.fill(this.leftPos + 20, this.topPos + 245, this.leftPos + 334, this.topPos + 246, 0x22D4AF37);

        // Highlight line under active mode button (Black Market - orange)
        extractor.fill(this.leftPos + 131, this.topPos + 243, this.leftPos + 223, this.topPos + 244, 0xFFFFAA00);

        // Player Inventory label
        extractor.text(this.font, Component.translatable("vcoins.inventory"),
                this.leftPos + VBlackMarketScreenHandler.PLAYER_X, this.topPos + 252, 0xFFC8A96E, false);

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
                ? VCoinsState.getClientCoins(this.minecraft.player.getUUID()) : 0L;

        extractor.fill(this.leftPos + 20, this.topPos + 39, this.leftPos + 334, this.topPos + 53, 0x88080310);
        extractor.text(this.font, Component.literal("§b" + timerStr), this.leftPos + 26, this.topPos + 42, 0xFF55FFFF, true);
        int pityRemaining = Math.max(0, VBlackMarket.PITY_THRESHOLD - syncedLifetimeFlipCount);
        extractor.centeredText(this.font, Component.translatable("vcoins.black_market.pity_remaining", pityRemaining),
                this.leftPos + this.imageWidth / 2, this.topPos + 42, 0xFFAAAAAA);
        String balStr = formatCompactNumber(balance);
        extractor.text(this.font, Component.literal(balStr), this.leftPos + 328 - this.font.width(balStr),
                this.topPos + 42, 0xFFFFFF55, true);

        if (purchaseConfirm.isArmed()) {
            purchaseConfirm.renderBanner(extractor, this.font, this.leftPos + 20, this.topPos + 39, 314, 14);
        }

        // 10 Cards Rendering (2 rows x 5 columns)
        long now = System.currentTimeMillis();
        if (deckTransitionActive()) {
            drawDeckTransition(extractor, now);
            return;
        }
        for (int i = 0; i < VBlackMarket.DAILY_ITEM_COUNT; i++) {
            // The cinematic owns this card until it has returned to its slot.
            if (i == godPullCardIndex && legendPresentationActive()) continue;
            int cardCol = i % BM_CARDS_PER_ROW;
            int cardRow = i / BM_CARDS_PER_ROW;
            int cardBaseX = this.leftPos + BM_CARD_START_X + cardCol * BM_CARD_SPACING;
            int cardBaseY = this.topPos + BM_CARD_Y + cardRow * (BM_CARD_H + BM_ROW_GAP);
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
                            int textColor = 0xFFFF7A66;
                            extractor.centeredText(this.font, Component.translatable("vcoins.mythic.badge"), cardBaseX + BM_CARD_W / 2, badgeY + 1, textColor);
                            extractor.centeredText(this.font, Component.translatable("vcoins.black_market.free_claim"), cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 15, 0xFF55FF55);
                        } else {
                            extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xEE140206);
                            int textColor = 0xFFFFE6A3;
                            extractor.centeredText(this.font, Component.translatable("vcoins.black_market.god_badge"), cardBaseX + BM_CARD_W / 2, badgeY + 1, textColor);
                            extractor.centeredText(this.font, Component.translatable("vcoins.black_market.free_claim"), cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 15, 0xFF55FF55);
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
        double time = still ? index * 0.7 : (System.nanoTime() - VFX_EPOCH_NANOS) / 1_000_000_000.0 + index * 0.7;
        float breath = still ? 0.65f : (float) (0.65 + 0.35 * Math.sin(time * 2.2));
        float power = Math.clamp((0.75f + 0.45f * hover) * (hidden ? 1f : 0.78f), 0f, 1f);

        if (isMythic) {
            VeloriaCardVfx.ambient(g, x, y, time, power, hover, hidden, true);
            return;
        }

        if (legend) {
            VeloriaCardVfx.ambient(g, x, y, time, power, hover, hidden, false);
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
            float px = x + (i % 2 == 0 ? 2f : BM_CARD_W - 3f);
            float py = y + BM_CARD_H - 3f - (t * (BM_CARD_H - 6f));
            int alpha = (int) (Math.sin(t * Math.PI) * 190 * power);
            drawParticleSprite(g, PARTICLE_GLOW_ORB, px, py, 3.2f, 64, alpha, highlight);
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
     * Blits a glowing particle spark/orb with subpixel coordinates and alpha.
     */
    private static void drawParticleSprite(GuiGraphicsExtractor g, Identifier tex,
                                           float px, float py, float size, int texSize, int alpha, int rgb) {
        if (alpha <= 2) return;
        int clampedA = Math.min(255, Math.max(0, alpha));
        int tint = (clampedA << 24) | (rgb & 0x00FFFFFF);
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().scale(size / texSize, size / texSize);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -texSize / 2, -texSize / 2,
                0.0f, 0.0f, texSize, texSize, texSize, texSize, texSize, texSize, tint);
        g.pose().popMatrix();
    }

    private static void drawParticleSprite(GuiGraphicsExtractor g, Identifier tex,
                                           float px, float py, float size, int texSize, int tint) {
        drawParticleSprite(g, tex, px, py, size, texSize, (tint >>> 24) & 0xFF, tint & 0x00FFFFFF);
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
                if (VBlackMarket.isRomanGodItem(stack) || VBlackMarket.isMythicItem(stack)) {
                    tooltip.add(Component.translatable("vcoins.black_market.free_claim").withStyle(ChatFormatting.GREEN));
                } else {
                    tooltip.add(Component.translatable("vcoins.black_market.buy_price", formatNumber(price)).withStyle(ChatFormatting.YELLOW));
                }
                boolean isPurchased = (syncedPurchasedMask & (1 << cardIndex)) != 0;
                if (isPurchased) {
                    tooltip.add(Component.translatable("vcoins.black_market.already_bought").withStyle(ChatFormatting.RED));
                } else {
                    tooltip.add(Component.translatable(VBlackMarket.isRomanGodItem(stack) || VBlackMarket.isMythicItem(stack) ? "vcoins.black_market.claim_left" : "vcoins.tooltip.buy_left").withStyle(ChatFormatting.GRAY));
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
        renderGloryCinematic(g, relic, false);
    }

    private void renderMythicCinematic(GuiGraphicsExtractor g, ItemStack relic) {
        renderGloryCinematic(g, relic, true);
    }

    private void renderGloryCinematic(GuiGraphicsExtractor g, ItemStack relic, boolean mythic) {
        long elapsed = System.currentTimeMillis() - godPullStartTime;
        if (mythic) playMythicScore(elapsed); else playLegendScore(elapsed);
        float entrance = Math.clamp(elapsed / 650f, 0f, 1f);
        float arrival = entrance * entrance * (3 - 2 * entrance);
        float departure = Math.clamp((elapsed - (GOD_CINEMATIC_DURATION_MS - 700f)) / 700f, 0f, 1f);
        float leave = departure * departure * (3 - 2 * departure);
        float presence = arrival * (1 - leave);
        float envelope = Math.clamp(elapsed / 200f, 0f, 1f) * (1 - leave);
        float stage = Math.min(1f, Math.min(this.width / 380f, this.height / 300f));
        int color = mythic ? VeloriaCardVfx.MYTHIC_FLAME : VeloriaCardVfx.GOLD_PRIMARY;
        int lightColor = mythic ? VeloriaCardVfx.MYTHIC_SEARING : VeloriaCardVfx.GOLD_LIGHT;
        VeloriaCardVfx.gloryBackground(g, this.width, this.height, stage, elapsed, envelope, mythic);

        int godCol = godPullCardIndex % BM_CARDS_PER_ROW;
        int godRow = godPullCardIndex / BM_CARDS_PER_ROW;
        float sourceX = leftPos + BM_CARD_START_X + godCol * BM_CARD_SPACING + BM_CARD_W / 2f;
        float sourceY = topPos + BM_CARD_Y + godRow * (BM_CARD_H + BM_ROW_GAP) + BM_CARD_H / 2f;
        float cx = sourceX + (width / 2f - sourceX) * presence;
        float cy = sourceY + (height / 2f - 10 * stage - sourceY) * presence;

        // 2. Powerful tactile impact camera shake on reveal (700ms) and mythic second ignition (1250ms)
        if (elapsed >= 700 && elapsed < 950) {
            float t = (elapsed - 700) / 250f;
            float impactShake = (1 - t) * (mythic ? 5.8f : 3.8f) * stage;
            cx += (float) Math.sin((elapsed - 700) * 0.09) * impactShake;
            cy += (float) Math.cos((elapsed - 700) * 0.11) * impactShake * 0.75f;
        }
        if (mythic && elapsed >= 1250 && elapsed < 1480) {
            float t = (elapsed - 1250) / 230f;
            float secondShake = (1 - t) * 3.2f * stage;
            cx += (float) Math.sin((elapsed - 1250) * 0.08) * secondShake;
            cy += (float) Math.cos((elapsed - 1250) * 0.10) * secondShake * 0.65f;
        }

        // 3. Smooth celestial levitation (gentle vertical float & horizontal drift)
        float floatBob = elapsed >= 700 ? (float) Math.sin((elapsed - 700) * 0.0028) * 3.5f * stage * presence : 0f;
        float floatSway = elapsed >= 700 ? (float) Math.cos((elapsed - 700) * 0.0020) * 1.5f * stage * presence : 0f;
        float cardX = cx + floatSway;
        float cardY = cy + floatBob;

        // 4. Card dynamic scale
        float punch = elapsed >= 700 && elapsed < 1100
                ? (float) Math.sin((elapsed - 700) / 400f * Math.PI) * (mythic ? 0.32f : 0.22f) : 0;
        float scale = 1 + presence * ((mythic ? 1.42f : 1.22f) * stage + punch);

        // 5. Render Glory behind the hero card (Mandala, shockwaves, orbits, spark bursts, pillars)
        VeloriaCardVfx.pillars(g, cardX, cardY - BM_CARD_H * scale / 2f, cardY + BM_CARD_H * scale / 2f, stage, elapsed, envelope, mythic);
        VeloriaCardVfx.glory(g, cardX, cardY, stage, elapsed, envelope, mythic);
        VeloriaCardVfx.ascension(g, cardX, cardY, stage, elapsed, envelope, mythic);

        boolean sealed = elapsed < 700;
        float face = elapsed < 520 ? 1 : elapsed < 700
                ? Math.max(0.025f, (700 - elapsed) / 180f)
                : Math.min(1, 0.025f + (elapsed - 700) / 180f);

        // 6. Draw Card Texture (Sealed Back or Revealed Front)
        g.pose().pushMatrix();
        g.pose().translate(cardX, cardY);
        g.pose().scale(scale * face, scale);
        drawCardTexture(g, sealed ? getCardBackTexture(relic) : getCardFrontTexture(relic),
                -BM_CARD_W / 2, -BM_CARD_H / 2, BM_CARD_W, BM_CARD_H);
        drawCardOutline(g, -BM_CARD_W / 2, -BM_CARD_H / 2, BM_CARD_W, BM_CARD_H,
                VeloriaCardVfx.tint(0.92f, color));
        g.pose().popMatrix();

        // 7. Holographic Foil Sheen & Prismatic Edge Luster on Revealed Card
        if (!sealed) {
            VeloriaCardVfx.gloryCardFace(g, cardX, cardY, scale * face, scale, stage, elapsed, envelope, mythic);

            // 8. Floating 3D Relic Item with Sacred Star Core
            float reveal = Math.clamp((elapsed - 700f) / 220f, 0f, 1f);
            float itemScale = (1 + (mythic ? 2.3f : 1.95f) * stage * presence) * reveal;
            float itemBob = (float) Math.sin((elapsed - 700) * 0.0032) * 2.2f * stage * presence;

            VeloriaCardVfx.gloryItemCore(g, cardX, cardY + itemBob, itemScale, stage, elapsed, envelope, mythic);

            g.pose().pushMatrix();
            g.pose().translate(cardX, cardY + itemBob);
            g.pose().scale(itemScale, itemScale);
            g.item(relic, -8, -8);
            g.pose().popMatrix();
        }

        // 9. Foreground Ascending Embers & Celestial Sparks
        VeloriaCardVfx.gloryForeground(g, cardX, cardY, stage, elapsed, envelope, mythic);

        // 10. Regal Title with Animated Glory VFX
        Component heading = Component.translatable(mythic ? "vcoins.mythic.reveal" : "vcoins.roman.reveal.title")
                .withStyle(ChatFormatting.BOLD);
        float headerY = height / 2f - 112 * stage;
        VeloriaCardVfx.gloryTitle(g, this.font, heading, width / 2f, headerY, stage, elapsed, (1 - leave), mythic);

        float textFade = Math.clamp((elapsed - 920f) / 300f, 0f, 1f) * (1 - leave);
        if (textFade > 0.02f) {

            // Relic Nameplate with Framed Plaque Backing
            String name = relic.getHoverName().getString();
            int maxWidth = Math.max(20, Math.min(290, width - 30));
            if (font.width(name) > maxWidth) name = font.plainSubstrByWidth(name, maxWidth - font.width("…")) + "…";
            int nameY = Math.round(height / 2f + 85 * stage);

            int nameW = font.width(name);
            int plaqueW = Math.min(nameW + 36, width - 24);
            int plaqueH = 18;
            int plaqueX = (width - plaqueW) / 2;
            int plaqueY = nameY - 4;

            // Translucent glassmorphic plaque fill
            g.fill(plaqueX, plaqueY, plaqueX + plaqueW, plaqueY + plaqueH,
                    VeloriaCardVfx.tint(textFade * 0.82f, mythic ? 0x1A080C : 0x100D1A));
            InventoryTextures.frame(g, plaqueX, plaqueY, plaqueW, plaqueH,
                    VeloriaCardVfx.tint(textFade * 0.88f, color));

            g.centeredText(font, Component.literal(name), width / 2, nameY + 1,
                    VeloriaCardVfx.tint(textFade, 0xFFFFFF));

            // Dismiss prompt with smooth breathing pulse
            float dismissPulse = 0.55f + 0.35f * (float) Math.sin(elapsed * 0.005);
            g.centeredText(font, Component.translatable("vcoins.roman.reveal.dismiss"), width / 2, nameY + 20,
                    VeloriaCardVfx.tint(textFade * dismissPulse, 0xD0C4E2));
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
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.65f, 0.90f));
            } else {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.70f, 0.55f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.85f, 0.60f));
            }
        }
    }

    private void playLegendScore(long elapsed) {
        if (this.minecraft == null) return;
        // Crossed stages are consumed once. Skip stale cues after a long frame stall.
        if (legendSoundStage < 1 && elapsed >= 350) {
            legendSoundStage = 1;
            if (elapsed < 650) {
                this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35f, 0.55f));
                this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.80f, 0.85f));
            }
        }
        if (legendSoundStage < 2 && elapsed >= 700) {
            legendSoundStage = 2;
            if (elapsed < 1150) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 0.85f, 0.70f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.85f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 0.90f, 1.25f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.80f, 1.05f));
            }
        }
        if (legendSoundStage < 3 && elapsed >= 1350) {
            legendSoundStage = 3;
            if (elapsed < 1800) {
                this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.65f, 0.50f));
                this.minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.BELL_RESONATE, 1.10f, 0.65f));
            }
        }
    }

    private void playMythicScore(long elapsed) {
        if (this.minecraft == null) return;
        if (mythicSoundStage < 1 && elapsed >= 350) {
            mythicSoundStage = 1;
            if (elapsed < 650) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 1.45f, 0.75f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.65f, 0.85f));
            }
        }
        if (mythicSoundStage < 2 && elapsed >= 700) {
            mythicSoundStage = 2;
            if (elapsed < 1150) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 0.80f, 0.80f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 0.90f, 0.85f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.95f, 0.45f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.10f, 0.75f));
            }
        }
        if (mythicSoundStage < 3 && elapsed >= 1250) {
            mythicSoundStage = 3;
            if (elapsed < 1700) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_RESONATE, 1.25f, 0.70f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.45f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.70f, 0.45f));
            }
        }
        if (mythicSoundStage < 4 && elapsed >= 1700) {
            mythicSoundStage = 4;
            if (elapsed < 2200) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_RESONATE, 1.75f, 0.45f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.30f, 0.50f));
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
