package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PaletteHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A placed shulker keeps its palette among the block entity's leftover components (the ones
 * it doesn't read itself), which vanilla saves and hands back to the item on break. This
 * exposes that one component for the control panel.
 */
@Mixin(ShulkerBoxBlockEntity.class)
abstract class ShulkerBoxBlockEntityMixin extends BlockEntity implements PaletteHolder {
    private ShulkerBoxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Palette shulkerpalette$palette() {
        Palette palette = components().get(Palette.COMPONENT);
        return palette != null ? palette : Palette.OFF;
    }

    @Override
    public void shulkerpalette$setPalette(Palette palette) {
        setComponents(DataComponentMap.builder().addAll(components()).set(Palette.COMPONENT, palette).build());
        setChanged();
    }
}
