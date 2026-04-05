package com.trevorschoeny.shulkerpalette.mixin;

import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("specialRenderer")
    SpecialModelRenderer<Object> trevorMod$getSpecialRenderer();

    @Accessor("transform")
    void trevorMod$setTransform(ItemTransform transform);
}
