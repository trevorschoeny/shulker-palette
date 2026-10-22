package com.trevorschoeny.shulkerpalette.palette;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Repaint (creative, v1): right-click replaces the block you look at with the palette's next
 * block, in the palette's shape, instead of placing one beside it.
 *
 * <p>It clears the target quietly and then runs the ordinary palette placement into the gap,
 * so sounds and the next-block roll are the same as placing. Then the new block takes the old
 * one's facing, half, stair shape and the like wherever it has them ({@link #carry}), so oak
 * stairs repainted as stone stairs face the same way. If the placement fails, the old block
 * goes back.
 */
public final class Repaint {
    private Repaint() {}

    /** Is repaint what a right-click does for this player and palette? */
    public static boolean active(Palette palette, Player player) {
        return palette.repaint() && player.isCreative() && !player.isSecondaryUseActive();
    }

    /**
     * Can this block be repainted? Any shape, but never a block with a block entity (replacing
     * a chest would delete what is in it), a liquid, or half of a two-block-tall block (a door),
     * which would leave its other half behind.
     */
    public static boolean canRepaint(BlockGetter level, BlockPos pos, BlockState state) {
        return !state.isAir() && !state.hasBlockEntity() && !(state.getBlock() instanceof LiquidBlock)
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    /**
     * The new block with the old one's values for every property they share (facing, half,
     * stair shape, slab type, axis, waterlogged...). Properties are shared only when they are
     * the same property, so a fence's connections never land on a wall's.
     */
    public static BlockState carry(BlockState old, BlockState placed) {
        BlockState result = placed;
        for (Property<?> property : old.getProperties()) {
            if (result.hasProperty(property)) result = copy(old, result, property);
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    public static InteractionResult repaint(UseOnContext context, BlockHitResult hit, ItemStack shulker, Palette palette) {
        Level level = context.getLevel();
        BlockPos pos = hit.getBlockPos();
        BlockState old = level.getBlockState(pos);
        if (!canRepaint(level, pos, old)) {
            level.playSound(context.getPlayer(), pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 1.2F);
            return InteractionResult.FAIL;
        }
        // Cleared without telling the neighbours, so a torch or button on it doesn't drop in
        // the moment between the old block and the new one.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        // The hit still names this position; it is air now, so vanilla places into it.
        InteractionResult result = Placement.place(context, hit, shulker, palette);
        if (!result.consumesAction()) {
            level.setBlock(pos, old, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            return result;
        }
        // Keep the old block's orientation, then let connections (fences, walls) settle again.
        BlockState placed = level.getBlockState(pos);
        BlockState carried = Block.updateFromNeighbourShapes(carry(old, placed), level, pos);
        if (carried != placed && carried.canSurvive(level, pos)) level.setBlock(pos, carried, Block.UPDATE_ALL);
        return result;
    }
}
