package com.trevorschoeny.shulkerpalette.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's frame sits, and the slot under the pointer (the palette key over a slot). */
@Mixin(AbstractContainerScreen.class)
public interface ScreenPosAccessor {
    @Accessor("leftPos") int shulkerpalette$leftPos();
    @Accessor("topPos") int shulkerpalette$topPos();
    @Accessor("hoveredSlot") net.minecraft.world.inventory.@org.jspecify.annotations.Nullable Slot shulkerpalette$hoveredSlot();
}
