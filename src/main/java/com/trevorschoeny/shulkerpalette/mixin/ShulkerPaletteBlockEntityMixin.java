package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.ShulkerPalette;
import com.trevorschoeny.shulkerpalette.ShulkerPaletteAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the "is palette" flag to ShulkerBoxBlockEntity.
 *
 * Persistence has TWO paths in 1.21.11:
 *   1. Disk (world save): saveAdditional/loadAdditional — writes to ValueOutput/ValueInput.
 *   2. Item (place/break cycle): collectImplicitComponents/applyImplicitComponents — uses
 *      the DataComponent system. We store our flag in CUSTOM_DATA to survive the cycle.
 *
 * collectImplicitComponents and applyImplicitComponents are defined on BaseContainerBlockEntity,
 * NOT on ShulkerBoxBlockEntity. We add @Override methods via the mixin to create the override.
 */
@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerPaletteBlockEntityMixin extends RandomizableContainerBlockEntity
        implements ShulkerPaletteAccessor {

    // Required by the extends — never actually constructed.
    protected ShulkerPaletteBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos,
                                              BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Unique
    private boolean trevorMod$isPalette = false;

    @Override
    public boolean trevorMod$isPalette() {
        return trevorMod$isPalette;
    }

    @Override
    public void trevorMod$setPalette(boolean value) {
        trevorMod$isPalette = value;
        this.setChanged();
    }

    // ── Path 1: Disk persistence (world save) ─────────────────────────────────

    @Inject(method = "saveAdditional", at = @At("RETURN"))
    private void trevorMod$savePaletteFlag(ValueOutput output, CallbackInfo ci) {
        if (trevorMod$isPalette) {
            output.putBoolean(ShulkerPalette.PALETTE_TAG, true);
        }
    }

    @Inject(method = "loadAdditional", at = @At("RETURN"))
    private void trevorMod$loadPaletteFlag(ValueInput input, CallbackInfo ci) {
        trevorMod$isPalette = input.getBooleanOr(ShulkerPalette.PALETTE_TAG, false);
    }

    // ── Path 2: Item persistence (place/break cycle) ──────────────────────────
    // These methods are inherited from BaseContainerBlockEntity — we add overrides
    // via the mixin since ShulkerBoxBlockEntity doesn't define them itself.

    /**
     * Block entity → Item: called when the shulker is broken.
     * Puts the palette flag into CUSTOM_DATA so it appears on the dropped item.
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (trevorMod$isPalette) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean(ShulkerPalette.PALETTE_TAG, true);
            builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /**
     * Item → Block entity: called when the shulker item is placed as a block.
     * Reads the palette flag from CUSTOM_DATA on the item.
     */
    @Override
    protected void applyImplicitComponents(DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        CustomData customData = getter.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            trevorMod$isPalette = customData.copyTag().getBoolean(ShulkerPalette.PALETTE_TAG).orElse(false);
        }
    }
}
