package com.customers.block;

import com.customers.data.RestaurantData;
import com.customers.net.ServerActions;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TakeawayBellBlock extends Block {
    public static final MapCodec<TakeawayBellBlock> CODEC = simpleCodec(TakeawayBellBlock::new);
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 9, 13);

    public TakeawayBellBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl) {
            String dim = sl.dimension().location().toString();
            RestaurantData data = RestaurantData.get(sl.getServer());
            data.addBell(pos, dim);
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (placer instanceof ServerPlayer sp) {
                ServerActions.bellPlacedMessage(sp, data, pos, dim);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.0F);
            ServerActions.openBell(sp, (ServerLevel) level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            String dim = sl.dimension().location().toString();
            RestaurantData.get(sl.getServer()).removeBell(pos, dim);
            ServerActions.removeOrdersForBell(RestaurantData.key(pos, dim));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
