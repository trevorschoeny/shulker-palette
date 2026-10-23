package com.trevorschoeny.shulkerpalette.mixin;

import com.trevorschoeny.shulkerpalette.palette.CheatSheet;
import com.trevorschoeny.shulkerpalette.palette.PaletteTransfer;
import java.util.List;
import com.trevorschoeny.shulkerpalette.palette.PaletteHolder;
import com.trevorschoeny.shulkerpalette.palette.PaletteMenu;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PanelActions;
import com.trevorschoeny.shulkerpalette.palette.RowSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
 *
 * <p>Extra rows: the box's rows 4 to 16 are {@link RowSlot}s over the same container, added
 * after the player's inventory (so vanilla's own slot indices stay put) and live only while
 * the palette has those rows. The client's container gets the same room. Shift-click knows
 * both box ranges.
 */
@Mixin(ShulkerBoxMenu.class)
abstract class ShulkerBoxMenuMixin extends AbstractContainerMenu implements PaletteMenu {
    @Unique private @Nullable PaletteHolder shulkerpalette$holder;
    @Unique private DataSlot shulkerpalette$state;
    /** Where the extra rows' slots sit in {@code slots}; empty when the menu has none. */
    @Unique private int shulkerpalette$extraStart, shulkerpalette$extraEnd;

    /** Vanilla's layout: the box's 27 slots, then the player's 36. */
    @Unique private static final int SHULKERPALETTE$BOX = 27, SHULKERPALETTE$PLAYER_END = 63;
    @Unique private static final int SHULKERPALETTE$PARKED = -10000;
    @Unique private static final int SHULKERPALETTE$ROOM = Palette.MAX_ROWS * Palette.ROW_WIDTH;

    private ShulkerBoxMenuMixin(@Nullable MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    /** The client's stand-in container gets room for every row, like the box it shows. */
    @ModifyConstant(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;)V", constant = @Constant(intValue = 27))
    private static int shulkerpalette$clientRoom(int vanilla) {
        return SHULKERPALETTE$ROOM;
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/Container;)V", at = @At("TAIL"))
    private void shulkerpalette$syncPalette(int containerId, Inventory inventory, Container container, CallbackInfo ci) {
        if (container instanceof PaletteHolder holder) {
            shulkerpalette$holder = holder;
            // The client can't read a placed box's components; send it the cheat sheet. (This
            // arrives before the screen opens; the client keeps it by container id.)
            if (inventory.player instanceof ServerPlayer server && !holder.shulkerpalette$cheatSheet().isEmpty()) {
                CheatSheet.send(server, containerId, holder);
            }
            shulkerpalette$state = addDataSlot(new DataSlot() {
                @Override public int get() { return PanelActions.pack(holder.shulkerpalette$palette()); }
                @Override public void set(int value) {}
            });
        } else {
            // The client's copy (vanilla gives it a plain container), or another mod's container:
            // a holder for whatever the server sends, 0 meaning "no palette here".
            shulkerpalette$state = addDataSlot(DataSlot.standalone());
        }
        // The extra rows, on a palette box (server) or the client's copy (which always has the
        // room); never over another mod's 27-slot container. Both sides add the same slots.
        if (shulkerpalette$holder != null || container.getContainerSize() >= SHULKERPALETTE$ROOM) {
            shulkerpalette$extraStart = slots.size();
            for (int i = SHULKERPALETTE$BOX; i < SHULKERPALETTE$ROOM; i++) {
                // Off-screen until the row grid places them: they have no spot in vanilla's
                // layout, so a slot not shown yet must not fall back to one (it would sit over
                // the player's inventory). Same off-screen spot SlotKit parks slots at.
                addSlot(new RowSlot(container, i, SHULKERPALETTE$PARKED, SHULKERPALETTE$PARKED, () -> shulkerpalette$state.get()));
            }
            shulkerpalette$extraEnd = slots.size();
        }
    }

    /**
     * Shift-click with extra rows: out of either box range goes to the player; into the box
     * fills the vanilla rows, then the live extra rows. Vanilla's own version splits box from
     * player at the container's size, which no longer matches the slot list.
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void shulkerpalette$quickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (shulkerpalette$extraEnd == shulkerpalette$extraStart) return;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean inBox = index < SHULKERPALETTE$BOX || (index >= shulkerpalette$extraStart && index < shulkerpalette$extraEnd);
        boolean moved;
        if (inBox) {
            moved = moveItemStackTo(stack, SHULKERPALETTE$BOX, SHULKERPALETTE$PLAYER_END, true);
        } else {
            // Only the rows the box has: merging skips mayPlace, so hidden rows must be left out.
            int liveRows = PanelActions.unpack(shulkerpalette$state.get())
                    .map(p -> p.rows() - Palette.VANILLA_ROWS).orElse(0);
            moved = moveItemStackTo(stack, 0, SHULKERPALETTE$BOX, false);
            if (!stack.isEmpty()) {
                moved |= moveItemStackTo(stack, shulkerpalette$extraStart,
                        shulkerpalette$extraStart + liveRows * Palette.ROW_WIDTH, false);
            }
        }
        if (!moved) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        cir.setReturnValue(original);
    }

    @Override
    public @Nullable PaletteHolder shulkerpalette$holder() {
        return shulkerpalette$holder;
    }

    @Override
    public int shulkerpalette$packedPalette() {
        return shulkerpalette$state.get();
    }

    /**
     * The box screen's only buttons: the rows bar (creative). A placed box is always off, and
     * its palette is never changed here; the palette menu changes a palette in the hand.
     */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (shulkerpalette$holder == null) return super.clickMenuButton(player, buttonId);
        if (buttonId != PanelActions.ROWS_LESS && buttonId != PanelActions.ROWS_MORE) return true;
        Palette palette = shulkerpalette$holder.shulkerpalette$palette();
        // A row is only taken away empty, so nothing ends up where no slot reaches it.
        if (buttonId == PanelActions.ROWS_LESS && shulkerpalette$holder instanceof Container box
                && !PanelActions.canDropRow(palette, box)) {
            return true;
        }
        shulkerpalette$holder.shulkerpalette$setPalette(PanelActions.apply(palette, buttonId, player));
        return true;
    }

}
