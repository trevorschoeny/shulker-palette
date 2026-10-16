package com.trevorschoeny.shulkerpalette.palette;

import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Right-click with a palette shulker: place its next block instead of the shulker.
 *
 * <p>Runs on both sides, like every vanilla placement. The client places the same block to
 * predict it (both sides read the same next block, chosen in advance by the server); only
 * the server takes the block out of the box and chooses the one after.
 */
public final class Placement {
    private Placement() {}

    /**
     * @param context the original right-click, with the shulker in hand
     * @param hit     where it landed (vanilla keeps this protected, hence passed in)
     */
    public static InteractionResult place(UseOnContext context, BlockHitResult hit, ItemStack shulker, Palette palette) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack next = palette.next().map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);

        // Nothing to place: a click and nothing else, the feel of reaching for a block you don't have.
        if (!(next.getItem() instanceof BlockItem block)) {
            level.playSound(player, context.getClickedPos(), SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 1.2F);
            return InteractionResult.FAIL;
        }

        // Vanilla's own placement, run as if the chosen block were in hand. It does the
        // survival checks, the orientation, the sound and the block entity data. It also
        // "consumes" one from `next`, emptying our throwaway copy, so what to take out of
        // the box is remembered first.
        ItemStack placed = next.copy();
        InteractionResult result = block.place(new BlockPlaceContext(level, player, context.getHand(), next, hit));
        if (!result.consumesAction() || level.isClientSide()) {
            // A placement that didn't happen uses nothing up and keeps the same next block.
            return result;
        }

        ItemContainerContents contents = shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (player == null || !player.hasInfiniteMaterials()) {
            Optional<ItemContainerContents> taken = Draw.removeOne(palette, contents, placed);
            if (taken.isEmpty()) {
                // The box no longer holds what was shown (a desync). The block is already
                // down, so choose again rather than undo it, but say so: silence here once
                // hid a bug that stopped every box from counting down.
                ShulkerPalette.LOGGER.warn("Palette placed {} but the box holds none; choosing again", placed);
                rollNext(shulker, palette, contents, level);
                return result;
            }
            contents = taken.get();
            shulker.set(DataComponents.CONTAINER, contents);
        }
        rollNext(shulker, palette, contents, level);
        return result;
    }

    /** Chooses the next block from these contents and stores it, with their fingerprint, on the shulker. */
    public static void rollNext(ItemStack shulker, Palette palette, ItemContainerContents contents, Level level) {
        shulker.set(Palette.COMPONENT, palette.withNext(Draw.roll(palette, contents, level.getRandom()), Draw.fingerprint(contents)));
    }
}
