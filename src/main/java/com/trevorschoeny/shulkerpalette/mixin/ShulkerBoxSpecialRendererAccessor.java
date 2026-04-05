package com.trevorschoeny.shulkerpalette.mixin;

import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ShulkerBoxSpecialRenderer.class)
public interface ShulkerBoxSpecialRendererAccessor {
    @Accessor("shulkerBoxRenderer")
    ShulkerBoxRenderer trevorMod$getShulkerBoxRenderer();

    @Accessor("orientation")
    Direction trevorMod$getOrientation();

    @Accessor("material")
    Material trevorMod$getMaterial();
}
