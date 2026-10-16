package com.customers.client;

import com.customers.net.OpenScreenPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientNet {
    private ClientNet() {}

    public static void handleOpen(OpenScreenPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            switch (p.kind()) {
                case "restaurant" -> mc.setScreen(new RestaurantScreen(p.data()));
                case "bell" -> mc.setScreen(new BellScreen(p.data()));
                default -> { }
            }
        });
    }
}
