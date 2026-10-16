package com.trevorschoeny.shulkerpalette.mixin;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla keeps the hit result protected; placing a different block needs it. */
@Mixin(UseOnContext.class)
public interface UseOnContextAccessor {
    @Invoker("getHitResult")
    BlockHitResult shulkerpalette$hitResult();
}
