package com.trevorschoeny.shulkerpalette.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The layers vanilla keeps private, for opening the shulker in a palette's icon. */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    @Accessor("activeLayerCount") int shulkerpalette$activeLayerCount();
    @Accessor("layers") ItemStackRenderState.LayerRenderState[] shulkerpalette$layers();
}
