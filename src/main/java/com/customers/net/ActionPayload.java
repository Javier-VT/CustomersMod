package com.customers.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: a button was pressed in one of our GUIs. */
public record ActionPayload(String action, CompoundTag data) implements CustomPacketPayload {
    public static final Type<ActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("customers", "action"));
    public static final StreamCodec<ByteBuf, ActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ActionPayload::action,
            ByteBufCodecs.COMPOUND_TAG, ActionPayload::data,
            ActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
