package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenBlackMarketPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenBlackMarketPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "open_black_market"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBlackMarketPayload> CODEC =
            StreamCodec.unit(new OpenBlackMarketPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
