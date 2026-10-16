package com.customers.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: open a GUI ("restaurant" or "bell") with the given data. */
public record OpenScreenPayload(String type, CompoundTag data) implements CustomPacketPayload {
    public static final Type<OpenScreenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("customers", "open_screen"));
    public static final StreamCodec<ByteBuf, OpenScreenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenScreenPayload::type,
            ByteBufCodecs.COMPOUND_TAG, OpenScreenPayload::data,
            OpenScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
