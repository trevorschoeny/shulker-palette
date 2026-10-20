package com.trevorschoeny.shulkerpalette.palette;

import java.util.function.IntSupplier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.item.ItemStack;

/**
 * A slot in one of a palette's extra rows (4 to 16), added to every shulker menu after the
 * player's inventory. It is live while the box has that row, palette on or off; otherwise it
 * takes nothing, gives nothing and isn't drawn, so a box without extra rows behaves exactly
 * like vanilla's.
 */
public final class RowSlot extends ShulkerBoxSlot {
    private final int row;
    /** The menu's packed palette ({@link PanelActions#pack}), live on both sides. */
    private final IntSupplier packed;

    public RowSlot(Container container, int index, int x, int y, IntSupplier packed) {
        super(container, index, x, y);
        this.row = index / Palette.ROW_WIDTH;
        this.packed = packed;
    }

    @Override
    public boolean isActive() {
        return PanelActions.unpack(packed.getAsInt()).map(p -> row < p.rows()).orElse(false);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return isActive() && super.mayPlace(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return isActive() && super.mayPickup(player);
    }
}
