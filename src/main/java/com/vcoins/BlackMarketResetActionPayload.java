package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BlackMarketResetActionPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlackMarketResetActionPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "black_market_reset_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlackMarketResetActionPayload> CODEC =
            StreamCodec.unit(new BlackMarketResetActionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
