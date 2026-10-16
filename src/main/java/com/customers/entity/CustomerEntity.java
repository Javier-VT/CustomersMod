package com.customers.entity;

import com.customers.data.MenuManager;
import com.customers.data.RestaurantData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CustomerEntity extends PathfinderMob {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public enum State { SEARCHING, GOING_TO_TABLE, GOING_TO_COUNTER, WAITING, LEAVING }

    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SITTING = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Component> LINE1 = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Component> LINE2 = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Component> LINE3 = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.COMPONENT);

    private static final String[] GAMER_NAMES = {
        "ProGamer_2026", "Shadow99", "KikePro", "Zack_77", "MatrixX", "Blaze404", "LunaGamer", "AlphaDog", "SniperPro",
        "DarkKnight", "Vortex01", "NicoPlay", "SlayerKing", "PixelBoy", "TurboFast", "SkyWalker", "xX_Master_Xx", "Neon_Rider",
        "Cyber_Ninja", "Epic_Gamer", "NoobMaster69", "PVP_God", "CreeperSlayer", "EnderQueen", "ElBarto_MX", "TacosAlPastor",
        "GamerGirl_AR", "DonRamón_PV", "SeñorPancho", "ChicoTacos", "LaRosalia_99", "ElGuapo2000", "Paco_El_Pro", "MichiGamer",
        "CapitanArepa", "ElPolloLoco", "SobrinoDelXokas", "Ibai_Llanos", "Rubius_Fan", "Vegetta_777", "Fargan_Pro", "Milly_Cake",
        "ZorroPlateado", "GatoGalactico", "PoderLatino", "BurritoKing", "Tamalero_Pro", "ElMatador", "ReyDeCamas"};

    // ---- server-side state (saved in NBT) ----
    private State state = State.SEARCHING;
    private boolean initialized;
    private String restaurantKey = "", restaurantName = "Restaurant", filter = "any";
    private int patienceMinutes = 3;
    private boolean takeaway;
    private String bellKey = "";
    private BlockPos bellPos;
    private double counterX, counterY, counterZ;
    private String desiredItem = "", customerName = "Customer", profile = "normal";
    private double profilePatience = 1.0;
    private int profileTip;
    private int patienceTicks, maxPatience = 3600, searchTicks, stuckTicks, cooldown, leaveTimer;
    private double lastX, lastZ;
    private String failedChair = "";
    private boolean hasChair;
    private double chairX, chairZ, sitY;
    private float chairYaw;

    public CustomerEntity(EntityType<? extends CustomerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanFloat(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, 0);
        builder.define(SITTING, false);
        builder.define(LINE1, Component.empty());
        builder.define(LINE2, Component.empty());
        builder.define(LINE3, Component.empty());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return (state == State.SEARCHING || state == State.LEAVING) && super.canUse();
            }
            @Override
            public boolean canContinueToUse() {
                return (state == State.SEARCHING || state == State.LEAVING) && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0F, 0.5F));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    // ---------- client-visible accessors ----------
    public int getVariant() { return entityData.get(VARIANT); }
    public boolean isSitting() { return entityData.get(SITTING); }
    public State getState() { return state; }
    public String getRestaurantKey() { return restaurantKey; }
    public boolean isTakeaway() { return takeaway; }
    public boolean getLinesVip() { return profile.equals("vip"); }
    public String getBellKey() { return bellKey; }

    public List<Component> getLines() {
        List<Component> out = new ArrayList<>();
        for (EntityDataAccessor<Component> a : List.of(LINE1, LINE2, LINE3)) {
            Component c = entityData.get(a);
            if (!c.getString().isEmpty()) out.add(c);
        }
        return out;
    }

    // ---------- physics / behaviour tweaks ----------
    private boolean frozen() {
        return isSitting() || state == State.WAITING;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (frozen()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        super.travel(travelVector);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player) return false;
        return super.hurt(source, amount);
    }

    // ---------- setup (called by the spawner) ----------
    public void setupRestaurant(String key, String name, String filter, int minutes) {
        this.restaurantKey = key;
        this.restaurantName = name;
        this.filter = filter;
        this.patienceMinutes = Math.min(20, Math.max(1, minutes));
    }

    public void setupTakeaway(String bellKey, BlockPos bell, Vec3 counter) {
        this.takeaway = true;
        this.bellKey = bellKey;
        this.bellPos = bell;
        this.counterX = counter.x; this.counterY = counter.y; this.counterZ = counter.z;
    }

    private RestaurantData data() {
        return level().getServer() == null ? null : RestaurantData.get(level().getServer());
    }

    public void initCustomer() {
        if (initialized || level().isClientSide) return;
        initialized = true;
        int skin = random.nextInt(11);
        entityData.set(VARIANT, skin);
        customerName = skin == 0 ? "xRealSlayer" : GAMER_NAMES[random.nextInt(GAMER_NAMES.length)];

        RestaurantData d = data();
        int rep = d == null ? 50 : d.reputation(restaurantKey);
        double vip = rep >= 65 ? Math.min(0.135, (rep - 55) * 0.003) : 0;
        if (random.nextDouble() < vip) {
            profile = "vip"; profilePatience = 0.88; profileTip = 3;
        } else {
            double t = random.nextDouble() * 100;
            if ((t -= 55) <= 0) { profile = "normal"; profilePatience = 1.0; profileTip = 0; }
            else if ((t -= 20) <= 0) { profile = "rush"; profilePatience = 0.72; profileTip = 1; }
            else if ((t -= 18) <= 0) { profile = "patient"; profilePatience = 1.35; profileTip = 0; }
            else { profile = "generous"; profilePatience = 1.05; profileTip = 2; }
        }
        maxPatience = (int) Math.round(patienceMinutes * 1200 * profilePatience);

        List<MenuManager.Entry> menu = MenuManager.menuFor(filter);
        if (menu.isEmpty()) menu = MenuManager.ITEMS;
        if (!menu.isEmpty()) desiredItem = menu.get(random.nextInt(menu.size())).id().toString();

        state = takeaway ? State.GOING_TO_COUNTER : State.SEARCHING;
        lastX = getX(); lastZ = getZ();
        applySpeed();
        refreshLines();
    }

    private void applySpeed() {
        double v = switch (state) {
            case GOING_TO_TABLE -> 0.32;
            case GOING_TO_COUNTER -> 0.34;
            default -> 0.28;
        };
        var attr = getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) attr.setBaseValue(v);
    }

    private void setState(State s) {
        state = s;
        applySpeed();
    }

    // ---------- main tick ----------
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (frozen()) setDeltaMovement(Vec3.ZERO);
        if (state == State.LEAVING && --leaveTimer <= 0) {
            discard();
            return;
        }
        if (tickCount % 20 == 0) {
            try {
                aiTick();
            } catch (Exception e) {
                LOGGER.error("Customer AI error", e);
            }
        }
    }

    private void aiTick() {
        if (!initialized) initCustomer();
        if (state == State.LEAVING) { refreshLines(); return; }
        patienceTicks += 20;

        if (patienceTicks > maxPatience) {
            broadcast(Component.translatable(takeaway ? "customers.status.left_takeaway_patience" : "customers.status.left_patience", restaurantName, customerName));
            reputation(-3, true, "customers.reputation.lost", 3);
            discard();
            return;
        }
        switch (state) {
            case SEARCHING -> searchTick();
            case GOING_TO_COUNTER -> counterTick();
            case GOING_TO_TABLE -> tableTick();
            default -> { }
        }
        if (isAlive()) refreshLines();
    }

    private void reputation(int delta, boolean fail, String msgKey, int shown) {
        RestaurantData d = data();
        if (d == null) return;
        int now = d.registerResult(restaurantKey, delta, fail);
        if (now >= 0) broadcast(Component.translatable(msgKey, String.valueOf(shown), String.valueOf(now)));
    }

    private void searchTick() {
        if (cooldown > 0) { cooldown -= 20; return; }
        searchTicks += 20;
        if (searchTicks > 600) {
            broadcast(Component.translatable("customers.status.left_notable", restaurantName, customerName));
            reputation(-1, true, "customers.reputation.lost", 1);
            discard();
            return;
        }
        int offset = Math.abs(getUUID().hashCode()) % 40;
        if ((searchTicks + offset) % 80 < 20) {
            ChairFinder.Chair chair = findChair();
            if (chair != null) assignChair(chair);
        }
    }

    // ---------- chairs ----------
    private boolean chairTaken(double x, double z, List<CustomerEntity> others) {
        String key = ChairFinder.chairKey(x, z);
        for (CustomerEntity o : others) {
            if (o.hasChair && ChairFinder.chairKey(o.chairX, o.chairZ).equals(key)) return true;
            if (Math.hypot(x - o.getX(), z - o.getZ()) < 0.8) return true;
        }
        return false;
    }

    private List<CustomerEntity> others() {
        return level().getEntitiesOfClass(CustomerEntity.class, getBoundingBox().inflate(40),
                e -> e != this && e.state != State.LEAVING);
    }

    private ChairFinder.Chair findChair() {
        ServerLevel sl = (ServerLevel) level();
        String dim = sl.dimension().location().toString();
        RestaurantData d = data();
        String cacheKey;
        BlockPos center;
        RestaurantData.Restaurant r = d == null ? null : d.restaurants.get(restaurantKey);
        if (r != null) {
            cacheKey = "restaurant:" + restaurantKey;
            center = r.pos;
        } else {
            int cx = Math.floorDiv(blockPosition().getX(), 16), cz = Math.floorDiv(blockPosition().getZ(), 16);
            cacheKey = "cell:" + dim + ":" + cx + ":" + cz;
            center = new BlockPos(cx * 16 + 8, blockPosition().getY(), cz * 16 + 8);
        }
        List<CustomerEntity> others = others();
        ChairFinder.Chair best = null;
        double bestD = Double.MAX_VALUE;
        for (ChairFinder.Chair c : ChairFinder.scan(sl, cacheKey, center)) {
            if (c.key().equals(failedChair) || chairTaken(c.x(), c.z(), others)) continue;
            double dist = Math.hypot(getX() - c.x(), getZ() - c.z());
            if (dist < bestD) { bestD = dist; best = c; }
        }
        return best;
    }

    private void assignChair(ChairFinder.Chair c) {
        hasChair = true;
        chairX = c.x(); chairZ = c.z(); sitY = c.sitY(); chairYaw = c.yaw();
        setState(State.GOING_TO_TABLE);
        searchTicks = 0; stuckTicks = 0;
        lastX = getX(); lastZ = getZ();
        getNavigation().moveTo(chairX, Math.floor(sitY + 0.72), chairZ, 1.1);
    }

    private void giveUpChair(boolean markFailed) {
        if (markFailed) {
            failedChair = ChairFinder.chairKey(chairX, chairZ);
            cooldown = 100;
        }
        hasChair = false;
        setState(State.SEARCHING);
        searchTicks = 0; stuckTicks = 0;
        getNavigation().stop();
    }

    private void tableTick() {
        if (!hasChair) { setState(State.SEARCHING); return; }
        double dist = Math.hypot(chairX - getX(), chairZ - getZ());
        if (dist <= 1.5) {
            if (chairTaken(chairX, chairZ, others())) { giveUpChair(false); return; }
            getNavigation().stop();
            moveTo(chairX, sitY, chairZ, chairYaw, 0.0F);
            setYRot(chairYaw); setYBodyRot(chairYaw); setYHeadRot(chairYaw);
            SeatEntity seat = com.customers.Reg.SEAT.get().create(level());
            if (seat != null) {
                seat.moveTo(chairX, sitY, chairZ, chairYaw, 0.0F);
                level().addFreshEntity(seat);
                startRiding(seat, true);
            }
            setSitting(true);
            setState(State.WAITING);
            patienceTicks = 0; stuckTicks = 0; failedChair = "";
            level().playSound(null, blockPosition(), SoundEvents.WOOD_STEP, SoundSource.NEUTRAL, 1.0F, 1.0F);
            return;
        }
        searchTicks += 20;
        if (Math.hypot(getX() - lastX, getZ() - lastZ) < 0.08) {
            stuckTicks += 20;
            if (stuckTicks >= 60) {
                setDeltaMovement(getDeltaMovement().add((random.nextDouble() - 0.5) * 0.35, 0.25, (random.nextDouble() - 0.5) * 0.35));
                giveUpChair(true);
                return;
            }
        } else {
            stuckTicks = 0;
        }
        lastX = getX(); lastZ = getZ();
        if (searchTicks % 40 == 0 && stuckTicks == 0) getNavigation().moveTo(chairX, Math.floor(sitY + 0.72), chairZ, 1.1);
    }

    // ---------- takeaway ----------
    private void counterTick() {
        if (bellPos == null) {
            takeaway = false;
            setState(State.SEARCHING);
            return;
        }
        double dist = Math.hypot(bellPos.getX() + 0.5 - getX(), bellPos.getZ() + 0.5 - getZ());
        if (dist <= 2.25) {
            getNavigation().stop();
            moveTo(counterX, counterY, counterZ, getYRot(), 0.0F);
            setState(State.WAITING);
            patienceTicks = 0; stuckTicks = 0;
            level().playSound(null, blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 1.0F);
            return;
        }
        if (Math.hypot(getX() - lastX, getZ() - lastZ) < 0.08) stuckTicks += 20; else stuckTicks = 0;
        if (stuckTicks >= 80) {
            setDeltaMovement(getDeltaMovement().add((random.nextDouble() - 0.5) * 0.25, 0.22, (random.nextDouble() - 0.5) * 0.25));
            stuckTicks = 0;
        }
        lastX = getX(); lastZ = getZ();
        if (getNavigation().isDone()) getNavigation().moveTo(bellPos.getX() + 0.5, bellPos.getY(), bellPos.getZ() + 0.5, 1.05);
    }

    // ---------- name tag lines ----------
    private Component orderName() {
        ResourceLocation id = ResourceLocation.tryParse(desiredItem);
        if (id == null) return Component.literal(desiredItem);
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? Component.literal(desiredItem) : new ItemStack(item).getHoverName();
    }

    private String orderAddon() {
        ResourceLocation id = ResourceLocation.tryParse(desiredItem);
        return id == null ? "?" : MenuManager.addonName(id.getNamespace());
    }

    private Component mood() {
        double r = maxPatience <= 0 ? 0 : patienceTicks / (double) maxPatience;
        return Component.translatable(r > 0.66 ? "customers.mood.impatient" : r > 0.33 ? "customers.mood.ok" : "customers.mood.happy");
    }

    private void refreshLines() {
        MutableComponent head = Component.empty();
        if (profile.equals("vip")) head.append(Component.translatable("customers.tag.vip")).append(" ");
        head.append(Component.literal("§e" + customerName + "§r"));
        if (takeaway) head.append(" ").append(Component.translatable("customers.tag.takeaway"));
        Component l2 = Component.empty(), l3 = Component.empty();
        switch (state) {
            case WAITING -> {
                head.append(" ").append(mood());
                l2 = Component.translatable("customers.tag.order", orderName());
                l3 = Component.translatable("customers.tag.addon", orderAddon());
            }
            case LEAVING -> head = Component.translatable("customers.tag.thanks");
            case GOING_TO_COUNTER -> l2 = Component.translatable("customers.tag.to_counter");
            default -> l2 = Component.translatable("customers.tag.searching");
        }
        entityData.set(LINE1, head);
        entityData.set(LINE2, l2);
        entityData.set(LINE3, l3);
    }

    /** Leave the seat and step out in front of the chair. */
    private void standUp() {
        if (isPassenger()) {
            stopRiding();
            double r = Math.toRadians(chairYaw);
            moveTo(chairX - Math.sin(r) * 0.9, Math.floor(sitY + 0.72), chairZ + Math.cos(r) * 0.9, getYRot(), 0.0F);
        }
        setSitting(false);
    }

    private void setSitting(boolean s) {
        entityData.set(SITTING, s);
    }

    // ---------- messaging ----------
    private void broadcast(Component msg) {
        if (!(level() instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(this) <= 64 * 64) p.sendSystemMessage(msg);
        }
    }

    // ---------- interaction / payment ----------
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        if (state == State.SEARCHING || state == State.GOING_TO_TABLE) {
            sp.sendSystemMessage(Component.translatable("customers.interact.find_table", customerName));
            level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        if (state == State.GOING_TO_COUNTER) {
            sp.sendSystemMessage(Component.translatable("customers.interact.going_takeaway", customerName));
            level().playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        if (state == State.LEAVING || desiredItem.isEmpty()) return InteractionResult.SUCCESS;

        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() && BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(desiredItem)) {
            deliver(sp, held);
        } else {
            sp.sendSystemMessage(Component.translatable("customers.interact.wrong_order", customerName, orderName()));
        }
        return InteractionResult.SUCCESS;
    }

    private void deliver(ServerPlayer player, ItemStack held) {
        if (!player.getAbilities().instabuild) held.shrink(1);

        double ratio = patienceTicks / (double) Math.max(1, maxPatience);
        String group;
        int tip;
        if (ratio <= 0.33) { group = "fast"; tip = 2; }
        else if (ratio <= 0.66) { group = "normal"; tip = 1; }
        else { group = "slow"; tip = 0; }
        tip += Math.max(0, profileTip);
        boolean vip = profile.equals("vip");
        tip = Math.min(vip ? 6 : 4, tip);

        int gain = group.equals("fast") ? 2 : group.equals("normal") ? 1 : 0;
        if (vip && group.equals("fast")) gain += 1;
        RestaurantData d = data();
        if (d != null) {
            int now = d.registerResult(restaurantKey, gain, false);
            if (now >= 0 && gain > 0) {
                player.sendSystemMessage(Component.translatable("customers.reputation.gained", String.valueOf(gain), String.valueOf(now)));
            }
        }

        int idx = random.nextInt(8) + 1;
        player.sendSystemMessage(Component.literal("§e[" + customerName + "]: §f\"")
                .append(Component.translatable("customers.phrase." + group + "." + idx)).append("\"§r"));

        double chance = vip ? 0.92 : profile.equals("generous") ? 0.82 : 0.65;
        if (random.nextDouble() < chance) {
            int total = (vip ? 5 : 3) + tip;
            spawnAtLocation(new ItemStack(Items.EMERALD, total));
            player.sendSystemMessage(Component.translatable(vip ? "customers.payment.emeralds_vip" : "customers.payment.emeralds",
                    String.valueOf(total), String.valueOf(tip)));
        } else {
            int xp = (vip ? 90 : 50) + tip * 25;
            player.giveExperiencePoints(xp);
            player.sendSystemMessage(Component.translatable("customers.payment.xp", String.valueOf(xp)));
        }
        level().playSound(null, blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.0F);
        startLeaving();
    }

    private void startLeaving() {
        desiredItem = "";
        standUp();
        hasChair = false;
        setState(State.LEAVING);
        leaveTimer = 60;
        getNavigation().stop();
        refreshLines();
    }

    // ---------- persistence ----------
    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("State", state.ordinal());
        t.putBoolean("Init", initialized);
        t.putInt("Variant", getVariant());
        t.putBoolean("Sitting", isSitting());
        t.putString("RestKey", restaurantKey); t.putString("RestName", restaurantName); t.putString("Filter", filter);
        t.putInt("PatMin", patienceMinutes);
        t.putBoolean("Takeaway", takeaway); t.putString("BellKey", bellKey);
        if (bellPos != null) { t.putInt("BX", bellPos.getX()); t.putInt("BY", bellPos.getY()); t.putInt("BZ", bellPos.getZ()); }
        t.putDouble("CX", counterX); t.putDouble("CY", counterY); t.putDouble("CZ", counterZ);
        t.putString("Desired", desiredItem); t.putString("CName", customerName); t.putString("Profile", profile);
        t.putDouble("ProfPat", profilePatience); t.putInt("ProfTip", profileTip);
        t.putInt("PatTicks", patienceTicks); t.putInt("MaxPat", maxPatience); t.putInt("LeaveTimer", leaveTimer);
        t.putBoolean("HasChair", hasChair); t.putDouble("ChX", chairX); t.putDouble("ChZ", chairZ);
        t.putDouble("SitY", sitY); t.putFloat("ChYaw", chairYaw);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        State[] all = State.values();
        state = all[Math.max(0, Math.min(all.length - 1, t.getInt("State")))];
        initialized = t.getBoolean("Init");
        entityData.set(VARIANT, t.getInt("Variant"));
        entityData.set(SITTING, t.getBoolean("Sitting"));
        restaurantKey = t.getString("RestKey"); restaurantName = t.getString("RestName"); filter = t.getString("Filter");
        patienceMinutes = Math.max(1, t.getInt("PatMin"));
        takeaway = t.getBoolean("Takeaway"); bellKey = t.getString("BellKey");
        bellPos = t.contains("BX") ? new BlockPos(t.getInt("BX"), t.getInt("BY"), t.getInt("BZ")) : null;
        counterX = t.getDouble("CX"); counterY = t.getDouble("CY"); counterZ = t.getDouble("CZ");
        desiredItem = t.getString("Desired"); customerName = t.getString("CName"); profile = t.getString("Profile");
        profilePatience = t.getDouble("ProfPat"); profileTip = t.getInt("ProfTip");
        patienceTicks = t.getInt("PatTicks"); maxPatience = Math.max(1, t.getInt("MaxPat")); leaveTimer = t.getInt("LeaveTimer");
        hasChair = t.getBoolean("HasChair"); chairX = t.getDouble("ChX"); chairZ = t.getDouble("ChZ");
        sitY = t.getDouble("SitY"); chairYaw = t.getFloat("ChYaw");
        if (state == State.GOING_TO_TABLE && !hasChair) state = State.SEARCHING;
        applySpeed();
    }
}
