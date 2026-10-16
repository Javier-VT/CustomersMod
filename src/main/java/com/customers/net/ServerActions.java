package com.customers.net;

import com.customers.Reg;
import com.customers.data.MenuManager;
import com.customers.data.RestaurantData;
import com.customers.data.RestaurantData.Restaurant;
import com.customers.entity.CustomerEntity;
import com.customers.entity.RestaurantManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server side of the GUIs: builds the screen data and applies the player's actions. */
public final class ServerActions {
    private ServerActions() {}

    public record PlayerOrder(UUID playerId, String playerName, String itemId, String restaurantKey, String bellKey, BlockPos bell, String dim) {}

    private static final Map<String, PlayerOrder> ORDERS = new HashMap<>();
    private static final int PICKUP_DISTANCE = 12;
    private static final int QUEUE_LIMIT = 8;

    private static String okey(UUID id, String bellKey) { return id + "::" + bellKey; }

    public static void clearOrders() { ORDERS.clear(); }
    public static void removeOrdersOf(UUID id) { ORDERS.values().removeIf(o -> o.playerId().equals(id)); }
    public static void removeOrdersForBell(String bellKey) { ORDERS.values().removeIf(o -> o.bellKey().equals(bellKey)); }
    public static void removeOrdersForRestaurant(String key) { ORDERS.values().removeIf(o -> o.restaurantKey().equals(key)); }

    private static List<PlayerOrder> ordersFor(String bellKey) {
        List<PlayerOrder> out = new ArrayList<>();
        for (PlayerOrder o : ORDERS.values()) if (o.bellKey().equals(bellKey)) out.add(o);
        return out;
    }

    private static String dimOf(ServerLevel l) { return l.dimension().location().toString(); }

