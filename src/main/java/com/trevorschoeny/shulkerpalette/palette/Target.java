package com.trevorschoeny.shulkerpalette.palette;

import com.trevorschoeny.shulkerpalette.compat.OpenAnywhereServer;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The shulker a palette action is for: the one in the main hand, or one the player has open
 * through Open Anywhere, named by its slot on the open menu. The server resolves it from what
 * the client sent and checks it is really there before anything changes.
 *
 * @param stack the box itself, changed in place
 * @param slots the box's slots on the open menu while it is open there, in box order; null for
 *              the box in hand, whose contents live on its {@code minecraft:container}. An open
 *              box's contents are written back from these slots, so they're changed here, not on
 *              the component.
 */
public record Target(ItemStack stack, @Nullable List<Slot> slots) {
    /** The menu slot the client sends for "the shulker in the main hand". */
    public static final int HELD = -1;

    /** What {@code menuSlot} names for this player now, or null if it isn't a shulker that's there. */
    public static @Nullable Target resolve(ServerPlayer player, int menuSlot) {
        if (menuSlot == HELD) {
            ItemStack hand = player.getMainHandItem();
            return Palette.isShulker(hand) ? new Target(hand, null) : null;
        }
        // Only Open Anywhere opens a box away from the hand; without it, a menu slot names nothing.
        if (menuSlot < 0 || !FabricLoader.getInstance().isModLoaded("openanywhere")) return null;
        return OpenAnywhereServer.resolve(player, menuSlot);
    }
}
