package com.trevorschoeny.shulkerpalette.compat;

import com.trevlar.slotkit.api.group.ResolvedSlotGroup;
import com.trevlar.slotkit.api.group.SlotGroupCategories;
import com.trevorschoeny.openanywhere.api.OpenItems;
import com.trevorschoeny.shulkerpalette.palette.Palette;
import com.trevorschoeny.shulkerpalette.palette.Target;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A shulker the player has open through Open Anywhere, as a palette target. Loaded only when
 * Open Anywhere is installed ({@link Target#resolve} checks first), so nothing here is touched
 * without it.
 */
public final class OpenAnywhereServer {
    private OpenAnywhereServer() {}

    public static @Nullable Target resolve(ServerPlayer player, int menuSlot) {
        // Open Anywhere's own check: open at that slot of the open menu, and still in place.
        ItemStack stack = OpenItems.openAt(player, menuSlot);
        if (stack == null || !Palette.isShulker(stack)) return null;
        // Its slots on the menu: the slot group Open Anywhere shows its contents in.
        for (OpenItems.OpenItem item : OpenItems.of(player)) {
            if (item.menuSlot() != menuSlot) continue;
            for (ResolvedSlotGroup group : SlotGroupCategories.groups(player.containerMenu)) {
                if (group.id().equals(item.group())) return new Target(stack, group.slots());
            }
        }
        return null;
    }
}
