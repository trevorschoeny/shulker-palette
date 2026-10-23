package com.trevorschoeny.shulkerpalette.compat;

import com.trevorschoeny.openanywhere.api.OpenItems;
import com.trevorschoeny.shulkerpalette.palette.CheatSheet;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.PaletteHolder;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import com.trevorschoeny.shulkerpalette.palette.Target;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The shulker whose screen the player has in front through Open Anywhere, as a palette target.
 * Loaded only when Open Anywhere is installed ({@link Target#resolve} checks first), so nothing
 * here is touched without it.
 */
public final class OpenAnywhereServer {
    private OpenAnywhereServer() {}

    /** Common init: an opened shulker gets room for every row, as a placed one does. */
    public static void register() {
        OpenItems.boxSize(stack -> Palette.isShulker(stack) ? Palette.MAX_ROWS * Palette.ROW_WIDTH : 0);
    }

    /**
     * The palette behind an opened box's container, read from and written to the box item;
     * null for any other container.
     */
    public static @Nullable PaletteHolder holder(Container container) {
        if (!(container instanceof OpenItems.Box box) || !Palette.isShulker(box.stack())) return null;
        ItemStack stack = box.stack();
        return new PaletteHolder() {
            @Override public Palette shulkerpalette$palette() { return stack.getOrDefault(Palette.COMPONENT, Palette.OFF); }
            @Override public void shulkerpalette$setPalette(Palette palette) { stack.set(Palette.COMPONENT, palette); }
            @Override public List<SavedPalette.Entry> shulkerpalette$cheatSheet() { return stack.getOrDefault(CheatSheet.COMPONENT, List.of()); }
            @Override public void shulkerpalette$setCheatSheet(List<SavedPalette.Entry> entries) {
                stack.set(CheatSheet.COMPONENT, entries.isEmpty() ? null : entries);
            }
        };
    }

    public static @Nullable Target resolve(ServerPlayer player) {
        // Open Anywhere's own check: an opened item's screen is in front, and it's a shulker.
        OpenItems.OpenItem item = OpenItems.shown(player);
        if (item == null || !Palette.isShulker(item.stack())) return null;
        // The item in front is the menu: a shulker box menu whose non-player slots are the box's, by box index.
        if (!(player.containerMenu instanceof ShulkerBoxMenu menu)) return null;
        List<Slot> slots = menu.slots.stream()
                .filter(slot -> !(slot.container instanceof Inventory))
                .sorted(Comparator.comparingInt(Slot::getContainerSlot))
                .toList();
        return new Target(item.stack(), slots);
    }
}
