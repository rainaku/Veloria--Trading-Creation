package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BlackMarketSyncPayload(
        long secondsUntilReset,
        int revealedMask,
        int purchasedMask,
        long epochDay,
        int bankedResets,
        int resetSequence
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlackMarketSyncPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "black_market_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlackMarketSyncPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, BlackMarketSyncPayload::secondsUntilReset,
                    ByteBufCodecs.VAR_INT, BlackMarketSyncPayload::revealedMask,
                    ByteBufCodecs.VAR_INT, BlackMarketSyncPayload::purchasedMask,
                    ByteBufCodecs.VAR_LONG, BlackMarketSyncPayload::epochDay,
                    ByteBufCodecs.VAR_INT, BlackMarketSyncPayload::bankedResets,
                    ByteBufCodecs.VAR_INT, BlackMarketSyncPayload::resetSequence,
                    BlackMarketSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
