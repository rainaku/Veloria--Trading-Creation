package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BlackMarketRevealPayload(int slotIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlackMarketRevealPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "black_market_reveal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlackMarketRevealPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BlackMarketRevealPayload::slotIndex,
                    BlackMarketRevealPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