    // ---------------------------------------------------------------- open screens
    public static void openRestaurant(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        RestaurantData data = RestaurantData.get(level.getServer());
        Restaurant r = data.getOrCreate(pos, dimOf(level), sp.getName().getString());
        if (r.owner.isEmpty()) { r.owner = sp.getName().getString(); data.setDirty(); }
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos.asLong());
        t.putString("name", r.name);
        t.putString("filter", r.filter);
        ListTag filters = new ListTag();
        for (String f : MenuManager.FILTERS) filters.add(StringTag.valueOf(f));
        t.put("filters", filters);
        t.putInt("patience", r.patience); t.putInt("max", r.maxCustomers); t.putInt("rate", r.rate);
        t.putInt("radius", r.radius); t.putBoolean("open", r.open);
        t.putInt("rep", r.rep); t.putInt("services", r.services); t.putInt("fails", r.fails);
        PacketDistributor.sendToPlayer(sp, new OpenScreenPayload("restaurant", t));
    }

    public static void openBell(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        RestaurantData data = RestaurantData.get(level.getServer());
        Restaurant r = data.restaurantNearBell(pos, dimOf(level));
        if (r == null) {
            sp.sendSystemMessage(Component.translatable("customers.bell.no_restaurant"));
            return;
        }
        String bellKey = RestaurantData.key(pos, dimOf(level));
        int waiting = level.getEntitiesOfClass(CustomerEntity.class, new AABB(pos).inflate(12),
                c -> c.isTakeaway() && bellKey.equals(c.getBellKey()) && c.getState() != CustomerEntity.State.LEAVING).size();
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos.asLong());
        t.putString("name", r.name);
        t.putBoolean("open", r.open);
        t.putInt("waiting", waiting);
        PlayerOrder own = ORDERS.get(okey(sp.getUUID(), bellKey));
        t.putString("own", own == null ? "" : own.itemId());
        ListTag orders = new ListTag();
        for (PlayerOrder o : ordersFor(bellKey)) {
            CompoundTag c = new CompoundTag();
            c.putString("buyer", o.playerId().toString());
            c.putString("name", o.playerName());
            c.putString("item", o.itemId());
            orders.add(c);
        }
        t.put("orders", orders);
        ListTag menu = new ListTag();
        for (MenuManager.Entry e : MenuManager.menuFor(r.filter)) menu.add(StringTag.valueOf(e.id().toString()));
        t.put("menu", menu);
        PacketDistributor.sendToPlayer(sp, new OpenScreenPayload("bell", t));
    }

    public static void bellPlacedMessage(ServerPlayer sp, RestaurantData data, BlockPos pos, String dim) {
        Restaurant r = data.restaurantNearBell(pos, dim);
        if (r != null) sp.sendSystemMessage(Component.translatable("customers.bell.placed", r.name));
        else sp.sendSystemMessage(Component.translatable("customers.bell.placed_unlinked"));
    }

    // ---------------------------------------------------------------- actions from the client
    public static void handleAction(ActionPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        try {
            CompoundTag d = payload.data();
            BlockPos pos = BlockPos.of(d.getLong("pos"));
            ServerLevel level = sp.serverLevel();
            if (sp.distanceToSqr(Vec3.atCenterOf(pos)) > 12 * 12) return;
            RestaurantData data = RestaurantData.get(level.getServer());
            switch (payload.action()) {
                case "save_restaurant" -> saveRestaurant(sp, level, data, pos, d);
                case "toggle_open" -> toggleOpen(sp, level, data, pos);
                case "order" -> order(sp, level, data, pos, d.getString("item"));
                case "cancel_order" -> {
                    String bellKey = RestaurantData.key(pos, dimOf(level));
                    if (ORDERS.remove(okey(sp.getUUID(), bellKey)) != null) {
                        sp.sendSystemMessage(Component.translatable("customers.bell.cancelled"));
                    }
                }
                case "serve" -> serve(sp, level, pos, d.getString("buyer"));
                default -> { }
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("Customers action failed", e);
        }
    }

    private static void saveRestaurant(ServerPlayer sp, ServerLevel level, RestaurantData data, BlockPos pos, CompoundTag d) {
        if (!level.getBlockState(pos).is(Reg.RESTAURANT_SIGN.get())) return;
        Restaurant r = data.getOrCreate(pos, dimOf(level), sp.getName().getString());
        String name = d.getString("name").trim();
        if (name.length() > 32) name = name.substring(0, 32);
        r.name = name.isEmpty() ? sp.getName().getString() + "'s Restaurant" : name;
        String f = d.getString("filter");
        r.filter = MenuManager.FILTERS.contains(f) ? f : "any";
        r.patience = d.getInt("patience");
        r.maxCustomers = d.getInt("max");
        r.rate = d.getInt("rate");
        r.radius = d.getInt("radius");
        data.clampAndSave(r);
        sp.sendSystemMessage(Component.translatable("customers.ui.saved", r.name));
    }

    private static void toggleOpen(ServerPlayer sp, ServerLevel level, RestaurantData data, BlockPos pos) {
        Restaurant r = data.restaurants.get(RestaurantData.key(pos, dimOf(level)));
        if (r == null) return;
        r.open = !r.open;
        data.setDirty();
        RestaurantManager.notifyState(level, r, r.open);
        sp.sendSystemMessage(Component.translatable(r.open ? "customers.restaurant.open_msg" : "customers.restaurant.closed_msg", r.name));
    }

    private static void order(ServerPlayer sp, ServerLevel level, RestaurantData data, BlockPos pos, String itemId) {
        Restaurant r = data.restaurantNearBell(pos, dimOf(level));
        if (r == null) { sp.sendSystemMessage(Component.translatable("customers.bell.no_restaurant")); return; }
        if (!r.open) { sp.sendSystemMessage(Component.translatable("customers.bell.closed", r.name)); return; }
        MenuManager.Entry entry = MenuManager.find(itemId);
        if (entry == null || !MenuManager.menuFor(r.filter).contains(entry)) return;
        String bellKey = RestaurantData.key(pos, dimOf(level));
        String k = okey(sp.getUUID(), bellKey);
        if (!ORDERS.containsKey(k) && ordersFor(bellKey).size() >= QUEUE_LIMIT) {
            sp.sendSystemMessage(Component.translatable("customers.bell.queue_full"));
            return;
        }
        ORDERS.put(k, new PlayerOrder(sp.getUUID(), sp.getName().getString(), itemId, r.key(), bellKey, pos, dimOf(level)));
        sp.sendSystemMessage(Component.translatable("customers.bell.ordered", entry.name()));
        double radius = Math.max(r.radius, 8);
        for (ServerPlayer p : level.players()) {
            if (p == sp || Math.hypot(p.getX() - r.pos.getX(), p.getZ() - r.pos.getZ()) > radius) continue;
            p.sendSystemMessage(Component.translatable("customers.bell.ordered_other", r.name, sp.getName().getString(), entry.name()));
            p.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    private static void serve(ServerPlayer worker, ServerLevel level, BlockPos pos, String buyerId) {
        String bellKey = RestaurantData.key(pos, dimOf(level));
        UUID id;
        try { id = UUID.fromString(buyerId); } catch (IllegalArgumentException e) { return; }
        PlayerOrder o = ORDERS.get(okey(id, bellKey));
        if (o == null) { worker.sendSystemMessage(Component.translatable("customers.bell.no_orders")); return; }
        if (worker.getUUID().equals(o.playerId())) {
            worker.sendSystemMessage(Component.translatable("customers.bell.own_order"));
            return;
        }
        ServerPlayer buyer = level.getServer().getPlayerList().getPlayer(o.playerId());
        MenuManager.Entry entry = MenuManager.find(o.itemId());
        Component itemName = entry != null ? entry.name() : Component.literal(o.itemId());
        if (buyer == null || buyer.level() != level) {
            worker.sendSystemMessage(Component.translatable("customers.bell.buyer_gone"));
            return;
        }
        if (buyer.distanceToSqr(Vec3.atCenterOf(pos)) > PICKUP_DISTANCE * PICKUP_DISTANCE) {
            worker.sendSystemMessage(Component.translatable("customers.bell.buyer_far", o.playerName(), String.valueOf(PICKUP_DISTANCE)));
            return;
        }
        ItemStack held = worker.getMainHandItem();
        if (held.isEmpty() || !BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(o.itemId())) {
            worker.sendSystemMessage(Component.translatable("customers.bell.hold", itemName));
            return;
        }
        ItemStack give = held.copyWithCount(1);
        held.shrink(1);
        if (!buyer.getInventory().add(give)) buyer.drop(give, false);
        ORDERS.remove(okey(id, bellKey));
        worker.sendSystemMessage(Component.translatable("customers.bell.delivered", itemName, o.playerName()));
        buyer.sendSystemMessage(Component.translatable("customers.bell.received", itemName, worker.getName().getString()));
        worker.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1.0F, 1.0F);
        buyer.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1.0F, 1.0F);
    }
}
