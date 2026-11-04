package com.trevorschoeny.shulkerpalette.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.trevorschoeny.shulkerpalette.held.HeldLook;
import java.util.List;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the rainbow outline under an item marked by {@link HeldLook#RAINBOW}: each quad layer's
 * hull, in that layer's own transform, before vanilla draws the item itself.
 * Layers drawn by a special renderer (a chest, a skull) have no quads and get no
 * outline; add one per special renderer if a block like that ever looks bare.
 */
@Mixin(ItemStackRenderState.class)
abstract class ItemStackRenderStateMixin {
    @Shadow private int activeLayerCount;
    @Shadow private ItemStackRenderState.LayerRenderState[] layers;
    @Shadow public ItemDisplayContext displayContext;

    @Inject(method = "submit", at = @At("HEAD"))
    private void shulkerpalette$rainbowHull(PoseStack poses, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        if (!Boolean.TRUE.equals(((ItemStackRenderState) (Object) this).getData(HeldLook.RAINBOW))) return;
        for (int i = 0; i < activeLayerCount; i++) {
            LayerRenderStateAccessor layer = (LayerRenderStateAccessor) layers[i];
            List<BakedQuad> quads = layer.shulkerpalette$quads().all();
            if (quads.isEmpty()) continue;
            poses.pushPose();
            layer.shulkerpalette$itemTransform().apply(displayContext.leftHand(), poses.last());
            poses.last().mulPose(layer.shulkerpalette$localTransform());
            // One layer's quads share an atlas: blocks for a block, items for a flat item.
            collector.submitCustomGeometry(poses, HeldLook.HULL.apply(quads.getFirst().materialInfo().sprite().atlasLocation()),
                    (pose, out) -> HeldLook.outline(pose, out, quads));
            poses.popPose();
        }
    }
}
