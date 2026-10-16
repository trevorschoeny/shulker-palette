package com.trevorschoeny.shulkerpalette.mixin;

import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A layer's quads, transforms and special renderer, which vanilla keeps private. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("quads") ItemQuads shulkerpalette$quads();
    @Accessor("itemTransform") ItemTransform shulkerpalette$itemTransform();
    @Accessor("localTransform") Matrix4f shulkerpalette$localTransform();
    @Accessor("specialRenderer") @Nullable SpecialModelRenderer<Object> shulkerpalette$specialRenderer();
}
