package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.held.HeldLook;
import com.trevorschoeny.shulkerpalette.held.OpenIcon;
import com.trevorschoeny.shulkerpalette.palette.Mode;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps what a palette shulker draws as, where it shows the next block:
 * <ul>
 *   <li>In a hand: the next block itself.</li>
 *   <li>In the inventory (GUI): the shulker with its lid lifted out of the icon and the next
 *       block small inside ({@link OpenIcon}).</li>
 * </ul>
 * The modes that draw at random (random, equal random, gradient) also get the rainbow outline
 * ({@link HeldLook}); pick, which places one chosen block, does not. A palette with no next
 * block (empty) draws as the plain shulker.
 */
@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    /** Set while drawing the shulker itself inside its own icon, so this hook lets it through. */
    @Unique private static boolean shulkerpalette$inner;

    @Shadow
    public abstract void appendItemLayers(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
                                          @Nullable Level level, @Nullable ItemOwner owner, int seed);

    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void shulkerpalette$showNextBlock(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
                                              @Nullable Level level, @Nullable ItemOwner owner, int seed, CallbackInfo ci) {
        boolean hand = HeldLook.inHand(displayContext);
        boolean icon = displayContext == ItemDisplayContext.GUI;
        if (shulkerpalette$inner || !(hand || icon)) return;
        Palette palette = Palette.activeOn(item);
        if (palette == null || palette.next().isEmpty()) return;
        ItemStack next = palette.next().map(ItemStackTemplate::create).orElseThrow();
        ci.cancel();

        if (icon) {
            // The shulker first, drawn the normal way, then opened; then the block inside it.
            shulkerpalette$inner = true;
            try {
                appendItemLayers(output, item, displayContext, level, owner, seed);
            } finally {
                shulkerpalette$inner = false;
            }
            int shulkerLayers = OpenIcon.openLid(output);
            appendItemLayers(output, next, displayContext, level, owner, seed);
            OpenIcon.shrinkInside(output, shulkerLayers);
        } else {
            appendItemLayers(output, next, displayContext, level, owner, seed);
        }
        if (palette.mode() != Mode.PICK) {
            output.setData(HeldLook.RAINBOW, true);
            output.setAnimated(); // the rainbow cycles, so the icon is redrawn every frame
        }
    }
}
