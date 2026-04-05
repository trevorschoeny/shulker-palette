package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.ShulkerPaletteAccessor;
import com.trevorschoeny.shulkerpalette.ShulkerPaletteMenuAccessor;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a synced DataSlot to ShulkerBoxMenu for the palette flag.
 *
 * On the server, the DataSlot reads live from the ShulkerBoxBlockEntity.
 * On the client, it receives the synced value via vanilla's container data sync.
 * The screen mixin reads this slot to render the toggle button state.
 *
 * Extends AbstractContainerMenu so we can call the protected addDataSlot().
 */
@Mixin(ShulkerBoxMenu.class)
public abstract class ShulkerPaletteMenuMixin extends AbstractContainerMenu
        implements ShulkerPaletteMenuAccessor {

    // Required by the extends — never actually constructed.
    protected ShulkerPaletteMenuMixin(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Unique
    private DataSlot trevorMod$paletteSlot;

    /**
     * Inject at RETURN of the 3-arg constructor (the full constructor that both
     * server and client paths use).
     */
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;)V",
            at = @At("RETURN"))
    private void trevorMod$addPaletteDataSlot(int containerId, Inventory playerInventory,
                                               Container container, CallbackInfo ci) {
        if (container instanceof ShulkerPaletteAccessor accessor) {
            // Server side: DataSlot backed by the block entity's flag.
            // broadcastChanges() compares get() each tick and syncs on change.
            trevorMod$paletteSlot = this.addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return accessor.trevorMod$isPalette() ? 1 : 0;
                }

                @Override
                public void set(int value) {
                    accessor.trevorMod$setPalette(value != 0);
                }
            });
        } else {
            // Client side: standalone slot that receives synced values.
            trevorMod$paletteSlot = this.addDataSlot(DataSlot.standalone());
        }
    }

    @Override
    public DataSlot trevorMod$getPaletteSlot() {
        return trevorMod$paletteSlot;
    }
}
