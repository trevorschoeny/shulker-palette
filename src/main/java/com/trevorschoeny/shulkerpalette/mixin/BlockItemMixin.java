package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Placement;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A palette shulker places its next block. Shulker items are plain block items, so this is
 * the one place a right-click on a block reaches them. Sneaking, or anything that isn't a
 * palette-on shulker, falls through to vanilla, which places the box itself.
 */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void shulkerpalette$placeFromPalette(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Palette palette = Palette.activeOn(context.getItemInHand());
        if (palette == null) return;
        // ponytail: sneak + right-click places the box itself, as a stopgap (Trev, 2026-10-15).
        // It costs sneak-placing palette blocks at an edge; a better gesture is an open question.
        if (context.isSecondaryUseActive()) return;
        cir.setReturnValue(Placement.place(context, ((UseOnContextAccessor) context).shulkerpalette$hitResult(),
                context.getItemInHand(), palette));
    }
}
