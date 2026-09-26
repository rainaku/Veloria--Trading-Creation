package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenShopPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenShopPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("vcoins", "open_shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenShopPayload> CODEC = StreamCodec.unit(new OpenShopPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
