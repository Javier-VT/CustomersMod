package com.customers;

import com.customers.block.ChairBlock;
import com.customers.block.RestaurantSignBlock;
import com.customers.block.TakeawayBellBlock;
import com.customers.entity.CustomerEntity;
import com.customers.entity.SeatEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Reg {
    private Reg() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CustomersMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CustomersMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, CustomersMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CustomersMod.MODID);

    /** Chairs by wood name (pale oak does not exist in 1.21.1). */
    public static final Map<String, DeferredBlock<ChairBlock>> CHAIRS = new LinkedHashMap<>();

    static {
        chair("oak", Blocks.OAK_PLANKS);
        chair("spruce", Blocks.SPRUCE_PLANKS);
        chair("birch", Blocks.BIRCH_PLANKS);
        chair("jungle", Blocks.JUNGLE_PLANKS);
        chair("acacia", Blocks.ACACIA_PLANKS);
        chair("dark_oak", Blocks.DARK_OAK_PLANKS);
        chair("mangrove", Blocks.MANGROVE_PLANKS);
        chair("cherry", Blocks.CHERRY_PLANKS);
        chair("bamboo", Blocks.BAMBOO_PLANKS);
        chair("crimson", Blocks.CRIMSON_PLANKS);
        chair("warped", Blocks.WARPED_PLANKS);
    }

    private static void chair(String wood, Block planks) {
        DeferredBlock<ChairBlock> b = BLOCKS.register("chair_" + wood,
                () -> new ChairBlock(BlockBehaviour.Properties.ofFullCopy(planks).noOcclusion().strength(0.4F)));
        ITEMS.register("chair_" + wood, () -> new BlockItem(b.get(), new Item.Properties()));
        CHAIRS.put(wood, b);
    }

    public static final DeferredBlock<RestaurantSignBlock> RESTAURANT_SIGN = BLOCKS.register("restaurant_sign",
            () -> new RestaurantSignBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion().strength(1.0F)));
    public static final DeferredItem<BlockItem> RESTAURANT_SIGN_ITEM = ITEMS.register("restaurant_sign",
            () -> new BlockItem(RESTAURANT_SIGN.get(), new Item.Properties()));

    public static final DeferredBlock<TakeawayBellBlock> TAKEAWAY_BELL = BLOCKS.register("takeaway_bell",
            () -> new TakeawayBellBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.35F)
                    .sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> TAKEAWAY_BELL_ITEM = ITEMS.register("takeaway_bell",
            () -> new BlockItem(TAKEAWAY_BELL.get(), new Item.Properties()));

    public static final DeferredHolder<EntityType<?>, EntityType<CustomerEntity>> CUSTOMER = ENTITIES.register("customer",
            () -> EntityType.Builder.of(CustomerEntity::new, MobCategory.MISC)
                    .sized(0.5F, 1.8F).clientTrackingRange(8).build("customers:customer"));

    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F).clientTrackingRange(8).updateInterval(20).build("customers:seat"));

    public static final DeferredItem<DeferredSpawnEggItem> CUSTOMER_SPAWN_EGG = ITEMS.register("customer_spawn_egg",
            () -> new DeferredSpawnEggItem(CUSTOMER, 0xC8A27A, 0x4A6FA5, new Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.customers"))
                    .icon(() -> new ItemStack(RESTAURANT_SIGN_ITEM.get()))
                    .displayItems((params, out) -> ITEMS.getEntries().forEach(h -> out.accept(h.get())))
                    .build());
}
