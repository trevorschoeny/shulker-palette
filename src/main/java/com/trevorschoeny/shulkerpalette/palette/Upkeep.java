package com.trevorschoeny.shulkerpalette.palette;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Keeps every palette in a player's inventory showing a next block that still matches its box.
 *
 * <p>A box's contents can change in many places: placed and refilled by hand, edited from
 * the inventory by another mod, given by a command. Rather than hook each, the server
 * compares each palette's stored fingerprint with its contents and chooses again when they
 * differ. This also covers a palette that has never chosen (a freshly turned-on box, or one
 * written by a command), whose fingerprint is 0.
 */
public final class Upkeep {
    private Upkeep() {}

    public static void register() {
        // Scans every inventory every tick; reading one component per slot is cheap.
        // Move to a change hook if a profile ever shows it.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) refresh(player);
        });
    }

    private static void refresh(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            Palette palette = Palette.activeOn(stack);
            if (palette == null) continue;
            ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            if (Draw.fingerprint(contents) != palette.fingerprint()) {
                Placement.rollNext(stack, palette, contents, player.level());
            }
        }
    }
}
