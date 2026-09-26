package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ShopActionPayload(String action, String data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShopActionPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "shop_action"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopActionPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ShopActionPayload::action,
        ByteBufCodecs.STRING_UTF8, ShopActionPayload::data,
        ShopActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
