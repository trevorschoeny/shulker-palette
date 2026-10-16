package com.trevorschoeny.shulkerpalette.mixin;

import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A layer's quads and transforms, which vanilla keeps private, for drawing its hull. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("quads") ItemQuads shulkerpalette$quads();
    @Accessor("itemTransform") ItemTransform shulkerpalette$itemTransform();
    @Accessor("localTransform") Matrix4f shulkerpalette$localTransform();
}
