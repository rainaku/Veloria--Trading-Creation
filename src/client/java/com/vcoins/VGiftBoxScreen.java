package com.vcoins;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.sounds.SoundEvents;
import java.util.*;

public final class VGiftBoxScreen extends VeloriaContainerScreen<VGiftBoxMenu> {
    private final List<Button> actions = new ArrayList<>();
    private static final int[] COLORS = {0x69B3C0,0x70C06F,0x648BF2,0xC069F3,0xF8BF48};
    private static final Identifier[][] TEXTURES = new Identifier[5][2];
    static {
        for (int i=0;i<5;i++) for (int part=0;part<2;part++) TEXTURES[i][part] = Identifier.fromNamespaceAndPath(
                "vcoins", "textures/gui/gift_"+i+"_"+(part==0?"base":"lid")+".png");
    }
    private int revision, opened;
    private long start, sent;
    private boolean pending, sounded;
    public VGiftBoxScreen(VGiftBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 354, 260); titleLabelX=inventoryLabelX=1000;
    }
    @Override protected void init() {
        super.init(); actions.clear(); revision=menu.value(5); opened=menu.value(9);
        addRenderableWidget(VeloriaButton.create(Component.translatable("fortuna.back"), b ->
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new OpenShopPayload()))
                .bounds(leftPos+12,topPos+10,48,18).build());
        for (int i=0;i<5;i++) {
            final int tier=i;
            actions.add(addRenderableWidget(VeloriaButton.create(Component.translatable("vcoins.gift.buy"), b->send(tier))
                    .bounds(leftPos+12+i*67,topPos+127,62,18)
                    .tooltip(Tooltip.create(Component.translatable("vcoins.gift.odds"))).build()));
            actions.add(addRenderableWidget(VeloriaButton.create(Component.translatable("vcoins.gift.open"), b->send(tier+5))
                    .bounds(leftPos+12+i*67,topPos+148,62,18).build()));
        }
    }
    private void send(int action) {
        if (pending || minecraft==null || minecraft.gameMode==null) return;
        pending=true; sent=System.currentTimeMillis();
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);
    }
    @Override protected void containerTick() {
        super.containerTick(); long now=System.currentTimeMillis();
        if(menu.value(5)!=revision) { revision=menu.value(5);pending=false; }
        if(menu.value(9)!=opened) {
            opened=menu.value(9);start=now;sounded=false;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHEST_OPEN,0.9f));
        }
        if(pending && now-sent>3000) pending=false;
        boolean animating=start>0 && now-start<3600;
        long balance=minecraft.player==null?0:VCoinsState.getClientCoins(minecraft.player.getUUID());
        for(int i=0;i<5;i++) {
            actions.get(i*2).visible=actions.get(i*2+1).visible=!animating;
            actions.get(i*2).active=!pending&&!animating&&balance>=VGiftBox.PRICES[i];
            actions.get(i*2+1).active=!pending&&!animating&&menu.value(i)>0;
        }
        if(start>0&&!sounded&&(now-start>=650||VCoinsPurchaseConfirm.isReducedMotion())) {
            sounded=true;minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    menu.value(6)==3?SoundEvents.UI_TOAST_CHALLENGE_COMPLETE:SoundEvents.AMETHYST_BLOCK_CHIME,1.1f));
        }
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) {
        super.extractBackground(g,mx,my,delta);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xF5111020);
        InventoryTextures.frame(g,leftPos,topPos,imageWidth,imageHeight,0xFFC9A75E);
        g.centeredText(font,Component.literal("GIFT ATELIER"),leftPos+177,topPos+12,0xFFF0DDAA);
        long balance=minecraft.player==null?0:VCoinsState.getClientCoins(minecraft.player.getUUID());
        g.centeredText(font,Component.literal(String.format(Locale.ROOT,"%,d coins",balance)),leftPos+177,topPos+27,0xFFB9B2CB);
        for(int i=0;i<5;i++) {
            int cx=leftPos+43+i*67;
            g.centeredText(font,Component.literal("TIER "+(i+1)),cx,topPos+46,0xFF000000|COLORS[i]);
            box(g,i,cx,topPos+79,1,0,0);
            g.centeredText(font,Component.literal(String.format(Locale.ROOT,"%,d",VGiftBox.PRICES[i])),cx,topPos+106,0xFFF0DDAA);
            g.centeredText(font,Component.translatable("vcoins.gift.owned",menu.value(i)),cx,topPos+117,0xFFB9B2CB);
        }
        g.centeredText(font,Component.literal("75% / 20% / 4.5% / 0.5%"),leftPos+177,topPos+179,0xFFE0D2A2);
        g.centeredText(font,Component.translatable("vcoins.gift.bound"),leftPos+177,topPos+194,0xFFB9B2CB);
        g.centeredText(font,Component.translatable("vcoins.gift.menu_hint"),leftPos+177,topPos+222,0xFFB9B2CB);
        if(start>0) renderOpening(g);
    }
    private void box(GuiGraphicsExtractor g,int tier,float cx,float cy,float scale,float lift,float shake) {
        VeloriaCardVfx.glow(g,cx,cy+12*scale,60*scale,0.3f,COLORS[tier]);
        VeloriaCardVfx.layer(g,TEXTURES[tier][0],cx+shake,cy,54*scale,54*scale,0,1,64);
        VeloriaCardVfx.layer(g,TEXTURES[tier][1],cx+shake,cy-lift,54*scale,54*scale,-lift*0.006f,1,64);
    }
    private void renderOpening(GuiGraphicsExtractor g) {
        long elapsed=System.currentTimeMillis()-start;
        boolean reduced=VCoinsPurchaseConfirm.isReducedMotion();
        if(elapsed>3600)return;
        float fade=Math.clamp((3600-elapsed)/500f,0,1);
        int tier=Math.clamp(menu.value(8),0,4),quality=Math.clamp(menu.value(6),0,3);
        float cx=leftPos+177,cy=topPos+118;
        g.fill(leftPos+4,topPos+36,leftPos+350,topPos+252,VeloriaCardVfx.tint(fade*0.96f,0x100C1E));
        float burst=reduced?1:Math.clamp((elapsed-650)/650f,0,1);
        float shake=elapsed<650&&!reduced?(float)Math.sin(elapsed*0.055)*Math.min(3,elapsed/180f):0;
        if(!reduced) {
            VeloriaCardVfx.glow(g,cx,cy,100+burst*120,fade*(0.3f+quality*0.15f),COLORS[tier]);
            for(int i=0;i<18+quality*10;i++) {
                double a=i*2.399963;
                float radius=burst*(55+i%5*14);
                VeloriaCardVfx.spark(g,cx+(float)Math.cos(a)*radius,cy+(float)Math.sin(a)*radius,
                        5+quality*2,(float)a,fade*(1-burst*0.65f),COLORS[tier],quality==3);
            }
        }
        box(g,tier,cx,cy+18,1.8f,burst*48,shake);
        if(burst>0.5f) {
            g.pose().pushMatrix();g.pose().translate(cx,cy-20);g.pose().scale(2,2);
            g.item(menu.reward(),-8,-8);g.pose().popMatrix();
            g.centeredText(font,menu.reward().getHoverName(),(int)cx,topPos+203,0xFFFFFFFF);
            g.centeredText(font,Component.translatable("vcoins.gift.quality."+quality),(int)cx,topPos+219,0xFF000000|COLORS[tier]);
        }
    }
}
