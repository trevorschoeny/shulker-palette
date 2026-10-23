package com.trevorschoeny.shulkerpalette.mixin;

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
 * the one place a right-click on a block reaches them. A palette that's on always places blocks
 * and is never placed itself: turning it off makes it an ordinary shulker again.
 */
@Mixin(BlockItem.class)
abstract class BlockItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void shulkerpalette$placeFromPalette(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Palette palette = Palette.activeOn(context.getItemInHand());
        if (palette == null) return;
        var hit = ((UseOnContextAccessor) context).shulkerpalette$hitResult();
        cir.setReturnValue(context.getPlayer() != null && Repaint.active(palette, context.getPlayer())
                ? Repaint.repaint(context, hit, context.getItemInHand(), palette)
                : Placement.place(context, hit, context.getItemInHand(), palette));
    }
}
