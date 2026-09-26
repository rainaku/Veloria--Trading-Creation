package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VCoinsSyncPayload(long coins) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VCoinsSyncPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("vcoins", "sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VCoinsSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, VCoinsSyncPayload::coins,
            VCoinsSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
