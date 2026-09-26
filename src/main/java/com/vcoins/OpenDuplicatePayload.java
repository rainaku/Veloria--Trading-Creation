package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenDuplicatePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenDuplicatePayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "open_duplicate"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDuplicatePayload> CODEC =
            StreamCodec.unit(new OpenDuplicatePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
