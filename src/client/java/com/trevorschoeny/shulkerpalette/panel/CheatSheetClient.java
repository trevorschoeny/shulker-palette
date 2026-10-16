package com.trevorschoeny.shulkerpalette.panel;

import com.trevorschoeny.shulkerpalette.palette.CheatSheet;
import com.trevorschoeny.shulkerpalette.palette.SavedPalette;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.item.ItemStack;

/**
 * The cheat sheet of the box whose screen is open, as the server last sent it, keyed by the
 * menu it belongs to (it can arrive just before the screen opens).
 */
public final class CheatSheetClient {
    private CheatSheetClient() {}

    private static int containerId = -1;
    private static Map<Integer, ItemStack> bySlot = Map.of();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CheatSheet.Sync.TYPE, (payload, context) -> {
            Map<Integer, ItemStack> sheet = new HashMap<>();
            for (SavedPalette.Entry entry : payload.entries()) sheet.put(entry.slot(), entry.item().create());
            containerId = payload.containerId();
            bySlot = sheet;
        });
    }

    /** What belongs in this box slot of menu {@code menuId}, or empty. */
    public static ItemStack ghost(int menuId, int slot) {
        return menuId == containerId ? bySlot.getOrDefault(slot, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public static boolean has(int menuId) {
        return menuId == containerId && !bySlot.isEmpty();
    }
}
