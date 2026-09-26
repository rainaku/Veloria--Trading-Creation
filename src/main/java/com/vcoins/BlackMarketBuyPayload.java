package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record BlackMarketBuyPayload(int slotIndex, boolean buyStack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlackMarketBuyPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "black_market_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlackMarketBuyPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BlackMarketBuyPayload::slotIndex,
                    ByteBufCodecs.BOOL, BlackMarketBuyPayload::buyStack,
                    BlackMarketBuyPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
