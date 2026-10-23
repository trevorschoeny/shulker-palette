package com.trevorschoeny.shulkerpalette.mixin;

import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.client.resources.model.sprite.SpriteId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A shulker item renderer's parts, to build an opened copy of it with the same box and color. */
@Mixin(ShulkerBoxSpecialRenderer.class)
public interface ShulkerBoxSpecialRendererAccessor {
    @Accessor("shulkerBoxRenderer") ShulkerBoxRenderer shulkerpalette$renderer();
    @Accessor("sprite") SpriteId shulkerpalette$sprite();
}
