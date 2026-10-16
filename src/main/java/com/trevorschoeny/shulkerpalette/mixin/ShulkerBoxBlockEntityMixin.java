package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.palette.CheatSheet;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import java.util.List;
import com.trevorschoeny.shulkerpalette.palette.PaletteHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two things on a placed shulker:
 * <ul>
 *   <li>Its palette, kept among the block entity's leftover components (the ones it doesn't
 *       read itself), which vanilla saves and hands back to the item on break. Exposed here
 *       for the control panel.</li>
 *   <li>Room for extra rows. The item list holds 16 rows, so vanilla's own copying between
 *       the placed box and its item carries every row; but the container reports only the
 *       rows the palette has (3 unless rows were added in creative), so a plain shulker's
 *       comparator signal and everything else that counts slots stay exactly vanilla.</li>
 * </ul>
 */
@Mixin(ShulkerBoxBlockEntity.class)
abstract class ShulkerBoxBlockEntityMixin extends BlockEntity implements PaletteHolder {
    private ShulkerBoxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Shadow protected abstract void setItems(NonNullList<ItemStack> items);

    private static final int SHULKERPALETTE$CAPACITY = Palette.MAX_ROWS * Palette.ROW_WIDTH;

    @Inject(method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V", at = @At("TAIL"))
    private void shulkerpalette$roomForRows(BlockPos pos, BlockState state, CallbackInfo ci) {
        setItems(NonNullList.withSize(SHULKERPALETTE$CAPACITY, ItemStack.EMPTY));
    }

    /** Loading rebuilds the list at the reported size; keep the full room instead. */
    @Redirect(method = "loadFromTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/entity/ShulkerBoxBlockEntity;getContainerSize()I"))
    private int shulkerpalette$loadFullRoom(ShulkerBoxBlockEntity self) {
        return SHULKERPALETTE$CAPACITY;
    }

    @Inject(method = "getContainerSize", at = @At("HEAD"), cancellable = true)
    private void shulkerpalette$reportedRows(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(shulkerpalette$palette().rows() * Palette.ROW_WIDTH);
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

    @Override
    public List<SavedPalette.Entry> shulkerpalette$cheatSheet() {
        return components().getOrDefault(CheatSheet.COMPONENT, List.of());
    }

    @Override
    public void shulkerpalette$setCheatSheet(List<SavedPalette.Entry> entries) {
        setComponents(DataComponentMap.builder().addAll(components()).set(CheatSheet.COMPONENT, entries.isEmpty() ? null : entries).build());
        setChanged();
    }
}
