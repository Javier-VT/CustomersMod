package com.customers;

import com.customers.data.MenuManager;
import com.customers.entity.RestaurantManager;
import com.customers.net.ServerActions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.customers.data.RestaurantData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
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
        MinecraftServer server = e.getServer();
        int t = server.getTickCount();
        if (t % 20 == 0) RestaurantManager.tick(server);
        if (t % 40 == 7) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) scanInventory(server, p);
        }
        if (t % 600 == 11) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) scanRecipes(server, p);
        }
    }

    // ---- discovery: foods players craft / obtain are unlocked for restaurants in auto mode ----
    private static void discover(MinecraftServer server, ItemStack st) {
        if (server != null) MenuManager.discover(RestaurantData.get(server), st);
    }

    private static void scanInventory(MinecraftServer server, ServerPlayer p) {
        try {
            RestaurantData d = RestaurantData.get(server);
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) MenuManager.discover(d, p.getInventory().getItem(i));
        } catch (Exception ex) {
            // never break the server tick for a bad modded stack
        }
    }

    /** A food whose recipe is unlocked in the recipe book counts as known too. */
    private static void scanRecipes(MinecraftServer server, ServerPlayer p) {
        try {
            RestaurantData d = RestaurantData.get(server);
            for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) {
                ItemStack out = h.value().getResultItem(server.registryAccess());
                if (out.isEmpty() || d.discovered.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(out.getItem()).toString())) continue;
                if (MenuManager.find(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(out.getItem()).toString()) == null) continue;
                if (p.getRecipeBook().contains(h)) MenuManager.discover(d, out);
            }
        } catch (Exception ex) {
            // ignore exotic recipes
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.getServer() != null) {
            scanInventory(p.getServer(), p);
            scanRecipes(p.getServer(), p);
        }
    }

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) discover(p.getServer(), e.getCrafting());
    }

    @SubscribeEvent
    public static void smelted(PlayerEvent.ItemSmeltedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) discover(p.getServer(), e.getSmelting());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        ServerActions.removeOrdersOf(e.getEntity().getUUID());
    }
}
