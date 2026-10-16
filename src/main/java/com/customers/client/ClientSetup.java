package com.customers.client;

import com.customers.CustomersMod;
import com.customers.Reg;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CustomersMod.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(Reg.CUSTOMER.get(), CustomerRenderer::new);
        e.registerEntityRenderer(Reg.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }
}
