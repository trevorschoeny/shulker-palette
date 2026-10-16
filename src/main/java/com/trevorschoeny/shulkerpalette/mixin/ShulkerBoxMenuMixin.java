package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.palette.HeldShulker;
import com.trevorschoeny.shulkerpalette.palette.PaletteHolder;
import com.trevorschoeny.shulkerpalette.palette.PaletteMenu;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wires the shulker screen to the palette behind it. On the server the menu's container is
 * the placed box or the held item ({@link PaletteHolder}): its palette is synced to the client as one data
 * slot, and the panel's buttons arrive as menu-button clicks. On the client the data slot
 * is a plain holder the panel reads.
 */
@Mixin(ShulkerBoxMenu.class)
abstract class ShulkerBoxMenuMixin extends AbstractContainerMenu implements PaletteMenu {
    @Unique private @Nullable PaletteHolder shulkerpalette$holder;
    @Unique private DataSlot shulkerpalette$state;

    private ShulkerBoxMenuMixin(@Nullable MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;)V", at = @At("TAIL"))
    private void shulkerpalette$syncPalette(int containerId, Inventory inventory, Container container, CallbackInfo ci) {
        if (container instanceof PaletteHolder holder) {
            shulkerpalette$holder = holder;
            shulkerpalette$state = addDataSlot(new DataSlot() {
                @Override public int get() { return PanelActions.pack(holder.shulkerpalette$palette()); }
                @Override public void set(int value) {}
            });
        } else {
            // The client's copy (vanilla gives it a plain container), or another mod's container:
            // a holder for whatever the server sends, 0 meaning "no palette here".
            shulkerpalette$state = addDataSlot(DataSlot.standalone());
        }
    }

    @Override
    public int shulkerpalette$packedPalette() {
        return shulkerpalette$state.get();
    }

    /**
     * With the screen open over a held shulker, that shulker's own slot is locked: no
     * click, shift-click, throw or number-key swap moves it out from under its screen.
     */
    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (shulkerpalette$holder instanceof HeldShulker held) {
            boolean onIt = slotIndex >= 0 && slotIndex < slots.size() && held.holds(slots.get(slotIndex).getItem());
            boolean swapsIt = input == ContainerInput.SWAP && held.holds(player.getInventory().getItem(buttonNum));
            if (onIt || swapsIt) return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (shulkerpalette$holder == null) return super.clickMenuButton(player, buttonId);
        shulkerpalette$holder.shulkerpalette$setPalette(
                PanelActions.apply(shulkerpalette$holder.shulkerpalette$palette(), buttonId, player));
        return true;
    }
}
