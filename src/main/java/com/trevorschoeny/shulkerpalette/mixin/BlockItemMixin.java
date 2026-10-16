package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.palette.Keys;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Placement;
import com.trevorschoeny.shulkerpalette.palette.Repaint;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A palette shulker places its next block. Shulker items are plain block items, so this is
 * the one place a right-click on a block reaches them. Holding the palette modifier, or anything
 * that isn't a palette-on shulker, falls through to vanilla, which places the box itself.
 */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void shulkerpalette$placeFromPalette(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Palette palette = Palette.activeOn(context.getItemInHand());
        if (palette == null) return;
        // Modifier + right-click places the box itself. Sneak places palette blocks like any
        // block, so sneak-placing at an edge still works.
        if (context.getPlayer() != null && Keys.modifierHeld(context.getPlayer())) return;
        var hit = ((UseOnContextAccessor) context).shulkerpalette$hitResult();
        cir.setReturnValue(context.getPlayer() != null && Repaint.active(palette, context.getPlayer())
                ? Repaint.repaint(context, hit, context.getItemInHand(), palette)
                : Placement.place(context, hit, context.getItemInHand(), palette));
    }
}
