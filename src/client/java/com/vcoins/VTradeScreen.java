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
    private static final Identifier CARD_BACK  = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_back.png");
    private static final Identifier CARD_FRONT = Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "textures/gui/card_front.png");
    // Portrait card: 28×50 (≈ 4:7 ratio like a tarot card)
    private static final int BM_CARD_W       = 28;
    private static final int BM_CARD_H       = 50;
    private static final int BM_CARD_SPACING = 38;  // gap between card left edges
    private static final int BM_CARD_START_X = 13;  // centred in 236px panel
    private static final int BM_CARD_Y       = 38;
    private static final long BM_FLIP_MS     = 480L; // flip animation duration
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final Set<Integer> bmLocalRevealedCards = new HashSet<>();
    private final long[] bmFlipStartTime = new long[5];
    private final boolean[] bmHasSpawnedStartParticles = new boolean[5];
    private final boolean[] bmHasSpawnedMidParticles = new boolean[5];
    private final boolean[] bmHasSpawnedFinishParticles = new boolean[5];

    // Particle pool for Black Market (expanded for legendary god pull fireworks)
    private static final int BM_MAX_PARTICLES = 256;
    private static final String[] BM_PARTICLE_SYMBOLS = {"✦", "✧", "⋆", "★", "⚡", "☼", "⚜", "👑", "•", "ᛟ", "ᚱ", "◇"};
    private long bmGodPullStartTime = 0L;
    private int bmGodPullCardIndex = -1;
    private static final long BM_GOD_CINEMATIC_DURATION_MS = 2500L;
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
                bmFlipStartTime[i] = now + i * 75L;
                bmHasSpawnedStartParticles[i] = false;
                bmHasSpawnedMidParticles[i] = false;
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
        Arrays.fill(bmHasSpawnedMidParticles, false);
        Arrays.fill(bmHasSpawnedFinishParticles, false);
        bmGodPullStartTime = 0L;
        bmGodPullCardIndex = -1;
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

        // When not in SHOP view, the vanilla super call above still drew gray slot-cell
        // backgrounds for all 45 shop slots. Erase them with a solid opaque cover that
        // matches the panel's inner background colour so nothing bleeds through.
        if (this.currentView != MainView.SHOP) {
            int coverX1 = this.leftPos + VTradeScreenHandler.SHOP_X - 1;
            int coverY1 = this.topPos  + VTradeScreenHandler.SHOP_Y - 1;
            int coverX2 = coverX1 + VTradeScreenHandler.SHOP_COLUMNS * 18 + 2;
            int coverY2 = coverY1 + VTradeScreenHandler.SHOP_ROWS    * 18 + 2;
            extractor.fill(coverX1, coverY1, coverX2, coverY2, 0xFF0A0818);
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
            boolean hasNotStartedYet = !VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now < start;
            boolean isFlipping  = !VCoinsPurchaseConfirm.isReducedMotion()
                    && isRevealed && start > 0 && now >= start && now < start + BM_FLIP_MS;

            if (!isRevealed || hasNotStartedYet) {
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
                float raw = (float) (now - start) / (float) BM_FLIP_MS;
                float p = Math.clamp(raw, 0.0f, 1.0f);

                float ease = (1.0f - (float) Math.cos(p * Math.PI)) * 0.5f;
                float angle = ease * (float) Math.PI;
                float cosVal = (float) Math.cos(angle);
                boolean isBackSide = cosVal > 0.0f;
                float scaleX = Math.abs(cosVal);

                float lift = (float) Math.sin(p * Math.PI);
                float liftY = isGod ? (lift * 9.5f) : (lift * 5.5f);
                float zPop = isGod ? (1.0f + 0.15f * lift) : (1.0f + 0.05f * lift);

                float cx = cardBaseX + BM_CARD_W * 0.5f;
                float cy = cardBaseY + BM_CARD_H * 0.5f - liftY;
                int halfW = BM_CARD_W / 2;
                int halfH = BM_CARD_H / 2;

                // Soft drop shadow beneath lifted card
                float shadowW = (BM_CARD_W - 2) * (0.35f + 0.65f * scaleX);
                int shadowAlpha = (int) ((0.42f - lift * 0.18f) * 255.0f);
                if (shadowAlpha > 0) {
                    extractor.fill(
                            (int) (cx - shadowW * 0.5f), cardBaseY + BM_CARD_H - 2,
                            (int) (cx + shadowW * 0.5f), cardBaseY + BM_CARD_H + 3,
                            (shadowAlpha << 24)
                    );
                }

                if (!bmHasSpawnedStartParticles[i]) {
                    bmHasSpawnedStartParticles[i] = true;
                    spawnBmCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2);
                }

                if (!isBackSide && !bmHasSpawnedMidParticles[i]) {
                    bmHasSpawnedMidParticles[i] = true;
                    if (isGod) {
                        bmGodPullStartTime = System.currentTimeMillis();
                        bmGodPullCardIndex = i;
                        triggerBmGodPullCelebration(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                    } else {
                        spawnBmCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2);
                        if (this.minecraft != null) {
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED, 1.25f));
                        }
                    }
                }

                extractor.pose().pushMatrix();
                extractor.pose().translate(cx, cy);
                extractor.pose().scale(Math.max(0.015f, scaleX) * zPop, zPop);

                if (isBackSide) {
                    if (isGod) {
                        extractor.fill(-halfW - 1, -halfH - 1, halfW + 1, halfH + 1, 0x88FFD700);
                    }
                    drawCardTexture(extractor, CARD_BACK, -halfW, -halfH, BM_CARD_W, BM_CARD_H);

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

                    if (!stack.isEmpty()) {
                        extractor.item(stack, -8, -8 - 4);
                        extractor.itemDecorations(this.font, stack, -8, -8 - 4);
                    }

                    // Divine Supernova flash for God Item, or warm white-gold gleam for normal items
                    if (isGod && revealT <= 0.75f) {
                        float flashT = revealT / 0.75f;
                        float flashIntensity = (float) Math.sin(flashT * Math.PI);
                        int flashAlpha = (int) (flashIntensity * 230);
                        if (flashAlpha > 0) {
                            extractor.fill(-halfW - 4, -halfH - 4, halfW + 4, halfH + 4, (flashAlpha << 24) | 0xFFFFF5);
                            extractor.fill(-halfW - 1, -halfH - 1, halfW + 1, halfH + 1, (flashAlpha << 24) | 0xFFFFD700);
                        }
                    } else if (revealT <= 0.65f) {
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

                if (p >= 0.95f && !bmHasSpawnedFinishParticles[i]) {
                    bmHasSpawnedFinishParticles[i] = true;
                    if (isGod) {
                        triggerBmGodFinishCelebration(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2, stack);
                    } else {
                        spawnBmCardBurst(cardBaseX + BM_CARD_W / 2, cardBaseY + BM_CARD_H / 2);
                    }
                }

            } else {
                // ─── FULLY REVEALED CARD ───
                // Card border
                if (isGod) {
                    float pulse = (float)(0.5 + 0.5 * Math.sin(now * 0.007));
                    int borderAlpha = (int) (190 + pulse * 65);
                    // Tier 1: Outer cosmic blaze
                    extractor.fill(cardBaseX - 3, cardBaseY - 3, cardBaseX + BM_CARD_W + 3, cardBaseY + BM_CARD_H + 3, ((int)(borderAlpha * 0.6f) << 24) | 0xFFFF4500);
                    // Tier 2: Outer divine golden aura border
                    extractor.fill(cardBaseX - 2, cardBaseY - 2, cardBaseX + BM_CARD_W + 2, cardBaseY + BM_CARD_H + 2, (borderAlpha << 24) | 0xFFFFD700);
                    // Tier 3: Inner imperial crimson border
                    extractor.fill(cardBaseX - 1, cardBaseY - 1, cardBaseX + BM_CARD_W + 1, cardBaseY + BM_CARD_H + 1, 0xFF8B0000);

                    // 4 Corner Celestial Sun Sparks
                    float cornerPhase = (float) Math.sin(now * 0.006);
                    int cornerAlpha = (int) (160 + cornerPhase * 80);
                    int cornerCol = (cornerAlpha << 24) | 0xFFFFD700;
                    extractor.text(this.font, "☼", cardBaseX - 5, cardBaseY - 5, cornerCol, false);
                    extractor.text(this.font, "☼", cardBaseX + BM_CARD_W - 2, cardBaseY - 5, cornerCol, false);
                    extractor.text(this.font, "☼", cardBaseX - 5, cardBaseY + BM_CARD_H - 2, cornerCol, false);
                    extractor.text(this.font, "☼", cardBaseX + BM_CARD_W - 2, cardBaseY + BM_CARD_H - 2, cornerCol, false);

                    spawnBmAmbientSparkle(cardBaseX, cardBaseY, BM_CARD_W);
                } else {
                    // Normal card: subtle gold-tinted 1px border
                    extractor.fill(cardBaseX - 1, cardBaseY - 1, cardBaseX + BM_CARD_W + 1, cardBaseY,               0x88D4AF37);
                    extractor.fill(cardBaseX - 1, cardBaseY + BM_CARD_H, cardBaseX + BM_CARD_W + 1, cardBaseY + BM_CARD_H + 1, 0x44D4AF37);
                    extractor.fill(cardBaseX - 1, cardBaseY,             cardBaseX,                 cardBaseY + BM_CARD_H, 0x44D4AF37);
                    extractor.fill(cardBaseX + BM_CARD_W, cardBaseY,     cardBaseX + BM_CARD_W + 1, cardBaseY + BM_CARD_H, 0x44D4AF37);
                }

                drawCardTexture(extractor, CARD_FRONT, cardBaseX, cardBaseY, BM_CARD_W, BM_CARD_H);

                // Item centred in card
                if (!stack.isEmpty()) {
                    if (isGod) {
                        extractor.fill(cardBaseX + 3, cardBaseY + (BM_CARD_H - 16) / 2 - 5, cardBaseX + BM_CARD_W - 3, cardBaseY + (BM_CARD_H - 16) / 2 + 13, 0x50FFD700);
                    }
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
                        long nowMs = System.currentTimeMillis();
                        int badgeW = 32;
                        int badgeH = 9;
                        int badgeX = cardBaseX + (BM_CARD_W - badgeW) / 2;
                        int badgeY = cardBaseY + BM_CARD_H + 2;
                        extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xEE140206);
                        extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, 0xFFFFD700);
                        extractor.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, 0xFFFFD700);
                        int textColor = (nowMs % 800 < 400) ? 0xFFFFD700 : 0xFFFFF0;
                        extractor.centeredText(this.font, Component.literal("✦ THẦN ✦"), cardBaseX + BM_CARD_W / 2, badgeY + 1, textColor);
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
                long start = bmFlipStartTime[cardIndex];
                long now = System.currentTimeMillis();
                boolean isFlipping = !VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now >= start && now < start + BM_FLIP_MS;
                if (isFlipping) {
                    return;
                }
                boolean hasNotStartedYet = !VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now < start;
                if (isRevealed && !hasNotStartedYet && !stack.isEmpty()) {
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

        if (isPurchased || (!VCoinsPurchaseConfirm.isReducedMotion() && isRevealed && start > 0 && now >= start && now < start + BM_FLIP_MS)) {
            return;
        }

        if (!isRevealed) {
            bmLocalRevealedCards.add(card);
            bmFlipStartTime[card] = now;
            bmHasSpawnedStartParticles[card] = false;
            bmHasSpawnedMidParticles[card] = false;
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
        long now = System.currentTimeMillis();
        long godElapsed = (bmGodPullStartTime > 0L) ? (now - bmGodPullStartTime) : 999999L;
        boolean shaking = (this.currentView == MainView.BLACK_MARKET) && godElapsed < 420L && !VCoinsPurchaseConfirm.isReducedMotion();
        if (shaking) {
            float shakeProg = (float) godElapsed / 420.0f;
            float shakeAmp = (1.0f - shakeProg) * (1.0f - shakeProg) * 5.5f;
            float shakeX = (float) Math.sin(godElapsed * 0.14f) * shakeAmp;
            float shakeY = (float) Math.cos(godElapsed * 0.18f) * (shakeAmp * 0.65f);
            extractor.pose().pushMatrix();
            extractor.pose().translate(shakeX, shakeY);
        }

        super.extractRenderState(extractor, mouseX, mouseY, delta);
        if (this.currentView == MainView.BLACK_MARKET) {
            renderBmGodPullCinematic(extractor);
            renderBmParticles(extractor);
        }

        if (shaking) {
            extractor.pose().popMatrix();
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
                    float ease = (1.0f - (float) Math.pow(1.0f - progress, 2.5f));
                    float curX = bmPartX[i] + bmPartVx[i] * ease * 42.0f;
                    float curY = bmPartY[i] + bmPartVy[i] * ease * 42.0f - progress * progress * 24.0f;
                    int argb = (a << 24) | (bmPartColor[i] & 0x00FFFFFF);
                    extractor.text(this.font, BM_PARTICLE_SYMBOLS[bmPartSymbol[i]], (int) curX, (int) curY, argb, false);
                }
            }
        }
    }

    private void renderBmGodPullCinematic(GuiGraphicsExtractor extractor) {
        if (VCoinsPurchaseConfirm.isReducedMotion()) return;
        if (bmGodPullStartTime <= 0L || bmGodPullCardIndex < 0) return;

        long now = System.currentTimeMillis();
        long elapsed = now - bmGodPullStartTime;
        if (elapsed >= BM_GOD_CINEMATIC_DURATION_MS) {
            return;
        }

        // Intensity curve: rises in first 200ms, holds, then gently fades
        float intensity;
        if (elapsed < 200L) {
            intensity = (float) elapsed / 200.0f;
        } else {
            intensity = 1.0f - (float) (elapsed - 200L) / (float) (BM_GOD_CINEMATIC_DURATION_MS - 200L);
        }
        intensity = Math.clamp(intensity, 0.0f, 1.0f);

        int cardBaseX = this.leftPos + BM_CARD_START_X + bmGodPullCardIndex * BM_CARD_SPACING;
        int cardBaseY = this.topPos + BM_CARD_Y;
        float cx = cardBaseX + BM_CARD_W * 0.5f;
        float cy = cardBaseY + BM_CARD_H * 0.5f;

        // 1. Time-Freeze Supernova Flash (first 260ms of turnover)
        if (elapsed < 260L) {
            float flashT = (float) elapsed / 260.0f;
            float flashCurve = (1.0f - flashT) * (1.0f - flashT);
            int flashA = (int) (flashCurve * 205);
            if (flashA > 0) {
                extractor.fill(0, 0, this.width, this.height, (flashA << 24) | 0xFFFFF8);
                int haloR = (int) (BM_CARD_W * 3.2f * (1.0f - flashT * 0.4f));
                extractor.fill((int) (cx - haloR), (int) (cy - haloR), (int) (cx + haloR), (int) (cy + haloR),
                        ((flashA / 2) << 24) | 0xFFFFD700);
            }
        }

        // 2. Cinematic Ambient Darkness & Letterbox Bars
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
        int baseW = (int) (BM_CARD_W * (1.3f + 0.25f * (float) Math.sin(now * 0.007f)));
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
            float xOff = (float) Math.sin(s * 1.7f + now * 0.003f) * (BM_CARD_W * 0.55f);
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
        List<ItemStack> cards = VBlackMarketScreen.getSyncedCards();
        ItemStack godStack = (bmGodPullCardIndex >= 0 && bmGodPullCardIndex < cards.size()) ? cards.get(bmGodPullCardIndex) : ItemStack.EMPTY;
        if (godStack.isEmpty() && this.minecraft != null && this.minecraft.player != null) {
            List<ItemStack> fallback = VBlackMarket.getItemsForPlayer(this.minecraft.player.getUUID());
            if (bmGodPullCardIndex >= 0 && bmGodPullCardIndex < fallback.size()) godStack = fallback.get(bmGodPullCardIndex);
        }

        if (!godStack.isEmpty()) {
            float hoverBob = (float) Math.sin(now * 0.008f) * 3.5f;
            float heroScale = 1.16f + 0.04f * (float) Math.sin(now * 0.006f);
            int halfW = BM_CARD_W / 2;
            int halfH = BM_CARD_H / 2;

            extractor.pose().pushMatrix();
            extractor.pose().translate(cx, cy - hoverBob);
            extractor.pose().scale(heroScale, heroScale);

            // 4-layer blazing solar corona
            extractor.fill(-halfW - 5, -halfH - 5, halfW + 5, halfH + 5, 0x90FF4500);
            extractor.fill(-halfW - 3, -halfH - 3, halfW + 3, halfH + 3, 0xD0FFD700);
            extractor.fill(-halfW - 2, -halfH - 2, halfW + 2, halfH + 2, 0xFFFF8C00);
            extractor.fill(-halfW - 1, -halfH - 1, halfW + 1, halfH + 1, 0xFF8B0000);

            drawCardTexture(extractor, CARD_FRONT, -halfW, -halfH, BM_CARD_W, BM_CARD_H);

            // Holy solar halo on pedestal
            extractor.fill(-halfW + 3, -halfH + 3, halfW - 3, halfH - 3, 0x66FFD700);
            extractor.fill(-10, -14, 10, 6, 0x90FFA500);

            extractor.item(godStack, -8, -8 - 4);
            extractor.itemDecorations(this.font, godStack, -8, -8 - 4);

            // Golden badge
            int badgeW = 32;
            int badgeH = 9;
            int badgeX = -badgeW / 2;
            int badgeY = halfH - 11;
            extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xEE140206);
            extractor.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 1, 0xFFFFD700);
            extractor.fill(badgeX, badgeY + badgeH - 1, badgeX + badgeW, badgeY + badgeH, 0xFFFFD700);
            int textColor = (now % 800 < 400) ? 0xFFFFD700 : 0xFFFFF0;
            extractor.centeredText(this.font, Component.literal("✦ THẦN ✦"), 0, badgeY + 1, textColor);

            extractor.pose().popMatrix();
        }

        // 7. Imperial Floating Decree Banner ("✦ LA MÃ THẦN BẢO GIÁNG THẾ ✦")
        if (elapsed > 100L) {
            float bannerT = Math.clamp((elapsed - 100L) / 280.0f, 0.0f, 1.0f);
            float bannerAlphaF = bannerT * intensity;
            int textAlpha = (int) (bannerAlphaF * 255.0f);
            if (textAlpha > 10) {
                float bob = (float) Math.sin(now * 0.005f) * 2.5f;
                int bannerY = (int) (this.topPos + 10 - bob);
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
                Component titleComp = Component.literal("✦ LA MÃ THẦN BẢO GIÁNG THẾ ✦");
                extractor.centeredText(this.font, titleComp, screenMidX + 1, bannerY + 2, (textAlpha << 24) | 0x880000);
                extractor.centeredText(this.font, titleComp, screenMidX, bannerY + 1, (textAlpha << 24) | 0xFFFFF0);

                // Subtitle: ⚜ QUYỀN NĂNG TỐI CAO ĐÃ THỨC TỈNH ⚜
                Component subComp = Component.literal("⚜ QUYỀN NĂNG TỐI CAO ĐÃ THỨC TỈNH ⚜");
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

    private void triggerBmGodPullCelebration(int centerX, int centerY, ItemStack stack) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 0.90f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.15f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 0.70f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_ACTIVATE, 1.35f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.4f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f));
        }

        int[] palette = {0xFFFFD700, 0xFFFF4500, 0xFFFF2244, 0xFFFFFFFF, 0xFFFFE082, 0xFFFF8C00, 0xFFFF1744};
        for (int i = 0; i < 42; i++) {
            double angle = (TWO_PI / 42.0) * i + rng.nextDouble(-0.15, 0.15);
            float speed = (float) rng.nextDouble(1.8, 3.6);
            int color = palette[rng.nextInt(palette.length)];
            int symIdx = rng.nextInt(BM_PARTICLE_SYMBOLS.length);
            int life = rng.nextInt(650, 1050);
            addBmParticle(centerX, centerY, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed,
                    color, symIdx, life);
        }

        for (int i = 0; i < 24; i++) {
            float ox = centerX + (rng.nextFloat() - 0.5f) * BM_CARD_W * 1.4f;
            float oy = centerY + (rng.nextFloat() - 0.5f) * BM_CARD_H * 0.6f;
            float vx = (rng.nextFloat() - 0.5f) * 0.7f;
            float vy = -1.2f - rng.nextFloat() * 1.8f;
            int color = rng.nextBoolean() ? 0xFFFFD700 : 0xFFFF8C00;
            int symIdx = rng.nextInt(4);
            int life = rng.nextInt(850, 1500);
            addBmParticle(ox, oy, vx, vy, color, symIdx, life);
        }

        int[] royalSyms = {4, 6, 7, 5, 9, 10}; // ⚡, ⚜, 👑, ☼, ᛟ, ᚱ
        for (int i = 0; i < 12; i++) {
            double angle = rng.nextDouble(0, TWO_PI);
            float speed = (float) rng.nextDouble(0.8, 1.8);
            int color = rng.nextBoolean() ? 0xFFFFD700 : 0xFFFF2244;
            int symIdx = royalSyms[rng.nextInt(royalSyms.length)];
            int life = rng.nextInt(750, 1200);
            addBmParticle(centerX, centerY, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed,
                    color, symIdx, life);
        }
    }

    private void triggerBmGodFinishCelebration(int centerX, int centerY, ItemStack stack) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.25f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f));
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.45f));
        }

        int[] palette = {0xFFFFD700, 0xFFFFE082, 0xFFFF8C00, 0xFFFFFFFF, 0xFFFF1744, 0xFFFF4500};
        for (int i = 0; i < 42; i++) {
            double angle = (TWO_PI / 42.0) * i + rng.nextDouble(-0.1, 0.1);
            float speed = (float) rng.nextDouble(1.2, 3.0);
            int color = palette[rng.nextInt(palette.length)];
            int symIdx = rng.nextInt(BM_PARTICLE_SYMBOLS.length);
            int life = rng.nextInt(700, 1200);
            addBmParticle(centerX, centerY, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed,
                    color, symIdx, life);
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
