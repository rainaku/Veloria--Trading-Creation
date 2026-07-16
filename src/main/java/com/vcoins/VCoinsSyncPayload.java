package com.vcoins;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record VCoinsSyncPayload(long coins) implements CustomPayload {
    public static final CustomPayload.Id<VCoinsSyncPayload> ID = new CustomPayload.Id<>(Identifier.of("vcoins", "sync"));

    public static final PacketCodec<RegistryByteBuf, VCoinsSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, VCoinsSyncPayload::coins,
            VCoinsSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
