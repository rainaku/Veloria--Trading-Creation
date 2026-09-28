package com.vcoins;

import java.util.ArrayList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.ItemStack;
import com.mojang.datafixers.util.Pair;

/** Local singleplayer channels pass packet objects without running network codecs. */
public final class GiftBoxPackets {
    private GiftBoxPackets() {}

    public static Packet<?> hideContents(Packet<?> packet) {
        if (packet instanceof ClientboundContainerSetSlotPacket p)
            return new ClientboundContainerSetSlotPacket(p.getContainerId(), p.getStateId(), p.getSlot(), VGiftBox.networkView(p.getItem()));
        if (packet instanceof ClientboundContainerSetContentPacket p)
            return new ClientboundContainerSetContentPacket(p.containerId(), p.stateId(),
                    p.items().stream().map(VGiftBox::networkView).toList(), VGiftBox.networkView(p.carriedItem()));
        if (packet instanceof ClientboundSetCursorItemPacket p)
            return new ClientboundSetCursorItemPacket(VGiftBox.networkView(p.contents()));
        if (packet instanceof ClientboundSetPlayerInventoryPacket p)
            return new ClientboundSetPlayerInventoryPacket(p.slot(), VGiftBox.networkView(p.contents()));
        if (packet instanceof ClientboundSetEquipmentPacket p)
            return new ClientboundSetEquipmentPacket(p.getEntity(), p.getSlots().stream()
                    .map(pair -> Pair.of(pair.getFirst(), VGiftBox.networkView(pair.getSecond()))).toList());
        if (packet instanceof ClientboundSetEntityDataPacket p)
            return new ClientboundSetEntityDataPacket(p.id(), p.packedItems().stream().map(GiftBoxPackets::hideValue).toList());
        if (packet instanceof ClientboundBundlePacket p) {
            var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
            for (var child : p.subPackets()) packets.add(gamePacket(hideContents(child)));
            return new ClientboundBundlePacket(packets);
        }
        return packet;
    }

    @SuppressWarnings("unchecked")
    private static Packet<? super ClientGamePacketListener> gamePacket(Packet<?> packet) {
        return (Packet<? super ClientGamePacketListener>) packet;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static SynchedEntityData.DataValue<?> hideValue(SynchedEntityData.DataValue<?> value) {
        if (value.value() instanceof ItemStack stack)
            return new SynchedEntityData.DataValue(value.id(), value.serializer(), VGiftBox.networkView(stack));
        return value;
    }
}
