package com.trevorschoeny.shulkerpalette.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's frame sits, for placing the row grid over it. */
@Mixin(AbstractContainerScreen.class)
public interface ScreenPosAccessor {
    @Accessor("leftPos") int shulkerpalette$leftPos();
    @Accessor("topPos") int shulkerpalette$topPos();
}
