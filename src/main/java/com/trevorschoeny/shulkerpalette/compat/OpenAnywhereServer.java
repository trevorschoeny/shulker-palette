package com.trevorschoeny.shulkerpalette.compat;

import com.trevorschoeny.openanywhere.api.OpenItems;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Target;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * The shulker whose screen the player has in front through Open Anywhere, as a palette target.
 * Loaded only when Open Anywhere is installed ({@link Target#resolve} checks first), so nothing
 * here is touched without it.
 */
public final class OpenAnywhereServer {
    private OpenAnywhereServer() {}

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
