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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Repaint (creative, v1): right-click replaces the block you look at with the palette's next
 * block, instead of placing one beside it.
 *
 * <p>It clears the target quietly and then runs the ordinary palette placement into the gap,
 * so orientation, sounds and the next-block roll are the same as placing. If the placement
 * fails, the old block goes back.
 */
public final class Repaint {
    private Repaint() {}

    /** Is repaint what a right-click does for this player and palette? */
    public static boolean active(Palette palette, Player player) {
        return palette.repaint() && player.isCreative() && !player.isSecondaryUseActive();
    }

    /**
     * Can this block be repainted? Full, solid-shaped blocks only for now, and never a block
     * with a block entity: replacing a chest would delete what is in it.
     * Stairs, slabs and keeping orientation are not handled yet.
     */
    public static boolean canRepaint(BlockGetter level, BlockPos pos, BlockState state) {
        return !state.isAir() && !state.hasBlockEntity() && state.isCollisionShapeFullBlock(level, pos);
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
        }
        return result;
    }
}
