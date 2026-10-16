package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.palette.HeldShulker;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sneak + right-click into the air with a shulker box in the main hand opens it in place.
 * Sneak + right-click on a block still places the box (that path never reaches here).
 */
@Mixin(Item.class)
abstract class ItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void shulkerpalette$openHeld(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !player.isSecondaryUseActive() || !Palette.isShulker(stack)) return;
        if (player instanceof ServerPlayer server) HeldShulker.open(server, stack);
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
