package com.customers;

import com.customers.data.MenuManager;
import com.customers.entity.RestaurantManager;
import com.customers.net.ServerActions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = CustomersMod.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        MenuManager.generate();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        RestaurantManager.clear();
        ServerActions.clearOrders();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 20 == 0) RestaurantManager.tick(e.getServer());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        ServerActions.removeOrdersOf(e.getEntity().getUUID());
    }
}
