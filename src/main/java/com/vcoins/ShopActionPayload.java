package com.vcoins;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ShopActionPayload(String action, String data) implements CustomPayload {
    public static final CustomPayload.Id<ShopActionPayload> ID = new CustomPayload.Id<>(Identifier.of(VCoinsMod.MOD_ID, "shop_action"));
    
    public static final PacketCodec<RegistryByteBuf, ShopActionPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.STRING, ShopActionPayload::action,
        PacketCodecs.STRING, ShopActionPayload::data,
        ShopActionPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
