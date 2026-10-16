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
 * In a hand and in the inventory, a palette shulker draws as an open box with its lid gone
 * and the next block small inside ({@link OpenIcon}); an empty palette is the open, empty
 * box. The modes that draw at random (random, equal random, gradient) also get the rainbow
 * outline ({@link HeldLook}); pick, which places one chosen block, does not.
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
        if (palette == null) return;
        ci.cancel();

        // The shulker first, drawn the normal way, then opened; then the next block inside it.
        // An empty palette is just the open, empty box.
        shulkerpalette$inner = true;
        try {
            appendItemLayers(output, item, displayContext, level, owner, seed);
        } finally {
            shulkerpalette$inner = false;
        }
        int shulkerLayers = OpenIcon.openLid(output, hand);
        if (palette.next().isEmpty()) return;
        appendItemLayers(output, palette.next().map(ItemStackTemplate::create).orElseThrow(), displayContext, level, owner, seed);
        OpenIcon.shrinkInside(output, shulkerLayers);
        if (palette.mode() != Mode.PICK) {
            output.setData(HeldLook.RAINBOW, true);
            output.setAnimated(); // the rainbow cycles, so the icon is redrawn every frame
        }
    }
}
