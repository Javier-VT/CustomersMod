package com.customers.entity;

import com.customers.Reg;
import com.customers.data.RestaurantData;
import com.customers.data.RestaurantData.Bell;
import com.customers.data.RestaurantData.Restaurant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Opening hours + customer spawner. Runs once per second from the server tick. */
public final class RestaurantManager {
    private RestaurantManager() {}

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Map<String, Integer> TIMERS = new HashMap<>();
    private static final Map<String, Boolean> HOURS = new HashMap<>();
    private static final double TAKEAWAY_CHANCE = 0.32;
    private static final int TAKEAWAY_QUEUE_LIMIT = 3;
    private static final RandomSource RNG = RandomSource.create();

    public static void clear() {
        TIMERS.clear();
        HOURS.clear();
        ChairFinder.clear();
    }

    public static void forget(String key) {
        TIMERS.remove(key);
        HOURS.remove(key);
        ChairFinder.invalidate("restaurant:" + key);
    }

    public static ServerLevel levelOf(MinecraftServer server, String dim) {
        ResourceLocation id = ResourceLocation.tryParse(dim);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static void tick(MinecraftServer server) {
        RestaurantData data = RestaurantData.get(server);
        if (data.restaurants.isEmpty()) return;
        for (Restaurant r : new ArrayList<>(data.restaurants.values())) {
            try {
                tickRestaurant(server, data, r);
            } catch (Exception e) {
                LOGGER.error("Restaurant tick error", e);
            }
        }
    }

    private static boolean solidGround(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && s.getFluidState().isEmpty() && s.isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean passable(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getFluidState().isEmpty() && s.getCollisionShape(level, pos).isEmpty();
    }

    private static Vec3 findSafePoint(ServerLevel level, BlockPos c) {
        for (int i = 0; i < 15; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double dist = 2.5 + RNG.nextDouble() * 4.0;
            int cx = (int) Math.floor(c.getX() + Math.cos(ang) * dist);
            int cz = (int) Math.floor(c.getZ() + Math.sin(ang) * dist);
            for (int dy = 1; dy >= -4; dy--) {
                int cy = c.getY() + dy;
                BlockPos feet = new BlockPos(cx, cy, cz);
                if (!level.isLoaded(feet)) continue;
                if (solidGround(level, feet.below()) && passable(level, feet) && passable(level, feet.above())) {
                    return new Vec3(cx + 0.5, cy, cz + 0.5);
                }
            }
        }
        return null;
    }

    private static Vec3 findCounterPoint(ServerLevel level, BlockPos bell, int queueIndex) {
        int[][] offsets = {{0, 2}, {2, 0}, {0, -2}, {-2, 0}, {1, 2}, {2, 1}, {-1, -2}, {-2, -1}};
        int start = Math.abs(queueIndex) % offsets.length;
        for (int i = 0; i < offsets.length; i++) {
            int[] o = offsets[(start + i) % offsets.length];
            int cx = bell.getX() + o[0], cz = bell.getZ() + o[1];
            for (int dy = 0; dy >= -3; dy--) {
                BlockPos feet = new BlockPos(cx, bell.getY() + dy, cz);
                if (!level.isLoaded(feet)) continue;
                if (solidGround(level, feet.below()) && passable(level, feet) && passable(level, feet.above())) {
                    return new Vec3(cx + 0.5, feet.getY(), cz + 0.5);
                }
            }
        }
        return null;
    }

    private static boolean playerInRadius(ServerPlayer p, Restaurant r, double radius) {
        return Math.hypot(p.getX() - r.pos.getX(), p.getZ() - r.pos.getZ()) <= radius;
    }

    public static void notifyState(ServerLevel level, Restaurant r, boolean open) {
        double radius = Math.max(r.radius, 8);
        for (ServerPlayer p : level.players()) {
            if (!playerInRadius(p, r, radius)) continue;
            p.sendSystemMessage(Component.translatable(open ? "customers.status.open" : "customers.status.closed", r.name));
            p.playNotifySound((open ? SoundEvents.NOTE_BLOCK_CHIME : SoundEvents.NOTE_BLOCK_BASS).value(), SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    private static void tickRestaurant(MinecraftServer server, RestaurantData data, Restaurant r) {
        ServerLevel level = levelOf(server, r.dim);
        if (level == null || !level.isLoaded(r.pos)) return; // unloaded chunk != broken sign
        if (!level.getBlockState(r.pos).is(Reg.RESTAURANT_SIGN.get())) {
            data.removeRestaurant(r.key());
            forget(r.key());
            return;
        }
        String key = r.key();
        long time = level.getDayTime() % 24000L;
        boolean open = time < 12000L && r.open;
        Boolean prev = HOURS.get(key);
        if (prev != null && prev != open) notifyState(level, r, open);
        HOURS.put(key, open);
        if (!open) return;

        int timer = TIMERS.getOrDefault(key, 0) + 20;
        int delay = r.rate == 0 ? 40 : r.rate == 2 ? 800 : 300;
        if (time < 2000) delay = (int) Math.round(delay * 1.15);
        else if (time >= 4500 && time < 7500) delay = Math.max(30, (int) Math.round(delay * 0.85));
        else if (time >= 10000) delay = (int) Math.round(delay * 1.20);

        double radius = Math.max(r.radius, 8);
        boolean anyPlayer = false;
        for (ServerPlayer p : level.players()) if (playerInRadius(p, r, radius)) { anyPlayer = true; break; }
        if (!anyPlayer) { TIMERS.put(key, timer); return; }
        if (timer < delay) { TIMERS.put(key, timer); return; }

        int cap = Math.min(12, r.maxCustomers);
        List<CustomerEntity> inside = level.getEntitiesOfClass(CustomerEntity.class, new AABB(r.pos).inflate(64),
                c -> key.equals(c.getRestaurantKey()) && c.getState() != CustomerEntity.State.LEAVING);
        if (inside.size() >= cap) { TIMERS.put(key, delay); return; }

        // takeaway?
        Bell chosenBell = null;
        int queueIndex = 0;
        int best = Integer.MAX_VALUE;
        for (Bell b : data.bellsNear(r)) {
            int q = 0;
            for (CustomerEntity c : inside) if (c.isTakeaway() && b.key().equals(c.getBellKey())) q++;
            if (q < TAKEAWAY_QUEUE_LIMIT && q < best) { best = q; chosenBell = b; queueIndex = q; }
        }
        boolean takeaway = chosenBell != null && RNG.nextDouble() < TAKEAWAY_CHANCE;
        Vec3 counter = null;
        if (takeaway) {
            counter = findCounterPoint(level, chosenBell.pos(), queueIndex);
            if (counter == null) takeaway = false;
        }

        Vec3 spawn = findSafePoint(level, r.pos);
        if (spawn == null) { TIMERS.put(key, delay - 20); return; }

        CustomerEntity c = Reg.CUSTOMER.get().create(level);
        if (c == null) return;
        c.moveTo(spawn.x, spawn.y, spawn.z, RNG.nextFloat() * 360.0F, 0.0F);
        c.setupRestaurant(key, r.name, r.filter, r.patience);
        if (takeaway) c.setupTakeaway(chosenBell.key(), chosenBell.pos(), counter);
        c.initCustomer();
        level.addFreshEntity(c);

        boolean vip = c.getLinesVip();
        for (ServerPlayer p : level.players()) {
            if (!playerInRadius(p, r, radius)) continue;
            if (takeaway) {
                p.sendSystemMessage(Component.translatable(vip ? "customers.status.arrival_takeaway_vip" : "customers.status.arrival_takeaway", r.name));
                if (vip) p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0F, 1.0F);
            } else if (vip) {
                p.sendSystemMessage(Component.translatable("customers.status.arrival_vip", r.name));
                p.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 1.0F, 1.0F);
            } else {
                p.sendSystemMessage(Component.translatable("customers.status.arrival", r.name, String.valueOf(inside.size() + 1), String.valueOf(cap)));
            }
        }
        TIMERS.put(key, 0);
    }
}
