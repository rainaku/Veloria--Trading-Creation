package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ShopTransactionPayload(int slotIndex, boolean buyStack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShopTransactionPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "shop_transaction"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopTransactionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShopTransactionPayload::slotIndex,
            ByteBufCodecs.BOOL, ShopTransactionPayload::buyStack,
            ShopTransactionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
