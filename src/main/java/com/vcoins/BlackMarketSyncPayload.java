package com.vcoins;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import net.minecraft.world.item.ItemStack;

public record BlackMarketSyncPayload(
        long secondsUntilReset,
        int revealedMask,
        int purchasedMask,
        long epochDay,
        int bankedResets,
        int resetSequence,
        List<ItemStack> items,
        int lifetimeFlipCount,
        /** Current lucky bonus %, increments of 5 per 130 non-legend flips */
        int luckyPercent
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlackMarketSyncPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VCoinsMod.MOD_ID, "black_market_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlackMarketSyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarLong(payload.secondsUntilReset());
                buf.writeVarInt(payload.revealedMask());
                buf.writeVarInt(payload.purchasedMask());
                buf.writeVarLong(payload.epochDay());
                buf.writeVarInt(payload.bankedResets());
                buf.writeVarInt(payload.resetSequence());
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, payload.items());
                buf.writeVarInt(payload.lifetimeFlipCount());
                buf.writeVarInt(payload.luckyPercent());
            },
            buf -> new BlackMarketSyncPayload(
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
