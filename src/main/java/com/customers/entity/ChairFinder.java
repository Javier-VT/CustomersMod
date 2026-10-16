package com.customers.entity;

import com.customers.Reg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Finds seats (our chairs and any block whose id looks like a chair/stool/bench/sofa...). */
public final class ChairFinder {
    private ChairFinder() {}

    public record Chair(double x, double z, double sitY, float yaw, String key) {}

    private record Cache(long tick, List<Chair> chairs) {}

    private static final Map<String, Cache> CACHE = new HashMap<>();
    private static final int TTL = 100, RADIUS = 16, SCAN_Y = 2;
    private static final String[] WORDS = {"chair", "seat", "stool", "bench", "sofa", "couch", "booth", "cushion",
            "silla", "banco", "taburete", "sillon", "butaca", "banquito", "asiento"};

    public static void clear() {
        CACHE.clear();
    }

    public static void invalidate(String key) {
        CACHE.remove(key);
    }

    public static String chairKey(double x, double z) {
        return String.format(java.util.Locale.ROOT, "%.1f_%.1f", x, z);
    }

    private static boolean isChair(BlockState state) {
        for (var c : Reg.CHAIRS.values()) if (state.is(c.get())) return true;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("workbench")) return false;
        for (String w : WORDS) if (path.contains(w)) return true;
        return false;
    }

    public static List<Chair> scan(ServerLevel level, String cacheKey, BlockPos center) {
        long now = level.getGameTime();
        Cache c = CACHE.get(cacheKey);
        if (c != null && now - c.tick() <= TTL) return c.chairs();
        List<Chair> out = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = center.getY() - SCAN_Y; y <= center.getY() + SCAN_Y; y++) {
            for (int x = center.getX() - RADIUS; x <= center.getX() + RADIUS; x++) {
                for (int z = center.getZ() - RADIUS; z <= center.getZ() + RADIUS; z++) {
                    p.set(x, y, z);
                    if (!level.isLoaded(p)) continue;
                    BlockState st = level.getBlockState(p);
                    if (st.isAir() || !isChair(st)) continue;
                    VoxelShape shape = st.getCollisionShape(level, p);
                    double top = shape.isEmpty() ? 0.5 : shape.max(Direction.Axis.Y);
                    double sitY = y + top - 0.72;
                    double cx = x + 0.5, cz = z + 0.5;
                    // head room: the seat must not be under a solid block
                    BlockPos head = BlockPos.containing(cx, sitY + 1.7, cz);
                    if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) continue;
                    float yaw = 0;
                    if (st.hasProperty(HorizontalDirectionalBlock.FACING)) {
                        Direction f = st.getValue(HorizontalDirectionalBlock.FACING);
                        boolean ours = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace().equals("customers");
                        yaw = (ours ? f.getOpposite() : f).toYRot();
                    }
                    out.add(new Chair(cx, cz, sitY, yaw, chairKey(cx, cz)));
                }
            }
        }
        CACHE.put(cacheKey, new Cache(now, out));
        return out;
    }
}
