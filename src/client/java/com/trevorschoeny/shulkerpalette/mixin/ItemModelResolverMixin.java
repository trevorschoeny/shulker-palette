package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.held.HeldLook;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In a hand, a palette shulker is drawn as its next block, marked for the rainbow outline
 * ({@link HeldLook}). A palette with no next block (empty) still draws as the shulker.
 */
@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    @Shadow
    public abstract void appendItemLayers(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
                                          @Nullable Level level, @Nullable ItemOwner owner, int seed);

    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void shulkerpalette$showNextBlock(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
                                              @Nullable Level level, @Nullable ItemOwner owner, int seed, CallbackInfo ci) {
        if (!HeldLook.inHand(displayContext)) return;
        Palette palette = Palette.activeOn(item);
        if (palette == null || palette.next().isEmpty()) return;
        ItemStack next = palette.next().map(ItemStackTemplate::create).orElseThrow();
        appendItemLayers(output, next, displayContext, level, owner, seed);
        output.setData(HeldLook.RAINBOW, true);
        ci.cancel();
    }
}
