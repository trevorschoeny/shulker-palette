package com.trevorschoeny.shulkerpalette.palette;

import com.trevorschoeny.shulkerpalette.compat.OpenAnywhereServer;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The shulker a palette action is for: the one in the main hand, one the player pointed at in
 * a screen (the palette key over a slot), or the one whose screen the player has in front
 * through Open Anywhere. The client names it with one int ({@link #HELD}, {@link #SHOWN},
 * {@link #menuSlot}, {@link #inventorySlot}); the server resolves it and checks it is really
 * there before anything changes.
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
    /** The menu slot the client sends for "the shulker opened through Open Anywhere, in front". */
    public static final int SHOWN = -2;
    /** Below this, a player inventory index: {@code INVENTORY - index}. */
    private static final int INVENTORY = -100;

    /** A slot of the player's open menu, by its index there (a chest's slot, say). */
    public static int menuSlot(int index) {
        return index;
    }

    /**
     * A slot of the player's own inventory, by its inventory index. Used for inventory slots on
     * any screen, since the creative screen's slots don't match the server's menu.
     */
    public static int inventorySlot(int index) {
        return INVENTORY - index;
    }

    /** What {@code menuSlot} names for this player now, or null if it isn't a shulker that's there. */
    public static @Nullable Target resolve(ServerPlayer player, int menuSlot) {
        if (menuSlot == HELD) {
            ItemStack hand = player.getMainHandItem();
            return Palette.isShulker(hand) ? new Target(hand, null) : null;
        }
        if (menuSlot == SHOWN) {
            // Only Open Anywhere opens a box away from the hand.
            return FabricLoader.getInstance().isModLoaded("openanywhere") ? OpenAnywhereServer.resolve(player) : null;
        }
        ItemStack stack;
        if (menuSlot >= 0) {
            if (menuSlot >= player.containerMenu.slots.size()) return null;
            stack = player.containerMenu.slots.get(menuSlot).getItem();
        } else {
            int index = INVENTORY - menuSlot;
            if (index < 0 || index >= player.getInventory().getContainerSize()) return null;
            stack = player.getInventory().getItem(index);
        }
        // The box itself, in its slot: changed in place, and the menu sends the change.
        return Palette.isShulker(stack) ? new Target(stack, null) : null;
    }
}
