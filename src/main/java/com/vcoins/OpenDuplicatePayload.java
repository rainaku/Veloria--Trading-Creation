package com.vcoins;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record OpenDuplicatePayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenDuplicatePayload> ID =
            new CustomPayload.Id<>(Identifier.of(VCoinsMod.MOD_ID, "open_duplicate"));
    public static final PacketCodec<RegistryByteBuf, OpenDuplicatePayload> CODEC =
            PacketCodec.unit(new OpenDuplicatePayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
