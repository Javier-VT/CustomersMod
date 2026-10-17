package com.customers.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-wide persistent storage for restaurants (signs) and takeaway bells. */
public class RestaurantData extends SavedData {
    public static final String NAME = "customers_data";
    public static final int BELL_MAX_DISTANCE = 32;

    public static class Restaurant {
        public BlockPos pos;
        public String dim;
        public String name;
        /** Item ids the owner turned off. Everything else (from any mod) is on the menu. */
        public java.util.Set<String> disabled = new java.util.HashSet<>();
        /** true = only foods the players already crafted/obtained are served (menu fills itself). */
        public boolean auto = true;
        public int patience = 5;      // minutes
        public int maxCustomers = 12;
        public int rate = 1;          // 0 high, 1 medium, 2 low
        public int radius = 64;
        public boolean open = true;
        public int rep = 50;
        public int services = 0;
        public int fails = 0;
        public String owner = "";

        public String key() {
            return RestaurantData.key(pos, dim);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("x", pos.getX()); t.putInt("y", pos.getY()); t.putInt("z", pos.getZ());
            t.putString("dim", dim); t.putString("name", name);
            t.putInt("patience", patience); t.putInt("max", maxCustomers); t.putInt("rate", rate);
            t.putInt("radius", radius); t.putBoolean("open", open); t.putInt("rep", rep);
            t.putInt("services", services); t.putInt("fails", fails); t.putString("owner", owner);
            ListTag dis = new ListTag();
            for (String d : disabled) dis.add(net.minecraft.nbt.StringTag.valueOf(d));
            t.put("disabled", dis);
            t.putBoolean("auto", auto);
            return t;
        }

        static Restaurant load(CompoundTag t) {
            Restaurant r = new Restaurant();
            r.pos = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
            r.dim = t.getString("dim"); r.name = t.getString("name");
            for (Tag d : t.getList("disabled", Tag.TAG_STRING)) r.disabled.add(d.getAsString());
            r.auto = t.contains("auto") && t.getBoolean("auto");
            r.patience = t.getInt("patience"); r.maxCustomers = t.getInt("max"); r.rate = t.getInt("rate");
            r.radius = t.getInt("radius"); r.open = t.getBoolean("open"); r.rep = t.getInt("rep");
            r.services = t.getInt("services"); r.fails = t.getInt("fails"); r.owner = t.getString("owner");
            return r;
        }
    }

    public record Bell(BlockPos pos, String dim) {
        public String key() {
            return RestaurantData.key(pos, dim);
        }
    }

    public final Map<String, Restaurant> restaurants = new LinkedHashMap<>();
    /** Item ids any player has crafted, smelted, picked up or held in this world. */
    public final java.util.Set<String> discovered = new java.util.HashSet<>();
    public final Map<String, Bell> bells = new LinkedHashMap<>();

    public static String key(BlockPos pos, String dim) {
        return pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + dim;
    }

    public static RestaurantData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RestaurantData::new, RestaurantData::load, null), NAME);
    }

    // ---- restaurants ----
    public Restaurant createRestaurant(BlockPos pos, String dim, String owner, boolean overwrite) {
        String k = key(pos, dim);
        Restaurant r = restaurants.get(k);
        if (r != null && !overwrite) return r;
        r = new Restaurant();
        r.pos = pos.immutable(); r.dim = dim; r.owner = owner;
        r.name = owner + "'s Restaurant";
        restaurants.put(k, r);
        setDirty();
        return r;
    }

    public Restaurant getOrCreate(BlockPos pos, String dim, String owner) {
        return createRestaurant(pos, dim, owner, false);
    }

    public void removeRestaurant(String k) {
        if (restaurants.remove(k) != null) setDirty();
    }

    public void clampAndSave(Restaurant r) {
        r.patience = Math.min(20, Math.max(1, r.patience));
        r.maxCustomers = Math.min(12, Math.max(1, r.maxCustomers));
        r.radius = Math.min(64, Math.max(8, r.radius));
        r.rate = Math.min(2, Math.max(0, r.rate));
        r.rep = Math.min(100, Math.max(0, r.rep));
        setDirty();
    }

    /** @return new reputation, or -1 when the restaurant no longer exists. */
    public int registerResult(String key, int delta, boolean failure) {
        Restaurant r = key == null ? null : restaurants.get(key);
        if (r == null) return -1;
        r.rep = Math.min(100, Math.max(0, r.rep + delta));
        if (failure) r.fails++; else r.services++;
        setDirty();
        return r.rep;
    }

    public int reputation(String key) {
        Restaurant r = key == null ? null : restaurants.get(key);
        return r == null ? 50 : r.rep;
    }

    // ---- bells ----
    public void addBell(BlockPos pos, String dim) {
        bells.put(key(pos, dim), new Bell(pos.immutable(), dim));
        setDirty();
    }

    public void removeBell(BlockPos pos, String dim) {
        if (bells.remove(key(pos, dim)) != null) setDirty();
    }

    public static double bellRange(Restaurant r) {
        return Math.min(BELL_MAX_DISTANCE, Math.max(8, r.radius));
    }

    public List<Bell> bellsNear(Restaurant r) {
        List<Bell> out = new ArrayList<>();
        double max = bellRange(r);
        for (Bell b : bells.values()) {
            if (!b.dim().equals(r.dim)) continue;
            double dx = b.pos().getX() - r.pos.getX(), dz = b.pos().getZ() - r.pos.getZ();
            if (dx * dx + dz * dz <= max * max) out.add(b);
        }
        return out;
    }

    public Restaurant restaurantNearBell(BlockPos bell, String dim) {
        Restaurant best = null;
        double bestD = Double.MAX_VALUE;
        for (Restaurant r : restaurants.values()) {
            if (!r.dim.equals(dim)) continue;
            double max = bellRange(r);
            double dx = r.pos.getX() - bell.getX(), dz = r.pos.getZ() - bell.getZ();
            double d = dx * dx + dz * dz;
            if (d <= max * max && d < bestD) { bestD = d; best = r; }
        }
        return best;
    }

    // ---- persistence ----
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag rs = new ListTag();
        for (Restaurant r : restaurants.values()) rs.add(r.save());
        tag.put("restaurants", rs);
        ListTag bs = new ListTag();
        for (Bell b : bells.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", b.pos().getX()); t.putInt("y", b.pos().getY()); t.putInt("z", b.pos().getZ());
            t.putString("dim", b.dim());
            bs.add(t);
        }
        tag.put("bells", bs);
        ListTag ds = new ListTag();
        for (String id : discovered) ds.add(net.minecraft.nbt.StringTag.valueOf(id));
        tag.put("discovered", ds);
        return tag;
    }

    public static RestaurantData load(CompoundTag tag, HolderLookup.Provider lookup) {
        RestaurantData d = new RestaurantData();
        for (Tag t : tag.getList("restaurants", Tag.TAG_COMPOUND)) {
            Restaurant r = Restaurant.load((CompoundTag) t);
            d.restaurants.put(r.key(), r);
        }
        for (Tag t : tag.getList("discovered", Tag.TAG_STRING)) d.discovered.add(t.getAsString());
        for (Tag t : tag.getList("bells", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Bell b = new Bell(new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")), c.getString("dim"));
            d.bells.put(b.key(), b);
        }
        return d;
    }
}
