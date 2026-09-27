package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DuplicateActionPayload(int slotIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DuplicateActionPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "duplicate_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DuplicateActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DuplicateActionPayload::slotIndex,
            DuplicateActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
