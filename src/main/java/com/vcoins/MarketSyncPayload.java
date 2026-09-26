package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public record MarketSyncPayload(long serverEpochSecond, Map<String, Float> volumeModifiers) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MarketSyncPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "market_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketSyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarLong(payload.serverEpochSecond());
                buf.writeVarInt(payload.volumeModifiers().size());
                for (Map.Entry<String, Float> entry : payload.volumeModifiers().entrySet()) {
                    buf.writeUtf(entry.getKey());
                    buf.writeFloat(entry.getValue());
                }
            },
            buf -> {
                long epoch = buf.readVarLong();
                int size = buf.readVarInt();
                Map<String, Float> map = new HashMap<>(size);
                for (int i = 0; i < size; i++) {
                    map.put(buf.readUtf(), buf.readFloat());
                }
                return new MarketSyncPayload(epoch, map);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
