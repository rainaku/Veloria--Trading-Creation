package com.vcoins;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class VTradeScreen extends AbstractContainerScreen<VTradeScreenHandler> {
    public enum MainView {
        SHOP,
        BLACK_MARKET,
        DUPLICATE
    }

    private final VeloriaMerchantPreview merchantPreview = new VeloriaMerchantPreview();
    private final VCoinsPurchaseConfirm purchaseConfirm = new VCoinsPurchaseConfirm();
    private static final int TAB_WIDTH = 27;
    private static final int TAB_HEIGHT = 28;
    private static final int TAB_GAP = 1;
    private static final int TOP_TAB_COUNT = 7;
    private static final int SCROLLBAR_X = 194;
    private static final int SCROLLBAR_Y = 38;
    private static final int SCROLLBAR_HEIGHT = 90;
    private static final int SCROLL_THUMB_HEIGHT = 15;
    private static final int SHOP_X = VTradeScreenHandler.SHOP_X;
    private static final int SHOP_Y = VTradeScreenHandler.SHOP_Y;
    private static final int PLAYER_X = VTradeScreenHandler.PLAYER_X;
    private static final int PLAYER_INVENTORY_Y = VTradeScreenHandler.PLAYER_INVENTORY_Y;
    private static final int PLAYER_HOTBAR_Y = VTradeScreenHandler.PLAYER_HOTBAR_Y;
    private static final int SLOT_SPACING = 18;
    private static final int SLOT_SIZE = 16;

    private static final ShopCategory[] TABS = {
            ShopCategory.ALL,
            ShopCategory.BUILDING,
            ShopCategory.COLORED,
            ShopCategory.NATURAL,
            ShopCategory.FUNCTIONAL,
            ShopCategory.REDSTONE,
            ShopCategory.TOOLS,
            ShopCategory.COMBAT,
            ShopCategory.FOOD,
            ShopCategory.INGREDIENTS,
            ShopCategory.MISC,
            ShopCategory.BUYBACK
    };

    private EditBox searchBox;
    private ShopCategory selectedCategory = ShopCategory.ALL;
    private float scrollPosition;
    private boolean scrolling;
    private int lastScrollOffset = -1;

    // View Navigation
    private MainView currentView = MainView.SHOP;
    private Button verifyToggleButton;
    private Button shopTabButton;
    private Button blackMarketTabButton;
    private Button duplicateTabButton;
    private Button backToShopButton;
    private Button bmRevealAllButton;
    private Button bmResetButton;
    private Button duplicateActionButton;

    // Black Market State & Animations
    private static final Identifier CARD_BACK = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_back.png");
    // Portrait card: 28×50 (≈ 4:7 ratio like a tarot card)
    private static final int BM_CARD_W       = 28;
    private static final int BM_CARD_H       = 50;
    private static final int BM_CARD_SPACING = 38;  // gap between card left edges
    private static final int BM_CARD_START_X = 13;  // centred in 236px panel
    private static final int BM_CARD_Y       = 38;
    private static final long BM_FLIP_MS     = 600L; // flip animation duration
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final Set<Integer> bmLocalRevealedCards = new HashSet<>();
    private final long[] bmFlipStartTime = new long[5];
    private final boolean[] bmHasSpawnedStartParticles = new boolean[5];
    private final boolean[] bmHasSpawnedFinishParticles = new boolean[5];

    // Particle pool for Black Market
    private static final int BM_MAX_PARTICLES = 48;
    private static final String[] BM_PARTICLE_SYMBOLS = {"✦", "✧", "⋆", "•"};
    private final float[] bmPartX = new float[BM_MAX_PARTICLES];
    private final float[] bmPartY = new float[BM_MAX_PARTICLES];
    private final float[] bmPartVx = new float[BM_MAX_PARTICLES];
    private final float[] bmPartVy = new float[BM_MAX_PARTICLES];
    private final int[] bmPartColor = new int[BM_MAX_PARTICLES];
    private final byte[] bmPartSymbol = new byte[BM_MAX_PARTICLES];
    private final long[] bmPartSpawn = new long[BM_MAX_PARTICLES];
    private final int[] bmPartLife = new int[BM_MAX_PARTICLES];
    private int bmParticleCount = 0;

    // Duplicate State
    private int selectedDuplicateSlot = -1;
    private ItemStack selectedDuplicateStack = ItemStack.EMPTY;

    public VTradeScreen(VTradeScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 236, 250);
    }

    @Override
    protected void init() {
        super.init();

        this.searchBox = new EditBox(this.font, this.leftPos + 112, this.topPos + 6, 110, 15,
                Component.translatable("vcoins.search"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setHint(Component.translatable("vcoins.search"));
        this.searchBox.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);

        // Nút quay lại Mua hàng (khi ở Chợ Đen / Duplicate)
        this.backToShopButton = this.addRenderableWidget(VeloriaButton.create(
                Component.literal("◀ Mua"),
                button -> switchView(MainView.SHOP))
                .bounds(this.leftPos + 16, this.topPos + 6, 42, 14)
                .build());

        // Nút Lật tất cả cho Chợ Đen
        this.bmRevealAllButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.black_market.reveal_all"),
                button -> revealAllBlackMarketCards())
                .bounds(this.leftPos + 158, this.topPos + 6, 62, 14)
                .build());

        // Nút Đặt lại Chợ Đen tích lũy
        this.bmResetButton = this.addRenderableWidget(VeloriaButton.create(
                Component.literal("↺ ×" + VBlackMarketScreen.getSyncedBankedResets()),
                button -> useBankedReset())
                .bounds(this.leftPos + 86, this.topPos + 6, 46, 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("Đặt lại lượt Chợ Đen (" + VBlackMarketScreen.getSyncedBankedResets() + " lần tích lũy)")))
                .build());

        // Nút Nhân Bản Vật Phẩm
        this.duplicateActionButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.duplicate.action"),
                button -> executeDuplicate())
                .bounds(this.leftPos + 68, this.topPos + 102, 100, 18)
                .build());

        // Công tắc bật/tắt xác minh giao dịch >100k
        this.verifyToggleButton = this.addRenderableWidget(VeloriaButton.create(
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
                .bounds(this.leftPos + 14, this.topPos + 132, 44, 16)
                .tooltip(VCoinsPurchaseConfirm.getToggleTooltip())
                .build());

        // 3 nút chuyển đổi Chế độ ngay trên thanh Toolbar
        this.shopTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.title"),
                button -> switchView(MainView.SHOP))
                .bounds(this.leftPos + 60, this.topPos + 132, 50, 16)
                .build());

        this.blackMarketTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.tab.black_market"),
                button -> switchView(MainView.BLACK_MARKET))
                .bounds(this.leftPos + 114, this.topPos + 132, 52, 16)
                .build());

        this.duplicateTabButton = this.addRenderableWidget(VeloriaButton.create(
                Component.translatable("vcoins.duplicate.open"),
                button -> switchView(MainView.DUPLICATE))
                .bounds(this.leftPos + 170, this.topPos + 132, 52, 16)
                .build());

        this.setInitialFocus(this.searchBox);
        updateViewWidgets();
    }

    public void switchView(MainView view) {
        if (this.currentView == view) return;
        this.currentView = view;
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
        updateViewWidgets();
    }

    private void updateViewWidgets() {
        boolean isShop = (this.currentView == MainView.SHOP);
        boolean isBm = (this.currentView == MainView.BLACK_MARKET);
        boolean isDup = (this.currentView == MainView.DUPLICATE);

        if (this.searchBox != null) {
            this.searchBox.setVisible(isShop);
            if (!isShop) {
                this.searchBox.setFocused(false);
            }
        }
        if (this.verifyToggleButton != null) {
            this.verifyToggleButton.visible = isShop;
        }
        if (this.backToShopButton != null) {
            this.backToShopButton.visible = !isShop;
        }
        if (this.bmRevealAllButton != null) {
            this.bmRevealAllButton.visible = isBm;
            int count = 0;
            for (int i = 0; i < 5; i++) {
                if (((VBlackMarketScreen.getSyncedRevealedMask() & (1 << i)) != 0) || bmLocalRevealedCards.contains(i)) {
                    count++;
                }
            }
            this.bmRevealAllButton.active = (count < 5);
        }
        if (this.bmResetButton != null) {
            int banked = VBlackMarketScreen.getSyncedBankedResets();
            this.bmResetButton.visible = isBm && (banked > 0);
            if (this.bmResetButton.visible) {
                this.bmResetButton.setMessage(Component.literal("↺ ×" + banked));
                this.bmResetButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("Đặt lại lượt Chợ Đen (" + banked + " lần tích lũy)")));
            }
        }
        if (this.duplicateActionButton != null) {
            this.duplicateActionButton.visible = isDup;
            updateDuplicateButtonState();
        }
    }

    public void onBlackMarketSyncReceived(int newResetSequence, long newDay) {
        updateViewWidgets();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.currentView == MainView.DUPLICATE) {
            updateDuplicateButtonState();
        }
    }

    private void updateDuplicateButtonState() {
        if (this.duplicateActionButton == null || this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (this.selectedDuplicateSlot < 0 || this.selectedDuplicateStack.isEmpty()) {
            this.duplicateActionButton.active = false;
            return;
        }
        ItemStack current = this.minecraft.player.getInventory().getItem(this.selectedDuplicateSlot);
        if (current.isEmpty() || !VCoinsPricing.isTradeable(current.getItem())) {
            this.duplicateActionButton.active = false;
            return;
        }
        long coinCost = VDuplicatePricing.getCoinCost(current);
        int levelCost = VDuplicatePricing.getExperienceLevelCost(current);
        long balance = VCoinsState.getCoins(this.minecraft.player.getUUID());
        this.duplicateActionButton.active = (balance >= coinCost && this.minecraft.player.experienceLevel >= levelCost);
    }

    private void executeDuplicate() {
        if (this.selectedDuplicateSlot >= 0 && !this.selectedDuplicateStack.isEmpty()) {
            ClientPlayNetworking.send(new ShopActionPayload("DUPLICATE", String.valueOf(this.selectedDuplicateSlot)));
            if (this.minecraft != null) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_USE, 1.0f));
            }
        }
    }

    private void revealAllBlackMarketCards() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            boolean isRevealed = ((VBlackMarketScreen.getSyncedRevealedMask() & (1 << i)) != 0) || bmLocalRevealedCards.contains(i);
            if (!isRevealed) {
                bmLocalRevealedCards.add(i);
                bmFlipStartTime[i] = now + i * 80L;
                bmHasSpawnedStartParticles[i] = false;
                bmHasSpawnedFinishParticles[i] = false;
            }
        }
        ClientPlayNetworking.send(new BlackMarketRevealPayload(-1));
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.15f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35f));
        }
        updateViewWidgets();
    }

    private void useBankedReset() {
        ClientPlayNetworking.send(new BlackMarketResetActionPayload());
        bmLocalRevealedCards.clear();
        Arrays.fill(bmFlipStartTime, 0L);
        Arrays.fill(bmHasSpawnedStartParticles, false);
        Arrays.fill(bmHasSpawnedFinishParticles, false);
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f));
        }
        updateViewWidgets();
    }

    private void updateVerifyToggleButton() {
        if (this.verifyToggleButton != null) {
            this.verifyToggleButton.setMessage(VCoinsPurchaseConfirm.getToggleLabel());
            this.verifyToggleButton.setTooltip(VCoinsPurchaseConfirm.getToggleTooltip());
        }
    }

    private void onSearchChanged(String query) {
        if (!query.isEmpty() && this.selectedCategory != ShopCategory.ALL) {
            selectCategory(ShopCategory.ALL);
        }
        this.scrollPosition = 0.0f;
        this.lastScrollOffset = 0;
        this.menu.setSearchQuery(query);
        ClientPlayNetworking.send(new ShopActionPayload("SEARCH", query));
    }

    private void selectCategory(ShopCategory category) {
        if (this.selectedCategory == category) {
            return;
        }

        VeloriaMenuEffects.categoryChanged();
        this.selectedCategory = category;
        this.scrollPosition = 0.0f;
        this.lastScrollOffset = 0;
        this.menu.setCategory(category);
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        }
        ClientPlayNetworking.send(new ShopActionPayload("TAB", category.name()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        // Suppress the hover highlight for shop slots when not in SHOP view, so their
        // gray slot cells don't bleed through into the Black Market / Duplicate views.
        Slot savedHoveredSlot = null;
        if (this.currentView != MainView.SHOP && this.hoveredSlot != null
                && this.hoveredSlot.index < VTradeScreenHandler.SHOP_SLOT_COUNT) {
            savedHoveredSlot = this.hoveredSlot;
            this.hoveredSlot = null;
        }
        super.extractBackground(extractor, mouseX, mouseY, delta);
        if (savedHoveredSlot != null) {
            this.hoveredSlot = savedHoveredSlot;
        }
        merchantPreview.drawBehindMenu(extractor, this.leftPos, this.topPos, this.height, mouseX, mouseY);
        drawPanel(extractor);

        if (this.currentView == MainView.SHOP) {
            drawTabs(extractor);
            drawSlotGrid(extractor, SHOP_X, SHOP_Y, VTradeScreenHandler.SHOP_COLUMNS, VTradeScreenHandler.SHOP_ROWS);
            drawScrollbar(extractor);

            if (purchaseConfirm.isArmed()) {
                purchaseConfirm.renderBanner(extractor, this.font, this.leftPos + 16, this.topPos + 22, 204, 13);
            } else {
                renderShopBalance(extractor);
            }
        } else if (this.currentView == MainView.BLACK_MARKET) {
            renderBlackMarketContent(extractor, mouseX, mouseY, delta);
        } else if (this.currentView == MainView.DUPLICATE) {
            renderDuplicateContent(extractor, mouseX, mouseY, delta);
        }

        // Shared player inventory grid
        drawSlotGrid(extractor, PLAYER_X, PLAYER_INVENTORY_Y, 9, 3);
        drawSlotGrid(extractor, PLAYER_X, PLAYER_HOTBAR_Y, 9, 1);

        // Toolbar decorative tray
        extractor.fill(this.leftPos + 12, this.topPos + 130, this.leftPos + 224, this.topPos + 150, 0x55080310);
        extractor.fill(this.leftPos + 12, this.topPos + 130, this.leftPos + 224, this.topPos + 131, 0x22D4AF37);
        extractor.fill(this.leftPos + 12, this.topPos + 149, this.leftPos + 224, this.topPos + 150, 0x22D4AF37);

        // Highlight line under active mode button
        if (this.currentView == MainView.SHOP) {
            extractor.fill(this.leftPos + 60, this.topPos + 147, this.leftPos + 110, this.topPos + 148, 0xFFD4AF37);
        } else if (this.currentView == MainView.BLACK_MARKET) {
            extractor.fill(this.leftPos + 114, this.topPos + 147, this.leftPos + 166, this.topPos + 148, 0xFFFFAA00);
        } else if (this.currentView == MainView.DUPLICATE) {
            extractor.fill(this.leftPos + 170, this.topPos + 147, this.leftPos + 222, this.topPos + 148, 0xFF55FFFF);
        }

        // Player Inventory label
        extractor.text(this.font, Component.translatable("vcoins.inventory"),
                this.leftPos + PLAYER_X, this.topPos + 153, 0xFFC8A96E, false);
        VeloriaMenuEffects.draw(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    private void renderShopBalance(GuiGraphicsExtractor extractor) {
        long balance = this.minecraft != null && this.minecraft.player != null
                ? VCoinsState.getCoins(this.minecraft.player.getUUID())
                : 0L;
        extractor.fill(this.leftPos + 16, this.topPos + 22, this.leftPos + 220, this.topPos + 35, 0x88080310);
        extractor.fill(this.leftPos + 16, this.topPos + 22, this.leftPos + 220, this.topPos + 23, 0x33D4AF37);
        extractor.fill(this.leftPos + 16, this.topPos + 34, this.leftPos + 220, this.topPos + 35, 0x33D4AF37);
        extractor.text(this.font, Component.translatable("vcoins.balance", formatNumber(balance)),
                this.leftPos + 20, this.topPos + 24, 0xFFE8B829, true);
    }

    private void renderBlackMarketContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        // Black Market Header bar: countdown & balance
        long now = System.currentTimeMillis();
        long elapsedSec = (now - VBlackMarketScreen.getLastSyncTimeMs()) / 1000L;
        long currentSecondsLeft = Math.max(0L, VBlackMarketScreen.getBaseSecondsRemaining() - elapsedSec);
        long hours = currentSecondsLeft / 3600L;
        long minutes = (currentSecondsLeft % 3600L) / 60L;
        long seconds = currentSecondsLeft % 60L;
        String timerFormatted = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);

        extractor.fill(this.leftPos + 16, this.topPos + 22, this.leftPos + 220, this.topPos + 35, 0x88080310);
        extractor.fill(this.leftPos + 16, this.topPos + 22, this.leftPos + 220, this.topPos + 23, 0x33D4AF37);
        extractor.fill(this.leftPos + 16, this.topPos + 34, this.leftPos + 220, this.topPos + 35, 0x33D4AF37);

        extractor.text(this.font, Component.literal("§bĐổi mới sau: §f" + timerFormatted),
                this.leftPos + 20, this.topPos + 24, 0xFF00E5FF, true);

        long balance = this.minecraft != null && this.minecraft.player != null
                ? VCoinsState.getCoins(this.minecraft.player.getUUID())
                : 0L;
        extractor.text(this.font, Component.literal("§e" + formatCompactNumber(balance)),
                this.leftPos + 178, this.topPos + 24, 0xFFE8B829, true);

        // Render 5 Daily Cards
        List<ItemStack> cards = VBlackMarketScreen.getSyncedCards();
        for (int i = 0; i < 5; i++) {
            int cardBaseX = this.leftPos + BM_CARD_START_X + i * BM_CARD_SPACING;
            int cardBaseY = this.topPos + BM_CARD_Y;

            ItemStack stack = (i < cards.size()) ? cards.get(i) : ItemStack.EMPTY;
            if (stack.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
                List<ItemStack> fallback = VBlackMarket.getItemsForPlayer(this.minecraft.player.getUUID());
                if (i < fallback.size()) stack = fallback.get(i);
            }

            boolean isRevealed  = ((VBlackMarketScreen.getSyncedRevealedMask()   & (1 << i)) != 0) || bmLocalRevealedCards.contains(i);
            boolean isPurchased = (VBlackMarketScreen.getSyncedPurchasedMask()   & (1 << i)) != 0;
            boolean isHovered   = (getBlackMarketCardAt(mouseX, mouseY) == i);
            boolean isGod       = !stack.isEmpty() && VBlackMarket.isRomanGodItem(stack);
            long start = bmFlipStartTime[i];
            boolean isFlipping  = !VCoinsPurchaseConfirm.isReducedMotion()
                    && isRevealed && start > 0 && now >= start && now < start + BM_FLIP_MS;

            // ── Easing helper: cubic ease-in-out ──────────────────────────────
            // progress ∈ [0,1], returns smoothed value

            if (!isRevealed) {
                // ─── FACE-DOWN CARD ───
                if (isGod) {
                    // Animated golden shimmer border hinting at power within
                    float pulse = (float) (0.5 + 0.5 * Math.sin(now * 0.005));
                    int alpha = (int) (55 + pulse * 80);
                    // outer glow
                    extractor.fill(cardBaseX - 2, cardBaseY - 2, cardBaseX + BM_CARD_W + 2, cardBaseY + BM_CARD_H + 2, (alpha << 24) | 0xFFD700);
                    extractor.fill(cardBaseX - 1, cardBaseY - 1, cardBaseX + BM_CARD_W + 1, cardBaseY + BM_CARD_H + 1, ((alpha / 2) << 24) | 0xFF8C00);
                }
                drawCardTexture(extractor, CARD_BACK, cardBaseX, cardBaseY, BM_CARD_W, BM_CARD_H);
                // Thin decorative border
                extractor.fill(cardBaseX,              cardBaseY,              cardBaseX + BM_CARD_W, cardBaseY + 1,              0x55FFFFFF);
                extractor.fill(cardBaseX,              cardBaseY + BM_CARD_H - 1, cardBaseX + BM_CARD_W, cardBaseY + BM_CARD_H,  0x33FFFFFF);
                if (isHovered) {
                    extractor.fill(cardBaseX, cardBaseY, cardBaseX + BM_CARD_W, cardBaseY + BM_CARD_H, 0x30FFD700);
                }
                extractor.centeredText(this.font, Component.literal("§7?"),
                        cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 4, 0xFFAAAAAA);

            } else if (isFlipping) {
                // ─── FLIPPING ANIMATION ───
                float raw = (float)(now - start) / (float) BM_FLIP_MS;
                float p   = raw < 1f ? raw : 1f;

                // Cubic ease-in-out on raw progress
                float eased = p < 0.5f
                        ? 4f * p * p * p
                        : 1f - (float) Math.pow(-2f * p + 2f, 3) / 2f;

                // Horizontal scale: cos curve collapses to 0 at mid-flip then opens back
                float scaleX = (float) Math.cos(eased * Math.PI);
                boolean pastMid = scaleX < 0f;
                float absScale  = Math.abs(scaleX);
                int currentW    = Math.max(2, (int)(BM_CARD_W * absScale));
                int currentX    = cardBaseX + (BM_CARD_W - currentW) / 2;

                // Subtle vertical lift arc: rises slightly then drops back
                float liftArc   = (float) Math.sin(eased * Math.PI); // 0→1→0
                int liftY       = (int)(liftArc * 5f);               // max 5px up
                int drawY       = cardBaseY - liftY;

                // Spawn burst particles at the half-turn moment
                if (pastMid && !bmHasSpawnedStartParticles[i]) {
                    bmHasSpawnedStartParticles[i] = true;
                    spawnBmCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2);
                }
                if (p >= 1f && !bmHasSpawnedFinishParticles[i]) {
                    bmHasSpawnedFinishParticles[i] = true;
                    spawnBmCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2);
                }

                if (!pastMid) {
                    // Front half: still showing card back, collapsing
                    drawCardTexture(extractor, CARD_BACK, currentX, drawY, currentW, BM_CARD_H);
                } else {
                    // Back half: revealing item face
                    InventoryTextures.recess(extractor, currentX, drawY, currentW, BM_CARD_H);

                    // Golden flash at the exact moment of reveal (absScale near 0)
                    if (absScale < 0.35f) {
                        int flashAlpha = (int)((0.35f - absScale) / 0.35f * 200f);
                        extractor.fill(currentX, drawY, currentX + currentW, drawY + BM_CARD_H,
                                (flashAlpha << 24) | 0xFFD700);
                    }

                    // Item appears once card is wide enough to show properly
                    if (absScale > 0.45f && !stack.isEmpty()) {
                        int itemX = cardBaseX + (BM_CARD_W - 16) / 2;
                        int itemY = drawY  + (BM_CARD_H - 16) / 2;
                        extractor.item(stack, itemX, itemY);
                    }
                }

            } else {
                // ─── FULLY REVEALED CARD ───
                // Card border
                if (isGod) {
                    float pulse = (float)(0.5 + 0.5 * Math.sin(now * 0.006));
                    // Animated rainbow-gold border
                    int r   = (int)(0xD7 + pulse * (0xFF - 0xD7));
                    int g   = (int)(0x20 + pulse * 0xBF);
                    int col = (0xFF << 24) | (r << 16) | (g << 8) | 0x00;
                    // outer glow
                    extractor.fill(cardBaseX - 2, cardBaseY - 2, cardBaseX + BM_CARD_W + 2, cardBaseY + BM_CARD_H + 2, (80 << 24) | (col & 0xFFFFFF));
                    // 1px bright border
                    extractor.fill(cardBaseX - 1, cardBaseY - 1, cardBaseX + BM_CARD_W + 1, cardBaseY,               col);
                    extractor.fill(cardBaseX - 1, cardBaseY + BM_CARD_H, cardBaseX + BM_CARD_W + 1, cardBaseY + BM_CARD_H + 1, col);
                    extractor.fill(cardBaseX - 1, cardBaseY,             cardBaseX,                 cardBaseY + BM_CARD_H, col);
                    extractor.fill(cardBaseX + BM_CARD_W, cardBaseY,     cardBaseX + BM_CARD_W + 1, cardBaseY + BM_CARD_H, col);
                    spawnBmAmbientSparkle(cardBaseX, cardBaseY, BM_CARD_W);
                } else {
                    // Normal card: subtle gold-tinted 1px border
                    extractor.fill(cardBaseX - 1, cardBaseY - 1, cardBaseX + BM_CARD_W + 1, cardBaseY,               0x88D4AF37);
                    extractor.fill(cardBaseX - 1, cardBaseY + BM_CARD_H, cardBaseX + BM_CARD_W + 1, cardBaseY + BM_CARD_H + 1, 0x44D4AF37);
                    extractor.fill(cardBaseX - 1, cardBaseY,             cardBaseX,                 cardBaseY + BM_CARD_H, 0x44D4AF37);
                    extractor.fill(cardBaseX + BM_CARD_W, cardBaseY,     cardBaseX + BM_CARD_W + 1, cardBaseY + BM_CARD_H, 0x44D4AF37);
                }

                InventoryTextures.recess(extractor, cardBaseX, cardBaseY, BM_CARD_W, BM_CARD_H);

                // Item centred in card
                if (!stack.isEmpty()) {
                    int itemX = cardBaseX + (BM_CARD_W - 16) / 2;
                    int itemY = cardBaseY + (BM_CARD_H - 16) / 2 - 4; // slightly above centre to leave room for label
                    extractor.item(stack, itemX, itemY);
                    extractor.itemDecorations(this.font, stack, itemX, itemY);
                }

                if (isHovered && !isPurchased) {
                    extractor.fill(cardBaseX, cardBaseY, cardBaseX + BM_CARD_W, cardBaseY + BM_CARD_H, 0x28FFFFFF);
                }

                if (isPurchased) {
                    extractor.fill(cardBaseX, cardBaseY, cardBaseX + BM_CARD_W, cardBaseY + BM_CARD_H, 0x88000000);
                    extractor.centeredText(this.font, Component.literal("§a§lĐÃ\nMUA"), cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2 - 4, 0xFF55FF55);
                    extractor.centeredText(this.font, Component.literal("§a§lĐÃ MUA"), cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 4, 0xFF55FF55);
                } else {
                    int disc  = VBlackMarket.getDiscountPercent(stack, VBlackMarketScreen.getSyncedDay(), VBlackMarketScreen.getSyncedResetSequence());
                    long price = VBlackMarket.getDiscountedPrice(stack, VBlackMarketScreen.getSyncedDay(), VBlackMarketScreen.getSyncedResetSequence());

                    if (isGod) {
                        extractor.centeredText(this.font, Component.literal("§c§lCẤP THẦN"), cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 3,  0xFFFF5555);
                        extractor.centeredText(this.font, Component.literal("§6"  + formatCompactNumber(price)),  cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 13, 0xFFFFAA00);
                    } else {
                        extractor.centeredText(this.font, Component.literal("§a-" + disc + "%"),                  cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 3,  0xFF55FF55);
                        extractor.centeredText(this.font, Component.literal("§e"  + formatCompactNumber(price)),  cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H + 13, 0xFFFFFF55);
                    }
                }
            }
        }
    }

    private void renderDuplicateContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        // Forge Workspace Tray
        extractor.fill(this.leftPos + 18, this.topPos + 36, this.leftPos + 218, this.topPos + 124, 0x66080310);
        extractor.fill(this.leftPos + 18, this.topPos + 36, this.leftPos + 218, this.topPos + 37, 0x33D4AF37);
        extractor.fill(this.leftPos + 18, this.topPos + 123, this.leftPos + 218, this.topPos + 124, 0x33D4AF37);

        // Original Box
        InventoryTextures.recess(extractor, this.leftPos + 40, this.topPos + 44, 32, 32);
        extractor.centeredText(this.font, Component.literal("§eGốc"), this.leftPos + 56, this.topPos + 36, 0xFFD4AF37);
        if (!selectedDuplicateStack.isEmpty()) {
            extractor.item(selectedDuplicateStack, this.leftPos + 48, this.topPos + 52);
            extractor.itemDecorations(this.font, selectedDuplicateStack, this.leftPos + 48, this.topPos + 52);
        }

        // Glowing Forge Arrow
        extractor.centeredText(this.font, Component.literal("§6§l➜"), this.leftPos + 118, this.topPos + 54, 0xFFFFAA00);

        // Clone Box
        InventoryTextures.recess(extractor, this.leftPos + 164, this.topPos + 44, 32, 32);
        extractor.centeredText(this.font, Component.literal("§bBản sao"), this.leftPos + 180, this.topPos + 36, 0xFF55FFFF);
        if (!selectedDuplicateStack.isEmpty()) {
            ItemStack clone = selectedDuplicateStack.copyWithCount(1);
            extractor.item(clone, this.leftPos + 172, this.topPos + 52);
            extractor.itemDecorations(this.font, clone, this.leftPos + 172, this.topPos + 52);
        }

        // Cost and Instruction Info
        if (selectedDuplicateStack.isEmpty()) {
            extractor.centeredText(this.font, Component.literal("§7Nhấp một món đồ trong túi đồ để nhân bản"),
                    this.leftPos + 118, this.topPos + 84, 0xFFAAAAAA);
        } else {
            long coinCost = VDuplicatePricing.getCoinCost(selectedDuplicateStack);
            int levelCost = VDuplicatePricing.getExperienceLevelCost(selectedDuplicateStack);
            long balance = (this.minecraft != null && this.minecraft.player != null)
                    ? VCoinsState.getCoins(this.minecraft.player.getUUID()) : 0L;
            int playerLevels = (this.minecraft != null && this.minecraft.player != null)
                    ? this.minecraft.player.experienceLevel : 0;

            String coinStr = (balance >= coinCost ? "§a" : "§c") + formatCompactNumber(coinCost) + " Coins";
            String xpStr = (playerLevels >= levelCost ? "§a" : "§c") + levelCost + " Cấp EXP";
            extractor.centeredText(this.font, Component.literal("§eGiá: " + coinStr + " §7| " + xpStr),
                    this.leftPos + 118, this.topPos + 84, 0xFFFFFFFF);
        }
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY) {
        if (this.currentView != MainView.SHOP && slot.index < VTradeScreenHandler.SHOP_SLOT_COUNT) {
            return;
        }

        super.extractSlot(extractor, slot, mouseX, mouseY);

        if (this.currentView == MainView.SHOP && slot.index < VTradeScreenHandler.SHOP_SLOT_COUNT && slot.hasItem()
                && selectedCategory != ShopCategory.BUYBACK) {
            var trend = VMarketEngine.getTrend(slot.getItem());
            extractor.text(this.font, Component.literal(trend.getArrow()).withStyle(trend.direction().getColor()),
                    slot.x + 11, slot.y, 0xFFFFFFFF, true);
        }
        if (this.currentView == MainView.SHOP && slot.index < VTradeScreenHandler.SHOP_SLOT_COUNT && purchaseConfirm.isSlotPending(slot.index)) {
            purchaseConfirm.renderSlotWarningPulse(extractor, slot.x - 1, slot.y - 1, 18);
        }
    }

    private void drawPanel(GuiGraphicsExtractor extractor) {
        InventoryTextures.panel(extractor, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);

        Component title = switch (this.currentView) {
            case SHOP -> Component.translatable("vcoins.title");
            case BLACK_MARKET -> Component.translatable("vcoins.black_market.title");
            case DUPLICATE -> Component.translatable("vcoins.duplicate.title");
        };
        int titleColor = switch (this.currentView) {
            case SHOP -> 0xFFD4AF37;
            case BLACK_MARKET -> 0xFFFFAA00;
            case DUPLICATE -> 0xFF55FFFF;
        };

        int titleX = (this.currentView == MainView.SHOP) ? this.leftPos + 18 : this.leftPos + 62;
        extractor.text(this.font, title, titleX, this.topPos + 9, titleColor, false);
    }

    private void drawSlotGrid(GuiGraphicsExtractor extractor, int relativeX, int relativeY, int columns, int rows) {
        InventoryTextures.slots(extractor, this.leftPos + relativeX, this.topPos + relativeY, columns, rows);
    }

    private void drawTabs(GuiGraphicsExtractor extractor) {
        for (int index = 0; index < TABS.length; index++) {
            TabBounds bounds = getTabBounds(index);
            boolean selected = TABS[index] == this.selectedCategory;
            InventoryTextures.button(extractor, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    true, selected, selected ? 0.65f : 0f);
            if (selected) {
                if (index < TOP_TAB_COUNT) {
                    extractor.fill(bounds.x() + 2, bounds.y() + bounds.height() - 2, bounds.x() + bounds.width() - 2, bounds.y() + bounds.height(), 0xFFD4AF37);
                } else {
                    extractor.fill(bounds.x() + 2, bounds.y(), bounds.x() + bounds.width() - 2, bounds.y() + 2, 0xFFD4AF37);
                }
            }
            Item icon = getTabIcon(TABS[index]);
            extractor.item(new ItemStack(icon), bounds.x() + 5, bounds.y() + 6);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor extractor) {
        int trackX = this.leftPos + SCROLLBAR_X;
        int trackY = this.topPos + SCROLLBAR_Y;
        int maxRows = this.menu.getMaxRows();
        boolean enabled = maxRows > 0;

        InventoryTextures.recess(extractor, trackX, trackY, 12, SCROLLBAR_HEIGHT);
        int travel = SCROLLBAR_HEIGHT - SCROLL_THUMB_HEIGHT;
        int thumbY = trackY + Math.round(this.scrollPosition * travel);
        InventoryTextures.scrollThumb(extractor, trackX, thumbY, 12, SCROLL_THUMB_HEIGHT, enabled);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        if (this.currentView == MainView.BLACK_MARKET) {
            int cardIndex = getBlackMarketCardAt(mouseX, mouseY);
            if (cardIndex >= 0) {
                List<ItemStack> cards = VBlackMarketScreen.getSyncedCards();
                ItemStack stack = (cardIndex < cards.size()) ? cards.get(cardIndex) : ItemStack.EMPTY;
                if (stack.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
                    List<ItemStack> fallback = VBlackMarket.getItemsForPlayer(this.minecraft.player.getUUID());
                    if (cardIndex < fallback.size()) stack = fallback.get(cardIndex);
                }
                boolean isRevealed = ((VBlackMarketScreen.getSyncedRevealedMask() & (1 << cardIndex)) != 0) || bmLocalRevealedCards.contains(cardIndex);
                if (isRevealed && !stack.isEmpty()) {
                    List<Component> tooltip = VeloriaTooltip.withoutPrices(this.getTooltipFromContainerItem(stack));
                    long day = VBlackMarketScreen.getSyncedDay();
                    int seq = VBlackMarketScreen.getSyncedResetSequence();
                    long price = VBlackMarket.getDiscountedPrice(stack, day, seq);
                    int discount = VBlackMarket.getDiscountPercent(stack, day, seq);

                    tooltip.add(Component.empty());
                    if (VBlackMarket.isRomanGodItem(stack)) {
                        tooltip.add(Component.literal("✦ LA MÃ THẦN BẢO ✦").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    }
                    if (discount > 0) {
                        tooltip.add(Component.translatable("vcoins.black_market.discount", discount).withStyle(ChatFormatting.GREEN));
                    }
                    tooltip.add(Component.translatable("vcoins.black_market.buy_price", formatNumber(price)).withStyle(ChatFormatting.YELLOW));
                    boolean isPurchased = (VBlackMarketScreen.getSyncedPurchasedMask() & (1 << cardIndex)) != 0;
                    if (isPurchased) {
                        tooltip.add(Component.translatable("vcoins.black_market.already_bought").withStyle(ChatFormatting.RED));
                    } else {
                        tooltip.add(Component.translatable("vcoins.tooltip.buy_left").withStyle(ChatFormatting.GRAY));
                    }
                    extractor.setTooltipForNextFrame(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY,
                            stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
                    return;
                } else if (!isRevealed) {
                    extractor.setTooltipForNextFrame(this.font, Component.translatable("vcoins.black_market.card_hidden").withStyle(ChatFormatting.GOLD), mouseX, mouseY);
                    return;
                }
            }
        } else if (this.currentView == MainView.DUPLICATE) {
            if (mouseX >= this.leftPos + 40 && mouseX < this.leftPos + 72 && mouseY >= this.topPos + 44 && mouseY < this.topPos + 76 && !selectedDuplicateStack.isEmpty()) {
                extractor.setTooltipForNextFrame(this.font, this.getTooltipFromContainerItem(selectedDuplicateStack), selectedDuplicateStack.getTooltipImage(), mouseX, mouseY,
                        selectedDuplicateStack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
                return;
            }
            if (mouseX >= this.leftPos + 164 && mouseX < this.leftPos + 196 && mouseY >= this.topPos + 44 && mouseY < this.topPos + 76 && !selectedDuplicateStack.isEmpty()) {
                extractor.setTooltipForNextFrame(this.font, this.getTooltipFromContainerItem(selectedDuplicateStack), selectedDuplicateStack.getTooltipImage(), mouseX, mouseY,
                        selectedDuplicateStack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
                return;
            }
        }

        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            if (this.currentView != MainView.SHOP && this.hoveredSlot.index < VTradeScreenHandler.SHOP_SLOT_COUNT) {
                return;
            }
            extractSlotTooltip(extractor, mouseX, mouseY);
            return;
        }

        if (this.currentView == MainView.SHOP) {
            ShopCategory hoveredTab = getHoveredTab(mouseX, mouseY);
            if (hoveredTab != null) {
                extractor.setTooltipForNextFrame(this.font, getTabName(hoveredTab), mouseX, mouseY);
                return;
            }
        }

        super.extractTooltip(extractor, mouseX, mouseY);
    }

    private void extractSlotTooltip(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        ItemStack stack = this.hoveredSlot.getItem();
        List<Component> tooltip = this.getTooltipFromContainerItem(stack);

        extractor.setTooltipForNextFrame(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY,
                stack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE), true);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = VeloriaTooltip.withoutPrices(super.getTooltipFromContainerItem(stack));
        long buyPrice = VCoinsPricing.getPrice(stack);
        if (this.hoveredSlot != null && this.hoveredSlot.index < VTradeScreenHandler.SHOP_SLOT_COUNT) {
            appendShopSlotTooltip(tooltip, stack, buyPrice);
        } else {
            appendInventorySlotTooltip(tooltip, stack, buyPrice);
        }
        return tooltip;
    }

    private void appendShopSlotTooltip(List<Component> tooltip, ItemStack stack, long buyPrice) {
        VMarketEngine.MarketTrend trend = VMarketEngine.getTrend(stack);
        tooltip.add(Component.empty());
        if (this.selectedCategory == ShopCategory.BUYBACK) {
            long totalPrice = safeMultiply(VCoinsPricing.getBuybackPrice(stack), stack.getCount());
            if (buyPrice > 0) {
                MutableComponent priceComp = Component.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                        .withStyle(ChatFormatting.YELLOW);
                priceComp.append(Component.literal(" ")).append(trend.getBadge());
                tooltip.add(priceComp);
            }
            tooltip.add(Component.translatable("vcoins.tooltip.buyback", formatNumber(totalPrice)).withStyle(ChatFormatting.GOLD));
        } else {
            MutableComponent priceComp = Component.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                    .withStyle(ChatFormatting.YELLOW);
            priceComp.append(Component.literal(" ")).append(trend.getBadge());
            tooltip.add(priceComp);
            tooltip.add(Component.translatable(trend.reasonKey()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            tooltip.add(Component.translatable("vcoins.tooltip.buy_left").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("vcoins.tooltip.buy_shift").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("vcoins.tooltip.buy_right", stack.getMaxStackSize()).withStyle(ChatFormatting.GRAY));
        }
    }

    private void appendInventorySlotTooltip(List<Component> tooltip, ItemStack stack, long buyPrice) {
        long sellPrice = VCoinsPricing.getSellPrice(stack);
        VMarketEngine.MarketTrend trend = VMarketEngine.getTrend(stack);

        if (buyPrice > 0 || sellPrice > 0) {
            tooltip.add(Component.empty());
            if (buyPrice > 0) {
                MutableComponent buyComp = Component.translatable("vcoins.tooltip.buy_price", formatNumber(buyPrice))
                        .withStyle(ChatFormatting.YELLOW);
                buyComp.append(Component.literal(" ")).append(trend.getBadge());
                tooltip.add(buyComp);
            }
        }
        if (sellPrice > 0) {
            MutableComponent sellComp = Component.translatable("vcoins.tooltip.sell_price", formatNumber(sellPrice))
                    .withStyle(ChatFormatting.GREEN);
            sellComp.append(Component.literal(" ")).append(VMarketEngine.getSellTrend(stack).getBadge());
            tooltip.add(sellComp);
            tooltip.add(Component.translatable("vcoins.tooltip.sell_shift").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();

        if (this.currentView == MainView.SHOP) {
            for (int index = 0; index < TABS.length; index++) {
                TabBounds bounds = getTabBounds(index);
                if (bounds.contains(mouseX, mouseY)) {
                    selectCategory(TABS[index]);
                    return true;
                }
            }

            int trackX = this.leftPos + SCROLLBAR_X;
            int trackY = this.topPos + SCROLLBAR_Y;
            if (click.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && mouseX >= trackX && mouseX < trackX + 12
                    && mouseY >= trackY && mouseY < trackY + SCROLLBAR_HEIGHT
                    && this.menu.getMaxRows() > 0) {
                this.scrolling = true;
                updateScrollFromMouse(mouseY);
                return true;
            }

            int shopSlot = getShopSlotAt(mouseX, mouseY);
            if (shopSlot >= 0) {
                if (click.button() == InputConstants.MOUSE_BUTTON_LEFT
                        || click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                    boolean buyStack = click.button() == InputConstants.MOUSE_BUTTON_RIGHT || click.hasShiftDown();
                    Slot slot = this.menu.slots.get(shopSlot);
                    if (slot != null && slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        long unitPrice = (this.selectedCategory == ShopCategory.BUYBACK)
                                ? VCoinsPricing.getBuybackPrice(stack)
                                : VCoinsPricing.getPrice(stack);
                        int amount = buyStack ? stack.getMaxStackSize() : 1;
                        long totalCost = safeMultiply(unitPrice, amount);

                        if (purchaseConfirm.checkOrArm(shopSlot, buyStack, stack, totalCost, this.minecraft)) {
                            ClientPlayNetworking.send(new ShopTransactionPayload(shopSlot, buyStack));
                        }
                    }
                }
                return true;
            }
        } else if (this.currentView == MainView.BLACK_MARKET) {
            int card = getBlackMarketCardAt(mouseX, mouseY);
            if (card >= 0) {
                handleBlackMarketCardClick(card, click);
                return true;
            }
        } else if (this.currentView == MainView.DUPLICATE) {
            if (this.hoveredSlot != null && this.hoveredSlot.index >= VTradeScreenHandler.SHOP_SLOT_COUNT) {
                ItemStack item = this.hoveredSlot.getItem();
                if (!item.isEmpty() && VCoinsPricing.isTradeable(item.getItem())) {
                    this.selectedDuplicateSlot = this.hoveredSlot.getContainerSlot();
                    this.selectedDuplicateStack = item.copy();
                    updateDuplicateButtonState();
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private void handleBlackMarketCardClick(int card, net.minecraft.client.input.MouseButtonEvent click) {
        long now = System.currentTimeMillis();
        boolean isRevealed = ((VBlackMarketScreen.getSyncedRevealedMask() & (1 << card)) != 0) || bmLocalRevealedCards.contains(card);
        boolean isPurchased = (VBlackMarketScreen.getSyncedPurchasedMask() & (1 << card)) != 0;
        long start = bmFlipStartTime[card];

        if (isPurchased || (!VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now >= start && now < start + 300L)) {
            return;
        }

        if (!isRevealed) {
            bmLocalRevealedCards.add(card);
            bmFlipStartTime[card] = now;
            bmHasSpawnedStartParticles[card] = false;
            bmHasSpawnedFinishParticles[card] = false;

            ClientPlayNetworking.send(new BlackMarketRevealPayload(card));
            if (this.minecraft != null) {
                float pitch = 0.9f + (card % 8) * 0.1f;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f));
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch));
            }
            updateViewWidgets();
            return;
        }

        // Revealed: trigger purchase
        boolean buyStack = click.button() == InputConstants.MOUSE_BUTTON_RIGHT || click.hasShiftDown();
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
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.scrolling && this.currentView == MainView.SHOP) {
            updateScrollFromMouse(click.y());
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && this.scrolling) {
            this.scrolling = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.currentView == MainView.SHOP) {
            int maxRows = this.menu.getMaxRows();
            if (maxRows > 0 && verticalAmount != 0.0) {
                int currentOffset = Math.round(this.scrollPosition * maxRows);
                int nextOffset = Math.max(0, Math.min(maxRows, currentOffset - (int) Math.signum(verticalAmount)));
                setScrollOffset(nextOffset, maxRows);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxRows = this.menu.getMaxRows();
        if (maxRows <= 0) return;
        int trackY = this.topPos + SCROLLBAR_Y;
        float travel = SCROLLBAR_HEIGHT - SCROLL_THUMB_HEIGHT;
        float value = (float) ((mouseY - trackY - SCROLL_THUMB_HEIGHT / 2.0) / travel);
        this.scrollPosition = Math.max(0.0f, Math.min(1.0f, value));
        setScrollOffset(Math.round(this.scrollPosition * maxRows), maxRows);
    }

    private void setScrollOffset(int offset, int maxRows) {
        this.scrollPosition = maxRows == 0 ? 0.0f : (float) offset / maxRows;
        if (offset == this.lastScrollOffset) return;
        this.lastScrollOffset = offset;
        this.menu.setScrollOffset(offset);
        ClientPlayNetworking.send(new ShopActionPayload("SCROLL", Integer.toString(offset)));
    }

    private int getShopSlotAt(double mouseX, double mouseY) {
        double localX = mouseX - (this.leftPos + SHOP_X);
        double localY = mouseY - (this.topPos + SHOP_Y);
        if (localX < 0 || localY < 0) return -1;
        int column = (int) (localX / SLOT_SPACING);
        int row = (int) (localY / SLOT_SPACING);
        if (column >= VTradeScreenHandler.SHOP_COLUMNS || row >= VTradeScreenHandler.SHOP_ROWS) return -1;
        if (localX - column * SLOT_SPACING >= SLOT_SIZE || localY - row * SLOT_SPACING >= SLOT_SIZE) return -1;
        return column + row * VTradeScreenHandler.SHOP_COLUMNS;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyInput) {
        if (!this.searchBox.isFocused() && (keyInput.key() == InputConstants.KEY_SPACE || keyInput.key() == InputConstants.KEY_RETURN || keyInput.key() == InputConstants.KEY_NUMPADENTER)) {
            if (purchaseConfirm.handleKeyPress((slot, buyStack) -> {
                ClientPlayNetworking.send(new ShopTransactionPayload(slot, buyStack));
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
                }
            })) {
                return true;
            }
        }
        if (this.currentView == MainView.SHOP) {
            if (this.searchBox.keyPressed(keyInput)) return true;
            if (this.searchBox.isFocused() && !keyInput.isEscape()) return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent charInput) {
        if (this.currentView == MainView.SHOP) {
            if (charInput.isAllowedChatCharacter() && !this.searchBox.isFocused()) {
                this.setFocused(this.searchBox);
            }
            if (this.searchBox.charTyped(charInput)) return true;
        }
        return super.charTyped(charInput);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        if (this.currentView == MainView.BLACK_MARKET) {
            renderBmParticles(extractor);
        }
    }

    private void addBmParticle(float x, float y, float vx, float vy, int color, int symbol, int life) {
        int idx = bmParticleCount < BM_MAX_PARTICLES ? bmParticleCount++ : ThreadLocalRandom.current().nextInt(BM_MAX_PARTICLES);
        bmPartX[idx] = x;
        bmPartY[idx] = y;
        bmPartVx[idx] = vx;
        bmPartVy[idx] = vy;
        bmPartColor[idx] = color;
        bmPartSymbol[idx] = (byte) symbol;
        bmPartSpawn[idx] = System.currentTimeMillis();
        bmPartLife[idx] = life;
    }

    private void renderBmParticles(GuiGraphicsExtractor extractor) {
        if (VCoinsPurchaseConfirm.isReducedMotion() || bmParticleCount == 0) {
            bmParticleCount = 0;
            return;
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < bmParticleCount; i++) {
            long elapsed = now - bmPartSpawn[i];
            int life = bmPartLife[i];
            if (elapsed >= life) {
                int last = bmParticleCount - 1;
                if (i != last) {
                    bmPartX[i] = bmPartX[last];
                    bmPartY[i] = bmPartY[last];
                    bmPartVx[i] = bmPartVx[last];
                    bmPartVy[i] = bmPartVy[last];
                    bmPartColor[i] = bmPartColor[last];
                    bmPartSymbol[i] = bmPartSymbol[last];
                    bmPartSpawn[i] = bmPartSpawn[last];
                    bmPartLife[i] = bmPartLife[last];
                }
                bmParticleCount--;
                i--;
            } else {
                float progress = (float) elapsed / life;
                float alpha = 1.0f - progress;
                int a = (int) (alpha * 255.0f);
                if (a > 0) {
                    float curX = bmPartX[i] + bmPartVx[i] * progress * 20.0f;
                    float curY = bmPartY[i] + bmPartVy[i] * progress * 20.0f - progress * 14.0f;
                    int argb = (a << 24) | (bmPartColor[i] & 0x00FFFFFF);
                    extractor.text(this.font, BM_PARTICLE_SYMBOLS[bmPartSymbol[i]], (int) curX, (int) curY, argb, false);
                }
            }
        }
    }

    private void spawnBmCardBurst(int centerX, int centerY) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < 20; i++) {
            double angle = rng.nextDouble(0, TWO_PI);
            float speed = (float) rng.nextDouble(0.5, 1.5);
            addBmParticle(centerX, centerY, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed,
                    0xFFD700, rng.nextInt(BM_PARTICLE_SYMBOLS.length), rng.nextInt(350, 600));
        }
    }

    private void spawnBmAmbientSparkle(int cardX, int cardY, int cardSize) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextFloat() < 0.25f) {
            float ox = cardX + rng.nextFloat() * cardSize;
            float oy = cardY + rng.nextFloat() * cardSize;
            addBmParticle(ox, oy, (rng.nextFloat() - 0.5f) * 0.3f, -0.5f - rng.nextFloat() * 0.3f,
                    rng.nextBoolean() ? 0xFFFFD700 : 0xFFFF4500, rng.nextInt(BM_PARTICLE_SYMBOLS.length), rng.nextInt(300, 500));
        }
    }

    private static void drawCardTexture(GuiGraphicsExtractor extractor, Identifier texture, int x, int y, int width, int height) {
        extractor.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, width, height, 128, 128, 128, 128);
    }

    private ShopCategory getHoveredTab(double mouseX, double mouseY) {
        for (int index = 0; index < TABS.length; index++) {
            if (getTabBounds(index).contains(mouseX, mouseY)) {
                return TABS[index];
            }
        }
        return null;
    }

    private TabBounds getTabBounds(int index) {
        boolean top = index < TOP_TAB_COUNT;
        int rowIndex = top ? index : index - TOP_TAB_COUNT;
        int count = top ? TOP_TAB_COUNT : TABS.length - TOP_TAB_COUNT;
        int totalWidth = count * TAB_WIDTH + (count - 1) * TAB_GAP;
        int startX = this.leftPos + (this.imageWidth - totalWidth) / 2;
        int tabX = startX + rowIndex * (TAB_WIDTH + TAB_GAP);
        int tabY = top ? this.topPos - TAB_HEIGHT + 4 : this.topPos + this.imageHeight - 4;
        return new TabBounds(tabX, tabY, TAB_WIDTH, TAB_HEIGHT);
    }

    private static Item getTabIcon(ShopCategory category) {
        return switch (category) {
            case ALL -> Items.COMPASS;
            case BUILDING -> Items.BRICKS;
            case COLORED -> Items.WOOL.cyan();
            case NATURAL -> Items.GRASS_BLOCK;
            case FUNCTIONAL -> Items.CRAFTING_TABLE;
            case REDSTONE -> Items.REDSTONE;
            case TOOLS -> Items.DIAMOND_PICKAXE;
            case COMBAT -> Items.DIAMOND_SWORD;
            case FOOD -> Items.GOLDEN_APPLE;
            case INGREDIENTS -> Items.IRON_INGOT;
            case SPAWN_EGGS -> Items.PIG_SPAWN_EGG;
            case MISC -> Items.BUNDLE;
            case BLACK_MARKET -> Items.NETHER_STAR;
            case BUYBACK -> Items.CHEST;
        };
    }

    private static Component getTabName(ShopCategory category) {
        return Component.translatable("vcoins.tab." + category.name().toLowerCase(Locale.ROOT));
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

    private static long safeMultiply(long value, int count) {
        try {
            return Math.multiplyExact(value, (long) count);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private record TabBounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
