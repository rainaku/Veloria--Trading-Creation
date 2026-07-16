package com.vcoins;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ShopTransactionPayload(int slotIndex, boolean buyStack) implements CustomPayload {
    public static final CustomPayload.Id<ShopTransactionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VCoinsMod.MOD_ID, "shop_transaction"));

    public static final PacketCodec<RegistryByteBuf, ShopTransactionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ShopTransactionPayload::slotIndex,
            PacketCodecs.BOOLEAN, ShopTransactionPayload::buyStack,
            ShopTransactionPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
